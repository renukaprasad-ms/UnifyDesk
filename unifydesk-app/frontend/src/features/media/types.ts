export type MediaPurpose =
  | 'USER_PROFILE'
  | 'USER_COVER'
  | 'MESSAGE_ATTACHMENT'
  | 'EMAIL_ATTACHMENT'
  | 'WHATSAPP_MEDIA'
  | 'COMPANY_LOGO'
  | 'TEMPLATE_MEDIA'
  | 'AD_CREATIVE'
  | 'OTHER'

export type InitiateMediaUploadResponse = {
  mediaId: string
  uploadToken?: string | null
  uploadUrl: string
  expiresAt: string
  requiredHeaders: Record<string, string>
}

export type MediaResponse = {
  mediaId: string
  ownerUserId?: string
  storageProvider?: 'SUPABASE' | 'S3'
  bucket?: string
  objectKey?: string
  originalFileName?: string
  contentType?: string
  sizeBytes?: number
  mediaType?: 'IMAGE' | 'VIDEO' | 'AUDIO' | 'DOCUMENT' | 'OTHER'
  purpose?: MediaPurpose
  status: 'PENDING' | 'ACTIVE' | 'FAILED' | 'DELETE_PENDING' | 'DELETED'
  createdAt?: string
  completedAt?: string | null
  deletedAt?: string | null
}

export type MediaAccessResponse = {
  url: string
  expiresAt: string
}
