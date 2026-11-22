import { Result, Spin } from 'antd'
import { useSyncExternalStore } from 'react'
import { useAuth } from 'react-oidc-context'
import { UIProvider } from './ui'
import { LoginScreen } from './auth/LoginScreen'
import { getWorkspaceRole } from './auth/workspaceRole'
import { AppShell, type WorkspacePage } from './workspace/AppShell'
import { HomeScreen } from './workspace/HomeScreen'
import { homeContent } from './workspace/homeContent'
import { EntitiesList } from './entities/EntitiesList'
import { VehiclePartSelectionScreen } from './configurator/VehiclePartSelectionScreen'
import { ProductsPage } from './products/ProductsPage'

function subscribe(onChange: () => void) {
  window.addEventListener('hashchange', onChange)
  return () => window.removeEventListener('hashchange', onChange)
}

function Workspace() {
  const auth = useAuth()
  const hash = useSyncExternalStore(subscribe, () => window.location.hash)

  if (auth.isLoading && !auth.activeNavigator) {
    return <main className="session-loading" role="status" aria-label="Loading authentication state"><Spin size="large" /></main>
  }
  if (!auth.isAuthenticated || !auth.user) return <LoginScreen />

  const role = getWorkspaceRole(auth.user.access_token)
  const pages: WorkspacePage[] = ['home']
  if (role) pages.push('products', 'configurator')
  if (role === 'pricing_admin') pages.push('definitions')
  const requested = hash.slice(1) === 'catalog' ? 'configurator' : hash.slice(1)
  const page = pages.find((candidate) => candidate === requested) ?? 'home'
  const home = role && homeContent[role]
  const titles = {
    home: home ? home.title : 'Workspace',
    products: undefined,
    catalog: 'Catalog',
    configurator: 'Chọn xe / Chọn part',
    definitions: undefined,
  }

  return (
    <AppShell
      page={page}
      pages={pages}
      title={titles[page]}
      primaryAction={page === 'home' && home ? { label: home.action, href: home.href } : undefined}
      accountName={auth.user.profile?.name ?? auth.user.profile?.preferred_username ?? 'Account'}
      roleLabel={role && { sales: 'Sales', sales_manager: 'Sales Manager', pricing_admin: 'Pricing Admin' }[role]}
      onSignOut={() => {
        window.location.hash = 'home'
        void auth.removeUser()
      }}
    >
      {!role && <Result status="403" title="Workspace access required"
        subTitle="Ask your organization administrator to assign a CPQ role to your account." />}
      {role && page === 'home' && <HomeScreen role={role} />}
      {page === 'products' && <ProductsPage />}
      {page === 'definitions' && <EntitiesList accessToken={auth.user.access_token} />}
      {page === 'configurator' && <VehiclePartSelectionScreen accessToken={auth.user.access_token} />}
    </AppShell>
  )
}

export default function App() {
  return <UIProvider><Workspace /></UIProvider>
}
