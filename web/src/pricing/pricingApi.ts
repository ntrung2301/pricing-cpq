import type { PriceCalculationRequest, PriceCalculationResponse } from './types'

// Same override pattern as web/src/entities/entitiesApi.ts / web/src/catalog/catalogApi.ts.
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

/**
 * Calls the real `POST /api/pricing/calculate` backend endpoint
 * (S2-T10/S2-T12/S2-T13) with the caller's OIDC access token. Throws on
 * any non-2xx response (e.g. 404 when the referenced price_list/rule_set/
 * price_list_item doesn't exist, per docs/decisions.md D-031) — the
 * caller (PriceWaterfallScreen) turns that into an error banner, never a
 * crash.
 */
export async function calculatePrice(
  accessToken: string,
  request: PriceCalculationRequest,
): Promise<PriceCalculationResponse> {
  const response = await fetch(`${API_BASE_URL}/api/pricing/calculate`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    throw new Error(`POST /api/pricing/calculate failed with status ${response.status}`)
  }

  return (await response.json()) as PriceCalculationResponse
}
