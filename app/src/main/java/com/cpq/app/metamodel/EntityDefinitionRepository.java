package com.cpq.app.metamodel;

import com.cpq.core.metamodel.EntityDefinition;
import java.util.List;
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

    /**
     * Loads every {@link EntityDefinition} together with its attributes in
     * one query (fetch join) — same N+1 avoidance reasoning as
     * {@link #findByCodeWithAttributes}, but for the "list everything"
     * access pattern used by {@code GET /api/entities} (S1-T8).
     * {@code DISTINCT} avoids duplicate parent rows from the
     * one-to-many join; ordered by {@code code} for a stable response.
     */
    @Query("SELECT DISTINCT e FROM EntityDefinition e LEFT JOIN FETCH e.attributes ORDER BY e.code")
    List<EntityDefinition> findAllWithAttributes();

    /**
     * Added for S2-T9-prereq (docs/decisions.md D-030): {@code
     * EntityInstanceQueryServiceImpl} needs a cheap existence check for
     * {@code GET /api/catalog/{entityDefCode}/instances}'s 404 branch
     * (no attribute fetch-join needed, unlike {@link #findByCodeWithAttributes}
     * — this endpoint never returns entity_def metadata, only
     * entity_instance rows).
     */
    boolean existsByCode(String code);
}
