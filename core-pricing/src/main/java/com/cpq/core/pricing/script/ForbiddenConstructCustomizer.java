package com.cpq.core.pricing.script;

import java.util.Set;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;

/**
 * Second layer of the Groovy sandbox (D-014/CLAUDE.md mục 7), on top of
 * {@link SecureASTCustomizer}'s import blacklist (see
 * {@link GroovyRuleCompiler}). {@code SecureASTCustomizer}'s import lists
 * only catch usages that go through an {@code import} statement; a script
 * can trivially bypass that with a fully-qualified reference (e.g.
 * {@code new java.io.File(...)} or {@code java.lang.System.exit(1)}
 * without ever importing anything). This visitor walks the AST directly
 * and rejects, independent of imports:
 *
 * <ul>
 *   <li>construction of I/O, reflection, process, or non-deterministic
 *       time/random types ({@code new Date()}, {@code new Random()}, ...)
 *       — blocked unconditionally, including seeded {@code Random}, since
 *       a pricing rule has no legitimate use for randomness at all;
 *   <li>method calls on {@code System}/{@code Thread}/{@code Runtime}
 *       (whether reached via an imported short name or a fully-qualified
 *       {@link ClassExpression} receiver);
 *   <li>{@code Math.random()} specifically (other {@code Math} methods
 *       are deterministic and stay allowed);
 *   <li>reflection-pattern method names ({@code forName},
 *       {@code getMethod}, {@code getDeclaredMethod}, {@code invoke},
 *       {@code setAccessible}, ...) regardless of receiver.
 * </ul>
 *
 * <p>Runs at {@link CompilePhase#SEMANTIC_ANALYSIS}, same phase
 * {@code SecureASTCustomizer} itself uses, so both layers run during
 * {@code GroovyShell#parse}/compile — never at script execution time.
 */
final class ForbiddenConstructCustomizer extends CompilationCustomizer {

    private static final Set<String> FORBIDDEN_CONSTRUCTOR_TYPES =
            Set.of(
                    "java.io.File",
                    "java.io.FileInputStream",
                    "java.io.FileOutputStream",
                    "java.io.FileReader",
                    "java.io.FileWriter",
                    "java.io.RandomAccessFile",
                    "java.util.Date",
                    "java.util.Random",
                    "java.net.Socket",
                    "java.net.URL",
                    "java.lang.Thread",
                    "java.lang.ProcessBuilder",
                    "File",
                    "FileInputStream",
                    "FileOutputStream",
                    "FileReader",
                    "FileWriter",
                    "RandomAccessFile",
                    "Date",
                    "Random",
                    "Socket",
                    "URL",
                    "Thread",
                    "ProcessBuilder");

    private static final Set<String> FORBIDDEN_CALL_RECEIVER_TYPES =
            Set.of(
                    "java.lang.System", "System",
                    "java.lang.Thread", "Thread",
                    "java.lang.Runtime", "Runtime",
                    "java.lang.ProcessBuilder", "ProcessBuilder",
                    "java.nio.file.Files", "Files");

    private static final Set<String> FORBIDDEN_METHOD_NAMES_ANY_RECEIVER =
            Set.of(
                    "forName",
                    "getMethod",
                    "getDeclaredMethod",
                    "getMethods",
                    "getDeclaredMethods",
                    "getDeclaredField",
                    "getDeclaredFields",
                    "invoke",
                    "setAccessible",
                    "exit",
                    "currentTimeMillis",
                    "nanoTime");

    ForbiddenConstructCustomizer() {
        super(CompilePhase.SEMANTIC_ANALYSIS);
    }

    @Override
    public void call(SourceUnit source, GeneratorContext context, ClassNode classNode)
            throws CompilationFailedException {
        new PolicyVisitor(source).visitClass(classNode);
    }

    private static final class PolicyVisitor extends ClassCodeVisitorSupport {

        private final SourceUnit sourceUnit;

        private PolicyVisitor(SourceUnit sourceUnit) {
            this.sourceUnit = sourceUnit;
        }

        @Override
        protected SourceUnit getSourceUnit() {
            return sourceUnit;
        }

        @Override
        public void visitConstructorCallExpression(ConstructorCallExpression call) {
            String typeName = call.getType().getName();
            if (FORBIDDEN_CONSTRUCTOR_TYPES.contains(typeName)) {
                throw new SecurityException(
                        "Rule script may not construct " + typeName + " (sandbox policy)");
            }
            super.visitConstructorCallExpression(call);
        }

        @Override
        public void visitMethodCallExpression(MethodCallExpression call) {
            String methodName = call.getMethodAsString();
            Expression receiver = call.getObjectExpression();
            String receiverType = receiverTypeNameOf(receiver);

            if (receiverType != null && FORBIDDEN_CALL_RECEIVER_TYPES.contains(receiverType)) {
                throw new SecurityException(
                        "Rule script may not call " + receiverType + "." + methodName + " (sandbox policy)");
            }
            if (isMathType(receiverType) && "random".equals(methodName)) {
                throw new SecurityException("Rule script may not call Math.random() (non-deterministic)");
            }
            if (methodName != null && FORBIDDEN_METHOD_NAMES_ANY_RECEIVER.contains(methodName)) {
                throw new SecurityException(
                        "Rule script may not call ." + methodName + "(...) (sandbox policy)");
            }
            super.visitMethodCallExpression(call);
        }

        @Override
        public void visitStaticMethodCallExpression(StaticMethodCallExpression call) {
            String ownerType = call.getOwnerType().getName();
            String methodName = call.getMethod();
            if (FORBIDDEN_CALL_RECEIVER_TYPES.contains(ownerType)) {
                throw new SecurityException(
                        "Rule script may not call " + ownerType + "." + methodName + " (sandbox policy)");
            }
            if (isMathType(ownerType) && "random".equals(methodName)) {
                throw new SecurityException("Rule script may not call Math.random() (non-deterministic)");
            }
            if (methodName != null && FORBIDDEN_METHOD_NAMES_ANY_RECEIVER.contains(methodName)) {
                throw new SecurityException(
                        "Rule script may not call ." + methodName + "(...) (sandbox policy)");
            }
            super.visitStaticMethodCallExpression(call);
        }

        private static boolean isMathType(String typeName) {
            return "Math".equals(typeName) || "java.lang.Math".equals(typeName);
        }

        private static String receiverTypeNameOf(Expression receiver) {
            if (receiver instanceof ClassExpression classExpression) {
                return classExpression.getType().getName();
            }
            return null;
        }
    }
}
