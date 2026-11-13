package com.cpq.app.metamodel;

import com.cpq.core.metamodel.EntityInstance;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link EntityInstance} (S2-T1). Lives in
 * {@code app}, not {@code core-metamodel} — same ADR-005 reasoning as
 * {@link EntityDefinitionRepository}.
 */
public interface EntityInstanceRepository extends JpaRepository<EntityInstance, Long> {

    /**
     * Added for S2-T5's catalog-import integration tests: querying by the
     * owning entity definition's code at the DB level (rather than {@code
     * findAll()} + filtering in Java on the lazy {@code entityDefinition}
     * association) avoids a {@code LazyInitializationException} once the
     * caller is outside the transaction that loaded the instance.
     */
    List<EntityInstance> findByEntityDefinition_Code(String code);

    /**
     * Added for S2-T7: same query as {@link #findByEntityDefinition_Code}
     * but with a deterministic {@code id} ascending order, so a caller
     * seeding/consuming rows by their original CSV import row order (e.g.
     * {@code com.cpq.app.demo.AutomotiveFitmentSeedRunner} matching a
     * hardcoded "vehicle at CSV row N" plan) gets a stable mapping instead
     * of relying on unspecified row order.
     */
    List<EntityInstance> findByEntityDefinition_CodeOrderByIdAsc(String code);

    /**
     * Added for S2-T7: looks up a single instance by its owning entity
     * definition's code plus its denormalized {@code business_key}
     * (D-019) — used by {@code com.cpq.app.demo.AutomotiveFitmentSeedRunner}
     * and its tests to resolve a known SKU (e.g. {@code "BRK-PAD-OLD-001"})
     * to the {@code entity_instance} row created for it by S2-T6's import.
     */
    Optional<EntityInstance> findByEntityDefinition_CodeAndBusinessKey(String code, String businessKey);
}
