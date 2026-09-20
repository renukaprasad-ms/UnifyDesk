import apiClient from '../../lib/api/apiClient'
import type { AsyncJob } from '../asyncJob/types'
import type { ApiResponse } from '../../lib/api/types'
import type {
  EmailSyncQueued,
  EmailSyncRequest,
  ImapSmtpConnectRequest,
  ImapSmtpConnection,
  ImapSmtpSendRequest,
  ImapSmtpSendResponse,
} from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (!response.data) throw new Error(response.error_message ?? 'Empty API response')
  return response.data
}

export const imapSmtpApi = {
  async connect(companyId: string, request: ImapSmtpConnectRequest) {
    const response = await apiClient.post<ApiResponse<ImapSmtpConnection>>(
      `/api/v1/companies/${companyId}/channels/email/imap-smtp/connect`,
      request,
    )
    return unwrap(response.data)
  },

  async activeSync(companyId: string, connectionId: string) {
    const response = await apiClient.get<ApiResponse<AsyncJob>>(
      `/api/v1/companies/${companyId}/channels/email/imap-smtp/${connectionId}/sync/active`,
    )
    return response.data.data ?? null
  },

  async sync(companyId: string, connectionId: string, request: EmailSyncRequest) {
    const response = await apiClient.post<ApiResponse<EmailSyncQueued>>(
      `/api/v1/companies/${companyId}/channels/email/imap-smtp/${connectionId}/sync`,
      request,
    )
    return unwrap(response.data)
  },

  async send(companyId: string, connectionId: string, request: ImapSmtpSendRequest) {
    const response = await apiClient.post<ApiResponse<ImapSmtpSendResponse>>(
      `/api/v1/companies/${companyId}/channels/email/imap-smtp/${connectionId}/send`,
      request,
    )
    return unwrap(response.data)
  },
}
