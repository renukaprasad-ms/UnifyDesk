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
  | 'PENDING_PAYMENT'
  | 'SCHEDULED'
  | 'APPLIED'
  | 'CANCELLED'
  | 'FAILED'
export type PaymentStatus = 'CREATED' | 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'

export type Entitlement = { entitlementId: string; key: string; value: string }

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

export type CompanyPlanDetails = { subscription: CompanySubscription | null; plan: Plan | null }

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

export type CheckoutCustomer = { name?: string | null; email: string; phone: string }
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

export type AutoPayDetails = {
  authorizationId?: string | null
  status: 'ACTIVE' | 'PENDING_AUTH' | 'CANCELLED' | 'FAILED' | 'NOT_CONFIGURED'
  maxAmount: number
  currency: string
  providerSubscriptionId?: string | null
  subscriptionSessionId?: string | null
  paymentMethodType?: string | null
  actionUrl?: string | null
}

/** Maps raw AutoPay status codes to user-friendly labels */
export const autoPayStatusLabel: Record<AutoPayDetails['status'], string> = {
  ACTIVE: 'Active',
  PENDING_AUTH: 'Pending Approval',
  CANCELLED: 'Cancelled',
  FAILED: 'Failed',
  NOT_CONFIGURED: 'Not Configured',
}

export type AutoPaySetupInput = {
  name?: string | null
  email?: string | null
  phone?: string | null
  returnUrl?: string | null
  maxAmount?: number | null
}

export type CompanyWallet = {
  companyId: string
  balance: number
  currency: string
  autoRechargeEnabled: boolean
  autoRechargeThreshold: number
  autoRechargeAmount: number
  autoRechargeFailedAttempts?: number
  autoRechargePaused?: boolean
  updatedAt: string
}

export type WalletTransaction = {
  id: string
  companyId: string
  type: string
  amount: number
  balanceAfter: number
  currency: string
  referenceId?: string | null
  description: string
  actorUserId?: number | null
  createdAt: string
}

export type CompanyBillingProfile = {
  legalBusinessName?: string | null
  taxIdOrGstin?: string | null
  billingEmail?: string | null
  billingPhone?: string | null
  addressLine1?: string | null
  addressLine2?: string | null
  city?: string | null
  state?: string | null
  postalCode?: string | null
  country?: string | null
}

export type BillingUsageSummary = {
  seatsUsed: number
  seatsLimit: number
  contactsUsed: number
  contactsLimit: number
  emailMonthlyUsed: number
  emailMonthlyLimit: number
  smsMonthlyUsed: number
  smsMonthlyLimit: number
  walletBalance: number
  walletCurrency: string
}

export type Invoice = {
  invoiceId: string
  companyId: string
  billingPeriodId?: string | null
  invoiceNumber: string
  status: string
  currency: string
  subtotal: number
  discountAmount: number
  creditAmount: number
  adjustmentAmount: number
  taxAmount: number
  total: number
  amountDue: number
  servicePeriodStart?: string | null
  servicePeriodEnd?: string | null
  finalizedAt?: string | null
  dueAt?: string | null
  paidAt?: string | null
}

export type WalletTopupOrder = {
  orderId: string
  paymentSessionId?: string | null
  amount: number
  currency: string
  status: string
}

