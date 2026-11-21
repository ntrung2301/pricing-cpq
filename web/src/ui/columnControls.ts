import type { TableColumnType } from 'antd'

type CellValue = string | number | boolean | null | undefined

/** Apply native Ant Design sorting and compact filters to a scalar column. */
export function columnControls<Row>(
  rows: readonly Row[],
  getValue: (row: Row) => CellValue,
  filterable = false,
): Pick<TableColumnType<Row>, 'sorter' | 'filters' | 'filterSearch' | 'onFilter'> {
  const collator = new Intl.Collator(undefined, { numeric: true, sensitivity: 'base' })
  const sorter = (left: Row, right: Row) => {
    const a = getValue(left)
    const b = getValue(right)
    return typeof a === 'number' && typeof b === 'number'
      ? a - b
      : collator.compare(String(a ?? ''), String(b ?? ''))
  }
  if (!filterable) return { sorter }

  const labels = rows.map((row) => String(getValue(row) ?? '')).filter(Boolean)
  const options = [...new Set(labels)]
  if (options.length === 0 || options.length > 12) return { sorter }

  return {
    sorter,
    filterSearch: true,
    filters: options.sort(collator.compare).map((value) => ({ text: value, value })),
    onFilter: (value, row) => String(getValue(row)) === value,
  }
}
