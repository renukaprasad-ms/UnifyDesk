export type AdProvider = 'GOOGLE_ADS' | 'META_ADS'

export type ReachlyoCampaignObjective =
  | 'SALES'
  | 'LEADS'
  | 'TRAFFIC'
  | 'AWARENESS'
  | 'APP_PROMOTION'
  | 'LOCAL_PROMOTION'
  | 'RETARGETING'
  | 'PRODUCT_SALES'

export type CampaignBusinessGoal =
  | 'PURCHASE'
  | 'LEAD'
  | 'QUALIFIED_LEAD'
  | 'SIGN_UP'
  | 'PHONE_CALL'
  | 'WHATSAPP_CONVERSATION'
  | 'APP_INSTALL'
  | 'TRAFFIC'
  | 'AWARENESS'

export type ReachlyoCampaignStatus =
  | 'DRAFT'
  | 'VALIDATING'
  | 'READY'
  | 'DEPLOYING'
  | 'PARTIALLY_DEPLOYED'
  | 'PAUSED'
  | 'ACTIVE'
  | 'PARTIAL_FAILURE'
  | 'FAILED'
  | 'COMPLETED'
  | 'ARCHIVED'

export type CampaignChannelType =
  | 'GOOGLE_SEARCH'
  | 'GOOGLE_PERFORMANCE_MAX'
  | 'GOOGLE_DEMAND_GEN'
  | 'GOOGLE_DISPLAY'
  | 'GOOGLE_SHOPPING'
  | 'META_ADS'

export type CampaignChannelStatus =
  | 'DRAFT'
  | 'READY'
  | 'DEPLOYING'
  | 'DEPLOYED'
  | 'FAILED'
  | 'DISABLED'

export type ProviderDeploymentStatus =
  | 'PENDING'
  | 'DEPLOYING'
  | 'PAUSED'
  | 'ACTIVE'
  | 'PARTIAL_FAILURE'
  | 'FAILED'
  | 'ARCHIVED'

export type BudgetAllocationMode = 'AUTOMATIC' | 'PERCENTAGE' | 'FIXED'
export type BudgetPeriod = 'DAILY' | 'TOTAL'

export type ReachlyoBudgetPlan = {
  id: string
  campaignId: string
  currency: string
  totalBudgetMinor: number
  budgetPeriod: BudgetPeriod
  allocationMode: BudgetAllocationMode
  createdAt: string
  updatedAt: string
}

export type ReachlyoCampaignChannel = {
  id: string
  campaignId: string
  provider: AdProvider
  channelType: CampaignChannelType
  enabled: boolean
  budgetAllocationMinor?: number | null
  allocationPercent?: number | null
  providerOverrideJson?: string | null
  status: CampaignChannelStatus
  createdAt: string
  updatedAt: string
}

export type ReachlyoProviderDeployment = {
  id: string
  campaignId: string
  channelId: string
  provider: AdProvider
  adAccountId: string
  localCampaignId?: string | null
  externalCampaignId?: string | null
  providerStatus?: string | null
  deploymentStatus: ProviderDeploymentStatus
  lastErrorCode?: string | null
  lastErrorMessage?: string | null
  lastSyncAt?: string | null
  createdAt: string
  updatedAt: string
}

export type ReachlyoCampaignSummary = {
  id: string
  companyId: string
  name: string
  objective: ReachlyoCampaignObjective
  businessGoal: CampaignBusinessGoal
  landingPageUrl?: string | null
  status: ReachlyoCampaignStatus
  currency: string
  startAt?: string | null
  endAt?: string | null
  createdByUserId: string
  createdAt: string
  updatedAt: string
}

export type ReachlyoCampaign = ReachlyoCampaignSummary & {
  budgetPlan?: ReachlyoBudgetPlan | null
  channels: ReachlyoCampaignChannel[]
  deployments: ReachlyoProviderDeployment[]
}

export type ReachlyoCampaignAnalyticsSummary = {
  spend: number
  impressions: number
  clicks: number
  ctr?: number | null
  conversions: number
  conversionValue: number
  cpa?: number | null
  roas?: number | null
  leads: number
  purchases: number
}

export type ReachlyoCampaignAnalyticsDailyPoint = {
  date: string
  spend: number
  impressions: number
  clicks: number
  conversions: number
  conversionValue: number
}

export type ReachlyoCampaignAnalyticsDeployment = {
  deploymentId: string
  channelId: string
  provider: AdProvider
  channelType?: CampaignChannelType | null
  adAccountId: string
  localCampaignId?: string | null
  externalCampaignId?: string | null
  providerStatus?: string | null
  deploymentStatus: ProviderDeploymentStatus
  summary: ReachlyoCampaignAnalyticsSummary
  spendSharePercent: number
  exactRangeAvailable: boolean
  lastSyncedAt?: string | null
}

export type ReachlyoCampaignRecommendationItem = {
  key: string
  severity: AdRecommendationSeverity
  category: AdRecommendationCategory
  source: AdRecommendationSource
  provider?: AdProvider | null
  adAccountId?: string | null
  deploymentId?: string | null
  localCampaignId?: string | null
  title: string
  description: string
  actionLabel?: string | null
  actionPath?: string | null
  evidence: Record<string, unknown>
  providerRecommendationResourceName?: string | null
}

export type ReachlyoCampaignRecommendations = {
  campaignId: string
  criticalCount: number
  warningCount: number
  opportunityCount: number
  infoCount: number
  generatedAt: string
  analyticsLastSyncAt?: string | null
  items: ReachlyoCampaignRecommendationItem[]
}

export type ReachlyoCampaignAnalytics = {
  campaignId: string
  currency: string
  from: string
  to: string
  summary: ReachlyoCampaignAnalyticsSummary
  daily: ReachlyoCampaignAnalyticsDailyPoint[]
  deployments: ReachlyoCampaignAnalyticsDeployment[]
  exactRangeAvailable: boolean
  lastSyncedAt?: string | null
}

export type ReachlyoCampaignAnalyticsSync = {
  synchronizedAccounts: number
  failures: Array<{
    provider: AdProvider
    adAccountId: string
    message: string
  }>
  analytics: ReachlyoCampaignAnalytics
}

export type CreateReachlyoCampaignInput = {
  name: string
  objective: ReachlyoCampaignObjective
  businessGoal: CampaignBusinessGoal
  landingPageUrl?: string | null
  currency: string
  startAt?: string | null
  endAt?: string | null
}

export type UpdateReachlyoCampaignInput = Partial<CreateReachlyoCampaignInput>

export type CreateReachlyoCampaignChannelInput = {
  channelType: CampaignChannelType
  enabled?: boolean
  budgetAllocationMinor?: number | null
  allocationPercent?: number | null
  providerOverrideJson?: string | null
}

export type UpdateReachlyoCampaignChannelInput = {
  enabled?: boolean
  budgetAllocationMinor?: number | null
  allocationPercent?: number | null
  providerOverrideJson?: string | null
}

export type UpsertReachlyoBudgetPlanInput = {
  totalBudgetMinor: number
  budgetPeriod: BudgetPeriod
  allocationMode: BudgetAllocationMode
}



export type CreativeAssetRole = 'IMAGE' | 'LOGO' | 'VIDEO'
export type CreativeVariantOrigin = 'USER' | 'ADAPTED' | 'GENERATED'
export type CreativeVariantStatus = 'DRAFT' | 'READY' | 'INVALID'
export type CreativeVariantType =
  | 'GOOGLE_RSA'
  | 'GOOGLE_DISPLAY'
  | 'GOOGLE_PERFORMANCE_MAX'
  | 'GOOGLE_DEMAND_GEN'
  | 'META_FEED'
  | 'META_STORY'
  | 'META_REEL'
  | 'META_CAROUSEL'
  | 'META_WHATSAPP_STATUS'

export type CreativePackAssetInput = {
  mediaId: string
  role: CreativeAssetRole
  label?: string | null
  sortOrder?: number | null
}

export type ReachlyoCreativePackAsset = {
  id: string
  mediaId: string
  role: CreativeAssetRole
  label?: string | null
  sortOrder: number
}

