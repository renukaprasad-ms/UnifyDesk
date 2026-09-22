import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type { ChannelType } from '../channel/types'
import type {
  BroadcastGroup,
  BroadcastMember,
  BroadcastMemberImportResult,
  BroadcastMemberInput,
  BroadcastMessage,
  BroadcastMessageSummary,
  BroadcastRecipient,
  BroadcastSender,
  ConnectAmazonSesInput,
  ConnectAzureEmailInput,
  ConnectMsg91Input,
  ConnectSendGridInput,
  CreateBroadcastGroupInput,
  CreateBroadcastMessageInput,
  Page,
} from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (!response.data) throw new Error(response.error_message ?? 'Empty API response')
  return response.data
}

export const broadcastApi = {
  async senders(companyId: string) {
    const response = await apiClient.get<ApiResponse<BroadcastSender[]>>(
      `/api/v1/companies/${companyId}/broadcast/senders`,
    )
    return unwrap(response.data)
  },

  async connectAmazonSes(companyId: string, input: ConnectAmazonSesInput) {
    const response = await apiClient.post<ApiResponse<BroadcastSender>>(
      `/api/v1/companies/${companyId}/broadcast/senders/amazon-ses`,
      input,
    )
    return unwrap(response.data)
  },

  async connectSendGrid(companyId: string, input: ConnectSendGridInput) {
    const response = await apiClient.post<ApiResponse<BroadcastSender>>(
      `/api/v1/companies/${companyId}/broadcast/senders/sendgrid`,
      input,
    )
    return unwrap(response.data)
  },

  async connectAzureEmail(companyId: string, input: ConnectAzureEmailInput) {
    const response = await apiClient.post<ApiResponse<BroadcastSender>>(
      `/api/v1/companies/${companyId}/broadcast/senders/azure-email`,
      input,
    )
    return unwrap(response.data)
  },

  async connectMsg91(companyId: string, input: ConnectMsg91Input) {
    const response = await apiClient.post<ApiResponse<BroadcastSender>>(
      `/api/v1/companies/${companyId}/broadcast/senders/msg91`,
      input,
    )
    return unwrap(response.data)
  },

  async refreshSender(companyId: string, senderId: string) {
    const response = await apiClient.post<ApiResponse<BroadcastSender>>(
      `/api/v1/companies/${companyId}/broadcast/senders/${senderId}/refresh`,
    )
    return unwrap(response.data)
  },

  async disconnectSender(companyId: string, senderId: string) {
    await apiClient.delete(
      `/api/v1/companies/${companyId}/broadcast/senders/${senderId}`,
    )
  },

  async groups(companyId: string, channelType: ChannelType, page = 0, size = 50) {
    const response = await apiClient.get<ApiResponse<Page<BroadcastGroup>>>(
      `/api/v1/companies/${companyId}/broadcast/groups`,
      { params: { channelType, page, size } },
    )
    return unwrap(response.data)
  },

  async createGroup(companyId: string, input: CreateBroadcastGroupInput) {
    const response = await apiClient.post<ApiResponse<BroadcastGroup>>(
      `/api/v1/companies/${companyId}/broadcast/groups`,
      input,
    )
    return unwrap(response.data)
  },

  async updateGroup(
    companyId: string,
    groupId: string,
    input: Pick<CreateBroadcastGroupInput, 'name' | 'description'>,
  ) {
    const response = await apiClient.patch<ApiResponse<BroadcastGroup>>(
      `/api/v1/companies/${companyId}/broadcast/groups/${groupId}`,
      input,
    )
    return unwrap(response.data)
  },

  async group(companyId: string, groupId: string) {
    const response = await apiClient.get<ApiResponse<BroadcastGroup>>(
      `/api/v1/companies/${companyId}/broadcast/groups/${groupId}`,
    )
    return unwrap(response.data)
  },

  async members(companyId: string, groupId: string, page = 0, size = 50) {
    const response = await apiClient.get<ApiResponse<Page<BroadcastMember>>>(
      `/api/v1/companies/${companyId}/broadcast/groups/${groupId}/members`,
      { params: { page, size } },
    )
    return unwrap(response.data)
  },

  async addMembers(companyId: string, groupId: string, members: BroadcastMemberInput[]) {
    const response = await apiClient.post<ApiResponse<BroadcastMemberImportResult>>(
      `/api/v1/companies/${companyId}/broadcast/groups/${groupId}/members`,
      { members },
    )
    return unwrap(response.data)
  },

  async importMembers(companyId: string, groupId: string, file: File) {
    const form = new FormData()
    form.append('file', file)
    const response = await apiClient.post<ApiResponse<BroadcastMemberImportResult>>(
      `/api/v1/companies/${companyId}/broadcast/groups/${groupId}/members/import`,
      form,
    )
    return unwrap(response.data)
  },

  async removeMember(companyId: string, groupId: string, memberId: string) {
    await apiClient.delete(
      `/api/v1/companies/${companyId}/broadcast/groups/${groupId}/members/${memberId}`,
    )
  },

  async history(companyId: string, groupId: string, page = 0, size = 30) {
    const response = await apiClient.get<ApiResponse<Page<BroadcastMessageSummary>>>(
      `/api/v1/companies/${companyId}/broadcast/groups/${groupId}/messages`,
      { params: { page, size } },
    )
    return unwrap(response.data)
  },

  async createMessage(
    companyId: string,
    groupId: string,
    input: CreateBroadcastMessageInput,
  ) {
    const response = await apiClient.post<ApiResponse<BroadcastMessage>>(
      `/api/v1/companies/${companyId}/broadcast/groups/${groupId}/messages`,
      input,
    )
    return unwrap(response.data)
  },

  async updateMessage(
    companyId: string,
    broadcastId: string,
    input: CreateBroadcastMessageInput,
  ) {
    const response = await apiClient.put<ApiResponse<BroadcastMessage>>(
      `/api/v1/companies/${companyId}/broadcast/messages/${broadcastId}`,
      input,
    )
    return unwrap(response.data)
  },

  async message(companyId: string, broadcastId: string) {
    const response = await apiClient.get<ApiResponse<BroadcastMessage>>(
      `/api/v1/companies/${companyId}/broadcast/messages/${broadcastId}`,
    )
    return unwrap(response.data)
  },

  async recipients(companyId: string, broadcastId: string, page = 0, size = 50) {
    const response = await apiClient.get<ApiResponse<Page<BroadcastRecipient>>>(
      `/api/v1/companies/${companyId}/broadcast/messages/${broadcastId}/recipients`,
      { params: { page, size } },
    )
    return unwrap(response.data)
  },

  async send(companyId: string, broadcastId: string) {
    const response = await apiClient.post<ApiResponse<BroadcastMessage>>(
      `/api/v1/companies/${companyId}/broadcast/messages/${broadcastId}/send`,
    )
    return unwrap(response.data)
  },

  async schedule(companyId: string, broadcastId: string, scheduledAt: string) {
    const response = await apiClient.post<ApiResponse<BroadcastMessage>>(
      `/api/v1/companies/${companyId}/broadcast/messages/${broadcastId}/schedule`,
      { scheduledAt },
    )
    return unwrap(response.data)
  },

  async pause(companyId: string, broadcastId: string) {
    const response = await apiClient.post<ApiResponse<BroadcastMessage>>(
      `/api/v1/companies/${companyId}/broadcast/messages/${broadcastId}/pause`,
    )
    return unwrap(response.data)
  },

  async resume(companyId: string, broadcastId: string) {
    const response = await apiClient.post<ApiResponse<BroadcastMessage>>(
      `/api/v1/companies/${companyId}/broadcast/messages/${broadcastId}/resume`,
    )
    return unwrap(response.data)
  },

  async cancel(companyId: string, broadcastId: string) {
    const response = await apiClient.post<ApiResponse<BroadcastMessage>>(
      `/api/v1/companies/${companyId}/broadcast/messages/${broadcastId}/cancel`,
    )
    return unwrap(response.data)
  },
}
