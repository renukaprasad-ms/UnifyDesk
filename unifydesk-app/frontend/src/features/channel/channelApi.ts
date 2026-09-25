import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type {
  ChannelConnection,
  ChannelType,
  DisconnectDataPolicy,
  SenderIdentity,
} from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (!response.data) throw new Error(response.error_message ?? 'Empty API response')
  return response.data
}

export const channelApi = {
  async list(companyId: string, channelType?: ChannelType) {
    const response = await apiClient.get<ApiResponse<ChannelConnection[]>>(
      `/api/v1/companies/${companyId}/channels`,
      { params: channelType ? { channelType } : undefined },
    )
    return unwrap(response.data)
  },

  async senders(companyId: string, connectionId: string) {
    const response = await apiClient.get<ApiResponse<SenderIdentity[]>>(
      `/api/v1/companies/${companyId}/channels/${connectionId}/senders`,
    )
    return unwrap(response.data)
  },

  async disconnect(
    companyId: string,
    connectionId: string,
    dataPolicy: DisconnectDataPolicy = 'KEEP_7_DAYS',
  ) {
    const response = await apiClient.post<ApiResponse<ChannelConnection>>(
      `/api/v1/companies/${companyId}/channels/${connectionId}/disconnect`,
      { dataPolicy },
    )
    return unwrap(response.data)
  },
}
