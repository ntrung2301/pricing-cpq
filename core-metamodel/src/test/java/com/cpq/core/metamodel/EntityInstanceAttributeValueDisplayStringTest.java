package com.cpq.core.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Plain-unit (no DB) tests for {@link EntityInstanceAttributeValue#asDisplayString()}
 * — the same canonical string convention as {@link AttributeValue#asDisplayString()}
 * (D-019), needed by {@code EntityInstanceService} (S3-T1, D-039) to read
 * back an already-written dimension attribute value (e.g. {@code period})
 * when assembling an {@link EntityRole#EXTENSION} instance's composite
 * business key.
 */
class EntityInstanceAttributeValueDisplayStringTest {

    private static final EntityInstance DUMMY_INSTANCE =
            new EntityInstance(new EntityDefinition("WIDGET", "Widget", 1), "test-pack", 1);
    private static final AttributeDefinition DUMMY_ATTRIBUTE =
            new AttributeDefinition("period", "Period", AttributeDataType.STRING, true, null);

    @Test
    void rendersEachTypedValueCanonically() {
        assertThat(
                        new EntityInstanceAttributeValue(DUMMY_INSTANCE, DUMMY_ATTRIBUTE, AttributeValue.ofString("2026-Q1"))
                                .asDisplayString())
                .isEqualTo("2026-Q1");
        assertThat(
                        new EntityInstanceAttributeValue(
                                        DUMMY_INSTANCE, DUMMY_ATTRIBUTE, AttributeValue.ofNumber(new BigDecimal("12.50")))
                                .asDisplayString())
                .isEqualTo("12.50");
        assertThat(
                        new EntityInstanceAttributeValue(
                                        DUMMY_INSTANCE, DUMMY_ATTRIBUTE, AttributeValue.ofDate(LocalDate.of(2026, 1, 1)))
                                .asDisplayString())
                .isEqualTo("2026-01-01");
        assertThat(
                        new EntityInstanceAttributeValue(DUMMY_INSTANCE, DUMMY_ATTRIBUTE, AttributeValue.ofBoolean(true))
                                .asDisplayString())
                .isEqualTo("true");
        assertThat(
                        new EntityInstanceAttributeValue(
                                        DUMMY_INSTANCE, DUMMY_ATTRIBUTE, AttributeValue.ofReferenceInstanceId(42L))
                                .asDisplayString())
                .isEqualTo("42");
    }
}
