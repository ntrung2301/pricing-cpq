package com.cpq.core.metamodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Plain-unit (no DB) tests for the D-019 key/label attribute mechanism on
 * {@link EntityDefinition}: core only knows the generic "key"/"label"
 * concept, and an attribute must belong to the entity definition it is
 * being designated key/label for.
 */
class EntityDefinitionKeyLabelTest {

    @Test
    void designatesAnOwnedAttributeAsKeyAndLabel() {
        EntityDefinition entityDefinition = new EntityDefinition("WIDGET", "Widget", 1);
        AttributeDefinition sku = new AttributeDefinition("sku", "SKU", AttributeDataType.STRING, true, null);
        AttributeDefinition name = new AttributeDefinition("name", "Name", AttributeDataType.STRING, true, null);
        entityDefinition.addAttribute(sku);
        entityDefinition.addAttribute(name);

        entityDefinition.designateKeyAttribute(sku);
        entityDefinition.designateLabelAttribute(name);

        assertThat(entityDefinition.getKeyAttributeDefinition()).isSameAs(sku);
        assertThat(entityDefinition.getLabelAttributeDefinition()).isSameAs(name);
    }

    @Test
    void rejectsDesignatingAnAttributeThatBelongsToAnotherEntityDefinitionAsKey() {
        EntityDefinition widget = new EntityDefinition("WIDGET", "Widget", 1);
        EntityDefinition otherWidget = new EntityDefinition("OTHER_WIDGET", "Other Widget", 1);
        AttributeDefinition otherSku =
                new AttributeDefinition("sku", "SKU", AttributeDataType.STRING, true, null);
        otherWidget.addAttribute(otherSku);

        assertThatThrownBy(() -> widget.designateKeyAttribute(otherSku))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDesignatingAnAttributeThatBelongsToAnotherEntityDefinitionAsLabel() {
        EntityDefinition widget = new EntityDefinition("WIDGET", "Widget", 1);
        EntityDefinition otherWidget = new EntityDefinition("OTHER_WIDGET", "Other Widget", 1);
        AttributeDefinition otherName =
                new AttributeDefinition("name", "Name", AttributeDataType.STRING, true, null);
        otherWidget.addAttribute(otherName);

        assertThatThrownBy(() -> widget.designateLabelAttribute(otherName))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assignsPackIdAndVersion() {
        EntityDefinition entityDefinition = new EntityDefinition("WIDGET", "Widget", 1);

        entityDefinition.assignPack("synthetic-test-pack", 3);

        assertThat(entityDefinition.getPackId()).isEqualTo("synthetic-test-pack");
        assertThat(entityDefinition.getPackVersion()).isEqualTo(3);
    }
}
