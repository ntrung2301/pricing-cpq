package com.cpq.app.pack;

import com.cpq.core.metamodel.AttributeDataType;

/**
 * One {@code attributes[]} entry of an entity in {@code pack.yaml}
 * (ADR-001). Deserialized from YAML (snake_case keys — see
 * {@code PackLoaderService}'s {@code ObjectMapper}), e.g.:
 *
 * <pre>
 * - code: sku
 *   label: SKU
 *   data_type: STRING
 *   required: true
 *   external_code_hint: "PIES:PartNumber"
 * </pre>
 */
public record AttributeDescriptor(
        String code, String label, AttributeDataType dataType, boolean required, String externalCodeHint) {}
