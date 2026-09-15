import axios from 'axios'
import apiClient, { type ApiRequestConfig } from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type { InitiateMediaUploadResponse, MediaAccessResponse, MediaPurpose } from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (!response.data) {
    throw new Error(response.error_message ?? 'Empty API response')
  }
  return response.data
}

export const mediaApi = {
  async initiateUpload(file: File, purpose: MediaPurpose, skipAuthRefresh = false) {
    const config: ApiRequestConfig | undefined = skipAuthRefresh ? { _skipAuthRefresh: true } : undefined
    const initiate = await apiClient.post<ApiResponse<InitiateMediaUploadResponse>>(
      '/api/v1/media/uploads',
      {
        fileName: file.name,
        contentType: file.type,
        sizeBytes: file.size,
        purpose,
      },
      config,
    )
    return unwrap(initiate.data)
  },

  async initiateProfilePictureUpload(file: File) {
    return mediaApi.initiateUpload(file, 'USER_PROFILE', true)
  },

  async initiateCompanyLogoUpload(file: File) {
    return mediaApi.initiateUpload(file, 'COMPANY_LOGO')
  },

  async uploadFileToStorage(
    upload: InitiateMediaUploadResponse,
    file: File,
    onProgress?: (progress: number) => void,
  ) {
    await axios.put(upload.uploadUrl, file, {
      headers: upload.requiredHeaders,
      onUploadProgress: (event) => {
        if (!event.total) {
          return
        }
        onProgress?.(Math.round((event.loaded / event.total) * 100))
      },
    })
  },

  async access(mediaId: string) {
    const response = await apiClient.get<ApiResponse<MediaAccessResponse>>(
      `/api/v1/media/${mediaId}/access`,
    )
    return unwrap(response.data)
  },

  async completeUpload(mediaId: string) {
    const response = await apiClient.post<ApiResponse<unknown>>(`/api/v1/media/${mediaId}/complete`)
    return unwrap(response.data)
  },
}
