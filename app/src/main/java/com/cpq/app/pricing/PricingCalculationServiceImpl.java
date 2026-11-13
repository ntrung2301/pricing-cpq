package com.cpq.app.pricing;

import com.cpq.core.api.pricing.PriceCalculationRequest;
import com.cpq.core.api.pricing.PriceCalculationResponse;
import com.cpq.core.api.pricing.PricingCalculationService;
import com.cpq.core.api.pricing.WaterfallStepSummary;
import com.cpq.core.pricing.PricingContext;
import com.cpq.core.pricing.Rule;
import com.cpq.core.pricing.RuleEngine;
import com.cpq.core.pricing.RuleSet;
import com.cpq.core.pricing.WaterfallStep;
import com.cpq.core.pricing.persistence.PriceListItemEntity;
import com.cpq.core.pricing.persistence.RuleSetEntity;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code app}-side implementation of {@code core-api}'s
 * {@link PricingCalculationService} port (S2-T10/E3-02), same
 * interface-in-core-api/implementation-in-app split as
 * {@code EntityDefinitionQueryServiceImpl} (S1-T8).
 *
 * <p>Resolves the starting base price from {@code price_list_item}
 * (instance-specific row preferred, falling back to the segment-level
 * row for the entity_def — ADR-003), loads the {@code rule_set} by code,
 * and delegates the actual pipeline run to the generic
 * {@link RuleEngine} (core-pricing) — this class owns no pricing logic
 * itself, only DB lookups + DTO mapping, per CLAUDE.md's "engine lõi
 * generic" boundary.
 */
@Service
public class PricingCalculationServiceImpl implements PricingCalculationService {

    private final RuleSetRepository ruleSetRepository;
    private final PriceListRepository priceListRepository;
    private final PriceListItemRepository priceListItemRepository;
    private final RuleEngine ruleEngine;

    public PricingCalculationServiceImpl(
            RuleSetRepository ruleSetRepository,
            PriceListRepository priceListRepository,
            PriceListItemRepository priceListItemRepository,
            RuleEngine ruleEngine) {
        this.ruleSetRepository = ruleSetRepository;
        this.priceListRepository = priceListRepository;
        this.priceListItemRepository = priceListItemRepository;
        this.ruleEngine = ruleEngine;
    }

    @Override
    @Transactional(readOnly = true)
    public PriceCalculationResponse calculate(PriceCalculationRequest request) {
        BigDecimal basePrice = resolveBasePrice(request);
        RuleSet ruleSet = loadRuleSet(request.ruleSetCode());
        PricingContext context =
                new PricingContext(basePrice, request.quantity(), normalizeAttributes(request.attributes()));

        List<WaterfallStep> steps = ruleEngine.run(context, ruleSet);
        return toResponse(steps);
    }

    private BigDecimal resolveBasePrice(PriceCalculationRequest request) {
        var priceList =
                priceListRepository
                        .findByCode(request.priceListCode())
                        .orElseThrow(
                                () ->
                                        new PricingDataNotFoundException(
                                                "No price_list with code " + request.priceListCode()));

        if (request.entityInstanceId() != null) {
            var instanceSpecific =
                    priceListItemRepository.findInstanceSpecific(
                            priceList.getId(), request.entityDefId(), request.entityInstanceId());
            if (instanceSpecific.isPresent()) {
                return instanceSpecific.get().getAmount();
            }
        }
        PriceListItemEntity segmentLevel =
                priceListItemRepository
                        .findSegmentLevel(priceList.getId(), request.entityDefId())
                        .orElseThrow(
                                () ->
                                        new PricingDataNotFoundException(
                                                "No price_list_item for price_list "
                                                        + request.priceListCode()
                                                        + " / entity_def "
                                                        + request.entityDefId()));
        return segmentLevel.getAmount();
    }

    private RuleSet loadRuleSet(String ruleSetCode) {
        RuleSetEntity entity =
                ruleSetRepository
                        .findByCodeWithRules(ruleSetCode)
                        .orElseThrow(
                                () -> new PricingDataNotFoundException("No rule_set with code " + ruleSetCode));
        List<Rule> rules = entity.getRules().stream().map(r -> r.toEngineRule()).toList();
        return new RuleSet(entity.getId(), entity.getCode(), entity.getVersion(), rules);
    }

    /**
     * JSON request attributes decode as {@link Integer}/{@link Long}/
     * {@link Double} (Jackson's default {@code Map<String,Object>}
     * handling), never {@link BigDecimal} — but
     * {@link com.cpq.core.pricing.dsl.DslRuleExecutor}/
     * {@link com.cpq.core.pricing.script.GroovyRuleExecutor} compare
     * numeric attributes against {@link BigDecimal} literals. Normalizing
     * every {@link Number} to {@link BigDecimal} here (via its exact
     * {@code toString()}, not a {@code doubleValue()} round-trip) keeps
     * CLAUDE.md's "luôn BigDecimal, không double" guarantee intact across
     * the HTTP boundary.
     */
    private static Map<String, Object> normalizeAttributes(Map<String, Object> attributes) {
        if (attributes == null) {
            return Map.of();
        }
        Map<String, Object> normalized = new HashMap<>();
        attributes.forEach(
                (key, value) ->
                        normalized.put(key, value instanceof Number number ? new BigDecimal(number.toString()) : value));
        return normalized;
    }

    private static PriceCalculationResponse toResponse(List<WaterfallStep> steps) {
        List<WaterfallStepSummary> summaries =
                steps.stream()
                        .map(
                                step ->
                                        new WaterfallStepSummary(
                                                step.stepOrder(),
                                                step.stage().wireCode(),
                                                step.ruleId(),
                                                step.label(),
                                                step.deltaAmount(),
                                                step.resultingAmount(),
                                                step.rounded()))
                        .toList();
        BigDecimal finalAmount = steps.get(steps.size() - 1).resultingAmount();
        return new PriceCalculationResponse(summaries, finalAmount);
    }
}
