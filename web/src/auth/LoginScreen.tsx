import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Typography from '@mui/material/Typography'
import { useAuth } from 'react-oidc-context'

/**
 * Unauthenticated-state screen (S1-T8): redirects to the real Keycloak
 * login page (Authorization Code + PKCE) via react-oidc-context's
 * `signinRedirect`. No local login form — Keycloak owns the login UI.
 */
export function LoginScreen() {
  const auth = useAuth()

  return (
    <Box sx={{ textAlign: 'center', py: 8 }}>
      <Typography variant="h5" gutterBottom>
        Please sign in to continue
      </Typography>
      <Button variant="contained" onClick={() => auth.signinRedirect()}>
        Login with Keycloak
      </Button>
    </Box>
  )
}
