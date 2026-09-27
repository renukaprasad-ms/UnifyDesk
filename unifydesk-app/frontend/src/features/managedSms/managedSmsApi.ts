import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import {
  sanitizeManagedSmsDeliveryHealth,
  sanitizeManagedSmsDltProfile,
  sanitizeManagedSmsHeader,
  sanitizeManagedSmsHeaders,
  sanitizeManagedSmsPreview,
  sanitizeManagedSmsStatus,
  sanitizeManagedSmsTemplate,
  sanitizeManagedSmsTemplates,
  sanitizeManagedSmsUsage,
} from './privacy'
import type {
  CreateManagedSmsDltProfileInput,
  CreateManagedSmsHeaderInput,
  CreateManagedSmsTemplateInput,
  ManagedSmsSender,
} from './types'

const basePath = (companyId: string) => `/api/companies/${companyId}/managed-sms`

const data = (response: ApiResponse<unknown>) => response.data

export const managedSmsApi = {
  async status(companyId: string) {
    const response = await apiClient.get<ApiResponse<unknown>>(`${basePath(companyId)}/status`)
    return sanitizeManagedSmsStatus(data(response.data))
  },

  async dltProfile(companyId: string) {
    const response = await apiClient.get<ApiResponse<unknown>>(`${basePath(companyId)}/dlt-profile`)
    return sanitizeManagedSmsDltProfile(data(response.data))
  },

  async createDltProfile(companyId: string, input: CreateManagedSmsDltProfileInput) {
    const response = await apiClient.post<ApiResponse<unknown>>(
      `${basePath(companyId)}/dlt-profile`,
      input,
    )
    return sanitizeManagedSmsDltProfile(data(response.data))
  },

  async submitDltProfile(companyId: string, profileId: string) {
    const response = await apiClient.post<ApiResponse<unknown>>(
      `${basePath(companyId)}/dlt-profile/${profileId}/submit`,
    )
    return sanitizeManagedSmsDltProfile(data(response.data))
  },

  async refreshDltProfile(companyId: string, profileId: string) {
    const response = await apiClient.post<ApiResponse<unknown>>(
      `${basePath(companyId)}/dlt-profile/${profileId}/refresh`,
    )
    return sanitizeManagedSmsDltProfile(data(response.data))
  },

  async headers(companyId: string) {
    const response = await apiClient.get<ApiResponse<unknown>>(`${basePath(companyId)}/headers`)
    return sanitizeManagedSmsHeaders(data(response.data))
  },

  async createHeader(companyId: string, input: CreateManagedSmsHeaderInput) {
    const response = await apiClient.post<ApiResponse<unknown>>(
      `${basePath(companyId)}/headers`,
      input,
    )
    return sanitizeManagedSmsHeader(data(response.data))
  },

  async submitHeader(companyId: string, headerId: string) {
    const response = await apiClient.post<ApiResponse<unknown>>(
      `${basePath(companyId)}/headers/${headerId}/submit`,
    )
    return sanitizeManagedSmsHeader(data(response.data))
  },

  async refreshHeader(companyId: string, headerId: string) {
    const response = await apiClient.post<ApiResponse<unknown>>(
      `${basePath(companyId)}/headers/${headerId}/refresh`,
    )
    return sanitizeManagedSmsHeader(data(response.data))
  },

  async templates(companyId: string) {
    const response = await apiClient.get<ApiResponse<unknown>>(`${basePath(companyId)}/templates`)
    return sanitizeManagedSmsTemplates(data(response.data))
  },

  async createTemplate(companyId: string, input: CreateManagedSmsTemplateInput) {
    const response = await apiClient.post<ApiResponse<unknown>>(
      `${basePath(companyId)}/templates`,
      input,
    )
    return sanitizeManagedSmsTemplate(data(response.data))
  },

  async submitTemplateVersion(companyId: string, templateId: string, versionId: string) {
    const response = await apiClient.post<ApiResponse<unknown>>(
      `${basePath(companyId)}/templates/${templateId}/versions/${versionId}/submit`,
    )
    return sanitizeManagedSmsTemplate(data(response.data))
  },

  async refreshTemplateVersion(companyId: string, templateId: string, versionId: string) {
    const response = await apiClient.post<ApiResponse<unknown>>(
      `${basePath(companyId)}/templates/${templateId}/versions/${versionId}/refresh`,
    )
    return sanitizeManagedSmsTemplate(data(response.data))
  },

  async previewTemplate(
    companyId: string,
    templateId: string,
    input: { headerId: string; variables: Record<string, string> },
  ) {
    const response = await apiClient.post<ApiResponse<unknown>>(
      `${basePath(companyId)}/templates/${templateId}/preview`,
      input,
    )
    return sanitizeManagedSmsPreview(data(response.data))
  },

  async createSender(
    companyId: string,
    input: { headerId: string; displayName?: string | null },
  ) {
    const response = await apiClient.post<ApiResponse<ManagedSmsSender>>(
      `${basePath(companyId)}/senders`,
      input,
    )
    if (!response.data.data) throw new Error('Managed SMS sender response was empty')
    return response.data.data
  },

  async usage(companyId: string) {
    const response = await apiClient.get<ApiResponse<unknown>>(`${basePath(companyId)}/usage`)
    return sanitizeManagedSmsUsage(data(response.data))
  },

  async deliveryHealth(companyId: string) {
    const response = await apiClient.get<ApiResponse<unknown>>(
      `${basePath(companyId)}/delivery-health`,
    )
    return sanitizeManagedSmsDeliveryHealth(data(response.data))
  },
}
