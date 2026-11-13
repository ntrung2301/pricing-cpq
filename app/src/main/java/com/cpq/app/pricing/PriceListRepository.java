package com.cpq.app.pricing;

import com.cpq.core.pricing.persistence.PriceListEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link PriceListEntity} (S2-T10). */
public interface PriceListRepository extends JpaRepository<PriceListEntity, Long> {

    Optional<PriceListEntity> findByCode(String code);
}
