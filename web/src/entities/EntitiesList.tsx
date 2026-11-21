import { Alert, Spin } from 'antd'
import type { TableColumnsType } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { DataTable } from '../ui'
import { fetchEntities } from './entitiesApi'
import type { EntityDefinitionSummary } from './types'

interface EntitiesListProps {
  /** Real OIDC access token from the authenticated session (react-oidc-context). */
  accessToken: string
}

const columns: TableColumnsType<EntityDefinitionSummary> = [
  { title: 'Code', dataIndex: 'code' },
  { title: 'Label', dataIndex: 'label' },
  { title: 'Version', dataIndex: 'version' },
  {
    title: 'Attributes',
    align: 'right',
    render: (_, entityDefinition) => entityDefinition.attributes.length,
  },
]

/**
 * S1-T8 walking-skeleton screen: calls the real `GET /api/entities`
 * backend endpoint (via TanStack Query) and renders the result — generic
 * core UI, no hardcoded domain field names (CLAUDE.md "Ranh gioi Core vs
 * Domain" / frontend-dev Definition of Done), just whatever
 * code/label/version/attribute-count the backend returns.
 *
 * Migrated from MUI to Ant Design (the shared `DataTable`) + web/design.md
 * tokens (D-050) — logic/API call unchanged, only the JSX.
 */
export function EntitiesList({ accessToken }: EntitiesListProps) {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['entities', accessToken],
    queryFn: () => fetchEntities(accessToken),
  })

  if (isLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: '32px 0' }}>
        <Spin aria-label="Loading entity definitions" />
      </div>
    )
  }

  if (isError) {
    return <Alert type="error" message={(error as Error).message} />
  }

  if (!data || data.length === 0) {
    return <p>No entity definitions found.</p>
  }

  return (
    <DataTable<EntityDefinitionSummary>
      aria-label="entity definitions"
      rowKey="code"
      columns={columns}
      dataSource={data}
      pageSize={false}
    />
  )
}
