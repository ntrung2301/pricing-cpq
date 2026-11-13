package com.cpq.app.metamodel;

import com.cpq.core.metamodel.RelationshipDefinition;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link RelationshipDefinition}. Lives in
 * {@code app} — same ADR-005 reasoning as {@link EntityDefinitionRepository}.
 * Used by {@code PackLoaderService} (S2-T1/S2-T3) to upsert relationship
 * definitions such as {@code "supersedes"}/{@code "contains"}.
 */
public interface RelationshipDefinitionRepository extends JpaRepository<RelationshipDefinition, Long> {

    Optional<RelationshipDefinition> findByCode(String code);
}
