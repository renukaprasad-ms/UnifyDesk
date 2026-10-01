import type {
  ManagedSmsCustomerState,
  ManagedSmsDeliveryHealth,
  ManagedSmsDltProfile,
  ManagedSmsHeader,
  ManagedSmsMessageCategory,
  ManagedSmsPreview,
  ManagedSmsStatus,
  ManagedSmsTemplate,
  ManagedSmsTemplateVariable,
  ManagedSmsTemplateVersion,
  ManagedSmsUsage,
} from './types'

type UnknownRecord = Record<string, unknown>

const asRecord = (value: unknown): UnknownRecord =>
  value !== null && typeof value === 'object' && !Array.isArray(value)
    ? (value as UnknownRecord)
    : {}

const text = (value: unknown) => (typeof value === 'string' ? value : '')
const nullableText = (value: unknown) => (typeof value === 'string' ? value : null)
const number = (value: unknown) => (typeof value === 'number' && Number.isFinite(value) ? value : 0)
const nullableNumber = (value: unknown) =>
  typeof value === 'number' && Number.isFinite(value) ? value : null
const bool = (value: unknown) => value === true

const customerStates = new Set<ManagedSmsCustomerState>([
  'ACTION_REQUIRED',
  'WAITING_DLT_APPROVAL',
  'WAITING_UNIFYDESK_REVIEW',
  'PROVIDER_SYNCING',
  'READY',
  'REJECTED',
  'SUSPENDED',
])

const safeCustomerState = (value: unknown): ManagedSmsCustomerState => {
  const candidate = text(value) as ManagedSmsCustomerState
  return customerStates.has(candidate) ? candidate : 'ACTION_REQUIRED'
}

const safeCategory = (value: unknown): ManagedSmsMessageCategory => {
  const candidate = text(value)
  if (
    candidate === 'PROMOTIONAL' ||
    candidate === 'SERVICE' ||
    candidate === 'TRANSACTIONAL' ||
    candidate === 'GOVERNMENT'
  ) {
    return candidate
  }
  return 'SERVICE'
}

export const sanitizeManagedSmsStatus = (value: unknown): ManagedSmsStatus => {
  const raw = asRecord(value)
  const rawProfile = raw.dltProfile ? asRecord(raw.dltProfile) : null
  const rawUsage = asRecord(raw.usage)

  return {
    enabled: bool(raw.enabled),
    state: safeCustomerState(raw.state),
    nextAction: nullableText(raw.nextAction),
    dltProfile: rawProfile
      ? {
          id: text(rawProfile.id),
          peIdMasked: nullableText(rawProfile.peIdMasked),
          state: nullableText(rawProfile.state),
          chainState: nullableText(rawProfile.chainState),
        }
      : null,
    readyHeaderCount: number(raw.readyHeaderCount),
    readyTemplateCount: number(raw.readyTemplateCount),
    sendingEnabled: bool(raw.sendingEnabled),
    usage: {
      periodStart: nullableText(rawUsage.periodStart),
      periodEnd: nullableText(rawUsage.periodEnd),
      acceptedSegments: number(rawUsage.acceptedSegments),
      limit: nullableNumber(rawUsage.limit),
    },
  }
}

export const sanitizeManagedSmsDltProfile = (value: unknown): ManagedSmsDltProfile => {
  const raw = asRecord(value)
  return {
    id: text(raw.id),
    legalName: nullableText(raw.legalName),
    dltPlatform:
      raw.dltPlatform === 'AIRTEL' ||
      raw.dltPlatform === 'JIO' ||
      raw.dltPlatform === 'VIL' ||
      raw.dltPlatform === 'BSNL' ||
      raw.dltPlatform === 'OTHER'
        ? raw.dltPlatform
        : null,
    peIdMasked: nullableText(raw.peIdMasked),
    state: nullableText(raw.state),
    chainState: nullableText(raw.chainState),
    actionRequired: nullableText(raw.actionRequired),
    updatedAt: nullableText(raw.updatedAt),
  }
}

export const sanitizeManagedSmsHeader = (value: unknown): ManagedSmsHeader => {
  const raw = asRecord(value)
  return {
    id: text(raw.id),
    header: text(raw.header),
    messageCategory: safeCategory(raw.messageCategory),
    state: text(raw.state),
    providerSyncState: nullableText(raw.providerSyncState),
    actionRequired: nullableText(raw.actionRequired),
    updatedAt: nullableText(raw.updatedAt),
  }
}

const sanitizeVariable = (value: unknown): ManagedSmsTemplateVariable => {
  const raw = asRecord(value)
  return {
    key: text(raw.key),
    type: text(raw.type) || 'ALPHANUMERIC',
    maxLength: Math.max(1, number(raw.maxLength) || 100),
  }
}

const sanitizeVersion = (value: unknown): ManagedSmsTemplateVersion => {
  const raw = asRecord(value)
  return {
    id: text(raw.id),
    version: number(raw.version),
    dltTemplateId: text(raw.dltTemplateId),
    bodyText: text(raw.bodyText),
    variables: Array.isArray(raw.variables) ? raw.variables.map(sanitizeVariable) : [],
    allowedHeaderIds: Array.isArray(raw.allowedHeaderIds)
      ? raw.allowedHeaderIds.map(text).filter(Boolean)
      : [],
    providerSyncState: nullableText(raw.providerSyncState),
  }
}

export const sanitizeManagedSmsTemplate = (value: unknown): ManagedSmsTemplate => {
  const raw = asRecord(value)
  return {
    id: text(raw.id),
    name: text(raw.name),
    messageCategory: safeCategory(raw.messageCategory),
    state: text(raw.state),
    activeVersion: raw.activeVersion ? sanitizeVersion(raw.activeVersion) : null,
    actionRequired: nullableText(raw.actionRequired),
    updatedAt: nullableText(raw.updatedAt),
  }
}

export const sanitizeManagedSmsUsage = (value: unknown): ManagedSmsUsage => {
  const raw = asRecord(value)
  return {
    periodStart: nullableText(raw.periodStart),
    periodEnd: nullableText(raw.periodEnd),
    acceptedSegments: number(raw.acceptedSegments),
    reservedSegments: number(raw.reservedSegments),
    unknownSegments: number(raw.unknownSegments),
    limit: nullableNumber(raw.limit),
    remaining: nullableNumber(raw.remaining),
    sendingEnabled: bool(raw.sendingEnabled),
  }
}

export const sanitizeManagedSmsDeliveryHealth = (value: unknown): ManagedSmsDeliveryHealth => {
  const raw = asRecord(value)
  return {
    status: text(raw.status) || 'DEGRADED',
    delivered: number(raw.delivered),
    failed: number(raw.failed),
    rejected: number(raw.rejected),
    unknown: number(raw.unknown),
    actionRequired: nullableText(raw.actionRequired),
  }
}

export const sanitizeManagedSmsPreview = (value: unknown): ManagedSmsPreview => {
  const raw = asRecord(value)
  const errors = Array.isArray(raw.validationErrors) ? raw.validationErrors : []
  return {
    bodyText: nullableText(raw.bodyText),
    renderedBody: nullableText(raw.renderedBody),
    validationErrors: errors.map((item) => {
      const error = asRecord(item)
      return {
        field: nullableText(error.field),
        message: text(error.message) || 'This value is not valid.',
      }
    }),
  }
}

export const sanitizeManagedSmsHeaders = (value: unknown) =>
  Array.isArray(value) ? value.map(sanitizeManagedSmsHeader) : []

export const sanitizeManagedSmsTemplates = (value: unknown) =>
  Array.isArray(value) ? value.map(sanitizeManagedSmsTemplate) : []
