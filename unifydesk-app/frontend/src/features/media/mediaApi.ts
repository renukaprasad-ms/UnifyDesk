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
  async initiateUpload(
    file: File,
    purpose: MediaPurpose,
    companyId?: string,
    skipAuthRefresh = false,
  ) {
    const config: ApiRequestConfig | undefined = skipAuthRefresh
      ? { _skipAuthRefresh: true }
      : undefined
    const initiate = await apiClient.post<ApiResponse<InitiateMediaUploadResponse>>(
      '/api/v1/media/uploads',
      {
        fileName: file.name,
        contentType: file.type,
        sizeBytes: file.size,
        purpose,
        companyId,
      },
      config,
    )
    return unwrap(initiate.data)
  },

  async initiateProfilePictureUpload(file: File) {
    return mediaApi.initiateUpload(file, 'USER_PROFILE', undefined, true)
  },

  async initiateCompanyLogoUpload(file: File, companyId?: string) {
    return mediaApi.initiateUpload(file, 'COMPANY_LOGO', companyId)
  },

  async initiateEmailAttachmentUpload(file: File, companyId: string) {
    return mediaApi.initiateUpload(file, 'EMAIL_ATTACHMENT', companyId)
  },

  async initiateTemplateMediaUpload(file: File, companyId: string) {
    return mediaApi.initiateUpload(file, 'TEMPLATE_MEDIA', companyId)
  },

  async initiateAdCreativeUpload(file: File, companyId: string) {
    return mediaApi.initiateUpload(file, 'AD_CREATIVE', companyId)
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

  async attachCurrentUserProfilePicture(mediaId: string) {
    const response = await apiClient.put<ApiResponse<{ userId: string; mediaId: string }>>(
      `/api/v1/users/me/profile-picture/${mediaId}`,
    )
    return unwrap(response.data)
  },

  async removeCurrentUserProfilePicture() {
    await apiClient.delete<ApiResponse<void>>('/api/v1/users/me/profile-picture')
  },

  async listCompanyMedia(companyId: string) {
    const response = await apiClient.get<ApiResponse<import('./types').MediaResponse[]>>(
      `/api/v1/media/company/${companyId}`,
    )
    return unwrap(response.data)
  },

  async uploadAdCreative(file: File, companyId: string, onProgress?: (progress: number) => void) {
    const upload = await mediaApi.initiateAdCreativeUpload(file, companyId)
    await mediaApi.uploadFileToStorage(upload, file, onProgress)
    await mediaApi.completeUpload(upload.mediaId)
    const access = await mediaApi.access(upload.mediaId)
    return { mediaId: upload.mediaId, access }
  },

  async uploadTemplateMedia(
    file: File,
    companyId: string,
    onProgress?: (progress: number) => void,
  ) {
    const upload = await mediaApi.initiateTemplateMediaUpload(file, companyId)
    await mediaApi.uploadFileToStorage(upload, file, onProgress)
    await mediaApi.completeUpload(upload.mediaId)
    const access = await mediaApi.access(upload.mediaId)
    return { mediaId: upload.mediaId, access }
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

  async delete(mediaId: string) {
    const response = await apiClient.delete<ApiResponse<unknown>>(`/api/v1/media/${mediaId}`)
    return unwrap(response.data)
  },
}
