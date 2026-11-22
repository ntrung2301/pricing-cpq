import type { WorkspaceRole } from '../auth/workspaceRole'

export const homeContent = {
  sales: {
    title: 'Quotes',
    description: 'Configure products and calculate pricing for your next quote.',
    action: 'New quote',
    href: '#configurator',
    lead: 'Recent quotes',
    leadDescription: 'Saved quote history isn’t available in this workspace yet.',
    secondary: 'Needs action',
    secondaryDescription: 'Returned quotes and their review reasons will appear here when quote tracking is available.',
  },
  sales_manager: {
    title: 'Approvals',
    description: 'Review quote exceptions and the pricing decisions behind them.',
    action: 'Browse catalog',
    href: '#configurator',
    lead: 'Pending approvals',
    leadDescription: 'The approval queue isn’t available in this workspace yet.',
    secondary: 'Pricing review',
    secondaryDescription: 'Choose a product and customer from the catalog to inspect a live pricing breakdown.',
  },
  pricing_admin: {
    title: 'Pricing administration',
    description: 'Inspect catalog definitions and validate product pricing.',
    action: 'View catalog definitions',
    href: '#definitions',
    lead: 'Catalog definitions',
    leadDescription: 'Browse entity definitions, schema versions, and attribute counts for the active catalog.',
    secondary: 'Price lists',
    secondaryDescription: 'Price list management and audit history aren’t available in this workspace yet.',
  },
} satisfies Record<WorkspaceRole, Record<string, string>>
