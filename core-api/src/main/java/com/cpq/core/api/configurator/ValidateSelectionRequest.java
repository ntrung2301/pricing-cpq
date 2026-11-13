package com.cpq.core.api.configurator;

import java.util.List;

/**
 * Request body for {@code POST /api/configurator/validate-selection}
 * (S2-T8, E2-01). {@code entityInstanceIds} is the full set of
 * {@code entity_instance} ids the caller currently has selected in the
 * configurator — generic; core has no idea whether these are
 * "vehicle"/"part" ids or anything else.
 */
public record ValidateSelectionRequest(List<Long> entityInstanceIds) {}
