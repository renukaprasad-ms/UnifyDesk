import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type {
  AuthUser,
  LoginPayload,
  LoginResponse,
  PasswordResetChallenge,
  RegisterPayload,
} from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (!response.data) {
    throw new Error(response.error_message ?? 'Empty API response')
  }
  return response.data
}

export const authApi = {
  async register(payload: RegisterPayload) {
    const response = await apiClient.post<ApiResponse<AuthUser>>('/api/auth/register', payload)
    return unwrap(response.data)
  },

  async login(payload: LoginPayload) {
    const response = await apiClient.post<ApiResponse<LoginResponse>>('/api/auth/login', payload)
    return unwrap(response.data)
  },

  async verifyTwoFactor(preAuthToken: string, otp: string) {
    const response = await apiClient.post<ApiResponse<AuthUser>>('/api/auth/2fa/verify', {
      preAuthToken,
      otp,
    })
    return unwrap(response.data)
  },

  async requestPasswordReset(identifier: string) {
    const response = await apiClient.post<ApiResponse<PasswordResetChallenge>>(
      '/api/auth/password-reset/request',
      { identifier },
    )
    return unwrap(response.data)
  },

  async verifyPasswordResetOtp(challengeId: string, otp: string) {
    await apiClient.post<ApiResponse<void>>('/api/auth/password-reset/verify', {
      challengeId,
      otp,
    })
  },

  async completePasswordReset(password: string) {
    await apiClient.post<ApiResponse<void>>('/api/auth/password-reset/complete', { password })
  },

  async refresh() {
    const response = await apiClient.post<ApiResponse<AuthUser>>('/api/auth/refresh')
    return unwrap(response.data)
  },

  async verifyEmail(otp: string) {
    const response = await apiClient.post<ApiResponse<AuthUser>>('/api/auth/verify-email', { otp })
    return unwrap(response.data)
  },

  async resendVerificationEmail() {
    await apiClient.post<ApiResponse<void>>('/api/auth/verify-email/resend')
  },

  async beginTwoFactorSettingsChange(currentPassword: string, enabled: boolean) {
    const response = await apiClient.post<
      ApiResponse<{ challengeToken: string; expiresInSeconds: number }>
    >('/api/auth/2fa/settings/challenge', { currentPassword, enabled })
    return unwrap(response.data)
  },

  async completeTwoFactorSettingsChange(challengeToken: string, otp: string) {
    await apiClient.post<ApiResponse<void>>('/api/auth/2fa/settings/complete', {
      challengeToken,
      otp,
    })
  },

  async logout() {
    await apiClient.post<ApiResponse<void>>('/api/auth/logout')
  },
}
