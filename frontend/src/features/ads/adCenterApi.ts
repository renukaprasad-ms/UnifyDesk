import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type {
  AdAccount,
  AdProviderConnection,
  AdProviderInfo,
  MetaAdsAuthorization,
} from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (!response.data) throw new Error(response.error_message ?? 'Empty API response')
  return response.data
}

export const adCenterApi = {
  async providers(companyId: string) {
    const response = await apiClient.get<ApiResponse<AdProviderInfo[]>>(
      `/api/v1/companies/${companyId}/ads/providers`,
    )
    return unwrap(response.data)
  },

  async connections(companyId: string) {
    const response = await apiClient.get<ApiResponse<AdProviderConnection[]>>(
      `/api/v1/companies/${companyId}/ads/connections`,
    )
    return unwrap(response.data)
  },

  async accounts(companyId: string) {
    const response = await apiClient.get<ApiResponse<AdAccount[]>>(
      `/api/v1/companies/${companyId}/ads/accounts`,
    )
    return unwrap(response.data)
  },

  async authorizeMeta(companyId: string, returnTo = '/ad-center') {
    const response = await apiClient.get<ApiResponse<MetaAdsAuthorization>>(
      `/api/v1/companies/${companyId}/ads/meta/authorize`,
      { params: { returnTo } },
    )
    return unwrap(response.data)
  },

  async disconnectConnection(companyId: string, connectionId: string) {
    const response = await apiClient.post<ApiResponse<AdProviderConnection>>(
      `/api/v1/companies/${companyId}/ads/connections/${connectionId}/disconnect`,
    )
    return unwrap(response.data)
  },

  async updateAccount(
    companyId: string,
    accountId: string,
    managed: boolean,
    primaryAccount?: boolean,
  ) {
    const response = await apiClient.patch<ApiResponse<AdAccount>>(
      `/api/v1/companies/${companyId}/ads/accounts/${accountId}`,
      { managed, primaryAccount },
    )
    return unwrap(response.data)
  },
}
