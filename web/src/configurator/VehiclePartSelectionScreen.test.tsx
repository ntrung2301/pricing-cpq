import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactElement } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as catalogApi from '../catalog/catalogApi'
import type { EntityInstancePageDto } from '../catalog/types'
import * as entitiesApi from '../entities/entitiesApi'
import * as configuratorApi from '../configurator/configuratorApi'
import { VehiclePartSelectionScreen } from './VehiclePartSelectionScreen'

// S2-T9 (docs/sprints/sprint-2.md) — "chọn xe -> part tương thích", per
// wireframe docs/ux/01-vehicle-part-selection.md. No mocking of fetch at
// the network layer here (that is the Playwright E2E test's job,
// e2e/tests/vehicle-part-selection.spec.ts, no-mock per S1-T8's
// precedent) — this component test mocks the thin API wrapper functions
// only, same pattern as web/src/entities/EntitiesList.test.tsx.

const VEHICLES_PAGE: EntityInstancePageDto = {
  content: [
    {
      id: 101,
      businessKey: null,
      displayLabel: null,
      attributes: { make: 'Toyota', model: 'Camry', year: 2018, engine: 'L4-2.5L' },
    },
    {
      id: 102,
      businessKey: null,
      displayLabel: null,
      attributes: { make: 'Honda', model: 'Civic', year: 2015, engine: 'L4-1.8L' },
    },
  ],
  page: 0,
  size: 100,
  totalElements: 2,
  totalPages: 1,
}

const PRODUCTS_PAGE: EntityInstancePageDto = {
  content: [
    {
      id: 201,
      businessKey: 'BRK-PAD-NEW-001',
      displayLabel: 'Brake Pad Set Front',
      attributes: { oemOrAftermarket: 'AFTERMARKET' },
    },
    {
      id: 202,
      businessKey: 'BRK-PAD-OLD-001',
      displayLabel: 'Brake Pad Set Front (Discontinued)',
      attributes: { oemOrAftermarket: 'AFTERMARKET' },
    },
    {
      id: 203,
      businessKey: 'SPK-PLUG-001',
      displayLabel: 'Spark Plug',
      attributes: { oemOrAftermarket: 'OEM' },
    },
  ],
  page: 0,
  size: 100,
  totalElements: 3,
  totalPages: 1,
}

/** Finds the `<tr>` for a product row by its (unique) businessKey cell text. */
function rowForBusinessKey(businessKey: string): HTMLElement {
  const cell = screen.getByText(businessKey)
  const row = cell.closest('tr')
  if (!row) {
    throw new Error(`no <tr> ancestor found for businessKey ${businessKey}`)
  }
  return row
}

function renderScreen(ui: ReactElement = <VehiclePartSelectionScreen accessToken="fake-token" />) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>)
}

const EMPTY_PAGE: EntityInstancePageDto = {
  content: [],
  page: 0,
  size: 100,
  totalElements: 0,
  totalPages: 0,
}

async function mockFetchInstances(
  byEntityDefCode: Partial<Record<string, EntityInstancePageDto>>,
) {
  vi.spyOn(catalogApi, 'fetchInstances').mockImplementation(
    async (_accessToken: string, entityDefCode: string) => {
      // Falls back to an empty page (not a throw) for entityDefCode this
      // test doesn't care about — e.g. "Customer", which the nested S2-T13
      // PriceWaterfallScreen (web/src/pricing/PriceWaterfallScreen.tsx)
      // queries once a part is selected, outside this test file's concern.
      return byEntityDefCode[entityDefCode] ?? EMPTY_PAGE
    },
  )
}

