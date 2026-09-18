export type ChannelType = 'EMAIL' | 'WHATSAPP' | 'SMS'
export type ProviderType = 'GMAIL' | 'MICROSOFT' | 'SMTP' | 'META_WHATSAPP' | 'MSG91'
export type ChannelConnectionStatus = 'PENDING' | 'ACTIVE' | 'ERROR' | 'DISCONNECTED'
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
  createdAt: string
  updatedAt: string
  providerConfigured: boolean
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
  state: string
  coexistenceEnabled: boolean
}

export type MetaWhatsAppSignupResult = {
  code: string
  wabaId: string
  phoneNumberId: string
}

export type MetaWhatsAppConnection = {
  connectionId: string
  wabaId: string
  wabaName: string
  status: ChannelConnectionStatus
  phoneNumber: {
    senderIdentityId: string
    phoneNumberId: string
    displayPhoneNumber: string
    verifiedName?: string | null
    qualityRating?: string | null
    status: SenderIdentityStatus
  }
}

export type GmailAuthorization = {
  authorizationUrl: string
}
