package com.cpq.core.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Plain-unit (no DB) tests for the {@code anchorInstance} pointer on
 * {@link EntityInstance} (ADR-009 mục 1, S3-T1): only meaningful when the
 * instance's entity definition has {@link EntityRole#EXTENSION} — points at
 * the anchor (e.g. {@code Product}/{@code Customer}) instance it extends.
 */
class EntityInstanceAnchorTest {

    @Test
    void hasNoAnchorInstanceByDefault() {
        EntityDefinition widget = new EntityDefinition("WIDGET", "Widget", 1);
        EntityInstance instance = new EntityInstance(widget, "test-pack", 1);

        assertThat(instance.getAnchorInstance()).isNull();
    }

    @Test
    void assignsAnAnchorInstance() {
        EntityDefinition anchorDef = new EntityDefinition("GADGET", "Gadget", 1);
        anchorDef.assignRole(EntityRole.ANCHOR);
        EntityInstance anchorInstance = new EntityInstance(anchorDef, "test-pack", 1);

        EntityDefinition extensionDef = new EntityDefinition("GADGET_HISTORY", "Gadget History", 1);
        extensionDef.assignRole(EntityRole.EXTENSION);
        extensionDef.assignExtendsEntityDefinition(anchorDef);
        EntityInstance extensionInstance = new EntityInstance(extensionDef, "test-pack", 1);

        extensionInstance.assignAnchorInstance(anchorInstance);

        assertThat(extensionInstance.getAnchorInstance()).isSameAs(anchorInstance);
    }
}
