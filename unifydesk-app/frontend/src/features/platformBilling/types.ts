export type BillingCycle = 'FREE' | 'MONTHLY' | 'YEARLY'
export type PlanStatus = 'DRAFT' | 'PUBLISHED' | 'RETIRED'
export type PlanVisibility = 'PUBLIC' | 'PRIVATE' | 'INTERNAL'
export type RateCardStatus = 'ACTIVE' | 'ARCHIVED'
export type SubscriptionStatus = 'ACTIVE' | 'TRIALING' | 'PAST_DUE' | 'SUSPENDED' | 'CANCELLED' | 'EXPIRED'
export type PaymentPolicy = 'CASHFREE' | 'COMPED' | 'OFFLINE_CONTRACT'
export type EntitlementValueType = 'INTEGER' | 'BOOLEAN' | 'DECIMAL' | 'TEXT'
export type EntitlementCategory = 'WORKSPACE' | 'CHANNEL' | 'BROADCAST' | 'TEMPLATE' | 'STORAGE' | 'FEATURE'
export type ChannelType = 'EMAIL' | 'WHATSAPP' | 'SMS'
export type ProviderType = 'GMAIL' | 'MICROSOFT' | 'ZOHO_MAIL' | 'IMAP_SMTP' | 'SMTP' | 'AMAZON_SES' | 'UNIFYDESK_EMAIL' | 'SENDGRID' | 'AZURE_EMAIL' | 'MSG91_EMAIL' | 'META_WHATSAPP' | 'MSG91_SMS'
export type BillingCategory = 'SMS_PROMOTIONAL' | 'SMS_TRANSACTIONAL' | 'SMS_OTP' | 'WHATSAPP_MARKETING' | 'WHATSAPP_UTILITY' | 'WHATSAPP_AUTHENTICATION' | 'WHATSAPP_SERVICE' | 'EMAIL_OUTBOUND' | 'EMAIL_INBOUND'
export type CommunicationDirection = 'INBOUND' | 'OUTBOUND'
export type UsageUnit = 'MESSAGE' | 'SEGMENT' | 'CONVERSATION' | 'CLICK' | 'IMPRESSION'
export type PlanPrice = { planPriceId:string; planId:string; billingCycle:BillingCycle; amount:number; currency:string; effectiveFrom?:string|null; effectiveTo?:string|null; status:RateCardStatus }
export type PlanEntitlement = { entitlementId:string; key:string; value:string }
export type BillingPlan = { planId:string; code:string; name:string; description?:string|null; status:PlanStatus; visibility:PlanVisibility; billingCycle:BillingCycle; basePrice:number; currency:string; version:number; prices:PlanPrice[]; entitlements:PlanEntitlement[] }
export type CreateBillingPlanInput = { code:string; name:string; description?:string; billingCycle:BillingCycle; basePrice:number; currency:string; version:number; visibility?:PlanVisibility }
export type UpdateBillingPlanInput = Partial<Pick<BillingPlan,'name'|'description'|'billingCycle'|'basePrice'|'currency'|'status'|'visibility'>>
export type EntitlementDefinition = { entitlementDefinitionId:string; key:string; name:string; description?:string|null; valueType:EntitlementValueType; category:EntitlementCategory; defaultValue:string; active:boolean }
export type CreatePlanPriceInput = { billingCycle:BillingCycle; amount:number; currency:string; effectiveFrom:string; effectiveTo?:string|null }
export type UpdatePlanPriceInput = { amount?:number; currency?:string; effectiveFrom?:string|null; effectiveTo?:string|null; status?:RateCardStatus }
export type RateCard = { rateCardId:string; planId:string; channel:ChannelType; provider:ProviderType; country?:string|null; carrier?:string|null; billingCategory:BillingCategory; direction:CommunicationDirection; unit:UsageUnit; price:number; currency:string; effectiveFrom?:string|null; effectiveTo?:string|null; status:RateCardStatus }
export type CreateRateCardInput = { planId:string; channel:ChannelType; provider:ProviderType; country?:string; carrier?:string; billingCategory:BillingCategory; direction:CommunicationDirection; unit:UsageUnit; price:number; currency:string; effectiveFrom:string; effectiveTo?:string|null }
export type UpdateRateCardInput = { price?:number; effectiveFrom?:string|null; effectiveTo?:string|null; status?:RateCardStatus }
export type CompanyRateOverride = Omit<RateCard,'rateCardId'|'planId'> & { overrideId:string; companyId:string; createdByUserId:string; reason?:string|null }
export type CreateCompanyRateOverrideInput = { channel:ChannelType; provider:ProviderType; billingCategory:BillingCategory; direction:CommunicationDirection; country?:string; carrier?:string; unit:UsageUnit; price:number; currency:string; effectiveFrom:string; effectiveTo?:string|null; reason?:string }
export type CompanySubscription = { subscriptionId:string; companyId:string; planId:string; planPriceId?:string|null; planCode:string; planName:string; status:SubscriptionStatus; startedAt:string; endsAt?:string|null; currentPeriodStart?:string|null; currentPeriodEnd?:string|null; billingAnchor?:string|null; trialEndsAt?:string|null; graceEndsAt?:string|null; cancelAtPeriodEnd:boolean; cancelledAt?:string|null; cancellationEffectiveAt?:string|null; paymentPolicy:PaymentPolicy; externalContractReference?:string|null }
export type AssignCompanySubscriptionInput = { planId:string; planPriceId?:string|null; status?:SubscriptionStatus; startedAt?:string|null; endsAt?:string|null; trialEndsAt?:string|null; currentPeriodEnd?:string|null }
export type PlanAssignment = { assignmentId:string; planId:string; companyId:string; assignedAt:string; assignedByUserId:string; effectiveFrom?:string|null; effectiveTo?:string|null; revokedAt?:string|null; revokedByUserId?:string|null; reason?:string|null }
export type PlanAssignmentInput = { effectiveFrom?:string|null; effectiveTo?:string|null; reason?:string }
export type GrantSubscriptionInput = { planId:string; planPriceId?:string|null; status?:SubscriptionStatus; startedAt?:string|null; endsAt?:string|null; trialEndsAt?:string|null; currentPeriodEnd?:string|null; paymentPolicy:Extract<PaymentPolicy,'COMPED'|'OFFLINE_CONTRACT'>; reason:string; externalContractReference?:string }