describe('VehiclePartSelectionScreen', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    // S2-T13's PriceWaterfallScreen (nested once a part is selected) calls
    // GET /api/entities to resolve Product's entityDefId — not this test
    // file's concern, so a safe empty default here keeps those render
    // paths from making a real network call.
    vi.spyOn(entitiesApi, 'fetchEntities').mockResolvedValue([])
  })

  it('shows the full Product catalog by default when no vehicle has been chosen yet, with a hint to pick one', async () => {
    await mockFetchInstances({ Vehicle: VEHICLES_PAGE, Product: PRODUCTS_PAGE })
    vi.spyOn(catalogApi, 'fetchSupersededBy').mockResolvedValue([])

    renderScreen()

    await waitFor(() => {
      expect(screen.getByText('BRK-PAD-NEW-001')).toBeInTheDocument()
    })
    expect(screen.getByText('BRK-PAD-OLD-001')).toBeInTheDocument()
    expect(screen.getByText('SPK-PLUG-001')).toBeInTheDocument()
    expect(screen.getByText(/chọn xe ở trên để lọc nhanh hơn/i)).toBeInTheDocument()
  })

  it('calls GET /api/configurator/compatible with the chosen vehicle and shows only the matching products', async () => {
    await mockFetchInstances({ Vehicle: VEHICLES_PAGE, Product: PRODUCTS_PAGE })
    vi.spyOn(catalogApi, 'fetchSupersededBy').mockResolvedValue([])
    const compatibleSpy = vi.spyOn(configuratorApi, 'fetchCompatibleInstances').mockResolvedValue([
      { entityInstanceId: 201, businessKey: 'BRK-PAD-NEW-001', displayLabel: 'Brake Pad Set Front' },
    ])

    const user = userEvent.setup()
    renderScreen()

    await waitFor(() => {
      expect(screen.getByText('BRK-PAD-NEW-001')).toBeInTheDocument()
    })

    await user.click(screen.getByRole('combobox', { name: /chọn xe/i }))
    await user.click(await screen.findByRole('option', { name: /toyota camry 2018/i }))
    await user.click(screen.getByRole('button', { name: /tìm part tương thích/i }))

    await waitFor(() => {
      expect(compatibleSpy).toHaveBeenCalledWith('fake-token', 101, 'Product')
    })

    expect(screen.getByText('BRK-PAD-NEW-001')).toBeInTheDocument()
    expect(screen.queryByText('BRK-PAD-OLD-001')).not.toBeInTheDocument()
    expect(screen.queryByText('SPK-PLUG-001')).not.toBeInTheDocument()
  })

  it('shows an empty-state message when no part is compatible with the chosen vehicle', async () => {
    await mockFetchInstances({ Vehicle: VEHICLES_PAGE, Product: PRODUCTS_PAGE })
    vi.spyOn(catalogApi, 'fetchSupersededBy').mockResolvedValue([])
    vi.spyOn(configuratorApi, 'fetchCompatibleInstances').mockResolvedValue([])

    const user = userEvent.setup()
    renderScreen()

    await waitFor(() => {
      expect(screen.getByText('BRK-PAD-NEW-001')).toBeInTheDocument()
    })

    await user.click(screen.getByRole('combobox', { name: /chọn xe/i }))
    await user.click(await screen.findByRole('option', { name: /honda civic 2015/i }))
    await user.click(screen.getByRole('button', { name: /tìm part tương thích/i }))

    await waitFor(() => {
      expect(
        screen.getByText(/không có part tương thích với xe đã chọn/i),
      ).toBeInTheDocument()
    })
  })

  it('shows a supersession badge for a part that has been superseded, and not for one that has not', async () => {
    await mockFetchInstances({ Vehicle: VEHICLES_PAGE, Product: PRODUCTS_PAGE })
    vi.spyOn(catalogApi, 'fetchSupersededBy').mockImplementation(async (_token, instanceId) => {
      if (instanceId === 202) {
        return [{ entityInstanceId: 201, businessKey: 'BRK-PAD-NEW-001', displayLabel: 'Brake Pad Set Front' }]
      }
      return []
    })

    renderScreen()

    await waitFor(() => {
      expect(screen.getByText(/đã bị supersede bởi brake pad set front/i)).toBeInTheDocument()
    })

    const sparkPlugRow = rowForBusinessKey('SPK-PLUG-001')
    expect(within(sparkPlugRow).queryByText(/đã bị supersede/i)).not.toBeInTheDocument()
  })

  it('highlights the chosen part and shows a confirmation line when "Chọn part này" is clicked', async () => {
    await mockFetchInstances({ Vehicle: VEHICLES_PAGE, Product: PRODUCTS_PAGE })
    vi.spyOn(catalogApi, 'fetchSupersededBy').mockResolvedValue([])

    const user = userEvent.setup()
    renderScreen()

    await waitFor(() => {
      expect(screen.getByText('BRK-PAD-NEW-001')).toBeInTheDocument()
    })

    const row = rowForBusinessKey('BRK-PAD-NEW-001')
    await user.click(within(row).getByRole('button', { name: /chọn part này/i }))

    expect(screen.getByText(/đã chọn: brake pad set front$/i)).toBeInTheDocument()
  })

  it('shows an error banner with a retry button when loading the product catalog fails, without crashing the page', async () => {
    vi.spyOn(catalogApi, 'fetchInstances').mockImplementation(async (_token, entityDefCode) => {
      if (entityDefCode === 'Vehicle') {
        return VEHICLES_PAGE
      }
      throw new Error('GET /api/catalog/Product/instances failed with status 500')
    })
    vi.spyOn(catalogApi, 'fetchSupersededBy').mockResolvedValue([])

    renderScreen()

    await waitFor(() => {
      expect(screen.getByText(/failed with status 500/i)).toBeInTheDocument()
    })
    expect(screen.getByRole('button', { name: /thử lại/i })).toBeInTheDocument()
    // The rest of the page (vehicle selector) must still be usable, not crashed.
    expect(screen.getByRole('combobox', { name: /chọn xe/i })).toBeInTheDocument()
  })
})
