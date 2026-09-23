export type AdProvider = 'GOOGLE_ADS' | 'META_ADS'

export type AdCapability =
  | 'ACCOUNT_DISCOVERY'
  | 'CAMPAIGN_MANAGEMENT'
  | 'CREATIVE_MANAGEMENT'
  | 'AUDIENCE_MANAGEMENT'
  | 'CONVERSION_MANAGEMENT'
  | 'ANALYTICS'
  | 'BILLING_READ'
  | 'BILLING_WRITE'
  | 'RECOMMENDATIONS'

export type AdProviderInfo = {
  provider: AdProvider
  displayName: string
  configured: boolean
  capabilities: AdCapability[]
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

export type MetaTargetingLocation = {
  countryCode: string
  locationTypes?: string[]
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
export type AdRecommendationSource = 'META_PROVIDER' | 'REACHLYO_DIAGNOSTIC'

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
}

export type AdRecommendations = {
  adAccountId: string
  metaOpportunityScore?: number | null
  metaOpportunityScoreWeight?: number | null
  criticalCount: number
  warningCount: number
  opportunityCount: number
  infoCount: number
  generatedAt: string
  healthLastSyncAt?: string | null
  billingLastSyncAt?: string | null
  analyticsLastSyncAt?: string | null
  items: AdRecommendationItem[]
}
