import { useMemo, useRef, useState, type DragEvent } from 'react'
import { Form, Input, InputNumber, Modal, Radio, Space, Tag } from 'antd'
import { Button, DataPage, Field, Select, StatusPill } from '../ui'
import { fetchInstances } from '../catalog/catalogApi'
import type { EntityInstanceSummary } from '../catalog/types'
import { BINDING_PRESETS } from './bindingPresets'
import {
  buildCalculationGraph,
  GraphBuildError,
  type CanvasNode,
  type Operand,
} from './graphBuilder'
import {
  addPriceListItem,
  approvePriceList,
  createPriceList,
  getPriceList,
  submitPriceList,
} from './priceListV2Api'
import type { PriceListDetailDto, PriceListDto, PriceListItemDto, TraceEntryDto } from './types'

type PaletteKind = CanvasNode['kind']

interface DragPayload {
  paletteKind: PaletteKind
  presetIndex?: number
}

const numericKinds = new Set<PaletteKind>(['formula', 'round'])

function formatValue(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

/**
 * Measure Canvas (S3-T8, backlog E8-06): context switcher + drag-and-drop
 * Data source/calculation palette + canvas + Calculation Trace panel,
 * built with Ant Design (D-036) on top of the real S3-T6/S3-T8
 * `/api/price-lists-v2/...` backend. No mocking anywhere in this
 * component — every button calls the real API.
 *
 * The canvas is deliberately a single ordered chain (append-only), not a
 * free-form node editor with manual port wiring — see docs/decisions.md
 * D-049 for why ("không cần kéo-thả hoàn hảo... đừng over-engineer").
 */
export function MeasureCanvasPage({ accessToken }: { accessToken: string }) {
  const [productOptions, setProductOptions] = useState<EntityInstanceSummary[]>([])
  const [productsLoaded, setProductsLoaded] = useState(false)
  const [customerOptions, setCustomerOptions] = useState<EntityInstanceSummary[]>([])
  const [customersLoaded, setCustomersLoaded] = useState(false)
  const [contextProductId, setContextProductId] = useState<number | null>(null)

  const [nodes, setNodes] = useState<CanvasNode[]>([])
  const nodeCounter = useRef(0)
  const [pendingDrop, setPendingDrop] = useState<PaletteKind | null>(null)
  const [dropHint, setDropHint] = useState<string | null>(null)
  // Remounts NodeConfigModal with fresh internal state on every drop — it
  // is otherwise a single long-lived component instance (only `kind`
  // changes), so without this its local useState (operator/operand
  // values/etc) would leak stale values from the previous node configured
  // into the next one.
  const [modalInstanceKey, setModalInstanceKey] = useState(0)

  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [priceList, setPriceList] = useState<PriceListDto | null>(null)
  const [item, setItem] = useState<PriceListItemDto | null>(null)
  const [detail, setDetail] = useState<PriceListDetailDto | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [expandedNodeId, setExpandedNodeId] = useState<string | null>(null)

  const numericNodes = useMemo(() => nodes.filter((n) => numericKinds.has(n.kind)), [nodes])

  async function ensureProductsLoaded() {
    if (productsLoaded) return
    const page = await fetchInstances(accessToken, 'Product', { size: 100 })
    setProductOptions(page.content)
    setProductsLoaded(true)
  }

  async function ensureCustomersLoaded() {
    if (customersLoaded) return
    const page = await fetchInstances(accessToken, 'Customer', { size: 100 })
    setCustomerOptions(page.content)
    setCustomersLoaded(true)
  }

  function nextId(prefix: string) {
    nodeCounter.current += 1
    return `${prefix}_${nodeCounter.current}`
  }

  function onPaletteDragStart(event: DragEvent, payload: DragPayload) {
    event.dataTransfer.setData('application/json', JSON.stringify(payload))
    event.dataTransfer.effectAllowed = 'copy'
  }

  function onCanvasDrop(event: DragEvent) {
    event.preventDefault()
    const raw = event.dataTransfer.getData('application/json')
    if (!raw) return
    const payload = JSON.parse(raw) as DragPayload
    if (payload.paletteKind === 'source') {
      void ensureCustomersLoaded()
    }
    setPendingDrop(payload.paletteKind)
    setDropHint(null)
    setModalInstanceKey((key) => key + 1)
    pendingPresetIndex.current = payload.presetIndex ?? 0
  }

  const pendingPresetIndex = useRef(0)

  function addNode(node: CanvasNode) {
    setNodes((current) => [...current, node])
    setPendingDrop(null)
    setDropHint(`Added "${node.label}" to the graph.`)
  }

  async function onCreatePriceList() {
    setError(null)
    try {
      const { calculationGraph, resultNodeId } = buildCalculationGraph(nodes)
      setBusy(true)
      const created = await createPriceList(accessToken, {
        code,
        name,
        currency: 'USD',
        validFrom: null,
        validTo: null,
        calculationGraph,
        resultNodeId,
      })
      setPriceList(created)
    } catch (err) {
      setError(err instanceof GraphBuildError ? err.message : (err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  async function onAddItem() {
    if (!priceList || contextProductId === null) return
    setError(null)
    setBusy(true)
    try {
      const addedItem = await addPriceListItem(accessToken, priceList.id, {
        entityInstanceId: contextProductId,
      })
      setItem(addedItem)
      const refreshed = await getPriceList(accessToken, priceList.id)
      setDetail(refreshed)
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  async function onSubmit() {
    if (!priceList) return
    setBusy(true)
    setError(null)
    try {
      setPriceList(await submitPriceList(accessToken, priceList.id))
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  async function onApprove() {
    if (!priceList) return
    setBusy(true)
    setError(null)
    try {
      setPriceList(await approvePriceList(accessToken, priceList.id))
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const traceByNodeId = useMemo(() => {
    const map = new Map<string, TraceEntryDto>()
    for (const entry of item?.waterfallSteps ?? []) {
      map.set(entry.nodeId, entry)
    }
    return map
  }, [item])

  function toggleExpanded(nodeId: string) {
    setExpandedNodeId((current) => (current === nodeId ? null : nodeId))
  }

  return (
    <DataPage title="Measure Canvas" description="Price List Logic Builder — ADR-009 DAG, kéo-thả Data source/khối tính toán, xem Calculation Trace thật.">
      <div className="measure-canvas-context" style={{ display: 'flex', gap: 12, alignItems: 'end', marginBottom: 16 }}>
        <Field label="Context — Product (SKU)">
          {(props) => (
            <Select
              {...props}
              showSearch
              virtual={false}
              optionFilterProp="label"
              style={{ width: 280 }}
              placeholder="Chọn Product"
              value={contextProductId ?? undefined}
              onOpenChange={(open) => open && void ensureProductsLoaded()}
              onChange={(value) => setContextProductId(value as number)}
              options={productOptions.map((p) => ({
                value: p.id,
                label: p.displayLabel ?? p.businessKey ?? `#${p.id}`,
              }))}
            />
          )}
        </Field>
      </div>

      <div className="measure-canvas-workbench" style={{ display: 'grid', gridTemplateColumns: '220px 1fr 320px', gap: 16 }}>
        <div className="measure-canvas-palette" style={{ border: '1px solid #e8e8e8', borderRadius: 8, padding: 12 }}>
          <h3>Data sources</h3>
          {BINDING_PRESETS.map((preset, index) => (
            <div
              key={preset.bindingAttributeCode}
              draggable
              role="button"
              aria-label={`Drag ${preset.label}`}
              onDragStart={(event) => onPaletteDragStart(event, { paletteKind: 'source', presetIndex: index })}
              style={{ padding: 8, border: '1px solid #d9d9d9', borderRadius: 6, marginBottom: 6, cursor: 'grab' }}
            >
              {preset.label}
            </div>
          ))}

          <h3>Calculation nodes</h3>
          {(['formula', 'round', 'output'] as PaletteKind[]).map((kind) => (
            <div
              key={kind}
              draggable
              role="button"
              aria-label={`Drag ${kind}`}
              onDragStart={(event) => onPaletteDragStart(event, { paletteKind: kind })}
              style={{ padding: 8, border: '1px solid #d9d9d9', borderRadius: 6, marginBottom: 6, cursor: 'grab' }}
            >
              {kind === 'formula' ? 'Formula' : kind === 'round' ? 'Round' : 'Output'}
            </div>
          ))}
        </div>

        <div
          className="measure-canvas-flow"
          data-testid="measure-canvas-flow"
          onDragOver={(event) => event.preventDefault()}
          onDrop={onCanvasDrop}
          style={{ border: '1px solid #d9d9d9', borderRadius: 8, padding: 12, minHeight: 240 }}
        >
          <h3>Price List Logic</h3>
          {nodes.length === 0 && <p>Drop a palette item here to add it to this graph.</p>}
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            {nodes.map((node) => {
              const trace = traceByNodeId.get(node.id)
              const expanded = expandedNodeId === node.id
              return (
                <div
                  key={node.id}
                  role="button"
                  aria-expanded={expanded}
                  onClick={() => toggleExpanded(node.id)}
                  data-testid={`canvas-node-${node.id}`}
                  style={{
                    border: '1px solid #d9d9d9',
                    borderRadius: 8,
                    padding: 10,
                    minWidth: 160,
                    cursor: 'pointer',
                    background: expanded ? '#f0f7f4' : '#fff',
                  }}
                >
                  <Tag>{node.kind}</Tag>
                  <div style={{ fontWeight: 600 }}>{node.label}</div>
                  <div data-testid={`canvas-node-value-${node.id}`} style={{ fontFamily: 'monospace' }}>
                    {trace ? formatValue(trace.output) : '—'}
                  </div>
                  {expanded && trace && (
                    <pre style={{ fontSize: 11, marginTop: 6, whiteSpace: 'pre-wrap' }}>
                      {JSON.stringify(trace.resolvedInputs, null, 2)}
                    </pre>
                  )}
                </div>
              )
            })}
          </div>
          {dropHint && <p role="status">{dropHint}</p>}
        </div>

        <div className="measure-canvas-trace" style={{ border: '1px solid #e8e8e8', borderRadius: 8, padding: 12 }}>
          <h3>Calculation Trace</h3>
          {!item && <p>Add this Price List's item to see the real Calculation Trace.</p>}
          {item?.waterfallSteps.map((entry) => (
            <div
              key={entry.nodeId}
              role="button"
              data-testid={`trace-row-${entry.nodeId}`}
              onClick={() => toggleExpanded(entry.nodeId)}
              style={{
                padding: '6px 8px',
                borderRadius: 6,
                cursor: 'pointer',
                background: expandedNodeId === entry.nodeId ? '#f0f7f4' : undefined,
              }}
            >
              <span style={{ fontFamily: 'monospace', fontSize: 11, color: '#888' }}>{entry.type}</span>{' '}
              <strong>{entry.nodeId}</strong>{' '}
              <span style={{ fontFamily: 'monospace' }}>{formatValue(entry.output)}</span>
            </div>
          ))}
          {item && (
            <div style={{ marginTop: 10, borderTop: '1px solid #e8e8e8', paddingTop: 10 }}>
              <strong>Result price: </strong>
              <span data-testid="trace-result-price">{formatValue(item.resultPrice)}</span>
            </div>
          )}
          {detail && (
            <p data-testid="trace-loaded-via-get" style={{ fontSize: 11, color: '#888', marginTop: 8 }}>
              Loaded via GET /api/price-lists-v2/{priceList?.id}: {detail.items.length} item(s),{' '}
              {detail.calculationGraph.length} graph node(s).
            </p>
          )}
        </div>
      </div>

      <div style={{ marginTop: 20, display: 'flex', gap: 12, alignItems: 'end', flexWrap: 'wrap' }}>
        {!priceList && (
          <>
            <Field label="Code">{(props) => <Input {...props} value={code} onChange={(e) => setCode(e.target.value)} />}</Field>
            <Field label="Name">{(props) => <Input {...props} value={name} onChange={(e) => setName(e.target.value)} />}</Field>
            <Button variant="primary" loading={busy} disabled={!code || !name} onClick={() => void onCreatePriceList()}>
              Tạo Price List
            </Button>
          </>
        )}

        {priceList && (
          <>
            <StatusPill tone="brand">{priceList.status}</StatusPill>
            {!item && (
              <Button variant="primary" loading={busy} disabled={contextProductId === null} onClick={() => void onAddItem()}>
                Thêm Product vào Price List
              </Button>
            )}
            {item && priceList.status === 'DRAFT' && (
              <Button variant="primary" loading={busy} onClick={() => void onSubmit()}>
                Submit
              </Button>
            )}
            {priceList.status === 'PENDING_APPROVAL' && (
              <Button variant="primary" loading={busy} onClick={() => void onApprove()}>
                Approve
              </Button>
            )}
          </>
        )}
      </div>

      {error && <p role="alert" style={{ color: 'red' }}>{error}</p>}

      <NodeConfigModal
        key={modalInstanceKey}
        kind={pendingDrop}
        presetIndex={pendingPresetIndex.current}
        numericNodes={numericNodes}
        allNodes={nodes}
        customerOptions={customerOptions}
        onCancel={() => setPendingDrop(null)}
        onSubmit={(node) => addNode(node)}
        nextId={nextId}
      />
    </DataPage>
  )
}

function NodeConfigModal({
  kind,
  presetIndex,
  numericNodes,
  allNodes,
  customerOptions,
  onCancel,
  onSubmit,
  nextId,
}: {
  kind: PaletteKind | null
  presetIndex: number
  numericNodes: CanvasNode[]
  allNodes: CanvasNode[]
  customerOptions: EntityInstanceSummary[]
  onCancel: () => void
  onSubmit: (node: CanvasNode) => void
  nextId: (prefix: string) => string
}) {
  const [label, setLabel] = useState('')
  const [operator, setOperator] = useState<'+' | '-' | '*' | '/'>('+')
  const [operandAType, setOperandAType] = useState<'literal' | 'ref'>('literal')
  const [operandAValue, setOperandAValue] = useState<number>(0)
  const [operandARef, setOperandARef] = useState<string | undefined>(undefined)
  const [operandBType, setOperandBType] = useState<'literal' | 'ref'>('literal')
  const [operandBValue, setOperandBValue] = useState<number>(1)
  const [operandBRef, setOperandBRef] = useState<string | undefined>(undefined)
  const [nearest, setNearest] = useState<number>(1)
  const [inputNodeId, setInputNodeId] = useState<string | undefined>(undefined)
  const [anchorCustomerId, setAnchorCustomerId] = useState<number | undefined>(undefined)
  const [outputFields, setOutputFields] = useState<string[]>([])

  if (!kind) return null

  const preset = BINDING_PRESETS[presetIndex]

  function resetAndClose() {
    setLabel('')
    setOperandARef(undefined)
    setOperandBRef(undefined)
    setInputNodeId(undefined)
    setAnchorCustomerId(undefined)
    setOutputFields([])
    onCancel()
  }

  function operand(type: 'literal' | 'ref', literalValue: number, ref: string | undefined): Operand {
    return type === 'literal' ? { type: 'literal', value: literalValue } : { type: 'ref', nodeId: ref as string }
  }

  function handleOk() {
    if (kind === 'source') {
      onSubmit({
        id: nextId('src'),
        kind: 'source',
        label: label || preset.label,
        bindingAttributeCode: preset.bindingAttributeCode,
        sourceType: preset.sourceType,
        anchorInstanceId: preset.requiresAnchorPicker ? anchorCustomerId : undefined,
      })
    } else if (kind === 'formula') {
      onSubmit({
        id: nextId('f'),
        kind: 'formula',
        label: label || 'Formula',
        operator,
        operandA: operand(operandAType, operandAValue, operandARef),
        operandB: operand(operandBType, operandBValue, operandBRef),
      })
    } else if (kind === 'round') {
      if (!inputNodeId) return
      onSubmit({
        id: nextId('r'),
        kind: 'round',
        label: label || 'Round to nearest',
        nearest,
        inputNodeId,
      })
    } else {
      onSubmit({
        id: nextId('out'),
        kind: 'output',
        label: label || 'Output',
        fields: outputFields.map((nodeId) => ({
          label: allNodes.find((n) => n.id === nodeId)?.label.replace(/\s+/g, '_') ?? nodeId,
          nodeId,
        })),
      })
    }
    resetAndClose()
  }

  return (
    <Modal
      title={`Configure ${kind} node`}
      open={kind !== null}
      onCancel={resetAndClose}
      onOk={handleOk}
      okButtonProps={{ disabled: kind === 'round' && !inputNodeId }}
    >
      <Form layout="vertical">
        <Form.Item label="Label" htmlFor="node-config-label">
          <Input
            id="node-config-label"
            value={label}
            onChange={(e) => setLabel(e.target.value)}
            placeholder={kind === 'source' ? preset?.label : kind}
          />
        </Form.Item>

        {kind === 'source' && preset.requiresAnchorPicker && (
          <Form.Item label="Customer" htmlFor="node-config-customer">
            <Select
              id="node-config-customer"
              showSearch
              virtual={false}
              optionFilterProp="label"
              style={{ width: '100%' }}
              value={anchorCustomerId}
              onChange={(value) => setAnchorCustomerId(value as number)}
              options={customerOptions.map((c) => ({ value: c.id, label: c.displayLabel ?? c.businessKey ?? `#${c.id}` }))}
            />
          </Form.Item>
        )}

        {kind === 'formula' && (
          <>
            <Form.Item label="Operator">
              <Radio.Group value={operator} onChange={(e) => setOperator(e.target.value)}>
                <Radio.Button value="+">+</Radio.Button>
                <Radio.Button value="-">−</Radio.Button>
                <Radio.Button value="*">×</Radio.Button>
                <Radio.Button value="/">÷</Radio.Button>
              </Radio.Group>
            </Form.Item>
            <Form.Item label="Operand A">
              <Space>
                <Radio.Group value={operandAType} onChange={(e) => setOperandAType(e.target.value)}>
                  <Radio.Button value="literal">Số</Radio.Button>
                  <Radio.Button value="ref">Node</Radio.Button>
                </Radio.Group>
                {operandAType === 'literal' ? (
                  <InputNumber value={operandAValue} onChange={(v) => setOperandAValue(Number(v))} />
                ) : (
                  <Select
                    style={{ width: 160 }}
                    virtual={false}
                    value={operandARef}
                    onChange={(v) => setOperandARef(v as string)}
                    options={numericNodes.map((n) => ({ value: n.id, label: n.label }))}
                  />
                )}
              </Space>
            </Form.Item>
            <Form.Item label="Operand B">
              <Space>
                <Radio.Group value={operandBType} onChange={(e) => setOperandBType(e.target.value)}>
                  <Radio.Button value="literal">Số</Radio.Button>
                  <Radio.Button value="ref">Node</Radio.Button>
                </Radio.Group>
                {operandBType === 'literal' ? (
                  <InputNumber value={operandBValue} onChange={(v) => setOperandBValue(Number(v))} />
                ) : (
                  <Select
                    style={{ width: 160 }}
                    virtual={false}
                    value={operandBRef}
                    onChange={(v) => setOperandBRef(v as string)}
                    options={numericNodes.map((n) => ({ value: n.id, label: n.label }))}
                  />
                )}
              </Space>
            </Form.Item>
          </>
        )}

        {kind === 'round' && (
          <>
            <Form.Item label="Nearest" htmlFor="node-config-nearest">
              <InputNumber id="node-config-nearest" value={nearest} min={0.01} onChange={(v) => setNearest(Number(v))} />
            </Form.Item>
            <Form.Item label="Input node" htmlFor="node-config-input-node">
              <Select
                id="node-config-input-node"
                style={{ width: '100%' }}
                virtual={false}
                value={inputNodeId}
                onChange={(v) => setInputNodeId(v as string)}
                options={numericNodes.map((n) => ({ value: n.id, label: n.label }))}
              />
            </Form.Item>
          </>
        )}

        {kind === 'output' && (
          <Form.Item label="Fields to display" htmlFor="node-config-output-fields">
            <Select
              id="node-config-output-fields"
              mode="multiple"
              style={{ width: '100%' }}
              virtual={false}
              value={outputFields}
              onChange={(v) => setOutputFields(v as string[])}
              options={allNodes.map((n) => ({ value: n.id, label: n.label }))}
            />
          </Form.Item>
        )}
      </Form>
    </Modal>
  )
}
