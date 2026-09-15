import { useState } from 'react'
import { FiHome, FiLogOut, FiShield } from 'react-icons/fi'
import { NavLink, Outlet, useNavigate } from 'react-router'
import { useDispatch } from 'react-redux'
import Header from '../components/Header'
import WorkspaceSwitcher from '../components/WorkspaceSwitcher'
import { authApi } from '../features/auth/authApi'
import type { AppDispatch } from '../store/store'
import { clearAuthUser } from '../store/user/userSlice'

export default function MainLayout() {
  const dispatch = useDispatch<AppDispatch>()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)
  const [loggingOut, setLoggingOut] = useState(false)
  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `sidebar-link ${isActive ? 'sidebar-link-active' : ''}`

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
            <FiHome aria-hidden="true" />Home
          </NavLink>
          <NavLink to="/auth" className={linkClass}>
            <FiShield aria-hidden="true" />Auth layout
          </NavLink>
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
