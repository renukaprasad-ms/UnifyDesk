import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type { InboxConversation, InboxMessage, InboxPage } from './types'

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

  async conversation(companyId: string, conversationId: string) {
    const response = await apiClient.get<ApiResponse<InboxConversation>>(
      `/api/v1/companies/${companyId}/inbox/conversations/${conversationId}`,
    )
    return unwrap(response.data)
  },

  async messages(companyId: string, conversationId: string) {
    const response = await apiClient.get<ApiResponse<InboxMessage[]>>(
      `/api/v1/companies/${companyId}/inbox/conversations/${conversationId}/messages`,
    )
    return unwrap(response.data)
  },
}
