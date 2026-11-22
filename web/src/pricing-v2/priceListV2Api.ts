import type {
  AddPriceListItemRequest,
  CreatePriceListRequest,
  OverridePriceRequest,
  PriceListDetailDto,
  PriceListDto,
  PriceListItemDto,
} from './types'

// Calls the real S3-T6/S3-T8 `/api/price-lists-v2/...` backend (ADR-009
// mục 5) with the caller's OIDC access token. Same
// override-via-env-var/Bearer-token pattern as web/src/catalog/catalogApi.ts
// (docs/decisions.md D-017) — not mocked anywhere in this module.
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

async function parseJsonOrThrow<T>(response: Response, label: string): Promise<T> {
  if (!response.ok) {
    const body = await response.text().catch(() => '')
    throw new Error(`${label} failed with status ${response.status}${body ? `: ${body}` : ''}`)
  }
  return (await response.json()) as T
}

function authHeaders(accessToken: string): HeadersInit {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${accessToken}`,
  }
}

export async function createPriceList(
  accessToken: string,
  request: CreatePriceListRequest,
): Promise<PriceListDto> {
  const response = await fetch(`${API_BASE_URL}/api/price-lists-v2`, {
    method: 'POST',
    headers: authHeaders(accessToken),
    body: JSON.stringify(request),
  })
  return parseJsonOrThrow<PriceListDto>(response, 'POST /api/price-lists-v2')
}

export async function addPriceListItem(
  accessToken: string,
  priceListId: number,
  request: AddPriceListItemRequest,
): Promise<PriceListItemDto> {
  const response = await fetch(`${API_BASE_URL}/api/price-lists-v2/${priceListId}/items`, {
    method: 'POST',
    headers: authHeaders(accessToken),
    body: JSON.stringify(request),
  })
  return parseJsonOrThrow<PriceListItemDto>(response, 'POST /api/price-lists-v2/{id}/items')
}

export async function submitPriceList(accessToken: string, priceListId: number): Promise<PriceListDto> {
  const response = await fetch(`${API_BASE_URL}/api/price-lists-v2/${priceListId}/submit`, {
    method: 'POST',
    headers: authHeaders(accessToken),
  })
  return parseJsonOrThrow<PriceListDto>(response, 'POST /api/price-lists-v2/{id}/submit')
}

export async function approvePriceList(accessToken: string, priceListId: number): Promise<PriceListDto> {
  const response = await fetch(`${API_BASE_URL}/api/price-lists-v2/${priceListId}/approve`, {
    method: 'POST',
    headers: authHeaders(accessToken),
  })
  return parseJsonOrThrow<PriceListDto>(response, 'POST /api/price-lists-v2/{id}/approve')
}

export async function overridePriceListItemPrice(
  accessToken: string,
  itemId: number,
  request: OverridePriceRequest,
): Promise<PriceListItemDto> {
  const response = await fetch(`${API_BASE_URL}/api/price-lists-v2/items/${itemId}/override`, {
    method: 'PATCH',
    headers: authHeaders(accessToken),
    body: JSON.stringify(request),
  })
  return parseJsonOrThrow<PriceListItemDto>(response, 'PATCH /api/price-lists-v2/items/{itemId}/override')
}

export async function listPriceLists(accessToken: string): Promise<PriceListDto[]> {
  const response = await fetch(`${API_BASE_URL}/api/price-lists-v2`, {
    headers: authHeaders(accessToken),
  })
  return parseJsonOrThrow<PriceListDto[]>(response, 'GET /api/price-lists-v2')
}

export async function getPriceList(accessToken: string, priceListId: number): Promise<PriceListDetailDto> {
  const response = await fetch(`${API_BASE_URL}/api/price-lists-v2/${priceListId}`, {
    headers: authHeaders(accessToken),
  })
  return parseJsonOrThrow<PriceListDetailDto>(response, 'GET /api/price-lists-v2/{id}')
}
