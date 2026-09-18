export type PlatformPermission =
  | 'COMPANY_VIEW'
  | 'COMPANY_UPDATE'
  | 'COMPANY_SUSPEND'
  | 'USER_VIEW'
  | 'USER_UPDATE'
  | 'PLAN_VIEW'
  | 'PLAN_CREATE'
  | 'PLAN_UPDATE'
  | 'PLAN_DISABLE'
  | 'BILLING_VIEW'
  | 'BILLING_ADJUST'
  | 'ADMIN_VIEW'
  | 'ADMIN_CREATE'
  | 'ADMIN_ASSIGN'
  | 'AUDIT_VIEW'

export type PlatformAccess = {
  platformAdmin: boolean
  role: 'SUPER_ADMIN' | 'ADMIN' | null
  permissions: PlatformPermission[]
}

export type PlatformAdmin = {
  id: string
  userId: string
  name: string
  email: string
  role: 'SUPER_ADMIN' | 'ADMIN'
  status: 'ACTIVE' | 'SUSPENDED'
  permissions: PlatformPermission[]
}
export type PlatformCompany = {
  id: string
  name: string
  slug: string
  businessEmail?: string
  country?: string
  timezone?: string
  status: 'ACTIVE' | 'SUSPENDED' | 'DELETED'
}
export type Page<T> = {
  content: T[]
  number: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export type PlatformUser = {
  id: string
  name: string
  email: string
  username: string
  verified: boolean
  twoFactorEnabled: boolean
}
export type PlatformPlan = {
  id: string
  code: string
  name: string
  status: 'ACTIVE' | 'ARCHIVED'
  billingCycle: 'FREE' | 'MONTHLY' | 'YEARLY'
  basePrice: number
  currency: string
  version: number
  entitlements: Record<string, string>
}
export type PlatformPlanInput = Omit<PlatformPlan, 'id' | 'status'>
