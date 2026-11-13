package com.cpq.core.api.pricing;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/pricing/calculate} (S2-T10/E3-02): thin controller,
 * same pattern as {@link com.cpq.core.api.entities.EntitiesController} —
 * the real logic (and {@code @PreAuthorize}) lives in
 * {@link PricingCalculationService}.
 */
@RestController
public class PricingController {

    private final PricingCalculationService pricingCalculationService;

    public PricingController(PricingCalculationService pricingCalculationService) {
        this.pricingCalculationService = pricingCalculationService;
    }

    @PostMapping("/api/pricing/calculate")
    public PriceCalculationResponse calculate(@RequestBody PriceCalculationRequest request) {
        return pricingCalculationService.calculate(request);
    }
}
