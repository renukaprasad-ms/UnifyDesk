import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type {
  AssignCompanySubscriptionInput,
  BillingPlan,
  CompanyRateOverride,
  CompanySubscription,
  CreateBillingPlanInput,
  CreateCompanyRateOverrideInput,
  CreatePlanPriceInput,
  CreateRateCardInput,
  EntitlementDefinition,
  PlanEntitlement,
  PlanPrice,
  RateCard,
  UpdateBillingPlanInput,
  UpdatePlanPriceInput,
  UpdateRateCardInput,
} from './types'

const unwrap = <T>(response: ApiResponse<T>, fallback: string) => {
  if (response.data === undefined || response.data === null) {
    throw new Error(response.error_message ?? fallback)
  }
  return response.data
}

export const adminBillingApi = {
  async plans() {
    const response = await apiClient.get<ApiResponse<BillingPlan[]>>('/api/v1/admin/billing/plans')
    return unwrap(response.data, 'Unable to load billing plans')
  },

  async createPlan(input: CreateBillingPlanInput) {
    const response = await apiClient.post<ApiResponse<BillingPlan>>('/api/v1/admin/billing/plans', input)
    return unwrap(response.data, 'Unable to create billing plan')
  },

  async updatePlan(planId: string, input: UpdateBillingPlanInput) {
    const response = await apiClient.patch<ApiResponse<BillingPlan>>(
      `/api/v1/admin/billing/plans/${planId}`,
      input,
    )
    return unwrap(response.data, 'Unable to update billing plan')
  },

  async entitlementDefinitions() {
    const response = await apiClient.get<ApiResponse<EntitlementDefinition[]>>(
      '/api/v1/admin/billing/entitlement-definitions',
    )
    return unwrap(response.data, 'Unable to load entitlement definitions')
  },

  async saveEntitlement(planId: string, key: string, value: string) {
    const response = await apiClient.post<ApiResponse<PlanEntitlement>>(
      `/api/v1/admin/billing/plans/${planId}/entitlements`,
      { key, value },
    )
    return unwrap(response.data, 'Unable to save entitlement')
  },

  async deleteEntitlement(entitlementId: string) {
    await apiClient.delete(`/api/v1/admin/billing/entitlements/${entitlementId}`)
  },

  async planPrices(planId: string) {
    const response = await apiClient.get<ApiResponse<PlanPrice[]>>(
      `/api/v1/admin/billing/plans/${planId}/prices`,
    )
    return unwrap(response.data, 'Unable to load plan prices')
  },

  async createPlanPrice(planId: string, input: CreatePlanPriceInput) {
    const response = await apiClient.post<ApiResponse<PlanPrice>>(
      `/api/v1/admin/billing/plans/${planId}/prices`,
      input,
    )
    return unwrap(response.data, 'Unable to create plan price')
  },

  async updatePlanPrice(planPriceId: string, input: UpdatePlanPriceInput) {
    const response = await apiClient.patch<ApiResponse<PlanPrice>>(
      `/api/v1/admin/billing/plan-prices/${planPriceId}`,
      input,
    )
    return unwrap(response.data, 'Unable to update plan price')
  },

  async rateCards(planId: string) {
    const response = await apiClient.get<ApiResponse<RateCard[]>>('/api/v1/admin/billing/rate-cards', {
      params: { planId },
    })
    return unwrap(response.data, 'Unable to load rate cards')
  },

  async createRateCard(input: CreateRateCardInput) {
    const response = await apiClient.post<ApiResponse<RateCard>>('/api/v1/admin/billing/rate-cards', input)
    return unwrap(response.data, 'Unable to create rate card')
  },

  async updateRateCard(rateCardId: string, input: UpdateRateCardInput) {
    const response = await apiClient.patch<ApiResponse<RateCard>>(
      `/api/v1/admin/billing/rate-cards/${rateCardId}`,
      input,
    )
    return unwrap(response.data, 'Unable to update rate card')
  },

  async companyRateOverrides(companyId: string) {
    const response = await apiClient.get<ApiResponse<CompanyRateOverride[]>>(
      `/api/v1/admin/billing/companies/${companyId}/rate-overrides`,
    )
    return unwrap(response.data, 'Unable to load company rate overrides')
  },

  async createCompanyRateOverride(companyId: string, input: CreateCompanyRateOverrideInput) {
    const response = await apiClient.post<ApiResponse<CompanyRateOverride>>(
      `/api/v1/admin/billing/companies/${companyId}/rate-overrides`,
      input,
    )
    return unwrap(response.data, 'Unable to create company rate override')
  },

  async assignCompanySubscription(companyId: string, input: AssignCompanySubscriptionInput) {
    const response = await apiClient.post<ApiResponse<CompanySubscription>>(
      `/api/v1/admin/billing/companies/${companyId}/subscription`,
      input,
    )
    return unwrap(response.data, 'Unable to assign company subscription')
  },

  async cancelCompanySubscription(companyId: string, atPeriodEnd = true) {
    const response = await apiClient.post<ApiResponse<CompanySubscription>>(
      `/api/v1/admin/billing/companies/${companyId}/subscription/cancel`,
      undefined,
      { params: { atPeriodEnd } },
    )
    return unwrap(response.data, 'Unable to cancel company subscription')
  },
}
