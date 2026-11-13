package com.cpq.app.pricing;

import com.cpq.core.pricing.persistence.PriceListItemEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link PriceListItemEntity} (S2-T10).
 * {@code entity_instance_id} may be {@code null} on a row (ADR-003:
 * applies by segment rather than one instance) — the two finder methods
 * below resolve that in the priority order documented on
 * {@link #findInstanceSpecific}/{@link #findSegmentLevel}.
 */
public interface PriceListItemRepository extends JpaRepository<PriceListItemEntity, Long> {

    /** The most specific price: an item targeting this exact entity_instance. */
    @Query(
            "SELECT p FROM PriceListItemEntity p WHERE p.priceList.id = :priceListId "
                    + "AND p.entityDefId = :entityDefId AND p.entityInstanceId = :entityInstanceId")
    Optional<PriceListItemEntity> findInstanceSpecific(
            @Param("priceListId") Long priceListId,
            @Param("entityDefId") Long entityDefId,
            @Param("entityInstanceId") Long entityInstanceId);

    /** Fallback: the segment/default price for this entity_def (entity_instance_id IS NULL). */
    @Query(
            "SELECT p FROM PriceListItemEntity p WHERE p.priceList.id = :priceListId "
                    + "AND p.entityDefId = :entityDefId AND p.entityInstanceId IS NULL")
    Optional<PriceListItemEntity> findSegmentLevel(
            @Param("priceListId") Long priceListId, @Param("entityDefId") Long entityDefId);
}
