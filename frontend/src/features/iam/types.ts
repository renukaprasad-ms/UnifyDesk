import type { PermissionKey } from './permissions'

export type CompanyIamPermissions = {
  companyId: string
  owner: boolean
  permissions: PermissionKey[]
}

export type PermissionDefinition = {
  key: PermissionKey
  resource: string
  action: string
  description: string
  assignable: boolean
}

export type CompanyRole = {
  id: string
  companyId: string
  name: string
  description?: string | null
  systemRole: boolean
  permissions: PermissionKey[]
  createdAt: string
  updatedAt: string
}

export type MemberRoleAssignment = {
  roleId: string
  roleName: string
  expiresAt?: string | null
  revokedAt?: string | null
  createdAt: string
}
