import apiClient from '../../lib/api/apiClient'
import type { AsyncJob } from '../asyncJob/types'
import type { ApiResponse } from '../../lib/api/types'
import type {
  InboxConversation,
  InboxConversationDetail,
  InboxMessage,
  InboxPage,
  InboxReadStateResponse,
  InboxRealtimeSnapshot,
} from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (!response.data) throw new Error(response.error_message ?? 'Empty API response')
  return response.data
}

export const inboxApi = {
  async conversations(
    companyId: string,
    page = 0,
    size = 30,
    connectionId?: string | null,
    folder: 'INBOX' | 'SENT' = 'INBOX',
  ) {
    const response = await apiClient.get<ApiResponse<InboxPage<InboxConversation>>>(
      `/api/v1/companies/${companyId}/inbox/conversations`,
      { params: { page, size, connectionId: connectionId || undefined, folder } },
    )
    return unwrap(response.data)
  },

  async search(
    companyId: string,
    connectionId: string,
    query: string,
    folder: 'INBOX' | 'SENT' = 'INBOX',
    page = 0,
    size = 30,
  ) {
    const response = await apiClient.get<ApiResponse<InboxPage<InboxConversation>>>(
      `/api/v1/companies/${companyId}/inbox/search`,
      { params: { connectionId, q: query, folder, page, size } },
    )
    return unwrap(response.data)
  },

  async conversation(companyId: string, conversationId: string) {
    const response = await apiClient.get<ApiResponse<InboxConversation>>(
      `/api/v1/companies/${companyId}/inbox/conversations/${conversationId}`,
    )
    return unwrap(response.data)
  },

  async conversationDetail(companyId: string, conversationId: string) {
    const response = await apiClient.get<ApiResponse<InboxConversationDetail>>(
      `/api/v1/companies/${companyId}/inbox/conversations/${conversationId}/detail`,
    )
    return unwrap(response.data)
  },

  async realtimeSnapshot(
    companyId: string,
    request: {
      connectionId: string
      folder: 'INBOX' | 'SENT'
      detailConversationId?: string | null
      conversationIds: string[]
      messageIds: string[]
    },
  ) {
    const response = await apiClient.post<ApiResponse<InboxRealtimeSnapshot>>(
      `/api/v1/companies/${companyId}/inbox/realtime-snapshot`,
      request,
    )
    return unwrap(response.data)
  },

  async messages(companyId: string, conversationId: string) {
    const response = await apiClient.get<ApiResponse<InboxMessage[]>>(
      `/api/v1/companies/${companyId}/inbox/conversations/${conversationId}/messages`,
    )
    return unwrap(response.data)
  },

  attachmentContentUrl(companyId: string, attachmentId: string) {
    const base = String(apiClient.defaults.baseURL ?? '').replace(/\/$/, '')
    return `${base}/api/v1/companies/${encodeURIComponent(companyId)}/inbox/attachments/${encodeURIComponent(attachmentId)}/content`
  },

  async setMessageReadState(companyId: string, messageId: string, read: boolean) {
    const response = await apiClient.put<ApiResponse<InboxReadStateResponse>>(
      `/api/v1/companies/${companyId}/inbox/messages/${messageId}/read-state`,
      { read },
    )
    return unwrap(response.data)
  },

  async setConversationReadState(companyId: string, conversationId: string, read: boolean) {
    const response = await apiClient.put<ApiResponse<InboxReadStateResponse>>(
      `/api/v1/companies/${companyId}/inbox/conversations/${conversationId}/read-state`,
      { read },
    )
    return unwrap(response.data)
  },

  async markAllRead(companyId: string, connectionId: string, folder: 'INBOX' | 'SENT' = 'INBOX') {
    const response = await apiClient.post<ApiResponse<AsyncJob>>(
      `/api/v1/companies/${companyId}/inbox/conversations/mark-all-read`,
      null,
      { params: { connectionId, folder } },
    )
    return unwrap(response.data)
  },
}
