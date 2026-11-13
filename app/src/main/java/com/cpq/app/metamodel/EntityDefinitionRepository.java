package com.cpq.app.metamodel;

import com.cpq.core.metamodel.EntityDefinition;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link EntityDefinition}. Lives in
 * {@code app}, not {@code core-metamodel}: per ADR-005, {@code app} is the
 * composition root that owns DB-facing wiring (Flyway, Spring Data JPA,
 * `PackLoaderService`-to-be); {@code core-metamodel} only holds the plain
 * JPA-annotated entity classes.
 */
public interface EntityDefinitionRepository extends JpaRepository<EntityDefinition, Long> {

    /**
     * Loads an {@link EntityDefinition} together with its attributes in one
     * query (fetch join), avoiding a {@code LazyInitializationException}
     * when the attributes are read outside of the transaction that loaded
     * the entity definition — the normal access pattern for an admin
     * screen or API that always needs both together.
     */
    @Query("SELECT e FROM EntityDefinition e LEFT JOIN FETCH e.attributes WHERE e.code = :code")
    Optional<EntityDefinition> findByCodeWithAttributes(@Param("code") String code);
}
