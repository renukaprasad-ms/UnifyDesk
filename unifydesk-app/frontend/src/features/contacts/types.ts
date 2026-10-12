export type CursorPage<T> = {
  items: T[]
  nextCursor: string | null
  hasMore: boolean
}

export type ContactChannel = 'EMAIL' | 'SMS' | 'WHATSAPP'

export type ContactGrowthPoint = {
  date: string
  added: number
}

export type ContactDashboardSummary = {
  totalContacts: number
  newContacts30d: number
  emailSubscribers: number
  smsSubscribers: number
  whatsappSubscribers: number
  growth: ContactGrowthPoint[]
}
export type PreferenceState = 'UNKNOWN' | 'OPTED_IN' | 'OPTED_OUT'
export type ContactSort = 'UPDATED_DESC' | 'CREATED_DESC' | 'NAME_ASC'
export type ContactStatus = 'ACTIVE' | 'DELETED' | 'MERGED'
export type ContactSearchStatus = Exclude<ContactStatus, 'DELETED'>
export type IdentityType = 'EMAIL' | 'PHONE' | 'EXTERNAL'

export type TagSummary = {
  id: string
  name: string
  color?: string | null
  createdAt?: string
  updatedAt?: string
}

export type ListSummary = {
  id: string
  name: string
  description?: string | null
  status?: string
  memberCount: number
  createdAt?: string
  updatedAt?: string
  version?: number
}

export type ContactIdentity = {
  id: string
  type: IdentityType
  value: string
  normalizedValue?: string | null
  namespace?: string | null
  primary: boolean
  status?: string
}

export type CustomFieldType =
  'TEXT' | 'NUMBER' | 'BOOLEAN' | 'DATE' | 'DATETIME' | 'SINGLE_SELECT' | 'MULTI_SELECT'

export type CustomFieldValue = string | number | boolean | string[] | null

export type CustomFieldDefinition = {
  id: string
  fieldKey?: string
  label: string
  type: CustomFieldType
  options?: string[] | null
  status: string
  indexability: string
  position?: number
  createdAt?: string
  updatedAt?: string
}

export type ContactPreferenceSummary = Partial<Record<ContactChannel, PreferenceState>>

export type ContactSummary = {
  id: string
  displayName: string
  firstName?: string | null
  lastName?: string | null
  organization?: string | null
  primaryEmail?: string | null
  primaryPhone?: string | null
  tags?: TagSummary[]
  preferenceSummary?: ContactPreferenceSummary
  updatedAt: string
  createdAt?: string
  lastActivityAt?: string | null
  status?: ContactStatus
  version?: number
}

export type ContactDetail = ContactSummary & {
  companyId?: string
  version: number
  identities: ContactIdentity[]
  lists?: ListSummary[]
  customFields?: Record<string, CustomFieldValue>
  notes?: string | null
  mergedIntoContactId?: string | null
  jobTitle?: string | null
  countryCode?: string | null
  timezone?: string | null
  language?: string | null
  source?: string | null
  ownerUserId?: string | null
}

export type ContactWriteInput = {
  displayName: string
  firstName?: string | null
  lastName?: string | null
  organization?: string | null
  notes?: string | null
  identities?: Array<{
    type: IdentityType
    value: string
    namespace?: string | null
    primary?: boolean
  }>
  tagIds?: string[]
  customFields?: Record<string, CustomFieldValue>
}

export type ContactPatchInput = {
  version: number
} & Partial<
  Pick<
    ContactWriteInput,
    'displayName' | 'firstName' | 'lastName' | 'organization' | 'notes' | 'customFields'
  >
>

export type ContactQuery = {
  q?: string
  status?: ContactSearchStatus
  tagId?: string
  listId?: string
  createdAfter?: string
  createdBefore?: string
  sort?: ContactSort
  size?: number
}

export type ExplicitSelection = { type: 'IDS'; ids: string[] }
export type FilterSelection = { type: 'FILTER'; filter: ContactQuery }
export type ListSelection = { type: 'LIST'; listId: string }
export type SegmentSelection = { type: 'SEGMENT'; segmentId: string }
export type ContactSelection =
  ExplicitSelection | FilterSelection | ListSelection | SegmentSelection

