import apiClient from '../../lib/api/apiClient'
import type { AsyncJob } from '../asyncJob/types'
import type { ApiResponse } from '../../lib/api/types'
import type {
  ChannelConnection,
  DisconnectDataPolicy,
  EmailSyncQueued,
  EmailSyncRequest,
  ZohoMailAuthorization,
  ZohoMailSendRequest,
  ZohoMailSendResponse,
} from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (!response.data) throw new Error(response.error_message ?? 'Empty API response')
  return response.data
}

export const zohoMailApi = {
  async authorize(companyId: string, returnTo?: string) {
    const response = await apiClient.get<ApiResponse<ZohoMailAuthorization>>(
      `/api/v1/companies/${companyId}/channels/email/zoho/authorize`,
      { params: returnTo ? { returnTo } : undefined },
    )
    return unwrap(response.data)
  },

  async activeSync(companyId: string, connectionId: string) {
    const response = await apiClient.get<ApiResponse<AsyncJob>>(
      `/api/v1/companies/${companyId}/channels/email/zoho/${connectionId}/sync/active`,
    )
    return response.data.data ?? null
  },

  async sync(companyId: string, connectionId: string, request: EmailSyncRequest) {
    const response = await apiClient.post<ApiResponse<EmailSyncQueued>>(
      `/api/v1/companies/${companyId}/channels/email/zoho/${connectionId}/sync`,
      request,
    )
    return unwrap(response.data)
  },

  async send(companyId: string, connectionId: string, request: ZohoMailSendRequest) {
    const response = await apiClient.post<ApiResponse<ZohoMailSendResponse>>(
      `/api/v1/companies/${companyId}/channels/email/zoho/${connectionId}/send`,
      request,
    )
    return unwrap(response.data)
  },

  async webhookStatus(companyId: string, connectionId: string) {
    const response = await apiClient.get<ApiResponse<{
      configured: boolean
      status: 'NOT_CONFIGURED' | 'AWAITING_HANDSHAKE' | 'ACTIVE'
      lastReceivedAt?: string | null
      lastProcessedAt?: string | null
    }>>(`/api/v1/companies/${companyId}/channels/email/zoho/${connectionId}/webhook`)
    return unwrap(response.data)
  },

  async setupWebhook(companyId: string, connectionId: string) {
    const response = await apiClient.post<ApiResponse<{
      webhookUrl: string
      status: 'AWAITING_HANDSHAKE'
    }>>(`/api/v1/companies/${companyId}/channels/email/zoho/${connectionId}/webhook/setup`)
    return unwrap(response.data)
  },

  async disableWebhook(companyId: string, connectionId: string) {
    await apiClient.delete(
      `/api/v1/companies/${companyId}/channels/email/zoho/${connectionId}/webhook`,
    )
  },

  async disconnect(
    companyId: string,
    connectionId: string,
    dataPolicy: DisconnectDataPolicy = 'KEEP_7_DAYS',
  ) {
    const response = await apiClient.post<ApiResponse<ChannelConnection>>(
      `/api/v1/companies/${companyId}/channels/email/zoho/${connectionId}/disconnect`,
      { dataPolicy },
    )
    return unwrap(response.data)
  },
}
