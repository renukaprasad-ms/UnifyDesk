import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type {
  MetaWhatsAppConnection,
  MetaWhatsAppSignupConfig,
  MetaWhatsAppSignupResult,
} from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (!response.data) throw new Error(response.error_message ?? 'Empty API response')
  return response.data
}

export const metaWhatsAppApi = {
  async signupConfig(companyId: string) {
    const response = await apiClient.get<ApiResponse<MetaWhatsAppSignupConfig>>(
      `/api/v1/companies/${companyId}/channels/whatsapp/meta/signup-config`,
    )
    return unwrap(response.data)
  },

  async complete(companyId: string, state: string, result: MetaWhatsAppSignupResult) {
    const response = await apiClient.post<ApiResponse<MetaWhatsAppConnection>>(
      `/api/v1/companies/${companyId}/channels/whatsapp/meta/complete`,
      {
        state,
        code: result.code,
        wabaId: result.wabaId,
        phoneNumberId: result.phoneNumberId,
      },
    )
    return unwrap(response.data)
  },
}
