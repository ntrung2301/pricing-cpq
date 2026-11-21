import { ArrowDownOutlined, ArrowUpOutlined, TableOutlined } from '@ant-design/icons'
import { Checkbox, Popover } from 'antd'
import { useId, useState } from 'react'
import { Button } from '../ui'

interface EditableColumn {
  key: string
  label: string
  visible: boolean
}

interface ColumnsMenuProps<Column extends EditableColumn> {
  columns: Column[]
  onChange: (columns: Column[]) => void
  onReset: () => void
}

export function ColumnsMenu<Column extends EditableColumn>({
  columns, onChange, onReset,
}: ColumnsMenuProps<Column>) {
  const [open, setOpen] = useState(false)
  const panelId = useId()

  function moveColumn(index: number, direction: -1 | 1) {
    const target = index + direction
    if (target < 0 || target >= columns.length) return

    const reordered = [...columns]
    const column = reordered[index]
    reordered[index] = reordered[target]
    reordered[target] = column
    onChange(reordered)
  }

  return (
    <Popover
      placement="bottomLeft"
      trigger="click"
      open={open}
      onOpenChange={setOpen}
      title="Customize columns"
      content={(
        <div id={panelId} className="product-columns-menu" role="group" aria-label="Customize columns">
          <p>Checkbox, SKU, and Product are pinned.</p>
          <ul className="product-columns-list">
            {columns.map((column, index) => (
              <li key={column.key}>
                <Checkbox checked={column.visible} onChange={(event) =>
                  onChange(columns.map((item) => item.key === column.key
                    ? { ...item, visible: event.target.checked }
                    : item))
                }>
                  {column.label}
                </Checkbox>
                <div className="product-column-order">
                  <Button
                    size="small"
                    variant="quiet"
                    icon={<ArrowUpOutlined aria-hidden="true" />}
                    aria-label={`Move ${column.label} up`}
                    disabled={index === 0}
                    onClick={() => moveColumn(index, -1)}
                  />
                  <Button
                    size="small"
                    variant="quiet"
                    icon={<ArrowDownOutlined aria-hidden="true" />}
                    aria-label={`Move ${column.label} down`}
                    disabled={index === columns.length - 1}
                    onClick={() => moveColumn(index, 1)}
                  />
                </div>
              </li>
            ))}
          </ul>
          <Button size="small" variant="quiet" onClick={onReset}>Reset columns</Button>
        </div>
      )}
    >
      <Button
        icon={<TableOutlined aria-hidden="true" />}
        aria-expanded={open}
        aria-controls={open ? panelId : undefined}
      >
        Columns
      </Button>
    </Popover>
  )
}
