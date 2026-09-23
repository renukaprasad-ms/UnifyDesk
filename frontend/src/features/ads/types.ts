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
