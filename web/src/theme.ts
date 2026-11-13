import { createTheme } from '@mui/material/styles'

// Minimal default MUI theme for the skeleton. Domain packs / PO branding
// decisions can override this later (see docs/decisions.md D-015).
const theme = createTheme({
  palette: {
    mode: 'light',
  },
})

export default theme
