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



export type AdEntityStatus = 'ACTIVE' | 'PAUSED' | 'ARCHIVED' | 'REMOVED' | 'UNKNOWN'

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
}

export type UpdateMetaAdSetInput = Partial<CreateMetaAdSetInput>

export type MetaAdSetSync = {
  synchronizedCount: number
}
