// Mirrors the backend's generic pricing DTOs (core-api's
// com.cpq.core.api.pricing.{PriceCalculationRequest,PriceCalculationResponse,
// WaterfallStepSummary}), S2-T10/S2-T12/S2-T13. Deliberately generic:
// `stage` is one of the 6 fixed ADR-004 pipeline stages
// (base/segment-adjustment/quantity-adjustment/discount/override/
// guardrail) as a plain string — this module has no idea what a
// "Customer"/"tier"/"contract override" means, it just carries ids and
// numbers through to the real backend rule engine.

export interface WaterfallStepSummary {
  stepOrder: number
  stage: string
  ruleId: number | null
  label: string
  deltaAmount: number
  resultingAmount: number
  rounded: boolean
}

export interface PriceCalculationRequest {
  ruleSetCode: string
  priceListCode: string
  entityDefId: number
  entityInstanceId: number
  quantity: number
  attributes: Record<string, unknown>
  customerInstanceId: number | null
  contractOverridePrice: number | null
}

export interface PriceCalculationResponse {
  steps: WaterfallStepSummary[]
  finalAmount: number
}
