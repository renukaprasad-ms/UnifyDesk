import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type { CompanyIamPermissions, CompanyRole, MemberRoleAssignment } from './types'

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

  async roles(companyId: string) {
    const response = await apiClient.get<ApiResponse<CompanyRole[]>>(
      `/api/v1/companies/${companyId}/iam/roles`,
    )
    return unwrap(response.data)
  },

  async memberRoles(companyId: string, userId: string) {
    const response = await apiClient.get<ApiResponse<MemberRoleAssignment[]>>(
      `/api/v1/companies/${companyId}/iam/members/${userId}/roles`,
    )
    return unwrap(response.data)
  },
}
