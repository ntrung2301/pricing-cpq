import { Drawer, Spin } from 'antd'
import type { TableColumnsType } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Alert, DataPage, DataTable, StatusPill } from '../ui'
import { getPriceList, listPriceLists } from '../pricing-v2/priceListV2Api'
import type { PriceListDto, TraceEntryDto } from '../pricing-v2/types'

interface PriceListsPageProps {
  /** Real OIDC access token from the authenticated session (react-oidc-context). */
  accessToken: string
}

function statusTone(status: string) {
  switch (status) {
    case 'APPROVED':
    case 'ACTIVE':
      return 'success' as const
    case 'PENDING_APPROVAL':
      return 'info' as const
    case 'REJECTED':
    case 'EXPIRED':
      return 'danger' as const
    default:
      return 'neutral' as const
  }
}

const columns: TableColumnsType<PriceListDto> = [
  {
    title: 'Code',
    dataIndex: 'code',
    render: (code: string, row) => (
      <button type="button" className="price-list-code-action" data-price-list-id={row.id}>
        {code}
      </button>
    ),
  },
  { title: 'Name', dataIndex: 'name' },
  { title: 'Currency', dataIndex: 'currency' },
  {
    title: 'Status',
    dataIndex: 'status',
    render: (status: string) => <StatusPill tone={statusTone(status)}>{status}</StatusPill>,
  },
  { title: 'Version', dataIndex: 'version', align: 'right' },
  { title: 'Submitted', dataIndex: 'submittedAt' },
  { title: 'Approved by', dataIndex: 'approvedBy' },
]

function formatTraceOutput(output: unknown): string {
  return typeof output === 'object' && output !== null ? JSON.stringify(output) : String(output)
}

function TraceTable({ steps }: { steps: TraceEntryDto[] }) {
  return (
    <table className="waterfall-table" aria-label="calculation trace">
      <thead>
        <tr>
          <th>Node</th>
          <th>Type</th>
          <th>Resolved inputs</th>
          <th className="numeric">Output</th>
        </tr>
      </thead>
      <tbody>
        {steps.map((step) => (
          <tr key={step.nodeId}>
            <td>{step.nodeId}</td>
            <td>{step.type}</td>
            <td>{JSON.stringify(step.resolvedInputs)}</td>
            <td className="numeric">{formatTraceOutput(step.output)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function PriceListDetail({ accessToken, priceListId }: { accessToken: string; priceListId: number }) {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['price-list-detail', accessToken, priceListId],
    queryFn: () => getPriceList(accessToken, priceListId),
  })

  if (isLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: '32px 0' }}>
        <Spin aria-label="Loading price list" />
      </div>
    )
  }

  if (isError) {
    return <Alert type="error" message={(error as Error).message} />
  }

  if (!data) return null

  return (
    <div className="stack">
      <dl className="price-list-summary">
        <div>
          <dt>Name</dt>
          <dd>{data.name}</dd>
        </div>
        <div>
          <dt>Currency</dt>
          <dd>{data.currency}</dd>
        </div>
        <div>
          <dt>Version</dt>
          <dd>{data.version}</dd>
        </div>
        <div>
          <dt>Created by</dt>
          <dd>{data.createdBy}</dd>
        </div>
        <div>
          <dt>Approved by</dt>
          <dd>{data.approvedBy ?? '—'}</dd>
        </div>
      </dl>

      {data.items.length === 0 && <p>This price list has no items yet.</p>}

      {data.items.map((item) => (
        <div key={item.id} className="price-list-item-block">
          <h3>Item #{item.id}</h3>
          <dl className="price-list-summary">
            <div>
              <dt>Entity instance</dt>
              <dd>{item.entityInstanceId}</dd>
            </div>
            <div>
              <dt>Result price</dt>
              <dd>{item.resultPrice}</dd>
            </div>
            <div>
              <dt>Manual override</dt>
              <dd>{item.manualOverridePrice ?? '—'}</dd>
            </div>
          </dl>
          <TraceTable steps={item.waterfallSteps} />
        </div>
      ))}
    </div>
  )
}

/**
 * Browse page for the DAG-engine-based Price List module (S3-T6/S3-T7,
 * ADR-009 mục 5) — reads the real `GET /api/price-lists-v2`/`GET
 * /api/price-lists-v2/{id}` endpoints (no mock), same client
 * (`pricing-v2/priceListV2Api`) the Measure Canvas UI already uses
 * against the real backend. Read-only: creating/submitting/approving a
 * Price List still happens via the Measure Canvas drag-and-drop builder
 * (S3-T8) — this page is purely for finding and auditing ones that
 * already exist (code/status/version + full Calculation Trace per item).
 */
export function PriceListsPage({ accessToken }: PriceListsPageProps) {
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['price-lists', accessToken],
    queryFn: () => listPriceLists(accessToken),
  })

  if (isLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: '32px 0' }}>
        <Spin aria-label="Loading price lists" />
      </div>
    )
  }

  if (isError) {
    return <Alert type="error" message={(error as Error).message} />
  }

  const selected = data?.find((priceList) => priceList.id === selectedId) ?? null

  return (
    <DataPage title="Price Lists">
      <DataTable<PriceListDto>
        aria-label="price lists"
        rowKey="id"
        columns={columns}
        dataSource={data ?? []}
        pageSize={false}
        emptyMessage="No price lists yet."
        onRow={(row) => ({
          onClick: (event) => {
            if ((event.target as HTMLElement).closest('.price-list-code-action')) {
              setSelectedId(row.id)
            }
          },
        })}
      />

      <Drawer
        title={selected ? `Price list ${selected.code}` : undefined}
        open={selectedId !== null}
        onClose={() => setSelectedId(null)}
        size="large"
      >
        {selectedId !== null && <PriceListDetail accessToken={accessToken} priceListId={selectedId} />}
      </Drawer>
    </DataPage>
  )
}
