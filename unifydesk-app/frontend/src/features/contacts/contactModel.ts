import type {
  ContactDetail,
  ContactExportField,
  ContactPatchInput,
  ContactQuery,
  ContactSearchStatus,
  ContactSelection,
  ContactSummary,
  ContactWriteInput,
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

export const parseContactSearchStatus = (
  value: string | null,
): ContactSearchStatus | undefined => {
  switch (value) {
    case 'ACTIVE':
    case 'MERGED':
      return value
    default:
      return undefined
  }
}

export const validateContactDraft = (
  draft: ContactFormDraft,
  validateIdentities = true,
) => {
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
) => (reset ? page.items : [...current, ...page.items.filter((next) => !current.some((item) => item.id === next.id))])

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

const normalizedImportHeader = (value: string) =>
  value.trim().toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '')

const suggestedTarget = (
  column: string,
  namespaceColumn?: string,
): ImportFieldTarget => {
  switch (normalizedImportHeader(column)) {
    case 'display_name':
    case 'full_name':
      return { type: 'BUILTIN', field: 'displayName' }
    case 'first_name':
    case 'firstname':
      return { type: 'BUILTIN', field: 'firstName' }
    case 'last_name':
    case 'lastname':
    case 'surname':
      return { type: 'BUILTIN', field: 'lastName' }
    case 'organization':
    case 'company':
    case 'company_name':
      return { type: 'BUILTIN', field: 'organization' }
    case 'job_title':
    case 'jobtitle':
      return { type: 'BUILTIN', field: 'jobTitle' }
    case 'country_code':
    case 'countrycode':
      return { type: 'BUILTIN', field: 'countryCode' }
    case 'timezone':
    case 'time_zone':
      return { type: 'BUILTIN', field: 'timezone' }
    case 'language':
      return { type: 'BUILTIN', field: 'language' }
    case 'source':
      return { type: 'BUILTIN', field: 'source' }
    case 'notes':
    case 'note':
      return { type: 'BUILTIN', field: 'notes' }
    case 'email':
    case 'email_address':
      return { type: 'IDENTITY', identityType: 'EMAIL' }
    case 'phone':
    case 'phone_number':
    case 'mobile':
    case 'mobile_number':
      return { type: 'IDENTITY', identityType: 'PHONE' }
    case 'tags':
    case 'tag_names':
      return { type: 'TAG_NAMES' }
    case 'external_id':
    case 'externalid':
      return namespaceColumn
        ? {
            type: 'IDENTITY',
            identityType: 'EXTERNAL',
            externalNamespaceColumn: namespaceColumn,
          }
        : { type: 'IGNORE' }
    default:
      return { type: 'IGNORE' }
  }
}

export const suggestImportMappings = (headers: string[]): ImportFieldMapping[] => {
  const namespaceColumn = headers.find((header) =>
    ['source', 'external_namespace', 'external_source'].includes(
      normalizedImportHeader(header),
    ),
  )
  return headers.map((column) => ({
    column,
    target: suggestedTarget(column, namespaceColumn),
  }))
}

export const buildImportCommitInput = (input: {
  mappings: ImportFieldMapping[]
  duplicateStrategy: ImportCommitInput['duplicateStrategy']
  consentMappings: ImportCommitInput['consentMappings']
  targetListId?: string | null
  idempotencyKey: string
}): ImportCommitInput => ({
  mappings: input.mappings.filter((mapping) => mapping.target.type !== 'IGNORE'),
  duplicateStrategy: input.duplicateStrategy,
  consentMappings: input.consentMappings,
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
