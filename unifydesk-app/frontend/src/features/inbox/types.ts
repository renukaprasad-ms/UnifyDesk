import type { ProviderType } from '../channel/types'
import type { WhatsAppSendStatus } from '../whatsapp/types'

export type ConversationStatus = 'OPEN' | 'ARCHIVED'
export type InboxMessageDirection = 'INBOUND' | 'OUTBOUND'
export type InboxMessageStatus = 'RECEIVED' | 'SENT' | 'DRAFT' | 'FAILED'
export type InboxParticipantType = 'EMAIL_ADDRESS' | 'PHONE_NUMBER'

export type InboxParticipantContactResolution = 'MATCHED' | 'UNMATCHED' | 'AMBIGUOUS'

export type InboxParticipant = {
  address: string
  displayName?: string | null
  participantType: InboxParticipantType
  contactId?: string | null
  contactIdentityId?: string | null
  contactResolution?: InboxParticipantContactResolution | null
}

export type InboxConversation = {
  id: string
  connectionId: string
  externalConversationId: string
  providerType: ProviderType
  subject?: string | null
  lastMessagePreview?: string | null
  lastMessageAt?: string | null
  unreadCount: number
  status: ConversationStatus
  participants: InboxParticipant[]
}

export type InboxAttachment = {
  id: string
  externalAttachmentId: string
  filename?: string | null
  mimeType?: string | null
  sizeBytes?: number | null
}

export type InboxMessage = {
  id: string
  conversationId: string
  externalMessageId: string
  providerFolderKey?: string | null
  internetMessageId?: string | null
  direction: InboxMessageDirection
  status: InboxMessageStatus
  whatsappStatus?: WhatsAppSendStatus | null
  subject?: string | null
  snippet?: string | null
  bodyText?: string | null
  bodyHtml?: string | null
  fromAddress?: string | null
  replyToAddress?: string | null
  to: string[]
  cc: string[]
  bcc: string[]
  inReplyTo?: string | null
  references?: string | null
  sentAt?: string | null
  read: boolean
  attachments: InboxAttachment[]
}

export type InboxPage<T> = {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type InboxReadStateResponse = {
  read: boolean
  updatedMessages: number
  updatedConversations: number
}

export type InboxConversationDetail = {
  conversation: InboxConversation
  messages: InboxMessage[]
}

export type InboxWhatsAppStatusChangedEvent = {
  connectionId: string
  conversationId: string
  messageId: string
}

export type InboxEmailChangedEvent = {
  connectionId: string
  provider: ProviderType
  conversationIds: string[]
  messageIds: string[]
  deletedMessageIds: string[]
  fullRefreshRequired: boolean
}

export type InboxRealtimeSnapshot = {
  conversations: InboxConversation[]
  messages: InboxMessage[]
  missingConversationIds: string[]
  missingMessageIds: string[]
}
