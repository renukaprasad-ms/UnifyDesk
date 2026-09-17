import { useState } from 'react'
import { Outlet, useNavigate } from 'react-router'
import Header from '../components/Header'
import AppSidebar from '../components/navigation/AppSidebar'
import { authApi } from '../features/auth/authApi'
import { useAppDispatch } from '../store/hooks'
import { sessionCleared } from '../store/auth/authSlice'

export default function MainLayout({ platform = false }: { platform?: boolean }) {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)
  const [loggingOut, setLoggingOut] = useState(false)

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
        <AppSidebar
          platform={platform}
          loggingOut={loggingOut}
          onNavigate={() => setMenuOpen(false)}
          onLogout={() => void logout()}
        />
      </aside>
      <div className="min-w-0 lg:ml-[248px]">
        <Header menuOpen={menuOpen} onToggleMenu={() => setMenuOpen((open) => !open)} />
        <main id="main-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
