import type { EntityDefinitionSummary } from './types'

// Overridable so the dev server (vite port 5173) can point at the backend
// (app module, default port 8080) without hardcoding — same spirit as the
// backend's KEYCLOAK_ISSUER_URI env override (docs/decisions.md D-017).
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

/**
 * Calls the real `GET /api/entities` backend endpoint (S1-T8) with the
 * caller's OIDC access token. No mocking here — this is the function the
 * walking-skeleton Playwright E2E test (and, in production, the UI)
 * depends on to see real Postgres data.
 */
export async function fetchEntities(accessToken: string): Promise<EntityDefinitionSummary[]> {
  const response = await fetch(`${API_BASE_URL}/api/entities`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  })

  if (!response.ok) {
    throw new Error(`GET /api/entities failed with status ${response.status}`)
  }

  return (await response.json()) as EntityDefinitionSummary[]
}
