import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'

export type WhatsAppContext = {
  connected: boolean
  connectionId: string | null
  connectionName: string | null
  phoneNumbers: string[]
  primaryPhone: string | null
  conversationId: string | null
  freeFormAllowed: boolean
  sessionExpiresAt: string | null
}

export type WhatsAppTemplateComponent = {
  type: 'HEADER' | 'BODY' | 'FOOTER' | 'BUTTONS'
  format?: string
  text?: string
  buttons?: Array<{ type: string; text: string }>
}

export type WhatsAppTemplate = {
  id: string
  providerTemplateId: string
  name: string
  language: string
  category: string
  status: string
  components: WhatsAppTemplateComponent[]
  syncedAt?: string
}

export type SendWhatsAppInput = {
  contactId?: string | number | null
  dealId?: string | number | null
  phone?: string
  connectionId?: string | number | null
  type?: 'TEMPLATE' | 'TEXT'
  templateId?: string
  parameters?: {
    header?: string[]
    body?: string[]
    buttons?: string[]
  }
  text?: string
}

export type SendWhatsAppResult = {
  success: boolean
  messageId: string
  conversationId: string
  status: string
  recipientPhone: string
}

const url = (companyId: string) => `/api/v1/companies/${companyId}/crm/whatsapp`

const unwrap = <T>(value: ApiResponse<T>) => {
  if (value.data === undefined) throw new Error(value.error_message ?? 'WhatsApp CRM request failed')
  return value.data
}

export const crmWhatsAppApi = {
  async context(companyId: string, contactId?: string | null): Promise<WhatsAppContext> {
    const response = await apiClient.get<ApiResponse<WhatsAppContext>>(`${url(companyId)}/context`, {
      params: contactId ? { contactId } : undefined,
    })
    return unwrap(response.data)
  },

  async templates(companyId: string, connectionId?: string | null): Promise<WhatsAppTemplate[]> {
    const response = await apiClient.get<ApiResponse<WhatsAppTemplate[]>>(`${url(companyId)}/templates`, {
      params: connectionId ? { connectionId } : undefined,
    })
    return unwrap(response.data)
  },

  async send(companyId: string, input: SendWhatsAppInput): Promise<SendWhatsAppResult> {
    const response = await apiClient.post<ApiResponse<SendWhatsAppResult>>(`${url(companyId)}/send`, input)
    return unwrap(response.data)
  },
}
