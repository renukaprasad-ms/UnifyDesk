import {
  FiActivity,
  FiArrowLeft,
  FiBriefcase,
  FiCreditCard,
  FiFileText,
  FiHome,
  FiShield,
  FiUsers,
} from 'react-icons/fi'
import { NavLink } from 'react-router'
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
  { label: 'Companies', to: '/platform/companies', icon: <FiBriefcase />, permission: 'COMPANY_VIEW' },
  { label: 'Users', to: '/platform/users', icon: <FiUsers />, permission: 'USER_VIEW' },
  { label: 'Plans & pricing', to: '/platform/plans', icon: <FiCreditCard />, superAdminOnly: true },
  { label: 'Billing operations', to: '/platform/billing', icon: <FiActivity />, superAdminOnly: true },
  { label: 'Admins', to: '/platform/admins', icon: <FiShield />, permission: 'ADMIN_VIEW' },
  { label: 'Audit logs', to: '/platform/audit', icon: <FiFileText />, permission: 'AUDIT_VIEW' },
]

export default function AdminSidebar({ access, onNavigate }: { access: PlatformAccess; onNavigate: () => void }) {
  const isSuperAdmin = access.role === 'SUPER_ADMIN'
  const allowed = (link: AdminLink) => {
    if (isSuperAdmin) return true
    if (link.superAdminOnly) return false
    return !link.permission || access.permissions.includes(link.permission)
  }

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
        <NavLink to="/workspace" className="admin-back-button" onClick={onNavigate} title="Back to workspace">
          <FiArrowLeft aria-hidden="true" />
        </NavLink>
      </div>

      <nav aria-label="Administration" className="mt-6 space-y-1">
        {links.filter(allowed).map((link) => (
          <NavLink
            key={link.to}
            to={link.to}
            end={link.to === '/platform'}
            onClick={onNavigate}
            className={({ isActive }) => `admin-nav-link ${isActive ? 'is-active' : ''}`}
          >
            <span aria-hidden="true" className="admin-nav-icon">{link.icon}</span>
            <span>{link.label}</span>
          </NavLink>
        ))}
      </nav>
    </>
  )
}
