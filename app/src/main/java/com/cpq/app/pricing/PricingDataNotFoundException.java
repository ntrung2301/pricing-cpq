package com.cpq.app.pricing;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when {@code POST /api/pricing/calculate} (S2-T10) references a
 * {@code rule_set.code} or {@code price_list} item that does not exist —
 * a client/caller error (missing/wrong code), not a server fault, hence
 * {@code 404} rather than letting it fall through as an unhandled 500.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class PricingDataNotFoundException extends RuntimeException {

    public PricingDataNotFoundException(String message) {
        super(message);
    }
}
