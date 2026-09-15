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
  async initiateProfilePictureUpload(file: File) {
    const skipAuthRefreshConfig: ApiRequestConfig = { _skipAuthRefresh: true }
    const initiate = await apiClient.post<ApiResponse<InitiateMediaUploadResponse>>(
      '/api/v1/media/uploads',
      {
        fileName: file.name,
        contentType: file.type,
        sizeBytes: file.size,
        purpose: 'USER_PROFILE' satisfies MediaPurpose,
      },
      skipAuthRefreshConfig,
    )
    return unwrap(initiate.data)
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
}
