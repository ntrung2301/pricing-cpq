package com.cpq.app.constraint;

import com.cpq.core.metamodel.ConstraintDefinition;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link ConstraintDefinition} (S2-T2).
 * Lives in {@code app}, not {@code core-metamodel} — same ADR-005
 * reasoning as {@code com.cpq.app.metamodel.EntityDefinitionRepository}.
 */
public interface ConstraintDefinitionRepository extends JpaRepository<ConstraintDefinition, Long> {

    /**
     * Added for S2-T7: {@code com.cpq.app.demo.AutomotiveFitmentSeedRunner}
     * uses this to look up/create one fixed {@code constraint_def} (code
     * {@code "automotive-fitment"}, type {@code "compatibility"}) idempotently
     * on each app startup, same pattern {@code RelationshipDefinitionRepository
     * .findByCode} already provides for relationship definitions.
     */
    Optional<ConstraintDefinition> findByCode(String code);
}
