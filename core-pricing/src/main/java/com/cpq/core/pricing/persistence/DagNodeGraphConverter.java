package com.cpq.core.pricing.persistence;

import com.cpq.core.pricing.dag.DagNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JPA {@link AttributeConverter} for {@code measure_definition.graph}
 * (S3-T5, ADR-009 mục 2 "Measure"): a {@code List<DagNode>} stored as a
 * {@code jsonb} column, serialized/deserialized with Jackson directly
 * against the {@link DagNode} record — same technique {@code
 * PackLoaderService} uses Jackson for (there, YAML; here, the database's
 * native JSON), per this task's instructions. Paired with {@code
 * @JdbcTypeCode(SqlTypes.JSON)} on the owning entity attribute (see {@code
 * MeasureDefinitionEntity}) so Hibernate binds the converted {@code String}
 * through PostgreSQL's JSON JDBC type instead of plain {@code varchar}
 * (otherwise insert fails: "column is of type jsonb but expression is of
 * type character varying").
 *
 * <p>{@code USE_BIG_DECIMAL_FOR_FLOATS} mirrors {@code DslRuleExecutor}'s
 * {@code ObjectMapper} configuration (core-pricing/dsl) — money/ratio
 * config values (e.g. a {@code round} node's {@code nearest}) must
 * deserialize as {@link java.math.BigDecimal}, never {@code Double}
 * (CLAUDE.md mục 7).
 *
 * <p><b>{@code formula} node normalization:</b> {@code
 * FormulaNodeExecutor} (core-pricing/dag, S3-T2, not touched by this task)
 * requires its {@code expression} config entry to be an actual {@code
 * com.fasterxml.jackson.databind.JsonNode} instance — that is how every
 * in-Java-code graph in this codebase builds one (see {@code
 * DagEngineTest}/{@code FormulaNodeExecutorTest}: {@code
 * Map.of("expression", someJsonNode)}). Jackson's generic {@code
 * Map<String,Object>} deserialization instead hands back a plain {@code
 * LinkedHashMap}/scalar for that same JSON value, which would silently
 * break {@code instanceof JsonNode} for every {@code formula} node inside
 * any graph loaded back from this converter (not just a Measure's — the
 * same shape will recur for Price List's {@code calculation_rule_set} in
 * S3-T6). Rather than touch {@code FormulaNodeExecutor} itself (out of
 * scope, and it would need to accept plain Maps for in-Java-code callers
 * too), this converter re-wraps exactly that one config entry via {@code
 * objectMapper.valueToTree(...)} on read, for {@code formula} nodes only —
 * a minimal, localized normalization at the one boundary (JSON <-> Java)
 * that actually loses the type.
 */
@Converter
public class DagNodeGraphConverter implements AttributeConverter<List<DagNode>, String> {

    private static final String FORMULA_NODE_TYPE = "formula";
    private static final String FORMULA_EXPRESSION_CONFIG_KEY = "expression";

    private final ObjectMapper objectMapper =
            new ObjectMapper().configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);

    @Override
    public String convertToDatabaseColumn(List<DagNode> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize DAG graph to JSON", e);
        }
    }

    @Override
    public List<DagNode> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            List<DagNode> raw = objectMapper.readValue(dbData, new TypeReference<List<DagNode>>() {});
            return normalizeFormulaExpressions(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize DAG graph JSON: " + dbData, e);
        }
    }

    /**
     * Re-wraps {@code formula} nodes' {@code expression} config entry as a
     * real {@link JsonNode} (see class Javadoc) — every other node type's
     * config is left exactly as Jackson's generic deserialization produced
     * it.
     */
    private List<DagNode> normalizeFormulaExpressions(List<DagNode> nodes) {
        List<DagNode> result = new ArrayList<>(nodes.size());
        for (DagNode node : nodes) {
            Object expression = node.config().get(FORMULA_EXPRESSION_CONFIG_KEY);
            if (FORMULA_NODE_TYPE.equals(node.type()) && expression != null && !(expression instanceof JsonNode)) {
                Map<String, Object> patchedConfig = new LinkedHashMap<>(node.config());
                patchedConfig.put(FORMULA_EXPRESSION_CONFIG_KEY, objectMapper.valueToTree(expression));
                result.add(new DagNode(node.id(), node.type(), patchedConfig, node.inputs()));
            } else {
                result.add(node);
            }
        }
        return result;
    }
}