export type ReachlyoCreativeVariant = {
  id: string
  creativePackId: string
  campaignChannelId?: string | null
  provider: AdProvider
  variantType: CreativeVariantType
  origin: CreativeVariantOrigin
  status: CreativeVariantStatus
  name: string
  placement?: string | null
  primaryText?: string | null
  headlinesJson?: string | null
  descriptionsJson?: string | null
  callToAction?: string | null
  destinationUrl?: string | null
  assetSpecJson?: string | null
  createdAt: string
  updatedAt: string
}

export type ReachlyoCreativePack = {
  id: string
  campaignId: string
  name: string
  businessName?: string | null
  offerText?: string | null
  primaryMessage?: string | null
  callToAction?: string | null
  destinationUrl?: string | null
  headlinesJson?: string | null
  descriptionsJson?: string | null
  createdByUserId: string
  assets: ReachlyoCreativePackAsset[]
  variants: ReachlyoCreativeVariant[]
  createdAt: string
  updatedAt: string
}

export type CreateReachlyoCreativePackInput = {
  name: string
  businessName?: string | null
  offerText?: string | null
  primaryMessage?: string | null
  callToAction?: string | null
  destinationUrl?: string | null
  headlines?: string[]
  descriptions?: string[]
  assets?: CreativePackAssetInput[]
}

export type UpdateReachlyoCreativePackInput = Partial<CreateReachlyoCreativePackInput>

export type CreateReachlyoCreativeVariantInput = {
  variantType: CreativeVariantType
  campaignChannelId?: string | null
  name: string
  placement?: string | null
  primaryText?: string | null
  headlines?: string[]
  descriptions?: string[]
  callToAction?: string | null
  destinationUrl?: string | null
  assetSpecJson?: string | null
}

export type UpdateReachlyoCreativeVariantInput = Omit<
  Partial<CreateReachlyoCreativeVariantInput>,
  'variantType'
>

export type AudienceMappingStatus =
  | 'EXACT'
  | 'APPROXIMATE'
  | 'PROVIDER_ONLY'
  | 'UNSUPPORTED'

export type ReachlyoAudienceLocationInput = {
  countryCode: string
  region?: string | null
  city?: string | null
  postalCode?: string | null
  latitude?: number | null
  longitude?: number | null
  radiusKm?: number | null
}

export type ReachlyoAudienceMapping = {
  id: string
  campaignChannelId: string
  provider: AdProvider
  mappingStatus: AudienceMappingStatus
  localAudienceId?: string | null
  externalAudienceId?: string | null
  providerCriteriaJson?: string | null
  notes?: string | null
}

export type ReachlyoAudienceDefinition = {
  id: string
  campaignId: string
  name: string
  description?: string | null
  ageMin?: number | null
  ageMax?: number | null
  locationsJson?: string | null
  languagesJson?: string | null
  gendersJson?: string | null
  customerSegmentsJson?: string | null
  retargetingJson?: string | null
  exclusionsJson?: string | null
  interestsJson?: string | null
  searchIntentJson?: string | null
  createdByUserId: string
  mappings: ReachlyoAudienceMapping[]
  createdAt: string
  updatedAt: string
}

export type CreateReachlyoAudienceDefinitionInput = {
  name: string
  description?: string | null
  ageMin?: number | null
  ageMax?: number | null
  locations?: ReachlyoAudienceLocationInput[]
  languages?: string[]
  genders?: string[]
  customerSegmentIds?: string[]
  retargeting?: unknown
  exclusions?: unknown
  interests?: string[]
  searchIntents?: string[]
}

export type UpdateReachlyoAudienceDefinitionInput =
  Partial<CreateReachlyoAudienceDefinitionInput>

export type ConversionTargetType = 'PROVIDER_CONVERSION' | 'STANDARD_EVENT'

export type ReachlyoConversionGoalMapping = {
  id: string
  campaignId: string
  businessGoal: CampaignBusinessGoal
  provider: AdProvider
  adAccountId: string
  targetType: ConversionTargetType
  adConversionId?: string | null
  providerEventName?: string | null
  externalConversionId?: string | null
  validated: boolean
  createdAt: string
  updatedAt: string
}

export type UpsertReachlyoConversionGoalMappingInput = {
  provider: AdProvider
  adAccountId: string
  targetType: ConversionTargetType
  adConversionId?: string | null
  providerEventName?: string | null
}


export type PlanIssueSeverity = 'ERROR' | 'WARNING' | 'INFO'
export type ProviderPlanAction = 'CREATE' | 'CONFIGURE' | 'ATTACH'
export type ProviderPlanResource =
  | 'CAMPAIGN_BUDGET'
  | 'CAMPAIGN'
  | 'CAMPAIGN_TARGETING'
  | 'AD_GROUP'
  | 'AD_SET'
  | 'KEYWORDS'
  | 'CREATIVE'
  | 'AD'
  | 'ASSET_GROUP'
  | 'ASSET_GROUP_ASSETS'
  | 'AUDIENCE_SIGNALS'
  | 'LISTING_GROUP'
  | 'PRODUCT_AD'
  | 'CONVERSION_GOAL'

export type ProviderPlanIssue = {
  severity: PlanIssueSeverity
  code: string
  field?: string | null
  message: string
}

export type ProviderPlanOperation = {
  sequence: number
  operationKey: string
  action: ProviderPlanAction
  resource: ProviderPlanResource
  createPaused: boolean
  requiredCapabilities: AdFeatureCapability[]
  configuration: Record<string, unknown>
}

export type ProviderCampaignPlan = {
  planVersion: number
  campaignId: string
  channelId: string
  provider: AdProvider
  channelType: CampaignChannelType
  adAccountId: string
  deployable: boolean
  effectiveBudgetMinor?: number | null
  budgetPeriod?: BudgetPeriod | null
  creativePackId?: string | null
  audienceDefinitionId?: string | null
  audienceMappingStatus?: AudienceMappingStatus | null
  audienceMappingNote?: string | null
  conversionGoalMappingId?: string | null
  requiredCapabilities: AdFeatureCapability[]
  issues: ProviderPlanIssue[]
  operations: ProviderPlanOperation[]
}

export type CompileReachlyoCampaignInput = {
  adAccountId: string
  creativePackId?: string | null
  audienceDefinitionId?: string | null
}


export type LaunchRunStatus =
  | 'VALIDATING'
  | 'VALIDATION_FAILED'
  | 'READY'
  | 'RUNNING'
  | 'PAUSED_READY'
  | 'PARTIAL_FAILURE'
  | 'FAILED'

export type LaunchStepStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'SKIPPED'

export type LaunchChannelInput = {
  channelId: string
  adAccountId: string
  creativePackId?: string | null
  audienceDefinitionId?: string | null
}

export type StartCampaignLaunchInput = {
  idempotencyKey: string
  channels: LaunchChannelInput[]
}

export type LaunchStep = {
  id: string
  runId: string
  channelId: string
  deploymentId: string
  adAccountId: string
  provider: AdProvider
  sequence: number
  operationKey: string
  action: ProviderPlanAction
  resource: ProviderPlanResource
  createPaused: boolean
  status: LaunchStepStatus
  attemptCount: number
  localResourceId?: string | null
  externalResourceId?: string | null
  lastErrorCode?: string | null
  lastErrorMessage?: string | null
  startedAt?: string | null
  completedAt?: string | null
  createdAt: string
  updatedAt: string
}

export type LaunchRun = {
  id: string
  companyId: string
  campaignId: string
  idempotencyKey: string
  status: LaunchRunStatus
  requestedByUserId: string
  validationIssuesJson?: string | null
  lastErrorCode?: string | null
  lastErrorMessage?: string | null
  startedAt?: string | null
  completedAt?: string | null
  steps: LaunchStep[]
  createdAt: string
  updatedAt: string
}

export type AdCapability =
  | 'ACCOUNT_DISCOVERY'
  | 'CAMPAIGN_MANAGEMENT'
  | 'CREATIVE_MANAGEMENT'
  | 'AUDIENCE_MANAGEMENT'
  | 'CONVERSION_MANAGEMENT'
  | 'ANALYTICS'
  | 'BILLING_READ'
  | 'BILLING_WRITE'
  | 'PRODUCT_CATALOG'
  | 'RECOMMENDATIONS'

