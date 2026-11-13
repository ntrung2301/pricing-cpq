package com.cpq.core.constraintengine;

import java.util.Map;

/**
 * Maps generic aliases (e.g. {@code "x"}, {@code "y"}) to the
 * {@link AttributeBag} holding that side's attribute values, for
 * resolving {@code {"var": "<alias>.<attributeCode>"}} references in a
 * condition (ADR-002, E1-02). Core only knows aliases as arbitrary
 * strings — it never knows whether "x" means a "Part" or a "Vehicle" or
 * anything else; the domain pack only shapes which attribute codes exist
 * inside each bag.
 */
public final class EvaluationContext {

    private final Map<String, AttributeBag> bagsByAlias;

    private EvaluationContext(Map<String, AttributeBag> bagsByAlias) {
        this.bagsByAlias = bagsByAlias;
    }

    public static EvaluationContext of(Map<String, AttributeBag> bagsByAlias) {
        return new EvaluationContext(Map.copyOf(bagsByAlias));
    }

    /**
     * Resolves a {@code "<alias>.<attributeCode>"} variable path. Throws
     * {@link ConditionEvaluationException} if the alias is unknown —
     * deterministic rejection rather than silently treating a typo as
     * "null"/"false" (CLAUDE.md mục 7: pricing/constraint evaluation must
     * be deterministic and auditable, not silently wrong).
     */
    Object resolveVar(String path) {
        int dot = path.indexOf('.');
        if (dot < 0) {
            throw new ConditionEvaluationException(
                    "Variable path must be '<alias>.<attributeCode>', got: " + path);
        }
        String alias = path.substring(0, dot);
        String attributeCode = path.substring(dot + 1);
        AttributeBag bag = bagsByAlias.get(alias);
        if (bag == null) {
            throw new ConditionEvaluationException("Unknown alias in condition: " + alias);
        }
        return bag.get(attributeCode);
    }
}
