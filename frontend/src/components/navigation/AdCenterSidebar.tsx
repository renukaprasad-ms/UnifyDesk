import {
  FiActivity,
  FiBarChart2,
  FiClock,
  FiCreditCard,
  FiGrid,
  FiImage,
  FiSettings,
  FiTarget,
  FiUsers,
  FiZap,
} from 'react-icons/fi'
import { NavLink } from 'react-router'

const items = [
  { label: 'Overview', to: '/ad-center', icon: <FiGrid /> },
  { label: 'Campaigns', to: '/ad-center/campaigns', icon: <FiTarget /> },
  { label: 'Creatives', to: '/ad-center/creatives', icon: <FiImage /> },
  { label: 'Audiences', to: '/ad-center/audiences', icon: <FiUsers /> },
  { label: 'Conversions', to: '/ad-center/conversions', icon: <FiActivity /> },
  { label: 'Analytics', to: '/ad-center/analytics', icon: <FiBarChart2 /> },
  { label: 'Recommendations', to: '/ad-center/recommendations', icon: <FiZap /> },
  { label: 'Billing', to: '/ad-center/billing', icon: <FiCreditCard /> },
  { label: 'Activity', to: '/ad-center/activity', icon: <FiClock /> },
  { label: 'Settings', to: '/ad-center/settings', icon: <FiSettings /> },
]

export default function AdCenterSidebar({ onNavigate }: { onNavigate: () => void }) {
  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `sidebar-link ${isActive ? 'sidebar-link-active' : ''}`

  return (
    <div className="flex min-h-full flex-col">
      <div className="px-3 pb-4 pt-2">
        <p className="sidebar-label">AD CENTER</p>
      </div>
      <nav aria-label="Ad Center navigation" className="space-y-1 px-2">
        {items.map((item) => (
          <NavLink key={item.to} to={item.to} end={item.to === '/ad-center'} className={linkClass} onClick={onNavigate}>
            <span className="sidebar-icon" aria-hidden="true">{item.icon}</span>
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
