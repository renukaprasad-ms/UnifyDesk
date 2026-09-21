import apiClient from '../../lib/api/apiClient'
import type { AsyncJob } from '../asyncJob/types'
import type { ApiResponse } from '../../lib/api/types'
import type {
  ChannelConnection,
  DisconnectDataPolicy,
  GmailAuthorization,
  GmailSendRequest,
  GmailSendResponse,
  GmailSyncQueued,
  GmailSyncRequest,
} from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (!response.data) throw new Error(response.error_message ?? 'Empty API response')
  return response.data
}

export const gmailApi = {
  async authorize(companyId: string, returnTo?: string) {
    const response = await apiClient.get<ApiResponse<GmailAuthorization>>(
      `/api/v1/companies/${companyId}/channels/email/gmail/authorize`,
      { params: returnTo ? { returnTo } : undefined },
    )
    return unwrap(response.data)
  },

  async activeSync(companyId: string, connectionId: string) {
    const response = await apiClient.get<ApiResponse<AsyncJob>>(
      `/api/v1/companies/${companyId}/channels/email/gmail/${connectionId}/sync/active`,
    )
    return response.data.data ?? null
  },

  async sync(companyId: string, connectionId: string, request: GmailSyncRequest) {
    const response = await apiClient.post<ApiResponse<GmailSyncQueued>>(
      `/api/v1/companies/${companyId}/channels/email/gmail/${connectionId}/sync`,
      request,
    )
    return unwrap(response.data)
  },

  async send(companyId: string, connectionId: string, request: GmailSendRequest) {
    const response = await apiClient.post<ApiResponse<GmailSendResponse>>(
      `/api/v1/companies/${companyId}/channels/email/gmail/${connectionId}/send`,
      request,
    )
    return unwrap(response.data)
  },

  async disconnect(
    companyId: string,
    connectionId: string,
    dataPolicy: DisconnectDataPolicy = 'KEEP_7_DAYS',
  ) {
    const response = await apiClient.post<ApiResponse<ChannelConnection>>(
      `/api/v1/companies/${companyId}/channels/email/gmail/${connectionId}/disconnect`,
      { dataPolicy },
    )
    return unwrap(response.data)
  },
}
