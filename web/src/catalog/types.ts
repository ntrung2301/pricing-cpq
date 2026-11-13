// Mirrors the backend's generic catalog DTOs (core-api's
// com.cpq.core.api.catalog.{EntityInstanceSummary,EntityInstancePageDto,
// SupersedingInstanceSummary}), S2-T9-prereq/S2-T7 (docs/decisions.md
// D-030, D-029). Deliberately generic: the UI has no idea whether a given
// row means "Vehicle", "Product", or anything else — `attributes` is a
// bag of whatever the active entity_def declares (same genericity
// principle as web/src/entities/types.ts, CLAUDE.md "Ranh gioi Core vs
// Domain").

export interface EntityInstanceSummary {
  id: number
  businessKey: string | null
  displayLabel: string | null
  attributes: Record<string, unknown>
}

export interface EntityInstancePageDto {
  content: EntityInstanceSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/**
 * One entity_instance that supersedes (replaces) another, via a
 * "supersedes"-typed relationship_instance (S2-T7). Generic: core only
 * knows "this instance replaces that one", never "this part replaces that
 * part".
 */
export interface SupersedingInstanceSummary {
  entityInstanceId: number
  businessKey: string
  displayLabel: string
}
