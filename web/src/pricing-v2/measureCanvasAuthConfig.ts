import type { AuthProviderProps } from 'react-oidc-context'

// S3-T8: same real Keycloak realm/client as web/src/auth/authConfig.ts
// (S1-T8) — a separate config object only because `redirect_uri` must
// point back at THIS page (`/measure-canvas.html`), not at the SPA root
// `web/src/auth/authConfig.ts` hardcodes (`window.location.origin`).
// `cpq-web`'s registered redirect URI is the wildcard
// `http://localhost:5173/*` (keycloak/realm-export.json), so any path
// under the dev server works without touching the Keycloak realm or the
// existing authConfig.ts (out of scope for this task — see
// docs/decisions.md D-049 for why this screen is a separate Vite entry
// point instead of a route inside the existing App.tsx).
export const measureCanvasOidcConfig: AuthProviderProps = {
  authority: import.meta.env.VITE_KEYCLOAK_ISSUER_URI ?? 'http://localhost:8089/realms/cpq',
  client_id: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'cpq-web',
  redirect_uri: `${window.location.origin}/measure-canvas.html`,
  post_logout_redirect_uri: `${window.location.origin}/measure-canvas.html`,
  response_type: 'code',
  scope: 'openid profile email',
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname)
  },
}
