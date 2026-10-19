import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import axios from 'axios'
import { useCallback, useEffect, useRef, useState } from 'react'
import { FiBell, FiCheckCircle, FiRefreshCw } from 'react-icons/fi'
import { useNavigate } from 'react-router'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { notificationApi } from '../../features/notifications/notificationApi'
import { notificationPresentation } from '../../features/notifications/notificationPresentation'
import { useNotifications } from '../../features/notifications/notificationContext'
import { isSafeAppRoute } from '../../features/notifications/notificationUtils'
import type { NotificationItem } from '../../features/notifications/types'
import { ListSkeleton } from '../../components/ui/Skeleton'

const formatWhen = (value: string) =>
  new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))

const statusCode = (error: unknown) => (axios.isAxiosError(error) ? error.response?.status : undefined)

export default function NotificationsPage() {
  const navigate = useNavigate()
  const {
    companyId,
    unreadCount,
    setRead,
    markAllRead,
    refreshVersion,
  } = useNotifications()
  const [items, setItems] = useState<NotificationItem[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useSnackbarState('', 'error')
  const [workspaceUnavailable, setWorkspaceUnavailable] = useState(false)
  const [online, setOnline] = useState(() => navigator.onLine)
  const loadSequence = useRef(0)
  const lastProviderVersion = useRef(refreshVersion)

  const loadFirst = useCallback(async () => {
    const sequence = ++loadSequence.current
    setLoading(true)
    setError('')
    setWorkspaceUnavailable(false)
    try {
      const page = await notificationApi.list(companyId, {
        limit: 20,
        unreadOnly,
      })
      if (sequence !== loadSequence.current) return
      setItems(page.items)
      setCursor(page.nextCursor)
      setHasMore(page.hasMore)
    } catch (loadError) {
      if (sequence !== loadSequence.current) return
      const status = statusCode(loadError)
      if (status === 403 || status === 404) setWorkspaceUnavailable(true)
      setError(apiErrorMessage(loadError, 'Unable to load notifications'))
    } finally {
      if (sequence === loadSequence.current) setLoading(false)
    }
  }, [companyId, unreadOnly])

  useEffect(() => {
    const onOnline = () => {
      setOnline(true)
      void loadFirst()
    }
    const onOffline = () => setOnline(false)
    window.addEventListener('online', onOnline)
    window.addEventListener('offline', onOffline)
    return () => {
      window.removeEventListener('online', onOnline)
      window.removeEventListener('offline', onOffline)
    }
  }, [loadFirst])

  useEffect(() => {
    void loadFirst()
  }, [loadFirst])

  useEffect(() => {
    if (lastProviderVersion.current === refreshVersion) return
    lastProviderVersion.current = refreshVersion
    void loadFirst()
  }, [loadFirst, refreshVersion])

  useEffect(() => {
    if (loading || items.length === 0) return
    const unseenIds = items.filter((item) => item.seenAt === null).map((item) => item.id)
    if (unseenIds.length === 0) return

    void notificationApi
      .markSeen(companyId, unseenIds)
      .then(() => {
        const seenAt = new Date().toISOString()
        setItems((current) =>
          current.map((item) =>
            unseenIds.includes(item.id) && item.seenAt === null ? { ...item, seenAt } : item,
          ),
        )
      })
      .catch(() => undefined)
  }, [companyId, items, loading])

  const loadMore = async () => {
    if (!cursor || loadingMore) return
    setLoadingMore(true)
    setError('')
    try {
      const page = await notificationApi.list(companyId, {
        limit: 20,
        cursor,
        unreadOnly,
      })
      setItems((current) => {
        const known = new Set(current.map((item) => item.id))
        return [...current, ...page.items.filter((item) => !known.has(item.id))]
      })
      setCursor(page.nextCursor)
      setHasMore(page.hasMore)
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load more notifications'))
    } finally {
      setLoadingMore(false)
    }
  }

  const changeReadState = async (notification: NotificationItem, read: boolean) => {
    const previous = items
    setError('')
    setItems((current) =>
      current.map((item) =>
        item.id === notification.id
          ? {
              ...item,
              unread: !read,
              readAt: read ? new Date().toISOString() : null,
            }
          : item,
      ),
    )
    try {
      await setRead(notification, read)
      if (unreadOnly && read) {
        setItems((current) => current.filter((item) => item.id !== notification.id))
      }
      return true
    } catch (mutationError) {
      setItems(previous)
      setError(apiErrorMessage(mutationError, 'Unable to update notification'))
      return false
    }
  }

  const visit = async (notification: NotificationItem) => {
    setError('')
    try {
      if (notification.unread && !(await changeReadState(notification, true))) return
      if (isSafeAppRoute(notification.action?.path)) {
        navigate(notification.action.path)
      }
    } catch (visitError) {
      setError(apiErrorMessage(visitError, 'Unable to open notification'))
    }
  }

  const readAll = async () => {
    const previous = items
    setError('')
    setItems((current) =>
      current.map((item) => ({
        ...item,
        unread: false,
        readAt: item.readAt ?? new Date().toISOString(),
      })),
    )
    try {
      await markAllRead()
      if (unreadOnly) setItems([])
    } catch (mutationError) {
      setItems(previous)
      setError(apiErrorMessage(mutationError, 'Unable to mark notifications read'))
    }
  }

  return (
    <section className="notification-page px-4 py-7 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-5xl">
        <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="eyebrow">Activity</p>
            <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">Notifications</h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
              Account activity and updates for your current workspace. {unreadCount.total} unread.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              className={`secondary-button ${unreadOnly ? 'notification-filter-active' : ''}`}
              aria-pressed={unreadOnly}
              onClick={() => setUnreadOnly((current) => !current)}
            >
              {unreadOnly ? 'Showing unread' : 'Show unread only'}
            </button>
            <button
              type="button"
              className="secondary-button"
              disabled={unreadCount.total === 0}
              onClick={() => void readAll()}
            >
              <FiCheckCircle aria-hidden="true" className="mr-2" />
              Mark all read
            </button>
          </div>
        </header>

        {!online && (
          <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-800" role="status">
            You are offline. Showing the last notification state available in this tab. We will refresh when your connection returns.
          </div>
        )}



        {workspaceUnavailable ? (
          <div className="panel mt-6 p-8 text-center">
            <FiBell aria-hidden="true" className="mx-auto text-3xl text-slate-300" />
            <h2 className="mt-4 text-lg font-bold text-slate-900">Workspace notifications are no longer available</h2>
            <p className="mx-auto mt-2 max-w-lg text-sm leading-6 text-slate-500">
              Your workspace access may have changed. Choose a workspace you can access and try again.
            </p>
            <button type="button" className="primary-button mt-5" onClick={() => navigate('/select-workspace')}>
              Choose workspace
            </button>
          </div>
        ) : (
          <div className="panel mt-6 overflow-hidden">
            {loading && <div className="p-4"><ListSkeleton rows={7} /></div>}

            {!loading && items.length === 0 && (
              <div className="p-10 text-center">
                <FiBell aria-hidden="true" className="mx-auto text-3xl text-slate-300" />
                <h2 className="mt-4 text-base font-bold text-slate-900">
                  {unreadOnly ? 'No unread notifications' : 'No notifications yet'}
                </h2>
                <p className="mt-1 text-sm text-slate-500">
                  {unreadOnly ? 'You are caught up.' : 'New activity will appear here.'}
                </p>
              </div>
            )}

            {!loading && items.length > 0 && (
              <div className="divide-y divide-slate-100">
                {items.map((notification) => {
                  const presentation = notificationPresentation(notification)
                  return (
                    <article
                      key={notification.id}
                      className={`notification-page-row ${notification.unread ? 'is-unread' : ''}`}
                    >
                      <span
                        className={`notification-unread-marker mt-1.5 ${notification.unread ? 'is-visible' : ''}`}
                        aria-hidden="true"
                      />
                      <div className="min-w-0 flex-1">
                        <button
                          type="button"
                          className="block w-full text-left"
                          onClick={() => void visit(notification)}
                        >
                          <span className="flex flex-wrap items-center gap-2">
                            <span className="font-bold text-slate-950">{presentation.headline}</span>
                            {notification.unread && (
                              <span className="notification-unread-label">Unread</span>
                            )}
                          </span>
                          {presentation.subject && (
                            <span className="mt-1 block text-sm font-semibold text-slate-700">
                              {presentation.subject}
                            </span>
                          )}
                          {presentation.preview && (
                            <span className="mt-1 block text-sm leading-6 text-slate-600">
                              {presentation.preview}
                            </span>
                          )}
                          <span className="mt-2 block text-xs font-medium text-slate-400">
                            {formatWhen(notification.lastEventAt)}
                            {presentation.source ? ` · ${presentation.source}` : ''}
                          </span>
                        </button>
                      </div>
                      <button
                        type="button"
                        className="notification-text-button shrink-0 self-start"
                        onClick={() => void changeReadState(notification, !notification.unread)}
                      >
                        {notification.unread ? 'Mark read' : 'Mark unread'}
                      </button>
                    </article>
                  )
                })}
              </div>
            )}

            {!loading && hasMore && (
              <div className="border-t border-slate-100 p-4 text-center">
                <button
                  type="button"
                  className="secondary-button"
                  disabled={loadingMore}
                  onClick={() => void loadMore()}
                >
                  {loadingMore ? 'Loading…' : 'Load more'}
                </button>
              </div>
            )}

            {!loading && error && items.length === 0 && !workspaceUnavailable && (
              <div className="border-t border-slate-100 p-4 text-center">
                <button type="button" className="secondary-button" onClick={() => void loadFirst()}>
                  <FiRefreshCw aria-hidden="true" className="mr-2" />
                  Retry
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </section>
  )
}
