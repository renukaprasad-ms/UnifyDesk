export type BillingCycle = 'FREE' | 'MONTHLY' | 'YEARLY'
export type PlanVisibility = 'PUBLIC' | 'PRIVATE' | 'INTERNAL'
export type SubscriptionStatus =
  | 'TRIALING'
  | 'ACTIVE'
  | 'PAST_DUE'
  | 'SUSPENDED'
  | 'CANCELLED'
  | 'EXPIRED'
export type PaymentPolicy = 'CASHFREE' | 'COMPED' | 'OFFLINE_CONTRACT'
export type SubscriptionChangeType = 'UPGRADE' | 'DOWNGRADE' | 'REACTIVATION'
export type SubscriptionChangeStatus =
  | 'REQUESTED'
  | 'PAYMENT_PENDING'
  | 'SCHEDULED'
  | 'APPLIED'
  | 'CANCELLED'
  | 'FAILED'
export type PaymentStatus = 'CREATED' | 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'

export type Entitlement = {
  entitlementId: string
  key: string
  value: string
}

export type PlanPrice = {
  planPriceId: string
  planId: string
  billingCycle: BillingCycle
  amount: number
  currency: string
  effectiveFrom?: string | null
  effectiveTo?: string | null
  status: 'ACTIVE' | 'ARCHIVED'
}

export type Plan = {
  planId: string
  code: string
  name: string
  description?: string | null
  status: 'DRAFT' | 'PUBLISHED' | 'RETIRED'
  visibility: PlanVisibility
  billingCycle: BillingCycle
  basePrice: number
  currency: string
  version: number
  prices: PlanPrice[]
  entitlements: Entitlement[]
}

export type CompanySubscription = {
  subscriptionId: string
  companyId: string
  planId: string
  planPriceId?: string | null
  planCode: string
  planName: string
  status: SubscriptionStatus
  startedAt: string
  endsAt?: string | null
  currentPeriodStart?: string | null
  currentPeriodEnd?: string | null
  billingAnchor?: string | null
  trialEndsAt?: string | null
  graceEndsAt?: string | null
  cancelAtPeriodEnd: boolean
  cancelledAt?: string | null
  cancellationEffectiveAt?: string | null
  paymentPolicy: PaymentPolicy
  externalContractReference?: string | null
}

export type CompanyPlanDetails = {
  subscription: CompanySubscription | null
  plan: Plan | null
}

export type SubscriptionChange = {
  changeId: string
  companyId: string
  subscriptionId: string
  fromPlanId: string
  toPlanId: string
  type: SubscriptionChangeType
  status: SubscriptionChangeStatus
  requestedAt: string
  effectiveAt?: string | null
  quoteReference?: string | null
  invoiceId?: string | null
  currency?: string | null
  currentPrice?: number | null
  targetPrice?: number | null
  targetProratedCharge?: number | null
  unusedCurrentCredit?: number | null
  netAmount?: number | null
  amountDue?: number | null
  creditAmount?: number | null
}

export type InvoiceIdentity = {
  companyName?: string | null
  businessEmail?: string | null
  addressLine1?: string | null
  addressLine2?: string | null
  city?: string | null
  state?: string | null
  postalCode?: string | null
  country?: string | null
  taxId?: string | null
  taxRegistration?: string | null
}

export type CheckoutCustomer = {
  name?: string | null
  email: string
  phone: string
}

export type InitialCheckoutInput = {
  planId: string
  planPriceId: string
  idempotencyKey: string
  invoiceIdentity?: InvoiceIdentity | null
  customer: CheckoutCustomer
}

export type UpgradeCheckoutInput = {
  changeId: string
  quoteReference: string
  customer: CheckoutCustomer
}

export type CheckoutResponse = {
  paymentId: string
  invoiceId: string
  providerOrderId?: string | null
  paymentSessionId?: string | null
  amount: number
  currency: string
  status: PaymentStatus
  recurringAuthorizationSessionId?: string | null
  recurringAuthorizationActionUrl?: string | null
}
