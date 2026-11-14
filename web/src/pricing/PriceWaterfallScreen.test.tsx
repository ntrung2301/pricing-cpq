import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactElement } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as catalogApi from '../catalog/catalogApi'
import type { EntityInstancePageDto, EntityInstanceSummary } from '../catalog/types'
import * as entitiesApi from '../entities/entitiesApi'
import { PriceWaterfallScreen } from './PriceWaterfallScreen'
import * as pricingApi from './pricingApi'
import type { PriceCalculationResponse } from './types'

// S2-T13 (docs/sprints/sprint-2.md) — "giá + waterfall", nối tiếp từ
// "Chọn part này" của S2-T9 (docs/ux/03-pricing-waterfall.md). No network
// mocking at the fetch layer here (that's e2e/tests/vehicle-part-selection.spec.ts's
// job, real stack, no mock) — this test mocks only the thin API wrapper
// functions, same pattern as VehiclePartSelectionScreen.test.tsx.

const SELECTED_PRODUCT: EntityInstanceSummary = {
  id: 1,
  businessKey: 'BRK-PAD-NEW-001',
  displayLabel: 'Brake Pad Set Front',
  attributes: { oemOrAftermarket: 'AFTERMARKET' },
}

const ENTITY_DEFS = [
  { id: 99, code: 'Product', label: 'Product', version: 1, attributes: [] },
  { id: 98, code: 'Vehicle', label: 'Vehicle', version: 1, attributes: [] },
]

const CUSTOMERS_PAGE: EntityInstancePageDto = {
  content: [
    {
      id: 301,
      businessKey: 'CUST-OEM-001',
      displayLabel: 'Acme Motors OEM Division',
      attributes: { tier: 'OEM' },
    },
    {
      id: 302,
      businessKey: 'CUST-DLR-001',
      displayLabel: 'Downtown Auto Dealership',
      attributes: { tier: 'DEALER' },
    },
  ],
  page: 0,
  size: 100,
  totalElements: 2,
  totalPages: 1,
}

const PRICE_RESPONSE: PriceCalculationResponse = {
  steps: [
    { stepOrder: 1, stage: 'base', ruleId: null, label: 'List price', deltaAmount: 100, resultingAmount: 100, rounded: false },
    {
      stepOrder: 2,
      stage: 'segment-adjustment',
      ruleId: 7,
      label: 'Tier adjustment (OEM -15%)',
      deltaAmount: -15,
      resultingAmount: 85,
      rounded: true,
    },
  ],
  finalAmount: 85,
}

function renderScreen(ui: ReactElement = <PriceWaterfallScreen accessToken="fake-token" product={SELECTED_PRODUCT} />) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>)
}

