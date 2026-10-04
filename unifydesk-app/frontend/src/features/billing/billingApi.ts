import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type {
  CheckoutResponse,
  CompanyPlanDetails,
  CompanySubscription,
  InitialCheckoutInput,
  Plan,
  SubscriptionChange,
  UpgradeCheckoutInput,
} from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (response.data === undefined || response.data === null) {
    throw new Error(response.error_message ?? 'Empty API response')
  }
  return response.data
}

export const billingApi = {
  async plans() {
    const response = await apiClient.get<ApiResponse<Plan[]>>('/api/v1/plans')
    return unwrap(response.data)
  },

  async eligiblePlans(companyId: string) {
    const response = await apiClient.get<ApiResponse<Plan[]>>(
      `/api/v1/companies/${companyId}/plans`,
    )
    return unwrap(response.data)
  },

  async selectPlan(companyId: string, planId: string) {
    const response = await apiClient.post<ApiResponse<CompanySubscription>>(
      `/api/v1/companies/${companyId}/subscription`,
      { planId },
    )
    return unwrap(response.data)
  },

  async companyPlan(companyId: string) {
    const response = await apiClient.get<ApiResponse<CompanyPlanDetails>>(
      `/api/v1/companies/${companyId}/plan`,
    )
    return unwrap(response.data)
  },

  async requestUpgrade(
    companyId: string,
    input: { targetPlanId: string; targetPlanPriceId: string; idempotencyKey: string },
  ) {
    const response = await apiClient.post<ApiResponse<SubscriptionChange>>(
      `/api/v1/companies/${companyId}/subscription/upgrade`,
      input,
    )
    return unwrap(response.data)
  },

  async scheduleDowngrade(
    companyId: string,
    input: { targetPlanId: string; targetPlanPriceId: string; idempotencyKey: string },
  ) {
    const response = await apiClient.post<ApiResponse<SubscriptionChange>>(
      `/api/v1/companies/${companyId}/subscription/downgrade`,
      input,
    )
    return unwrap(response.data)
  },

  async cancelAtPeriodEnd(companyId: string) {
    const response = await apiClient.post<ApiResponse<CompanySubscription>>(
      `/api/v1/companies/${companyId}/subscription/cancel`,
      undefined,
      { params: { atPeriodEnd: true } },
    )
    return unwrap(response.data)
  },

  async initialCheckout(companyId: string, input: InitialCheckoutInput) {
    const response = await apiClient.post<ApiResponse<CheckoutResponse>>(
      `/api/v1/companies/${companyId}/billing/payments/initial`,
      input,
    )
    return unwrap(response.data)
  },

  async upgradeCheckout(companyId: string, input: UpgradeCheckoutInput) {
    const response = await apiClient.post<ApiResponse<CheckoutResponse>>(
      `/api/v1/companies/${companyId}/billing/payments/upgrade`,
      input,
    )
    return unwrap(response.data)
  },
}
