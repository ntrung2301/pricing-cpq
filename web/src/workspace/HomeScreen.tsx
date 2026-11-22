import { AppstoreOutlined, ClockCircleOutlined } from '@ant-design/icons'
import { Card, Empty } from '../ui'
import type { WorkspaceRole } from '../auth/workspaceRole'
import { homeContent } from './homeContent'

export function HomeScreen({ role }: { role: WorkspaceRole }) {
  const content = homeContent[role]

  return (
    <div className="workspace-overview">
      <div className="home-cards">
        <a className="home-action-card" href={content.href}>
          <span className="home-card-icon"><AppstoreOutlined aria-hidden="true" /></span>
          <span>
            <strong>{role === 'pricing_admin' ? 'Browse catalog definitions' : 'Explore the catalog'}</strong>
            <span>{content.description}</span>
          </span>
        </a>
        {'secondaryHref' in content ? (
          <a className="home-action-card" href={content.secondaryHref}>
            <span className="home-card-icon secondary"><ClockCircleOutlined aria-hidden="true" /></span>
            <span>
              <strong>{content.secondary}</strong>
              <span>{content.secondaryDescription}</span>
            </span>
          </a>
        ) : (
          <div className="home-action-card">
            <span className="home-card-icon secondary"><ClockCircleOutlined aria-hidden="true" /></span>
            <span>
              <strong>{content.secondary}</strong>
              <span>{content.secondaryDescription}</span>
            </span>
          </div>
        )}
      </div>

      <Card title={<h2>{content.lead}</h2>} className="home-lead">
        {role === 'pricing_admin' ? (
          <p>{content.leadDescription}</p>
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={<p>{content.leadDescription}</p>} />
        )}
      </Card>
    </div>
  )
}
