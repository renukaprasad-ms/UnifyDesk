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
  startDate?: string | null
  endDate?: string | null
}

export type UpdateGoogleCampaignInput = Partial<CreateGoogleCampaignInput>

export type CreateGoogleAdvancedCampaignInput = {
  name: string
  channelType: 'DISPLAY' | 'DEMAND_GEN' | 'SHOPPING'
  dailyBudgetMinor: number
  bidStrategy?: 'MANUAL_CPC' | 'MAXIMIZE_CLICKS' | 'MAXIMIZE_CONVERSIONS' | 'TARGET_CPA' | 'TARGET_ROAS'
  targetCpaMinor?: number | null
  targetRoas?: number | null
  merchantId?: string | null
  campaignPriority?: number | null
  enableLocal?: boolean | null
  containsEuPoliticalAdvertising?: boolean
  startDate?: string | null
  endDate?: string | null
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
  startDate?: string | null
  endDate?: string | null
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
