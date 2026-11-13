package com.cpq.core.api.configurator;

import java.util.List;

/**
 * Response body for {@code POST /api/configurator/validate-selection}
 * (S2-T8, E2-01). {@code valid} is {@code true} only when every
 * {@code constraint_instance} relevant to the selection (both its
 * endpoints present in the request) evaluated to {@code passed() == true}
 * — a selection with no relevant constraint_instance at all is trivially
 * valid. {@link ConfiguratorController} maps {@code valid == false} to
 * HTTP 400, never 500 (sprint-2.md S2-T8 Definition of Done).
 */
public record ValidateSelectionResponse(boolean valid, List<ConstraintViolationSummary> violations) {}
