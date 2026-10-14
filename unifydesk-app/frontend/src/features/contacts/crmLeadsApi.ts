import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type { CustomFieldValues } from './crmCustomFieldsApi'

export type LeadStage = 'NEW' | 'CONTACTED' | 'QUALIFIED' | 'UNQUALIFIED' | 'CONVERTED'
export type Lead = {
  contactId: string
  displayName: string | null
  organization: string | null
  ownerUserId: string | null
  stage: LeadStage
  customFields?: CustomFieldValues
  version: number
  createdAt: string
  updatedAt: string
}
export type LeadHistoryEvent = {
  id: string
  previousStage: LeadStage | null
  newStage: LeadStage
  actorUserId: string | null
  actorName?: string | null
  actorUsername?: string | null
  occurredAt: string
}
export type LeadPage = { items: Lead[]; nextCursor: string | null; hasMore: boolean }
export type LeadSummary = { total: number; newLeads: number; qualified: number; converted: number }
const url = (companyId: string) => `/api/v1/companies/${companyId}/crm/leads`
const unwrap = <T>(value: ApiResponse<T>) => {
  if (value.data === undefined) throw new Error(value.error_message ?? 'CRM request failed')
  return value.data
}
export const crmLeadsApi = {
  async get(companyId: string, contactId: string) {
    return unwrap(
      (await apiClient.get<ApiResponse<Lead>>(url(companyId) + '/' + encodeURIComponent(contactId)))
        .data,
    )
  },
  async list(companyId: string, stage?: LeadStage, before?: string | null) {
    const response = await apiClient.get<ApiResponse<LeadPage>>(url(companyId), {
      params: { stage, before: before || undefined, size: 50 },
    })
    return unwrap(response.data)
  },
  async history(companyId: string, contactId: string) {
    const response = await apiClient.get<ApiResponse<LeadHistoryEvent[]>>(
      url(companyId) + '/' + encodeURIComponent(contactId) + '/history',
    )
    return unwrap(response.data)
  },
  async summary(companyId: string) {
    const response = await apiClient.get<ApiResponse<LeadSummary>>(url(companyId) + '/summary')
    return unwrap(response.data)
  },
  async enroll(companyId: string, contactId: string, customFields?: CustomFieldValues) {
    const response = await apiClient.post<ApiResponse<Lead>>(url(companyId), {
      contactId,
      customFields: customFields || {},
    })
    return unwrap(response.data)
  },
  async updateCustomFields(
    companyId: string,
    contactId: string,
    customFields: CustomFieldValues,
  ) {
    const response = await apiClient.patch<ApiResponse<Lead>>(
      url(companyId) + '/' + encodeURIComponent(contactId) + '/custom-fields',
      { customFields },
    )
    return unwrap(response.data)
  },
  async moveStage(companyId: string, contactId: string, version: number, stage: LeadStage) {
    const response = await apiClient.patch<ApiResponse<Lead>>(
      url(companyId) + '/' + encodeURIComponent(contactId) + '/stage',
      { stage, version },
    )
    return unwrap(response.data)
  },
  async move(companyId: string, lead: Lead, stage: LeadStage) {
    const response = await apiClient.patch<ApiResponse<Lead>>(
      url(companyId) + '/' + encodeURIComponent(lead.contactId) + '/stage',
      { stage, version: lead.version },
    )
    return unwrap(response.data)
  },
}
