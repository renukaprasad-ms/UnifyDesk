import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type { StorageUsage } from './types'

export const storageApi = {
  async usage(companyId: string) {
    const response = await apiClient.get<ApiResponse<StorageUsage>>(
      `/api/v1/companies/${companyId}/storage`,
    )
    if (!response.data.data) {
      throw new Error(response.data.error_message ?? 'Storage usage unavailable')
    }
    return response.data.data
  },
}
