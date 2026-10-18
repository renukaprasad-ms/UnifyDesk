import {
  FiBarChart2,
  FiDollarSign,
  FiLayers,
  FiPieChart,
  FiZap,
} from 'react-icons/fi'
import { NavLink } from 'react-router'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'

const billingNavItems = [
  { label: 'Overview', to: '/billing/overview', icon: <FiPieChart /> },
  { label: 'Usage', to: '/billing/usage', icon: <FiBarChart2 /> },
  { label: 'Wallet', to: '/billing/wallet', icon: <FiDollarSign /> },
  { label: 'Plans', to: '/billing/plans', icon: <FiLayers /> },
  { label: 'AutoPay & Payment Methods', to: '/billing/autopay', icon: <FiZap /> },
]

export default function BillingSidebar({ onNavigate }: { onNavigate: () => void }) {
  const { can } = useIam()

  const links = billingNavItems.filter(() => {
    return can(PERMISSIONS.BILLING_READ) || can(PERMISSIONS.BILLING_BALANCE_READ)
  })

  return (
    <div className="flex min-h-full flex-col">
      <div className="px-3 pb-4 pt-2">
        <p className="sidebar-label">Billing &amp; Finance</p>
      </div>
      <nav aria-label="Billing navigation" className="space-y-1 px-2">
        {links.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/billing/overview' || item.to === '/billing'}
            className={({ isActive }) => `sidebar-link ${isActive ? 'sidebar-link-active' : ''}`}
            onClick={onNavigate}
          >
            <span className="sidebar-icon" aria-hidden="true">
              {item.icon}
            </span>
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
