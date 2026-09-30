export type WhatsAppComposerState = {
  freeFormAllowed: boolean
  customerServiceExpiresAt?: string | null
  templateRequired: boolean
  supportedMedia: Array<'IMAGE' | 'DOCUMENT'>
  connectionStatus: string
}

export type WhatsAppTemplateComponent = {
  type: 'HEADER' | 'BODY' | 'FOOTER' | 'BUTTONS' | string
  text?: string | null
  format?: string | null
  buttons?: Array<{
    type?: string
    text?: string
    url?: string
  }>
  example?: {
    header_text?: string[]
    body_text?: string[][]
  } | null
}

export type WhatsAppTemplate = {
  id: string
  providerTemplateId?: string | null
  name: string
  language: string
  category: string
  status: string
  components: WhatsAppTemplateComponent[]
  syncedAt: string
}

export type WhatsAppTemplateParameters = {
  header: string[]
  body: string[]
  buttons: string[]
}

export type WhatsAppSendStatus =
  | 'PENDING'
  | 'ACCEPTED'
  | 'SENT'
  | 'DELIVERED'
  | 'READ'
  | 'FAILED'
  | 'UNKNOWN'

export type WhatsAppSendResult = {
  messageId: string
  clientRequestId: string
  providerMessageId?: string | null
  status: WhatsAppSendStatus
  acceptedAt?: string | null
  safeErrorCode?: string | null
}

export type WhatsAppTextRequest = {
  clientRequestId: string
  type: 'TEXT'
  text: string
  replyToMessageId?: string
}

export type WhatsAppTemplateRequest = {
  clientRequestId: string
  type: 'TEMPLATE'
  templateId: string
  parameters: WhatsAppTemplateParameters
}

export type WhatsAppMediaRequest = {
  clientRequestId: string
  type: 'MEDIA'
  mediaId: string
  caption?: string
  replyToMessageId?: string
}

export type WhatsAppSendRequest =
  | WhatsAppTextRequest
  | WhatsAppTemplateRequest
  | WhatsAppMediaRequest
