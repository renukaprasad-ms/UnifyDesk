import type { BroadcastSender } from '../broadcast/types'

export type ManagedSmsCustomerState =
  | 'ACTION_REQUIRED'
  | 'WAITING_DLT_APPROVAL'
  | 'WAITING_UNIFYDESK_REVIEW'
  | 'PROVIDER_SYNCING'
  | 'READY'
  | 'REJECTED'
  | 'SUSPENDED'

export type ManagedSmsMessageCategory =
  | 'PROMOTIONAL'
  | 'SERVICE'
  | 'TRANSACTIONAL'
  | 'GOVERNMENT'

export type ManagedSmsDltPlatform = 'AIRTEL' | 'JIO' | 'VIL' | 'BSNL' | 'OTHER'

export type ManagedSmsStatusUsage = {
  periodStart?: string | null
  periodEnd?: string | null
  acceptedSegments: number
  limit?: number | null
}

export type ManagedSmsStatus = {
  enabled: boolean
  state: ManagedSmsCustomerState
  nextAction?: string | null
  dltProfile?: {
    id: string
    peIdMasked?: string | null
    state?: string | null
    chainState?: string | null
  } | null
  readyHeaderCount: number
  readyTemplateCount: number
  sendingEnabled: boolean
  usage: ManagedSmsStatusUsage
}

export type ManagedSmsDltProfile = {
  id: string
  legalName?: string | null
  dltPlatform?: ManagedSmsDltPlatform | null
  peIdMasked?: string | null
  state?: string | null
  chainState?: string | null
  actionRequired?: string | null
  updatedAt?: string | null
}

export type ManagedSmsHeader = {
  id: string
  header: string
  messageCategory: ManagedSmsMessageCategory
  state: string
  providerSyncState?: 'NOT_STARTED' | 'SYNCING' | 'READY' | 'FAILED' | string | null
  actionRequired?: string | null
  updatedAt?: string | null
}

export type ManagedSmsTemplateVariable = {
  key: string
  type: string
  maxLength: number
}

export type ManagedSmsTemplateVersion = {
  id: string
  version: number
  dltTemplateId: string
  bodyText: string
  variables: ManagedSmsTemplateVariable[]
  allowedHeaderIds: string[]
  providerSyncState?: string | null
}

export type ManagedSmsTemplate = {
  id: string
  name: string
  messageCategory: ManagedSmsMessageCategory
  state: string
  activeVersion?: ManagedSmsTemplateVersion | null
  actionRequired?: string | null
  updatedAt?: string | null
}

export type ManagedSmsUsage = {
  periodStart?: string | null
  periodEnd?: string | null
  acceptedSegments: number
  reservedSegments: number
  unknownSegments: number
  limit?: number | null
  remaining?: number | null
  sendingEnabled: boolean
}

export type ManagedSmsDeliveryHealth = {
  status: 'HEALTHY' | 'DEGRADED' | 'SUSPENDED' | string
  delivered: number
  failed: number
  rejected: number
  unknown: number
  actionRequired?: string | null
}

export type ManagedSmsPreviewValidationError = {
  field?: string | null
  message: string
}

export type ManagedSmsPreview = {
  bodyText?: string | null
  renderedBody?: string | null
  validationErrors: ManagedSmsPreviewValidationError[]
}

export type CreateManagedSmsDltProfileInput = {
  legalName: string
  dltPlatform: ManagedSmsDltPlatform
  peId: string
  tmChainReference?: string | null
  evidence?: Record<string, string>
}

export type CreateManagedSmsHeaderInput = {
  header: string
  messageCategory: ManagedSmsMessageCategory
}

export type CreateManagedSmsTemplateInput = {
  name: string
  headerIds: string[]
  messageCategory: ManagedSmsMessageCategory
  dltTemplateId: string
  bodyText: string
  variables: ManagedSmsTemplateVariable[]
  ctaPolicy: {
    urlVariables: string[]
    callbackVariables: string[]
  }
  consentPolicy: null
  optOutPolicy: null
}

export type ManagedSmsSender = BroadcastSender
