export type StorageUsageType =
  | 'MESSAGE'
  | 'MESSAGE_ATTACHMENT'
  | 'EMAIL_ATTACHMENT'
  | 'WHATSAPP_MEDIA'
  | 'SMS_ATTACHMENT'
  | 'TEMPLATE_MEDIA'
  | 'COMPANY_MEDIA'
  | 'CONTACT_DATA'
  | 'AD_CREATIVE'
  | 'EXPORT'
  | 'OTHER'

export type StorageUsageBreakdown = {
  type: StorageUsageType
  usedBytes: number
  reservedBytes: number
}

export type StorageUsage = {
  companyId: string
  usedBytes: number
  reservedBytes: number
  limitBytes: number
  remainingBytes: number
  usedPercent: number
  breakdown: StorageUsageBreakdown[]
}
