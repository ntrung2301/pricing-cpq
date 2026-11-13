package com.cpq.core.constraintengine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A generic, named bag of attribute values for one side of a condition
 * evaluation (E1-02, S2-T2) — e.g. "the attribute values of entity
 * instance X". Values are raw Java objects mirroring the five value
 * shapes core-metamodel's {@code AttributeValue} already knows
 * ({@code String}/{@code BigDecimal}/{@code Boolean}/{@code LocalDate}/
 * {@code Long} for a reference id), but this class deliberately does not
 * depend on core-metamodel — it has no idea the values came from an
 * {@code EntityInstance}'s typed EAV rows. The caller (app) is
 * responsible for building one of these per alias before calling
 * {@link ConstraintEngine#evaluate}.
 */
public final class AttributeBag {

    private final Map<String, Object> values;

    private AttributeBag(Map<String, Object> values) {
        this.values = values;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Returns the raw value for {@code attributeCode}, or {@code null} if not present. */
    public Object get(String attributeCode) {
        return values.get(attributeCode);
    }

    public boolean has(String attributeCode) {
        return values.containsKey(attributeCode);
    }

    public static final class Builder {
        private final Map<String, Object> values = new LinkedHashMap<>();

        public Builder put(String attributeCode, Object value) {
            values.put(attributeCode, value);
            return this;
        }

        public AttributeBag build() {
            return new AttributeBag(Map.copyOf(values));
        }
    }
}
