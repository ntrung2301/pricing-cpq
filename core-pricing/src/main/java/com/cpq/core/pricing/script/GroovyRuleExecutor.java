package com.cpq.core.pricing.script;

import com.cpq.core.pricing.PricingContext;
import com.cpq.core.pricing.Rule;
import com.cpq.core.pricing.RuleApplication;
import com.cpq.core.pricing.RuleDefinitionRejectedException;
import com.cpq.core.pricing.RuleExecutionException;
import com.cpq.core.pricing.RuleExecutor;
import groovy.lang.Binding;
import groovy.lang.Script;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executor for {@code rule_type: SCRIPT} rules (ADR-002/D-014). Compiles
 * {@code rule.definition()} via {@link GroovyRuleCompiler} (sandboxed —
 * see that class for the two-layer policy) on every call — cheap relative
 * to the rest of pricing, and defends against a rule that somehow bypassed
 * validation at save time ever actually running.
 *
 * <h2>Script contract</h2>
 * The script runs with four bound variables, the entire generic
 * {@code RuleContext} ADR-002 describes:
 * <ul>
 *   <li>{@code quantity} — {@link BigDecimal}, from
 *       {@link PricingContext#quantity()};
 *   <li>{@code basePrice} — {@link BigDecimal}, from
 *       {@link PricingContext#basePrice()};
 *   <li>{@code currentAmount} — {@link BigDecimal}, the running total
 *       entering this rule;
 *   <li>{@code attributes} — unmodifiable {@code Map<String, Object>}
 *       from {@link PricingContext#attributes()}.
 * </ul>
 * It must {@code return} either {@code null} (the rule's condition does
 * not match — no waterfall step produced, same contract as a DSL rule
 * whose {@code conditions} evaluates to false) or a {@code Map} with a
 * {@code delta} entry (coercible to {@link BigDecimal}) and an optional
 * {@code label} entry.
 *
 * <h2>Timeout</h2>
 * Execution runs on a short-lived daemon thread with a fixed wall-clock
 * budget ({@link #TIMEOUT}). This is <strong>infrastructure</strong>
 * around the script, not code inside it — the sandbox policy forbids a
 * *script* from touching {@code Thread}, but the engine itself is allowed
 * to use one to bound how long it waits for a script
 * (docs/sprints/sprint-2.md S2-T10: "timeout là hạ tầng chạy script,
 * không phải code trong script"). Known limitation: a tight CPU-bound
 * loop with no interruption point (no I/O, no blocking call) cannot
 * actually be killed by {@link Thread#interrupt()} — the call to this
 * executor still returns/throws within the timeout budget (the caller is
 * never blocked longer than {@link #TIMEOUT}), but the orphaned
 * background thread may keep burning CPU until the JVM exits. Documented
 * in {@code docs/decisions.md} as a known risk, not fixed by this task.
 */
public final class GroovyRuleExecutor implements RuleExecutor {

    private static final Duration TIMEOUT = Duration.ofMillis(500);

    private final GroovyRuleCompiler compiler = new GroovyRuleCompiler();

    private final ExecutorService executorService =
            Executors.newThreadPerTaskExecutor(runnable -> {
                Thread thread = new Thread(runnable, "groovy-rule-script");
                thread.setDaemon(true);
                return thread;
            });

    @Override
    public Optional<RuleApplication> apply(Rule rule, PricingContext context, BigDecimal currentAmount) {
        Class<Script> compiledScript = compiler.compile(rule.definition());

        Callable<Object> task = () -> runScript(compiledScript, context, currentAmount);
        java.util.concurrent.Future<Object> future = executorService.submit(task);
        Object result;
        try {
            result = future.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuleExecutionException(
                    "Script rule " + rule.id() + " exceeded the " + TIMEOUT.toMillis() + "ms execution timeout",
                    e);
        } catch (Exception e) {
            throw new RuleExecutionException("Script rule " + rule.id() + " failed during execution", e);
        }

        return interpretResult(rule, result);
    }

    private static Object runScript(Class<Script> compiledScript, PricingContext context, BigDecimal currentAmount) {
        try {
            Binding binding = new Binding();
            binding.setVariable("quantity", context.quantity());
            binding.setVariable("basePrice", context.basePrice());
            binding.setVariable("currentAmount", currentAmount);
            binding.setVariable("attributes", context.attributes());

            Script script = compiledScript.getDeclaredConstructor().newInstance();
            script.setBinding(binding);
            return script.run();
        } catch (ReflectiveOperationException e) {
            throw new RuleExecutionException("Could not instantiate compiled rule script", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Optional<RuleApplication> interpretResult(Rule rule, Object result) {
        if (result == null) {
            return Optional.empty();
        }
        if (!(result instanceof Map<?, ?> map)) {
            throw new RuleExecutionException(
                    "Script rule " + rule.id() + " must return null or a Map with a \"delta\" entry, got: "
                            + result.getClass());
        }
        Object deltaValue = map.get("delta");
        if (deltaValue == null) {
            throw new RuleExecutionException("Script rule " + rule.id() + " returned a Map without \"delta\"");
        }
        BigDecimal delta = toBigDecimal(rule, deltaValue);
        Object labelValue = map.get("label");
        String label = labelValue != null ? labelValue.toString() : "Script rule " + rule.id();
        return Optional.of(new RuleApplication(delta, label));
    }

    private static BigDecimal toBigDecimal(Rule rule, Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        throw new RuleExecutionException(
                "Script rule " + rule.id() + " returned a non-numeric \"delta\": " + value);
    }
}
