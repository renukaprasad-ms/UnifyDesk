import { useState, type ReactNode } from 'react'
import {
  FiBriefcase,
  FiChevronDown,
  FiCreditCard,
  FiFileText,
  FiHome,
  FiInbox,
  FiLogOut,
  FiMail,
  FiMessageSquare,
  FiRadio,
  FiSend,
  FiSettings,
  FiShield,
  FiSmartphone,
  FiUser,
  FiUsers,
} from 'react-icons/fi'
import { NavLink } from 'react-router'
import WorkspaceSwitcher from '../WorkspaceSwitcher'
import { PERMISSIONS, type PermissionKey } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'

type AppSidebarProps = {
  platform: boolean
  loggingOut: boolean
  onNavigate: () => void
  onLogout: () => void
}

type WorkspaceLink = {
  label: string
  to: string
  icon: ReactNode
  permission?: PermissionKey
}

const inboxLinks: WorkspaceLink[] = [
  { label: 'WhatsApp', to: '/inbox/social-media', icon: <FiMessageSquare />, permission: PERMISSIONS.WHATSAPP_READ },
  { label: 'SMS', to: '/inbox/sms', icon: <FiSmartphone />, permission: PERMISSIONS.SMS_READ },
  { label: 'Email', to: '/inbox/email', icon: <FiMail />, permission: PERMISSIONS.EMAIL_READ },
]

const broadcastLinks: WorkspaceLink[] = [
  { label: 'SMS', to: '/broadcast/sms', icon: <FiSmartphone />, permission: PERMISSIONS.BROADCAST_READ },
  { label: 'Social media', to: '/broadcast/social-media', icon: <FiSend />, permission: PERMISSIONS.BROADCAST_READ },
  { label: 'Email', to: '/broadcast/email', icon: <FiMail />, permission: PERMISSIONS.BROADCAST_READ },
]

const companyLinks: WorkspaceLink[] = [
  { label: 'Company details', to: '/company/details', icon: <FiSettings />, permission: PERMISSIONS.COMPANY_READ },
  { label: 'Plan details', to: '/company/plan', icon: <FiCreditCard />, permission: PERMISSIONS.BILLING_READ },
  { label: 'Users', to: '/company/users', icon: <FiUsers />, permission: PERMISSIONS.USER_READ },
]

export default function AppSidebar({ platform, loggingOut, onNavigate, onLogout }: AppSidebarProps) {
  const { can } = useIam()
  const [openSections, setOpenSections] = useState<Record<string, boolean>>({
    inbox: false,
    broadcast: false,
    company: false,
    account: false,
  })

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `sidebar-link ${isActive ? 'sidebar-link-active' : ''}`
  const nestedLinkClass = ({ isActive }: { isActive: boolean }) =>
    `sidebar-link sidebar-link-nested ${isActive ? 'sidebar-link-active' : ''}`
  const toggleSection = (section: string) =>
    setOpenSections((current) => ({ ...current, [section]: !current[section] }))
  const visible = (links: WorkspaceLink[]) => links.filter((link) => !link.permission || can(link.permission))

  const renderSection = (key: string, label: string, icon: ReactNode, links: WorkspaceLink[]) => {
    const items = visible(links)
    if (items.length === 0) return null
    return (
      <div>
        <button type="button" className="sidebar-link w-full" onClick={(event) => { event.stopPropagation(); toggleSection(key) }} aria-expanded={openSections[key]}>
          {icon}<span className="min-w-0 flex-1 text-left">{label}</span><FiChevronDown aria-hidden="true" className={`shrink-0 transition-transform ${openSections[key] ? 'rotate-180' : ''}`} />
        </button>
        <div className={`sidebar-submenu ${openSections[key] ? 'is-open' : ''}`}>
          {items.map((item) => <NavLink key={item.to} to={item.to} className={nestedLinkClass} onClick={onNavigate}><span aria-hidden="true">{item.icon}</span>{item.label}</NavLink>)}
        </div>
      </div>
    )
  }

  return (
    <>
      <div className="pb-8 pt-1">{platform ? <div className="px-3"><div className="font-bold text-slate-950">Messaging</div><div className="text-xs text-slate-500">Platform administration</div></div> : <WorkspaceSwitcher />}</div>
      <nav aria-label="Main navigation" className="space-y-1">
        <p className="sidebar-label">{platform ? 'PLATFORM' : 'APP'}</p>
        {platform ? (
          <>
            <NavLink to="/platform" end className={linkClass} onClick={onNavigate}><FiHome />Dashboard</NavLink>
            <NavLink to="/platform/companies" className={linkClass} onClick={onNavigate}><FiBriefcase />Companies</NavLink>
            <NavLink to="/platform/users" className={linkClass} onClick={onNavigate}><FiUsers />Users</NavLink>
            <NavLink to="/platform/plans" className={linkClass} onClick={onNavigate}><FiCreditCard />Plans</NavLink>
            <NavLink to="/platform/admins" className={linkClass} onClick={onNavigate}><FiShield />Admins</NavLink>
            <NavLink to="/platform/billing" className={linkClass} onClick={onNavigate}><FiCreditCard />Billing</NavLink>
            <NavLink to="/platform/audit" className={linkClass} onClick={onNavigate}><FiFileText />Audit logs</NavLink>
          </>
        ) : (
          <>
            <NavLink to="/workspace" end className={linkClass} onClick={onNavigate}><FiHome aria-hidden="true" />Dashboard</NavLink>
            {renderSection('inbox', 'Inbox', <FiInbox aria-hidden="true" />, inboxLinks)}
            {renderSection('broadcast', 'Broadcast', <FiRadio aria-hidden="true" />, broadcastLinks)}
            {can(PERMISSIONS.TEMPLATE_READ) && <NavLink to="/templates" className={linkClass} onClick={onNavigate}><FiFileText aria-hidden="true" />Templates</NavLink>}
            {renderSection('company', 'Company', <FiBriefcase aria-hidden="true" />, companyLinks)}
          </>
        )}
        {!platform && (
          <div>
            <button type="button" className="sidebar-link w-full" onClick={(event) => { event.stopPropagation(); toggleSection('account') }} aria-expanded={openSections.account}>
              <FiUser aria-hidden="true" /><span className="min-w-0 flex-1 text-left">Account</span><FiChevronDown aria-hidden="true" className={`shrink-0 transition-transform ${openSections.account ? 'rotate-180' : ''}`} />
            </button>
            <div className={`sidebar-submenu ${openSections.account ? 'is-open' : ''}`}>
              <NavLink to="/account/profile" className={nestedLinkClass} onClick={onNavigate}><FiShield aria-hidden="true" />Profile</NavLink>
              <NavLink to="/account/settings" className={nestedLinkClass} onClick={onNavigate}><FiSettings aria-hidden="true" />Settings</NavLink>
            </div>
          </div>
        )}
      </nav>
      <div className="mt-auto border-t border-slate-100 pt-5"><button type="button" className="sidebar-link w-full text-left" onClick={onLogout} disabled={loggingOut}><FiLogOut aria-hidden="true" />{loggingOut ? 'Logging out...' : 'Logout'}</button></div>
    </>
  )
}
