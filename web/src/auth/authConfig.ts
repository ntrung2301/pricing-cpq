import type { AuthProviderProps } from 'react-oidc-context'

// S1-T8: real Authorization Code + PKCE login against the local-dev
// Keycloak realm `cpq`, client `cpq-web` (keycloak/realm-export.json) —
// a public client, PKCE-only (no client secret, no Resource Owner
// Password Credentials). See docs/decisions.md for why this client is
// separate from `cpq-backend` (ROPC, manual backend verification only,
// ADR-006/D-017).
//
// Both values are overridable via Vite env vars so this isn't hardcoded
// to one developer's machine (same spirit as the backend's
// KEYCLOAK_ISSUER_URI override, D-017).
export const oidcConfig: AuthProviderProps = {
  authority: import.meta.env.VITE_KEYCLOAK_ISSUER_URI ?? 'http://localhost:8089/realms/cpq',
  client_id: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'cpq-web',
  redirect_uri: window.location.origin,
  post_logout_redirect_uri: window.location.origin,
  response_type: 'code',
  scope: 'openid profile email',
  // Strip the `code`/`state` query params Keycloak appends to the
  // redirect URI after login, so refreshing the page doesn't attempt to
  // replay the same (already-consumed) authorization code.
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname)
  },
}
