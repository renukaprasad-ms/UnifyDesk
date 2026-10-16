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

  async getWallet(companyId: string) {
    const response = await apiClient.get<ApiResponse<import('./types').CompanyWallet>>(
      `/api/v1/companies/${companyId}/billing/wallet`,
    )
    return unwrap(response.data)
  },

  async getTransactions(companyId: string, page = 0, size = 20) {
    const response = await apiClient.get<ApiResponse<{ content: import('./types').WalletTransaction[]; totalElements: number }>>(
      `/api/v1/companies/${companyId}/billing/wallet/transactions`,
      { params: { page, size } },
    )
    return unwrap(response.data)
  },

  async createTopupOrder(
    companyId: string,
    input: { amount: number; currency?: string; customer?: import('./types').CheckoutCustomer },
  ) {
    const response = await apiClient.post<ApiResponse<import('./types').WalletTopupOrder>>(
      `/api/v1/companies/${companyId}/billing/wallet/topup-order`,
      input,
    )
    return unwrap(response.data)
  },

  async confirmTopup(companyId: string, orderId: string) {
    const response = await apiClient.post<ApiResponse<import('./types').CompanyWallet>>(
      `/api/v1/companies/${companyId}/billing/wallet/topup-confirm`,
      { orderId },
    )
    return unwrap(response.data)
  },

  async configureAutoRecharge(
    companyId: string,
    input: { enabled: boolean; threshold?: number; amount?: number },
  ) {
    const response = await apiClient.put<ApiResponse<import('./types').CompanyWallet>>(
      `/api/v1/companies/${companyId}/billing/wallet/auto-recharge`,
      input,
    )
    return unwrap(response.data)
  },

  async getBillingProfile(companyId: string) {
    const response = await apiClient.get<ApiResponse<import('./types').CompanyBillingProfile>>(
      `/api/v1/companies/${companyId}/billing/profile`,
    )
    return unwrap(response.data)
  },

  async updateBillingProfile(companyId: string, input: import('./types').CompanyBillingProfile) {
    const response = await apiClient.put<ApiResponse<import('./types').CompanyBillingProfile>>(
      `/api/v1/companies/${companyId}/billing/profile`,
      input,
    )
    return unwrap(response.data)
  },

  async getInvoices(companyId: string) {
    const response = await apiClient.get<ApiResponse<import('./types').Invoice[]>>(
      `/api/v1/companies/${companyId}/billing/invoices`,
    )
    return unwrap(response.data)
  },

  async getUsage(companyId: string) {
    const response = await apiClient.get<ApiResponse<import('./types').BillingUsageSummary>>(
      `/api/v1/companies/${companyId}/billing/usage`,
    )
    return unwrap(response.data)
  },

  async getAutoPayDetails(companyId: string) {
    const response = await apiClient.get<ApiResponse<import('./types').AutoPayDetails>>(
      `/api/v1/companies/${companyId}/billing/autopay`,
    )
    return unwrap(response.data)
  },

  async setupAutoPay(companyId: string, input?: import('./types').AutoPaySetupInput) {
    const response = await apiClient.post<ApiResponse<import('./types').AutoPayDetails>>(
      `/api/v1/companies/${companyId}/billing/autopay/setup`,
      input ?? {},
    )
    return unwrap(response.data)
  },

  async verifyAutoPay(companyId: string) {
    const response = await apiClient.post<ApiResponse<import('./types').AutoPayDetails>>(
      `/api/v1/companies/${companyId}/billing/autopay/verify`,
    )
    return unwrap(response.data)
  },

  async cancelAutoPay(companyId: string) {
    const response = await apiClient.delete<ApiResponse<void>>(
      `/api/v1/companies/${companyId}/billing/autopay`,
    )
    return unwrap(response.data)
  },
}