describe('PriceWaterfallScreen', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(entitiesApi, 'fetchEntities').mockResolvedValue(ENTITY_DEFS)
    vi.spyOn(catalogApi, 'fetchInstances').mockImplementation(async (_token, entityDefCode) => {
      if (entityDefCode === 'Customer') {
        return CUSTOMERS_PAGE
      }
      throw new Error(`unexpected entityDefCode in test: ${entityDefCode}`)
    })
  })

  it('shows a hint instead of calling the pricing API until a customer is chosen', async () => {
    const calculateSpy = vi.spyOn(pricingApi, 'calculatePrice')

    renderScreen()

    await waitFor(() => {
      expect(screen.getByRole('combobox', { name: /khách hàng/i })).toBeInTheDocument()
    })
    expect(screen.getByText(/chọn khách hàng để tính giá/i)).toBeInTheDocument()
    expect(calculateSpy).not.toHaveBeenCalled()
  })

  it('calculates the price with quantity=1 default once a customer is chosen, and renders the waterfall', async () => {
    const calculateSpy = vi.spyOn(pricingApi, 'calculatePrice').mockResolvedValue(PRICE_RESPONSE)

    const user = userEvent.setup()
    renderScreen()

    await waitFor(() => {
      expect(screen.getByRole('combobox', { name: /khách hàng/i })).toBeInTheDocument()
    })
    await user.click(screen.getByRole('combobox', { name: /khách hàng/i }))
    await user.click(await screen.findByRole('option', { name: /acme motors oem division/i }))

    await waitFor(() => {
      expect(calculateSpy).toHaveBeenCalledWith('fake-token', {
        ruleSetCode: 'AUTOMOTIVE-STANDARD',
        priceListCode: 'AUTOMOTIVE-DEFAULT',
        entityDefId: 99,
        entityInstanceId: 1,
        quantity: 1,
        attributes: {},
        customerInstanceId: 301,
        contractOverridePrice: null,
      })
    })

    expect(screen.getByText('List price')).toBeInTheDocument()
    expect(screen.getByText('Tier adjustment (OEM -15%)')).toBeInTheDocument()
    expect(screen.getAllByText(/85/).length).toBeGreaterThan(0)
  })

  it('includes an updated quantity and contract override price in the request', async () => {
    const calculateSpy = vi.spyOn(pricingApi, 'calculatePrice').mockResolvedValue(PRICE_RESPONSE)

    const user = userEvent.setup()
    renderScreen()

    await waitFor(() => {
      expect(screen.getByRole('combobox', { name: /khách hàng/i })).toBeInTheDocument()
    })
    await user.click(screen.getByRole('combobox', { name: /khách hàng/i }))
    await user.click(await screen.findByRole('option', { name: /acme motors oem division/i }))

    const quantityInput = screen.getByRole('spinbutton', { name: /số lượng/i })
    await user.clear(quantityInput)
    await user.type(quantityInput, '3')

    const contractInput = screen.getByRole('spinbutton', { name: /giá hợp đồng riêng/i })
    await user.type(contractInput, '77.77')

    await waitFor(() => {
      expect(calculateSpy).toHaveBeenLastCalledWith('fake-token', {
        ruleSetCode: 'AUTOMOTIVE-STANDARD',
        priceListCode: 'AUTOMOTIVE-DEFAULT',
        entityDefId: 99,
        entityInstanceId: 1,
        quantity: 3,
        attributes: {},
        customerInstanceId: 301,
        contractOverridePrice: 77.77,
      })
    })
  })

  it('shows a loading indicator while calculating', async () => {
    let resolveCalculate: (value: PriceCalculationResponse) => void = () => {}
    vi.spyOn(pricingApi, 'calculatePrice').mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveCalculate = resolve
        }),
    )

    const user = userEvent.setup()
    renderScreen()

    await waitFor(() => {
      expect(screen.getByRole('combobox', { name: /khách hàng/i })).toBeInTheDocument()
    })
    await user.click(screen.getByRole('combobox', { name: /khách hàng/i }))
    await user.click(await screen.findByRole('option', { name: /acme motors oem division/i }))

    expect(await screen.findByText(/đang tính giá/i)).toBeInTheDocument()

    resolveCalculate(PRICE_RESPONSE)
    await waitFor(() => {
      expect(screen.queryByText(/đang tính giá/i)).not.toBeInTheDocument()
    })
  })

  it('shows an error banner with a retry button when pricing fails, without crashing', async () => {
    const calculateSpy = vi
      .spyOn(pricingApi, 'calculatePrice')
      .mockRejectedValue(new Error('POST /api/pricing/calculate failed with status 404'))

    const user = userEvent.setup()
    renderScreen()

    await waitFor(() => {
      expect(screen.getByRole('combobox', { name: /khách hàng/i })).toBeInTheDocument()
    })
    await user.click(screen.getByRole('combobox', { name: /khách hàng/i }))
    await user.click(await screen.findByRole('option', { name: /acme motors oem division/i }))

    await waitFor(() => {
      expect(screen.getByText(/failed with status 404/i)).toBeInTheDocument()
    })
    const retryButton = screen.getByRole('button', { name: /thử lại/i })
    expect(retryButton).toBeInTheDocument()

    calculateSpy.mockResolvedValue(PRICE_RESPONSE)
    await user.click(retryButton)

    await waitFor(() => {
      expect(screen.getByText('List price')).toBeInTheDocument()
    })
  })
})
