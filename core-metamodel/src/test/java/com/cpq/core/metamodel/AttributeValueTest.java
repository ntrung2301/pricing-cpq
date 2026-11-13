package com.cpq.core.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Plain-unit tests for {@link AttributeValue}'s type-matching (ADR-001:
 * "validate theo attribute_def.data_type lúc viết") and its canonical
 * string form used to sync {@code business_key}/{@code display_label}
 * (D-019).
 */
class AttributeValueTest {

    @Test
    void stringValueMatchesStringEnumAndJsonDataTypesOnly() {
        AttributeValue value = AttributeValue.ofString("ABC-100");

        assertThat(value.matches(AttributeDataType.STRING)).isTrue();
        assertThat(value.matches(AttributeDataType.ENUM)).isTrue();
        assertThat(value.matches(AttributeDataType.JSON)).isTrue();
        assertThat(value.matches(AttributeDataType.NUMBER)).isFalse();
        assertThat(value.matches(AttributeDataType.BOOLEAN)).isFalse();
    }

    @Test
    void numberValueMatchesNumberAndDecimalDataTypesOnly() {
        AttributeValue value = AttributeValue.ofNumber(new BigDecimal("12.50"));

        assertThat(value.matches(AttributeDataType.NUMBER)).isTrue();
        assertThat(value.matches(AttributeDataType.DECIMAL)).isTrue();
        assertThat(value.matches(AttributeDataType.STRING)).isFalse();
    }

    @Test
    void asDisplayStringRendersEachTypedValueCanonically() {
        assertThat(AttributeValue.ofString("ABC").asDisplayString()).isEqualTo("ABC");
        assertThat(AttributeValue.ofNumber(new BigDecimal("12.50")).asDisplayString()).isEqualTo("12.50");
        assertThat(AttributeValue.ofDate(LocalDate.of(2026, 1, 1)).asDisplayString()).isEqualTo("2026-01-01");
        assertThat(AttributeValue.ofBoolean(true).asDisplayString()).isEqualTo("true");
        assertThat(AttributeValue.ofReferenceInstanceId(42L).asDisplayString()).isEqualTo("42");
    }
}
