import Box from '@mui/material/Box'
import Container from '@mui/material/Container'
import Typography from '@mui/material/Typography'

function App() {
  return (
    <Container maxWidth="sm">
      <Box component="main" sx={{ py: 8, textAlign: 'center' }}>
        <Typography variant="h3" component="h1" gutterBottom>
          CPQ Pricing Platform
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Frontend skeleton — Vite + React + TypeScript + MUI + TanStack
          Query.
        </Typography>
      </Box>
    </Container>
  )
}

export default App
