package com.cpq.app.pack;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * One {@code entities[]} entry of {@code pack.yaml} (ADR-001, amended by
 * ADR-009 mục 1/D-039 for the {@code role}/{@code extends}/{@code
 * dimension_attributes} fields). {@code keyAttribute}/{@code labelAttribute}
 * (D-019) are attribute codes (must match one of {@code attributes[].code})
 * designating the generic business-key/display-label attribute.
 * Deserialized from YAML (snake_case keys), e.g.:
 *
 * <pre>
 * - code: Product
 *   label: Product
 *   key_attribute: sku
 *   label_attribute: name
 *   attributes:
 *     - code: sku
 *       ...
 * </pre>
 *
 * <p>{@code role} (optional — {@code "ANCHOR"}/{@code "EXTENSION"}/{@code
 * "PARAMETER"}, matching {@link com.cpq.core.metamodel.EntityRole}; null
 * when a pack doesn't declare one, preserving the pre-S3-T1 behaviour —
 * see {@code PackLoaderService} for the full D-039 rules), {@code
 * extendsEntityCode} (the {@code extends:} YAML key — named differently in
 * Java because {@code extends} is a reserved word; only meaningful when
 * {@code role == "EXTENSION"}), and {@code dimensionAttributes} (ordered
 * attribute codes, must each match one of this entity's own declared
 * attributes; only meaningful when {@code role == "EXTENSION"}), e.g.:
 *
 * <pre>
 * - code: CostHistory
 *   label: Cost History
 *   role: EXTENSION
 *   extends: Product
 *   dimension_attributes: [period]
 *   attributes:
 *     - code: period
 *       ...
 * </pre>
 */
public record EntityDescriptor(
        String code,
        String label,
        String keyAttribute,
        String labelAttribute,
        List<AttributeDescriptor> attributes,
        String role,
        @JsonProperty("extends") String extendsEntityCode,
        List<String> dimensionAttributes) {

    public EntityDescriptor {
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
        dimensionAttributes = dimensionAttributes == null ? List.of() : List.copyOf(dimensionAttributes);
    }
}
