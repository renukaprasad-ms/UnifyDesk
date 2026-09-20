import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type {
  ChannelConnection,
  MicrosoftAuthorization,
  MicrosoftSendRequest,
  MicrosoftSendResponse,
} from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (!response.data) throw new Error(response.error_message ?? 'Empty API response')
  return response.data
}

export const microsoftApi = {
  async authorize(companyId: string, returnTo?: string) {
    const response = await apiClient.get<ApiResponse<MicrosoftAuthorization>>(
      `/api/v1/companies/${companyId}/channels/email/microsoft/authorize`,
      { params: returnTo ? { returnTo } : undefined },
    )
    return unwrap(response.data)
  },

  async sync(companyId: string, connectionId: string) {
    await apiClient.post(
      `/api/v1/companies/${companyId}/channels/email/microsoft/${connectionId}/sync`,
    )
  },

  async send(companyId: string, connectionId: string, request: MicrosoftSendRequest) {
    const response = await apiClient.post<ApiResponse<MicrosoftSendResponse>>(
      `/api/v1/companies/${companyId}/channels/email/microsoft/${connectionId}/send`,
      request,
    )
    return unwrap(response.data)
  },

  async disconnect(companyId: string, connectionId: string) {
    const response = await apiClient.post<ApiResponse<ChannelConnection>>(
      `/api/v1/companies/${companyId}/channels/email/microsoft/${connectionId}/disconnect`,
    )
    return unwrap(response.data)
  },
}
