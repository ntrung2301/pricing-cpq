package com.cpq.core.pricing.persistence;

import com.cpq.core.pricing.PricingStage;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps {@link PricingStage} to/from its {@link PricingStage#wireCode()}
 * (e.g. {@code SEGMENT_ADJUSTMENT} &lt;-&gt; {@code "segment-adjustment"})
 * — the exact string the {@code rule.stage} column's {@code CHECK}
 * constraint allows (ADR-004 naming), rather than Hibernate's default
 * {@link Enum#name()} mapping.
 */
@Converter(autoApply = false)
public class PricingStageConverter implements AttributeConverter<PricingStage, String> {

    @Override
    public String convertToDatabaseColumn(PricingStage attribute) {
        return attribute == null ? null : attribute.wireCode();
    }

    @Override
    public PricingStage convertToEntityAttribute(String dbData) {
        return dbData == null ? null : PricingStage.fromWireCode(dbData);
    }
}
