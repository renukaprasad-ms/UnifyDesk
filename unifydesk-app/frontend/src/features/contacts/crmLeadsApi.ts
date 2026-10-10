import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'

export type LeadStage = 'NEW' | 'CONTACTED' | 'QUALIFIED' | 'UNQUALIFIED' | 'CONVERTED'
export type Lead = {
  contactId: string
  displayName: string | null
  organization: string | null
  ownerUserId: string | null
  stage: LeadStage
  version: number
  createdAt: string
  updatedAt: string
}
export type LeadPage = { items: Lead[]; nextCursor: string | null; hasMore: boolean }
export type LeadSummary = { total: number; newLeads: number; qualified: number; converted: number }
const url = (companyId: string) => `/api/v1/companies/${companyId}/crm/leads`
const unwrap = <T>(value: ApiResponse<T>) => {
  if (value.data === undefined) throw new Error(value.error_message ?? 'CRM request failed')
  return value.data
}
export const crmLeadsApi = {
  async list(companyId: string, stage?: LeadStage, before?: string | null) {
    const response = await apiClient.get<ApiResponse<LeadPage>>(url(companyId), {
      params: { stage, before: before || undefined, size: 50 },
    })
    return unwrap(response.data)
  },
  async summary(companyId: string) {
    const response = await apiClient.get<ApiResponse<LeadSummary>>(url(companyId) + '/summary')
    return unwrap(response.data)
  },
  async enroll(companyId: string, contactId: string) {
    const response = await apiClient.post<ApiResponse<Lead>>(url(companyId), { contactId })
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
