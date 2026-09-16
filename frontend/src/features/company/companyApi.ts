import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type { Company, CompanyPermissionResponse, CompanyUser, CreateCompanyPayload } from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (!response.data) {
    throw new Error(response.error_message ?? 'Empty API response')
  }
  return response.data
}

export const companyApi = {
  async myCompanies() {
    const response = await apiClient.get<ApiResponse<Company[]>>('/api/v1/companies/me')
    return unwrap(response.data)
  },

  async create(payload: CreateCompanyPayload) {
    const response = await apiClient.post<ApiResponse<Company>>('/api/v1/companies', payload)
    return unwrap(response.data)
  },

  async permissions(companyId: string) {
    const response = await apiClient.get<ApiResponse<CompanyPermissionResponse>>(
      `/api/v1/companies/${companyId}/permissions/me`,
    )
    return unwrap(response.data)
  },

  async users(companyId: string) {
    const response = await apiClient.get<ApiResponse<CompanyUser[]>>(
      `/api/v1/companies/${companyId}/users`,
    )
    return unwrap(response.data)
  },
}
