import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactElement } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PriceListsPage } from './PriceListsPage'

// Component-level test for the new Price Lists browse page (list + detail
// drawer with the Calculation Trace) — written before trusting the screen
// works (CLAUDE.md TDD rule). Mocks only the HTTP boundary module
// (pricing-v2/priceListV2Api, the same real client the Measure Canvas UI
// already uses against the real S3-T6/S3-T8 backend) — everything else
// (table rendering, drawer open/close, trace display) is the real
// component code.

const listPriceLists = vi.fn()
const getPriceList = vi.fn()

vi.mock('../pricing-v2/priceListV2Api', () => ({
  listPriceLists: (...args: unknown[]) => listPriceLists(...args),
  getPriceList: (...args: unknown[]) => getPriceList(...args),
}))

const summaries = [
  {
    id: 1,
    code: 'PL-DEMO-BRK-PAD-NEW-001',
    name: 'Demo brake pad price list',
    currency: 'USD',
    status: 'APPROVED',
    validFrom: null,
    validTo: null,
    resultNodeId: 'rounded',
    version: 1,
    createdBy: 'seed',
    submittedAt: '2026-11-20T10:00:00Z',
    approvedBy: 'pricing-admin',
    approvedAt: '2026-11-20T10:05:00Z',
  },
]

const detail = {
  ...summaries[0],
  calculationGraph: [],
  items: [
    {
      id: 10,
      priceListId: 1,
      entityInstanceId: 100,
      resultPrice: '57.50',
      manualOverridePrice: null,
      validFrom: null,
      validTo: null,
      waterfallSteps: [
        { nodeId: 'q_cost', type: 'query', resolvedInputs: {}, output: '42.29' },
        { nodeId: 'rounded', type: 'round', resolvedInputs: { x: '57.0976' }, output: '57.50' },
      ],
    },
  ],
}

function renderWithQueryClient(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>)
}

function renderPage() {
  return renderWithQueryClient(<PriceListsPage accessToken="test-token" />)
}

describe('PriceListsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('lists every price list with its status', async () => {
    listPriceLists.mockResolvedValue(summaries)

    renderPage()

    expect(await screen.findByText('PL-DEMO-BRK-PAD-NEW-001')).toBeInTheDocument()
    expect(screen.getByText('APPROVED')).toBeInTheDocument()
  })

  it('opens a detail drawer with the item and its full calculation trace on click', async () => {
    listPriceLists.mockResolvedValue(summaries)
    getPriceList.mockResolvedValue(detail)
    const user = userEvent.setup()

    renderPage()

    await user.click(await screen.findByRole('button', { name: 'PL-DEMO-BRK-PAD-NEW-001' }))

    await waitFor(() => expect(getPriceList).toHaveBeenCalledWith('test-token', 1))
    expect((await screen.findAllByText('57.50')).length).toBeGreaterThan(0)
    expect(screen.getByText('q_cost')).toBeInTheDocument()
    expect(screen.getByText('rounded')).toBeInTheDocument()
  })

  it('shows an empty state when there are no price lists yet', async () => {
    listPriceLists.mockResolvedValue([])

    renderPage()

    expect(await screen.findByText(/no price lists/i)).toBeInTheDocument()
  })
})
