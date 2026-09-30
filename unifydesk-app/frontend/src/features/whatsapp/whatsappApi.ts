import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type {
  WhatsAppComposerState,
  WhatsAppSendRequest,
  WhatsAppSendResult,
  WhatsAppTemplate,
} from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (!response.data) throw new Error(response.error_message ?? 'Empty API response')
  return response.data
}

const controlBase = (companyId: string) =>
  `/api/v1/companies/${companyId}/channels/whatsapp/meta`

export const whatsappApi = {
  async composer(companyId: string, conversationId: string) {
    const response = await apiClient.get<ApiResponse<WhatsAppComposerState>>(
      `/api/v1/companies/${companyId}/inbox/conversations/${conversationId}/whatsapp/composer`,
    )
    return unwrap(response.data)
  },

  async templates(companyId: string, connectionId: string) {
    const response = await apiClient.get<ApiResponse<WhatsAppTemplate[]>>(
      `${controlBase(companyId)}/templates`,
      { params: { connectionId } },
    )
    return unwrap(response.data)
  },

  async syncTemplates(companyId: string, connectionId: string) {
    const response = await apiClient.post<
      ApiResponse<{ synced: number; approved: number; rejected: number; syncedAt: string }>
    >(`${controlBase(companyId)}/templates/sync`, { connectionId })
    return unwrap(response.data)
  },

  async send(companyId: string, conversationId: string, request: WhatsAppSendRequest) {
    const response = await apiClient.post<ApiResponse<WhatsAppSendResult>>(
      `/api/v1/companies/${companyId}/inbox/conversations/${conversationId}/whatsapp/messages`,
      request,
    )
    return unwrap(response.data)
  },
}
