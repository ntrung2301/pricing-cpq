package com.cpq.app.pack;

import java.util.List;

/**
 * Generic top-level shape of one {@code pack.yaml} file (ADR-001). Core
 * (and {@code PackLoaderService}) only understands this generic structure
 * — {@code entities[]}/{@code relationships[]} — never any specific
 * domain's content. {@code ruleSets}/{@code importMappings}/{@code
 * uiLabels} from ADR-001's full pack.yaml shape are out of scope for
 * S2-T1 (later tasks: S2-T5/S2-T10 and beyond) and intentionally not
 * modeled here yet.
 */
public record PackDescriptor(
        String packId, int version, List<EntityDescriptor> entities, List<RelationshipDescriptor> relationships) {

    public PackDescriptor {
        entities = entities == null ? List.of() : List.copyOf(entities);
        relationships = relationships == null ? List.of() : List.copyOf(relationships);
    }
}
