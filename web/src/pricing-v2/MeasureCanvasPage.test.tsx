import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { UIProvider } from '../ui'
import { MeasureCanvasPage } from './MeasureCanvasPage'

// S3-T8: component-level test for the Measure Canvas drag-and-drop ->
// graph-build -> create-Price-List flow, written before trusting the
// screen works (CLAUDE.md TDD rule). Mocks only the two HTTP boundary
// modules (catalogApi/priceListV2Api) — everything else (drag/drop
// handling, graph building via graphBuilder.ts, modal form state) is the
// real component code. The real-backend flow itself is proven separately
// by the Playwright E2E spec (e2e/tests/measure-canvas.spec.ts), which
// mocks nothing.

vi.mock('../catalog/catalogApi', () => ({
  fetchInstances: vi.fn().mockResolvedValue({
    content: [{ id: 1, businessKey: 'BRK-PAD-NEW-001', displayLabel: 'BRK-PAD-NEW-001', attributes: {} }],
    page: 0,
    size: 100,
    totalElements: 1,
    totalPages: 1,
  }),
}))

const createPriceList = vi.fn().mockResolvedValue({
  id: 1,
  code: 'PLV2_TEST',
  name: 'Test',
  currency: 'USD',
  status: 'DRAFT',
  validFrom: null,
  validTo: null,
  resultNodeId: 'f_1',
  version: 1,
  createdBy: 'tester',
  submittedAt: null,
  approvedBy: null,
  approvedAt: null,
})

vi.mock('./priceListV2Api', () => ({
  createPriceList: (...args: unknown[]) => createPriceList(...args),
  addPriceListItem: vi.fn(),
  submitPriceList: vi.fn(),
  approvePriceList: vi.fn(),
  getPriceList: vi.fn(),
  overridePriceListItemPrice: vi.fn(),
}))

function dropPayload(payload: unknown) {
  return {
    dataTransfer: {
      getData: () => JSON.stringify(payload),
      setData: () => {},
    },
  }
}

describe('MeasureCanvasPage', () => {
  beforeEach(() => {
    createPriceList.mockClear()
  })

  it('renders the Data source / Calculation node palette', () => {
    render(
      <UIProvider>
        <MeasureCanvasPage accessToken="token" />
      </UIProvider>,
    )

    expect(screen.getByText('Product — Country of Manufacture')).toBeInTheDocument()
    expect(screen.getByText('Customer — Tier')).toBeInTheDocument()
    expect(screen.getByText('Formula')).toBeInTheDocument()
    expect(screen.getByText('Round')).toBeInTheDocument()
    expect(screen.getByText('Output')).toBeInTheDocument()
  })

  it('dropping a Formula chip onto the canvas opens a config modal, and confirming it adds the node to the graph', async () => {
    const user = userEvent.setup()
    render(
      <UIProvider>
        <MeasureCanvasPage accessToken="token" />
      </UIProvider>,
    )

    const canvas = screen.getByTestId('measure-canvas-flow')
    fireEvent.drop(canvas, dropPayload({ paletteKind: 'formula' }))

    expect(await screen.findByText('Configure formula node')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Label'), 'Base cost')
    await user.click(screen.getByRole('button', { name: 'OK' }))

    await waitFor(() => expect(screen.getByText('Base cost')).toBeInTheDocument())
  })

  it('building a 2-node graph (formula -> round) and clicking "Tạo Price List" calls createPriceList with the correct DagNodeDto[]/resultNodeId', async () => {
    const user = userEvent.setup()
    render(
      <UIProvider>
        <MeasureCanvasPage accessToken="token" />
      </UIProvider>,
    )

    const canvas = screen.getByTestId('measure-canvas-flow')

    fireEvent.drop(canvas, dropPayload({ paletteKind: 'formula' }))
    await screen.findByText('Configure formula node')
    await user.type(screen.getByLabelText('Label'), 'Base cost')
    await user.click(screen.getByRole('button', { name: 'OK' }))
    await waitFor(() => expect(screen.getByText('Base cost')).toBeInTheDocument())

    fireEvent.drop(canvas, dropPayload({ paletteKind: 'round' }))
    await screen.findByText('Configure round node')
    await user.type(screen.getByLabelText('Label'), 'Round price')
    // Select the "Input node" option (antd Select) -> pick "Base cost".
    const inputNodeSelect = screen.getByLabelText('Input node')
    await user.click(inputNodeSelect)
    await user.click(await screen.findByTitle('Base cost'))
    await user.click(screen.getByRole('button', { name: 'OK' }))
    await waitFor(() => expect(screen.getByText('Round price')).toBeInTheDocument())

    await user.type(screen.getByLabelText('Code'), 'PLV2_E2E_TEST')
    await user.type(screen.getByLabelText('Name'), 'E2E Test Price List')
    await user.click(screen.getByRole('button', { name: 'Tạo Price List' }))

    await waitFor(() => expect(createPriceList).toHaveBeenCalledTimes(1))
    const [, request] = createPriceList.mock.calls[0]
    expect(request.resultNodeId).toMatch(/^r_/)
    expect(request.calculationGraph).toHaveLength(2)
    expect(request.calculationGraph[0].type).toBe('formula')
    expect(request.calculationGraph[1].type).toBe('round')
    expect(request.calculationGraph[1].inputs).toEqual({ value: request.calculationGraph[0].id })
  })
})
