import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { realtimeClient } from '../realtime/realtimeClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId } from '../../store/workspace/workspaceSlice'
import { notificationApi } from './notificationApi'
import {
  installNotificationServiceWorkerNavigationBridge,
  reconcileExistingBrowserSubscription,
  registerNotificationServiceWorker,
} from './notificationBrowser'
import type {
  NotificationChangedPayload,
  NotificationItem,
  NotificationUnreadCount,
} from './types'

type RefreshReason = 'initial' | 'scope' | 'realtime' | 'reconnect' | 'focus' | 'broadcast' | 'push'

type NotificationContextValue = {
  companyId: string | null
  unreadCount: NotificationUnreadCount
  recent: NotificationItem[]
  loadingRecent: boolean
  connected: boolean
  refresh: (reason?: RefreshReason) => Promise<void>
  setRead: (notification: NotificationItem, read: boolean) => Promise<void>
  markAllRead: () => Promise<void>
  openNotification: (notification: NotificationItem) => Promise<string | null>
  broadcastRefresh: () => void
  refreshVersion: number
}

const EMPTY_COUNT: NotificationUnreadCount = { account: 0, company: 0, total: 0 }
const NotificationContext = createContext<NotificationContextValue | null>(null)
const CHANNEL_NAME = 'unifydesk-notifications'

const updateAppBadge = async (count: number) => {
  const badgeNavigator = navigator as Navigator & {
    setAppBadge?: (contents?: number) => Promise<void>
    clearAppBadge?: () => Promise<void>
  }

  try {
    if (count > 0 && badgeNavigator.setAppBadge) {
      await badgeNavigator.setAppBadge(count)
    } else if (badgeNavigator.clearAppBadge) {
      await badgeNavigator.clearAppBadge()
    }
  } catch {
    // App badging is an optional enhancement.
  }
}

