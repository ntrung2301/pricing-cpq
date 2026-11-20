package com.cpq.core.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Plain-unit (no DB) tests for the entity taxonomy v2 mechanism on
 * {@link EntityDefinition} (ADR-009 mục 1, D-037/D-038/D-039, S3-T1): a new
 * {@code role} ({@link EntityRole#ANCHOR}/{@link EntityRole#EXTENSION}/
 * {@link EntityRole#PARAMETER}) and, when {@code role == EXTENSION}, a
 * pointer to the anchor (or any other) entity definition it extends.
 * Core only knows the generic taxonomy concept — it has no idea whether a
 * given {@code EXTENSION} entity def represents "CostHistory" or anything
 * else.
 */
class EntityDefinitionTaxonomyTest {

    @Test
    void hasNoRoleByDefault() {
        EntityDefinition entityDefinition = new EntityDefinition("WIDGET", "Widget", 1);

        assertThat(entityDefinition.getRole()).isNull();
        assertThat(entityDefinition.getExtendsEntityDefinition()).isNull();
    }

    @Test
    void assignsAnchorRole() {
        EntityDefinition entityDefinition = new EntityDefinition("PRODUCT", "Product", 1);

        entityDefinition.assignRole(EntityRole.ANCHOR);

        assertThat(entityDefinition.getRole()).isEqualTo(EntityRole.ANCHOR);
    }

    @Test
    void assignsExtensionRoleWithAnExtendsPointer() {
        EntityDefinition anchor = new EntityDefinition("GADGET", "Gadget", 1);
        anchor.assignRole(EntityRole.ANCHOR);
        EntityDefinition extension = new EntityDefinition("GADGET_HISTORY", "Gadget History", 1);

        extension.assignRole(EntityRole.EXTENSION);
        extension.assignExtendsEntityDefinition(anchor);

        assertThat(extension.getRole()).isEqualTo(EntityRole.EXTENSION);
        assertThat(extension.getExtendsEntityDefinition()).isSameAs(anchor);
    }

    @Test
    void assignsParameterRoleWithNoExtendsPointer() {
        EntityDefinition entityDefinition = new EntityDefinition("MARGIN_THRESHOLD", "Margin Threshold", 1);

        entityDefinition.assignRole(EntityRole.PARAMETER);

        assertThat(entityDefinition.getRole()).isEqualTo(EntityRole.PARAMETER);
        assertThat(entityDefinition.getExtendsEntityDefinition()).isNull();
    }
}
