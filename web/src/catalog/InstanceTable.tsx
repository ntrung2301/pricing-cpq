import Button from '@mui/material/Button'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import Paper from '@mui/material/Paper'
import type { ReactNode } from 'react'
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
 */
export function InstanceTable({
  rows,
  emptyMessage = 'No rows to display.',
  selectedRowId,
  onSelectRow,
  selectActionLabel,
  renderRowFooter,
}: InstanceTableProps) {
  if (rows.length === 0) {
    return <Typography>{emptyMessage}</Typography>
  }

  const attributeColumns = Object.keys(rows[0].attributes)

  return (
    <TableContainer component={Paper}>
      <Table aria-label="entity instances">
        <TableHead>
          <TableRow>
            <TableCell>Business key</TableCell>
            <TableCell>Display label</TableCell>
            {attributeColumns.map((code) => (
              <TableCell key={code}>{code}</TableCell>
            ))}
            {selectActionLabel && <TableCell align="right" />}
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => {
            const footer = renderRowFooter?.(row)
            return (
              <TableRow
                key={row.id}
                selected={selectedRowId === row.id}
                aria-selected={selectedRowId === row.id}
                hover
              >
                <TableCell>{row.businessKey ?? '—'}</TableCell>
                <TableCell>
                  {row.displayLabel ?? '—'}
                  {footer}
                </TableCell>
                {attributeColumns.map((code) => (
                  <TableCell key={code}>{String(row.attributes[code] ?? '')}</TableCell>
                ))}
                {selectActionLabel && (
                  <TableCell align="right">
                    <Button size="small" onClick={() => onSelectRow?.(row)}>
                      {selectActionLabel}
                    </Button>
                  </TableCell>
                )}
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
    </TableContainer>
  )
}