export default function NotificationProvider({
  children,
  companyIdOverride,
}: {
  children: ReactNode
  companyIdOverride?: string | null
}) {
  const selectedCompanyId = useAppSelector(selectSelectedCompanyId)
  const companyId = companyIdOverride === undefined ? selectedCompanyId : companyIdOverride
  const [unreadCount, setUnreadCount] = useState<NotificationUnreadCount>(EMPTY_COUNT)
  const [recent, setRecent] = useState<NotificationItem[]>([])
  const [loadingRecent, setLoadingRecent] = useState(true)
  const [connected, setConnected] = useState(realtimeClient.isConnected())
  const [refreshVersion, setRefreshVersion] = useState(0)
  const channelRef = useRef<BroadcastChannel | null>(null)
  const lastHiddenAt = useRef<number | null>(null)
  const refreshSequence = useRef(0)

  const refresh = useCallback(
    async (_reason: RefreshReason = 'initial') => {
      const sequence = ++refreshSequence.current
      const [countResult, listResult] = await Promise.allSettled([
        notificationApi.unreadCount(companyId),
        notificationApi.list(companyId, { limit: 8 }),
      ])
      if (sequence !== refreshSequence.current) return

      if (countResult.status === 'fulfilled') setUnreadCount(countResult.value)
      if (listResult.status === 'fulfilled') setRecent(listResult.value.items)
      setLoadingRecent(false)
      setRefreshVersion((version) => version + 1)
    },
    [companyId],
  )

  const broadcastRefresh = useCallback(() => {
    channelRef.current?.postMessage({ type: 'REFRESH', companyId })
  }, [companyId])

  useEffect(() => {
    setLoadingRecent(true)
    setRecent([])
    setUnreadCount(EMPTY_COUNT)
    void refresh('scope')
  }, [companyId, refresh])

  useEffect(() => {
    void updateAppBadge(unreadCount.total)
  }, [unreadCount.total])

  useEffect(() => {
    if (!('BroadcastChannel' in window)) return
    const channel = new BroadcastChannel(CHANNEL_NAME)
    channelRef.current = channel
    channel.onmessage = (event) => {
      const message = event.data as { type?: string; companyId?: string | null } | null
      if (message?.type !== 'REFRESH') return
      if (message.companyId && companyId && message.companyId !== companyId) return
      void refresh('broadcast')
    }
    return () => {
      channel.close()
      if (channelRef.current === channel) channelRef.current = null
    }
  }, [companyId, refresh])

  useEffect(() => {
    const unsubscribe = realtimeClient.subscribe<NotificationChangedPayload>(
      'notification.changed.v1',
      (event) => {
        const payload = event.payload
        if (payload.companyId !== null && payload.companyId !== companyId) return
        setUnreadCount({
          account: payload.accountUnread,
          company: payload.companyUnread,
          total: payload.totalUnread,
        })
        void refresh('realtime')
      },
    )
    return unsubscribe
  }, [companyId, refresh])

  useEffect(() => {
    let wasConnected = realtimeClient.isConnected()
    const unsubscribe = realtimeClient.subscribeConnection((nextConnected) => {
      setConnected(nextConnected)
      if (!wasConnected && nextConnected) void refresh('reconnect')
      wasConnected = nextConnected
    })
    return unsubscribe
  }, [refresh])

  useEffect(() => {
    const onVisibilityChange = () => {
      if (document.visibilityState === 'hidden') {
        lastHiddenAt.current = Date.now()
        return
      }
      const hiddenAt = lastHiddenAt.current
      lastHiddenAt.current = null
      if (hiddenAt !== null && Date.now() - hiddenAt > 15_000) void refresh('focus')
    }
    document.addEventListener('visibilitychange', onVisibilityChange)
    return () => document.removeEventListener('visibilitychange', onVisibilityChange)
  }, [refresh])

  useEffect(() => {
    if (!('serviceWorker' in navigator)) return
    const onMessage = (event: MessageEvent) => {
      const data = event.data as { type?: string; companyId?: string | null } | null
      if (data?.type !== 'UNIFYDESK_NOTIFICATION_PUSH') return
      if (data.companyId && companyId && data.companyId !== companyId) return
      void refresh('push')
    }
    navigator.serviceWorker.addEventListener('message', onMessage)
    return () => navigator.serviceWorker.removeEventListener('message', onMessage)
  }, [companyId, refresh])

  useEffect(() => {
    void registerNotificationServiceWorker().catch(() => null)
    void reconcileExistingBrowserSubscription().catch(() => undefined)
    return installNotificationServiceWorkerNavigationBridge()
  }, [])

  const setRead = useCallback(
    async (notification: NotificationItem, read: boolean) => {
      const previousRecent = recent
      const previousCount = unreadCount
      const itemCompanyId = notification.scope.kind === 'COMPANY' ? notification.scope.companyId ?? companyId : null
      const wasUnread = notification.unread

      setRecent((items) =>
        items.map((item) =>
          item.id === notification.id
            ? {
                ...item,
                unread: !read,
                readAt: read ? new Date().toISOString() : null,
              }
            : item,
        ),
      )
      if (wasUnread !== !read) {
        const delta = read ? -1 : 1
        setUnreadCount((count) => ({
          account: Math.max(0, count.account + (notification.scope.kind === 'ACCOUNT' ? delta : 0)),
          company: Math.max(0, count.company + (notification.scope.kind === 'COMPANY' ? delta : 0)),
          total: Math.max(0, count.total + delta),
        }))
      }

      try {
        const result = await notificationApi.setReadState(notification.id, itemCompanyId, read)
        setUnreadCount(result.unreadCount)
        setRecent((items) =>
          items.map((item) => (item.id === notification.id ? result.notification : item)),
        )
        broadcastRefresh()
      } catch (error) {
        setRecent(previousRecent)
        setUnreadCount(previousCount)
        void refresh('broadcast')
        throw error
      }
    },
    [broadcastRefresh, companyId, recent, refresh, unreadCount],
  )

  const markAllRead = useCallback(async () => {
    const previousRecent = recent
    const previousCount = unreadCount
    const cutoff = new Date().toISOString()
    setRecent((items) =>
      items.map((item) => ({ ...item, unread: false, readAt: item.readAt ?? cutoff })),
    )
    setUnreadCount(EMPTY_COUNT)

    try {
      const result = await notificationApi.markAllRead(companyId, cutoff)
      setUnreadCount(result.unreadCount)
      broadcastRefresh()
      void refresh('broadcast')
    } catch (error) {
      setRecent(previousRecent)
      setUnreadCount(previousCount)
      throw error
    }
  }, [broadcastRefresh, companyId, recent, refresh, unreadCount])

  const openNotification = useCallback(
    async (notification: NotificationItem) => {
      if (notification.unread) await setRead(notification, true)
      const path = notification.action?.path
      return path ?? null
    },
    [setRead],
  )

  const value = useMemo(
    () => ({
      companyId,
      unreadCount,
      recent,
      loadingRecent,
      connected,
      refresh,
      refreshVersion,
      setRead,
      markAllRead,
      openNotification,
      broadcastRefresh,
    }),
    [
      broadcastRefresh,
      companyId,
      connected,
      loadingRecent,
      markAllRead,
      openNotification,
      recent,
      refresh,
      refreshVersion,
      setRead,
      unreadCount,
    ],
  )

  return <NotificationContext.Provider value={value}>{children}</NotificationContext.Provider>
}

export const useNotifications = () => {
  const value = useContext(NotificationContext)
  if (!value) throw new Error('useNotifications must be used inside NotificationProvider')
  return value
}
