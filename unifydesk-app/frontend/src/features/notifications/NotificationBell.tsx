import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useEffect, useRef, useState } from 'react'
import { FiBell, FiCheckCircle, FiMail, FiUsers } from 'react-icons/fi'
import { useNavigate } from 'react-router'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { notificationApi } from './notificationApi'
import { useNotifications } from './notificationContext'
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
  const [, setError] = useSnackbarState('', 'error')
  const rootRef = useRef<HTMLDivElement | null>(null)
  const triggerRef = useRef<HTMLButtonElement | null>(null)
  const panelRef = useRef<HTMLDivElement | null>(null)
  const closeTimerRef = useRef<number | null>(null)
  const focusPanelOnOpenRef = useRef(false)
  const pinnedOpenRef = useRef(false)
  const badge = formatUnreadBadge(unreadCount.total)

  const supportsHover = () =>
    window.matchMedia('(hover: hover) and (pointer: fine)').matches

  const clearCloseTimer = () => {
    if (closeTimerRef.current === null) return
    window.clearTimeout(closeTimerRef.current)
    closeTimerRef.current = null
  }

  const openPopover = (focusPanel = false, pinned = false) => {
    clearCloseTimer()
    focusPanelOnOpenRef.current = focusPanel
    pinnedOpenRef.current = pinned
    setOpen((current) => {
      if (!current) void refresh('realtime')
      return true
    })
  }

  const closePopover = () => {
    clearCloseTimer()
    pinnedOpenRef.current = false
    focusPanelOnOpenRef.current = false
    setOpen(false)
  }

  const scheduleHoverClose = () => {
    if (!supportsHover() || pinnedOpenRef.current) return
    clearCloseTimer()
    closeTimerRef.current = window.setTimeout(() => {
      closeTimerRef.current = null
      if (!pinnedOpenRef.current) setOpen(false)
    }, 180)
  }

  useEffect(() => {
    if (!open) return
    const onPointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) closePopover()
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      closePopover()
      triggerRef.current?.focus()
    }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    if (focusPanelOnOpenRef.current) {
      window.setTimeout(() => panelRef.current?.focus(), 0)
      focusPanelOnOpenRef.current = false
    }
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  useEffect(
    () => () => {
      clearCloseTimer()
    },
    [],
  )

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
      closePopover()
      if (isSafeAppRoute(path)) navigate(path)
    } catch (visitError) {
      setError(apiErrorMessage(visitError, 'Unable to open this notification'))
    }
  }

  return (
    <div
      ref={rootRef}
      className="notification-bell-root"
      onMouseEnter={() => {
        if (supportsHover()) openPopover(false, false)
      }}
      onMouseLeave={scheduleHoverClose}
    >
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

          if (open && !pinnedOpenRef.current) {
            // Hover already opened it. A click pins the popover instead of immediately
            // closing it, which avoids a surprising hover-then-click interaction.
            pinnedOpenRef.current = true
            clearCloseTimer()
            return
          }

          if (open) {
            closePopover()
            return
          }

          openPopover(true, true)
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
              closePopover()
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
