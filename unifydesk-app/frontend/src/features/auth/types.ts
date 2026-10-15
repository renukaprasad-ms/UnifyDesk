export type AuthUser = {
  userId: string
  name: string
  username: string
  email: string
  profilePictureMediaId?: string | null
  verified: boolean
  hasCompanies: boolean
  companyCount: number
  appearanceMode: 'LIGHT' | 'DARK' | 'SYSTEM'
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
  identifier: string
  password: string
}

export type PasswordResetChallenge = {
  challengeId: string
  expiresInSeconds: number
}

export type LoginResponse =
  | { status: 'AUTHENTICATED'; user: AuthUser; preAuthToken: null; expiresInSeconds: number }
  | { status: 'TWO_FACTOR_REQUIRED'; user: null; preAuthToken: string; expiresInSeconds: number }
