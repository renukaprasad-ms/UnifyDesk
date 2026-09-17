import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import { workspaceApi } from '../workspace/workspaceApi'
import type { CompanyUser, CreateCompanyPayload } from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (!response.data) {
    throw new Error(response.error_message ?? 'Empty API response')
  }
  return response.data
}

export const companyApi = {
  // Kept as forwarding methods for existing workspace creation screens.
  // New workspace-level code should use workspaceApi directly.
  myCompanies: workspaceApi.list,
  create: (payload: CreateCompanyPayload) => workspaceApi.create(payload),

  async users(companyId: string) {
    const response = await apiClient.get<ApiResponse<CompanyUser[]>>(
      `/api/v1/companies/${companyId}/users`,
    )
    return unwrap(response.data)
  },
}
