import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import type { ReactElement } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { EntitiesList } from './EntitiesList'
import * as entitiesApi from './entitiesApi'

function renderWithQueryClient(ui: ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>)
}

describe('EntitiesList', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('renders the real entity definitions returned by GET /api/entities (not mocked data baked into the component)', async () => {
    const fetchEntitiesSpy = vi.spyOn(entitiesApi, 'fetchEntities').mockResolvedValue([
      {
        code: 'SampleWidget',
        label: 'Sample Widget',
        version: 1,
        attributes: [
          { code: 'name', label: 'Name', dataType: 'STRING', required: true },
          { code: 'quantity', label: 'Quantity', dataType: 'NUMBER', required: true },
          { code: 'notes', label: 'Notes', dataType: 'STRING', required: false },
        ],
      },
    ])

    renderWithQueryClient(<EntitiesList accessToken="fake-access-token" />)

    await waitFor(() => {
      expect(screen.getByText('SampleWidget')).toBeInTheDocument()
    })
    expect(screen.getByText('Sample Widget')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()

    // The access token from the (real, not mocked here) OIDC session must
    // flow through to the API call's Authorization header.
    expect(fetchEntitiesSpy).toHaveBeenCalledWith('fake-access-token')
  })

  it('shows an error message when the API call fails instead of silently showing nothing', async () => {
    vi.spyOn(entitiesApi, 'fetchEntities').mockRejectedValue(
      new Error('GET /api/entities failed with status 401'),
    )

    renderWithQueryClient(<EntitiesList accessToken="fake-access-token" />)

    await waitFor(() => {
      expect(screen.getByText(/failed with status 401/i)).toBeInTheDocument()
    })
  })

  it('shows an empty-state message when there are no entity definitions', async () => {
    vi.spyOn(entitiesApi, 'fetchEntities').mockResolvedValue([])

    renderWithQueryClient(<EntitiesList accessToken="fake-access-token" />)

    await waitFor(() => {
      expect(screen.getByText(/no entity definitions/i)).toBeInTheDocument()
    })
  })
})
