import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type {
  NotificationItem,
  NotificationPage,
  NotificationPreferences,
  NotificationUnreadCount,
  PushCapability,
  PushSubscriptionSummary,
  RegisterPushSubscription,
  UpdateNotificationPreferences,
} from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (response.data === undefined) {
    throw new Error(response.error_message ?? 'Empty API response')
  }
  return response.data
}

const scopeParams = (companyId: string | null) => (companyId ? { companyId } : undefined)

export const notificationApi = {
  async list(
    companyId: string | null,
    options: { limit?: number; cursor?: string | null; unreadOnly?: boolean } = {},
  ) {
    const response = await apiClient.get<ApiResponse<NotificationPage>>('/api/v1/notifications', {
      params: {
        ...(scopeParams(companyId) ?? {}),
        limit: options.limit,
        cursor: options.cursor ?? undefined,
        unreadOnly: options.unreadOnly,
      },
    })
    return unwrap(response.data)
  },

  async get(notificationId: string, companyId: string | null) {
    const response = await apiClient.get<ApiResponse<NotificationItem>>(
      `/api/v1/notifications/${notificationId}`,
      { params: scopeParams(companyId) },
    )
    return unwrap(response.data)
  },

  async unreadCount(companyId: string | null) {
    const response = await apiClient.get<ApiResponse<NotificationUnreadCount>>(
      '/api/v1/notifications/unread-count',
      { params: scopeParams(companyId) },
    )
    return unwrap(response.data)
  },

  async markSeen(companyId: string | null, notificationIds: string[]) {
    const response = await apiClient.post<ApiResponse<{ updated: number }>>(
      '/api/v1/notifications/seen',
      { companyId, notificationIds: [...new Set(notificationIds)] },
    )
    return unwrap(response.data)
  },

  async setReadState(notificationId: string, companyId: string | null, read: boolean) {
    const response = await apiClient.patch<
      ApiResponse<{ notification: NotificationItem; unreadCount: NotificationUnreadCount }>
    >(`/api/v1/notifications/${notificationId}/read-state`, { companyId, read })
    return unwrap(response.data)
  },

  async markAllRead(companyId: string | null, before: string | null = null) {
    const response = await apiClient.post<
      ApiResponse<{
        updated: number
        cutoffAt: string
        unreadCount: NotificationUnreadCount
      }>
    >('/api/v1/notifications/read-all', { companyId, before })
    return unwrap(response.data)
  },

  async preferences(companyId: string | null) {
    const response = await apiClient.get<ApiResponse<NotificationPreferences>>(
      '/api/v1/notifications/preferences',
      { params: scopeParams(companyId) },
    )
    return unwrap(response.data)
  },

  async updatePreferences(companyId: string | null, payload: UpdateNotificationPreferences) {
    const response = await apiClient.put<ApiResponse<NotificationPreferences>>(
      '/api/v1/notifications/preferences',
      payload,
      { params: scopeParams(companyId) },
    )
    return unwrap(response.data)
  },

  async pushCapability() {
    const response = await apiClient.get<ApiResponse<PushCapability>>(
      '/api/v1/notifications/push/capability',
    )
    return unwrap(response.data)
  },

  async registerPushSubscription(payload: RegisterPushSubscription) {
    const response = await apiClient.post<ApiResponse<PushSubscriptionSummary>>(
      '/api/v1/notifications/push/subscriptions',
      payload,
    )
    return unwrap(response.data)
  },

  async pushSubscriptions() {
    const response = await apiClient.get<ApiResponse<PushSubscriptionSummary[]>>(
      '/api/v1/notifications/push/subscriptions',
    )
    return unwrap(response.data)
  },

  async revokePushSubscription(subscriptionId: string) {
    const response = await apiClient.delete<ApiResponse<PushSubscriptionSummary>>(
      `/api/v1/notifications/push/subscriptions/${subscriptionId}`,
    )
    return unwrap(response.data)
  },
}
