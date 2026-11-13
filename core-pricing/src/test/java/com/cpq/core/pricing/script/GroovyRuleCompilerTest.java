package com.cpq.core.pricing.script;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cpq.core.pricing.RuleDefinitionRejectedException;
import org.junit.jupiter.api.Test;

/**
 * {@link GroovyRuleCompiler} is the compile/validate-time gate (D-014/
 * CLAUDE.md mục 7): a script violating the sandbox policy must be
 * rejected here, before it is ever executed — this is what a future
 * "save rule" admin endpoint would call. Every malicious script below is
 * chosen so that if the sandbox failed and the script actually ran, the
 * test process itself would be harmed (e.g. {@code System.exit(1)} would
 * kill the JVM) — compile-time rejection is the only safe way to test
 * this.
 */
class GroovyRuleCompilerTest {

    private final GroovyRuleCompiler compiler = new GroovyRuleCompiler();

    @Test
    void acceptsBenignScriptReturningDeltaMap() {
        compiler.compile("return [delta: currentAmount * new BigDecimal('-0.1'), label: 'ok']");
        // No exception == accepted.
    }

    @Test
    void rejectsImportedFileIO() {
        assertRejected("import java.io.File\nnew File('/tmp/x')");
    }

    @Test
    void rejectsFullyQualifiedFileIOWithoutImport() {
        assertRejected("new java.io.File('/tmp/x').exists()");
    }

    @Test
    void rejectsSystemExit() {
        assertRejected("System.exit(1)");
    }

    @Test
    void rejectsSystemCurrentTimeMillis() {
        assertRejected("return System.currentTimeMillis()");
    }

    @Test
    void rejectsNewDateNonDeterministicClock() {
        assertRejected("return new Date()");
    }

    @Test
    void rejectsUnseededRandom() {
        assertRejected("return new Random().nextInt()");
    }

    @Test
    void rejectsMathRandom() {
        assertRejected("return Math.random()");
    }

    @Test
    void rejectsThreadSleep() {
        assertRejected("Thread.sleep(10)");
    }

    @Test
    void rejectsReflection() {
        assertRejected("Class.forName('java.lang.Runtime').getMethod('exit', int.class)");
    }

    @Test
    void rejectsProcessBuilder() {
        assertRejected("new ProcessBuilder('ls').start()");
    }

    private void assertRejected(String maliciousScript) {
        assertThatThrownBy(() -> compiler.compile(maliciousScript))
                .isInstanceOf(RuleDefinitionRejectedException.class);
    }
}
