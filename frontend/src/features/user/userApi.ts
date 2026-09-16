import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type {
  CreateUserPayload,
  ResetPasswordPayload,
  ToggleTwoFactorPayload,
  UpdateUserPayload,
  User,
} from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (!response.data) {
    throw new Error(response.error_message ?? 'Empty API response')
  }
  return response.data
}

export const userApi = {
  async list() {
    const response = await apiClient.get<ApiResponse<User[]>>('/api/users')
    return unwrap(response.data)
  },

  async me() {
    const response = await apiClient.get<ApiResponse<User>>('/api/users/me')
    return unwrap(response.data)
  },

  async get(userId: number) {
    const response = await apiClient.get<ApiResponse<User>>(`/api/users/${userId}`)
    return unwrap(response.data)
  },

  async create(payload: CreateUserPayload) {
    const response = await apiClient.post<ApiResponse<User>>('/api/users', payload)
    return unwrap(response.data)
  },

  async update(userId: number, payload: UpdateUserPayload) {
    const response = await apiClient.patch<ApiResponse<User>>(`/api/users/${userId}`, payload)
    return unwrap(response.data)
  },

  async resetPassword(userId: number, payload: ResetPasswordPayload) {
    const response = await apiClient.patch<ApiResponse<User>>(`/api/users/${userId}/password`, payload)
    return unwrap(response.data)
  },

  async toggleTwoFactor(userId: number, payload: ToggleTwoFactorPayload) {
    const response = await apiClient.patch<ApiResponse<User>>(`/api/users/${userId}/2fa`, payload)
    return unwrap(response.data)
  },

  async delete(userId: number) {
    await apiClient.delete<ApiResponse<void>>(`/api/users/${userId}`)
  },
}
