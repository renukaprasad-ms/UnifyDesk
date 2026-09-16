export type User = {
  id: number
  email: string
  name: string
  verified: boolean
  username: string
  twoFactorEnabled: boolean
}

export type CreateUserPayload = {
  email: string
  name: string
  username: string
  password: string
  twoFactorEnabled: boolean
}

export type UpdateUserPayload = {
  email?: string
  name?: string
  username?: string
  verified?: boolean
}

export type ResetPasswordPayload = {
  password: string
}

export type ToggleTwoFactorPayload = {
  enabled: boolean
}
