package com.cpq.app.metamodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.cpq.core.metamodel.AttributeDataType;
import com.cpq.core.metamodel.AttributeDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the one non-trivial validation rule in
 * {@link MetamodelDefinitionService}: an {@code EntityDefinition} must not
 * be created with two {@code AttributeDefinition}s sharing the same
 * {@code code} (attribute_def has a unique (entity_def_id, code)
 * constraint at the DB level too — see
 * {@code V2__metamodel_definitions.sql} — but failing fast in the service
 * gives a clear domain error instead of a DB constraint-violation
 * exception). Uses a mocked repository, no database needed.
 */
@ExtendWith(MockitoExtension.class)
class MetamodelDefinitionServiceTest {

    @Mock private EntityDefinitionRepository entityDefinitionRepository;

    @Test
    void rejectsDuplicateAttributeCodeWithinSameEntityDefinition() {
        MetamodelDefinitionService service =
                new MetamodelDefinitionService(entityDefinitionRepository);

        AttributeDefinition first =
                new AttributeDefinition("sku", "SKU", AttributeDataType.STRING, true, null);
        AttributeDefinition duplicate =
                new AttributeDefinition(
                        "sku", "SKU (duplicate)", AttributeDataType.STRING, false, null);

        assertThatThrownBy(
                        () ->
                                service.defineEntity(
                                        "TEST_WIDGET",
                                        "Test Widget",
                                        1,
                                        List.of(first, duplicate)))
                .isInstanceOf(DuplicateAttributeCodeException.class)
                .hasMessageContaining("sku");

        verify(entityDefinitionRepository, never()).save(any());
    }

    @Test
    void duplicateAttributeCodeExceptionCarriesTheOffendingCode() {
        DuplicateAttributeCodeException exception = new DuplicateAttributeCodeException("sku");

        assertThat(exception.getMessage()).contains("sku");
    }
}
