import type { ChannelType } from '../channel/types'

export type MessageTemplateCategory =
  | 'MARKETING'
  | 'TRANSACTIONAL'
  | 'UTILITY'
  | 'AUTHENTICATION'

export type MessageTemplateStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED'

export type MessageTemplateProviderStatus =
  | 'LOCAL_ONLY'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'PAUSED'
  | 'DISABLED'

export type MessageTemplateButton = {
  type: 'QUICK_REPLY' | 'URL' | 'PHONE_NUMBER'
  text: string
  url?: string | null
  phoneNumber?: string | null
}

export type MessageTemplate = {
  id: string
  companyId: string
  name: string
  channelType: ChannelType
  category: MessageTemplateCategory
  status: MessageTemplateStatus
  providerStatus: MessageTemplateProviderStatus
  providerType?: string | null
  externalTemplateId?: string | null
  language?: string | null
  subject?: string | null
  headerText?: string | null
  bodyText: string
  bodyHtml?: string | null
  footerText?: string | null
  buttons: MessageTemplateButton[]
  variables: string[]
  designJson?: string | null
  createdAt: string
  updatedAt: string
  archivedAt?: string | null
}

export type SaveMessageTemplateInput = {
  name: string
  channelType?: ChannelType
  category: MessageTemplateCategory
  language?: string | null
  subject?: string | null
  headerText?: string | null
  bodyText: string
  bodyHtml?: string | null
  footerText?: string | null
  buttons?: MessageTemplateButton[]
  designJson?: string | null
}
