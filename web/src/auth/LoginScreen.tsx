import { useAuth } from 'react-oidc-context'
import { Button } from '../ui'

/**
 * Unauthenticated-state screen (S1-T8): redirects to the real Keycloak
 * login page (Authorization Code + PKCE) via react-oidc-context's
 * `signinRedirect`. No local login form — Keycloak owns the login UI.
 *
 * Migrated from MUI to Ant Design + web/design.md tokens (D-050) — logic
 * unchanged, only the JSX/markup.
 */
export function LoginScreen() {
  const auth = useAuth()

  return (
    <div style={{ textAlign: 'center', padding: '64px 0' }}>
      <h2>Please sign in to continue</h2>
      <Button variant="primary" onClick={() => auth.signinRedirect()}>
        Login with Keycloak
      </Button>
    </div>
  )
}
