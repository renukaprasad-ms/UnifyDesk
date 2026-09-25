export type Entitlement = {
  entitlementId: string
  key: string
  value: string
}

export type PlanPrice = {
  planPriceId: string
  planId: string
  billingCycle: 'FREE' | 'MONTHLY' | 'YEARLY'
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
  billingCycle: 'FREE' | 'MONTHLY' | 'YEARLY'
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
  status: 'ACTIVE' | 'TRIALING' | 'PAST_DUE' | 'CANCELLED'
  startedAt: string
  endsAt?: string | null
  currentPeriodStart?: string | null
  currentPeriodEnd?: string | null
  trialEndsAt?: string | null
  cancelAtPeriodEnd: boolean
  cancelledAt?: string | null
}

export type CompanyPlanDetails = {
  subscription: CompanySubscription | null
  plan: Plan | null
}
