package com.cpq.app;

/**
 * Proves, at compile time, that {@code app} is the one place that depends
 * on every {@code core-*} module (ADR-005): implementing every module's
 * {@code ModuleMarker} interface here means this class (and therefore the
 * {@code com.cpq.app} package) imports all of {@code com.cpq.core.*}.
 * {@code com.cpq.app.arch.ModuleLayeringArchTest} checks the converse: that
 * no individual {@code core-*} module does the same.
 *
 * <p>Not a Spring bean, not business logic — purely a compile-time/
 * architecture-test fixture. Safe to delete once real cross-module wiring
 * exists in {@code app} (e.g. a real {@code @Configuration} class that
 * actually injects beans from each core-* module).
 */
public final class CompositionRoot
        implements
        com.cpq.core.metamodel.ModuleMarker,
        com.cpq.core.constraintengine.ModuleMarker,
        com.cpq.core.pricing.ModuleMarker,
        com.cpq.core.catalog.ModuleMarker,
        com.cpq.core.quote.ModuleMarker,
        com.cpq.core.security.ModuleMarker,
        com.cpq.core.api.ModuleMarker {

    private CompositionRoot() {
    }
}
