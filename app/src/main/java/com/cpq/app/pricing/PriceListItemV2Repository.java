package com.cpq.app.pricing;

import com.cpq.core.pricing.persistence.PriceListItemV2Entity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link PriceListItemV2Entity} (S3-T6).
 * Named {@code PriceListItemV2Repository}, not {@code
 * PriceListItemRepository} — that name is already taken, in this exact
 * package, by the Sprint 2 RuleEngine-based {@code price_list_item}
 * repository (see docs/decisions.md D-046).
 */
public interface PriceListItemV2Repository extends JpaRepository<PriceListItemV2Entity, Long> {

    List<PriceListItemV2Entity> findByPriceList_Id(Long priceListId);
}
