import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type { ChannelType } from '../channel/types'
import type { MessageTemplate, SaveMessageTemplateInput } from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (response.data === undefined || response.data === null) {
    throw new Error(response.error_message ?? 'Empty API response')
  }
  return response.data
}

export const templateApi = {
  async list(companyId: string, channelType?: ChannelType, includeArchived = false) {
    const response = await apiClient.get<ApiResponse<MessageTemplate[]>>(
      `/api/v1/companies/${companyId}/templates`,
      { params: { channelType, includeArchived } },
    )
    return unwrap(response.data)
  },

  async active(companyId: string, channelType: ChannelType) {
    const response = await apiClient.get<ApiResponse<MessageTemplate[]>>(
      `/api/v1/companies/${companyId}/templates/active`,
      { params: { channelType } },
    )
    return unwrap(response.data)
  },

  async create(companyId: string, input: SaveMessageTemplateInput & { channelType: ChannelType }) {
    const response = await apiClient.post<ApiResponse<MessageTemplate>>(
      `/api/v1/companies/${companyId}/templates`,
      input,
    )
    return unwrap(response.data)
  },

  async update(companyId: string, templateId: string, input: SaveMessageTemplateInput) {
    const { channelType: _channelType, ...payload } = input
    const response = await apiClient.put<ApiResponse<MessageTemplate>>(
      `/api/v1/companies/${companyId}/templates/${templateId}`,
      payload,
    )
    return unwrap(response.data)
  },

  async activate(companyId: string, templateId: string) {
    const response = await apiClient.post<ApiResponse<MessageTemplate>>(
      `/api/v1/companies/${companyId}/templates/${templateId}/activate`,
    )
    return unwrap(response.data)
  },

  async moveToDraft(companyId: string, templateId: string) {
    const response = await apiClient.post<ApiResponse<MessageTemplate>>(
      `/api/v1/companies/${companyId}/templates/${templateId}/draft`,
    )
    return unwrap(response.data)
  },

  async archive(companyId: string, templateId: string) {
    await apiClient.delete<ApiResponse<null>>(
      `/api/v1/companies/${companyId}/templates/${templateId}`,
    )
  },
}
