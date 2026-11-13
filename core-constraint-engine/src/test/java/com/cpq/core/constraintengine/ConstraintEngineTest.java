package com.cpq.core.constraintengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ConstraintEngine} — the generic evaluator for the
 * JSON condition grammar shared with rule conditions (ADR-002: ==, !=, >,
 * >=, <, <=, in, and, or, not, var). Deliberately uses two made-up,
 * non-automotive entities ("Gadget"/"Widget") to prove the engine has no
 * idea what "fitment"/"Vehicle"/"Part" are (E1-02, S2-T2) — it only knows
 * aliases ("x"/"y") pointing at generic {@link AttributeBag}s.
 */
class ConstraintEngineTest {

    private final ConstraintEngine engine = new ConstraintEngine();

    private EvaluationContext gadgetAndWidget(AttributeBag x, AttributeBag y) {
        return EvaluationContext.of(Map.of("x", x, "y", y));
    }

    @Test
    void equalityConditionPassesWhenBothAttributeValuesMatch() {
        AttributeBag gadget = AttributeBag.builder().put("color", "red").build();
        AttributeBag widget = AttributeBag.builder().put("color", "red").build();
        EvaluationContext context = gadgetAndWidget(gadget, widget);

        ConstraintEvaluationResult result =
                engine.evaluate("{\"==\": [{\"var\": \"x.color\"}, {\"var\": \"y.color\"}]}", context);

        assertThat(result.passed()).isTrue();
    }

    @Test
    void equalityConditionFailsWhenAttributeValuesDiffer() {
        AttributeBag gadget = AttributeBag.builder().put("color", "red").build();
        AttributeBag widget = AttributeBag.builder().put("color", "blue").build();
        EvaluationContext context = gadgetAndWidget(gadget, widget);

        ConstraintEvaluationResult result =
                engine.evaluate("{\"==\": [{\"var\": \"x.color\"}, {\"var\": \"y.color\"}]}", context);

        assertThat(result.passed()).isFalse();
    }

    @Test
    void equalityAgainstLiteralValue() {
        AttributeBag gadget = AttributeBag.builder().put("color", "red").build();
        EvaluationContext context = gadgetAndWidget(gadget, AttributeBag.builder().build());

        assertThat(engine.evaluate("{\"==\": [{\"var\": \"x.color\"}, \"red\"]}", context).passed()).isTrue();
        assertThat(engine.evaluate("{\"!=\": [{\"var\": \"x.color\"}, \"blue\"]}", context).passed()).isTrue();
    }

    @Test
    void numericOrderingComparesAttributeAgainstAnotherAttribute() {
        AttributeBag gadget = AttributeBag.builder().put("weight", new BigDecimal("4.500")).build();
        AttributeBag widget = AttributeBag.builder().put("maxWeight", new BigDecimal("5")).build();
        EvaluationContext context = gadgetAndWidget(gadget, widget);

        ConstraintEvaluationResult result =
                engine.evaluate("{\"<=\": [{\"var\": \"x.weight\"}, {\"var\": \"y.maxWeight\"}]}", context);

        assertThat(result.passed()).isTrue();
    }

    @Test
    void numericOrderingFailsWhenOutOfRange() {
        AttributeBag gadget = AttributeBag.builder().put("weight", new BigDecimal("6")).build();
        AttributeBag widget = AttributeBag.builder().put("maxWeight", new BigDecimal("5")).build();
        EvaluationContext context = gadgetAndWidget(gadget, widget);

        ConstraintEvaluationResult result =
                engine.evaluate("{\"<=\": [{\"var\": \"x.weight\"}, {\"var\": \"y.maxWeight\"}]}", context);

        assertThat(result.passed()).isFalse();
    }

    @Test
    void dateOrderingComparesLocalDateAttributeAgainstIsoLiteralString() {
        AttributeBag gadget = AttributeBag.builder().put("availableFrom", LocalDate.of(2026, 1, 1)).build();
        EvaluationContext context = gadgetAndWidget(gadget, AttributeBag.builder().build());

        ConstraintEvaluationResult result =
                engine.evaluate("{\">=\": [{\"var\": \"x.availableFrom\"}, \"2025-01-01\"]}", context);

        assertThat(result.passed()).isTrue();
    }

