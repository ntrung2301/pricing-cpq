import type { ReactNode } from 'react'
import type { TableColumnsType } from 'antd'
import { Button, DataTable } from '../ui'
import type { EntityInstanceSummary } from './types'

interface InstanceTableProps {
  rows: EntityInstanceSummary[]
  /** Shown instead of the table when `rows` is empty. */
  emptyMessage?: string
  /** Id of the row (entity_instance id) currently highlighted, if any. */
  selectedRowId?: number | null
  /** Called with the full row when its select-action button is clicked. */
  onSelectRow?: (row: EntityInstanceSummary) => void
  /** Label for the per-row select-action button; the button is omitted when not provided. */
  selectActionLabel?: string
  /** Optional extra content rendered under a row's cells (e.g. a relationship hint/badge). */
  renderRowFooter?: (row: EntityInstanceSummary) => ReactNode
}

/**
 * [core] generic data table (docs/ux/01-vehicle-part-selection.md section
 * 2 "Bảng danh sách Part [core]"): columns are derived from whatever
 * `attributes` keys the rows carry, plus `businessKey`/`displayLabel` —
 * this component never hardcodes a domain field name (e.g. "Part
 * Number"). The attribute column header is the raw attribute code (e.g.
 * `sku`), not a human label, because `GET
 * /api/catalog/{entityDefCode}/instances` (docs/decisions.md D-030) does
 * not return attribute_def labels — see docs/decisions.md for this
 * task's simplification decision.
 *
 * Column set is derived from the first row's attribute keys (entity_def
 * rows are expected to share the same attribute shape).
 *
 * Migrated from a hand-rolled MUI table to the shared `DataTable`
 * (web/src/ui/DataTable.tsx) per D-050 — same props API as before (callers
 * in web/src/configurator/VehiclePartSelectionScreen.tsx are unchanged),
 * only the rendering underneath moved to Ant Design + web/design.md tokens.
 */
export function InstanceTable({
  rows,
  emptyMessage = 'No rows to display.',
  selectedRowId,
  onSelectRow,
  selectActionLabel,
  renderRowFooter,
}: InstanceTableProps) {
  const attributeColumns = rows.length > 0 ? Object.keys(rows[0].attributes) : []

  const columns: TableColumnsType<EntityInstanceSummary> = [
    {
      title: 'Business key',
      dataIndex: 'businessKey',
      render: (value: string | null) => value ?? '—',
    },
    {
      title: 'Display label',
      dataIndex: 'displayLabel',
      render: (value: string | null, row) => (
        <>
          {value ?? '—'}
          {renderRowFooter?.(row)}
        </>
      ),
    },
    ...attributeColumns.map((code) => ({
      title: code,
      key: code,
      render: (_: unknown, row: EntityInstanceSummary) => String(row.attributes[code] ?? ''),
    })),
    ...(selectActionLabel
      ? [
          {
            title: '',
            key: 'select-action',
            align: 'right' as const,
            render: (_: unknown, row: EntityInstanceSummary) => (
              <Button size="small" onClick={() => onSelectRow?.(row)}>
                {selectActionLabel}
              </Button>
            ),
          },
        ]
      : []),
  ]

  return (
    <DataTable<EntityInstanceSummary>
      aria-label="entity instances"
      rowKey="id"
      columns={columns}
      dataSource={rows}
      pageSize={false}
      emptyMessage={emptyMessage}
      rowClassName={(row) => (selectedRowId === row.id ? 'instance-row--selected' : '')}
      onRow={(row) => ({ 'aria-selected': selectedRowId === row.id })}
    />
  )
}
