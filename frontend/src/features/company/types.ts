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
  role: CompanyRole | null
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
  customRoles: string[]
}

export type CompanyInvitationStatus = 'PENDING' | 'ACCEPTED' | 'REVOKED' | 'EXPIRED'

export type CompanyInvitationRole = {
  roleId: string
  name: string
}

export type CompanyInvitation = {
  invitationId: string
  companyId: string
  companyName: string
  email: string
  status: CompanyInvitationStatus
  roles: CompanyInvitationRole[]
  expiresAt: string
  createdAt: string
}

export type CreateCompanyInvitationPayload = {
  email: string
  roleIds: string[]
}

export type AcceptCompanyInvitationResponse = {
  companyId: string
  companyName: string
  memberId: string
}

export type CreateCompanyPayload = {
  name: string
  businessEmail?: string
  country?: string
  timezone?: string
  logoMediaId?: string
}
