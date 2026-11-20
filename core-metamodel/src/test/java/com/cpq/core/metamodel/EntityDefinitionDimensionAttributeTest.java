package com.cpq.core.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Plain-unit (no DB) tests for {@link EntityDefinitionDimensionAttribute}
 * (ADR-009 mục 1, S3-T1): one ordered "dimension" attribute (e.g.
 * {@code period}, {@code city}) of an {@link EntityRole#EXTENSION} entity
 * definition, used together with the anchor instance's own business key to
 * assemble a composite business key (D-039).
 */
class EntityDefinitionDimensionAttributeTest {

    @Test
    void holdsEntityDefinitionAttributeDefinitionAndPosition() {
        EntityDefinition extension = new EntityDefinition("GADGET_HISTORY", "Gadget History", 1);
        AttributeDefinition period =
                new AttributeDefinition("period", "Period", AttributeDataType.STRING, true, null);
        extension.addAttribute(period);

        EntityDefinitionDimensionAttribute dimension =
                new EntityDefinitionDimensionAttribute(extension, period, 0);

        assertThat(dimension.getEntityDefinition()).isSameAs(extension);
        assertThat(dimension.getAttributeDefinition()).isSameAs(period);
        assertThat(dimension.getPosition()).isZero();
    }
}
