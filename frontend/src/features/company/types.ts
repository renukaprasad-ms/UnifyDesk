export type CompanyRole = 'OWNER' | 'MANAGER' | 'MEMBER'

export type CompanyStatus = 'ACTIVE' | 'SUSPENDED' | 'DELETED'

export type Company = {
  companyId: string
  name: string
  slug: string
  businessEmail?: string | null
  country?: string | null
  timezone?: string | null
  status: CompanyStatus
  role: CompanyRole
}

export type CreateCompanyPayload = {
  name: string
  businessEmail?: string
  country?: string
  timezone?: string
}
