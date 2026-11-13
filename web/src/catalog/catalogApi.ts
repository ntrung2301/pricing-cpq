import type { EntityInstancePageDto, SupersedingInstanceSummary } from './types'

// Overridable so the dev server (vite port 5173) can point at the backend
// (app module, default port 8080) without hardcoding — same pattern as
// web/src/entities/entitiesApi.ts (docs/decisions.md D-017).
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface FetchInstancesOptions {
  page?: number
  size?: number
  search?: string
}

/**
 * Calls the real `GET /api/catalog/{entityDefCode}/instances` backend
 * endpoint (S2-T9-prereq, docs/decisions.md D-030) with the caller's OIDC
 * access token. Generic over `entityDefCode` — this function has no idea
 * whether it is listing "Vehicle" or "Product" rows (or anything else);
 * the caller decides that.
 */
export async function fetchInstances(
  accessToken: string,
  entityDefCode: string,
  options: FetchInstancesOptions = {},
): Promise<EntityInstancePageDto> {
  const params = new URLSearchParams()
  params.set('page', String(options.page ?? 0))
  params.set('size', String(options.size ?? 50))
  if (options.search) {
    params.set('search', options.search)
  }

  const response = await fetch(
    `${API_BASE_URL}/api/catalog/${entityDefCode}/instances?${params.toString()}`,
    {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  )

  if (!response.ok) {
    throw new Error(
      `GET /api/catalog/${entityDefCode}/instances failed with status ${response.status}`,
    )
  }

  return (await response.json()) as EntityInstancePageDto
}

/**
 * Calls the real `GET /api/catalog/instances/{instanceId}/superseded-by`
 * backend endpoint (S2-T7, docs/decisions.md D-029). Returns an empty
 * array when the instance is not superseded by anything.
 */
export async function fetchSupersededBy(
  accessToken: string,
  instanceId: number,
): Promise<SupersedingInstanceSummary[]> {
  const response = await fetch(
    `${API_BASE_URL}/api/catalog/instances/${instanceId}/superseded-by`,
    {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  )

  if (!response.ok) {
    throw new Error(
      `GET /api/catalog/instances/${instanceId}/superseded-by failed with status ${response.status}`,
    )
  }

  return (await response.json()) as SupersedingInstanceSummary[]
}
