import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type {
  DisconnectDataPolicy,
  MetaWhatsAppConnection,
  MetaWhatsAppSignupConfig,
  MetaWhatsAppSignupResult,
} from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (!response.data) throw new Error(response.error_message ?? 'Empty API response')
  return response.data
}

const basePath = (companyId: string) => `/api/v1/companies/${companyId}/channels/whatsapp/meta`

export const metaWhatsAppApi = {
  async signupSession(companyId: string) {
    const response = await apiClient.post<ApiResponse<MetaWhatsAppSignupConfig>>(
      `${basePath(companyId)}/signup-session`,
    )
    return unwrap(response.data)
  },

  async reauthorizeSession(companyId: string) {
    const response = await apiClient.post<ApiResponse<MetaWhatsAppSignupConfig>>(
      `${basePath(companyId)}/reauthorize-session`,
    )
    return unwrap(response.data)
  },

  async complete(companyId: string, state: string, result: MetaWhatsAppSignupResult) {
    const response = await apiClient.post<ApiResponse<MetaWhatsAppConnection>>(
      `${basePath(companyId)}/complete`,
      {
        state,
        code: result.code,
        wabaId: result.wabaId,
        phoneNumberId: result.phoneNumberId,
        whatsappAccountId: result.whatsappAccountId,
        messagingAccountId: result.messagingAccountId,
      },
    )
    return unwrap(response.data)
  },

  async connection(companyId: string) {
    const response = await apiClient.get<ApiResponse<MetaWhatsAppConnection>>(
      `${basePath(companyId)}/connection`,
    )
    return unwrap(response.data)
  },

  async reconcile(companyId: string) {
    const response = await apiClient.post<ApiResponse<MetaWhatsAppConnection>>(
      `${basePath(companyId)}/reconcile`,
    )
    return unwrap(response.data)
  },

  async disconnect(companyId: string, dataPolicy: DisconnectDataPolicy) {
    const response = await apiClient.post<ApiResponse<MetaWhatsAppConnection | null>>(
      `${basePath(companyId)}/disconnect`,
      { dataPolicy },
    )
    return response.data.data ?? null
  },
}