export type AdFeatureCapability =
  | 'ACCOUNT_DISCOVERY'
  | 'CAMPAIGN_CREATE'
  | 'CAMPAIGN_UPDATE'
  | 'CAMPAIGN_STATUS'
  | 'CREATIVE_CREATE'
  | 'AUDIENCE_CREATE'
  | 'CONVERSION_CREATE'
  | 'ANALYTICS_READ'
  | 'RECOMMENDATIONS'
  | 'BILLING_READ'
  | 'GOOGLE_SEARCH'
  | 'GOOGLE_PERFORMANCE_MAX'
  | 'GOOGLE_DISPLAY'
  | 'GOOGLE_DEMAND_GEN'
  | 'GOOGLE_SHOPPING'
  | 'GOOGLE_STANDALONE_VIDEO_READ_ONLY'
  | 'GOOGLE_CUSTOMER_MATCH'
  | 'GOOGLE_DATA_MANAGER'
  | 'GOOGLE_MERCHANT'
  | 'GOOGLE_OFFLINE_CONVERSION_UPLOAD'
  | 'GOOGLE_NATIVE_RECOMMENDATIONS'
  | 'GOOGLE_AI_MAX'
  | 'GOOGLE_APP_CAMPAIGN'
  | 'GOOGLE_KEYWORD_PLANNER'
  | 'GOOGLE_FORECAST'
  | 'GOOGLE_EXPERIMENTS'
  | 'GOOGLE_ASSET_GENERATION'
  | 'GOOGLE_BID_SIMULATION'
  | 'GOOGLE_BILLING_WRITE_MONTHLY_INVOICE'
  | 'META_CAMPAIGN_HIERARCHY'
  | 'META_IMAGE_VIDEO_CAROUSEL'
  | 'META_WEBSITE_AUDIENCE'
  | 'META_LOOKALIKE'
  | 'META_PIXEL'
  | 'META_CUSTOM_CONVERSION'
  | 'META_BILLING_SPEND_CAP_WRITE'
  | 'META_DETAILED_TARGETING'
  | 'META_CUSTOM_AUDIENCE_CUSTOMER_LIST'
  | 'META_ADVANTAGE_AUDIENCE'
  | 'META_ADVANTAGE_PLACEMENTS'
  | 'META_ADVANTAGE_CREATIVE'
  | 'META_GENERATIVE_TEXT'
  | 'META_IMAGE_UNCROP'
  | 'META_BACKGROUND_GENERATION'
  | 'META_CATALOG'
  | 'META_EXISTING_POST'
  | 'META_PARTNERSHIP_ADS'
  | 'META_WHATSAPP_STATUS'

export type AdFeatureAvailability =
  | 'AVAILABLE'
  | 'ACCOUNT_ELIGIBILITY_REQUIRED'
  | 'READ_ONLY'
  | 'PROVIDER_HOSTED'
  | 'PLANNED'
  | 'UNAVAILABLE'

export type AdFeatureCapabilityInfo = {
  feature: AdFeatureCapability
  availability: AdFeatureAvailability
  implemented: boolean
  supported: boolean
  accountEligible?: boolean | null
  requiresManagedAccount: boolean
  reason?: string | null
  apiVersion?: string | null
  requiredScopes: string[]
}

export type AdProviderInfo = {
  provider: AdProvider
  displayName: string
  configured: boolean
  capabilities: AdCapability[]
  featureCapabilities: AdFeatureCapabilityInfo[]
}

export type AdAccount = {
  id: string
  companyId: string
  connectionId: string
  provider: AdProvider
  externalAccountId: string
  name: string
  externalBusinessId?: string | null
  currency?: string | null
  timezone?: string | null
  status: 'ACTIVE' | 'DISABLED' | 'CLOSED' | 'UNKNOWN'
  primaryAccount: boolean
  managed: boolean
  accessible: boolean
  lastSyncAt?: string | null
  createdAt: string
  updatedAt: string
}


export type MetaAdsAuthorization = {
  authorizationUrl: string
}

export type GoogleAdsAuthorization = {
  authorizationUrl: string
}


export type AdProviderConnection = {
  id: string
  companyId: string
  provider: AdProvider
  displayName?: string | null
  status: 'PENDING' | 'ACTIVE' | 'ERROR' | 'DISCONNECTED'
  lastSyncAt?: string | null
  lastError?: string | null
  createdAt: string
  updatedAt: string
}

export type AdAccountResourceType =
  | 'FACEBOOK_PAGE'
  | 'INSTAGRAM_ACCOUNT'
  | 'META_PIXEL'

export type AdAccountResource = {
  id: string
  companyId: string
  adAccountId: string
  provider: AdProvider
  resourceType: AdAccountResourceType
  externalResourceId: string
  name?: string | null
  accessible: boolean
  lastSyncAt?: string | null
}

export type MetaAdsResourceSync = {
  pages: number
  instagramAccounts: number
  pixels: number
  resources: AdAccountResource[]
}



export type AdEntityStatus = 'ACTIVE' | 'PAUSED' | 'ARCHIVED' | 'REMOVED' | 'DISABLED' | 'UNKNOWN'

export type AdCampaign = {
  id: string
  companyId: string
  adAccountId: string
  provider: AdProvider
  externalCampaignId: string
  name: string
  objective?: string | null
  buyingType?: string | null
  bidStrategy?: string | null
  status: AdEntityStatus
  providerStatus?: string | null
  effectiveStatus?: string | null
  dailyBudgetMinor?: number | null
  lifetimeBudgetMinor?: number | null
  spendCapMinor?: number | null
  startAt?: string | null
  endAt?: string | null
  providerCreatedAt?: string | null
  providerUpdatedAt?: string | null
  accessible: boolean
  lastSyncAt?: string | null
}

