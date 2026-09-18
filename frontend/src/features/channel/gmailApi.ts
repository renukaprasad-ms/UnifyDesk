import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type { ChannelConnection, GmailAuthorization } from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (!response.data) throw new Error(response.error_message ?? 'Empty API response')
  return response.data
}

export const gmailApi = {
  async authorize(companyId: string) {
    const response = await apiClient.get<ApiResponse<GmailAuthorization>>(
      `/api/v1/companies/${companyId}/channels/email/gmail/authorize`,
    )
    return unwrap(response.data)
  },

  async disconnect(companyId: string, connectionId: string) {
    const response = await apiClient.post<ApiResponse<ChannelConnection>>(
      `/api/v1/companies/${companyId}/channels/email/gmail/${connectionId}/disconnect`,
    )
    return unwrap(response.data)
  },
}
