import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type {
  CreateUserPayload,
  ResetPasswordPayload,
  UpdateOwnProfilePayload,
  UpdateUserPayload,
  User,
  UserPreferences,
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

  async preferences() {
    const response = await apiClient.get<ApiResponse<UserPreferences>>('/api/users/me/preferences')
    return unwrap(response.data)
  },

  async updatePreferences(appearanceMode: 'LIGHT' | 'DARK' | 'SYSTEM') {
    const response = await apiClient.patch<ApiResponse<UserPreferences>>(
      '/api/users/me/preferences',
      { appearanceMode },
    )
    return unwrap(response.data)
  },

  async updateProfile(payload: UpdateOwnProfilePayload) {
    const response = await apiClient.patch<ApiResponse<User>>('/api/users/me/profile', payload)
    return unwrap(response.data)
  },

  async get(userId: string) {
    const response = await apiClient.get<ApiResponse<User>>(`/api/users/${userId}`)
    return unwrap(response.data)
  },

  async create(payload: CreateUserPayload) {
    const response = await apiClient.post<ApiResponse<User>>('/api/users', payload)
    return unwrap(response.data)
  },

  async update(userId: string, payload: UpdateUserPayload) {
    const response = await apiClient.patch<ApiResponse<User>>(`/api/users/${userId}`, payload)
    return unwrap(response.data)
  },

  async resetPassword(userId: string, payload: ResetPasswordPayload) {
    const response = await apiClient.patch<ApiResponse<User>>(
      `/api/users/${userId}/password`,
      payload,
    )
    return unwrap(response.data)
  },

  async deleteCurrentAccount(currentPassword: string) {
    await apiClient.delete<ApiResponse<void>>('/api/users/me', {
      data: { currentPassword },
    })
  },
}
