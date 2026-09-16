export type CompanyRole = 'OWNER' | 'MANAGER' | 'MEMBER'

export type CompanyStatus = 'ACTIVE' | 'SUSPENDED' | 'DELETED'

export type CompanyPermission =
  | 'COMPANY_VIEW'
  | 'COMPANY_UPDATE'
  | 'MEMBER_INVITE'
  | 'MEMBER_REMOVE'
  | 'MEMBER_ROLE_UPDATE'
  | 'BILLING_VIEW'
  | 'BILLING_MANAGE'
  | 'BROADCAST_CREATE'
  | 'BROADCAST_APPROVE'
  | 'TEMPLATE_CREATE'
  | 'TEMPLATE_APPROVE'
  | 'MESSAGE_VIEW'
  | 'MESSAGE_SEND'
  | 'ADS_VIEW'
  | 'ADS_MANAGE'
  | 'SETTINGS_VIEW'
  | 'SETTINGS_MANAGE'
  | 'MEDIA_UPLOAD'

export type Company = {
  companyId: string
  name: string
  slug: string
  businessEmail?: string | null
  country?: string | null
  timezone?: string | null
  logoMediaId?: string | null
  status: CompanyStatus
  role: CompanyRole
}

export type CompanyPermissionResponse = {
  companyId: string
  permissions: CompanyPermission[]
}

export type CompanyUser = {
  memberId: string
  userId: string
  name: string
  username: string
  email: string
  verified: boolean
  twoFactorEnabled: boolean
  role: CompanyRole
  status: 'ACTIVE' | 'INVITED' | 'SUSPENDED' | 'LEFT'
  joinedAt: string
}

export type CreateCompanyPayload = {
  name: string
  businessEmail?: string
  country?: string
  timezone?: string
  logoMediaId?: string
}
