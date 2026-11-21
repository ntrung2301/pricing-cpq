// Mirrors the backend's generic S3-T6/S3-T8 Price List v2 DTOs
// (core-api's com.cpq.core.api.pricelist.*) — deliberately generic, same
// genericity principle as web/src/catalog/types.ts: the UI has no idea
// what "cost"/"margin"/"SKU" mean, a DagNode is just id/type/config/inputs
// data (ADR-009 mục 2).

export interface DagNodeDto {
  id: string
  type: string
  config: Record<string, unknown>
  inputs: Record<string, string>
}

export interface TraceEntryDto {
  nodeId: string
  type: string
  resolvedInputs: Record<string, unknown>
  output: unknown
}

export interface PriceListDto {
  id: number
  code: string
  name: string
  currency: string
  status: string
  validFrom: string | null
  validTo: string | null
  resultNodeId: string
  version: number
  createdBy: string | null
  submittedAt: string | null
  approvedBy: string | null
  approvedAt: string | null
}

export interface PriceListItemDto {
  id: number
  priceListId: number
  entityInstanceId: number
  resultPrice: string | null
  manualOverridePrice: string | null
  waterfallSteps: TraceEntryDto[]
  validFrom: string | null
  validTo: string | null
}

export interface PriceListDetailDto extends PriceListDto {
  calculationGraph: DagNodeDto[]
  items: PriceListItemDto[]
}

export interface CreatePriceListRequest {
  code: string
  name: string
  currency: string
  validFrom: string | null
  validTo: string | null
  calculationGraph: DagNodeDto[]
  resultNodeId: string
}

export interface AddPriceListItemRequest {
  entityInstanceId: number
}

export interface OverridePriceRequest {
  manualOverridePrice: number | null
}
