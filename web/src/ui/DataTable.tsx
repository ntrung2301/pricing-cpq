import { SearchOutlined } from '@ant-design/icons'
import { Empty, Input, Pagination, Table, type TableProps } from 'antd'
import { useState, type Key, type ReactNode } from 'react'
import { Button } from './Button'

const pageSizeOptions = [10, 20, 25, 50, 100]
const sizeChanger = { 'aria-label': 'Rows per page' }
const formatRowCount = (total: number) => `${total} ${total === 1 ? 'record' : 'records'}`

type DataTableProps<Row extends object> = Omit<TableProps<Row>, 'pagination' | 'rowSelection'> & {
  rowKey: NonNullable<TableProps<Row>['rowKey']>
  search?: { label: string; getText: (row: Row) => string }
  filters?: ReactNode
  actions?: ReactNode
  pageSize?: number | false
  scrollRows?: boolean
  pinnedSelectionWidth?: number
  /** Enables checkbox selection and supplies accessible row labels. */
  getRowLabel?: (row: Row) => string
  selectionActions?: (keys: Key[]) => ReactNode
  emptyMessage?: ReactNode
}

export function DataTable<Row extends object>({
  rowKey, dataSource, search, filters, actions, pageSize = 20, scrollRows = false, getRowLabel,
  selectionActions, emptyMessage = 'No records to display.', onChange, scroll, pinnedSelectionWidth, ...props
}: DataTableProps<Row>) {
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(1)
  const [rowsPerPage, setRowsPerPage] = useState(pageSize || 20)
  const [selectedKeys, setSelectedKeys] = useState<Key[]>([])
  const rows = dataSource ?? []
  const queryText = query.trim().toLocaleLowerCase()
  const visibleRows = search && queryText
    ? rows.filter((row) => search.getText(row).toLocaleLowerCase().includes(queryText))
    : rows

  const availableKeys = new Set(rows.map((row) =>
    typeof rowKey === 'function' ? rowKey(row) : row[rowKey as keyof Row],
  ))
  const selected = selectedKeys.filter((key) => availableKeys.has(key))

  return (
    <div className={scrollRows ? 'ui-data-table ui-data-table--fit' : 'ui-data-table'}>
      {(search || filters || actions || selected.length > 0) && (
        <div className="ui-data-toolbar">
          <div className="ui-data-controls">
            {search && (
              <Input
                className="ui-data-search"
                aria-label={search.label}
                placeholder={search.label}
                prefix={<SearchOutlined aria-hidden="true" />}
                allowClear
                value={query}
                onChange={(event) => {
                  setQuery(event.target.value)
                  setPage(1)
                }}
              />
            )}
            {filters}
          </div>
          <div className="ui-data-actions">
            {selected.length > 0 && (
              <>
                <span role="status">{selected.length} selected</span>
                {selectionActions?.(selected)}
                <Button size="small" variant="quiet" onClick={() => setSelectedKeys([])}>
                  Clear selection
                </Button>
              </>
            )}
            {actions}
          </div>
        </div>
      )}
      <div className="ui-data-grid">
        <Table<Row>
          scroll={{ ...(scrollRows ? { y: 'none' } : { x: 'max-content' }), ...scroll }}
          {...props}
          showSorterTooltip={{ target: 'sorter-icon' }}
          rowKey={rowKey}
          dataSource={visibleRows}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={rows.length === 0 ? emptyMessage : 'No matching records.'}
              />
            ),
            ...props.locale,
          }}
          pagination={pageSize === false ? false : {
            current: page,
            pageSize: rowsPerPage,
            showSizeChanger: sizeChanger,
            showLessItems: scrollRows,
            size: scrollRows ? 'small' : undefined,
            pageSizeOptions,
            showTotal: formatRowCount,
            hideOnSinglePage: !scrollRows && rows.length === 0,
          }}
          onChange={(pagination, tableFilters, sorter, extra) => {
            setPage(extra.action === 'paginate' ? pagination.current ?? 1 : 1)
            if (pagination.pageSize) setRowsPerPage(pagination.pageSize)
            onChange?.(pagination, tableFilters, sorter, extra)
          }}
          // Table's native pagination is omitted when the filtered data has no rows.
          footer={scrollRows && pageSize !== false ? (pageRows) => (
            <>
              {props.footer?.(pageRows)}
              {pageRows.length === 0 && (
                <Pagination
                  className="ui-data-empty-pagination"
                  size="small"
                  total={0}
                  current={1}
                  pageSize={rowsPerPage}
                  pageSizeOptions={pageSizeOptions}
                  showSizeChanger={sizeChanger}
                  showTotal={formatRowCount}
                  onChange={(_, size) => {
                    setPage(1)
                    setRowsPerPage(size)
                  }}
                />
              )}
            </>
          ) : props.footer}
          rowSelection={getRowLabel ? {
            fixed: pinnedSelectionWidth === undefined ? undefined : 'start',
            columnWidth: pinnedSelectionWidth,
            selectedRowKeys: selected,
            preserveSelectedRowKeys: true,
            onChange: setSelectedKeys,
            getCheckboxProps: (row) => ({ 'aria-label': `Select ${getRowLabel(row)}` }),
          } : undefined}
        />
      </div>
    </div>
  )
}
