export type PlatformPermission =
  | 'COMPANY_VIEW' | 'COMPANY_UPDATE' | 'COMPANY_SUSPEND'
  | 'USER_VIEW' | 'USER_UPDATE'
  | 'PLAN_VIEW' | 'PLAN_CREATE' | 'PLAN_UPDATE' | 'PLAN_DISABLE'
  | 'BILLING_VIEW' | 'BILLING_ADJUST'
  | 'ADMIN_VIEW' | 'ADMIN_CREATE' | 'ADMIN_ASSIGN' | 'AUDIT_VIEW'

export type PlatformAccess = {
  platformAdmin: boolean
  role: 'SUPER_ADMIN' | 'PLATFORM_ADMIN' | null
  permissions: PlatformPermission[]
}
