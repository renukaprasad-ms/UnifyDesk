import type {
  ContactDetail,
  ContactExportField,
  ContactPatchInput,
  ContactQuery,
  ContactSearchStatus,
  ContactSelection,
  ContactSummary,
  ContactWriteInput,
  ConsentImportDeclaration,
  ConsentImportMapping,
  CursorPage,
  ImportCommitInput,
  ImportFieldMapping,
  ImportFieldTarget,
} from './types'

export type ContactFormDraft = {
  displayName: string
  firstName: string
  lastName: string
  organization: string
  notes: string
  email: string
  phone: string
}

export const emptyContactDraft: ContactFormDraft = {
  displayName: '',
  firstName: '',
  lastName: '',
  organization: '',
  notes: '',
  email: '',
  phone: '',
}

export const DEFAULT_CONTACT_EXPORT_FIELDS: readonly ContactExportField[] = [
  'displayName',
  'organization',
]

export const parseContactSearchStatus = (value: string | null): ContactSearchStatus | undefined => {
  switch (value) {
    case 'ACTIVE':
    case 'MERGED':
      return value
    default:
      return undefined
  }
}

export const validateContactDraft = (draft: ContactFormDraft, validateIdentities = true) => {
  const errors: Partial<Record<keyof ContactFormDraft, string>> = {}
  if (!draft.displayName.trim()) errors.displayName = 'Display name is required.'
  if (draft.displayName.trim().length > 160) errors.displayName = 'Display name is too long.'
  if (
    validateIdentities &&
    draft.email.trim() &&
    !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(draft.email.trim())
  ) {
    errors.email = 'Enter a valid email address.'
  }
  if (validateIdentities && draft.phone.trim() && draft.phone.trim().length < 7) {
    errors.phone = 'Enter a complete phone number including country code.'
  }
  return errors
}

export const toCreateContactInput = (draft: ContactFormDraft): ContactWriteInput => {
  const identities: NonNullable<ContactWriteInput['identities']> = []
  if (draft.email.trim()) {
    identities.push({ type: 'EMAIL', value: draft.email.trim(), primary: true })
  }
  if (draft.phone.trim()) {
    identities.push({ type: 'PHONE', value: draft.phone.trim(), primary: true })
  }
  return {
    displayName: draft.displayName.trim(),
    firstName: draft.firstName.trim() || null,
    lastName: draft.lastName.trim() || null,
    organization: draft.organization.trim() || null,
    notes: draft.notes.trim() || null,
    identities,
  }
}

export const toPatchContactInput = (
  draft: ContactFormDraft,
  version: number,
): ContactPatchInput => ({
  version,
  displayName: draft.displayName.trim(),
  firstName: draft.firstName.trim() || null,
  lastName: draft.lastName.trim() || null,
  organization: draft.organization.trim() || null,
  notes: draft.notes.trim() || null,
})

export const appendCursorPage = (
  current: ContactSummary[],
  page: CursorPage<ContactSummary>,
  reset: boolean,
) =>
  reset
    ? page.items
    : [...current, ...page.items.filter((next) => !current.some((item) => item.id === next.id))]

export const buildSelectionDescriptor = (
  selectedIds: ReadonlySet<string>,
  allMatching: boolean,
  query: ContactQuery,
): ContactSelection =>
  allMatching
    ? { type: 'FILTER', filter: { ...query } }
    : { type: 'IDS', ids: Array.from(selectedIds) }

export const normalizeContactQuery = (query: ContactQuery): ContactQuery => {
  const q = query.q?.trim()
  return {
    ...(q ? { q } : {}),
    ...(query.status ? { status: query.status } : {}),
    ...(query.tagId ? { tagId: query.tagId } : {}),
    ...(query.listId ? { listId: query.listId } : {}),
    ...(query.createdAfter ? { createdAfter: query.createdAfter } : {}),
    ...(query.createdBefore ? { createdBefore: query.createdBefore } : {}),
    ...(query.sort ? { sort: query.sort } : {}),
    size: Math.min(Math.max(query.size ?? 50, 1), 200),
  }
}

const normalizeImportHeader = (value: string) =>
  value
    .trim()
    .toLowerCase()
    .replace(/[\s.-]+/g, '_')
    .replace(/_+/g, '_')

export const suggestImportTarget = (column: string): ImportFieldTarget => {
  switch (normalizeImportHeader(column)) {
    case 'name':
    case 'full_name':
    case 'display_name':
    case 'contact_name':
      return { type: 'BUILTIN', field: 'displayName' }
    case 'first_name':
    case 'firstname':
    case 'given_name':
      return { type: 'BUILTIN', field: 'firstName' }
    case 'last_name':
    case 'lastname':
    case 'surname':
    case 'family_name':
      return { type: 'BUILTIN', field: 'lastName' }
    case 'organization':
    case 'organisation':
    case 'company':
    case 'company_name':
      return { type: 'BUILTIN', field: 'organization' }
    case 'notes':
    case 'note':
    case 'comments':
      return { type: 'BUILTIN', field: 'notes' }
    case 'email':
    case 'email_address':
    case 'email_id':
    case 'e_mail':
      return { type: 'IDENTITY', identityType: 'EMAIL' }
    case 'phone':
    case 'phone_number':
    case 'mobile':
    case 'mobile_number':
    case 'telephone':
      return { type: 'IDENTITY', identityType: 'PHONE' }
    case 'tags':
    case 'tag':
    case 'tag_names':
      return { type: 'TAG_NAMES' }
    default:
      return { type: 'IGNORE' }
  }
}

