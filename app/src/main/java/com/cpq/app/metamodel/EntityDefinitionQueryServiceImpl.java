package com.cpq.app.metamodel;

import com.cpq.core.api.entities.AttributeSummary;
import com.cpq.core.api.entities.EntityDefinitionQueryService;
import com.cpq.core.api.entities.EntityDefinitionSummary;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.EntityDefinition;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code app}-side implementation of {@code core-api}'s
 * {@link EntityDefinitionQueryService} port (S1-T8), analogous to
 * {@link MetamodelDefinitionService} (S1-T7): lives in {@code app} because
 * it needs {@link EntityDefinitionRepository}, which per ADR-005 is
 * composition-root-only (Spring Data JPA wiring). Maps the JPA
 * {@link EntityDefinition}/{@link AttributeDefinition} aggregate to the
 * generic REST DTOs — never returns the JPA entities themselves.
 *
 * <p>{@code @Transactional(readOnly = true)} keeps the fetch-join query and
 * attribute traversal inside one transaction/session (avoiding
 * {@code LazyInitializationException}), even though the repository query
 * already fetch-joins attributes.
 */
@Service
public class EntityDefinitionQueryServiceImpl implements EntityDefinitionQueryService {

    private final EntityDefinitionRepository entityDefinitionRepository;

    public EntityDefinitionQueryServiceImpl(EntityDefinitionRepository entityDefinitionRepository) {
        this.entityDefinitionRepository = entityDefinitionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EntityDefinitionSummary> listEntityDefinitions() {
        return entityDefinitionRepository.findAllWithAttributes().stream()
                .map(EntityDefinitionQueryServiceImpl::toSummary)
                .toList();
    }

    private static EntityDefinitionSummary toSummary(EntityDefinition entityDefinition) {
        List<AttributeSummary> attributeSummaries =
                entityDefinition.getAttributes().stream()
                        .map(EntityDefinitionQueryServiceImpl::toAttributeSummary)
                        .toList();
        return new EntityDefinitionSummary(
                entityDefinition.getId(),
                entityDefinition.getCode(),
                entityDefinition.getLabel(),
                entityDefinition.getVersion(),
                attributeSummaries);
    }

    private static AttributeSummary toAttributeSummary(AttributeDefinition attributeDefinition) {
        return new AttributeSummary(
                attributeDefinition.getCode(),
                attributeDefinition.getLabel(),
                attributeDefinition.getDataType().name(),
                attributeDefinition.isRequired());
    }
}
