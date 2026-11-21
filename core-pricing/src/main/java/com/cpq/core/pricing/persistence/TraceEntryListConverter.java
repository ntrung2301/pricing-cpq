package com.cpq.core.pricing.persistence;

import com.cpq.core.pricing.dag.TraceEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

/**
 * JPA {@link AttributeConverter} for {@code price_list_item_v2.waterfall_steps}
 * (S3-T6, ADR-009 mục 5): a {@code List<TraceEntry>} stored as {@code
 * jsonb}, serialized/deserialized with Jackson directly against the {@link
 * TraceEntry} record — same technique as {@link DagNodeGraphConverter}
 * (reused as-is for {@code price_list_v2.calculation_graph}, per
 * docs/decisions.md D-045 point 3's note to reuse it directly for Price
 * List rather than rewrite it).
 *
 * <p><b>Why this is a separate, new converter rather than reusing {@link
 * DagNodeGraphConverter}</b>: the two attribute types are different
 * records ({@code List<DagNode>} vs {@code List<TraceEntry>}) — Java
 * generics erase {@code AttributeConverter}'s type parameter, so one
 * converter class cannot serve both without an unchecked cast, and more
 * importantly the two have different *semantic* requirements on read:
 * {@link DagNodeGraphConverter} must re-wrap a {@code formula} node's
 * {@code expression} config back into a real {@code JsonNode} because that
 * graph gets RE-EXECUTED by {@code DagEngine}, which enforces an {@code
 * instanceof JsonNode} check. A {@code price_list_item_v2}'s {@code
 * waterfall_steps} is pure, read-only audit data (the already-computed
 * Calculation Trace) — it is never fed back into {@code DagEngine}, so no
 * such re-wrapping is needed; a plain Jackson round-trip (with {@code
 * USE_BIG_DECIMAL_FOR_FLOATS}, same as every other {@code ObjectMapper} in
 * this codebase that touches money — CLAUDE.md mục 7) is sufficient.
 */
@Converter
public class TraceEntryListConverter implements AttributeConverter<List<TraceEntry>, String> {

    private final ObjectMapper objectMapper =
            new ObjectMapper().configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);

    @Override
    public String convertToDatabaseColumn(List<TraceEntry> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Calculation Trace to JSON", e);
        }
    }

    @Override
    public List<TraceEntry> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData, new TypeReference<List<TraceEntry>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize Calculation Trace JSON: " + dbData, e);
        }
    }
}