const importTargetKey = (target: ImportFieldTarget) => {
  if (target.type === 'IGNORE') return null
  if (target.type === 'BUILTIN') return 'BUILTIN:' + target.field
  if (target.type === 'IDENTITY') return 'IDENTITY:' + target.identityType
  if (target.type === 'TAG_NAMES') return 'TAG_NAMES'
  return 'CUSTOM_FIELD:' + target.fieldId
}

export const buildSuggestedImportMappings = (headers: string[]): ImportFieldMapping[] => {
  const usedTargets = new Set<string>()
  return headers.map<ImportFieldMapping>((column) => {
    const suggested = suggestImportTarget(column)
    const key = importTargetKey(suggested)
    if (!key || usedTargets.has(key)) {
      return { column, target: { type: 'IGNORE' } }
    }
    usedTargets.add(key)
    return { column, target: suggested }
  })
}

const findImportHeader = (headers: string[], candidates: string[]) => {
  const normalizedCandidates = new Set(candidates.map(normalizeImportHeader))
  return headers.find((header) => normalizedCandidates.has(normalizeImportHeader(header)))
}

export const buildSuggestedConsentMappings = (headers: string[]): ConsentImportMapping[] => {
  const sharedEvidence = findImportHeader(headers, [
    'consent_source',
    'marketing_consent_source',
    'consent_evidence',
  ])
  const sharedOccurredAt = findImportHeader(headers, [
    'consent_occurred_at',
    'consent_date',
    'consent_at',
  ])

  const definitions: Array<{
    channel: ConsentImportMapping['channel']
    state: string[]
    evidence: string[]
    occurredAt: string[]
  }> = [
    {
      channel: 'EMAIL',
      state: [
        'email_marketing_consent',
        'email_consent',
        'email_opt_in',
        'email_optin',
        'email_permission',
      ],
      evidence: ['email_consent_source', 'email_consent_evidence'],
      occurredAt: ['email_consent_occurred_at', 'email_consent_date', 'email_opt_in_at'],
    },
    {
      channel: 'SMS',
      state: [
        'sms_marketing_consent',
        'sms_consent',
        'sms_opt_in',
        'sms_optin',
        'sms_permission',
      ],
      evidence: ['sms_consent_source', 'sms_consent_evidence'],
      occurredAt: ['sms_consent_occurred_at', 'sms_consent_date', 'sms_opt_in_at'],
    },
    {
      channel: 'WHATSAPP',
      state: [
        'whatsapp_marketing_consent',
        'whatsapp_consent',
        'whatsapp_opt_in',
        'whatsapp_optin',
        'whatsapp_permission',
        'wa_marketing_consent',
      ],
      evidence: ['whatsapp_consent_source', 'whatsapp_consent_evidence'],
      occurredAt: [
        'whatsapp_consent_occurred_at',
        'whatsapp_consent_date',
        'whatsapp_opt_in_at',
      ],
    },
  ]

  return definitions.flatMap<ConsentImportMapping>((definition) => {
    const stateColumn = findImportHeader(headers, definition.state)
    if (!stateColumn) return []
    return [
      {
        channel: definition.channel,
        stateColumn,
        source: 'IMPORT',
        evidenceColumn: findImportHeader(headers, definition.evidence) ?? sharedEvidence ?? null,
        occurredAtColumn:
          findImportHeader(headers, definition.occurredAt) ?? sharedOccurredAt ?? null,
      },
    ]
  })
}

export const buildImportCommitInput = (input: {
  mappings: ImportFieldMapping[]
  duplicateStrategy: ImportCommitInput['duplicateStrategy']
  consentMappings: ImportCommitInput['consentMappings']
  consentDeclarations?: ConsentImportDeclaration[]
  targetListId?: string | null
  idempotencyKey: string
}): ImportCommitInput => ({
  mappings: input.mappings.filter((mapping) => mapping.target.type !== 'IGNORE'),
  duplicateStrategy: input.duplicateStrategy,
  consentMappings: input.consentMappings,
  consentDeclarations: input.consentDeclarations ?? [],
  targetListId: input.targetListId || null,
  idempotencyKey: input.idempotencyKey,
})

export const createIdempotencyKey = () => {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  return 'contact-job-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2)
}

export const contactPrimaryIdentityValue = (
  contact: Pick<ContactDetail, 'identities' | 'primaryEmail' | 'primaryPhone'>,
  type: 'EMAIL' | 'PHONE',
) => {
  const direct = type === 'EMAIL' ? contact.primaryEmail : contact.primaryPhone
  if (direct) return direct

  const identity = contact.identities.find(
    (candidate) =>
      candidate.type === type &&
      candidate.primary &&
      (!candidate.status || candidate.status === 'ACTIVE'),
  )
  return identity?.normalizedValue || identity?.value || null
}
