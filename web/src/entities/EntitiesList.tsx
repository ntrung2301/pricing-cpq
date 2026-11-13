import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import Paper from '@mui/material/Paper'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import { useQuery } from '@tanstack/react-query'
import { fetchEntities } from './entitiesApi'

interface EntitiesListProps {
  /** Real OIDC access token from the authenticated session (react-oidc-context). */
  accessToken: string
}

/**
 * S1-T8 walking-skeleton screen: calls the real `GET /api/entities`
 * backend endpoint (via TanStack Query) and renders the result — generic
 * core UI, no hardcoded domain field names (CLAUDE.md "Ranh gioi Core vs
 * Domain" / frontend-dev Definition of Done), just whatever
 * code/label/version/attribute-count the backend returns.
 */
export function EntitiesList({ accessToken }: EntitiesListProps) {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['entities', accessToken],
    queryFn: () => fetchEntities(accessToken),
  })

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
        <CircularProgress aria-label="Loading entity definitions" />
      </Box>
    )
  }

  if (isError) {
    return <Alert severity="error">{(error as Error).message}</Alert>
  }

  if (!data || data.length === 0) {
    return <Typography>No entity definitions found.</Typography>
  }

  return (
    <TableContainer component={Paper}>
      <Table aria-label="entity definitions">
        <TableHead>
          <TableRow>
            <TableCell>Code</TableCell>
            <TableCell>Label</TableCell>
            <TableCell>Version</TableCell>
            <TableCell align="right">Attributes</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {data.map((entityDefinition) => (
            <TableRow key={entityDefinition.code}>
              <TableCell>{entityDefinition.code}</TableCell>
              <TableCell>{entityDefinition.label}</TableCell>
              <TableCell>{entityDefinition.version}</TableCell>
              <TableCell align="right">{entityDefinition.attributes.length}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}
