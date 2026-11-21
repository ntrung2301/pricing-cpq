import { Alert, Spin } from 'antd'
import { useAuth } from 'react-oidc-context'
import { useEffect, useState } from 'react'
import { UIProvider } from './ui'
import { LoginScreen } from './auth/LoginScreen'
import { getWorkspaceRole } from './auth/workspaceRole'
import { AppShell, type WorkspacePage } from './workspace/AppShell'
import { EntitiesList } from './entities/EntitiesList'
import { VehiclePartSelectionScreen } from './configurator/VehiclePartSelectionScreen'

const pages: WorkspacePage[] = ['configurator', 'definitions']
const pageTitles: Record<WorkspacePage, string> = {
  home: 'Home',
  products: 'Products',
  catalog: 'Catalog',
  configurator: 'Chọn xe / Chọn part',
  definitions: 'Catalog definitions',
}

/**
 * Simple hash-based routing (no react-router, per D-050 — "đừng
 * over-engineer"), same pattern AppShell's nav links already use
 * (`href="#configurator"` etc). Defaults to the `configurator` page so the
 * authenticated landing screen matches the pre-migration behavior (S2-T9
 * Playwright specs expect "Chọn xe / Chọn part" immediately after login).
 */
function useHashPage(): WorkspacePage {
  const [page, setPage] = useState<WorkspacePage>(() => {
    const hash = window.location.hash.replace('#', '') as WorkspacePage
    return pages.includes(hash) ? hash : 'configurator'
  })

  useEffect(() => {
    function onHashChange() {
      const hash = window.location.hash.replace('#', '') as WorkspacePage
      setPage(pages.includes(hash) ? hash : 'configurator')
    }
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [])

  return page
}

function App() {
  const auth = useAuth()
  const page = useHashPage()

  return (
    <UIProvider>
      {auth.isLoading && (
        <div style={{ display: 'flex', justifyContent: 'center', padding: '64px 0' }}>
          <Spin aria-label="Loading authentication state" />
        </div>
      )}

      {!auth.isLoading && auth.error && (
        <div style={{ padding: 24 }}>
          <Alert type="error" message={auth.error.message} />
        </div>
      )}

      {!auth.isLoading && !auth.error && !auth.isAuthenticated && <LoginScreen />}

      {!auth.isLoading && !auth.error && auth.isAuthenticated && auth.user && (
        <AppShell
          page={page}
          pages={pages}
          title={pageTitles[page]}
          accountName={auth.user.profile?.email ?? auth.user.profile?.preferred_username ?? 'User'}
          roleLabel={getWorkspaceRole(auth.user.access_token)}
          onSignOut={() => auth.removeUser()}
        >
          {page === 'definitions' && <EntitiesList accessToken={auth.user.access_token} />}
          {page === 'configurator' && (
            <VehiclePartSelectionScreen accessToken={auth.user.access_token} />
          )}
        </AppShell>
      )}
    </UIProvider>
  )
}

export default App
