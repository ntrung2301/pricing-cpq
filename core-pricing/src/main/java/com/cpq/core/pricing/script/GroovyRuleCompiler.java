package com.cpq.core.pricing.script;

import com.cpq.core.pricing.RuleDefinitionRejectedException;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import java.util.List;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;

/**
 * Compiles (and by doing so, validates) a Groovy rule script against the
 * mandatory sandbox policy (D-014/CLAUDE.md mục 7). This is the
 * compile/validate-time gate: rejection here means the script body is
 * never executed — the exact requirement from
 * {@code docs/sprints/sprint-2.md} S2-T10 ("vi phạm → reject rule lúc lưu,
 * không lúc runtime"). A future "save SCRIPT rule" admin endpoint should
 * call {@link #compile(String)} (or {@link #validate(String)}) before
 * persisting; {@link GroovyRuleExecutor} also calls it on every
 * execution, defensively, in case an already-invalid script ever made it
 * into the database some other way.
 *
 * <p>Two independent layers (see class docs on each for why both are
 * needed):
 * <ol>
 *   <li>{@link SecureASTCustomizer} — blocks I/O/reflection/net
 *       imports (including {@code import java.io.*}-style star imports)
 *       and disallows package declarations;
 *   <li>{@link ForbiddenConstructCustomizer} — a custom AST visitor that
 *       additionally blocks the same constructs when referenced fully
 *       qualified (no import), plus specific non-deterministic calls
 *       ({@code Math.random()}, {@code System.currentTimeMillis()},
 *       {@code new Date()}, {@code new Random()}, ...) that aren't a
 *       "type" an import list can name.
 * </ol>
 */
public final class GroovyRuleCompiler {

    private final CompilerConfiguration compilerConfiguration = buildSandboxedConfiguration();

    /**
     * Compiles {@code source} and returns the resulting {@link Script}
     * class, ready to be {@code newInstance()}'d with a {@link
     * groovy.lang.Binding} and executed. Throws
     * {@link RuleDefinitionRejectedException} if the script is malformed
     * Groovy or violates the sandbox policy — in both cases, no part of
     * the script body is ever executed.
     */
    @SuppressWarnings("unchecked")
    public Class<Script> compile(String source) {
        GroovyShell shell = new GroovyShell(compilerConfiguration);
        try {
            return (Class<Script>) shell.getClassLoader().parseClass(source);
        } catch (RuntimeException e) {
            // SecureASTCustomizer violations surface as
            // org.codehaus.groovy.control.MultipleCompilationErrorsException
            // (SecurityException wrapped per-error); ForbiddenConstructCustomizer
            // throws SecurityException directly. Both — and any other compile
            // failure (syntax errors, etc.) — are rejections, never left to
            // surface as some other exception type the caller didn't ask for.
            throw new RuleDefinitionRejectedException(
                    "Rule script rejected at compile/validate time: " + e.getMessage(), e);
        }
    }

    /** Validates {@code source} without keeping the compiled class around. */
    public void validate(String source) {
        compile(source);
    }

    private static CompilerConfiguration buildSandboxedConfiguration() {
        SecureASTCustomizer secureASTCustomizer = new SecureASTCustomizer();
        secureASTCustomizer.setClosuresAllowed(true);
        secureASTCustomizer.setMethodDefinitionAllowed(true);
        secureASTCustomizer.setPackageAllowed(false);
        secureASTCustomizer.setImportsBlacklist(
                List.of(
                        "java.io.File",
                        "java.io.FileInputStream",
                        "java.io.FileOutputStream",
                        "java.io.FileReader",
                        "java.io.FileWriter",
                        "java.io.RandomAccessFile",
                        "java.lang.reflect.Method",
                        "java.lang.reflect.Field",
                        "java.lang.reflect.Constructor",
                        "java.lang.Thread",
                        "java.lang.ThreadGroup",
                        "java.lang.Process",
                        "java.lang.ProcessBuilder",
                        "java.lang.Runtime",
                        "java.util.Date",
                        "java.util.Random",
                        "java.net.Socket",
                        "java.net.URL",
                        "java.net.URLConnection"));
        secureASTCustomizer.setStarImportsBlacklist(
                List.of(
                        "java.io.*",
                        "java.nio.*",
                        "java.nio.file.*",
                        "java.lang.reflect.*",
                        "java.net.*"));
        secureASTCustomizer.setStaticImportsBlacklist(
                List.of(
                        "java.lang.System.exit",
                        "java.lang.System.currentTimeMillis",
                        "java.lang.System.nanoTime",
                        "java.lang.Math.random"));
        secureASTCustomizer.setStaticStarImportsBlacklist(List.of("java.lang.System.*", "java.lang.Math.*"));

        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.addCompilationCustomizers(secureASTCustomizer, new ForbiddenConstructCustomizer());
        return configuration;
    }
}
