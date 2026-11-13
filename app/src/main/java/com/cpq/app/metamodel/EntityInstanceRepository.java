package com.cpq.app.metamodel;

import com.cpq.core.metamodel.EntityInstance;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link EntityInstance} (S2-T1). Lives in
 * {@code app}, not {@code core-metamodel} — same ADR-005 reasoning as
 * {@link EntityDefinitionRepository}.
 */
public interface EntityInstanceRepository extends JpaRepository<EntityInstance, Long> {}
