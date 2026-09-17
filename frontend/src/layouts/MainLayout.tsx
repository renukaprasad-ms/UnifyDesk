import { useEffect, useState } from 'react'
import { Outlet, useNavigate } from 'react-router'
import Header from '../components/Header'
import AdminSidebar from '../components/navigation/AdminSidebar'
import AppSidebar from '../components/navigation/AppSidebar'
import { authApi } from '../features/auth/authApi'
import { platformApi } from '../features/platform/platformApi'
import type { PlatformAccess } from '../features/platform/types'
import { sessionCleared } from '../store/auth/authSlice'
import { useAppDispatch } from '../store/hooks'

export default function MainLayout({ platform = false }: { platform?: boolean }) {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)
  const [loggingOut, setLoggingOut] = useState(false)
  const [platformAccess, setPlatformAccess] = useState<PlatformAccess | null>(null)
  const [railHovered, setRailHovered] = useState(false)

  useEffect(() => {
    let active = true
    platformApi.me().then((access) => {
      if (active && access.platformAdmin) setPlatformAccess(access)
    }).catch(() => {
      if (active) setPlatformAccess(null)
    })
    return () => {
      active = false
    }
  }, [])

  const logout = async () => {
    if (loggingOut) return
    setLoggingOut(true)
    try {
      await authApi.logout()
    } finally {
      dispatch(sessionCleared())
      navigate('/login', { replace: true })
    }
  }

  const closeNavigation = () => setMenuOpen(false)

  return (
    <div className={`app-shell ${platform ? 'is-platform-shell' : ''}`}>
      {menuOpen && (
        <button
          aria-label="Close navigation"
          onClick={() => setMenuOpen(false)}
          className="fixed inset-0 z-30 bg-slate-900/30 lg:hidden"
        />
      )}
      <aside
        id="main-navigation"
        className={`app-sidebar ${platform ? 'is-compact' : ''} ${railHovered ? 'is-hover-expanded' : ''} ${menuOpen ? 'is-open' : ''}`}
        onMouseEnter={() => platform && setRailHovered(true)}
        onMouseLeave={() => platform && setRailHovered(false)}
      >
        <AppSidebar
          platformAccess={platformAccess}
          compact={platform}
          loggingOut={loggingOut}
          onNavigate={closeNavigation}
          onLogout={() => void logout()}
        />
      </aside>

      {platform && platformAccess && (
        <aside className="admin-sidebar">
          <AdminSidebar access={platformAccess} onNavigate={closeNavigation} />
        </aside>
      )}

      <div className={`app-content min-w-0 ${platform ? 'with-admin-sidebar' : ''}`}>
        <Header menuOpen={menuOpen} onToggleMenu={() => setMenuOpen((open) => !open)} />
        <main id="main-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