export type ManagedUsageTotals = { emailMessages: number; smsSegments: number; whatsappMessages: number; managedChargesInr: number }
export type ManagedCompanyUsage = ManagedUsageTotals & { companyId: string }
export type ManagedDailyUsage = ManagedUsageTotals & { day: string }
export type ManagedUsageReport = { from: string; to: string; totals: ManagedUsageTotals; companies: ManagedCompanyUsage[]; daily: ManagedDailyUsage[] }

export type ManagedBillingSku = 'MANAGED_EMAIL_OUTBOUND' | 'MANAGED_SMS_SEGMENT'
export type GlobalManagedRate = { rateId: string; billingSku: ManagedBillingSku; price: number; currency: 'INR'; effectiveFrom: string; effectiveTo: string | null; createdByUserId: string | null; reason: string | null }
export type GlobalManagedRateInput = { billingSku: ManagedBillingSku; price: number; effectiveFrom?: string | null; reason?: string }

export type FinancialAmounts = { invoicedInr: number; collectedInr: number; refundedInr: number; openBalanceInr: number; invoicedPlanLinesInr: number; invoicedManagedLinesInr: number }
export type CompanyFinancial = FinancialAmounts & { companyId: string }
export type DailyFinancial = Pick<FinancialAmounts, 'invoicedInr' | 'collectedInr' | 'refundedInr'> & { day: string }
export type BillingFinancialReport = { from: string; to: string; totals: FinancialAmounts; companies: CompanyFinancial[]; daily: DailyFinancial[] }

export type ChannelTrafficPoint = { companyId: string; day: string; channel: ChannelType; direction: CommunicationDirection; messages: number }
export type BillingChannelTrafficReport = { from: string; to: string; points: ChannelTrafficPoint[] }
