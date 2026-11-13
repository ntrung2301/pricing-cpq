// Mirrors the backend's generic REST DTOs (core-api's
// com.cpq.core.api.entities.{EntityDefinitionSummary,AttributeSummary}),
// S1-T8. Deliberately generic field names only — the UI has no idea
// whether a given row means "Part", "Vehicle", "SampleWidget", or
// anything else (same genericity principle as the backend, CLAUDE.md
// "Ranh gioi Core vs Domain").

export interface AttributeSummary {
  code: string
  label: string
  dataType: string
  required: boolean
}

export interface EntityDefinitionSummary {
  code: string
  label: string
  version: number
  attributes: AttributeSummary[]
}
