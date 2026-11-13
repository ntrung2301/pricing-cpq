import type { CompatibleInstanceSummary } from './types'

// Same override pattern as web/src/entities/entitiesApi.ts / web/src/catalog/catalogApi.ts.
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

/**
 * Calls the real `GET /api/configurator/compatible` backend endpoint
 * (S2-T8, docs/decisions.md D-027) with the caller's OIDC access token.
 * Generic over `targetEntityDefCode` — this function has no idea it is
 * being used to find "parts" compatible with a "vehicle"; the caller
 * decides that.
 */
export async function fetchCompatibleInstances(
  accessToken: string,
  anchorInstanceId: number,
  targetEntityDefCode: string,
): Promise<CompatibleInstanceSummary[]> {
  const params = new URLSearchParams({
    anchorInstanceId: String(anchorInstanceId),
    targetEntityDefCode,
  })

  const response = await fetch(`${API_BASE_URL}/api/configurator/compatible?${params.toString()}`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  })

  if (!response.ok) {
    throw new Error(`GET /api/configurator/compatible failed with status ${response.status}`)
  }

  return (await response.json()) as CompatibleInstanceSummary[]
}
