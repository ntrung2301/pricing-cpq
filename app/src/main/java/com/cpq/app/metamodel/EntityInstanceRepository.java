package com.cpq.app.metamodel;

import com.cpq.core.metamodel.EntityInstance;
import java.util.List;
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
}
