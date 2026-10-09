import axios from 'axios'
import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type { ChannelType } from '../channel/types'
import type {
  AudienceSelector,
  AudienceSourceDescriptor,
  BroadcastGroup,
  BroadcastMember,
  BroadcastMemberImportResult,
  BroadcastMemberInput,
  BroadcastMessage,
  BroadcastMessageSummary,
  BroadcastRecipient,
  BroadcastSender,
  BroadcastSetupSession,
  BroadcastTestEmailResult,
  ConnectAmazonSesInput,
  ConnectAzureEmailInput,
  ConnectMsg91EmailInput,
  ConnectMsg91Input,
  ConnectSendGridInput,
  ConnectUnifyDeskEmailInput,
  ChannelReadiness,
  CreateAudienceBroadcastMessageInput,
  CreateBroadcastGroupInput,
  CreateBroadcastMessageInput,
  Page,
  RecipientResolutionPreview,
  UpsertBroadcastSetupSessionInput,
} from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (!response.data) throw new Error(response.error_message ?? 'Empty API response')
  return response.data
}

export const broadcastApi = {
  async activeSetupSession(companyId: string, channelType: ChannelType) {
    const response = await apiClient.get<ApiResponse<BroadcastSetupSession | null>>(
      `/api/v1/companies/${companyId}/broadcast/setup-sessions/active`,
      { params: { channelType } },
    )
    return response.data.data ?? null
  },

  async saveSetupSession(companyId: string, input: UpsertBroadcastSetupSessionInput) {
    const response = await apiClient.put<ApiResponse<BroadcastSetupSession>>(
      `/api/v1/companies/${companyId}/broadcast/setup-sessions/active`,
      input,
    )
    return unwrap(response.data)
  },

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

  async connectUnifyDeskEmail(companyId: string, input: ConnectUnifyDeskEmailInput) {
    const response = await apiClient.post<ApiResponse<BroadcastSender>>(
      `/api/v1/companies/${companyId}/broadcast/senders/unifydesk-email`,
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
      `/api/v1/companies/${companyId}/broadcast/senders/msg91-sms`,
      input,
    )
    return unwrap(response.data)
  },

  async connectMsg91Email(companyId: string, input: ConnectMsg91EmailInput) {
    const response = await apiClient.post<ApiResponse<BroadcastSender>>(
      `/api/v1/companies/${companyId}/broadcast/senders/msg91-email`,
      input,
    )
    return unwrap(response.data)
  },

  async sendTestEmail(
    companyId: string,
    senderId: string,
    input: {
      recipient: string
      subject?: string
      bodyText?: string
      templateId?: string
      variables?: Record<string, string>
    },
  ) {
    const response = await apiClient.post<ApiResponse<BroadcastTestEmailResult>>(
      `/api/v1/companies/${companyId}/broadcast/senders/${senderId}/test-email`,
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
    await apiClient.delete(`/api/v1/companies/${companyId}/broadcast/senders/${senderId}`)
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

  async channelHistory(companyId: string, channelType: ChannelType, page = 0, size = 30) {
    const response = await apiClient.get<ApiResponse<Page<BroadcastMessageSummary>>>(
      `/api/v1/companies/${companyId}/broadcast/messages`,
      { params: { channelType, page, size } },
    )
    return unwrap(response.data)
  },

  async createMessage(companyId: string, groupId: string, input: CreateBroadcastMessageInput) {
    const response = await apiClient.post<ApiResponse<BroadcastMessage>>(
      `/api/v1/companies/${companyId}/broadcast/groups/${groupId}/messages`,
      input,
    )
    return unwrap(response.data)
  },

  async createAudienceMessage(companyId: string, input: CreateAudienceBroadcastMessageInput) {
    const response = await apiClient.post<ApiResponse<BroadcastMessage>>(
      `/api/v1/companies/${companyId}/broadcast/audiences/messages`,
      input,
    )
    return unwrap(response.data)
  },

  async previewAudience(
    companyId: string,
    input: {
      selector: AudienceSelector
      channel: ChannelType
      senderId: string
    },
  ) {
    const source =
      input.selector.sourceType === 'CONTACT_LIST'
        ? { sourceType: 'LIST' as const, sourceId: input.selector.sourceId }
        : input.selector.sourceType === 'SEGMENT'
          ? { sourceType: 'SEGMENT' as const, sourceId: input.selector.sourceId }
          : { sourceType: 'CONTACTS' as const, contactIds: input.selector.contactIds }

    const response = await apiClient.post<ApiResponse<RecipientResolutionPreview>>(
      `/api/v1/companies/${companyId}/broadcast/audiences/preview`,
      {
        ...source,
        channelType: input.channel,
        senderId: input.senderId,
      },
    )
    return unwrap(response.data)
  },

  async channelReadiness(companyId: string, channel: ChannelType) {
    const response = await apiClient.get<ApiResponse<ChannelReadiness[]>>(
      `/api/v1/companies/${companyId}/broadcast/readiness`,
    )
    const readiness = unwrap(response.data).find((item) => item.channel === channel)
    if (!readiness) throw new Error(`No readiness state returned for ${channel}`)
    return readiness
  },

  async updateMessage(companyId: string, broadcastId: string, input: CreateBroadcastMessageInput) {
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

  async send(
    companyId: string,
    broadcastId: string,
    expectedSourceDescriptor?: AudienceSourceDescriptor,
  ) {
    const response = await apiClient.post<ApiResponse<BroadcastMessage>>(
      `/api/v1/companies/${companyId}/broadcast/messages/${broadcastId}/send`,
      expectedSourceDescriptor ? { expectedSourceDescriptor } : undefined,
    )
    return unwrap(response.data)
  },

  async schedule(
    companyId: string,
    broadcastId: string,
    scheduledAt: string,
    expectedSourceDescriptor?: AudienceSourceDescriptor,
  ) {
    const response = await apiClient.post<ApiResponse<BroadcastMessage>>(
      `/api/v1/companies/${companyId}/broadcast/messages/${broadcastId}/schedule`,
      {
        scheduledAt,
        ...(expectedSourceDescriptor ? { expectedSourceDescriptor } : {}),
      },
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


export const isBroadcastAudiencePreviewStale = (error: unknown) =>
  axios.isAxiosError<ApiResponse<never>>(error) &&
  error.response?.status === 409 &&
  error.response.data?.error_message === 'BROADCAST_AUDIENCE_PREVIEW_STALE'
