import { useState } from 'react'
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
import { NavLink, Outlet, useNavigate } from 'react-router'
import { useDispatch } from 'react-redux'
import Header from '../components/Header'
import WorkspaceSwitcher from '../components/WorkspaceSwitcher'
import { authApi } from '../features/auth/authApi'
import { useCompanyPermissions } from '../features/company/useCompanyPermissions'
import type { AppDispatch } from '../store/store'
import { clearAuthUser } from '../store/user/userSlice'

export default function MainLayout() {
  const dispatch = useDispatch<AppDispatch>()
  const navigate = useNavigate()
  const { can } = useCompanyPermissions()
  const [menuOpen, setMenuOpen] = useState(false)
  const [openSections, setOpenSections] = useState<Record<string, boolean>>({
    inbox: false,
    broadcast: false,
    company: false,
    account: false,
  })
  const [loggingOut, setLoggingOut] = useState(false)
  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `sidebar-link ${isActive ? 'sidebar-link-active' : ''}`
  const nestedLinkClass = ({ isActive }: { isActive: boolean }) =>
    `sidebar-link sidebar-link-nested ${isActive ? 'sidebar-link-active' : ''}`
  const canViewCompany = can('COMPANY_VIEW')
  const canViewBilling = can('BILLING_VIEW')
  const showCompanySection = canViewCompany || canViewBilling

  const toggleSection = (section: string) => {
    setOpenSections((current) => ({ ...current, [section]: !current[section] }))
  }

  const logout = async () => {
    if (loggingOut) return
    setLoggingOut(true)
    try {
      await authApi.logout()
    } finally {
      dispatch(clearAuthUser())
      navigate('/login', { replace: true })
    }
  }

  return (
    <div className="app-shell">
      {menuOpen && (
        <button
          aria-label="Close navigation"
          onClick={() => setMenuOpen(false)}
          className="fixed inset-0 z-30 bg-slate-900/30 lg:hidden"
        />
      )}
      <aside id="main-navigation" className={`app-sidebar ${menuOpen ? 'is-open' : ''}`}>
        <div className="pb-8 pt-1">
          <WorkspaceSwitcher />
        </div>
        <nav aria-label="Main navigation" className="space-y-1" onClick={() => setMenuOpen(false)}>
          <p className="sidebar-label">APP</p>
          <NavLink to="/" end className={linkClass}>
            <FiHome aria-hidden="true" />Dashboard
          </NavLink>

          <div>
            <button
              type="button"
              className="sidebar-link w-full"
              onClick={(event) => {
                event.stopPropagation()
                toggleSection('inbox')
              }}
              aria-expanded={openSections.inbox}
            >
              <FiInbox aria-hidden="true" />
              <span className="min-w-0 flex-1 text-left">Inbox</span>
              <FiChevronDown
                aria-hidden="true"
                className={`shrink-0 transition-transform ${openSections.inbox ? 'rotate-180' : ''}`}
              />
            </button>
            <div
              className={`sidebar-submenu ${openSections.inbox ? 'is-open' : ''}`}
              onClick={() => setMenuOpen(false)}
            >
                <NavLink to="/inbox/social-media" className={nestedLinkClass}>
                  <FiMessageSquare aria-hidden="true" />Social media
                </NavLink>
                <NavLink to="/inbox/sms" className={nestedLinkClass}>
                  <FiSmartphone aria-hidden="true" />SMS
                </NavLink>
                <NavLink to="/inbox/email" className={nestedLinkClass}>
                  <FiMail aria-hidden="true" />Email
                </NavLink>
            </div>
          </div>

          <div>
            <button
              type="button"
              className="sidebar-link w-full"
              onClick={(event) => {
                event.stopPropagation()
                toggleSection('broadcast')
              }}
              aria-expanded={openSections.broadcast}
            >
              <FiRadio aria-hidden="true" />
              <span className="min-w-0 flex-1 text-left">Broadcast</span>
              <FiChevronDown
                aria-hidden="true"
                className={`shrink-0 transition-transform ${openSections.broadcast ? 'rotate-180' : ''}`}
              />
            </button>
            <div
              className={`sidebar-submenu ${openSections.broadcast ? 'is-open' : ''}`}
              onClick={() => setMenuOpen(false)}
            >
                <NavLink to="/broadcast/sms" className={nestedLinkClass}>
                  <FiSmartphone aria-hidden="true" />SMS
                </NavLink>
                <NavLink to="/broadcast/social-media" className={nestedLinkClass}>
                  <FiSend aria-hidden="true" />SM
                </NavLink>
                <NavLink to="/broadcast/email" className={nestedLinkClass}>
                  <FiMail aria-hidden="true" />Email
                </NavLink>
            </div>
          </div>

          <NavLink to="/templates" className={linkClass}>
            <FiFileText aria-hidden="true" />Templates
          </NavLink>

          {showCompanySection && (
            <div>
              <button
                type="button"
                className="sidebar-link w-full"
                onClick={(event) => {
                  event.stopPropagation()
                  toggleSection('company')
                }}
                aria-expanded={openSections.company}
              >
                <FiBriefcase aria-hidden="true" />
                <span className="min-w-0 flex-1 text-left">Company</span>
                <FiChevronDown
                  aria-hidden="true"
                  className={`shrink-0 transition-transform ${openSections.company ? 'rotate-180' : ''}`}
                />
              </button>
              <div
                className={`sidebar-submenu ${openSections.company ? 'is-open' : ''}`}
                onClick={() => setMenuOpen(false)}
              >
                {canViewCompany && (
                <NavLink to="/company/details" className={nestedLinkClass}>
                  <FiSettings aria-hidden="true" />Company details
                </NavLink>
                )}
                {canViewBilling && (
                <NavLink to="/company/plan" className={nestedLinkClass}>
                  <FiCreditCard aria-hidden="true" />Plan details
                </NavLink>
                )}
                {canViewCompany && (
                <NavLink to="/company/users" className={nestedLinkClass}>
                  <FiUsers aria-hidden="true" />Users
                </NavLink>
                )}
              </div>
            </div>
          )}

          <div>
            <button
              type="button"
              className="sidebar-link w-full"
              onClick={(event) => {
                event.stopPropagation()
                toggleSection('account')
              }}
              aria-expanded={openSections.account}
            >
              <FiUser aria-hidden="true" />
              <span className="min-w-0 flex-1 text-left">Account</span>
              <FiChevronDown
                aria-hidden="true"
                className={`shrink-0 transition-transform ${openSections.account ? 'rotate-180' : ''}`}
              />
            </button>
            <div
              className={`sidebar-submenu ${openSections.account ? 'is-open' : ''}`}
              onClick={() => setMenuOpen(false)}
            >
                <NavLink to="/account/profile" className={nestedLinkClass}>
                  <FiShield aria-hidden="true" />Profile
                </NavLink>
                <NavLink to="/account/settings" className={nestedLinkClass}>
                  <FiSettings aria-hidden="true" />Settings
                </NavLink>
            </div>
          </div>
        </nav>
        <div className="mt-auto border-t border-slate-100 pt-5">
          <button
            type="button"
            className="sidebar-link w-full text-left"
            onClick={logout}
            disabled={loggingOut}
          >
            <FiLogOut aria-hidden="true" />
            {loggingOut ? 'Logging out...' : 'Logout'}
          </button>
        </div>
      </aside>
      <div className="min-w-0 lg:ml-[248px]">
        <Header menuOpen={menuOpen} onToggleMenu={() => setMenuOpen(!menuOpen)} />
        <main id="main-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
