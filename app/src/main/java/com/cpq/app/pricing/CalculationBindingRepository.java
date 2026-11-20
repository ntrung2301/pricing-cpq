package com.cpq.app.pricing;

import com.cpq.core.pricing.persistence.CalculationBindingEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link CalculationBindingEntity} (S3-T3).
 * Lives in {@code app}, not {@code core-pricing} — same ADR-005 reasoning
 * as {@code RuleSetRepository}.
 *
 * <p>No fetch-join for {@code filters}/{@code sorts} here (unlike {@code
 * RuleSetRepository#findByCodeWithRules}): two {@code @OneToMany} bags
 * fetch-joined in one JPQL query trips Hibernate's
 * {@code MultipleBagFetchException}. {@code QueryNodeExecutor} instead
 * reads both lazily, inside the {@code @Transactional(readOnly = true)}
 * boundary it executes within — same pattern as {@code
 * ConstraintInstanceEvaluationService}.
 */
public interface CalculationBindingRepository extends JpaRepository<CalculationBindingEntity, Long> {

    /**
     * Looked up by {@code QueryNodeExecutor} using the {@code query}
     * DagNode's {@code bindingAttributeCode} config entry — the binding's
     * own business key, not its database id.
     */
    Optional<CalculationBindingEntity> findByAttributeCode(String attributeCode);
}