export type BulkOperation =
  | { type: 'ADD_TAG'; tagId: string }
  | { type: 'REMOVE_TAG'; tagId: string }
  | { type: 'ADD_TO_LIST'; listId: string }
  | { type: 'REMOVE_FROM_LIST'; listId: string }
  | { type: 'UPDATE_FIELD'; fieldId: string; value: CustomFieldValue }
  | { type: 'DELETE' }

export type AsyncJob = {
  id: string
  jobId?: string | null
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | string
  progressPercent?: number | null
  processedCount?: number
  successCount?: number
  errorCount?: number
  message?: string | null
  createdAt?: string
  updatedAt?: string
}

export type FieldKind =
  | 'BUILTIN_CONTACT'
  | 'IDENTITY'
  | 'TAG'
  | 'LIST'
  | 'CUSTOM_FIELD'
  | 'CHANNEL_PREFERENCE'
  | 'SUPPRESSION'
  | 'CREATED_AT'
  | 'LAST_ACTIVITY_AT'

export type SegmentValueType =
  'TEXT' | 'NUMBER' | 'BOOLEAN' | 'DATE' | 'SINGLE_SELECT' | 'MULTI_SELECT' | 'NONE'

export type SegmentFieldDescriptor = {
  kind: FieldKind
  key: string
  fieldId?: string | null
}

export type SegmentOperator =
  | 'EQ'
  | 'NE'
  | 'IN'
  | 'NOT_IN'
  | 'PRESENT'
  | 'ABSENT'
  | 'CONTAINS'
  | 'GT'
  | 'GTE'
  | 'LT'
  | 'LTE'
  | 'BEFORE'
  | 'AFTER'
  | 'HAS_TAG'
  | 'IN_LIST'
  | 'IS_OPTED_IN'
  | 'IS_OPTED_OUT'
  | 'IS_SUPPRESSED'

export type SegmentPredicate = {
  type: 'predicate'
  field: SegmentFieldDescriptor
  operator: SegmentOperator
  value?: CustomFieldValue
}

export type SegmentGroup = {
  type: 'group'
  op: 'AND' | 'OR'
  children: SegmentNode[]
}

export type SegmentNode = SegmentGroup | SegmentPredicate

export type SegmentFieldOption = {
  descriptor: SegmentFieldDescriptor
  label: string
  valueType: SegmentValueType
  operators: SegmentOperator[]
  options?: Array<{ value: string; label: string }>
}

export type SegmentDefinition = {
  id: string
  name: string
  description?: string | null
  filterVersion: 1
  filter: SegmentGroup
  normalizedFilter?: SegmentGroup
  filterHash?: string
  count?: number | null
  countState?: 'EXACT' | 'CACHED' | 'PENDING'
  countComputedAt?: string | null
  archived?: boolean
  status?: string
  version: number
  createdAt?: string
  updatedAt?: string
}

export type SegmentPreview = {
  sample: ContactSummary[]
  count: number | null
  countState: 'EXACT' | 'CACHED' | 'PENDING'
  countComputedAt?: string | null
}

export type SegmentValidation = {
  valid: boolean
  normalizedFilter?: SegmentGroup
  filterHash?: string
  costClass?: 'LOW' | 'MEDIUM' | 'HIGH'
  errors?: Array<{ path?: string; code?: string; message: string }>
}

export type CommunicationPreference = {
  id?: string
  channel: ContactChannel
  purpose: 'MARKETING'
  state: PreferenceState
  identityId?: string | null
  source?: string | null
  sourceRef?: string | null
  occurredAt?: string | null
  updatedAt?: string | null
  reason?: string | null
  version?: number
}

export type SuppressionReasonCode =
  | 'MANUAL'
  | 'HARD_BOUNCE'
  | 'COMPLAINT'
  | 'PROVIDER_SUPPRESSED'
  | 'DND'
  | 'INVALID_DESTINATION'
  | 'ABUSE_POLICY'
  | 'LEGAL_POLICY'