    @Test
    void andCombinesSubConditionsRequiringAllTrue() {
        AttributeBag gadget =
                AttributeBag.builder().put("color", "red").put("weight", new BigDecimal("3")).build();
        AttributeBag widget =
                AttributeBag.builder().put("color", "red").put("maxWeight", new BigDecimal("5")).build();
        EvaluationContext context = gadgetAndWidget(gadget, widget);

        String condition =
                "{\"and\": ["
                        + "{\"==\": [{\"var\": \"x.color\"}, {\"var\": \"y.color\"}]},"
                        + "{\"<=\": [{\"var\": \"x.weight\"}, {\"var\": \"y.maxWeight\"}]}"
                        + "]}";

        assertThat(engine.evaluate(condition, context).passed()).isTrue();
    }

    @Test
    void andFailsWhenOneSubConditionFails() {
        AttributeBag gadget =
                AttributeBag.builder().put("color", "red").put("weight", new BigDecimal("9")).build();
        AttributeBag widget =
                AttributeBag.builder().put("color", "red").put("maxWeight", new BigDecimal("5")).build();
        EvaluationContext context = gadgetAndWidget(gadget, widget);

        String condition =
                "{\"and\": ["
                        + "{\"==\": [{\"var\": \"x.color\"}, {\"var\": \"y.color\"}]},"
                        + "{\"<=\": [{\"var\": \"x.weight\"}, {\"var\": \"y.maxWeight\"}]}"
                        + "]}";

        assertThat(engine.evaluate(condition, context).passed()).isFalse();
    }

    @Test
    void orPassesWhenAtLeastOneSubConditionTrue() {
        AttributeBag gadget = AttributeBag.builder().put("color", "red").build();
        AttributeBag widget = AttributeBag.builder().put("color", "blue").build();
        EvaluationContext context = gadgetAndWidget(gadget, widget);

        String condition =
                "{\"or\": ["
                        + "{\"==\": [{\"var\": \"x.color\"}, {\"var\": \"y.color\"}]},"
                        + "{\"==\": [{\"var\": \"x.color\"}, \"red\"]}"
                        + "]}";

        assertThat(engine.evaluate(condition, context).passed()).isTrue();
    }

    @Test
    void notNegatesInnerCondition() {
        AttributeBag gadget = AttributeBag.builder().put("color", "red").build();
        AttributeBag widget = AttributeBag.builder().put("color", "blue").build();
        EvaluationContext context = gadgetAndWidget(gadget, widget);

        String condition = "{\"not\": {\"==\": [{\"var\": \"x.color\"}, {\"var\": \"y.color\"}]}}";

        assertThat(engine.evaluate(condition, context).passed()).isTrue();
    }

    @Test
    void inOperatorChecksMembershipAgainstJsonArrayLiteral() {
        AttributeBag gadget = AttributeBag.builder().put("size", "M").build();
        EvaluationContext context = gadgetAndWidget(gadget, AttributeBag.builder().build());

        String condition = "{\"in\": [{\"var\": \"x.size\"}, [\"S\", \"M\", \"L\"]]}";

        assertThat(engine.evaluate(condition, context).passed()).isTrue();
    }

    @Test
    void inOperatorFailsWhenValueNotInList() {
        AttributeBag gadget = AttributeBag.builder().put("size", "XL").build();
        EvaluationContext context = gadgetAndWidget(gadget, AttributeBag.builder().build());

        String condition = "{\"in\": [{\"var\": \"x.size\"}, [\"S\", \"M\", \"L\"]]}";

        assertThat(engine.evaluate(condition, context).passed()).isFalse();
    }

    @Test
    void unknownOperatorIsRejectedDeterministicallyRatherThanSilentlyPassing() {
        EvaluationContext context = gadgetAndWidget(AttributeBag.builder().build(), AttributeBag.builder().build());

        assertThatThrownBy(() -> engine.evaluate("{\"xor\": [true, false]}", context))
                .isInstanceOf(ConditionEvaluationException.class);
    }

    @Test
    void unknownAliasIsRejected() {
        EvaluationContext context = gadgetAndWidget(AttributeBag.builder().build(), AttributeBag.builder().build());

        assertThatThrownBy(() -> engine.evaluate("{\"==\": [{\"var\": \"z.color\"}, \"red\"]}", context))
                .isInstanceOf(ConditionEvaluationException.class);
    }

    @Test
    void malformedConditionJsonIsRejected() {
        EvaluationContext context = gadgetAndWidget(AttributeBag.builder().build(), AttributeBag.builder().build());

        assertThatThrownBy(() -> engine.evaluate("not valid json", context))
                .isInstanceOf(ConditionEvaluationException.class);
    }
}
