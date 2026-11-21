import type { SourceType } from './graphBuilder'

// S3-T8 Measure Canvas, "Data source" palette: a `query` node needs an
// EXISTING CalculationBindingEntity.attributeCode to read (ADR-009 mục 3)
// — there is no Calculation Binding Admin UI/listing API yet (out of this
// task's scope, see docs/decisions.md D-049), so this module hardcodes
// the small set of real, already-seeded binding codes
// (app's PriceListDemoSeedRunner, S3-T7) known to exist in any environment
// this app seeds. NEEDS-PO-REVIEW: replace this with a real
// `GET /api/calculation-bindings` listing once a Binding Admin screen
// exists — flagged, not blocking this task.
//
// PRODUCT/EXTENSION bindings auto-anchor to whichever Product the Price
// List item being priced is for (AnchorInjector, app). CUSTOMER bindings
// never do — the author must pick a concrete Customer instance (see
// graphBuilder.ts's SourceCanvasNode.anchorInstanceId).
export interface BindingPreset {
  bindingAttributeCode: string
  label: string
  sourceType: SourceType
  requiresAnchorPicker: boolean
}

export const BINDING_PRESETS: BindingPreset[] = [
  {
    bindingAttributeCode: 'plv2_product_country_of_manufacture',
    label: 'Product — Country of Manufacture',
    sourceType: 'PRODUCT',
    requiresAnchorPicker: false,
  },
  {
    bindingAttributeCode: 'plv2_customer_tier',
    label: 'Customer — Tier',
    sourceType: 'CUSTOMER',
    requiresAnchorPicker: true,
  },
]