export type Suppression = {
  id: string
  channel: ContactChannel
  identityId?: string | null
  reasonCode: SuppressionReasonCode
  source?: string | null
  sourceRef?: string | null
  state: string
  createdAt?: string | null
  liftedAt?: string | null
  liftable: boolean
  active: boolean
  version?: number
}

export type CreateSuppressionInput = {
  identityId?: string | null
  channel: ContactChannel
  reasonCode: SuppressionReasonCode
  sourceRef?: string | null
  occurredAt: string
  note?: string | null
  idempotencyKey: string
}

export type LiftSuppressionInput = {
  reason: string
  idempotencyKey: string
}

export type ContactActivity = {
  id: string
  type: string
  title: string
  summary?: string | null
  occurredAt: string
  href?: string | null
}

export type ImportUploadContract = {
  method: 'PUT' | 'POST' | string
  url: string
  headers?: Record<string, string>
}

export type ContactImport = {
  id: string
  status:
    | 'DRAFT_UPLOAD'
    | 'UPLOADED'
    | 'PREVIEWED'
    | 'QUEUED'
    | 'RUNNING'
    | 'COMPLETED'
    | 'VALIDATION_FAILED'
    | 'FAILED'
    | 'EXPIRED'
    | string
  fileName?: string | null
  upload?: ImportUploadContract | null
  serverUploadUrl?: string | null
  maxBytes?: number | null
  job?: AsyncJob | null
  totalRows?: number
  successRows?: number
  errorRows?: number
  targetListId?: string | null
  createdAt?: string
  updatedAt?: string
}

export type ImportPreview = {
  headers: string[]
  sampleRows: string[][]
  detectedDelimiter?: string | null
  encoding?: string | null
  estimatedRows?: number | null
  validationWarnings?: string[]
  suggestedMappings?: Array<{ column: string; target: string }>
}

export type ImportFieldTarget =
  | { type: 'IGNORE' }
  | {
      type: 'BUILTIN'
      field: 'displayName' | 'firstName' | 'lastName' | 'organization' | 'notes'
    }
  | { type: 'IDENTITY'; identityType: 'EMAIL' | 'PHONE' }
  | { type: 'TAG_NAMES' }
  | { type: 'CUSTOM_FIELD'; fieldId: string }

export type ImportFieldMapping = {
  column: string
  target: ImportFieldTarget
}

export type ConsentImportMapping = {
  channel: ContactChannel
  stateColumn: string
  source: 'IMPORT'
  evidenceColumn?: string | null
  occurredAtColumn?: string | null
}

export type ConsentImportDeclaration = {
  channel: ContactChannel
  state: 'OPTED_IN'
  source: 'IMPORT'
  evidence: string
  occurredAt?: string | null
  confirmed: boolean
}

export type ImportCommitInput = {
  mappings: ImportFieldMapping[]
  duplicateStrategy: 'SKIP' | 'MERGE_EXACT' | 'FAIL'
  consentMappings: ConsentImportMapping[]
  consentDeclarations: ConsentImportDeclaration[]
  targetListId?: string | null
  idempotencyKey: string
}

export type ImportErrors = {
  count: number
  ready: boolean
  downloadUrl?: string | null
  expiresAt?: string | null
}

export type ExportSourceType = 'CONTACTS' | 'LIST' | 'SEGMENT'

export type ContactExportField =
  | 'id'
  | 'displayName'
  | 'firstName'
  | 'lastName'
  | 'organization'
  | 'jobTitle'
  | 'countryCode'
  | 'language'

export type ContactExport = {
  id: string
  status: string
  job?: AsyncJob | null
  rowCount: number
  expiresAt?: string | null
}

export type ExportRequest = {
  sourceType: ExportSourceType
  sourceId?: string | null
  fields: ContactExportField[]
  idempotencyKey: string
}

export type ExportDownload = {
  url: string
  expiresAt?: string | null
}
