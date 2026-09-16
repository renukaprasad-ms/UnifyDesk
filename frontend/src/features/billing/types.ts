export type Entitlement = {
  entitlementId: string
  key: string
  value: string
}

export type Plan = {
  planId: string
  code: string
  name: string
  status: 'ACTIVE' | 'ARCHIVED'
  billingCycle: 'FREE' | 'MONTHLY' | 'YEARLY'
  basePrice: number
  currency: string
  version: number
  entitlements: Entitlement[]
}

export type CompanySubscription = {
  subscriptionId: string
  companyId: string
  planId: string
  planCode: string
  planName: string
  status: 'ACTIVE' | 'TRIALING' | 'PAST_DUE' | 'CANCELLED'
  startedAt: string
  endsAt?: string | null
}

export type CompanyPlanDetails = {
  subscription: CompanySubscription
  plan: Plan
}
