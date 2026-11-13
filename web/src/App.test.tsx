import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import type { ReactElement } from 'react'
import { describe, expect, it, vi } from 'vitest'
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
// test (S1-T8 scope), not this component-level test.
vi.mock('./entities/entitiesApi', () => ({
  fetchEntities: vi.fn().mockResolvedValue([]),
}))

function renderApp(ui: ReactElement = <App />) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>)
}

describe('App', () => {
  it('renders the CPQ Pricing Platform heading regardless of auth state', () => {
    useAuthMock.mockReturnValue({ isLoading: true, isAuthenticated: false, error: undefined })

    renderApp()

    expect(
      screen.getByRole('heading', { name: /cpq pricing platform/i }),
    ).toBeInTheDocument()
  })

  it('shows a login button when the user is not authenticated', () => {
    useAuthMock.mockReturnValue({
      isLoading: false,
      isAuthenticated: false,
      error: undefined,
      signinRedirect: vi.fn(),
    })

    renderApp()

    expect(screen.getByRole('button', { name: /login with keycloak/i })).toBeInTheDocument()
  })

  it('shows the entities screen (not the login button) once authenticated', () => {
    useAuthMock.mockReturnValue({
      isLoading: false,
      isAuthenticated: true,
      error: undefined,
      user: { access_token: 'fake-access-token' },
      removeUser: vi.fn(),
    })

    renderApp()

    expect(screen.getByRole('heading', { name: /entity definitions/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /login with keycloak/i })).not.toBeInTheDocument()
  })
})
