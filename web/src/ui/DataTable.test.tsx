import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it } from 'vitest'
import { DataTable, columnControls, type TableColumnsType } from './index'

const rows = [
  { id: 1, name: 'Widget', version: 2 },
  { id: 2, name: 'Bracket', version: 1 },
  { id: 3, name: 'Bearing', version: 2 },
]
const columns: TableColumnsType<(typeof rows)[number]> = [{
  title: 'Name', dataIndex: 'name', sorter: (a, b) => a.name.localeCompare(b.name),
}]

it('paginates, resets to the first page on search, and keeps checkbox selections across pages', async () => {
  const user = userEvent.setup()
  render(<DataTable
    rowKey="id"
    dataSource={rows}
    columns={columns}
    pageSize={2}
    getRowLabel={(row) => row.name}
    search={{ label: 'Search inventory', getText: (row) => row.name }}
  />)

  await user.click(screen.getByRole('checkbox', { name: 'Select Widget' }))
  await user.click(screen.getByRole('button', { name: 'Page 2' }))
  expect(screen.getByText('Bearing')).toBeInTheDocument()
  expect(screen.getByText('1 selected')).toBeInTheDocument()

  await user.type(screen.getByRole('textbox', { name: 'Search inventory' }), 'w')
  expect(screen.getByRole('checkbox', { name: 'Select Widget' })).toBeChecked()

  await user.click(screen.getByRole('button', { name: 'Clear selection' }))
  expect(screen.getByRole('checkbox', { name: 'Select Widget' })).not.toBeChecked()
})

it('sorts the data before pagination', async () => {
  render(<DataTable rowKey="id" dataSource={rows} columns={columns} pageSize={2} />)
  await userEvent.click(screen.getByRole('columnheader', { name: /name/i }))
  const table = screen.getByRole('table')
  expect(within(table).getAllByRole('row')[1]).toHaveTextContent('Bearing')
  expect(within(table).queryByText('Widget')).not.toBeInTheDocument()
})

it('filters with the shared native column controls', async () => {
  const user = userEvent.setup()
  render(<DataTable rowKey="id" dataSource={rows} columns={[
    ...columns,
    { title: 'Version', dataIndex: 'version', ...columnControls(rows, (row) => row.version, true) },
  ]} />)

  await user.click(screen.getByRole('button', { name: 'filter' }))
  await user.type(await screen.findByPlaceholderText('Search in filters'), '2')
  expect(screen.queryByRole('menuitem', { name: '1' })).not.toBeInTheDocument()
  await user.click(await screen.findByRole('menuitem', { name: '2' }))
  await user.click(screen.getByRole('button', { name: 'OK' }))
  const table = screen.getByRole('table')
  expect(within(table).getByText('Widget')).toBeInTheDocument()
  expect(within(table).queryByText('Bracket')).not.toBeInTheDocument()
})

it('offers a page-size selector in a fixed-height table, even when search has no results', async () => {
  const user = userEvent.setup()
  render(<DataTable
    scrollRows
    rowKey="id"
    dataSource={rows}
    columns={columns}
    pageSize={2}
    search={{ label: 'Search inventory', getText: (row) => row.name }}
  />)

  const pageSize = screen.getByRole('combobox', { name: 'Rows per page' })
  await user.click(pageSize)
  await user.click(await screen.findByText('10 / page'))
  expect(screen.getByText('Bearing')).toBeInTheDocument()

  await user.type(screen.getByRole('textbox', { name: 'Search inventory' }), 'z')
  expect(screen.getByText('No matching records.')).toBeInTheDocument()
  expect(screen.getByRole('combobox', { name: 'Rows per page' })).toBeInTheDocument()
  expect(screen.getByText('0 records')).toBeInTheDocument()
})
