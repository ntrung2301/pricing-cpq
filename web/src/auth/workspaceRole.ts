export type WorkspaceRole = 'sales' | 'sales_manager' | 'pricing_admin'

// The API independently authorizes requests. These claims only choose the UI.
export function getWorkspaceRole(accessToken: string): WorkspaceRole | undefined {
  const payload = accessToken.split('.')[1]
  if (!payload) return undefined

  try {
    const bytes = Uint8Array.from(atob(payload.replace(/-/g, '+').replace(/_/g, '/')), (char) =>
      char.charCodeAt(0),
    )
    const claims = JSON.parse(new TextDecoder().decode(bytes))
    const roles: unknown = claims?.realm_access?.roles
    if (!Array.isArray(roles)) return undefined
    if (roles.includes('pricing_admin')) return 'pricing_admin'
    if (roles.includes('sales_manager')) return 'sales_manager'
    if (roles.includes('sales')) return 'sales'
  } catch {
    return undefined
  }
}
