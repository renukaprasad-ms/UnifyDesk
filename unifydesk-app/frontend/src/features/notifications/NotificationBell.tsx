import { useEffect, useRef, useState } from 'react'
import { FiBell, FiCheckCircle, FiMail, FiUsers } from 'react-icons/fi'
import { useNavigate } from 'react-router'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { notificationApi } from './notificationApi'
import { useNotifications } from './NotificationProvider'
import { notificationPresentation } from './notificationPresentation'
import { formatUnreadBadge, isSafeAppRoute } from './notificationUtils'
import type { NotificationItem } from './types'

const relativeTime = (value: string) => {
  const elapsed = Date.now() - new Date(value).getTime()
  const minutes = Math.max(0, Math.floor(elapsed / 60_000))
  if (minutes < 1) return 'Now'
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  return `${Math.floor(hours / 24)}d ago`
}

const notificationIcon = (notification: NotificationItem) => {
  if (notification.type === 'INBOX_ACTIVITY') return <FiMail aria-hidden="true" />
  if (notification.type.startsWith('COMPANY_INVITATION')) return <FiUsers aria-hidden="true" />
  return <FiBell aria-hidden="true" />
}

export default function NotificationBell({ notificationPath = '/notifications' }: { notificationPath?: string }) {
  const navigate = useNavigate()
  const {
    companyId,
    unreadCount,
    recent,
    loadingRecent,
    markAllRead,
    openNotification,
    refresh,
  } = useNotifications()
  const [open, setOpen] = useState(false)
  const [error, setError] = useState('')
  const rootRef = useRef<HTMLDivElement | null>(null)
  const triggerRef = useRef<HTMLButtonElement | null>(null)
  const panelRef = useRef<HTMLDivElement | null>(null)
  const badge = formatUnreadBadge(unreadCount.total)

  useEffect(() => {
    if (!open) return
    const onPointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      setOpen(false)
      triggerRef.current?.focus()
    }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    window.setTimeout(() => panelRef.current?.focus(), 0)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  useEffect(() => {
    if (!open || loadingRecent) return
    const renderedIds = recent.filter((item) => item.seenAt === null).map((item) => item.id)
    if (renderedIds.length === 0) return
    void notificationApi.markSeen(companyId, renderedIds).catch(() => undefined)
  }, [companyId, loadingRecent, open, recent])

  const visit = async (notification: NotificationItem) => {
    setError('')
    try {
      const path = await openNotification(notification)
      setOpen(false)
      if (isSafeAppRoute(path)) navigate(path)
    } catch (visitError) {
      setError(apiErrorMessage(visitError, 'Unable to open this notification'))
    }
  }

  return (
    <div ref={rootRef} className="notification-bell-root">
      <button
        ref={triggerRef}
        type="button"
        className="notification-bell-button"
        aria-label={
          unreadCount.total > 0
            ? `Notifications, ${unreadCount.total} unread`
            : 'Notifications, no unread notifications'
        }
        aria-expanded={open}
        aria-haspopup="dialog"
        onClick={() => {
          if (window.matchMedia('(max-width: 639px)').matches) {
            navigate(notificationPath)
            return
          }
          setOpen((current) => {
            const next = !current
            if (next) void refresh('realtime')
            return next
          })
        }}
      >
        <FiBell aria-hidden="true" />
        {badge && <span className="notification-badge notification-bell-badge">{badge}</span>}
      </button>

      {open && (
        <div
          ref={panelRef}
          className="notification-popover"
          role="dialog"
          aria-label="Recent notifications"
          tabIndex={-1}
        >
          <div className="notification-popover-header">
            <div>
              <p className="text-sm font-bold text-slate-950">Notifications</p>
              <p className="mt-0.5 text-xs text-slate-500">
                {unreadCount.total > 0 ? `${unreadCount.total} unread` : 'You are all caught up'}
              </p>
            </div>
            {unreadCount.total > 0 && (
              <button
                type="button"
                className="notification-text-button"
                onClick={() => void markAllRead().catch(() => undefined)}
              >
                <FiCheckCircle aria-hidden="true" />
                Mark all read
              </button>
            )}
          </div>

          {error && <p className="notification-inline-error">{error}</p>}
          <div className="notification-popover-list" aria-live="polite">
            {loadingRecent && <p className="notification-state">Loading notifications…</p>}
            {!loadingRecent && recent.length === 0 && (
              <p className="notification-state">No notifications yet.</p>
            )}
            {!loadingRecent &&
              recent.map((notification) => {
                const presentation = notificationPresentation(notification)
                return (
                  <button
                    key={notification.id}
                    type="button"
                    className={`notification-row notification-row-button ${notification.unread ? 'is-unread' : ''}`}
                    onClick={() => void visit(notification)}
                  >
                    <span className="notification-row-icon">
                      {notificationIcon(notification)}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="notification-row-topline">
                        <span className="notification-row-headline">{presentation.headline}</span>
                        <span className="notification-row-status">
                          <span className="notification-row-time">
                            {relativeTime(notification.lastEventAt)}
                          </span>
                          <span
                            className={`notification-unread-marker ${notification.unread ? 'is-visible' : ''}`}
                            aria-hidden="true"
                          />
                        </span>
                      </span>
                      {presentation.subject && (
                        <span className="notification-row-subject">{presentation.subject}</span>
                      )}
                      {presentation.preview && (
                        <span className="notification-row-preview">{presentation.preview}</span>
                      )}
                      {presentation.source && (
                        <span className="notification-row-source">{presentation.source}</span>
                      )}
                    </span>
                    {notification.unread && <span className="sr-only">Unread</span>}
                  </button>
                )
              })}
          </div>
          <button
            type="button"
            className="notification-view-all"
            onClick={() => {
              setOpen(false)
              navigate(notificationPath)
            }}
          >
            View all notifications
          </button>
        </div>
      )}
    </div>
  )
}
