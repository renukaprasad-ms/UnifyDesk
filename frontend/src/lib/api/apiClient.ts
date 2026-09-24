import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { sessionCleared, setAuthUser } from '../../store/auth/authSlice'
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

const sessionClient = axios.create({
  baseURL,
  withCredentials: true,
  timeout: 15000,
})

let refreshPromise: Promise<AuthUser> | null = null
let csrfPromise: Promise<string> | null = null

const isMutation = (method?: string) =>
  ['post', 'put', 'patch', 'delete'].includes(method?.toLowerCase() ?? '')

const ensureCsrfToken = async () => {
  csrfPromise ??= sessionClient
    .get<ApiResponse<string>>('/api/auth/csrf')
    .then((response) => {
      const token = response.data.data
      if (!token) throw new Error('CSRF token response was empty')
      return token
    })
    .finally(() => {
      csrfPromise = null
    })

  return csrfPromise
}

apiClient.interceptors.request.use(async (config) => {
  if (isMutation(config.method) && !config.url?.includes('/api/auth/csrf')) {
    const csrfToken = await ensureCsrfToken()
    config.headers.set('X-XSRF-TOKEN', csrfToken)
  }
  return config
})

const refreshSession = async () => {
  const response = await sessionClient.post<ApiResponse<AuthUser>>('/api/auth/refresh')
  const user = response.data.data
  if (!user) throw new Error('Refresh response did not include a user')
  store.dispatch(setAuthUser(user))
  return user
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiResponse<never>>) => {
    const originalRequest = error.config as RetryableRequestConfig | undefined
    const unauthorized = error.response?.status === 401
    const refreshRequest = originalRequest?.url?.includes('/api/auth/refresh')

    if (
      !originalRequest ||
      !unauthorized ||
      originalRequest._retry ||
      originalRequest._skipAuthRefresh ||
      refreshRequest
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
      store.dispatch(sessionCleared())
      return Promise.reject(refreshError)
    }
  },
)

const cleanApiErrorMessage = (message: string | undefined, fallback: string) => {
  if (!message) return fallback

  let cleaned = message.replace(/\\s+/g, ' ').trim()
  if (
    cleaned.includes('Type definition error') ||
    cleaned.includes('com.fasterxml.jackson.databind.JsonNode')
  ) {
    return fallback
  }

  const rawJsonMatch = cleaned.match(/\\s*\\(HTTP\\s+\\d+\\)\\s*:\\s*[\\[{]/i)
  if (rawJsonMatch?.index !== undefined) {
    cleaned = cleaned.slice(0, rawJsonMatch.index).trim()
  } else {
    const jsonIndex = [cleaned.indexOf(': {'), cleaned.indexOf(': [')]
      .filter((index) => index >= 0)
      .sort((a, b) => a - b)[0]
    if (jsonIndex !== undefined) {
      cleaned = cleaned.slice(0, jsonIndex).trim()
    }
  }

  if (!cleaned) return fallback
  return cleaned.length > 320 ? cleaned.slice(0, 317) + '...' : cleaned
}

export const apiErrorMessage = (error: unknown, fallback = 'Something went wrong') => {
  if (axios.isAxiosError<ApiResponse<never>>(error)) {
    return cleanApiErrorMessage(error.response?.data?.error_message, fallback)
  }
  return fallback
}

export default apiClient
