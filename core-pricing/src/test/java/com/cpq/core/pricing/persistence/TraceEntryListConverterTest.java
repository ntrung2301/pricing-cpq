package com.cpq.core.pricing.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.cpq.core.pricing.dag.TraceEntry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link TraceEntryListConverter} (S3-T6, backs {@code
 * price_list_item_v2.waterfall_steps}): a {@code List<TraceEntry>} must
 * round-trip through the JSON text stored in the {@code jsonb} column back
 * into an equal {@code List<TraceEntry>}, with {@code BigDecimal} money
 * values preserved exactly (CLAUDE.md mục 7 — never silently widened to
 * {@code Double}).
 *
 * <p>Unlike {@link DagNodeGraphConverter} (which must re-wrap a {@code
 * formula} node's {@code expression} config back into a real {@code
 * JsonNode} so the graph can be RE-EXECUTED), {@code waterfall_steps} is
 * read-only audit data — it is never fed back into a {@link
 * com.cpq.core.pricing.dag.DagEngine} run, so no such normalization is
 * needed here (see docs/decisions.md D-046).
 */
class TraceEntryListConverterTest {

    private final TraceEntryListConverter converter = new TraceEntryListConverter();

    @Test
    void nullListRoundTripsToNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void roundTripsASimpleTraceEntryListUnchanged() {
        List<TraceEntry> trace =
                List.of(
                        new TraceEntry("q1", "query", Map.of(), new BigDecimal("40.00")),
                        new TraceEntry(
                                "r1",
                                "round",
                                Map.of("value", new BigDecimal("40.00")),
                                new BigDecimal("40.00")));

        String json = converter.convertToDatabaseColumn(trace);
        assertThat(json).contains("\"nodeId\":\"q1\"").contains("40.00");

        List<TraceEntry> roundTripped = converter.convertToEntityAttribute(json);
        assertThat(roundTripped).isEqualTo(trace);
    }

    @Test
    void preservesBigDecimalOutputAndResolvedInputsAcrossTheRoundTrip() {
        List<TraceEntry> trace =
                List.of(
                        new TraceEntry(
                                "weighted",
                                "formula",
                                Map.of("cost", new BigDecimal("100.00"), "margin", new BigDecimal("0.10")),
                                new BigDecimal("110.000")));

        String json = converter.convertToDatabaseColumn(trace);
        List<TraceEntry> roundTripped = converter.convertToEntityAttribute(json);

        TraceEntry entry = roundTripped.get(0);
        assertThat(entry.output()).isInstanceOf(BigDecimal.class);
        assertThat((BigDecimal) entry.output()).isEqualByComparingTo("110.000");
        assertThat(entry.resolvedInputs().get("cost")).isInstanceOf(BigDecimal.class);
        assertThat((BigDecimal) entry.resolvedInputs().get("cost")).isEqualByComparingTo("100.00");
    }

    @Test
    void roundTripsAnOutputNodesMapOutputUnchanged() {
        List<TraceEntry> trace =
                List.of(
                        new TraceEntry(
                                "out",
                                "output",
                                Map.of("finalCost", new BigDecimal("42.00")),
                                Map.of("finalCost", new BigDecimal("42.00"))));

        String json = converter.convertToDatabaseColumn(trace);
        List<TraceEntry> roundTripped = converter.convertToEntityAttribute(json);

        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) roundTripped.get(0).output();
        assertThat((BigDecimal) output.get("finalCost")).isEqualByComparingTo("42.00");
    }
}
