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
  AdKeyword,
  AdProviderConnection,
  AdProviderInfo,
  AdRecommendations,
  AdRecommendationSummary,
  AdRecommendationLifecycleAction,
  AdCampaign,
  CreateMetaAudienceInput,
  CreateMetaCampaignInput,
  CreateMetaConversionInput,
  CreateMetaCreativeInput,
  CreateMetaAdInput,
  CreateMetaAdSetInput,
  CreateGoogleCampaignInput,
  CreateGoogleAdvancedCampaignInput,
  CreateGooglePerformanceMaxCampaignInput,
  GoogleAsset,
  GoogleAssetGroup,
  GoogleAssetGroupAsset,
  GoogleAssetGroupAssetInput,
  GoogleAssetGroupSignal,
  AddGoogleAssetGroupSignalsInput,
  GoogleCustomerMatchUploadInput,
  GoogleConversionEventUploadInput,
  GoogleDataManagerUpload,
  GoogleNativeRecommendation,
  GoogleRecommendationAction,
  GoogleMerchantAccount,
  GoogleMerchantDataSource,
  CreateGoogleMerchantDataSourceInput,
  GoogleMerchantProductPage,
  GoogleMerchantProduct,
  UpsertGoogleMerchantProductInput,
  DeleteGoogleMerchantProductInput,
  UpdateGoogleCampaignInput,
  CreateGoogleAdGroupInput,
  UpdateGoogleAdGroupInput,
  CreateGoogleKeywordInput,
  UpdateGoogleKeywordInput,
  CreateGoogleResponsiveSearchAdInput,
  CreateGoogleResponsiveDisplayAdInput,
  CreateGoogleDemandGenVideoAdInput,
  CreateGoogleDemandGenMultiAssetAdInput,
  CreateGoogleDemandGenCarouselAdInput,
  CreateGoogleShoppingProductAdInput,
  UpdateGoogleResponsiveSearchAdInput,
  CreateGoogleAudienceInput,
  CreateGoogleConversionInput,
  MetaAdsAuthorization,
  GoogleAdsAuthorization,
  MetaAdsResourceSync,
  MetaAudienceSync,
  MetaCampaignSync,
  MetaConversionSync,
  MetaInsightBreakdownsSync,
  MetaInsightsSync,
  MetaCreativeSync,
  MetaAdSync,
  MetaAdSetSync,
  GoogleCampaignSync,
  GoogleAdGroupSync,
  GoogleKeywordSync,
  GoogleAdSync,
  GoogleAudienceSync,
  GoogleConversionSync,
  GoogleInsightsSync,
  GoogleFullSync,
  GoogleInsightBreakdownsSync,
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


  async syncGoogleAll(companyId: string, accountId: string) {
    const response = await apiClient.post<ApiResponse<GoogleFullSync>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/sync`,
    )
    return unwrap(response.data)
  },

  async syncGoogleCampaigns(companyId: string, accountId: string) {
    const response = await apiClient.post<ApiResponse<GoogleCampaignSync>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/campaigns/sync`,
    )
    return unwrap(response.data)
  },

  async createGoogleCampaign(
    companyId: string,
    accountId: string,
    input: CreateGoogleCampaignInput,
  ) {
    const response = await apiClient.post<ApiResponse<AdCampaign>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/campaigns`,
      input,
    )
    return unwrap(response.data)
  },

  async createGoogleAdvancedCampaign(
    companyId: string,
    accountId: string,
    input: CreateGoogleAdvancedCampaignInput,
  ) {
    const response = await apiClient.post<ApiResponse<AdCampaign>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/advanced-campaigns`,
      input,
    )
    return unwrap(response.data)
  },

  async createGooglePerformanceMaxCampaign(
    companyId: string,
    accountId: string,
    input: CreateGooglePerformanceMaxCampaignInput,
  ) {
    const response = await apiClient.post<ApiResponse<AdCampaign>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/performance-max-campaigns`,
      input,
    )
    return unwrap(response.data)
  },

  async googleAssets(companyId: string, accountId: string) {
    const response = await apiClient.get<ApiResponse<GoogleAsset[]>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/assets`,
    )
    return unwrap(response.data)
  },

  async googleAssetGroups(companyId: string, campaignId: string) {
    const response = await apiClient.get<ApiResponse<GoogleAssetGroup[]>>(
      `/api/v1/companies/${companyId}/ads/google/campaigns/${campaignId}/asset-groups`,
    )
    return unwrap(response.data)
  },

  async googleAssetGroupAssets(
    companyId: string,
    campaignId: string,
    assetGroupId: string,
  ) {
    const response = await apiClient.get<ApiResponse<GoogleAssetGroupAsset[]>>(
      `/api/v1/companies/${companyId}/ads/google/campaigns/${campaignId}/asset-groups/${assetGroupId}/assets`,
    )
    return unwrap(response.data)
  },

  async addGoogleAssetGroupAsset(
    companyId: string,
    campaignId: string,
    assetGroupId: string,
    input: GoogleAssetGroupAssetInput,
  ) {
    const response = await apiClient.post<ApiResponse<GoogleAssetGroupAsset[]>>(
      `/api/v1/companies/${companyId}/ads/google/campaigns/${campaignId}/asset-groups/${assetGroupId}/assets`,
      input,
    )
    return unwrap(response.data)
  },

  async removeGoogleAssetGroupAsset(
    companyId: string,
    campaignId: string,
    assetGroupId: string,
    resourceName: string,
  ) {
    const response = await apiClient.post<ApiResponse<GoogleAssetGroupAsset[]>>(
      `/api/v1/companies/${companyId}/ads/google/campaigns/${campaignId}/asset-groups/${assetGroupId}/assets:remove`,
      { resourceName },
    )
    return unwrap(response.data)
  },

  async googleAssetGroupSignals(
    companyId: string,
    campaignId: string,
    assetGroupId: string,
  ) {
    const response = await apiClient.get<ApiResponse<GoogleAssetGroupSignal[]>>(
      `/api/v1/companies/${companyId}/ads/google/campaigns/${campaignId}/asset-groups/${assetGroupId}/signals`,
    )
    return unwrap(response.data)
  },

  async addGoogleAssetGroupSignals(
    companyId: string,
    campaignId: string,
    assetGroupId: string,
    input: AddGoogleAssetGroupSignalsInput,
  ) {
    const response = await apiClient.post<ApiResponse<GoogleAssetGroupSignal[]>>(
      `/api/v1/companies/${companyId}/ads/google/campaigns/${campaignId}/asset-groups/${assetGroupId}/signals`,
      input,
    )
    return unwrap(response.data)
  },

  async setGoogleAssetGroupStatus(
    companyId: string,
    campaignId: string,
    assetGroupId: string,
    status: 'ACTIVE' | 'ENABLED' | 'PAUSED',
  ) {
    const response = await apiClient.post<ApiResponse<GoogleAssetGroup>>(
      `/api/v1/companies/${companyId}/ads/google/campaigns/${campaignId}/asset-groups/${assetGroupId}/status`,
      { status },
    )
    return unwrap(response.data)
  },

  async updateGoogleCampaign(
    companyId: string,
    campaignId: string,
    input: UpdateGoogleCampaignInput,
  ) {
    const response = await apiClient.patch<ApiResponse<AdCampaign>>(
      `/api/v1/companies/${companyId}/ads/google/campaigns/${campaignId}`,
      input,
    )
    return unwrap(response.data)
  },

  async setGoogleCampaignStatus(
    companyId: string,
    campaignId: string,
    status: 'ACTIVE' | 'ENABLED' | 'PAUSED',
  ) {
    const response = await apiClient.post<ApiResponse<AdCampaign>>(
      `/api/v1/companies/${companyId}/ads/google/campaigns/${campaignId}/status`,
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


  async syncGoogleAdGroups(companyId: string, campaignId: string) {
    const response = await apiClient.post<ApiResponse<GoogleAdGroupSync>>(
      `/api/v1/companies/${companyId}/ads/google/campaigns/${campaignId}/ad-groups/sync`,
    )
    return unwrap(response.data)
  },

  async createGoogleAdGroup(
    companyId: string,
    campaignId: string,
    input: CreateGoogleAdGroupInput,
  ) {
    const response = await apiClient.post<ApiResponse<AdSet>>(
      `/api/v1/companies/${companyId}/ads/google/campaigns/${campaignId}/ad-groups`,
      input,
    )
    return unwrap(response.data)
  },

  async updateGoogleAdGroup(
    companyId: string,
    adGroupId: string,
    input: UpdateGoogleAdGroupInput,
  ) {
    const response = await apiClient.patch<ApiResponse<AdSet>>(
      `/api/v1/companies/${companyId}/ads/google/ad-groups/${adGroupId}`,
      input,
    )
    return unwrap(response.data)
  },

  async setGoogleAdGroupStatus(
    companyId: string,
    adGroupId: string,
    status: 'ACTIVE' | 'ENABLED' | 'PAUSED',
  ) {
    const response = await apiClient.post<ApiResponse<AdSet>>(
      `/api/v1/companies/${companyId}/ads/google/ad-groups/${adGroupId}/status`,
      { status },
    )
    return unwrap(response.data)
  },

  async keywords(companyId: string, adGroupId: string) {
    const response = await apiClient.get<ApiResponse<AdKeyword[]>>(
      `/api/v1/companies/${companyId}/ads/ad-sets/${adGroupId}/keywords`,
    )
    return unwrap(response.data)
  },

  async keyword(companyId: string, keywordId: string) {
    const response = await apiClient.get<ApiResponse<AdKeyword>>(
      `/api/v1/companies/${companyId}/ads/keywords/${keywordId}`,
    )
    return unwrap(response.data)
  },

  async syncGoogleKeywords(companyId: string, adGroupId: string) {
    const response = await apiClient.post<ApiResponse<GoogleKeywordSync>>(
      `/api/v1/companies/${companyId}/ads/google/ad-groups/${adGroupId}/keywords/sync`,
    )
    return unwrap(response.data)
  },

  async createGoogleKeyword(
    companyId: string,
    adGroupId: string,
    input: CreateGoogleKeywordInput,
  ) {
    const response = await apiClient.post<ApiResponse<AdKeyword>>(
      `/api/v1/companies/${companyId}/ads/google/ad-groups/${adGroupId}/keywords`,
      input,
    )
    return unwrap(response.data)
  },

  async updateGoogleKeyword(
    companyId: string,
    keywordId: string,
    input: UpdateGoogleKeywordInput,
  ) {
    const response = await apiClient.patch<ApiResponse<AdKeyword>>(
      `/api/v1/companies/${companyId}/ads/google/keywords/${keywordId}`,
      input,
    )
    return unwrap(response.data)
  },

  async recommendationSummary(companyId: string) {
    const response = await apiClient.get<ApiResponse<AdRecommendationSummary>>(
      `/api/v1/companies/${companyId}/ads/recommendations/summary`,
    )
    return unwrap(response.data)
  },

  async googleNativeRecommendations(companyId: string, accountId: string) {
    const response = await apiClient.get<ApiResponse<GoogleNativeRecommendation[]>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/native-recommendations`,
    )
    return unwrap(response.data)
  },

  async applyGoogleNativeRecommendations(
    companyId: string,
    accountId: string,
    resourceNames: string[],
  ) {
    const response = await apiClient.post<ApiResponse<GoogleRecommendationAction>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/native-recommendations/apply`,
      { resourceNames },
    )
    return unwrap(response.data)
  },

  async dismissGoogleNativeRecommendations(
    companyId: string,
    accountId: string,
    resourceNames: string[],
  ) {
    const response = await apiClient.post<ApiResponse<GoogleRecommendationAction>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/native-recommendations/dismiss`,
      { resourceNames },
    )
    return unwrap(response.data)
  },

  async recommendations(companyId: string, accountId: string) {
    const response = await apiClient.get<ApiResponse<AdRecommendations>>(
      `/api/v1/companies/${companyId}/ads/accounts/${accountId}/recommendations`,
    )
    return unwrap(response.data)
  },

  async reconcileRecommendations(companyId: string, accountId: string) {
    const response = await apiClient.post<ApiResponse<AdRecommendations>>(
      `/api/v1/companies/${companyId}/ads/accounts/${accountId}/recommendations/reconcile`,
    )
    return unwrap(response.data)
  },

  async updateRecommendationState(
    companyId: string,
    accountId: string,
    recommendationKey: string,
    action: AdRecommendationLifecycleAction,
    snoozedUntil?: string,
  ) {
    const response = await apiClient.patch<ApiResponse<AdRecommendations>>(
      `/api/v1/companies/${companyId}/ads/accounts/${accountId}/recommendations/${encodeURIComponent(recommendationKey)}`,
      {
        action,
        ...(snoozedUntil ? { snoozedUntil } : {}),
      },
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


  async syncGoogleHealth(companyId: string, accountId: string) {
    const response = await apiClient.post<ApiResponse<AdAccountHealth>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/health/sync`,
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


  async googleMerchantAccounts(companyId: string, accountId: string) {
    const response = await apiClient.get<ApiResponse<GoogleMerchantAccount[]>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/merchant/accounts`,
    )
    return unwrap(response.data)
  },

  async googleMerchantDataSources(
    companyId: string,
    accountId: string,
    merchantId: string,
  ) {
    const response = await apiClient.get<ApiResponse<GoogleMerchantDataSource[]>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/merchant/accounts/${merchantId}/data-sources`,
    )
    return unwrap(response.data)
  },

  async createGoogleMerchantDataSource(
    companyId: string,
    accountId: string,
    merchantId: string,
    input: CreateGoogleMerchantDataSourceInput,
  ) {
    const response = await apiClient.post<ApiResponse<GoogleMerchantDataSource>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/merchant/accounts/${merchantId}/data-sources`,
      input,
    )
    return unwrap(response.data)
  },

  async deleteGoogleMerchantDataSource(
    companyId: string,
    accountId: string,
    merchantId: string,
    dataSourceId: string,
  ) {
    const response = await apiClient.delete<ApiResponse<boolean>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/merchant/accounts/${merchantId}/data-sources/${dataSourceId}`,
    )
    return unwrap(response.data)
  },

  async googleMerchantProducts(
    companyId: string,
    accountId: string,
    merchantId: string,
    pageToken?: string | null,
    pageSize = 100,
  ) {
    const response = await apiClient.get<ApiResponse<GoogleMerchantProductPage>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/merchant/accounts/${merchantId}/products`,
      { params: { pageToken: pageToken || undefined, pageSize } },
    )
    return unwrap(response.data)
  },

  async upsertGoogleMerchantProduct(
    companyId: string,
    accountId: string,
    merchantId: string,
    input: UpsertGoogleMerchantProductInput,
  ) {
    const response = await apiClient.post<ApiResponse<GoogleMerchantProduct>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/merchant/accounts/${merchantId}/products`,
      input,
    )
    return unwrap(response.data)
  },

  async deleteGoogleMerchantProduct(
    companyId: string,
    accountId: string,
    merchantId: string,
    input: DeleteGoogleMerchantProductInput,
  ) {
    const response = await apiClient.post<ApiResponse<boolean>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/merchant/accounts/${merchantId}/products:delete`,
      input,
    )
    return unwrap(response.data)
  },

  async syncGoogleBilling(companyId: string, accountId: string) {
    const response = await apiClient.post<ApiResponse<AdAccountBilling>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/billing/sync`,
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


  async syncGoogleInsights(
    companyId: string,
    accountId: string,
    from: string,
    to: string,
  ) {
    const response = await apiClient.post<ApiResponse<GoogleInsightsSync>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/insights/sync`,
      null,
      { params: { from, to } },
    )
    return unwrap(response.data)
  },

  async syncGoogleInsightBreakdowns(
    companyId: string,
    accountId: string,
    from: string,
    to: string,
  ) {
    const response = await apiClient.post<ApiResponse<GoogleInsightBreakdownsSync>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/insights/breakdowns/sync`,
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


  async syncGoogleAudiences(companyId: string, accountId: string) {
    const response = await apiClient.post<ApiResponse<GoogleAudienceSync>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/audiences/sync`,
    )
    return unwrap(response.data)
  },

  async uploadGoogleCustomerMatchMembers(
    companyId: string,
    audienceId: string,
    input: GoogleCustomerMatchUploadInput,
  ) {
    const response = await apiClient.post<ApiResponse<GoogleDataManagerUpload>>(
      `/api/v1/companies/${companyId}/ads/google/audiences/${audienceId}/members`,
      input,
    )
    return unwrap(response.data)
  },

  async createGoogleAudience(
    companyId: string,
    accountId: string,
    input: CreateGoogleAudienceInput,
  ) {
    const response = await apiClient.post<ApiResponse<AdAudience>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/audiences`,
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


  async syncGoogleConversions(companyId: string, accountId: string) {
    const response = await apiClient.post<ApiResponse<GoogleConversionSync>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/conversions/sync`,
    )
    return unwrap(response.data)
  },

  async uploadGoogleConversionEvents(
    companyId: string,
    conversionId: string,
    input: GoogleConversionEventUploadInput,
  ) {
    const response = await apiClient.post<ApiResponse<GoogleDataManagerUpload>>(
      `/api/v1/companies/${companyId}/ads/google/conversions/${conversionId}/events`,
      input,
    )
    return unwrap(response.data)
  },

  async createGoogleConversion(
    companyId: string,
    accountId: string,
    input: CreateGoogleConversionInput,
  ) {
    const response = await apiClient.post<ApiResponse<AdConversion>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/conversions`,
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

  async accountAds(companyId: string, accountId: string) {
    const response = await apiClient.get<ApiResponse<Ad[]>>(
      `/api/v1/companies/${companyId}/ads/accounts/${accountId}/ads`,
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


  async syncGoogleAds(companyId: string, adGroupId: string) {
    const response = await apiClient.post<ApiResponse<GoogleAdSync>>(
      `/api/v1/companies/${companyId}/ads/google/ad-groups/${adGroupId}/ads/sync`,
    )
    return unwrap(response.data)
  },

  async createGoogleResponsiveDisplayAd(
    companyId: string,
    adGroupId: string,
    input: CreateGoogleResponsiveDisplayAdInput,
  ) {
    const response = await apiClient.post<ApiResponse<Ad>>(
      `/api/v1/companies/${companyId}/ads/google/ad-groups/${adGroupId}/display-ads`,
      input,
    )
    return unwrap(response.data)
  },

  async createGoogleDemandGenVideoAd(
    companyId: string,
    adGroupId: string,
    input: CreateGoogleDemandGenVideoAdInput,
  ) {
    const response = await apiClient.post<ApiResponse<Ad>>(
      `/api/v1/companies/${companyId}/ads/google/ad-groups/${adGroupId}/demand-gen-video-ads`,
      input,
    )
    return unwrap(response.data)
  },

  async createGoogleDemandGenMultiAssetAd(
    companyId: string,
    adGroupId: string,
    input: CreateGoogleDemandGenMultiAssetAdInput,
  ) {
    const response = await apiClient.post<ApiResponse<Ad>>(
      `/api/v1/companies/${companyId}/ads/google/ad-groups/${adGroupId}/demand-gen-multi-asset-ads`,
      input,
    )
    return unwrap(response.data)
  },

  async createGoogleDemandGenCarouselAd(
    companyId: string,
    adGroupId: string,
    input: CreateGoogleDemandGenCarouselAdInput,
  ) {
    const response = await apiClient.post<ApiResponse<Ad>>(
      `/api/v1/companies/${companyId}/ads/google/ad-groups/${adGroupId}/demand-gen-carousel-ads`,
      input,
    )
    return unwrap(response.data)
  },

  async createGoogleShoppingProductAd(
    companyId: string,
    adGroupId: string,
    input: CreateGoogleShoppingProductAdInput,
  ) {
    const response = await apiClient.post<ApiResponse<Ad>>(
      `/api/v1/companies/${companyId}/ads/google/ad-groups/${adGroupId}/shopping-product-ads`,
      input,
    )
    return unwrap(response.data)
  },

  async createGoogleResponsiveSearchAd(
    companyId: string,
    adGroupId: string,
    input: CreateGoogleResponsiveSearchAdInput,
  ) {
    const response = await apiClient.post<ApiResponse<Ad>>(
      `/api/v1/companies/${companyId}/ads/google/ad-groups/${adGroupId}/ads`,
      input,
    )
    return unwrap(response.data)
  },

  async updateGoogleResponsiveSearchAd(
    companyId: string,
    adId: string,
    input: UpdateGoogleResponsiveSearchAdInput,
  ) {
    const response = await apiClient.patch<ApiResponse<Ad>>(
      `/api/v1/companies/${companyId}/ads/google/ads/${adId}`,
      input,
    )
    return unwrap(response.data)
  },

  async setGoogleAdStatus(
    companyId: string,
    adId: string,
    status: 'ACTIVE' | 'ENABLED' | 'PAUSED',
  ) {
    const response = await apiClient.post<ApiResponse<Ad>>(
      `/api/v1/companies/${companyId}/ads/google/ads/${adId}/status`,
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

  async authorizeGoogle(companyId: string, returnTo = '/ad-center') {
    const response = await apiClient.get<ApiResponse<GoogleAdsAuthorization>>(
      `/api/v1/companies/${companyId}/ads/google/authorize`,
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
