export type CompanyRole = 'OWNER' | 'MANAGER' | 'MEMBER'

export type CompanyStatus = 'ACTIVE' | 'SUSPENDED' | 'DELETED'

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
