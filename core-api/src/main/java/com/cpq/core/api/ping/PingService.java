package com.cpq.core.api.ping;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Trivial skeleton-verification hook for ADR-006's Keycloak/Spring
 * Security wiring (S1-T6) — NOT a real business feature, purely to prove
 * the OIDC resource server + RBAC wiring end-to-end.
 *
 * <p>The {@code @PreAuthorize} lives here, on the service method, rather
 * than (only) on {@link PingController}, per ADR-006's explicit
 * requirement to enforce RBAC "ở method layer (service, không chỉ
 * controller) để chặn cả khi có đường gọi nội bộ khác HTTP" (so an
 * in-process caller that bypasses the controller is still blocked).
 */
@Service
public class PingService {

    @PreAuthorize("hasRole('PRICING_ADMIN')")
    public String ping() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }
}
