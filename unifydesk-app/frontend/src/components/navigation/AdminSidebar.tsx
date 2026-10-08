import { useEffect, useState } from 'react'
import {
  FiActivity,
  FiArrowLeft,
  FiBarChart2,
  FiBriefcase,
  FiChevronDown,
  FiCreditCard,
  FiFileText,
  FiHome,
  FiRadio,
  FiServer,
  FiShield,
  FiUsers,
} from 'react-icons/fi'
import { NavLink, useLocation } from 'react-router'
import type { PlatformAccess, PlatformPermission } from '../../features/platform/types'

type AdminLink = {
  label: string
  to: string
  icon: React.ReactNode
  permission?: PlatformPermission
  superAdminOnly?: boolean
}

const links: AdminLink[] = [
  { label: 'Overview', to: '/platform', icon: <FiHome /> },
  {
    label: 'Companies',
    to: '/platform/companies',
    icon: <FiBriefcase />,
    permission: 'COMPANY_VIEW',
  },
  { label: 'Users', to: '/platform/users', icon: <FiUsers />, permission: 'USER_VIEW' },
  { label: 'Plans & pricing', to: '/platform/plans', icon: <FiCreditCard />, superAdminOnly: true },
  {
    label: 'Billing operations',
    to: '/platform/billing',
    icon: <FiActivity />,
    superAdminOnly: true,
  },
  { label: 'Managed providers', to: '/platform/managed-providers', icon: <FiServer />, superAdminOnly: true },
  { label: 'Managed SMS reviews', to: '/platform/managed-sms-reviews', icon: <FiShield />, superAdminOnly: true },
  { label: 'Admins', to: '/platform/admins', icon: <FiShield />, permission: 'ADMIN_VIEW' },
  { label: 'Audit logs', to: '/platform/audit', icon: <FiFileText />, permission: 'AUDIT_VIEW' },
]

export default function AdminSidebar({
  access,
  onNavigate,
}: {
  access: PlatformAccess
  onNavigate: () => void
}) {
  const location = useLocation()
  const isSuperAdmin = access.role === 'SUPER_ADMIN'
  const channelAnalyticsActive = location.pathname.startsWith('/platform/channel-analytics')
  const [channelAnalyticsOpen, setChannelAnalyticsOpen] = useState(channelAnalyticsActive)

  useEffect(() => {
    if (channelAnalyticsActive) setChannelAnalyticsOpen(true)
  }, [channelAnalyticsActive])

  const allowed = (link: AdminLink) => {
    if (isSuperAdmin) return true
    if (link.superAdminOnly) return false
    return !link.permission || access.permissions.includes(link.permission)
  }

  const canViewChannelAnalytics = isSuperAdmin || access.permissions.includes('AUDIT_VIEW')

  return (
    <>
      <div className="admin-sidebar-header">
        <div>
          <p className="eyebrow">PLATFORM</p>
          <h2 className="mt-1 text-[15px] font-bold text-slate-950">Administration</h2>
          <p className="mt-1 text-[11px] leading-4 text-slate-500">
            {isSuperAdmin ? 'Super Admin' : 'Platform Admin'}
          </p>
        </div>
        <NavLink
          to="/workspace"
          className="admin-back-button"
          onClick={onNavigate}
          title="Back to workspace"
        >
          <FiArrowLeft aria-hidden="true" />
        </NavLink>
      </div>

      <nav aria-label="Administration" className="mt-6 space-y-1">
        {links
          .filter(allowed)
          .slice(0, -1)
          .map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              end={link.to === '/platform'}
              onClick={onNavigate}
              className={({ isActive }) => `admin-nav-link ${isActive ? 'is-active' : ''}`}
            >
              <span aria-hidden="true" className="admin-nav-icon">
                {link.icon}
              </span>
              <span>{link.label}</span>
            </NavLink>
          ))}

        {canViewChannelAnalytics && (
          <div>
            <button
              type="button"
              onClick={() => setChannelAnalyticsOpen((open) => !open)}
              className={`admin-nav-link w-full text-left ${channelAnalyticsActive ? 'is-active' : ''}`}
              aria-expanded={channelAnalyticsOpen}
            >
              <span aria-hidden="true" className="admin-nav-icon">
                <FiBarChart2 />
              </span>
              <span className="flex-1">Channel analytics</span>
              <FiChevronDown
                aria-hidden="true"
                className={`transition-transform ${channelAnalyticsOpen ? 'rotate-180' : ''}`}
              />
            </button>

            {channelAnalyticsOpen && (
              <div className="ml-8 mt-1 space-y-1 border-l border-slate-200 pl-3">
                <div className="flex items-center justify-between rounded-lg px-3 py-2 text-xs font-semibold text-slate-400">
                  <span>Usage</span>
                  <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] uppercase tracking-wide">
                    Soon
                  </span>
                </div>
                <NavLink
                  to="/platform/channel-analytics/webhook-callbacks"
                  onClick={onNavigate}
                  className={({ isActive }) =>
                    `flex items-center gap-2 rounded-lg px-3 py-2 text-xs font-semibold transition-colors ${
                      isActive
                        ? 'bg-indigo-50 text-indigo-700'
                        : 'text-slate-600 hover:bg-slate-50 hover:text-slate-950'
                    }`
                  }
                >
                  <FiRadio aria-hidden="true" />
                  <span>Webhook callbacks</span>
                </NavLink>
              </div>
            )}
          </div>
        )}

        {links
          .filter(allowed)
          .slice(-1)
          .map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              onClick={onNavigate}
              className={({ isActive }) => `admin-nav-link ${isActive ? 'is-active' : ''}`}
            >
              <span aria-hidden="true" className="admin-nav-icon">
                {link.icon}
              </span>
              <span>{link.label}</span>
            </NavLink>
          ))}
      </nav>
    </>
  )
}
