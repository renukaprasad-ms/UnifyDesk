import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type { CompanyUser } from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (!response.data) {
    throw new Error(response.error_message ?? 'Empty API response')
  }
  return response.data
}

export const companyApi = {
  async users(companyId: string) {
    const response = await apiClient.get<ApiResponse<CompanyUser[]>>(
      `/api/v1/companies/${companyId}/users`,
    )
    return unwrap(response.data)
  },
}
