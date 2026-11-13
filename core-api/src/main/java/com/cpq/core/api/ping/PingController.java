package com.cpq.core.api.ping;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Skeleton verification endpoint only (S1-T6) — not a real business
 * feature, just a thin wrapper proving the Keycloak/Spring Security
 * wiring works end-to-end (ADR-006). Deliberately carries no
 * {@code @PreAuthorize} of its own: the actual RBAC check lives on
 * {@link PingService#ping()} (ADR-006 method-layer enforcement).
 */
@RestController
public class PingController {

    private final PingService pingService;

    public PingController(PingService pingService) {
        this.pingService = pingService;
    }

    @GetMapping("/api/ping")
    public PingResponse ping() {
        return new PingResponse("ok", pingService.ping());
    }
}
