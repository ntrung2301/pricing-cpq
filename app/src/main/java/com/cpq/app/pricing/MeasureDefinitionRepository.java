package com.cpq.app.pricing;

import com.cpq.core.pricing.persistence.MeasureDefinitionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link MeasureDefinitionEntity} (S3-T5).
 * Lives in {@code app}, not {@code core-pricing} — same ADR-005 reasoning
 * as {@code CalculationBindingRepository}.
 */
public interface MeasureDefinitionRepository extends JpaRepository<MeasureDefinitionEntity, Long> {

    /**
     * Looked up by {@code MeasureRefNodeExecutor} using the {@code
     * measure-ref} DagNode's {@code measureCode} config entry — the
     * measure's own business key, not its database id.
     */
    Optional<MeasureDefinitionEntity> findByCode(String code);
}
