package com.cpq.app.pricing.dag;

import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.app.metamodel.EntityInstanceAttributeValueRepository;
import com.cpq.app.metamodel.EntityInstanceRepository;
import com.cpq.app.pricing.CalculationBindingRepository;
import com.cpq.core.metamodel.AttributeDataType;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityInstance;
import com.cpq.core.metamodel.EntityInstanceAttributeValue;
import com.cpq.core.pricing.dag.CalculationBindingFilterOperator;
import com.cpq.core.pricing.dag.CalculationBindingSortDirection;
import com.cpq.core.pricing.dag.DagNode;
import com.cpq.core.pricing.dag.DagNodeExecutor;
import com.cpq.core.pricing.dag.DagValidationException;
import com.cpq.core.pricing.persistence.CalculationBindingEntity;
import com.cpq.core.pricing.persistence.CalculationBindingFilterEntity;
import com.cpq.core.pricing.persistence.CalculationBindingSortEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DAG node type {@code query} (ADR-009 mục 2/3, backlog E8-03/E8-03b,
 * S3-T3): resolves one value for the {@link DagNode} it's attached to by
 * reading a {@link CalculationBindingEntity} — the generic "Context
 * Resolver" ADR-009 mục 3 designs — and applying its
 * filter -> sort -> take-first pipeline.
 *
 * <p>Implements {@link DagNodeExecutor} (core-pricing, {@code
 * DagEngine}'s SPI) but — unlike {@code FormulaNodeExecutor}/{@code
 * RoundNodeExecutor} etc. (core-pricing, no JPA) — lives in {@code app}
 * because it reads the database (ADR-005: {@code app} owns DB-facing
 * wiring). It is <b>not</b> registered in {@code
 * DagNodeExecutors.standard()} (core-pricing has no JPA dependency and
 * must not gain one); callers instead merge it into their own {@code
 * Map<String, DagNodeExecutor>} before constructing a {@code DagEngine}
 * (see {@code com.cpq.app.pricing.dag.AppDagNodeExecutors}) — same glue-layer
 * pattern as {@code ConstraintInstanceEvaluationService} for {@code
 * core-constraint-engine}.
 *
 * <p><b>"Anchor instance currently being calculated" (docs/decisions.md
 * D-042, chosen default (a)):</b> {@code DagEngine.execute(List)} (S3-T2)
 * takes no external context parameter, by design (it was out of scope to
 * change for this task). The caller that builds the graph is responsible
 * for "baking" the id of the anchor instance being priced directly into
 * every {@code query} node's {@code config} under the key {@code
 * "anchorInstanceId"} — this executor only reads it from there, never
 * hardcodes or infers it. Generalising this (so a graph/Measure isn't tied
 * to one hardcoded instance) is S3-T5's job, not this one's — see D-042.
 */
@Service
public class QueryNodeExecutor implements DagNodeExecutor {

    static final String CONFIG_BINDING_ATTRIBUTE_CODE = "bindingAttributeCode";
    static final String CONFIG_ANCHOR_INSTANCE_ID = "anchorInstanceId";

    private final EntityInstanceRepository entityInstanceRepository;
    private final EntityDefinitionRepository entityDefinitionRepository;
    private final EntityInstanceAttributeValueRepository attributeValueRepository;
    private final CalculationBindingRepository calculationBindingRepository;

