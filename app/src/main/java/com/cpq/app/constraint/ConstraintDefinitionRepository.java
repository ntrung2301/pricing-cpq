package com.cpq.app.constraint;

import com.cpq.core.metamodel.ConstraintDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link ConstraintDefinition} (S2-T2).
 * Lives in {@code app}, not {@code core-metamodel} — same ADR-005
 * reasoning as {@code com.cpq.app.metamodel.EntityDefinitionRepository}.
 */
public interface ConstraintDefinitionRepository extends JpaRepository<ConstraintDefinition, Long> {}
