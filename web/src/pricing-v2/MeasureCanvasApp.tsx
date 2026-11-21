import { Alert, Spin } from 'antd'
import { useAuth } from 'react-oidc-context'
import { Button, UIProvider } from '../ui'
import { MeasureCanvasPage } from './MeasureCanvasPage'

/**
 * Top-level screen for the standalone `measure-canvas.html` entry (S3-T8,
 * docs/decisions.md D-049). Login prompt here is a small new Ant Design
 * screen — not a port of the existing MUI `LoginScreen.tsx` ("1 component
 * library đã chốt... không trộn nhiều lib", frontend-dev.md).
 */
export function MeasureCanvasApp() {
  const auth = useAuth()

  return (
    <UIProvider>
      <div style={{ maxWidth: 1320, margin: '0 auto', padding: '24px' }}>
        {auth.isLoading && <Spin aria-label="Loading authentication state" />}

        {!auth.isLoading && auth.error && <Alert type="error" message={auth.error.message} />}

        {!auth.isLoading && !auth.error && !auth.isAuthenticated && (
          <div style={{ textAlign: 'center', padding: '64px 0' }}>
            <h1>Measure Canvas</h1>
            <p>Please sign in to continue</p>
            <Button variant="primary" onClick={() => auth.signinRedirect()}>
              Login with Keycloak
            </Button>
          </div>
        )}

        {!auth.isLoading && !auth.error && auth.isAuthenticated && auth.user && (
          <>
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 12 }}>
              <Button variant="quiet" onClick={() => auth.removeUser()}>
                Sign out
              </Button>
            </div>
            <MeasureCanvasPage accessToken={auth.user.access_token} />
          </>
        )}
      </div>
    </UIProvider>
  )
}
