package com.cpq.core.api.entities;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Port (in the hexagonal sense) that {@link EntitiesController} depends on
 * for {@code GET /api/entities} (S1-T8). Declared as an interface here, in
 * {@code core-api}, rather than a concrete {@code @Service} — unlike
 * {@code PingService} (S1-T6) — because the real implementation needs
 * {@code EntityDefinitionRepository}, which per ADR-005 lives in {@code app}
 * (the composition root that owns DB-facing/Spring Data JPA wiring); a
 * {@code core-*} module must never depend on {@code app}
 * ({@code com.cpq.app.arch.ModuleLayeringArchTest#coreModulesMustNotDependOnApp()}).
 * {@code app} provides the {@code @Service} bean implementing this
 * interface ({@code com.cpq.app.metamodel.EntityDefinitionQueryServiceImpl}),
 * and Spring wires it into the controller by type.
 *
 * <p>{@code @PreAuthorize} is declared here, on the interface method, so it
 * applies regardless of whether Spring Security's method-security proxy
 * ends up being JDK-dynamic (interface-based) or CGLIB (class-based) —
 * annotations on the concrete implementation's override are not guaranteed
 * to be seen through a JDK interface proxy. ADR-006 ("enforce ở method
 * layer (service, không chỉ controller)") is still satisfied: the check
 * guards this service port, not {@link EntitiesController}.
 *
 * <p>Per this task's scope decision (docs/decisions.md): any of the 3 core
 * roles may call this — it is generic, read-only metamodel metadata, not a
 * sensitive pricing/approval action — unlike {@code PingService}'s
 * {@code PRICING_ADMIN}-only skeleton example.
 */
public interface EntityDefinitionQueryService {

    @PreAuthorize("hasAnyRole('SALES', 'SALES_MANAGER', 'PRICING_ADMIN')")
    List<EntityDefinitionSummary> listEntityDefinitions();
}
