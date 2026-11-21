package com.cpq.app.pricing;

import com.cpq.core.pricing.persistence.PriceListV2Entity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link PriceListV2Entity} (S3-T6). Named
 * {@code PriceListV2Repository}, not {@code PriceListRepository} — that
 * name is already taken, in this exact package, by the Sprint 2
 * RuleEngine-based {@code price_list} repository (see
 * docs/decisions.md D-046).
 */
public interface PriceListV2Repository extends JpaRepository<PriceListV2Entity, Long> {

    Optional<PriceListV2Entity> findByCode(String code);
}
