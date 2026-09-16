import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type { PlatformAccess } from './types'

export const platformApi = {
  async me() {
    const response = await apiClient.get<ApiResponse<PlatformAccess>>('/api/v1/platform/me')
    if (!response.data.data) throw new Error(response.data.error_message ?? 'Unable to load platform access')
    return response.data.data
  },
}
