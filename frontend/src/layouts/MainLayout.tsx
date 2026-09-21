import { useEffect, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router'
import Header from '../components/Header'
import AdminSidebar from '../components/navigation/AdminSidebar'
import ChannelContextSidebar from '../components/navigation/ChannelContextSidebar'
import AppSidebar from '../components/navigation/AppSidebar'
import { authApi } from '../features/auth/authApi'
import { platformApi } from '../features/platform/platformApi'
import type { PlatformAccess } from '../features/platform/types'
import { realtimeClient } from '../features/realtime/realtimeClient'
import { sessionCleared } from '../store/auth/authSlice'
import { useAppDispatch } from '../store/hooks'

export default function MainLayout({ platform = false }: { platform?: boolean }) {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)
  const [contextOpen, setContextOpen] = useState(false)
  const [loggingOut, setLoggingOut] = useState(false)
  const [platformAccess, setPlatformAccess] = useState<PlatformAccess | null>(null)
  const [railHovered, setRailHovered] = useState(false)
  const inboxMode = location.pathname.startsWith('/inbox/')
  const broadcastMode = location.pathname.startsWith('/broadcast/')
  const channelContextMode = inboxMode || broadcastMode
  const selectedEmailFolder =
    new URLSearchParams(location.search).get('folder') === 'SENT' ? 'Sent' : 'Inbox'
  const channelContextLabel = inboxMode
    ? selectedEmailFolder
    : broadcastMode
      ? 'Broadcast'
      : undefined
  const compactNavigation = platform || channelContextMode

  useEffect(() => {
    realtimeClient.connect()
    return () => realtimeClient.disconnect()
  }, [])

  useEffect(() => {
    let active = true
    platformApi
      .me()
      .then((access) => {
        if (active && access.platformAdmin) setPlatformAccess(access)
      })
      .catch(() => {
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
  const closeContext = () => setContextOpen(false)

  useEffect(() => {
    setMenuOpen(false)
    setContextOpen(false)
  }, [location.pathname])

  useEffect(() => {
    if (!menuOpen && !contextOpen) return
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      setMenuOpen(false)
      setContextOpen(false)
    }
    window.addEventListener('keydown', onKeyDown)

    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', onKeyDown)
    }
  }, [contextOpen, menuOpen])

  return (
    <div className={`app-shell ${platform ? 'is-platform-shell' : ''}`}>
      {(menuOpen || contextOpen) && (
        <button
          aria-label="Close navigation"
          onClick={() => {
            setMenuOpen(false)
            setContextOpen(false)
          }}
          className="fixed inset-0 z-30 bg-slate-900/30 lg:hidden"
        />
      )}
      <aside
        id="main-navigation"
        className={`app-sidebar ${compactNavigation ? 'is-compact' : ''} ${railHovered ? 'is-hover-expanded' : ''} ${menuOpen ? 'is-open' : ''}`}
        onMouseEnter={() => compactNavigation && setRailHovered(true)}
        onMouseLeave={() => compactNavigation && setRailHovered(false)}
      >
        {platform && platformAccess ? (
          <>
            <div className="flex min-h-full flex-col lg:hidden">
              <AdminSidebar access={platformAccess} onNavigate={closeNavigation} />
              <div className="mt-auto border-t border-slate-100 pt-5">
                <button
                  type="button"
                  className="sidebar-link w-full text-left"
                  onClick={() => void logout()}
                  disabled={loggingOut}
                >
                  {loggingOut ? 'Logging out...' : 'Logout'}
                </button>
              </div>
            </div>
            <div className="hidden lg:contents">
              <AppSidebar
                platformAccess={platformAccess}
                compact
                loggingOut={loggingOut}
                onNavigate={closeNavigation}
                onLogout={() => void logout()}
              />
            </div>
          </>
        ) : (
          <>
            <AppSidebar
              platformAccess={platformAccess}
              compact={compactNavigation}
              loggingOut={loggingOut}
              onNavigate={closeNavigation}
              onLogout={() => void logout()}
            />
          </>
        )}
      </aside>

      {platform && platformAccess && (
        <aside className="admin-sidebar">
          <AdminSidebar access={platformAccess} onNavigate={closeNavigation} />
        </aside>
      )}

      {!platform && channelContextMode && (
        <aside
          id="channel-navigation"
          className={`channel-context-sidebar ${contextOpen ? 'is-open' : ''}`}
        >
          <ChannelContextSidebar
            mode={inboxMode ? 'inbox' : 'broadcast'}
            onNavigate={closeContext}
          />
        </aside>
      )}

      <div
        className={`app-content min-w-0 ${
          platform ? 'with-admin-sidebar' : channelContextMode ? 'with-channel-context-sidebar' : ''
        }`}
      >
        <Header
          menuOpen={menuOpen}
          onToggleMenu={() => {
            setContextOpen(false)
            setMenuOpen((open) => !open)
          }}
          contextAvailable={!platform && channelContextMode}
          contextOpen={contextOpen}
          contextLabel={channelContextLabel}
          onToggleContext={() => {
            setMenuOpen(false)
            setContextOpen((open) => !open)
          }}
        />
        <main id="main-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
