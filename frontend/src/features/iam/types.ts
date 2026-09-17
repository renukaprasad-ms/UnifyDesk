import type { PermissionKey } from './permissions'

export type CompanyIamPermissions = {
  companyId: string | number
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
