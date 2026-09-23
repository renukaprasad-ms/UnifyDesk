import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type {
  AdAccount,
  AdAccountBilling,
  AdAccountHealth,
  AdAccountResource,
  AdActivityLog,
  AdAnalytics,
  AdAnalyticsBreakdown,
  AdInsightBreakdownType,
  AdInsightLevel,
  AdAudience,
  AdConversion,
  AdCreative,
  Ad,
  AdSet,
  AdProviderConnection,
  AdProviderInfo,
  AdRecommendations,
  AdCampaign,
  CreateMetaAudienceInput,
  CreateMetaCampaignInput,
  CreateMetaConversionInput,
  CreateMetaCreativeInput,
  CreateMetaAdInput,
  CreateMetaAdSetInput,
  MetaAdsAuthorization,
  MetaAdsResourceSync,
  MetaAudienceSync,
  MetaCampaignSync,
  MetaConversionSync,
  MetaInsightBreakdownsSync,
  MetaInsightsSync,
  MetaCreativeSync,
  MetaAdSync,
  MetaAdSetSync,
  PageResponse,
  UpdateMetaCampaignInput,
  UpdateMetaAdInput,
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

  async recommendations(companyId: string, accountId: string) {
    const response = await apiClient.get<ApiResponse<AdRecommendations>>(
      `/api/v1/companies/${companyId}/ads/accounts/${accountId}/recommendations`,
    )
    return unwrap(response.data)
  },

  async health(companyId: string, accountId: string) {
    const response = await apiClient.get<ApiResponse<AdAccountHealth>>(
      `/api/v1/companies/${companyId}/ads/accounts/${accountId}/health`,
    )
    return unwrap(response.data)
  },

  async syncMetaHealth(companyId: string, accountId: string) {
    const response = await apiClient.post<ApiResponse<AdAccountHealth>>(
      `/api/v1/companies/${companyId}/ads/meta/accounts/${accountId}/health/sync`,
    )
    return unwrap(response.data)
  },

  async activity(
    companyId: string,
    accountId?: string,
    page = 0,
    size = 50,
  ) {
    const response = await apiClient.get<ApiResponse<PageResponse<AdActivityLog>>>(
      `/api/v1/companies/${companyId}/ads/activity`,
      {
        params: {
          ...(accountId ? { accountId } : {}),
          page,
          size,
        },
      },
    )
    return unwrap(response.data)
  },

  async billing(companyId: string, accountId: string) {
    const response = await apiClient.get<ApiResponse<AdAccountBilling>>(
      `/api/v1/companies/${companyId}/ads/accounts/${accountId}/billing`,
    )
    return unwrap(response.data)
  },

  async syncMetaBilling(companyId: string, accountId: string) {
    const response = await apiClient.post<ApiResponse<AdAccountBilling>>(
      `/api/v1/companies/${companyId}/ads/meta/accounts/${accountId}/billing/sync`,
    )
    return unwrap(response.data)
  },

  async updateMetaSpendCap(
    companyId: string,
    accountId: string,
    spendCapMinor: number,
  ) {
    const response = await apiClient.patch<ApiResponse<AdAccountBilling>>(
      `/api/v1/companies/${companyId}/ads/meta/accounts/${accountId}/billing/spend-cap`,
      { spendCapMinor },
    )
    return unwrap(response.data)
  },

  async analytics(
    companyId: string,
    accountId: string,
    from: string,
    to: string,
    level: AdInsightLevel,
  ) {
    const response = await apiClient.get<ApiResponse<AdAnalytics>>(
      `/api/v1/companies/${companyId}/ads/accounts/${accountId}/analytics`,
      { params: { from, to, level } },
    )
    return unwrap(response.data)
  },

  async syncMetaInsights(
    companyId: string,
    accountId: string,
    from: string,
    to: string,
  ) {
    const response = await apiClient.post<ApiResponse<MetaInsightsSync>>(
      `/api/v1/companies/${companyId}/ads/meta/accounts/${accountId}/insights/sync`,
      null,
      { params: { from, to } },
    )
    return unwrap(response.data)
  },

  async analyticsBreakdown(
    companyId: string,
    accountId: string,
    from: string,
    to: string,
    type: AdInsightBreakdownType,
  ) {
    const response = await apiClient.get<ApiResponse<AdAnalyticsBreakdown>>(
      `/api/v1/companies/${companyId}/ads/accounts/${accountId}/analytics/breakdowns`,
      { params: { from, to, type } },
    )
    return unwrap(response.data)
  },

  async syncMetaInsightBreakdowns(
    companyId: string,
    accountId: string,
    from: string,
    to: string,
  ) {
    const response = await apiClient.post<ApiResponse<MetaInsightBreakdownsSync>>(
      `/api/v1/companies/${companyId}/ads/meta/accounts/${accountId}/insights/breakdowns/sync`,
      null,
      { params: { from, to } },
    )
    return unwrap(response.data)
  },

  async createMetaPixel(companyId: string, accountId: string, name: string) {
    const response = await apiClient.post<ApiResponse<AdAccountResource>>(
      `/api/v1/companies/${companyId}/ads/meta/accounts/${accountId}/pixels`,
      { name },
    )
    return unwrap(response.data)
  },

  async audiences(companyId: string, accountId: string) {
    const response = await apiClient.get<ApiResponse<AdAudience[]>>(
      `/api/v1/companies/${companyId}/ads/accounts/${accountId}/audiences`,
    )
    return unwrap(response.data)
  },

  async syncMetaAudiences(companyId: string, accountId: string) {
    const response = await apiClient.post<ApiResponse<MetaAudienceSync>>(
      `/api/v1/companies/${companyId}/ads/meta/accounts/${accountId}/audiences/sync`,
    )
    return unwrap(response.data)
  },

  async createMetaAudience(
    companyId: string,
    accountId: string,
    input: CreateMetaAudienceInput,
  ) {
    const response = await apiClient.post<ApiResponse<AdAudience>>(
      `/api/v1/companies/${companyId}/ads/meta/accounts/${accountId}/audiences`,
      input,
    )
    return unwrap(response.data)
  },

  async conversions(companyId: string, accountId: string) {
    const response = await apiClient.get<ApiResponse<AdConversion[]>>(
      `/api/v1/companies/${companyId}/ads/accounts/${accountId}/conversions`,
    )
    return unwrap(response.data)
  },

  async syncMetaConversions(companyId: string, accountId: string) {
    const response = await apiClient.post<ApiResponse<MetaConversionSync>>(
      `/api/v1/companies/${companyId}/ads/meta/accounts/${accountId}/conversions/sync`,
    )
    return unwrap(response.data)
  },

  async createMetaConversion(
    companyId: string,
    accountId: string,
    input: CreateMetaConversionInput,
  ) {
    const response = await apiClient.post<ApiResponse<AdConversion>>(
      `/api/v1/companies/${companyId}/ads/meta/accounts/${accountId}/conversions`,
      input,
    )
    return unwrap(response.data)
  },

  async creatives(companyId: string, accountId: string) {
    const response = await apiClient.get<ApiResponse<AdCreative[]>>(
      `/api/v1/companies/${companyId}/ads/accounts/${accountId}/creatives`,
    )
    return unwrap(response.data)
  },

  async creative(companyId: string, creativeId: string) {
    const response = await apiClient.get<ApiResponse<AdCreative>>(
      `/api/v1/companies/${companyId}/ads/creatives/${creativeId}`,
    )
    return unwrap(response.data)
  },

  async syncMetaCreatives(companyId: string, accountId: string) {
    const response = await apiClient.post<ApiResponse<MetaCreativeSync>>(
      `/api/v1/companies/${companyId}/ads/meta/accounts/${accountId}/creatives/sync`,
    )
    return unwrap(response.data)
  },

  async createMetaCreative(
    companyId: string,
    accountId: string,
    input: CreateMetaCreativeInput,
  ) {
    const response = await apiClient.post<ApiResponse<AdCreative>>(
      `/api/v1/companies/${companyId}/ads/meta/accounts/${accountId}/creatives`,
      input,
    )
    return unwrap(response.data)
  },

  async ads(companyId: string, adSetId: string) {
    const response = await apiClient.get<ApiResponse<Ad[]>>(
      `/api/v1/companies/${companyId}/ads/ad-sets/${adSetId}/ads`,
    )
    return unwrap(response.data)
  },

  async ad(companyId: string, adId: string) {
    const response = await apiClient.get<ApiResponse<Ad>>(
      `/api/v1/companies/${companyId}/ads/ads/${adId}`,
    )
    return unwrap(response.data)
  },

  async syncMetaAds(companyId: string, adSetId: string) {
    const response = await apiClient.post<ApiResponse<MetaAdSync>>(
      `/api/v1/companies/${companyId}/ads/meta/ad-sets/${adSetId}/ads/sync`,
    )
    return unwrap(response.data)
  },

  async createMetaAd(
    companyId: string,
    adSetId: string,
    input: CreateMetaAdInput,
  ) {
    const response = await apiClient.post<ApiResponse<Ad>>(
      `/api/v1/companies/${companyId}/ads/meta/ad-sets/${adSetId}/ads`,
      input,
    )
    return unwrap(response.data)
  },

  async updateMetaAd(
    companyId: string,
    adId: string,
    input: UpdateMetaAdInput,
  ) {
    const response = await apiClient.patch<ApiResponse<Ad>>(
      `/api/v1/companies/${companyId}/ads/meta/ads/${adId}`,
      input,
    )
    return unwrap(response.data)
  },

  async setMetaAdStatus(
    companyId: string,
    adId: string,
    status: 'ACTIVE' | 'PAUSED',
  ) {
    const response = await apiClient.post<ApiResponse<Ad>>(
      `/api/v1/companies/${companyId}/ads/meta/ads/${adId}/status`,
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
