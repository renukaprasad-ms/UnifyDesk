export type ChannelType = 'EMAIL' | 'WHATSAPP' | 'SMS'
export type ProviderType =
  | 'GMAIL'
  | 'MICROSOFT'
  | 'IMAP_SMTP'
  | 'SMTP'
  | 'AMAZON_SES'
  | 'AZURE_EMAIL'
  | 'SENDGRID'
  | 'META_WHATSAPP'
  | 'MSG91_EMAIL'
  | 'MSG91_SMS'
export type ChannelConnectionStatus = 'PENDING' | 'ACTIVE' | 'ERROR' | 'DISCONNECTED'
export type ChannelConnectionCapability = 'INBOX' | 'BROADCAST'
export type DisconnectDataPolicy = 'KEEP_7_DAYS' | 'DELETE_NOW'
export type SenderIdentityStatus = 'PENDING' | 'ACTIVE' | 'ERROR' | 'DISABLED'
export type SenderIdentityType = 'EMAIL_ADDRESS' | 'PHONE_NUMBER' | 'SMS_HEADER'

export type ChannelConnection = {
  id: string
  companyId: string
  channelType: ChannelType
  providerType: ProviderType
  name: string
  externalAccountId?: string | null
  status: ChannelConnectionStatus
  createdByUserId: string
  lastSyncAt?: string | null
  disconnectedAt?: string | null
  purgeAfter?: string | null
  createdAt: string
  updatedAt: string
  providerConfigured: boolean
  capabilities: ChannelConnectionCapability[]
}

export type SenderIdentity = {
  id: string
  connectionId: string
  identityType: SenderIdentityType
  value: string
  externalId?: string | null
  status: SenderIdentityStatus
  defaultIdentity: boolean
  createdAt: string
  updatedAt: string
}

export type MetaWhatsAppSignupConfig = {
  appId: string
  configurationId: string
  graphApiVersion: string
  embeddedSignupVersion: '4'
  state: string
  expiresAt: string
  coexistenceAvailable: boolean
}

export type MetaWhatsAppSignupResult = {
  code: string
  wabaId?: string
  phoneNumberId?: string
  whatsappAccountId?: string
  messagingAccountId?: string
}

export type MetaWhatsAppProductStatus =
  | 'CONNECTING'
  | 'VALIDATING'
  | 'ACTIVE'
  | 'DEGRADED'
  | 'ACTION_REQUIRED'
  | 'REAUTH_REQUIRED'
  | 'DISCONNECTED'
  | 'ERROR'

export type MetaWhatsAppAuthState =
  'VALID' | 'EXPIRING' | 'EXPIRED' | 'REVOKED' | 'MISSING_PERMISSION' | 'UNKNOWN'

export type MetaWhatsAppRegistrationState = 'PENDING' | 'REGISTERED' | 'ACTION_REQUIRED' | 'ERROR'

export type MetaWhatsAppSubscriptionState = 'PENDING' | 'SUBSCRIBED' | 'ERROR'
export type MetaWhatsAppCoexistenceState = 'DISABLED' | 'ELIGIBLE_UNVERIFIED' | 'ENABLED' | 'ERROR'

export type MetaWhatsAppNextAction = 'NONE' | 'WAIT' | 'REAUTHORIZE' | 'OPEN_META' | 'RETRY'

export type MetaWhatsAppPhone = {
  senderIdentityId?: string
  phoneNumberId: string
  displayPhoneNumber: string
  verifiedName?: string | null
  qualityRating?: string | null
}

export type MetaWhatsAppConnection = {
  connectionId: string
  genericStatus?: ChannelConnectionStatus
  status: MetaWhatsAppProductStatus
  authState?: MetaWhatsAppAuthState
  registrationState?: MetaWhatsAppRegistrationState
  subscriptionState?: MetaWhatsAppSubscriptionState
  coexistenceState?: MetaWhatsAppCoexistenceState
  lastValidatedAt?: string | null
  wabaId?: string | null
  whatsappAccountId?: string | null
  messagingAccountId?: string | null
  phone?: MetaWhatsAppPhone | null
  safeErrorCode?: string | null
  nextAction: MetaWhatsAppNextAction
}

export type GmailAuthorization = {
  authorizationUrl: string
}

export type EmailFolder = 'INBOX' | 'SENT'
export type EmailSyncMode = 'DATE_RANGE' | 'LATEST_MESSAGES'

export type EmailSyncRequest = {
  mode: EmailSyncMode
  folder: EmailFolder
  fromDate?: string
  toDate?: string
  messageCount?: number
}

export type EmailSyncQueued = {
  connectionId: string
  jobId: string
  status: 'QUEUED' | 'RUNNING'
  mode: EmailSyncMode
  folder: EmailFolder
  fromDate?: string | null
  toDate?: string | null
  messageCount?: number | null
}

export type GmailSyncQueued = EmailSyncQueued
export type GmailSyncRequest = EmailSyncRequest

export type GmailSendRequest = {
  to: string[]
  cc?: string[]
  bcc?: string[]
  subject: string
  bodyText: string
  threadId?: string | null
  inReplyTo?: string | null
  references?: string | null
  attachmentMediaIds?: string[]
}

export type GmailSendResponse = {
  connectionId: string
  messageId: string
  threadId: string
  from: string
  to: string[]
  cc: string[]
  bcc: string[]
  subject?: string | null
  sentAt: string
}

export type MicrosoftAuthorization = {
  authorizationUrl: string
}

export type MicrosoftSendRequest = {
  to: string[]
  cc?: string[]
  bcc?: string[]
  subject: string
  bodyText: string
  replyToMessageId?: string | null
  replyAll?: boolean
  attachmentMediaIds?: string[]
}

export type MicrosoftSendResponse = {
  connectionId: string
  from: string
  to: string[]
  cc: string[]
  bcc: string[]
  subject: string
  acceptedAt: string
}

export type MailSecurityMode = 'SSL_TLS' | 'STARTTLS'

export type ImapSmtpConnectRequest = {
  email: string
  displayName?: string
  username: string
  password: string
  imapHost: string
  imapPort: number
  imapSecurity: MailSecurityMode
  smtpHost: string
  smtpPort: number
  smtpSecurity: MailSecurityMode
  sentFolder?: string
}

export type ImapSmtpConnection = {
  connectionId: string
  email: string
  name: string
  status: ChannelConnectionStatus
  imapHost: string
  imapPort: number
  imapSecurity: MailSecurityMode
  smtpHost: string
  smtpPort: number
  smtpSecurity: MailSecurityMode
  sentFolder: string
}

export type ImapSmtpSendRequest = {
  to: string[]
  cc?: string[]
  bcc?: string[]
  subject?: string
  bodyText: string
  inReplyTo?: string | null
  references?: string | null
  attachmentMediaIds?: string[]
}

export type ImapSmtpSendResponse = {
  connectionId: string
  messageId: string
  from: string
  to: string[]
  cc: string[]
  bcc: string[]
  subject: string
  sentAt: string
}
