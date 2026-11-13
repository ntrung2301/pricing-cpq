import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Container from '@mui/material/Container'
import Typography from '@mui/material/Typography'
import { useAuth } from 'react-oidc-context'
import { LoginScreen } from './auth/LoginScreen'
import { EntitiesList } from './entities/EntitiesList'

function App() {
  const auth = useAuth()

  return (
    <Container maxWidth="md">
      <Box component="main" sx={{ py: 8 }}>
        <Typography variant="h3" component="h1" gutterBottom align="center">
          CPQ Pricing Platform
        </Typography>

        {auth.isLoading && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress aria-label="Loading authentication state" />
          </Box>
        )}

        {!auth.isLoading && auth.error && (
          <Alert severity="error">{auth.error.message}</Alert>
        )}

        {!auth.isLoading && !auth.error && !auth.isAuthenticated && <LoginScreen />}

        {!auth.isLoading && !auth.error && auth.isAuthenticated && auth.user && (
          <>
            <Box
              sx={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                mb: 2,
              }}
            >
              <Typography variant="h6" component="h2">
                Entity definitions
              </Typography>
              <Button onClick={() => auth.removeUser()}>Sign out</Button>
            </Box>
            <EntitiesList accessToken={auth.user.access_token} />
          </>
        )}
      </Box>
    </Container>
  )
}

export default App
