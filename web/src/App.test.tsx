import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, within } from '@testing-library/react'
import type { ReactElement } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'

// react-oidc-context's real AuthProvider talks to a real Keycloak
// discovery endpoint on mount, which this fast unit test must not depend
// on (that real end-to-end login flow is covered by the Playwright E2E
// test instead, per S1-T8's scope). Mocking `useAuth` here lets this test
// drive each auth state directly and deterministically.
const useAuthMock = vi.fn()
vi.mock('react-oidc-context', () => ({
  useAuth: () => useAuthMock(),
}))

// Prevents this fast unit test from making a real network call to the
// backend — the no-mock, real-data proof is the separate Playwright E2E
// test (S1-T8/S2-T9 scope), not this component-level test.
vi.mock('./catalog/catalogApi', () => ({
  fetchInstances: vi.fn().mockResolvedValue({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }),
  fetchSupersededBy: vi.fn().mockResolvedValue([]),
}))
vi.mock('./configurator/configuratorApi', () => ({
  fetchCompatibleInstances: vi.fn().mockResolvedValue([]),
}))

function renderApp(ui: ReactElement = <App />) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>)
}

function authenticatedAs(role: string) {
  const payload = btoa(JSON.stringify({ realm_access: { roles: [role] } }))
  useAuthMock.mockReturnValue({
    isLoading: false,
    isAuthenticated: true,
    user: { access_token: `header.${payload}.signature`, profile: { name: 'Alex' } },
    removeUser: vi.fn(),
  })
}

describe('App', () => {
  beforeEach(() => window.history.replaceState(null, '', '/'))

  it('shows a loading indicator while restoring the session', () => {
    useAuthMock.mockReturnValue({ isLoading: true, isAuthenticated: false })
    renderApp()
    expect(screen.getByLabelText('Loading authentication state')).toBeInTheDocument()
  })

  it('restores the split login and calls the existing Keycloak redirect', () => {
    const signinRedirect = vi.fn()
    useAuthMock.mockReturnValue({ isLoading: false, isAuthenticated: false, signinRedirect })
    renderApp()
    expect(screen.getByRole('heading', { name: 'Sign in to CPQ' })).toBeInTheDocument()
    expect(screen.getByText('Sample pricing breakdown')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(signinRedirect).toHaveBeenCalledOnce()
  })

  it('lands Sales on home and opens the existing configurator from New quote', async () => {
    authenticatedAs('sales')
    renderApp()
    expect(screen.getByRole('heading', { name: 'Quotes' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('link', { name: 'New quote' }))
    expect(await screen.findByRole('heading', { name: 'Chọn xe / Chọn part' })).toBeInTheDocument()
  })

  it('opens Products through Master Data with a single page heading and column controls', async () => {
    authenticatedAs('sales')
    renderApp()
    expect(within(screen.getByRole('navigation', { name: 'Workspace' })).queryByText('Products')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Master Data' }))
    const item = await screen.findByRole('menuitem', { name: 'Products' })
    fireEvent.click(within(item).getByRole('link'))
    expect(await screen.findByText('25 records')).toBeInTheDocument()
    expect(screen.getAllByText('Products', { selector: 'h1,h2' })).toHaveLength(1)
    expect(screen.getByRole('button', { name: 'Columns' })).toBeInTheDocument()
  })

  it('uses the manager home and denies access for accounts without a CPQ role', () => {
    authenticatedAs('sales_manager')
    const view = renderApp()
    expect(screen.getByRole('heading', { name: 'Pending approvals' })).toBeInTheDocument()
    view.unmount()
    authenticatedAs('unknown')
    renderApp()
    expect(screen.getByText('Workspace access required')).toBeInTheDocument()
  })
})
