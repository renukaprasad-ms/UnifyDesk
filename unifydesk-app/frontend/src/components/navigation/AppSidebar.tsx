import { useState, type ReactNode } from 'react'
import {
  FiBell,
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
  FiKey,
  FiLink,
  FiUser,
  FiUsers,
  FiTrendingUp,
} from 'react-icons/fi'
import { NavLink } from 'react-router'
import type { PlatformAccess } from '../../features/platform/types'
import { PERMISSIONS, type PermissionKey } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { useNotifications } from '../../features/notifications/notificationContext'
import { formatUnreadBadge } from '../../features/notifications/notificationUtils'
import WorkspaceSwitcher from '../WorkspaceSwitcher'

type AppSidebarProps = {
  platformAccess: PlatformAccess | null
  compact?: boolean
  closeSignal?: number
  loggingOut: boolean
  onNavigate: () => void
  onLogout: () => void
  notificationPath?: string
}

type WorkspaceLink = {
  label: string
  to: string
  icon: ReactNode
  permission?: PermissionKey
  anyOf?: PermissionKey[]
}

const inboxLinks: WorkspaceLink[] = [
  {
    label: 'WhatsApp',
    to: '/inbox/social-media',
    icon: <FiMessageSquare />,
    permission: PERMISSIONS.WHATSAPP_READ,
  },
  { label: 'SMS', to: '/inbox/sms', icon: <FiSmartphone />, permission: PERMISSIONS.SMS_READ },
  { label: 'Email', to: '/inbox/email', icon: <FiMail />, permission: PERMISSIONS.EMAIL_READ },
]

const broadcastLinks: WorkspaceLink[] = [
  {
    label: 'SMS',
    to: '/broadcast/sms',
    icon: <FiSmartphone />,
    permission: PERMISSIONS.BROADCAST_READ,
  },
  {
    label: 'WhatsApp',
    to: '/broadcast/social-media',
    icon: <FiSend />,
    permission: PERMISSIONS.BROADCAST_READ,
  },
  {
    label: 'Email',
    to: '/broadcast/email',
    icon: <FiMail />,
    permission: PERMISSIONS.BROADCAST_READ,
  },
]

const companyLinks: WorkspaceLink[] = [
  {
    label: 'Company details',
    to: '/company/details',
    icon: <FiSettings />,
    permission: PERMISSIONS.COMPANY_READ,
  },
  {
    label: 'Connections',
    to: '/company/connections',
    icon: <FiLink />,
    anyOf: [
      PERMISSIONS.WHATSAPP_READ,
      PERMISSIONS.WHATSAPP_CONNECT,
      PERMISSIONS.EMAIL_READ,
      PERMISSIONS.EMAIL_CONNECT,
      PERMISSIONS.SMS_READ,
      PERMISSIONS.SMS_CONNECT,
      PERMISSIONS.BROADCAST_READ,
      PERMISSIONS.BROADCAST_MODIFY,
    ],
  },
  {
    label: 'Plan details',
    to: '/company/plan',
    icon: <FiCreditCard />,
    permission: PERMISSIONS.BILLING_READ,
  },
  { label: 'Users', to: '/company/users', icon: <FiUsers />, permission: PERMISSIONS.USER_READ },
  {
    label: 'Roles & permissions',
    to: '/company/roles',
    icon: <FiKey />,
    permission: PERMISSIONS.ROLE_READ,
  },
]

