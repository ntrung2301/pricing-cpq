import type { ReactNode } from 'react'

type StatusTone = 'neutral' | 'brand' | 'success' | 'info' | 'danger'

export function StatusPill({ tone = 'neutral', children }: { tone?: StatusTone; children: ReactNode }) {
  return <span className={`ui-status-pill ui-status-pill--${tone}`}>{children}</span>
}
