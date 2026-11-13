package com.cpq.core.api.catalog;

import java.util.List;

/**
 * One page of {@link EntityInstanceSummary} results for {@code GET
 * /api/catalog/{entityDefCode}/instances} (S2-T9-prereq, see
 * docs/decisions.md D-030). Simple offset-based paging envelope — not
 * cursor-based — per this task's scope decision: sprint-2.md's Q-002 notes
 * catalog sizes on the order of "vài nghìn part" (a few thousand rows),
 * well within what plain {@code page}/{@code size} pagination handles.
 */
public record EntityInstancePageDto(
        List<EntityInstanceSummary> content, int page, int size, long totalElements, int totalPages) {}
