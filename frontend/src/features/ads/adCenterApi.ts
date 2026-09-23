import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type {
  AdAccount,
  AdAccountResource,
  AdSet,
  AdProviderConnection,
  AdProviderInfo,
  AdCampaign,
  CreateMetaCampaignInput,
  CreateMetaAdSetInput,
  MetaAdsAuthorization,
  MetaAdsResourceSync,
  MetaCampaignSync,
  MetaAdSetSync,
  PageResponse,
  UpdateMetaCampaignInput,
  UpdateMetaAdSetInput,
} from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (!response.data) throw new Error(response.error_message ?? 'Empty API response')
  return response.data
}

export const adCenterApi = {
  async providers(companyId: string) {
    const response = await apiClient.get<ApiResponse<AdProviderInfo[]>>(
      `/api/v1/companies/${companyId}/ads/providers`,
    )
    return unwrap(response.data)
  },

  async connections(companyId: string) {
    const response = await apiClient.get<ApiResponse<AdProviderConnection[]>>(
      `/api/v1/companies/${companyId}/ads/connections`,
    )
    return unwrap(response.data)
  },

  async accounts(companyId: string) {
    const response = await apiClient.get<ApiResponse<AdAccount[]>>(
      `/api/v1/companies/${companyId}/ads/accounts`,
    )
    return unwrap(response.data)
  },

  async allResources(companyId: string) {
    const response = await apiClient.get<ApiResponse<AdAccountResource[]>>(
      `/api/v1/companies/${companyId}/ads/resources`,
    )
    return unwrap(response.data)
  },

  async resources(companyId: string, accountId: string) {
    const response = await apiClient.get<ApiResponse<AdAccountResource[]>>(
      `/api/v1/companies/${companyId}/ads/accounts/${accountId}/resources`,
    )
    return unwrap(response.data)
  },

  async syncMetaResources(companyId: string, accountId: string) {
    const response = await apiClient.post<ApiResponse<MetaAdsResourceSync>>(
      `/api/v1/companies/${companyId}/ads/meta/accounts/${accountId}/resources/sync`,
    )
    return unwrap(response.data)
  },

  async campaigns(companyId: string, accountId?: string, page = 0, size = 50) {
    const response = await apiClient.get<ApiResponse<PageResponse<AdCampaign>>>(
      `/api/v1/companies/${companyId}/ads/campaigns`,
      { params: { ...(accountId ? { accountId } : {}), page, size } },
    )
    return unwrap(response.data)
  },

  async campaign(companyId: string, campaignId: string) {
    const response = await apiClient.get<ApiResponse<AdCampaign>>(
      `/api/v1/companies/${companyId}/ads/campaigns/${campaignId}`,
    )
    return unwrap(response.data)
  },

  async syncMetaCampaigns(companyId: string, accountId: string) {
    const response = await apiClient.post<ApiResponse<MetaCampaignSync>>(
      `/api/v1/companies/${companyId}/ads/meta/accounts/${accountId}/campaigns/sync`,
    )
    return unwrap(response.data)
  },

  async createMetaCampaign(
    companyId: string,
    accountId: string,
    input: CreateMetaCampaignInput,
  ) {
    const response = await apiClient.post<ApiResponse<AdCampaign>>(
      `/api/v1/companies/${companyId}/ads/meta/accounts/${accountId}/campaigns`,
      input,
    )
    return unwrap(response.data)
  },

  async updateMetaCampaign(
    companyId: string,
    campaignId: string,
    input: UpdateMetaCampaignInput,
  ) {
    const response = await apiClient.patch<ApiResponse<AdCampaign>>(
      `/api/v1/companies/${companyId}/ads/meta/campaigns/${campaignId}`,
      input,
    )
    return unwrap(response.data)
  },

  async setMetaCampaignStatus(
    companyId: string,
    campaignId: string,
    status: 'ACTIVE' | 'PAUSED',
  ) {
    const response = await apiClient.post<ApiResponse<AdCampaign>>(
      `/api/v1/companies/${companyId}/ads/meta/campaigns/${campaignId}/status`,
      { status },
    )
    return unwrap(response.data)
  },

  async adSets(companyId: string, campaignId: string) {
    const response = await apiClient.get<ApiResponse<AdSet[]>>(
      `/api/v1/companies/${companyId}/ads/campaigns/${campaignId}/ad-sets`,
    )
    return unwrap(response.data)
  },

  async adSet(companyId: string, adSetId: string) {
    const response = await apiClient.get<ApiResponse<AdSet>>(
      `/api/v1/companies/${companyId}/ads/ad-sets/${adSetId}`,
    )
    return unwrap(response.data)
  },

  async syncMetaAdSets(companyId: string, campaignId: string) {
    const response = await apiClient.post<ApiResponse<MetaAdSetSync>>(
      `/api/v1/companies/${companyId}/ads/meta/campaigns/${campaignId}/ad-sets/sync`,
    )
    return unwrap(response.data)
  },

  async createMetaAdSet(
    companyId: string,
    campaignId: string,
    input: CreateMetaAdSetInput,
  ) {
    const response = await apiClient.post<ApiResponse<AdSet>>(
      `/api/v1/companies/${companyId}/ads/meta/campaigns/${campaignId}/ad-sets`,
      input,
    )
    return unwrap(response.data)
  },

  async updateMetaAdSet(
    companyId: string,
    adSetId: string,
    input: UpdateMetaAdSetInput,
  ) {
    const response = await apiClient.patch<ApiResponse<AdSet>>(
      `/api/v1/companies/${companyId}/ads/meta/ad-sets/${adSetId}`,
      input,
    )
    return unwrap(response.data)
  },

  async setMetaAdSetStatus(
    companyId: string,
    adSetId: string,
    status: 'ACTIVE' | 'PAUSED',
  ) {
    const response = await apiClient.post<ApiResponse<AdSet>>(
      `/api/v1/companies/${companyId}/ads/meta/ad-sets/${adSetId}/status`,
      { status },
    )
    return unwrap(response.data)
  },

  async authorizeMeta(companyId: string, returnTo = '/ad-center') {
    const response = await apiClient.get<ApiResponse<MetaAdsAuthorization>>(
      `/api/v1/companies/${companyId}/ads/meta/authorize`,
      { params: { returnTo } },
    )
    return unwrap(response.data)
  },

  async disconnectConnection(companyId: string, connectionId: string) {
    const response = await apiClient.post<ApiResponse<AdProviderConnection>>(
      `/api/v1/companies/${companyId}/ads/connections/${connectionId}/disconnect`,
    )
    return unwrap(response.data)
  },

  async updateAccount(
    companyId: string,
    accountId: string,
    managed: boolean,
    primaryAccount?: boolean,
  ) {
    const response = await apiClient.patch<ApiResponse<AdAccount>>(
      `/api/v1/companies/${companyId}/ads/accounts/${accountId}`,
      { managed, primaryAccount },
    )
    return unwrap(response.data)
  },
}
