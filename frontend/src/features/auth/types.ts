export type AuthUser = {
  userId: string
  name: string
  username: string
  email: string
  profilePictureMediaId?: string | null
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
