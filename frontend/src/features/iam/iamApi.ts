import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type { CompanyIamPermissions } from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (!response.data) {
    throw new Error(response.error_message ?? 'Empty API response')
  }
  return response.data
}

export const iamApi = {
  async myPermissions(companyId: string) {
    const response = await apiClient.get<ApiResponse<CompanyIamPermissions>>(
      `/api/v1/companies/${companyId}/iam/permissions/me`,
    )
    return unwrap(response.data)
  },
}
