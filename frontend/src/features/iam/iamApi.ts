import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type {
  CompanyIamPermissions,
  CompanyRole,
  CreateCompanyRoleInput,
  MemberRoleAssignment,
  PermissionDefinition,
  UpdateCompanyRoleInput,
} from './types'

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

  async permissionCatalog(companyId: string) {
    const response = await apiClient.get<ApiResponse<PermissionDefinition[]>>(
      `/api/v1/companies/${companyId}/iam/permissions`,
    )
    return unwrap(response.data)
  },

  async roles(companyId: string) {
    const response = await apiClient.get<ApiResponse<CompanyRole[]>>(
      `/api/v1/companies/${companyId}/iam/roles`,
    )
    return unwrap(response.data)
  },

  async createRole(companyId: string, payload: CreateCompanyRoleInput) {
    const response = await apiClient.post<ApiResponse<CompanyRole>>(
      `/api/v1/companies/${companyId}/iam/roles`,
      payload,
    )
    return unwrap(response.data)
  },

  async updateRole(companyId: string, roleId: string, payload: UpdateCompanyRoleInput) {
    const response = await apiClient.put<ApiResponse<CompanyRole>>(
      `/api/v1/companies/${companyId}/iam/roles/${roleId}`,
      payload,
    )
    return unwrap(response.data)
  },

  async deleteRole(companyId: string, roleId: string) {
    await apiClient.delete(`/api/v1/companies/${companyId}/iam/roles/${roleId}`)
  },

  async memberRoles(companyId: string, userId: string) {
    const response = await apiClient.get<ApiResponse<MemberRoleAssignment[]>>(
      `/api/v1/companies/${companyId}/iam/members/${userId}/roles`,
    )
    return unwrap(response.data)
  },

  async assignRole(companyId: string, userId: string, roleId: string) {
    const response = await apiClient.post<ApiResponse<MemberRoleAssignment>>(
      `/api/v1/companies/${companyId}/iam/members/${userId}/roles/${roleId}`,
      {},
    )
    return unwrap(response.data)
  },

  async revokeRole(companyId: string, userId: string, roleId: string) {
    await apiClient.delete(
      `/api/v1/companies/${companyId}/iam/members/${userId}/roles/${roleId}`,
    )
  },
}
