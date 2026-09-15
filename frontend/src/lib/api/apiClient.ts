import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { setAuthUser, clearAuthUser } from '../../store/user/userSlice'
import { store } from '../../store/store'
import type { AuthUser } from '../../features/auth/types'
import type { ApiResponse } from './types'

type RetryableRequestConfig = InternalAxiosRequestConfig & {
  _retry?: boolean
  _skipAuthRefresh?: boolean
}

export type ApiRequestConfig = AxiosRequestConfig & {
  _skipAuthRefresh?: boolean
}

const configuredBaseURL = import.meta.env.VITE_API_BASE_URL
const isBrowserLocalhost =
  window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'
const baseURL =
  configuredBaseURL && (isBrowserLocalhost || !configuredBaseURL.includes('localhost'))
    ? configuredBaseURL
    : ''

export const apiClient = axios.create({
  baseURL,
  withCredentials: true,
  timeout: 15000,
})

const refreshClient = axios.create({
  baseURL,
  withCredentials: true,
  timeout: 15000,
})

let refreshPromise: Promise<AuthUser> | null = null

const refreshSession = async () => {
  const response = await refreshClient.post<ApiResponse<AuthUser>>('/api/auth/refresh')
  const user = response.data.data
  if (!user) {
    throw new Error('Refresh response did not include a user')
  }
  store.dispatch(setAuthUser(user))
  return user
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiResponse<never>>) => {
    const originalRequest = error.config as RetryableRequestConfig | undefined
    const isUnauthorized = error.response?.status === 401
    const isRefreshRequest = originalRequest?.url?.includes('/api/auth/refresh')

    if (
      !originalRequest ||
      !isUnauthorized ||
      originalRequest._retry ||
      originalRequest._skipAuthRefresh ||
      isRefreshRequest
    ) {
      return Promise.reject(error)
    }

    originalRequest._retry = true
    try {
      refreshPromise ??= refreshSession().finally(() => {
        refreshPromise = null
      })
      await refreshPromise
      return apiClient(originalRequest)
    } catch (refreshError) {
      store.dispatch(clearAuthUser())
      return Promise.reject(refreshError)
    }
  },
)

export const apiErrorMessage = (error: unknown, fallback = 'Something went wrong') => {
  if (axios.isAxiosError<ApiResponse<never>>(error)) {
    return error.response?.data?.error_message ?? fallback
  }
  return fallback
}

export default apiClient
