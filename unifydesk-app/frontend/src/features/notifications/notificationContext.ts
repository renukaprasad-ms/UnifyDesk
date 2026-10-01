import { createContext, useContext } from 'react'
import type { NotificationItem, NotificationUnreadCount } from './types'

export type NotificationRefreshReason =
  | 'initial'
  | 'scope'
  | 'realtime'
  | 'reconnect'
  | 'focus'
  | 'broadcast'
  | 'push'

export type NotificationContextValue = {
  companyId: string | null
  unreadCount: NotificationUnreadCount
  recent: NotificationItem[]
  loadingRecent: boolean
  connected: boolean
  refresh: (reason?: NotificationRefreshReason) => Promise<void>
  setRead: (notification: NotificationItem, read: boolean) => Promise<void>
  markAllRead: () => Promise<void>
  openNotification: (notification: NotificationItem) => Promise<string | null>
  broadcastRefresh: () => void
  refreshVersion: number
}

export const NotificationContext =
  createContext<NotificationContextValue | null>(null)

export const useNotifications = () => {
  const value = useContext(NotificationContext)
  if (!value) throw new Error('useNotifications must be used inside NotificationProvider')
  return value
}