export type PageResponse<T> = {
  content: T[]
  number: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export type MetaCampaignObjective =
  | 'OUTCOME_AWARENESS'
  | 'OUTCOME_TRAFFIC'
  | 'OUTCOME_ENGAGEMENT'
  | 'OUTCOME_LEADS'
  | 'OUTCOME_APP_PROMOTION'
  | 'OUTCOME_SALES'

export type CreateMetaCampaignInput = {
  name: string
  objective: MetaCampaignObjective
  buyingType?: 'AUCTION' | 'RESERVED'
  bidStrategy?: string | null
  dailyBudgetMinor?: number | null
  lifetimeBudgetMinor?: number | null
  specialAdCategories: string[]
}

export type UpdateMetaCampaignInput = {
  name?: string
  bidStrategy?: string
  dailyBudgetMinor?: number
  lifetimeBudgetMinor?: number
}

export type MetaCampaignSync = {
  synchronizedCount: number
}


export type CreateGoogleCampaignInput = {
  name: string
  dailyBudgetMinor: number
  bidStrategy?: 'MANUAL_CPC' | 'MAXIMIZE_CLICKS' | 'MAXIMIZE_CONVERSIONS' | 'MAXIMIZE_CONVERSION_VALUE'
  containsEuPoliticalAdvertising: boolean
  startDate?: string | null
  endDate?: string | null
}

export type UpdateGoogleCampaignInput = Partial<CreateGoogleCampaignInput>

export type CreateGoogleAdvancedCampaignInput = {
  name: string
  channelType: 'DISPLAY' | 'DEMAND_GEN' | 'SHOPPING'
  dailyBudgetMinor?: number | null
  totalBudgetMinor?: number | null
  bidStrategy?: 'MANUAL_CPC' | 'MAXIMIZE_CLICKS' | 'MAXIMIZE_CONVERSIONS' | 'TARGET_CPA' | 'TARGET_ROAS'
  targetCpaMinor?: number | null
  targetRoas?: number | null
  merchantId?: string | null
  campaignPriority?: number | null
  enableLocal?: boolean | null
  containsEuPoliticalAdvertising: boolean
  startDate?: string | null
  endDate?: string | null
}

export type GoogleAppCampaignSubtype =
  | 'APP_CAMPAIGN'
  | 'APP_CAMPAIGN_FOR_ENGAGEMENT'
  | 'APP_CAMPAIGN_FOR_PRE_REGISTRATION'

export type GoogleAppStore = 'GOOGLE_APP_STORE' | 'APPLE_APP_STORE'

export type GoogleAppBiddingGoal =
  | 'OPTIMIZE_INSTALLS_TARGET_INSTALL_COST'
  | 'OPTIMIZE_INSTALLS_WITHOUT_TARGET_INSTALL_COST'
  | 'OPTIMIZE_IN_APP_CONVERSIONS_TARGET_CONVERSION_COST'
  | 'OPTIMIZE_IN_APP_CONVERSIONS_TARGET_INSTALL_COST'
  | 'OPTIMIZE_IN_APP_CONVERSIONS_WITHOUT_TARGET_CPA'
  | 'OPTIMIZE_PRE_REGISTRATION_CONVERSION_VOLUME'
  | 'OPTIMIZE_RETURN_ON_ADVERTISING_SPEND'
  | 'OPTIMIZE_TOTAL_VALUE_WITHOUT_TARGET_ROAS'

export type CreateGoogleAppCampaignInput = {
  name: string
  advertisingChannelSubType: GoogleAppCampaignSubtype
  appId: string
  appStore: GoogleAppStore
  biddingStrategyGoalType: GoogleAppBiddingGoal
  dailyBudgetMinor: number
  targetCpaMinor?: number | null
  targetRoas?: number | null
  conversionActionIds?: string[]
  locations?: ReachlyoAudienceLocationInput[]
  excludedLocations?: ReachlyoAudienceLocationInput[]
  languages?: string[]
  containsEuPoliticalAdvertising: boolean
  startDate?: string | null
  endDate?: string | null
}

export type GoogleAppCampaignSettings = {
  advertisingChannelSubType: GoogleAppCampaignSubtype | string
  appId: string
  appStore: GoogleAppStore | string
  biddingStrategyGoalType: GoogleAppBiddingGoal | string
  conversionActionResources: string[]
  locationResources: string[]
  excludedLocationResources: string[]
  languageResources: string[]
}

export type GoogleAppAssetPerformance = {
  assetResourceName: string
  assetName?: string | null
  impressions: number
  clicks: number
}

export type GoogleProviderLandingPageRow = {
  campaignId?: string | null
  campaignName?: string | null
  adGroupId?: string | null
  adGroupName?: string | null
  url: string
  impressions: number
  clicks: number
  costMicros: number
  conversions: number
  conversionValue: number
}

export type GoogleProviderPlacementRow = {
  campaignId?: string | null
  campaignName?: string | null
  adGroupId?: string | null
  adGroupName?: string | null
  placement: string
  displayName?: string | null
  placementType?: string | null
  targetUrl?: string | null
  adNetworkType?: string | null
  impressions: number
  clicks: number
  costMicros: number
  conversions: number
  conversionValue: number
}

export type GoogleProviderAuctionInsightRow = {
  campaignId?: string | null
  campaignName?: string | null
  domain: string
  impressionShare?: number | null
  overlapRate?: number | null
  positionAboveRate?: number | null
  topImpressionPercentage?: number | null
  absoluteTopImpressionPercentage?: number | null
  outrankingShare?: number | null
}

export type GoogleProviderConversionAttributionRow = {
  campaignId?: string | null
  campaignName?: string | null
  conversionActionName: string
  conversionActionCategory?: string | null
  externalConversionSource?: string | null
  conversions: number
  conversionValue: number
  allConversions: number
  allConversionValue: number
}

export type GoogleProviderChangeEventRow = {
  changeDateTime?: string | null
  userEmail?: string | null
  clientType?: string | null
  resourceType?: string | null
  operation?: string | null
  changeResourceName?: string | null
  changedFields: string[]
  campaignResource?: string | null
  adGroupResource?: string | null
}

export type GoogleProviderReport = {
  landingPages: GoogleProviderLandingPageRow[]
  placements: GoogleProviderPlacementRow[]
  auctionInsights: GoogleProviderAuctionInsightRow[]
  conversionAttribution: GoogleProviderConversionAttributionRow[]
  changeEvents: GoogleProviderChangeEventRow[]
}

export type GoogleAppCampaignPerformance = {
  impressions: number
  clicks: number
  costMicros: number
  conversions: number
  allConversions: number
  skAdNetworkInstalls: number
  skAdNetworkTotalConversions: number
  assetPerformance: GoogleAppAssetPerformance[]
}

export type GoogleAppCreativeAsset = {
  resourceName: string
  name?: string | null
  type: 'IMAGE' | 'YOUTUBE_VIDEO' | string
  imageUrl?: string | null
  youtubeVideoId?: string | null
}

export type CreateGoogleAppAdGroupInput = {
  name: string
  targetCpaMinor?: number | null
  userListId?: string | null
}

export type CreateGoogleAppAdInput = {
  name: string
  headlines: string[]
  descriptions: string[]
  imageAssetResourceNames?: string[]
  videoAssetResourceNames?: string[]
}

export type CreateGooglePerformanceMaxCampaignInput = {
  name: string
  dailyBudgetMinor: number
  finalUrl: string
  businessName: string
  headlines: string[]
  longHeadline: string
  descriptions: string[]
  marketingImageBase64: string
  squareMarketingImageBase64: string
  logoImageBase64: string
  portraitMarketingImageBase64?: string | null
  landscapeLogoImageBase64?: string | null
  youtubeVideoIds?: string[]
  bidStrategy?: 'MAXIMIZE_CONVERSIONS' | 'MAXIMIZE_CONVERSION_VALUE'
  containsEuPoliticalAdvertising: boolean
  startDate?: string | null
  endDate?: string | null
}

export type GoogleReusableAssetType =
  | 'SITELINK'
  | 'CALLOUT'
  | 'STRUCTURED_SNIPPET'
  | 'CALL'
  | 'PRICE'
  | 'PROMOTION'
  | 'LEAD_FORM'
  | 'BUSINESS_NAME'
  | 'AD_IMAGE'
  | 'BUSINESS_LOGO'

export type GoogleUrlCustomParameter = {
  key: string
  value: string
}

export type GoogleReusableAsset = {
  id: string
  resourceName: string
  name?: string | null
  type: GoogleReusableAssetType | string
  source?: string | null
  linkText?: string | null
  description1?: string | null
  description2?: string | null
  calloutText?: string | null
  structuredSnippetHeader?: string | null
  structuredSnippetValues: string[]
  finalUrls: string[]
  finalMobileUrls: string[]
  trackingUrlTemplate?: string | null
  finalUrlSuffix?: string | null
  customParameters: GoogleUrlCustomParameter[]
  text?: string | null
  imageUrl?: string | null
  imageWidth?: number | null
  imageHeight?: number | null
}

export type CreateGoogleReusableAssetInput = {
  type: GoogleReusableAssetType
  name?: string | null
  linkText?: string | null
  description1?: string | null
  description2?: string | null
  calloutText?: string | null
  structuredSnippetHeader?: string | null
  structuredSnippetValues?: string[]
  finalUrl?: string | null
  finalMobileUrl?: string | null
  trackingUrlTemplate?: string | null
  finalUrlSuffix?: string | null
  customParameters?: GoogleUrlCustomParameter[]
  text?: string | null
  imageBase64?: string | null
}

export type GooglePriceOfferingInput = {
  header: string
  description: string
  amount: number
  currencyCode: string
  unit?: string | null
  finalUrl: string
  finalMobileUrl?: string | null
}

export type CreateGoogleAdvancedReusableAssetInput = {
  type: 'CALL' | 'PRICE' | 'PROMOTION' | 'LEAD_FORM'
  name?: string | null
  callCountryCode?: string | null
  callPhoneNumber?: string | null
  priceType?: string | null
  priceQualifier?: string | null
  languageCode?: string | null
  priceOfferings?: GooglePriceOfferingInput[]
  promotionTarget?: string | null
  promotionPercentOff?: number | null
  promotionMoneyAmountOff?: number | null
  promotionCurrencyCode?: string | null
  promotionDiscountModifier?: string | null
  promotionCode?: string | null
  promotionOccasion?: string | null
  promotionLanguageCode?: string | null
  finalUrl?: string | null
  leadBusinessName?: string | null
  leadCallToActionType?: string | null
  leadCallToActionDescription?: string | null
  leadHeadline?: string | null
  leadDescription?: string | null
  leadPrivacyPolicyUrl?: string | null
  leadFields?: string[]
  leadPostSubmitCallToActionType?: string | null
  leadPostSubmitHeadline?: string | null
  leadPostSubmitDescription?: string | null
}

export type GoogleAssetGenerationTextInput = {
  advertisingChannelType: 'SEARCH' | 'PERFORMANCE_MAX' | 'DISPLAY' | 'DEMAND_GEN'
  assetFieldTypes: Array<'HEADLINE' | 'LONG_HEADLINE' | 'DESCRIPTION'>
  finalUrl: string
  freeformPrompt?: string | null
  keywords?: string[]
}

export type GoogleGeneratedTextAsset = {
  text: string
  assetFieldType: string
}

export type GoogleAssetGenerationTextResult = {
  suggestions: GoogleGeneratedTextAsset[]
}

export type GoogleAssetGenerationImageInput = {
  advertisingChannelType: 'SEARCH' | 'PERFORMANCE_MAX' | 'DISPLAY' | 'DEMAND_GEN'
  assetFieldTypes?: Array<
    | 'MARKETING_IMAGE'
    | 'SQUARE_MARKETING_IMAGE'
    | 'PORTRAIT_MARKETING_IMAGE'
    | 'TALL_PORTRAIT_MARKETING_IMAGE'
  >
  generationMode: 'FINAL_URL' | 'FREEFORM'
  finalUrl?: string | null
  freeformPrompt?: string | null
}

export type GoogleGeneratedImageAsset = {
  imageTemporaryUrl: string
  assetFieldType?: string | null
}

export type GoogleAssetGenerationImageResult = {
  suggestions: GoogleGeneratedImageAsset[]
}

export type GoogleAssetLinkLevel = 'CUSTOMER' | 'CAMPAIGN' | 'AD_GROUP'

export type GooglePerformanceMaxVideoCrawlSource = {
  url: string
  sourcePlatform: 'LANDING_PAGE' | 'SOCIAL' | 'YOUTUBE'
  enabled?: boolean | null
}

export type GooglePerformanceMaxAutomation = {
  automationStatuses: Record<string, string>
  automatedVideoCrawlSources: GooglePerformanceMaxVideoCrawlSource[]
  urlExclusions: string[]
}

export type UpdateGooglePerformanceMaxAutomationInput = {
  finalUrlExpansion?: boolean | null
  textAssetAutomation?: boolean | null
  enhancedYoutubeVideos?: boolean | null
  imageEnhancement?: boolean | null
  automatedVideoCrawl?: boolean | null
  automatedVideoCrawlSources?: GooglePerformanceMaxVideoCrawlSource[]
  urlExclusions?: string[]
}

export type GoogleAssetGroupUrlOptions = {
  assetGroupId: string
  trackingUrlTemplate?: string | null
  finalUrlSuffix?: string | null
  customParameters: GoogleUrlCustomParameter[]
}

export type GoogleReusableAssetLink = {
  resourceName: string
  assetResourceName: string
  assetId: string
  assetName?: string | null
  assetType?: string | null
  fieldType: string
  level: GoogleAssetLinkLevel
  targetId?: string | null
  status?: string | null
  primaryStatus?: string | null
}

export type GoogleUrlOptions = {
  level: GoogleAssetLinkLevel
  targetId?: string | null
  trackingUrlTemplate?: string | null
  finalUrlSuffix?: string | null
  customParameters: GoogleUrlCustomParameter[]
}

export type UpdateGoogleUrlOptionsInput = {
  trackingUrlTemplate?: string | null
  finalUrlSuffix?: string | null
  customParameters?: GoogleUrlCustomParameter[] | null
}

export type GoogleAsset = {
  id?: string | null
  resourceName?: string | null
  name?: string | null
  type?: string | null
  text?: string | null
  imageUrl?: string | null
  imageWidth?: number | null
  imageHeight?: number | null
  youtubeVideoId?: string | null
}

export type GoogleAssetGroup = {
  id?: string | null
  resourceName?: string | null
  name?: string | null
  status?: string | null
  primaryStatus?: string | null
  adStrength?: string | null
  finalUrls: string[]
}


export type GoogleAssetGroupAsset = {
  resourceName: string
  assetResourceName?: string | null
  assetId?: string | null
  assetName?: string | null
  assetType?: string | null
  fieldType: string
  status?: string | null
  primaryStatus?: string | null
  policyApprovalStatus?: string | null
  text?: string | null
  imageUrl?: string | null
  imageWidth?: number | null
  imageHeight?: number | null
  youtubeVideoId?: string | null
}

export type GoogleAssetGroupAssetInput = {
  fieldType:
    | 'HEADLINE'
    | 'LONG_HEADLINE'
    | 'DESCRIPTION'
    | 'BUSINESS_NAME'
    | 'MARKETING_IMAGE'
    | 'SQUARE_MARKETING_IMAGE'
    | 'PORTRAIT_MARKETING_IMAGE'
    | 'LOGO'
    | 'LANDSCAPE_LOGO'
    | 'YOUTUBE_VIDEO'
  existingAssetResourceName?: string | null
  name?: string | null
  text?: string | null
  imageBase64?: string | null
  youtubeVideoId?: string | null
}

export type GoogleAssetGroupSignal = {
  resourceName?: string | null
  type: 'SEARCH_THEME' | 'AUDIENCE'
  value?: string | null
  approvalStatus?: string | null
  disapprovalReasons: string[]
}

export type AddGoogleAssetGroupSignalsInput = {
  searchThemes?: string[]
  audienceIds?: string[]
}

export type GoogleCustomerMatchMember = {
  emails?: string[]
  phoneNumbers?: string[]
  mobileIds?: string[]
  userId?: string | null
}

export type GoogleCustomerMatchUploadInput = {
  members: GoogleCustomerMatchMember[]
  adUserDataConsent: boolean
  adPersonalizationConsent: boolean
  customerMatchTermsAccepted: boolean
  validateOnly: boolean
}

export type GoogleConversionEventInput = {
  eventTimestamp: string
  transactionId?: string | null
  eventSource?: 'WEB' | 'APP' | 'IN_STORE' | 'PHONE' | 'MESSAGE' | 'OTHER'
  conversionValue?: number | null
  currency?: string | null
  gclid?: string | null
  gbraid?: string | null
  wbraid?: string | null
  emails?: string[]
  phoneNumbers?: string[]
  userId?: string | null
}

export type GoogleConversionEventUploadInput = {
  events: GoogleConversionEventInput[]
  adUserDataConsent: boolean
  adPersonalizationConsent: boolean
  validateOnly: boolean
}

export type GoogleDataManagerUpload = {
  requestIds: string[]
  submittedCount: number
  validateOnly: boolean
  warnings: string[]
}

export type GoogleNativeRecommendation = {
  resourceName: string
  type: string
  dismissed: boolean
  campaignResourceName?: string | null
  adGroupResourceName?: string | null
  impactJson?: string | null
}

export type GoogleRecommendationAction = {
  action: 'APPLY' | 'DISMISS'
  processedCount: number
  resourceNames: string[]
}

export type GoogleCampaignSync = {
  synchronizedCount: number
}


export type AdSet = {
  id: string
  companyId: string
  adAccountId: string
  campaignId: string
  provider: AdProvider
  externalAdSetId: string
  name: string
  optimizationGoal?: string | null
  billingEvent?: string | null
  bidStrategy?: string | null
  destinationType?: string | null
  status: AdEntityStatus
  providerStatus?: string | null
  effectiveStatus?: string | null
  dailyBudgetMinor?: number | null
  lifetimeBudgetMinor?: number | null
  bidAmountMinor?: number | null
  startAt?: string | null
  endAt?: string | null
  targetingJson?: string | null
  promotedObjectJson?: string | null
  attributionSpecJson?: string | null
  providerCreatedAt?: string | null
  providerUpdatedAt?: string | null
  accessible: boolean
  lastSyncAt?: string | null
}

export type MetaGeoTargetType = 'COUNTRY' | 'REGION' | 'CITY' | 'ZIP' | 'CUSTOM'

export type MetaTargetingLocation = {
  countryCode?: string | null
  type?: MetaGeoTargetType
  key?: string | null
  name?: string | null
  latitude?: number | null
  longitude?: number | null
  radiusKm?: number | null
  locationTypes?: Array<'home' | 'recent'>
}

export type MetaGeoLocationSuggestion = {
  key: string
  name: string
  type: 'COUNTRY' | 'REGION' | 'CITY' | 'ZIP' | string
  countryCode?: string | null
  countryName?: string | null
  region?: string | null
  regionId?: string | null
  supportsRegion?: boolean | null
  supportsCity?: boolean | null
}

export type CreateMetaAdSetInput = {
  name: string
  optimizationGoal: string
  billingEvent: string
  bidStrategy?: string | null
  bidAmountMinor?: number | null
  dailyBudgetMinor?: number | null
  lifetimeBudgetMinor?: number | null
  startAt?: string | null
  endAt?: string | null
  destinationType?: string | null
  locations: MetaTargetingLocation[]
  ageMin?: number | null
  ageMax?: number | null
  genders?: number[]
  publisherPlatforms?: string[]
  facebookPositions?: string[]
  instagramPositions?: string[]
  messengerPositions?: string[]
  audienceNetworkPositions?: string[]
  customAudienceIds?: string[]
  excludedCustomAudienceIds?: string[]
  pixelId?: string | null
  customEventType?: string | null
  customConversionId?: string | null
}

export type UpdateMetaAdSetInput = Partial<CreateMetaAdSetInput>

export type MetaAdSetSync = {
  synchronizedCount: number
}


export type CreateGoogleAdGroupInput = {
  name: string
  cpcBidMinor?: number | null
}

export type UpdateGoogleAdGroupInput = Partial<CreateGoogleAdGroupInput>

export type GoogleAdGroupSync = {
  synchronizedCount: number
}


export type MetaCreativeFormat = 'SINGLE_IMAGE' | 'VIDEO' | 'CAROUSEL'

export type AdCreative = {
  id: string
  companyId: string
  adAccountId: string
  provider: AdProvider
  externalCreativeId: string
  name: string
  creativeFormat?: MetaCreativeFormat | string | null
  pageId?: string | null
  instagramActorId?: string | null
  primaryText?: string | null
  headline?: string | null
  description?: string | null
  callToActionType?: string | null
  destinationUrl?: string | null
  imageHash?: string | null
  imageUrl?: string | null
  videoId?: string | null
  videoUrl?: string | null
  urlTags?: string | null
  objectStorySpecJson?: string | null
  carouselJson?: string | null
  providerCreatedAt?: string | null
  providerUpdatedAt?: string | null
  accessible: boolean
  lastSyncAt?: string | null
}

export type MetaCarouselCardInput = {
  headline?: string | null
  description?: string | null
  destinationUrl?: string | null
  imageHash?: string | null
  imageUrl?: string | null
  callToActionType?: string | null
}

export type CreateMetaCreativeInput = {
  name: string
  format: MetaCreativeFormat
  pageId: string
  instagramActorId?: string | null
  primaryText?: string | null
  headline?: string | null
  description?: string | null
  callToActionType?: string | null
  destinationUrl?: string | null
  imageHash?: string | null
  imageUrl?: string | null
  videoId?: string | null
  videoUrl?: string | null
  urlTags?: string | null
  carouselCards?: MetaCarouselCardInput[]
}

export type MetaCreativeSync = {
  synchronizedCount: number
}

export type Ad = {
  id: string
  companyId: string
  adAccountId: string
  campaignId: string
  adSetId: string
  creativeId?: string | null
  provider: AdProvider
  externalAdId: string
  externalCreativeId?: string | null
  name: string
  status: AdEntityStatus
  providerStatus?: string | null
  effectiveStatus?: string | null
  trackingSpecsJson?: string | null
  conversionSpecsJson?: string | null
  providerCreatedAt?: string | null
  providerUpdatedAt?: string | null
  accessible: boolean
  lastSyncAt?: string | null
}

export type CreateMetaAdInput = {
  name: string
  creativeId: string
}

export type UpdateMetaAdInput = {
  name?: string
  creativeId?: string
}

export type MetaAdSync = {
  synchronizedCount: number
}


export type CreateGoogleResponsiveDisplayAdInput = {
  name: string
  finalUrl: string
  businessName: string
  headlines: string[]
  longHeadline: string
  descriptions: string[]
  marketingImageBase64: string
  squareMarketingImageBase64: string
  squareLogoImageBase64?: string | null
}

export type GoogleDemandGenSelectedChannels = {
  youtubeInFeed?: boolean | null
  youtubeInStream?: boolean | null
  youtubeShorts?: boolean | null
  discover?: boolean | null
  gmail?: boolean | null
  display?: boolean | null
  maps?: boolean | null
}

export type GoogleDemandGenTargetingInput = {
  channelStrategy?: 'ALL_CHANNELS' | 'ALL_OWNED_AND_OPERATED_CHANNELS' | null
  selectedChannels?: GoogleDemandGenSelectedChannels | null
  locations?: ReachlyoAudienceLocationInput[] | null
  locationResourceNames?: string[] | null
  excludedLocations?: ReachlyoAudienceLocationInput[] | null
  excludedLocationResourceNames?: string[] | null
  languages?: string[] | null
  languageResourceNames?: string[] | null
  audienceResourceNames?: string[] | null
}

export type GoogleDemandGenTargeting = {
  channelConfig?: string | null
  channelStrategy?: string | null
  selectedChannels?: GoogleDemandGenSelectedChannels | null
  useAudienceGrouped: boolean
  locationResources: string[]
  excludedLocationResources: string[]
  languageResources: string[]
  audienceResources: string[]
}

export type GoogleDemandGenAudienceOption = {
  id?: string | null
  resourceName: string
  name?: string | null
  status?: string | null
  description?: string | null
}

export type GoogleDemandGenChannelPerformance = {
  channel: string
  impressions: number
  clicks: number
  costMicros: number
  conversions: number
  conversionsValue: number
}

export type CreateGoogleDemandGenVideoAdInput = {
  name: string
  finalUrl: string
  businessName: string
  headlines: string[]
  longHeadlines: string[]
  descriptions: string[]
  youtubeVideoId: string
  logoImageBase64: string
}

export type CreateGoogleDemandGenMultiAssetAdInput = {
  name: string
  finalUrl: string
  businessName: string
  headlines: string[]
  descriptions: string[]
  callToActionText?: string | null
  marketingImageBase64?: string | null
  squareMarketingImageBase64?: string | null
  portraitMarketingImageBase64?: string | null
  tallPortraitMarketingImageBase64?: string | null
  logoImageBase64: string
}

export type GoogleDemandGenCarouselCardInput = {
  headline: string
  callToActionText?: string | null
  marketingImageBase64?: string | null
  squareMarketingImageBase64?: string | null
  portraitMarketingImageBase64?: string | null
}

export type CreateGoogleDemandGenCarouselAdInput = {
  name: string
  finalUrl: string
  businessName: string
  headline: string
  description: string
  callToActionText?: string | null
  logoImageBase64: string
  cards: GoogleDemandGenCarouselCardInput[]
}

export type CreateGoogleShoppingProductAdInput = {
  name?: string | null
}

export type CreateGoogleResponsiveSearchAdInput = {
  name: string
  finalUrl: string
  headlines: string[]
  descriptions: string[]
  path1?: string | null
  path2?: string | null
}

export type UpdateGoogleResponsiveSearchAdInput = Partial<CreateGoogleResponsiveSearchAdInput>

export type GoogleAdSync = {
  synchronizedCount: number
}

export type AdKeyword = {
  id: string
  companyId: string
  adAccountId: string
  campaignId: string
  adSetId: string
  provider: AdProvider
  externalKeywordId: string
  keywordText: string
  matchType?: string | null
  status: AdEntityStatus
  providerStatus?: string | null
  bidAmountMicros?: number | null
  qualityScore?: number | null
  finalUrlsJson?: string | null
  negative: boolean
  accessible: boolean
  lastSyncAt?: string | null
}

export type CreateGoogleKeywordInput = {
  text: string
  matchType: 'BROAD' | 'PHRASE' | 'EXACT'
  cpcBidMinor?: number | null
  negative?: boolean
}

export type UpdateGoogleKeywordInput = {
  cpcBidMinor?: number | null
  status?: 'ENABLED' | 'ACTIVE' | 'PAUSED'
}

export type GoogleKeywordSync = {
  synchronizedCount: number
}

export type GoogleNegativeKeywordInput = {
  text: string
  matchType: 'BROAD' | 'PHRASE' | 'EXACT'
}

export type GoogleSearchTermRow = {
  searchTerm: string
  targetingStatus?: string | null
  matchedKeyword?: string | null
  matchedKeywordMatchType?: string | null
  searchTermMatchType?: string | null
  searchTermMatchSource?: string | null
  externalAdGroupId: string
  adGroupId?: string | null
  adGroupName?: string | null
  impressions: number
  clicks: number
  ctr?: number | null
  spend: number
  conversions: number
  conversionValue: number
  cpa?: number | null
  roas?: number | null
}

export type GoogleSearchTermReport = {
  campaignId: string
  from: string
  to: string
  currency?: string | null
  rows: GoogleSearchTermRow[]
}

export type GoogleKeywordPlannerMonthlyVolume = {
  year: number
  month?: string | null
  searches?: number | null
}

export type GoogleKeywordPlannerConcept = {
  name?: string | null
  groupName?: string | null
  groupType?: string | null
}

export type GoogleKeywordPlannerRow = {
  text: string
  averageMonthlySearches?: number | null
  competition?: string | null
  competitionIndex?: number | null
  lowTopOfPageBid?: number | null
  highTopOfPageBid?: number | null
  averageCpc?: number | null
  monthlyVolumes: GoogleKeywordPlannerMonthlyVolume[]
  closeVariants: string[]
  concepts: GoogleKeywordPlannerConcept[]
}

export type GoogleKeywordPlannerResponse = {
  cached: boolean
  currency?: string | null
  network: 'GOOGLE_SEARCH' | 'GOOGLE_SEARCH_AND_PARTNERS' | string
  languageResource?: string | null
  geoTargetResources: string[]
  rows: GoogleKeywordPlannerRow[]
  nextPageToken?: string | null
  totalSize: number
}

export type GoogleKeywordPlannerIdeasInput = {
  seedKeywords?: string[]
  url?: string | null
  useEntireSite?: boolean
  locations?: ReachlyoAudienceLocationInput[]
  language?: string | null
  network?: 'GOOGLE_SEARCH' | 'GOOGLE_SEARCH_AND_PARTNERS'
  includeAdultKeywords?: boolean
  pageSize?: number
  pageToken?: string | null
}

export type GoogleKeywordPlannerHistoricalInput = {
  keywords: string[]
  locations?: ReachlyoAudienceLocationInput[]
  language?: string | null
  network?: 'GOOGLE_SEARCH' | 'GOOGLE_SEARCH_AND_PARTNERS'
  includeAdultKeywords?: boolean
}

export type GoogleKeywordForecastMatchType = 'BROAD' | 'PHRASE' | 'EXACT'
export type GoogleKeywordForecastBiddingStrategy =
  | 'MANUAL_CPC'
  | 'MAXIMIZE_CLICKS'
  | 'MAXIMIZE_CONVERSIONS'

export type GoogleKeywordForecastInput = {
  keywords: Array<{
    text: string
    matchType: GoogleKeywordForecastMatchType
  }>
  locations?: ReachlyoAudienceLocationInput[]
  language?: string | null
  startDate: string
  endDate: string
  biddingStrategy: GoogleKeywordForecastBiddingStrategy
  dailyBudget?: number | null
  maxCpcBid?: number | null
  maxCpcBidCeiling?: number | null
}

export type GoogleKeywordForecastResponse = {
  cached: boolean
  currency?: string | null
  startDate: string
  endDate: string
  biddingStrategy: GoogleKeywordForecastBiddingStrategy | string
  keywordCount: number
  geoTargetResources: string[]
  languageResource?: string | null
  clicks?: number | null
  cost?: number | null
  averageCpc?: number | null
  conversions?: number | null
  averageCpa?: number | null
}

export type GoogleBrandSuggestion = {
  id: string
  name: string
  urls: string[]
  state?: string | null
}

export type GoogleAiMaxCombinationRow = {
  searchTerm?: string | null
  headline?: string | null
  landingPage?: string | null
  externalAdGroupId?: string | null
  adGroupName?: string | null
  impressions: number
  clicks: number
  spend: number
  conversions: number
  conversionValue: number
  cpa?: number | null
  roas?: number | null
}

export type GoogleAiMaxCombinationReport = {
  campaignId: string
  from: string
  to: string
  currency?: string | null
  rows: GoogleAiMaxCombinationRow[]
}

export type GoogleNegativeKeyword = {
  resourceName: string
  criterionId: string
  text: string
  matchType: 'BROAD' | 'PHRASE' | 'EXACT' | string
  status?: string | null
}

export type GoogleNegativeKeywordList = {
  resourceName: string
  id: string
  name: string
  status?: string | null
  memberCount: number
  referenceCount: number
  attachedToCampaign: boolean
  members: GoogleNegativeKeyword[]
}

export type GoogleNegativeKeywordMutation = {
  requestedCount: number
  createdCount: number
  reusedCount: number
}

export type CreateGoogleNegativeKeywordListInput = {
  name: string
  keywords?: GoogleNegativeKeywordInput[]
}


export type AdAudience = {
  id: string
  companyId: string
  adAccountId: string
  provider: AdProvider
  externalAudienceId: string
  name: string
  subtype?: string | null
  description?: string | null
  retentionDays?: number | null
  approximateCountLowerBound?: number | null
  approximateCountUpperBound?: number | null
  deliveryStatusJson?: string | null
  operationStatusJson?: string | null
  pixelId?: string | null
  ruleJson?: string | null
  lookalikeSpecJson?: string | null
  providerCreatedAt?: string | null
  providerUpdatedAt?: string | null
  accessible: boolean
  lastSyncAt?: string | null
}

export type CreateMetaAudienceInput = {
  name: string
  type: 'WEBSITE' | 'LOOKALIKE'
  description?: string | null
  pixelId?: string | null
  ruleJson?: string | null
  retentionDays?: number | null
  originAudienceId?: string | null
  country?: string | null
  ratio?: number | null
}

export type MetaAudienceSync = {
  synchronizedCount: number
}


export type CreateGoogleAudienceInput = {
  name: string
  description?: string | null
  membershipLifeSpan?: number | null
  uploadKeyType?: 'CONTACT_INFO' | 'CRM_ID' | 'MOBILE_ADVERTISING_ID'
}

export type GoogleAudienceSync = {
  synchronizedCount: number
}

export type AdConversion = {
  id: string
  companyId: string
  adAccountId: string
  provider: AdProvider
  externalConversionId: string
  name: string
  description?: string | null
  customEventType?: string | null
  eventSourceType?: string | null
  pixelId?: string | null
  ruleJson?: string | null
  dataSourcesJson?: string | null
  defaultConversionValue?: number | null
  archived: boolean
  unavailable: boolean
  firstFiredAt?: string | null
  lastFiredAt?: string | null
  providerCreatedAt?: string | null
  accessible: boolean
  lastSyncAt?: string | null
}

export type CreateMetaConversionInput = {
  name: string
  description?: string | null
  customEventType: string
  pixelId: string
  ruleJson: string
  defaultConversionValue?: number | null
}

export type MetaConversionSync = {
  synchronizedCount: number
}


export type CreateGoogleConversionInput = {
  name: string
  category: string
  type?: string | null
  defaultValue?: number | null
  countingType?: 'ONE_PER_CLICK' | 'MANY_PER_CLICK'
}

export type GoogleConversionSync = {
  synchronizedCount: number
}


export type AdInsightLevel = 'CAMPAIGN' | 'AD_SET' | 'AD'

export type AdAnalyticsSummary = {
  currency?: string | null
  spend: number
  impressions: number
  reach: number
  clicks: number
  ctr?: number | null
  cpc?: number | null
  cpm?: number | null
  conversions: number
  conversionValue: number
  cpa?: number | null
  roas?: number | null
  leads: number
  purchases: number
}

export type AdAnalyticsDailyPoint = {
  date: string
  spend: number
  impressions: number
  reach: number
  clicks: number
  conversions: number
  conversionValue: number
  roas?: number | null
}

export type AdAnalyticsEntityRow = {
  entityId?: string | null
  entityRef: string
  entityName?: string | null
  spend: number
  impressions: number
  reach: number
  clicks: number
  ctr?: number | null
  conversions: number
  conversionValue: number
  cpa?: number | null
  roas?: number | null
  leads: number
  purchases: number
}

export type AdAnalytics = {
  summary: AdAnalyticsSummary
  daily: AdAnalyticsDailyPoint[]
  entities: AdAnalyticsEntityRow[]
  exactRangeAvailable: boolean
  lastSyncedAt?: string | null
}

export type MetaInsightsSync = {
  accountRows: number
  campaignRows: number
  adSetRows: number
  adRows: number
}


export type GoogleInsightsSync = {
  accountRows: number
  campaignRows: number
  adGroupRows: number
  adRows: number
}


export type AdInsightBreakdownType =
  | 'AGE'
  | 'GENDER'
  | 'COUNTRY'
  | 'REGION'
  | 'PUBLISHER_PLATFORM'
  | 'PLATFORM_POSITION'
  | 'IMPRESSION_DEVICE'
  | 'DEVICE_PLATFORM'
  | 'CONVERSION_DESTINATION'
  | 'ACTION_TYPE'
  | 'ACTION_DEVICE'

export type AdAnalyticsBreakdownRow = {
  dimension: string
  secondaryDimension?: string | null
  actionType?: string | null
  currency?: string | null
  spend: number
  impressions: number
  reach: number
  clicks: number
  ctr?: number | null
  cpc?: number | null
  cpm?: number | null
  conversions: number
  conversionValue: number
  leads: number
  purchases: number
  actionValue: number
}

export type AdAnalyticsBreakdown = {
  breakdown: AdInsightBreakdownType
  rows: AdAnalyticsBreakdownRow[]
  lastSyncedAt?: string | null
}

export type MetaInsightBreakdownsSync = {
  age: number
  gender: number
  country: number
  region: number
  publisherPlatform: number
  platformPosition: number
  impressionDevice: number
  devicePlatform: number
  conversionDestination: number
  actionType: number
  actionDevice: number
  failedBreakdowns: string[]
  totalRows?: number
}

export type GoogleInsightBreakdownsSync = {
  age: number
  gender: number
  country: number
  region: number
  publisherPlatform: number
  platformPosition: number
  impressionDevice: number
  devicePlatform: number
  conversionDestination: number
  actionType: number
  actionDevice: number
  failedBreakdowns: string[]
}


export type AdAccountBilling = {
  synced: boolean
  id?: string | null
  companyId: string
  adAccountId: string
  provider: AdProvider
  currency?: string | null
  balanceMinor?: number | null
  amountSpentMinor?: number | null
  spendCapMinor?: number | null
  remainingSpendCapMinor?: number | null
  spendCapEnabled: boolean
  minCampaignGroupSpendCapMinor?: number | null
  minDailyBudgetMinor?: number | null
  prepayAccount?: boolean | null
  taxIdRequired?: boolean | null
  fundingSourceId?: string | null
  fundingSourceType?: number | null
  fundingSourceDisplay?: string | null
  taxIdStatus?: number | null
  taxIdType?: string | null
  disableReason?: number | null
  businessCountryCode?: string | null
  lastSyncAt?: string | null
}


export type MetaDeliveryCheck = {
  check_name?: string | null
  description?: string | null
  summary?: string | null
  extra_info?: unknown
}

export type AdAccountHealth = {
  synced: boolean
  id?: string | null
  companyId: string
  adAccountId: string
  provider: AdProvider
  accountStatus?: number | null
  disableReason?: number | null
  opportunityScore?: number | null
  opportunityScoreWeight?: number | null
  pageAuthorized?: boolean | null
  offsitePixelsTosAccepted?: boolean | null
  directDealsEnabled?: boolean | null
  notificationsEnabled?: boolean | null
  accessExpiresAt?: string | null
  offsiteCloSignalStatus?: number | null
  capabilities: string[]
  allCapabilities: string[]
  userTasks: string[]
  failedDeliveryChecks?: MetaDeliveryCheck[] | null
  lastSyncAt?: string | null
}

export type AdActivityLog = {
  id: string
  companyId: string
  adAccountId?: string | null
  provider: AdProvider
  actorUserId?: string | null
  action: string
  entityType: string
  entityId?: string | null
  beforeJson?: string | null
  afterJson?: string | null
  metadataJson?: string | null
  createdAt: string
}


export type AdRecommendationSeverity = 'CRITICAL' | 'WARNING' | 'OPPORTUNITY' | 'INFO'
export type AdRecommendationCategory =
  | 'ACCOUNT'
  | 'DELIVERY'
  | 'BILLING'
  | 'TRACKING'
  | 'SETUP'
  | 'PERFORMANCE'
export type AdRecommendationSource = 'META_PROVIDER' | 'GOOGLE_PROVIDER' | 'REACHLYO_DIAGNOSTIC'
export type AdRecommendationLifecycleStatus =
  | 'ACTIVE'
  | 'DISMISSED'
  | 'SNOOZED'
  | 'RESOLVED'
export type AdRecommendationLifecycleAction = 'DISMISS' | 'SNOOZE' | 'REOPEN'

export type AdRecommendationItem = {
  key: string
  severity: AdRecommendationSeverity
  category: AdRecommendationCategory
  source: AdRecommendationSource
  title: string
  description: string
  entityType?: string | null
  entityId?: string | null
  actionLabel?: string | null
  actionPath?: string | null
  evidence: Record<string, unknown>
  lifecycleStatus: AdRecommendationLifecycleStatus
  snoozedUntil?: string | null
  dismissible: boolean
  snoozable: boolean
}

export type AdRecommendations = {
  adAccountId: string
  metaOpportunityScore?: number | null
  metaOpportunityScoreWeight?: number | null
  criticalCount: number
  warningCount: number
  opportunityCount: number
  infoCount: number
  dismissedCount: number
  snoozedCount: number
  generatedAt: string
  healthLastSyncAt?: string | null
  billingLastSyncAt?: string | null
  analyticsLastSyncAt?: string | null
  items: AdRecommendationItem[]
  suppressedItems: AdRecommendationItem[]
}

export type AdRecommendationSummaryItem = {
  adAccountId: string
  accountName: string
  item: AdRecommendationItem
}

export type AdRecommendationSummary = {
  managedAccountCount: number
  criticalCount: number
  warningCount: number
  opportunityCount: number
  infoCount: number
  attentionCount: number
  dismissedCount: number
  snoozedCount: number
  topItems: AdRecommendationSummaryItem[]
}

export type GoogleMerchantAccount = {
  name: string
  accountId: string
  accountName?: string | null
  timeZone?: string | null
  languageCode?: string | null
  adultContent: boolean
}

export type GoogleMerchantDataSource = {
  name: string
  dataSourceId: string
  displayName?: string | null
  input?: string | null
  contentLanguage?: string | null
  feedLabel?: string | null
  countries: string[]
  apiManaged: boolean
}

export type CreateGoogleMerchantDataSourceInput = {
  displayName: string
  contentLanguage: string
  feedLabel: string
  countries: string[]
}

export type GoogleMerchantProduct = {
  name?: string | null
  offerId: string
  contentLanguage: string
  feedLabel: string
  dataSource?: string | null
  title?: string | null
  description?: string | null
  link?: string | null
  imageLink?: string | null
  availability?: string | null
  condition?: string | null
  brand?: string | null
  priceAmountMicros?: string | null
  priceCurrency?: string | null
  gtins: string[]
  googleProductCategory?: string | null
  issues: string[]
}

export type GoogleMerchantProductPage = {
  products: GoogleMerchantProduct[]
  nextPageToken?: string | null
}

export type UpsertGoogleMerchantProductInput = {
  dataSourceName: string
  offerId: string
  contentLanguage: string
  feedLabel: string
  title: string
  description?: string | null
  link: string
  imageLink: string
  availability: 'IN_STOCK' | 'OUT_OF_STOCK' | 'PREORDER' | 'BACKORDER'
  condition: 'NEW' | 'REFURBISHED' | 'USED'
  brand?: string | null
  price?: number | null
  currency?: string | null
  gtins?: string[]
  googleProductCategory?: string | null
}

export type DeleteGoogleMerchantProductInput = {
  dataSourceName: string
  contentLanguage: string
  feedLabel: string
  offerId: string
}

export type GoogleFullSync = {
  campaigns: number
  adGroups: number
  keywords: number
  ads: number
  audiences: number
  conversions: number
  analyticsRows: number
  failedOperations: string[]
}
