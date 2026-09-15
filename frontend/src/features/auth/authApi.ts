import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type { AuthUser, LoginPayload, RegisterPayload } from './types'

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
    const response = await apiClient.post<ApiResponse<AuthUser>>('/api/auth/login', payload)
    return unwrap(response.data)
  },

  async refresh() {
    const response = await apiClient.post<ApiResponse<AuthUser>>('/api/auth/refresh')
    return unwrap(response.data)
  },

  async verifyEmail(otp: string) {
    const response = await apiClient.post<ApiResponse<AuthUser>>('/api/auth/verify-email', { otp })
    return unwrap(response.data)
  },

  async logout() {
    await apiClient.post<ApiResponse<void>>('/api/auth/logout')
  },
}
