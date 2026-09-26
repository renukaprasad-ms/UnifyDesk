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
  AdFeatureCapabilityInfo,
  ReachlyoCampaign,
  ReachlyoCampaignSummary,
  ReachlyoCampaignAnalytics,
  ReachlyoCampaignRecommendations,
  ReachlyoCampaignAnalyticsSync,
  CreateReachlyoCampaignInput,
  UpdateReachlyoCampaignInput,
  CreateReachlyoCampaignChannelInput,
  UpdateReachlyoCampaignChannelInput,
  UpsertReachlyoBudgetPlanInput,
  ProviderCampaignPlan,
  CompileReachlyoCampaignInput,
  LaunchRun,
  StartCampaignLaunchInput,
  ReachlyoCreativePack,
  ReachlyoCreativeVariant,
  CreateReachlyoCreativePackInput,
  UpdateReachlyoCreativePackInput,
  CreateReachlyoCreativeVariantInput,
  UpdateReachlyoCreativeVariantInput,
  ReachlyoAudienceDefinition,
  CreateReachlyoAudienceDefinitionInput,
  UpdateReachlyoAudienceDefinitionInput,
  ReachlyoConversionGoalMapping,
  UpsertReachlyoConversionGoalMappingInput,
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
  GoogleAssetGenerationTextInput,
  GoogleAssetGenerationTextResult,
  GoogleAssetGenerationImageInput,
  GoogleAssetGenerationImageResult,
  GoogleReusableAsset,
  CreateGoogleReusableAssetInput,
  CreateGoogleAdvancedReusableAssetInput,
  GoogleAssetLinkLevel,
  GoogleReusableAssetLink,
  GoogleUrlOptions,
  UpdateGoogleUrlOptionsInput,
  GooglePerformanceMaxAutomation,
  UpdateGooglePerformanceMaxAutomationInput,
  GoogleAssetGroupUrlOptions,
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
  GoogleDemandGenTargeting,
  GoogleDemandGenTargetingInput,
  GoogleDemandGenAudienceOption,
  GoogleDemandGenChannelPerformance,
  CreateGoogleDemandGenMultiAssetAdInput,
  CreateGoogleDemandGenCarouselAdInput,
  CreateGoogleShoppingProductAdInput,
  UpdateGoogleResponsiveSearchAdInput,
  CreateGoogleAudienceInput,
  CreateGoogleConversionInput,
  MetaAdsAuthorization,
  GoogleAdsAuthorization,
  MetaAdsResourceSync,
  MetaGeoLocationSuggestion,
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
  GoogleNegativeKeyword,
  GoogleSearchTermReport,
  GoogleAiMaxCombinationReport,
  GoogleBrandSuggestion,
  GoogleKeywordPlannerIdeasInput,
  GoogleKeywordPlannerHistoricalInput,
  GoogleKeywordPlannerResponse,
  GoogleKeywordForecastInput,
  GoogleKeywordForecastResponse,
  GoogleNegativeKeywordInput,
  GoogleNegativeKeywordList,
  GoogleNegativeKeywordMutation,
  CreateGoogleNegativeKeywordListInput,
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
  async generateGoogleTextAssets(
    companyId: string,
    accountId: string,
    input: GoogleAssetGenerationTextInput,
  ) {
    const response = await apiClient.post<ApiResponse<GoogleAssetGenerationTextResult>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/asset-generation:text`,
      input,
    )
    return unwrap(response.data)
  },

  async generateGoogleImageAssets(
    companyId: string,
    accountId: string,
    input: GoogleAssetGenerationImageInput,
  ) {
    const response = await apiClient.post<ApiResponse<GoogleAssetGenerationImageResult>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/asset-generation:images`,
      input,
    )
    return unwrap(response.data)
  },

  async googleReusableAssets(companyId: string, accountId: string) {
    const response = await apiClient.get<ApiResponse<GoogleReusableAsset[]>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/reusable-assets`,
    )
    return unwrap(response.data)
  },

  async createGoogleReusableAsset(
    companyId: string,
    accountId: string,
    input: CreateGoogleReusableAssetInput,
  ) {
    const response = await apiClient.post<ApiResponse<GoogleReusableAsset>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/reusable-assets`,
      input,
    )
    return unwrap(response.data)
  },

  async createGoogleAdvancedReusableAsset(
    companyId: string,
    accountId: string,
    input: CreateGoogleAdvancedReusableAssetInput,
  ) {
    const response = await apiClient.post<ApiResponse<GoogleReusableAsset>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/reusable-assets/advanced`,
      input,
    )
    return unwrap(response.data)
  },

  async googleReusableAssetLinks(
    companyId: string,
    accountId: string,
    level: GoogleAssetLinkLevel,
    targetId?: string | null,
  ) {
    const response = await apiClient.get<ApiResponse<GoogleReusableAssetLink[]>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/reusable-assets/links`,
      { params: { level, ...(targetId ? { targetId } : {}) } },
    )
    return unwrap(response.data)
  },

  async attachGoogleReusableAsset(
    companyId: string,
    accountId: string,
    assetId: string,
    level: GoogleAssetLinkLevel,
    targetId?: string | null,
    fieldType?: string | null,
  ) {
    const response = await apiClient.post<ApiResponse<GoogleReusableAssetLink>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/reusable-assets/${assetId}/links`,
      { level, targetId: targetId ?? null, fieldType: fieldType ?? null },
    )
    return unwrap(response.data)
  },

  async detachGoogleReusableAsset(
    companyId: string,
    accountId: string,
    assetId: string,
    level: GoogleAssetLinkLevel,
    targetId?: string | null,
    fieldType?: string | null,
  ) {
    const response = await apiClient.post<ApiResponse<boolean>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/reusable-assets/${assetId}/links:detach`,
      { level, targetId: targetId ?? null, fieldType: fieldType ?? null },
    )
    return unwrap(response.data)
  },

  async googleUrlOptions(
    companyId: string,
    accountId: string,
    level: GoogleAssetLinkLevel,
    targetId?: string | null,
  ) {
    const suffix =
      level === 'CUSTOMER'
        ? `accounts/${accountId}/url-options`
        : level === 'CAMPAIGN'
          ? `accounts/${accountId}/campaigns/${targetId}/url-options`
          : `accounts/${accountId}/ad-groups/${targetId}/url-options`
    const response = await apiClient.get<ApiResponse<GoogleUrlOptions>>(
      `/api/v1/companies/${companyId}/ads/google/${suffix}`,
    )
    return unwrap(response.data)
  },

  async updateGoogleUrlOptions(
    companyId: string,
    accountId: string,
    level: GoogleAssetLinkLevel,
    targetId: string | null | undefined,
    input: UpdateGoogleUrlOptionsInput,
  ) {
    const suffix =
      level === 'CUSTOMER'
        ? `accounts/${accountId}/url-options`
        : level === 'CAMPAIGN'
          ? `accounts/${accountId}/campaigns/${targetId}/url-options`
          : `accounts/${accountId}/ad-groups/${targetId}/url-options`
    const response = await apiClient.put<ApiResponse<GoogleUrlOptions>>(
      `/api/v1/companies/${companyId}/ads/google/${suffix}`,
      input,
    )
    return unwrap(response.data)
  },

  async providers(companyId: string) {
    const response = await apiClient.get<ApiResponse<AdProviderInfo[]>>(
      `/api/v1/companies/${companyId}/ads/providers`,
    )
    return unwrap(response.data)
  },

  async accountCapabilities(companyId: string, accountId: string) {
    const response = await apiClient.get<ApiResponse<AdFeatureCapabilityInfo[]>>(
      `/api/v1/companies/${companyId}/ads/accounts/${accountId}/capabilities`,
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

  async reachlyoCampaigns(companyId: string, page = 0, size = 50) {
    const response = await apiClient.get<ApiResponse<PageResponse<ReachlyoCampaignSummary>>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns`,
      { params: { page, size } },
    )
    return unwrap(response.data)
  },

  async reachlyoCampaign(companyId: string, campaignId: string) {
    const response = await apiClient.get<ApiResponse<ReachlyoCampaign>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}`,
    )
    return unwrap(response.data)
  },

  async reachlyoCampaignRecommendations(
    companyId: string,
    campaignId: string,
  ) {
    const response = await apiClient.get<ApiResponse<ReachlyoCampaignRecommendations>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/recommendations`,
    )
    return unwrap(response.data)
  },

  async reachlyoCampaignAnalytics(
    companyId: string,
    campaignId: string,
    from: string,
    to: string,
  ) {
    const response = await apiClient.get<ApiResponse<ReachlyoCampaignAnalytics>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/analytics`,
      { params: { from, to } },
    )
    return unwrap(response.data)
  },

  async syncReachlyoCampaignAnalytics(
    companyId: string,
    campaignId: string,
    from: string,
    to: string,
  ) {
    const response = await apiClient.post<ApiResponse<ReachlyoCampaignAnalyticsSync>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/analytics/sync`,
      null,
      { params: { from, to } },
    )
    return unwrap(response.data)
  },

  async createReachlyoCampaign(companyId: string, input: CreateReachlyoCampaignInput) {
    const response = await apiClient.post<ApiResponse<ReachlyoCampaign>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns`,
      input,
    )
    return unwrap(response.data)
  },

  async updateReachlyoCampaign(
    companyId: string,
    campaignId: string,
    input: UpdateReachlyoCampaignInput,
  ) {
    const response = await apiClient.patch<ApiResponse<ReachlyoCampaign>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}`,
      input,
    )
    return unwrap(response.data)
  },

  async archiveReachlyoCampaign(companyId: string, campaignId: string) {
    const response = await apiClient.post<ApiResponse<ReachlyoCampaign>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/archive`,
    )
    return unwrap(response.data)
  },

  async addReachlyoCampaignChannel(
    companyId: string,
    campaignId: string,
    input: CreateReachlyoCampaignChannelInput,
  ) {
    const response = await apiClient.post<ApiResponse<ReachlyoCampaign>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/channels`,
      input,
    )
    return unwrap(response.data)
  },

  async updateReachlyoCampaignChannel(
    companyId: string,
    campaignId: string,
    channelId: string,
    input: UpdateReachlyoCampaignChannelInput,
  ) {
    const response = await apiClient.patch<ApiResponse<ReachlyoCampaign>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/channels/${channelId}`,
      input,
    )
    return unwrap(response.data)
  },

  async removeReachlyoCampaignChannel(
    companyId: string,
    campaignId: string,
    channelId: string,
  ) {
    const response = await apiClient.delete<ApiResponse<ReachlyoCampaign>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/channels/${channelId}`,
    )
    return unwrap(response.data)
  },

  async upsertReachlyoBudgetPlan(
    companyId: string,
    campaignId: string,
    input: UpsertReachlyoBudgetPlanInput,
  ) {
    const response = await apiClient.put<ApiResponse<ReachlyoCampaign>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/budget`,
      input,
    )
    return unwrap(response.data)
  },

  async removeReachlyoBudgetPlan(companyId: string, campaignId: string) {
    const response = await apiClient.delete<ApiResponse<ReachlyoCampaign>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/budget`,
    )
    return unwrap(response.data)
  },

  async compileReachlyoCampaignChannel(
    companyId: string,
    campaignId: string,
    channelId: string,
    input: CompileReachlyoCampaignInput,
  ) {
    const response = await apiClient.post<ApiResponse<ProviderCampaignPlan>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/channels/${channelId}/compile`,
      input,
    )
    return unwrap(response.data)
  },

  async prepareReachlyoCampaignLaunch(
    companyId: string,
    campaignId: string,
    input: StartCampaignLaunchInput,
  ) {
    const response = await apiClient.post<ApiResponse<LaunchRun>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/launch-runs/prepare`,
      input,
    )
    return unwrap(response.data)
  },

  async reachlyoCampaignLaunchRuns(companyId: string, campaignId: string) {
    const response = await apiClient.get<ApiResponse<LaunchRun[]>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/launch-runs`,
    )
    return unwrap(response.data)
  },

  async reachlyoCampaignLaunchRun(
    companyId: string,
    campaignId: string,
    runId: string,
  ) {
    const response = await apiClient.get<ApiResponse<LaunchRun>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/launch-runs/${runId}`,
    )
    return unwrap(response.data)
  },

  async executeReachlyoCampaignLaunch(
    companyId: string,
    campaignId: string,
    runId: string,
  ) {
    const response = await apiClient.post<ApiResponse<LaunchRun>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/launch-runs/${runId}/execute`,
    )
    return unwrap(response.data)
  },

  async retryReachlyoCampaignLaunch(
    companyId: string,
    campaignId: string,
    runId: string,
  ) {
    const response = await apiClient.post<ApiResponse<LaunchRun>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/launch-runs/${runId}/retry`,
    )
    return unwrap(response.data)
  },

  async reachlyoCreativePacks(companyId: string, campaignId: string) {
    const response = await apiClient.get<ApiResponse<ReachlyoCreativePack[]>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/creative-packs`,
    )
    return unwrap(response.data)
  },

  async createReachlyoCreativePack(
    companyId: string,
    campaignId: string,
    input: CreateReachlyoCreativePackInput,
  ) {
    const response = await apiClient.post<ApiResponse<ReachlyoCreativePack>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/creative-packs`,
      input,
    )
    return unwrap(response.data)
  },

  async updateReachlyoCreativePack(
    companyId: string,
    campaignId: string,
    packId: string,
    input: UpdateReachlyoCreativePackInput,
  ) {
    const response = await apiClient.patch<ApiResponse<ReachlyoCreativePack>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/creative-packs/${packId}`,
      input,
    )
    return unwrap(response.data)
  },

  async deleteReachlyoCreativePack(companyId: string, campaignId: string, packId: string) {
    await apiClient.delete(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/creative-packs/${packId}`,
    )
  },

  async createReachlyoCreativeVariant(
    companyId: string,
    campaignId: string,
    packId: string,
    input: CreateReachlyoCreativeVariantInput,
  ) {
    const response = await apiClient.post<ApiResponse<ReachlyoCreativeVariant>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/creative-packs/${packId}/variants`,
      input,
    )
    return unwrap(response.data)
  },

  async updateReachlyoCreativeVariant(
    companyId: string,
    campaignId: string,
    packId: string,
    variantId: string,
    input: UpdateReachlyoCreativeVariantInput,
  ) {
    const response = await apiClient.patch<ApiResponse<ReachlyoCreativeVariant>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/creative-packs/${packId}/variants/${variantId}`,
      input,
    )
    return unwrap(response.data)
  },

  async deleteReachlyoCreativeVariant(
    companyId: string,
    campaignId: string,
    packId: string,
    variantId: string,
  ) {
    await apiClient.delete(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/creative-packs/${packId}/variants/${variantId}`,
    )
  },

  async reachlyoAudienceDefinitions(companyId: string, campaignId: string) {
    const response = await apiClient.get<ApiResponse<ReachlyoAudienceDefinition[]>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/audience-definitions`,
    )
    return unwrap(response.data)
  },

  async createReachlyoAudienceDefinition(
    companyId: string,
    campaignId: string,
    input: CreateReachlyoAudienceDefinitionInput,
  ) {
    const response = await apiClient.post<ApiResponse<ReachlyoAudienceDefinition>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/audience-definitions`,
      input,
    )
    return unwrap(response.data)
  },

  async updateReachlyoAudienceDefinition(
    companyId: string,
    campaignId: string,
    definitionId: string,
    input: UpdateReachlyoAudienceDefinitionInput,
  ) {
    const response = await apiClient.patch<ApiResponse<ReachlyoAudienceDefinition>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/audience-definitions/${definitionId}`,
      input,
    )
    return unwrap(response.data)
  },

  async deleteReachlyoAudienceDefinition(
    companyId: string,
    campaignId: string,
    definitionId: string,
  ) {
    await apiClient.delete(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/audience-definitions/${definitionId}`,
    )
  },

  async reachlyoConversionGoalMappings(companyId: string, campaignId: string) {
    const response = await apiClient.get<ApiResponse<ReachlyoConversionGoalMapping[]>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/conversion-goal-mappings`,
    )
    return unwrap(response.data)
  },

  async upsertReachlyoConversionGoalMapping(
    companyId: string,
    campaignId: string,
    input: UpsertReachlyoConversionGoalMappingInput,
  ) {
    const response = await apiClient.put<ApiResponse<ReachlyoConversionGoalMapping>>(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/conversion-goal-mappings`,
      input,
    )
    return unwrap(response.data)
  },

  async deleteReachlyoConversionGoalMapping(
    companyId: string,
    campaignId: string,
    mappingId: string,
  ) {
    await apiClient.delete(
      `/api/v1/companies/${companyId}/ads/reachlyo-campaigns/${campaignId}/conversion-goal-mappings/${mappingId}`,
    )
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

  async metaGeoLocations(
    companyId: string,
    accountId: string,
    query: string,
    types: Array<'COUNTRY' | 'REGION' | 'CITY' | 'ZIP'>,
    countryCode?: string,
    limit = 20,
  ) {
    const response = await apiClient.get<ApiResponse<MetaGeoLocationSuggestion[]>>(
      `/api/v1/companies/${companyId}/ads/meta/accounts/${accountId}/geo-targeting/search`,
      {
        params: {
          q: query || undefined,
          types: types.join(','),
          countryCode: countryCode || undefined,
          limit,
        },
      },
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

  async googlePerformanceMaxAutomation(companyId: string, campaignId: string) {
    const response = await apiClient.get<ApiResponse<GooglePerformanceMaxAutomation>>(
      `/api/v1/companies/${companyId}/ads/google/campaigns/${campaignId}/performance-max/automation`,
    )
    return unwrap(response.data)
  },

  async updateGooglePerformanceMaxAutomation(
    companyId: string,
    campaignId: string,
    input: UpdateGooglePerformanceMaxAutomationInput,
  ) {
    const response = await apiClient.put<ApiResponse<GooglePerformanceMaxAutomation>>(
      `/api/v1/companies/${companyId}/ads/google/campaigns/${campaignId}/performance-max/automation`,
      input,
    )
    return unwrap(response.data)
  },

  async googleAssetGroupUrlOptions(
    companyId: string,
    campaignId: string,
    assetGroupId: string,
  ) {
    const response = await apiClient.get<ApiResponse<GoogleAssetGroupUrlOptions>>(
      `/api/v1/companies/${companyId}/ads/google/campaigns/${campaignId}/asset-groups/${assetGroupId}/url-options`,
    )
    return unwrap(response.data)
  },

  async updateGoogleAssetGroupUrlOptions(
    companyId: string,
    campaignId: string,
    assetGroupId: string,
    input: UpdateGoogleUrlOptionsInput,
  ) {
    const response = await apiClient.put<ApiResponse<GoogleAssetGroupUrlOptions>>(
      `/api/v1/companies/${companyId}/ads/google/campaigns/${campaignId}/asset-groups/${assetGroupId}/url-options`,
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

  async googleKeywordPlannerIdeas(
    companyId: string,
    accountId: string,
    input: GoogleKeywordPlannerIdeasInput,
  ) {
    const response = await apiClient.post<ApiResponse<GoogleKeywordPlannerResponse>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/keyword-planner/ideas`,
      input,
    )
    return unwrap(response.data)
  },

  async googleKeywordForecast(
    companyId: string,
    accountId: string,
    input: GoogleKeywordForecastInput,
  ) {
    const response = await apiClient.post<ApiResponse<GoogleKeywordForecastResponse>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/keyword-planner/forecast`,
      input,
    )
    return unwrap(response.data)
  },

  async googleKeywordPlannerHistoricalMetrics(
    companyId: string,
    accountId: string,
    input: GoogleKeywordPlannerHistoricalInput,
  ) {
    const response = await apiClient.post<ApiResponse<GoogleKeywordPlannerResponse>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/keyword-planner/historical-metrics`,
      input,
    )
    return unwrap(response.data)
  },

  async suggestGoogleAiMaxBrands(
    companyId: string,
    accountId: string,
    prefix: string,
    selectedBrandIds: string[] = [],
  ) {
    const response = await apiClient.get<ApiResponse<GoogleBrandSuggestion[]>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/ai-max/brands/suggest`,
      {
        params: {
          prefix,
          ...(selectedBrandIds.length
            ? { selectedBrandIds: selectedBrandIds.join(',') }
            : {}),
        },
      },
    )
    return unwrap(response.data)
  },

  async googleAiMaxReport(
    companyId: string,
    campaignId: string,
    from: string,
    to: string,
  ) {
    const response = await apiClient.get<ApiResponse<GoogleAiMaxCombinationReport>>(
      `/api/v1/companies/${companyId}/ads/google/campaigns/${campaignId}/ai-max/report`,
      { params: { from, to } },
    )
    return unwrap(response.data)
  },

  async googleSearchTerms(
    companyId: string,
    campaignId: string,
    from: string,
    to: string,
  ) {
    const response = await apiClient.get<ApiResponse<GoogleSearchTermReport>>(
      `/api/v1/companies/${companyId}/ads/google/campaigns/${campaignId}/search-terms`,
      { params: { from, to } },
    )
    return unwrap(response.data)
  },

  async googleCampaignNegativeKeywords(companyId: string, campaignId: string) {
    const response = await apiClient.get<ApiResponse<GoogleNegativeKeyword[]>>(
      `/api/v1/companies/${companyId}/ads/google/campaigns/${campaignId}/negative-keywords`,
    )
    return unwrap(response.data)
  },

  async addGoogleCampaignNegativeKeywords(
    companyId: string,
    campaignId: string,
    keywords: GoogleNegativeKeywordInput[],
  ) {
    const response = await apiClient.post<ApiResponse<GoogleNegativeKeywordMutation>>(
      `/api/v1/companies/${companyId}/ads/google/campaigns/${campaignId}/negative-keywords`,
      { keywords },
    )
    return unwrap(response.data)
  },

  async removeGoogleCampaignNegativeKeyword(
    companyId: string,
    campaignId: string,
    criterionId: string,
  ) {
    const response = await apiClient.delete<ApiResponse<boolean>>(
      `/api/v1/companies/${companyId}/ads/google/campaigns/${campaignId}/negative-keywords/${criterionId}`,
    )
    return unwrap(response.data)
  },

  async googleNegativeKeywordLists(
    companyId: string,
    accountId: string,
    campaignId?: string,
  ) {
    const response = await apiClient.get<ApiResponse<GoogleNegativeKeywordList[]>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/negative-keyword-lists`,
      { params: campaignId ? { campaignId } : undefined },
    )
    return unwrap(response.data)
  },

  async createGoogleNegativeKeywordList(
    companyId: string,
    accountId: string,
    input: CreateGoogleNegativeKeywordListInput,
  ) {
    const response = await apiClient.post<ApiResponse<GoogleNegativeKeywordList>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/negative-keyword-lists`,
      input,
    )
    return unwrap(response.data)
  },

  async googleNegativeKeywordList(
    companyId: string,
    accountId: string,
    sharedSetId: string,
    campaignId?: string,
  ) {
    const response = await apiClient.get<ApiResponse<GoogleNegativeKeywordList>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/negative-keyword-lists/${sharedSetId}`,
      { params: campaignId ? { campaignId } : undefined },
    )
    return unwrap(response.data)
  },

  async addGoogleNegativeKeywordListMembers(
    companyId: string,
    accountId: string,
    sharedSetId: string,
    keywords: GoogleNegativeKeywordInput[],
  ) {
    const response = await apiClient.post<ApiResponse<GoogleNegativeKeywordMutation>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/negative-keyword-lists/${sharedSetId}/members`,
      { keywords },
    )
    return unwrap(response.data)
  },

  async removeGoogleNegativeKeywordListMember(
    companyId: string,
    accountId: string,
    sharedSetId: string,
    criterionId: string,
  ) {
    const response = await apiClient.delete<ApiResponse<boolean>>(
      `/api/v1/companies/${companyId}/ads/google/accounts/${accountId}/negative-keyword-lists/${sharedSetId}/members/${criterionId}`,
    )
    return unwrap(response.data)
  },

  async attachGoogleNegativeKeywordList(
    companyId: string,
    campaignId: string,
    sharedSetId: string,
  ) {
    const response = await apiClient.post<ApiResponse<GoogleNegativeKeywordList>>(
      `/api/v1/companies/${companyId}/ads/google/campaigns/${campaignId}/negative-keyword-lists/${sharedSetId}`,
    )
    return unwrap(response.data)
  },

  async detachGoogleNegativeKeywordList(
    companyId: string,
    campaignId: string,
    sharedSetId: string,
  ) {
    const response = await apiClient.delete<ApiResponse<GoogleNegativeKeywordList>>(
      `/api/v1/companies/${companyId}/ads/google/campaigns/${campaignId}/negative-keyword-lists/${sharedSetId}`,
    )
    return unwrap(response.data)
  },

  async removeGoogleKeyword(companyId: string, keywordId: string) {
    const response = await apiClient.delete<ApiResponse<boolean>>(
      `/api/v1/companies/${companyId}/ads/google/keywords/${keywordId}`,
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

  async googleDemandGenTargeting(companyId: string, adGroupId: string) {
    const response = await apiClient.get<ApiResponse<GoogleDemandGenTargeting>>(
      `/api/v1/companies/${companyId}/ads/google/ad-groups/${adGroupId}/demand-gen-targeting`,
    )
    return unwrap(response.data)
  },

  async updateGoogleDemandGenTargeting(
    companyId: string,
    adGroupId: string,
    input: GoogleDemandGenTargetingInput,
  ) {
    const response = await apiClient.put<ApiResponse<GoogleDemandGenTargeting>>(
      `/api/v1/companies/${companyId}/ads/google/ad-groups/${adGroupId}/demand-gen-targeting`,
      input,
    )
    return unwrap(response.data)
  },

  async googleDemandGenAudiences(companyId: string, adGroupId: string) {
    const response = await apiClient.get<ApiResponse<GoogleDemandGenAudienceOption[]>>(
      `/api/v1/companies/${companyId}/ads/google/ad-groups/${adGroupId}/demand-gen-audiences`,
    )
    return unwrap(response.data)
  },

  async googleDemandGenChannelPerformance(
    companyId: string,
    adGroupId: string,
    from: string,
    to: string,
  ) {
    const response = await apiClient.get<ApiResponse<GoogleDemandGenChannelPerformance[]>>(
      `/api/v1/companies/${companyId}/ads/google/ad-groups/${adGroupId}/demand-gen-channel-performance`,
      { params: { from, to } },
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
