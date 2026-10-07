import type { ChannelType } from '../channel/types'

export type Page<T> = {
  content: T[]
  number: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export type BroadcastGroup = {
  id: string
  companyId: string
  name: string
  description?: string | null
  channelType: ChannelType
  memberCount: number
  createdAt: string
  updatedAt: string
}

export type BroadcastGroupMemberStatus = 'ACTIVE' | 'DISABLED'

export type BroadcastMember = {
  id: string
  destination: string
  displayName?: string | null
  metadataJson?: string | null
  status: BroadcastGroupMemberStatus
  createdAt: string
}

export type BroadcastMemberInput = {
  destination: string
  displayName?: string | null
  metadata?: Record<string, string>
}

export type BroadcastMemberImportResult = {
  processed: number
  insertedOrReactivated: number
  duplicates: number
  invalid: number
}

export type BroadcastSenderProvider =
  | 'AMAZON_SES'
  | 'AZURE_EMAIL'
  | 'SENDGRID'
  | 'MSG91_EMAIL'
  | 'MSG91_SMS'
  | 'META_WHATSAPP'
  | 'GMAIL'
  | 'MICROSOFT'
  | 'IMAP_SMTP'
  | 'SMTP'
export type BroadcastSenderManagementMode = 'CUSTOMER_MANAGED' | 'MESSAGING_MANAGED'
export type BroadcastSenderStatus = 'ACTIVE' | 'DISABLED'
export type BroadcastSenderAuthMode = 'IAM_ROLE' | 'ACCESS_KEYS' | 'API_KEY'
export type BroadcastSetupStatus =
  'IN_PROGRESS' | 'WAITING_EXTERNAL_ACTION' | 'READY_TO_CONNECT' | 'COMPLETED' | 'CANCELLED'
export type SendGridRegion = 'GLOBAL' | 'EU'

export type BroadcastSetupSession = {
  id: string
  companyId: string
  channelType: ChannelType
  provider: BroadcastSenderProvider
  currentStep: number
  status: BroadcastSetupStatus
  setupDataJson?: string | null
  createdAt: string
  updatedAt: string
  completedAt?: string | null
}

export type UpsertBroadcastSetupSessionInput = {
  channelType: ChannelType
  provider: BroadcastSenderProvider
  currentStep: number
  status: BroadcastSetupStatus
  setupDataJson?: string | null
}

export type ConnectSendGridInput = {
  name: string
  region: SendGridRegion
  apiKey: string
  fromAddress: string
  displayName?: string | null
  replyToAddress?: string | null
}

export type ConnectAzureEmailInput = {
  name: string
  endpoint: string
  accessKey: string
  fromAddress: string
  displayName?: string | null
  replyToAddress?: string | null
}

export type ConnectMsg91Input = {
  name: string
  authKey: string
  flowId: string
  senderId: string
  messageVariable?: string | null
  route?: string | null
}

export type ConnectMsg91EmailInput = {
  name: string
  authKey: string
  domain: string
  fromAddress: string
  displayName?: string | null
  replyToAddress?: string | null
}

export type BroadcastTestEmailResult = {
  provider: BroadcastSenderProvider
  recipient: string
  providerMessageId?: string | null
  acceptedAt?: string | null
}

export type BroadcastSender = {
  id: string
  connectionId: string
  channelConnectionId: string
  provider: BroadcastSenderProvider
  managementMode: BroadcastSenderManagementMode
  status: BroadcastSenderStatus
  fromAddress: string
  displayName?: string | null
  replyToAddress?: string | null
  connectionName: string
  region: string
  productionAccessEnabled: boolean
  sendingEnabled: boolean
  max24HourSend?: number | null
  maxSendRate?: number | null
  sentLast24Hours?: number | null
  lastValidatedAt?: string | null
}

export type ConnectAmazonSesInput = {
  name: string
  region: string
  authMode: BroadcastSenderAuthMode
  roleArn?: string | null
  externalId?: string | null
  accessKeyId?: string | null
  secretAccessKey?: string | null
  fromAddress: string
  displayName?: string | null
  replyToAddress?: string | null
}

export type BroadcastMessageStatus =
  | 'DRAFT'
  | 'SCHEDULED'
  | 'QUEUED'
  | 'RUNNING'
  | 'PAUSED'
  | 'COMPLETED'
  | 'PARTIALLY_FAILED'
  | 'CANCELLED'
  | 'FAILED'

export type BroadcastRunStatus =
  'PREPARING' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'PARTIALLY_FAILED' | 'CANCELLED' | 'FAILED'

export type BroadcastStats = {
  total: number
  pending: number
  processing: number
  accepted: number
  failed: number
  bounced: number
  unknown: number
  skipped: number
  processed?: number
}

export type BroadcastAudienceSourceType = 'LIST' | 'SEGMENT' | 'CONTACTS'

export type AudienceSelector =
  | { sourceType: 'CONTACT_LIST'; sourceId: string }
  | { sourceType: 'SEGMENT'; sourceId: string }
  | { sourceType: 'CONTACTS'; contactIds: string[] }

export type RecipientExclusionReason =
  | 'DUPLICATE'
  | 'MISSING_IDENTITY'
  | 'INVALID_IDENTITY'
  | 'OPTED_OUT'
  | 'CONSENT_REQUIRED'
  | 'SUPPRESSED'
  | 'CHANNEL_INELIGIBLE'

export type DeliveryCapabilities = {
  providerAcceptance: boolean
  deliveryEvents: boolean
  readEvents: boolean
  inboundReplies: boolean
  templateRequired: boolean
  freeFormSessionSupported: boolean
}

export type SenderReadiness = {
  senderId: string
  label: string
  addressOrNumber?: string | null
  state: 'READY' | 'ACTION_REQUIRED' | 'BLOCKED' | 'DISCONNECTED' | 'TEMPORARILY_UNAVAILABLE'
  nextAction?: string | null
  capabilities: DeliveryCapabilities
}

export type ChannelReadiness = {
  channel: ChannelType
  availability: 'AVAILABLE' | 'UNAVAILABLE'
  configuration: 'NOT_CONFIGURED' | 'ACTION_REQUIRED' | 'READY' | 'DEGRADED' | 'DISCONNECTED'
  senders: SenderReadiness[]
  nextAction?: string | null
}

export type RecipientResolutionSample = {
  contactId?: string | null
  maskedDestination?: string | null
  reason?: string | null
}

export type AudienceSourceDescriptor = {
  sourceType: BroadcastAudienceSourceType
  sourceId?: string | null
  sourceVersion?: string | null
  filterVersion?: number | null
  filterHash?: string | null
  contactSelectionHash?: string | null
  resolvedAt: string
}

export type RecipientResolutionPreview = {
  channel: ChannelType
  purpose: string
  selectedCount: number
  eligibleCount: number
  excludedCount: number
  sourceDescriptor: AudienceSourceDescriptor
  exclusions: Partial<Record<RecipientExclusionReason, number>>
  sampleEligible: RecipientResolutionSample[]
  sampleExcluded: RecipientResolutionSample[]
  senderReadiness?: SenderReadiness | null
  computedAt: string
}

export type CreateAudienceBroadcastMessageInput = CreateBroadcastMessageInput & {
  channelType: ChannelType
  whatsappTemplateId?: string | null
  whatsappTemplateParameters?: {
    header: string[]
    body: string[]
    buttons: string[]
  } | null
} & (
  | { sourceType: 'LIST' | 'SEGMENT'; sourceId: string; contactIds?: never }
  | { sourceType: 'CONTACTS'; contactIds: string[]; sourceId?: never }
)

export type BroadcastMessageSummary = {
  id: string
  groupId?: string | null
  audienceSourceType?: BroadcastAudienceSourceType | null
  audienceSourceId?: string | null
  audienceSnapshotId?: string | null
  channelType?: ChannelType | null
  senderId?: string | null
  sourceTemplateId?: string | null
  sourceTemplateVersion?: number | null
  managedSmsTemplateId?: string | null
  managedSmsTemplateVersion?: number | null
  legacyConnectionId?: string | null
  runId?: string | null
  subject?: string | null
  bodyPreview?: string | null
  status: BroadcastMessageStatus
  scheduledAt?: string | null
  createdAt: string
  updatedAt: string
  stats: BroadcastStats
  progressPercent?: number
}

export type BroadcastMessage = Omit<BroadcastMessageSummary, 'bodyPreview'> & {
  bodyText?: string | null
  templateVariables?: Record<string, string>
}

export type BroadcastRecipientStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'RETRY'
  | 'ACCEPTED_BY_PROVIDER'
  | 'FAILED'
  | 'BOUNCED'
  | 'UNKNOWN'
  | 'SKIPPED'

export type BroadcastRecipient = {
  id: string
  contactId?: string | null
  contactIdentityId?: string | null
  destination: string
  displayName?: string | null
  status: BroadcastRecipientStatus
  attemptCount: number
  providerMessageId?: string | null
  internetMessageId?: string | null
  acceptedAt?: string | null
  bouncedAt?: string | null
  failedAt?: string | null
  failureCode?: string | null
  failureReason?: string | null
}

export type BroadcastProgressEvent = {
  runId: string
  broadcastId: string
  groupId?: string | null
  status: BroadcastRunStatus
  stats: BroadcastStats
  progressPercent: number
}

export type CreateBroadcastGroupInput = {
  name: string
  description?: string | null
  channelType: ChannelType
}

export type CreateBroadcastMessageInput = {
  templateId?: string | null
  senderId: string
  subject?: string | null
  bodyText?: string | null
  managedSmsTemplateId?: string | null
  templateVariables?: Record<string, string>
}
