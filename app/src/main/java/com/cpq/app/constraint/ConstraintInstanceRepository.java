package com.cpq.app.constraint;

import com.cpq.core.metamodel.ConstraintInstance;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link ConstraintInstance} (S2-T2).
 * Lives in {@code app} — same ADR-005 reasoning as
 * {@link ConstraintDefinitionRepository}.
 *
 * <p>The two {@code @Query} methods below back S2-T8/E2-01's configurator
 * engine ({@code com.cpq.app.configurator.ConfiguratorServiceImpl}).
 */
public interface ConstraintInstanceRepository extends JpaRepository<ConstraintInstance, Long> {

    /**
     * Every {@code constraint_instance} whose <strong>both</strong>
     * endpoints (x and y) are present in {@code ids} — i.e. a constraint
     * actually relevant to a given selection of entity_instance ids
     * (D-024: a row is an assertion between two specific instances, not a
     * rule applying to many pairs), used by
     * {@code ConfiguratorServiceImpl#validateSelection}.
     */
    @Query(
            "select ci from ConstraintInstance ci "
                    + "where ci.entityInstanceX.id in :ids and ci.entityInstanceY.id in :ids")
    List<ConstraintInstance> findBetweenAnyOf(@Param("ids") List<Long> ids);

    /**
     * Every {@code constraint_instance} of the given {@code type} with
     * {@code anchorInstanceId} on either side (x or y) — used by
     * {@code ConfiguratorServiceImpl#listCompatibleInstances} to find
     * candidate compatibility assertions touching one anchor instance,
     * regardless of which side it was recorded on.
     */
    @Query(
            "select ci from ConstraintInstance ci "
                    + "where ci.constraintDefinition.type = :type "
                    + "and (ci.entityInstanceX.id = :anchorId or ci.entityInstanceY.id = :anchorId)")
    List<ConstraintInstance> findByTypeAndEitherEndpoint(
            @Param("type") String type, @Param("anchorId") Long anchorId);
}
