package com.cpq.app.pricing;

import com.cpq.core.pricing.persistence.RuleSetEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link RuleSetEntity} (S2-T10). Lives in
 * {@code app}, not {@code core-pricing} — same ADR-005 reasoning as
 * {@code EntityDefinitionRepository} (S1-T7).
 */
public interface RuleSetRepository extends JpaRepository<RuleSetEntity, Long> {

    /**
     * Fetch-joins {@code rules} in one query (same N+1 avoidance reasoning
     * as {@code EntityDefinitionRepository#findByCodeWithAttributes}) —
     * {@link com.cpq.core.pricing.RuleEngine} always needs the full rule
     * list for a rule set, never a lazy subset.
     */
    @Query("SELECT r FROM RuleSetEntity r LEFT JOIN FETCH r.rules WHERE r.code = :code")
    Optional<RuleSetEntity> findByCodeWithRules(@Param("code") String code);
}
