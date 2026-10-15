import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type {
  CreateCustomerManagedEmailDomainInput,
  CreateHostedManagedEmailDomainInput,
  CreateManagedEmailSenderInput,
  ManagedEmailDeliverability,
  ManagedEmailDomain,
  ManagedEmailOverview,
  ManagedEmailSender,
  ManagedEmailUsage,
  SubdomainAvailability,
} from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (!response.data) throw new Error(response.error_message ?? 'Empty API response')
  return response.data
}

const basePath = (companyId: string) => `/api/v1/companies/${companyId}/managed-email`

export const managedEmailApi = {
  async overview(companyId: string) {
    const response = await apiClient.get<ApiResponse<ManagedEmailOverview>>(
      `${basePath(companyId)}/overview`,
    )
    return unwrap(response.data)
  },

  async domains(companyId: string) {
    const response = await apiClient.get<ApiResponse<ManagedEmailDomain[]>>(
      `${basePath(companyId)}/domains`,
    )
    return unwrap(response.data)
  },

  async domain(companyId: string, domainId: string) {
    const response = await apiClient.get<ApiResponse<ManagedEmailDomain>>(
      `${basePath(companyId)}/domains/${domainId}`,
    )
    return unwrap(response.data)
  },

  async createCustomerDomain(companyId: string, input: CreateCustomerManagedEmailDomainInput) {
    const response = await apiClient.post<ApiResponse<ManagedEmailDomain>>(
      `${basePath(companyId)}/domains/customer`,
      input,
    )
    return unwrap(response.data)
  },

  async checkSubdomain(companyId: string, subdomain: string) {
    const response = await apiClient.get<ApiResponse<SubdomainAvailability>>(
      `${basePath(companyId)}/domains/hosted/check-subdomain`,
      { params: { subdomain } },
    )
    return unwrap(response.data)
  },

  async createHostedDomain(companyId: string, input: CreateHostedManagedEmailDomainInput) {
    const response = await apiClient.post<ApiResponse<ManagedEmailDomain>>(
      `${basePath(companyId)}/domains/hosted`,
      input,
    )
    return unwrap(response.data)
  },

  async refreshDomain(companyId: string, domainId: string) {
    const response = await apiClient.post<ApiResponse<ManagedEmailDomain>>(
      `${basePath(companyId)}/domains/${domainId}/refresh`,
    )
    return unwrap(response.data)
  },

  async retireDomain(companyId: string, domainId: string) {
    const response = await apiClient.delete<ApiResponse<ManagedEmailDomain>>(
      `${basePath(companyId)}/domains/${domainId}`,
    )
    return response.data.data ?? null
  },

  async senders(companyId: string) {
    const response = await apiClient.get<ApiResponse<ManagedEmailSender[]>>(
      `${basePath(companyId)}/senders`,
    )
    return unwrap(response.data)
  },

  async createSender(companyId: string, input: CreateManagedEmailSenderInput) {
    const response = await apiClient.post<ApiResponse<ManagedEmailSender>>(
      `${basePath(companyId)}/senders`,
      input,
    )
    return unwrap(response.data)
  },

  async removeSender(companyId: string, senderId: string) {
    await apiClient.delete(`${basePath(companyId)}/senders/${senderId}`)
  },

  async usage(companyId: string) {
    const response = await apiClient.get<ApiResponse<ManagedEmailUsage>>(
      `${basePath(companyId)}/usage`,
    )
    return unwrap(response.data)
  },

  async deliverability(companyId: string) {
    const response = await apiClient.get<ApiResponse<ManagedEmailDeliverability>>(
      `${basePath(companyId)}/deliverability`,
    )
    return unwrap(response.data)
  },
}

export type ManagedEmailApi = typeof managedEmailApi
