export type BillingCycle = 'FREE' | 'MONTHLY' | 'YEARLY'
export type PlanStatus = 'DRAFT' | 'PUBLISHED' | 'RETIRED'
export type RateCardStatus = 'ACTIVE' | 'ARCHIVED'
export type EntitlementValueType = 'INTEGER' | 'BOOLEAN' | 'DECIMAL' | 'TEXT'
export type EntitlementCategory = 'WORKSPACE' | 'CHANNEL' | 'BROADCAST' | 'TEMPLATE' | 'FEATURE'
export type ChannelType = 'EMAIL' | 'WHATSAPP' | 'SMS'
export type ProviderType = 'GMAIL' | 'MICROSOFT' | 'SMTP' | 'META_WHATSAPP' | 'MSG91'
export type BillingCategory =
  | 'SMS_PROMOTIONAL'
  | 'SMS_TRANSACTIONAL'
  | 'SMS_OTP'
  | 'WHATSAPP_MARKETING'
  | 'WHATSAPP_UTILITY'
  | 'WHATSAPP_AUTHENTICATION'
  | 'WHATSAPP_SERVICE'
  | 'EMAIL_OUTBOUND'
  | 'EMAIL_INBOUND'
export type CommunicationDirection = 'INBOUND' | 'OUTBOUND'
export type UsageUnit = 'MESSAGE' | 'CONVERSATION' | 'CLICK' | 'IMPRESSION'

export type PlanPrice = {
  planPriceId: string
  planId: string
  billingCycle: BillingCycle
  amount: number
  currency: string
  effectiveFrom?: string | null
  effectiveTo?: string | null
  status: RateCardStatus
}

export type PlanEntitlement = {
  entitlementId: string
  key: string
  value: string
}

export type BillingPlan = {
  planId: string
  code: string
  name: string
  description?: string | null
  status: PlanStatus
  billingCycle: BillingCycle
  basePrice: number
  currency: string
  version: number
  prices: PlanPrice[]
  entitlements: PlanEntitlement[]
}

export type CreateBillingPlanInput = {
  code: string
  name: string
  description?: string
  billingCycle: BillingCycle
  basePrice: number
  currency: string
  version: number
}

export type UpdateBillingPlanInput = Partial<
  Pick<BillingPlan, 'name' | 'description' | 'billingCycle' | 'basePrice' | 'currency' | 'status'>
>

export type EntitlementDefinition = {
  entitlementDefinitionId: string
  key: string
  name: string
  description?: string | null
  valueType: EntitlementValueType
  category: EntitlementCategory
  defaultValue: string
  active: boolean
}

export type CreatePlanPriceInput = {
  billingCycle: BillingCycle
  amount: number
  currency: string
  effectiveFrom?: string | null
  effectiveTo?: string | null
}

export type UpdatePlanPriceInput = {
  amount?: number
  currency?: string
  effectiveFrom?: string | null
  effectiveTo?: string | null
  status?: RateCardStatus
}

export type RateCard = {
  rateCardId: string
  planId: string
  channel: ChannelType
  provider: ProviderType
  country?: string | null
  carrier?: string | null
  billingCategory: BillingCategory
  direction: CommunicationDirection
  unit: UsageUnit
  price: number
  currency: string
  effectiveFrom?: string | null
  effectiveTo?: string | null
  status: RateCardStatus
}

export type CreateRateCardInput = {
  planId: string
  channel: ChannelType
  provider: ProviderType
  country?: string
  carrier?: string
  billingCategory: BillingCategory
  direction: CommunicationDirection
  unit: UsageUnit
  price: number
  currency: string
  effectiveFrom?: string | null
  effectiveTo?: string | null
}

export type UpdateRateCardInput = {
  price?: number
  effectiveFrom?: string | null
  effectiveTo?: string | null
  status?: RateCardStatus
}
