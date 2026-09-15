import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type { Company, CreateCompanyPayload } from './types'

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
}
