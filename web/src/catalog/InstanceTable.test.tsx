import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { InstanceTable } from './InstanceTable'
import type { EntityInstanceSummary } from './types'

// Generic [core] data table component (docs/ux/01-vehicle-part-selection.md
// section 2 "Bảng danh sách Part [core]"): columns come from whatever
// `attributes` keys the rows carry — never a hardcoded domain field name
// like "Part Number"/"OEM/Aftermarket". These two rows deliberately use
// non-automotive attribute keys to prove the component has no idea it is
// ever used for a "Product"/"Vehicle" entity_def.
const rows: EntityInstanceSummary[] = [
  {
    id: 1,
    businessKey: 'GZ-001',
    displayLabel: 'Gizmo One',
    attributes: { widgetColor: 'red', widgetWeight: 10 },
  },
  {
    id: 2,
    businessKey: 'GZ-002',
    displayLabel: 'Gizmo Two',
    attributes: { widgetColor: 'blue', widgetWeight: 20 },
  },
]

describe('InstanceTable', () => {
  it('renders businessKey/displayLabel and generic attribute columns derived from the data, not hardcoded', () => {
    render(<InstanceTable rows={rows} />)

    expect(screen.getByText('GZ-001')).toBeInTheDocument()
    expect(screen.getByText('Gizmo One')).toBeInTheDocument()
    expect(screen.getByText('widgetColor')).toBeInTheDocument()
    expect(screen.getByText('red')).toBeInTheDocument()
    expect(screen.getByText('20')).toBeInTheDocument()
  })

  it('shows the empty message passed in when there are no rows', () => {
    render(<InstanceTable rows={[]} emptyMessage="Nothing here" />)

    expect(screen.getByText('Nothing here')).toBeInTheDocument()
  })

  it('calls onSelectRow with the row when the select action button is clicked', async () => {
    const onSelectRow = vi.fn()
    render(
      <InstanceTable
        rows={rows}
        onSelectRow={onSelectRow}
        selectActionLabel="Chọn part này"
      />,
    )

    const buttons = screen.getAllByRole('button', { name: 'Chọn part này' })
    buttons[0].click()

    expect(onSelectRow).toHaveBeenCalledWith(rows[0])
  })

  it('highlights the selected row and renders the per-row footer content from renderRowFooter', () => {
    render(
      <InstanceTable
        rows={rows}
        selectedRowId={2}
        renderRowFooter={(row) => (row.id === 2 ? <span>row-2-hint</span> : null)}
      />,
    )

    expect(screen.getByText('row-2-hint')).toBeInTheDocument()
    const selectedRow = screen.getByRole('row', { name: /Gizmo Two/ })
    expect(selectedRow).toHaveAttribute('aria-selected', 'true')
  })
})
