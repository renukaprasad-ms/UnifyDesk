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

export type BroadcastSenderProvider = 'AMAZON_SES' | 'AZURE_EMAIL' | 'SENDGRID' | 'MSG91_EMAIL' | 'MSG91_SMS'
export type BroadcastSenderManagementMode = 'CUSTOMER_MANAGED' | 'MESSAGING_MANAGED'
export type BroadcastSenderStatus = 'ACTIVE' | 'DISABLED'
export type BroadcastSenderAuthMode = 'IAM_ROLE' | 'ACCESS_KEYS' | 'API_KEY'
export type SendGridRegion = 'GLOBAL' | 'EU'

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
  templateId: string
  fromAddress: string
  displayName?: string | null
  replyToAddress?: string | null
  messageVariable?: string | null
  subjectVariable?: string | null
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
  | 'PREPARING'
  | 'RUNNING'
  | 'PAUSED'
  | 'COMPLETED'
  | 'PARTIALLY_FAILED'
  | 'CANCELLED'
  | 'FAILED'

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

export type BroadcastMessageSummary = {
  id: string
  groupId: string
  senderId?: string | null
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
  bodyText: string
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
  groupId: string
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
  senderId: string
  subject?: string | null
  bodyText: string
}
