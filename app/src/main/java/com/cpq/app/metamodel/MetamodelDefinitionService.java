package com.cpq.app.metamodel;

import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.EntityDefinition;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Minimal service that creates an {@link EntityDefinition} together with
 * its {@link AttributeDefinition}s in one call (S1-T7 Definition of Done).
 * Entirely generic — it has no idea whether the caller is defining
 * "TestWidget", "Part", or anything else; that is the caller's data, not
 * code here (CLAUDE.md genericity principle, E1-01 acceptance pattern).
 */
@Service
public class MetamodelDefinitionService {

    private final EntityDefinitionRepository entityDefinitionRepository;

    public MetamodelDefinitionService(EntityDefinitionRepository entityDefinitionRepository) {
        this.entityDefinitionRepository = entityDefinitionRepository;
    }

    /**
     * Creates and persists a new {@link EntityDefinition} with the given
     * attributes attached, in one transaction. Rejects attributes sharing
     * the same {@code code} within the same entity definition before
     * touching the database.
     */
    @Transactional
    public EntityDefinition defineEntity(
            String code, String label, int version, List<AttributeDefinition> attributes) {
        rejectDuplicateAttributeCodes(attributes);

        EntityDefinition entityDefinition = new EntityDefinition(code, label, version);
        attributes.forEach(entityDefinition::addAttribute);

        return entityDefinitionRepository.save(entityDefinition);
    }

    private void rejectDuplicateAttributeCodes(List<AttributeDefinition> attributes) {
        Set<String> seenCodes = new HashSet<>();
        for (AttributeDefinition attribute : attributes) {
            if (!seenCodes.add(attribute.getCode())) {
                throw new DuplicateAttributeCodeException(attribute.getCode());
            }
        }
    }
}
