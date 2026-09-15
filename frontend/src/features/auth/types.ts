export type AuthUser = {
  userId: string
  username: string
  email: string
  verified: boolean
  hasCompanies: boolean
  companyCount: number
}

export type RegisterPayload = {
  email: string
  name: string
  username: string
  password: string
  profilePictureMediaId?: string
  profilePictureUploadToken?: string
}

export type LoginPayload = {
  email: string
  password: string
}
