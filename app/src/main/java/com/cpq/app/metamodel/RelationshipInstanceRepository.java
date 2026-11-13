package com.cpq.app.metamodel;

import com.cpq.core.metamodel.RelationshipInstance;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link RelationshipInstance} (S2-T7).
 * Lives in {@code app} — same ADR-005 reasoning as
 * {@link RelationshipDefinitionRepository}.
 */
public interface RelationshipInstanceRepository extends JpaRepository<RelationshipInstance, Long> {

    /**
     * Every {@code relationship_instance} of the given relationship_def
     * {@code code} whose "to" side is {@code toInstanceId} — generic (the
     * {@code code} is caller-supplied, e.g. {@code "supersedes"}), used by
     * {@code com.cpq.app.catalog.RelationshipInstanceQueryServiceImpl} to
     * find "which instances supersede/replace this one".
     */
    @Query(
            "select ri from RelationshipInstance ri "
                    + "where ri.relationshipDefinition.code = :code and ri.toInstance.id = :toInstanceId")
    List<RelationshipInstance> findByRelationshipDefinitionCodeAndToInstanceId(
            @Param("code") String code, @Param("toInstanceId") Long toInstanceId);

    /** Whether any relationship_instance of the given relationship_def {@code code} exists yet. */
    boolean existsByRelationshipDefinition_Code(String code);
}