export default function AppSidebar({
  platformAccess,
  compact = false,
  closeSignal = 0,
  loggingOut,
  onNavigate,
  onLogout,
  notificationPath = '/notifications',
}: AppSidebarProps) {
  const { can } = useIam()
  const { unreadCount } = useNotifications()
  const notificationBadge = formatUnreadBadge(unreadCount.total)
  const platformWorkspaceAccess = Boolean(platformAccess?.platformAdmin)
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
    setOpenSections((current) => ({
      inbox: false,
      broadcast: false,
      company: false,
      account: false,
      [section]: !current[section],
    }))
  const hasWorkspacePermission = (permission?: PermissionKey) =>
    !permission || can(permission)
  const visible = (links: WorkspaceLink[]) =>
    links.filter(
      (link) =>
        hasWorkspacePermission(link.permission) &&
        (!link.anyOf || link.anyOf.some((permission) => can(permission))),
    )

  const renderSection = (key: string, label: string, icon: ReactNode, links: WorkspaceLink[]) => {
    const items = visible(links)
    if (items.length === 0) return null
    return (
      <div>
        <button
          type="button"
          title={compact ? label : undefined}
          className="sidebar-link w-full"
          onClick={(event) => {
            event.stopPropagation()
            toggleSection(key)
          }}
          aria-expanded={openSections[key]}
        >
          <span className="sidebar-icon">{icon}</span>
          <span className="sidebar-copy min-w-0 flex-1 text-left">{label}</span>
          <FiChevronDown
            aria-hidden="true"
            className={`sidebar-copy shrink-0 transition-transform ${openSections[key] ? 'rotate-180' : ''}`}
          />
        </button>
        <div className={`sidebar-submenu ${openSections[key] ? 'is-open' : ''}`}>
          {items.map((item) => (
            <NavLink key={item.to} to={item.to} className={nestedLinkClass} onClick={onNavigate}>
              <span aria-hidden="true" className="sidebar-icon">
                {item.icon}
              </span>
              <span className="sidebar-copy">{item.label}</span>
            </NavLink>
          ))}
        </div>
      </div>
    )
  }

  return (
    <>
      <div className="sidebar-workspace pb-8 pt-1">
        <WorkspaceSwitcher platformAdmin={platformWorkspaceAccess} closeSignal={closeSignal} />
      </div>
      <nav aria-label="Main navigation" className="space-y-1">
        <p className="sidebar-label sidebar-copy">APP</p>
        <NavLink
          to="/workspace"
          end
          className={linkClass}
          onClick={onNavigate}
          title={compact ? 'Dashboard' : undefined}
        >
          <FiHome className="sidebar-icon" aria-hidden="true" />
          <span className="sidebar-copy">Dashboard</span>
        </NavLink>
        <NavLink
          to={notificationPath}
          className={linkClass}
          onClick={onNavigate}
          title={compact ? 'Notifications' : undefined}
          aria-label={
            unreadCount.total > 0 ? `Notifications, ${unreadCount.total} unread` : 'Notifications'
          }
        >
          <FiBell className="sidebar-icon" aria-hidden="true" />
          <span className="sidebar-copy min-w-0 flex-1">Notifications</span>
          {notificationBadge && (
            <span className="notification-badge sidebar-notification-badge">
              {notificationBadge}
            </span>
          )}
        </NavLink>
        {hasWorkspacePermission(PERMISSIONS.CONTACT_READ) && (
          <NavLink
            to="/contacts"
            className={linkClass}
            onClick={onNavigate}
            title={compact ? 'Contacts' : undefined}
          >
            <FiUsers className="sidebar-icon" aria-hidden="true" />
            <span className="sidebar-copy">Contacts</span>
          </NavLink>
        )}
        {renderSection('broadcast', 'Broadcast', <FiRadio aria-hidden="true" />, broadcastLinks)}
        {renderSection('inbox', 'Inbox', <FiInbox aria-hidden="true" />, inboxLinks)}
        {hasWorkspacePermission(PERMISSIONS.ADS_READ) && (
          <NavLink
            to="/ad-center"
            className={linkClass}
            onClick={onNavigate}
            title={compact ? 'Ad Center' : undefined}
          >
            <FiTrendingUp className="sidebar-icon" aria-hidden="true" />
            <span className="sidebar-copy">Ad Center</span>
          </NavLink>
        )}
        {hasWorkspacePermission(PERMISSIONS.TEMPLATE_READ) && (
          <NavLink
            to="/templates"
            className={linkClass}
            onClick={onNavigate}
            title={compact ? 'Templates' : undefined}
          >
            <FiFileText className="sidebar-icon" aria-hidden="true" />
            <span className="sidebar-copy">Templates</span>
          </NavLink>
        )}
        {renderSection('company', 'Company', <FiBriefcase aria-hidden="true" />, companyLinks)}

        {platformWorkspaceAccess && (
          <div className="pt-3">
            <p className="sidebar-label sidebar-copy">PLATFORM</p>
            <NavLink
              to="/platform"
              className={linkClass}
              onClick={onNavigate}
              title={compact ? 'Administration' : undefined}
            >
              <FiShield className="sidebar-icon" aria-hidden="true" />
              <span className="sidebar-copy min-w-0 flex-1">Administration</span>
              <span className="sidebar-copy rounded-md bg-indigo-50 px-1.5 py-0.5 text-[9px] font-bold text-indigo-500">
                ADMIN
              </span>
            </NavLink>
          </div>
        )}

        <div className="pt-3">
          <p className="sidebar-label sidebar-copy">YOU</p>
          <button
            type="button"
            title={compact ? 'Account' : undefined}
            className="sidebar-link w-full"
            onClick={(event) => {
              event.stopPropagation()
              toggleSection('account')
            }}
            aria-expanded={openSections.account}
          >
            <FiUser className="sidebar-icon" aria-hidden="true" />
            <span className="sidebar-copy min-w-0 flex-1 text-left">Account</span>
            <FiChevronDown
              aria-hidden="true"
              className={`sidebar-copy shrink-0 transition-transform ${openSections.account ? 'rotate-180' : ''}`}
            />
          </button>
          <div className={`sidebar-submenu ${openSections.account ? 'is-open' : ''}`}>
            <NavLink to="/account/profile" className={nestedLinkClass} onClick={onNavigate}>
              <FiShield className="sidebar-icon" aria-hidden="true" />
              <span className="sidebar-copy">Profile</span>
            </NavLink>
            <NavLink to="/account/invitations" className={nestedLinkClass} onClick={onNavigate}>
              <FiMail className="sidebar-icon" aria-hidden="true" />
              <span className="sidebar-copy">Invitations</span>
            </NavLink>
            <NavLink to="/account/settings" className={nestedLinkClass} onClick={onNavigate}>
              <FiSettings className="sidebar-icon" aria-hidden="true" />
              <span className="sidebar-copy">Settings</span>
            </NavLink>
          </div>
        </div>
      </nav>
      <div className="mt-auto border-t border-slate-100 pt-5">
        <button
          type="button"
          title={compact ? 'Logout' : undefined}
          className="sidebar-link w-full text-left"
          onClick={onLogout}
          disabled={loggingOut}
        >
          <FiLogOut className="sidebar-icon" aria-hidden="true" />
          <span className="sidebar-copy">{loggingOut ? 'Logging out...' : 'Logout'}</span>
        </button>
      </div>
    </>
  )
}
