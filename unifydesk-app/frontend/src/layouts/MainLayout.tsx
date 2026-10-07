import { useEffect, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router'
import Header from '../components/Header'
import AdminSidebar from '../components/navigation/AdminSidebar'
import ChannelContextSidebar from '../components/navigation/ChannelContextSidebar'
import GlobalSyncProgress from '../components/inbox/GlobalSyncProgress'
import AdCenterSidebar from '../components/navigation/AdCenterSidebar'
import AppSidebar from '../components/navigation/AppSidebar'
import ConfirmDialog from '../components/ui/ConfirmDialog'
import { authApi } from '../features/auth/authApi'
import { iamApi } from '../features/iam/iamApi'
import { platformApi } from '../features/platform/platformApi'
import NotificationProvider from '../features/notifications/NotificationProvider'
import { revokeCurrentBrowserSubscriptionBestEffort } from '../features/notifications/notificationBrowser'
import type { PlatformAccess } from '../features/platform/types'
import { realtimeClient } from '../features/realtime/realtimeClient'
import { sessionCleared } from '../store/auth/authSlice'
import { useAppDispatch, useAppSelector } from '../store/hooks'
import {
  iamLoadFailed,
  iamLoadStarted,
  iamLoadSucceeded,
  selectIamState,
} from '../store/iam/iamSlice'
import { selectSelectedCompanyId } from '../store/workspace/workspaceSlice'

export default function MainLayout({ platform = false }: { platform?: boolean }) {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)
  const [contextOpen, setContextOpen] = useState(false)
  const [loggingOut, setLoggingOut] = useState(false)
  const [logoutConfirmOpen, setLogoutConfirmOpen] = useState(false)
  const [platformAccess, setPlatformAccess] = useState<PlatformAccess | null>(null)
  const [railHovered, setRailHovered] = useState(false)
  const [navigationCloseSignal, setNavigationCloseSignal] = useState(0)
  const selectedCompanyId = useAppSelector(selectSelectedCompanyId)
  const iam = useAppSelector(selectIamState)
  const inboxMode = location.pathname.startsWith('/inbox/')
  const broadcastMode = location.pathname.startsWith('/broadcast/')
  const adCenterMode = location.pathname.startsWith('/ad-center')
  const selectedBroadcastGroupId = new URLSearchParams(location.search).get('groupId')
  const selectedBroadcastGroup = useAppSelector((state) =>
    state.broadcast.groups.find((group) => group.id === selectedBroadcastGroupId),
  )
  const channelContextMode = inboxMode || broadcastMode
  const workspaceContextMode = channelContextMode || adCenterMode
  const selectedEmailFolder =
    new URLSearchParams(location.search).get('folder') === 'SENT' ? 'Sent' : 'Inbox'
  const channelContextLabel = inboxMode
    ? selectedEmailFolder
    : broadcastMode
      ? selectedBroadcastGroup?.name || 'Broadcast'
      : undefined
  const compactNavigation = platform || workspaceContextMode

  useEffect(() => {
    realtimeClient.connect()
    return () => realtimeClient.disconnect()
  }, [])

  useEffect(() => {
    if (platform || !selectedCompanyId) return
    if (
      iam.companyId === selectedCompanyId &&
      (iam.status === 'loading' || iam.status === 'ready')
    ) {
      return
    }

    let active = true
    dispatch(iamLoadStarted(selectedCompanyId))
    iamApi
      .myPermissions(selectedCompanyId)
      .then((response) => {
        if (!active) return
        dispatch(
          iamLoadSucceeded({
            companyId: selectedCompanyId,
            owner: response.owner,
            permissions: response.permissions,
          }),
        )
      })
      .catch(() => {
        if (!active) return
        dispatch(
          iamLoadFailed({
            companyId: selectedCompanyId,
            error: 'Unable to load workspace permissions',
          }),
        )
      })

    return () => {
      active = false
    }
  }, [dispatch, iam.companyId, iam.status, platform, selectedCompanyId])

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
      await revokeCurrentBrowserSubscriptionBestEffort()
      await authApi.logout()
    } finally {
      dispatch(sessionCleared())
      navigate('/login', { replace: true })
    }
  }

  const closeWorkspaceMenus = () => setNavigationCloseSignal((signal) => signal + 1)
  const closeNavigation = () => {
    setMenuOpen(false)
    setRailHovered(false)
    closeWorkspaceMenus()
  }
  const closeContext = () => setContextOpen(false)
  const closeAllNavigation = () => {
    setMenuOpen(false)
    setContextOpen(false)
    setRailHovered(false)
    closeWorkspaceMenus()
  }

  useEffect(() => {
    closeAllNavigation()
  }, [location.pathname])

  useEffect(() => {
    if (menuOpen && contextOpen) setContextOpen(false)
  }, [contextOpen, menuOpen])

  useEffect(() => {
    if (!menuOpen && !contextOpen) return
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      closeAllNavigation()
    }
    window.addEventListener('keydown', onKeyDown)

    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', onKeyDown)
    }
  }, [contextOpen, menuOpen])

  return (
    <NotificationProvider companyIdOverride={platform ? null : undefined}>
      <div className={`app-shell ${platform ? 'is-platform-shell' : ''}`}>
        {(menuOpen || contextOpen) && (
          <button
            aria-label="Close navigation"
            onClick={closeAllNavigation}
            className="fixed inset-0 z-30 bg-slate-900/30 lg:hidden"
          />
        )}
        <aside
          id="main-navigation"
          className={`app-sidebar ${compactNavigation ? 'is-compact' : ''} ${railHovered ? 'is-hover-expanded' : ''} ${menuOpen ? 'is-open' : ''}`}
          onMouseEnter={() => compactNavigation && setRailHovered(true)}
          onMouseLeave={() => {
            if (!compactNavigation) return
            setRailHovered(false)
            closeWorkspaceMenus()
          }}
        >
          {platform && platformAccess ? (
            <>
              <div className="flex min-h-full flex-col lg:hidden">
                <AdminSidebar access={platformAccess} onNavigate={closeNavigation} />
                <div className="mt-auto border-t border-slate-100 pt-5">
                  <button
                    type="button"
                    className="sidebar-link w-full text-left"
                    onClick={() => setLogoutConfirmOpen(true)}
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
                  closeSignal={navigationCloseSignal}
                  loggingOut={loggingOut}
                  onNavigate={closeNavigation}
                  onLogout={() => setLogoutConfirmOpen(true)}
                  notificationPath={platform ? '/platform/notifications' : '/notifications'}
                />
              </div>
            </>
          ) : (
            <>
              <AppSidebar
                platformAccess={platformAccess}
                compact={compactNavigation}
                closeSignal={navigationCloseSignal}
                loggingOut={loggingOut}
                onNavigate={closeNavigation}
                onLogout={() => setLogoutConfirmOpen(true)}
                notificationPath={platform ? '/platform/notifications' : '/notifications'}
              />
            </>
          )}
        </aside>

        {platform && platformAccess && (
          <aside className="admin-sidebar">
            <AdminSidebar access={platformAccess} onNavigate={closeNavigation} />
          </aside>
        )}

        {!platform && workspaceContextMode && (
          <aside
            id="channel-navigation"
            className={`channel-context-sidebar ${contextOpen ? 'is-open' : ''}`}
          >
            {adCenterMode ? (
              <AdCenterSidebar onNavigate={closeContext} />
            ) : (
              <ChannelContextSidebar
                mode={inboxMode ? 'inbox' : 'broadcast'}
                onNavigate={closeContext}
              />
            )}
          </aside>
        )}

        <div
          className={`app-content min-w-0 ${
            platform
              ? 'with-admin-sidebar'
              : workspaceContextMode
                ? 'with-channel-context-sidebar'
                : ''
          }`}
        >
          <Header
            menuOpen={menuOpen}
            onToggleMenu={() => {
              setContextOpen(false)
              setMenuOpen((open) => {
                if (open) {
                  setRailHovered(false)
                  closeWorkspaceMenus()
                }
                return !open
              })
            }}
            contextAvailable={!platform && workspaceContextMode}
            contextOpen={contextOpen}
            contextLabel={adCenterMode ? 'Ad Center' : channelContextLabel}
            onToggleContext={() => {
              setMenuOpen(false)
              setRailHovered(false)
              closeWorkspaceMenus()
              setContextOpen((open) => !open)
            }}
            notificationPath={platform ? '/platform/notifications' : '/notifications'}
          />
          <main
            id="main-content"
            className={inboxMode || broadcastMode ? 'app-route-content is-immersive' : 'app-route-content'}
          >
            <Outlet />
          </main>
        </div>
        {!platform && <GlobalSyncProgress />}
        <ConfirmDialog
          open={logoutConfirmOpen}
          title="Log out of UnifyDesk?"
          description="You will need to sign in again to continue."
          confirmLabel="Yes, log out"
          cancelLabel="No, stay"
          tone="warning"
          kind="logout"
          working={loggingOut}
          onCancel={() => setLogoutConfirmOpen(false)}
          onConfirm={() => void logout()}
        />
      </div>
    </NotificationProvider>
  )
}
