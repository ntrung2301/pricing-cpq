package com.cpq.app.pack;

import java.util.List;

/**
 * One {@code entities[]} entry of {@code pack.yaml} (ADR-001). {@code
 * keyAttribute}/{@code labelAttribute} (D-019) are attribute codes
 * (must match one of {@code attributes[].code}) designating the
 * generic business-key/display-label attribute. Deserialized from YAML
 * (snake_case keys), e.g.:
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
 */
public record EntityDescriptor(
        String code, String label, String keyAttribute, String labelAttribute, List<AttributeDescriptor> attributes) {

    public EntityDescriptor {
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }
}
