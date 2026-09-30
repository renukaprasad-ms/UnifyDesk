import type {
  ContactPatchInput,
  ContactQuery,
  ContactSelection,
  ContactSummary,
  ContactWriteInput,
  CursorPage,
  ImportCommitInput,
  ImportFieldMapping,
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

export const validateContactDraft = (draft: ContactFormDraft) => {
  const errors: Partial<Record<keyof ContactFormDraft, string>> = {}
  if (!draft.displayName.trim()) errors.displayName = 'Display name is required.'
  if (draft.displayName.trim().length > 160) errors.displayName = 'Display name is too long.'
  if (draft.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(draft.email.trim())) {
    errors.email = 'Enter a valid email address.'
  }
  if (draft.phone.trim() && draft.phone.trim().length < 7) {
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

export const toPatchContactInput = (draft: ContactFormDraft): ContactPatchInput => ({
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
