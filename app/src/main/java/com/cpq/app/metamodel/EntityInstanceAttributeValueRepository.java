package com.cpq.app.metamodel;

import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.EntityInstance;
import com.cpq.core.metamodel.EntityInstanceAttributeValue;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link EntityInstanceAttributeValue}
 * (S2-T1). Lives in {@code app} — same ADR-005 reasoning as
 * {@link EntityDefinitionRepository}.
 */
public interface EntityInstanceAttributeValueRepository
        extends JpaRepository<EntityInstanceAttributeValue, Long> {

    /** Used by {@link EntityInstanceService} to upsert (update if already written). */
    Optional<EntityInstanceAttributeValue> findByEntityInstanceAndAttributeDefinition(
            EntityInstance entityInstance, AttributeDefinition attributeDefinition);
}
