import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type {
  EmailTemplate,
  EmailTemplateCatalogItem,
  EmailTemplateCategory,
  EmailTemplatePreview,
  EmailTemplateProviderBinding,
  EmailTemplateStatus,
  EmailTemplateSummary,
  Page,
  SaveEmailTemplateInput,
} from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (response.data === undefined || response.data === null) {
    throw new Error(response.error_message ?? 'Empty API response')
  }
  return response.data
}

export const emailTemplateApi = {
  async catalog(category?: EmailTemplateCategory) {
    const response = await apiClient.get<ApiResponse<EmailTemplateCatalogItem[]>>(
      '/api/v1/email-template-catalog',
      { params: { category } },
    )
    return unwrap(response.data)
  },

  async list(
    companyId: string,
    params: {
      q?: string
      category?: EmailTemplateCategory
      status?: EmailTemplateStatus
      includeArchived?: boolean
      page?: number
      size?: number
    } = {},
  ) {
    const response = await apiClient.get<ApiResponse<Page<EmailTemplateSummary>>>(
      `/api/v1/companies/${companyId}/email-templates`,
      { params },
    )
    return unwrap(response.data)
  },

  async published(companyId: string) {
    const response = await apiClient.get<ApiResponse<EmailTemplateSummary[]>>(
      `/api/v1/companies/${companyId}/email-templates/published`,
    )
    return unwrap(response.data)
  },

  async get(companyId: string, templateId: string) {
    const response = await apiClient.get<ApiResponse<EmailTemplate>>(
      `/api/v1/companies/${companyId}/email-templates/${templateId}`,
    )
    return unwrap(response.data)
  },

  async create(companyId: string, input: SaveEmailTemplateInput) {
    const response = await apiClient.post<ApiResponse<EmailTemplate>>(
      `/api/v1/companies/${companyId}/email-templates`,
      input,
    )
    return unwrap(response.data)
  },

  async update(companyId: string, templateId: string, input: SaveEmailTemplateInput) {
    const response = await apiClient.put<ApiResponse<EmailTemplate>>(
      `/api/v1/companies/${companyId}/email-templates/${templateId}`,
      input,
    )
    return unwrap(response.data)
  },

  async cloneCatalog(companyId: string, catalogId: string) {
    const response = await apiClient.post<ApiResponse<EmailTemplate>>(
      `/api/v1/companies/${companyId}/email-templates/from-catalog/${catalogId}`,
      {},
    )
    return unwrap(response.data)
  },

  async preview(companyId: string, input: SaveEmailTemplateInput) {
    const response = await apiClient.post<ApiResponse<EmailTemplatePreview>>(
      `/api/v1/companies/${companyId}/email-templates/preview`,
      input,
    )
    return unwrap(response.data)
  },

  async publish(companyId: string, templateId: string) {
    const response = await apiClient.post<ApiResponse<EmailTemplate>>(
      `/api/v1/companies/${companyId}/email-templates/${templateId}/publish`,
    )
    return unwrap(response.data)
  },

  async archive(companyId: string, templateId: string) {
    await apiClient.delete(`/api/v1/companies/${companyId}/email-templates/${templateId}`)
  },

  async publishAsset(companyId: string, mediaId: string) {
    const response = await apiClient.post<ApiResponse<{ id: string; mediaId: string; publicUrl: string }>>(
      `/api/v1/companies/${companyId}/email-template-assets`,
      { mediaId },
    )
    return unwrap(response.data)
  },

  async providerBindings(
    companyId: string,
    params: { templateId?: string; connectionId?: string },
  ) {
    const response = await apiClient.get<ApiResponse<EmailTemplateProviderBinding[]>>(
      `/api/v1/companies/${companyId}/email-template-provider-bindings`,
      { params },
    )
    return unwrap(response.data)
  },

  async syncMsg91(companyId: string, templateId: string, connectionId: string) {
    const response = await apiClient.post<ApiResponse<EmailTemplateProviderBinding>>(
      `/api/v1/companies/${companyId}/email-templates/${templateId}/providers/msg91/${connectionId}/sync`,
    )
    return unwrap(response.data)
  },

  async refreshProviderBinding(companyId: string, bindingId: string) {
    const response = await apiClient.post<ApiResponse<EmailTemplateProviderBinding>>(
      `/api/v1/companies/${companyId}/email-template-provider-bindings/${bindingId}/refresh`,
    )
    return unwrap(response.data)
  },
}
