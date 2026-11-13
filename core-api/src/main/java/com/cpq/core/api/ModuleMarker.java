package com.cpq.core.api;

/**
 * Trivial marker type for the {@code core-api} module, used by the
 * module-layering ArchUnit test ({@code com.cpq.app.arch.ModuleLayeringArchTest}).
 * Real REST controllers/OpenAPI types land here in later sprints; this
 * module must never contain business logic (ADR-005 §2).
 */
public interface ModuleMarker {
}
