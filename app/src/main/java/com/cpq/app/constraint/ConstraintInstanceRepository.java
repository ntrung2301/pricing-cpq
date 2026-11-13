package com.cpq.app.constraint;

import com.cpq.core.metamodel.ConstraintInstance;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link ConstraintInstance} (S2-T2).
 * Lives in {@code app} — same ADR-005 reasoning as
 * {@link ConstraintDefinitionRepository}.
 */
public interface ConstraintInstanceRepository extends JpaRepository<ConstraintInstance, Long> {}
