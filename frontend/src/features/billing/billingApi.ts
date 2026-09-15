import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type { CompanySubscription, Plan } from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (!response.data) {
    throw new Error(response.error_message ?? 'Empty API response')
  }
  return response.data
}

export const billingApi = {
  async plans() {
    const response = await apiClient.get<ApiResponse<Plan[]>>('/api/v1/plans')
    return unwrap(response.data)
  },

  async selectPlan(companyId: string, planId: string) {
    const response = await apiClient.post<ApiResponse<CompanySubscription>>(
      `/api/v1/companies/${companyId}/subscription`,
      { planId },
    )
    return unwrap(response.data)
  },
}