    public QueryNodeExecutor(
            EntityInstanceRepository entityInstanceRepository,
            EntityDefinitionRepository entityDefinitionRepository,
            EntityInstanceAttributeValueRepository attributeValueRepository,
            CalculationBindingRepository calculationBindingRepository) {
        this.entityInstanceRepository = entityInstanceRepository;
        this.entityDefinitionRepository = entityDefinitionRepository;
        this.attributeValueRepository = attributeValueRepository;
        this.calculationBindingRepository = calculationBindingRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Object execute(DagNode node, Map<String, Object> resolvedInputs) {
        String bindingAttributeCode = requireConfigString(node, CONFIG_BINDING_ATTRIBUTE_CODE);
        CalculationBindingEntity binding =
                calculationBindingRepository
                        .findByAttributeCode(bindingAttributeCode)
                        .orElseThrow(
                                () ->
                                        new DagValidationException(
                                                "Query node '"
                                                        + node.id()
                                                        + "' references unknown calculation binding '"
                                                        + bindingAttributeCode
                                                        + "'"));

        return switch (binding.getSourceType()) {
            case PRODUCT, CUSTOMER -> resolveFromAnchorDirectly(node, binding);
            case EXTENSION -> resolveFromExtension(node, binding, resolvedInputs);
            case PARAMETER -> resolveFromParameter(binding, resolvedInputs);
        };
    }

    /**
     * {@code PRODUCT}/{@code CUSTOMER} (ADR-009 mục 3): read {@code
     * sourceAttributeCode} directly off the anchor instance currently
     * being calculated — no filter/sort, generic over which of the two
     * fixed anchors it actually is (this executor never hardcodes
     * "Product"/"Customer" as entity_def codes).
     */
    private Object resolveFromAnchorDirectly(DagNode node, CalculationBindingEntity binding) {
        EntityInstance anchor = requireAnchorInstance(node);
        AttributeDefinition attribute = findAttribute(anchor.getEntityDefinition(), binding.getSourceAttributeCode());
        return readAttributeValueOrNull(anchor, attribute);
    }

    /**
     * {@code EXTENSION}: candidate rows auto-match the anchor instance
     * currently being calculated ({@code anchor_instance_id}), then
     * filter -> sort -> take-first (ADR-009 mục 3).
     */
    private Object resolveFromExtension(
            DagNode node, CalculationBindingEntity binding, Map<String, Object> resolvedInputs) {
        EntityInstance anchor = requireAnchorInstance(node);
        List<EntityInstance> candidates =
                entityInstanceRepository.findByEntityDefinition_CodeAndAnchorInstanceOrderByIdAsc(
                        binding.getSourceEntityCode(), anchor);
        return resolveFromCandidates(binding, candidates, resolvedInputs);
    }

    /**
     * {@code PARAMETER}: an independent entity, not auto-matched to any
     * anchor — only the declared filters narrow its candidate rows
     * (ADR-009 mục 3).
     */
    private Object resolveFromParameter(CalculationBindingEntity binding, Map<String, Object> resolvedInputs) {
        List<EntityInstance> candidates =
                entityInstanceRepository.findByEntityDefinition_CodeOrderByIdAsc(binding.getSourceEntityCode());
        return resolveFromCandidates(binding, candidates, resolvedInputs);
    }

    /**
     * Shared {@code EXTENSION}/{@code PARAMETER} pipeline: filter (AND) ->
     * sort (only if still >=2 rows) -> take-first (ADR-009 mục 3). 0 rows
     * after filter -> {@code null} (not an error). >=2 rows after
     * filter+sort and {@code take_first_after_sort == false} -> {@link
     * CalculationBindingAmbiguousException}, evaluated fresh on every
     * call (not cached), so source data changing between two calls is
     * always caught at the call that makes it ambiguous.
     */
    private Object resolveFromCandidates(
            CalculationBindingEntity binding, List<EntityInstance> candidates, Map<String, Object> resolvedInputs) {
        EntityDefinition sourceDefinition =
                entityDefinitionRepository
                        .findByCodeWithAttributes(binding.getSourceEntityCode())
                        .orElseThrow(
                                () ->
                                        new DagValidationException(
                                                "Calculation binding '"
                                                        + binding.getAttributeCode()
                                                        + "' references unknown entity definition '"
                                                        + binding.getSourceEntityCode()
                                                        + "'"));

        List<EntityInstance> filtered = applyFilters(binding, sourceDefinition, candidates, resolvedInputs);
        if (filtered.isEmpty()) {
            return null;
        }
        if (filtered.size() == 1) {
            return readSourceAttribute(binding, sourceDefinition, filtered.get(0));
        }

        List<EntityInstance> sorted = applySort(binding, sourceDefinition, filtered);
        if (!binding.isTakeFirstAfterSort()) {
            throw new CalculationBindingAmbiguousException(binding.getAttributeCode(), sorted.size());
        }
        return readSourceAttribute(binding, sourceDefinition, sorted.get(0));
    }

    private Object readSourceAttribute(
            CalculationBindingEntity binding, EntityDefinition sourceDefinition, EntityInstance instance) {
        AttributeDefinition attribute = findAttribute(sourceDefinition, binding.getSourceAttributeCode());
        return readAttributeValueOrNull(instance, attribute);
    }

    private List<EntityInstance> applyFilters(
            CalculationBindingEntity binding,
            EntityDefinition sourceDefinition,
            List<EntityInstance> candidates,
            Map<String, Object> resolvedInputs) {
        List<EntityInstance> result = candidates;
        for (CalculationBindingFilterEntity filter : binding.getFilters()) {
            AttributeDefinition attribute = findAttribute(sourceDefinition, filter.getFieldCode());
            Object comparisonValue = resolveComparisonValue(binding, filter, attribute, resolvedInputs);
            List<EntityInstance> next = new ArrayList<>();
            for (EntityInstance candidate : result) {
                Object rawValue = readAttributeValueOrNull(candidate, attribute);
                if (matches(rawValue, filter.getOperator(), comparisonValue)) {
                    next.add(candidate);
                }
            }
            result = next;
        }
        return result;
    }

    private Object resolveComparisonValue(
            CalculationBindingEntity binding,
            CalculationBindingFilterEntity filter,
            AttributeDefinition attribute,
            Map<String, Object> resolvedInputs) {
        return switch (filter.getValueSource()) {
            case CONSTANT -> parseConstantValue(filter, attribute);
            case CONTEXT_ATTRIBUTE -> resolvedInputs.get(filter.getValue());
            case REQUEST_PARAM ->
                    throw new DagValidationException(
                            "Calculation binding '"
                                    + binding.getAttributeCode()
                                    + "' filter on '"
                                    + filter.getFieldCode()
                                    + "' uses value_source REQUEST_PARAM, which is not implemented yet"
                                    + " (NEEDS-PO-REVIEW — no request-level parameter is plumbed into"
                                    + " DagEngine.execute() before S3-T5, see docs/decisions.md D-042)");
        };
    }

    private Object parseConstantValue(CalculationBindingFilterEntity filter, AttributeDefinition attribute) {
        if (filter.getOperator() == CalculationBindingFilterOperator.IN) {
            List<Object> values = new ArrayList<>();
            for (String token : filter.getValue().split(",")) {
                values.add(parseScalar(token.trim(), attribute.getDataType()));
            }
            return values;
        }
        return parseScalar(filter.getValue(), attribute.getDataType());
    }

    private Object parseScalar(String raw, AttributeDataType dataType) {
        return switch (dataType) {
            case STRING, ENUM, JSON -> raw;
            case NUMBER, DECIMAL -> new BigDecimal(raw);
            case DATE -> LocalDate.parse(raw);
            case BOOLEAN -> Boolean.parseBoolean(raw);
            case REFERENCE -> Long.parseLong(raw);
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean matches(Object rawValue, CalculationBindingFilterOperator operator, Object comparisonValue) {
        if (rawValue == null) {
            return false;
        }
        if (operator == CalculationBindingFilterOperator.IN) {
            List<?> values = comparisonValue instanceof List<?> list ? list : List.of(comparisonValue);
            for (Object value : values) {
                if (((Comparable) rawValue).compareTo(value) == 0) {
                    return true;
                }
            }
            return false;
        }
        int cmp = ((Comparable) rawValue).compareTo(comparisonValue);
        return switch (operator) {
            case EQ -> cmp == 0;
            case NE -> cmp != 0;
            case LT -> cmp < 0;
            case LTE -> cmp <= 0;
            case GT -> cmp > 0;
            case GTE -> cmp >= 0;
            case IN -> throw new IllegalStateException("IN handled above");
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<EntityInstance> applySort(
            CalculationBindingEntity binding, EntityDefinition sourceDefinition, List<EntityInstance> candidates) {
        List<CalculationBindingSortEntity> sorts = new ArrayList<>(binding.getSorts());
        sorts.sort(Comparator.comparingInt(CalculationBindingSortEntity::getSortOrder));
        if (sorts.isEmpty()) {
            return candidates;
        }

        Comparator<EntityInstance> comparator = null;
        for (CalculationBindingSortEntity sort : sorts) {
            AttributeDefinition attribute = findAttribute(sourceDefinition, sort.getFieldCode());
            Comparator<EntityInstance> fieldComparator =
                    Comparator.comparing(
                            (EntityInstance instance) -> (Comparable) readAttributeValueOrNull(instance, attribute),
                            Comparator.nullsLast(Comparator.naturalOrder()));
            if (sort.getDirection() == CalculationBindingSortDirection.DESC) {
                fieldComparator = fieldComparator.reversed();
            }
            comparator = comparator == null ? fieldComparator : comparator.thenComparing(fieldComparator);
        }

        List<EntityInstance> sorted = new ArrayList<>(candidates);
        sorted.sort(comparator);
        return sorted;
    }

    private EntityInstance requireAnchorInstance(DagNode node) {
        Object raw = node.config().get(CONFIG_ANCHOR_INSTANCE_ID);
        if (raw == null) {
            throw new DagValidationException(
                    "Query node '" + node.id() + "' requires config '" + CONFIG_ANCHOR_INSTANCE_ID + "'");
        }
        Long anchorInstanceId = toLong(node, raw);
        return entityInstanceRepository
                .findById(anchorInstanceId)
                .orElseThrow(
                        () ->
                                new DagValidationException(
                                        "Query node '"
                                                + node.id()
                                                + "' anchorInstanceId "
                                                + anchorInstanceId
                                                + " does not exist"));
    }

    private Long toLong(DagNode node, Object raw) {
        if (raw instanceof Long value) {
            return value;
        }
        if (raw instanceof Integer value) {
            return value.longValue();
        }
        if (raw instanceof String value) {
            return Long.parseLong(value);
        }
        throw new DagValidationException(
                "Query node '" + node.id() + "' config '" + CONFIG_ANCHOR_INSTANCE_ID + "' is not a valid id: " + raw);
    }

    private String requireConfigString(DagNode node, String key) {
        Object value = node.config().get(key);
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new DagValidationException(
                    "Query node '" + node.id() + "' requires a non-blank string config '" + key + "'");
        }
        return stringValue;
    }

    private AttributeDefinition findAttribute(EntityDefinition entityDefinition, String code) {
        return entityDefinition.getAttributes().stream()
                .filter(attribute -> attribute.getCode().equals(code))
                .findFirst()
                .orElseThrow(
                        () ->
                                new DagValidationException(
                                        "Entity definition '"
                                                + entityDefinition.getCode()
                                                + "' has no attribute '"
                                                + code
                                                + "'"));
    }

    /**
     * Reads {@code attribute}'s value for {@code instance}, or {@code
     * null} if it was never written — same "0 dòng/giá trị -> null, not an
     * error" convention as the rest of Calculation Binding (ADR-009 mục
     * 3). Picks whichever single typed column is populated (ADR-001's
     * typed EAV guarantees exactly one), same shapes as {@code
     * ConstraintInstanceEvaluationService.rawValue}.
     */
    private Object readAttributeValueOrNull(EntityInstance instance, AttributeDefinition attribute) {
        return attributeValueRepository
                .findByEntityInstanceAndAttributeDefinition(instance, attribute)
                .map(this::rawValue)
                .orElse(null);
    }

    private Object rawValue(EntityInstanceAttributeValue row) {
        if (row.getValueString() != null) {
            return row.getValueString();
        }
        if (row.getValueNumber() != null) {
            return row.getValueNumber();
        }
        if (row.getValueDate() != null) {
            return row.getValueDate();
        }
        if (row.getValueBoolean() != null) {
            return row.getValueBoolean();
        }
        return row.getValueRefInstanceId();
    }
}
