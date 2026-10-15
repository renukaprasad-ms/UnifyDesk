import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'

export interface FormFieldConfig {
  fieldKey: string
  label: string
  type: string // text, email, tel, textarea, select, number
  required: boolean
  placeholder?: string
  options?: string[]
}

export interface LeadForm {
  id: string
  formKey: string
  title: string
  description: string | null
  status: 'ACTIVE' | 'PAUSED'
  defaultLeadStage: string
  assigneeUserId: string | null
  assigneeName: string | null
  successMessage: string
  redirectUrl: string | null
  fields: FormFieldConfig[]
  submissionsCount: number
  createdAt: string
  updatedAt: string
}

export interface FormSubmission {
  id: string
  formId: string
  contactId: string | null
  contactName: string | null
  payload: Record<string, unknown>
  ipAddress: string | null
  submittedAt: string
}

export interface CreateLeadFormInput {
  title: string
  description?: string
  defaultLeadStage?: string
  assigneeUserId?: number | null
  successMessage?: string
  redirectUrl?: string
  fields?: FormFieldConfig[]
}

export interface UpdateLeadFormInput {
  title?: string
  description?: string
  status?: 'ACTIVE' | 'PAUSED'
  defaultLeadStage?: string
  assigneeUserId?: number | null
  successMessage?: string
  redirectUrl?: string
  fields?: FormFieldConfig[]
}

export interface PublicForm {
  formKey: string
  title: string
  description: string | null
  successMessage: string
  redirectUrl: string | null
  fields: FormFieldConfig[]
}

export interface SubmitPublicResult {
  success: boolean
  message: string
  redirectUrl: string | null
  contactId: string | null
}

const root = (companyId: string) =>
  `/api/v1/companies/${encodeURIComponent(companyId)}/crm/forms`

const unwrap = <T>(response: ApiResponse<T>): T => {
  if (response.data === undefined) {
    throw new Error(response.error_message ?? 'CRM Forms request failed')
  }
  return response.data
}

export const crmLeadFormsApi = {
  async list(companyId: string): Promise<LeadForm[]> {
    return unwrap(
      (await apiClient.get<ApiResponse<LeadForm[]>>(root(companyId))).data,
    )
  },

  async get(companyId: string, formId: string): Promise<LeadForm> {
    return unwrap(
      (await apiClient.get<ApiResponse<LeadForm>>(`${root(companyId)}/${encodeURIComponent(formId)}`)).data,
    )
  },

  async create(companyId: string, input: CreateLeadFormInput): Promise<LeadForm> {
    return unwrap(
      (await apiClient.post<ApiResponse<LeadForm>>(root(companyId), input)).data,
    )
  },

  async update(companyId: string, formId: string, input: UpdateLeadFormInput): Promise<LeadForm> {
    return unwrap(
      (await apiClient.patch<ApiResponse<LeadForm>>(`${root(companyId)}/${encodeURIComponent(formId)}`, input)).data,
    )
  },

  async delete(companyId: string, formId: string): Promise<void> {
    await apiClient.delete(`${root(companyId)}/${encodeURIComponent(formId)}`)
  },

  async submissions(companyId: string, formId: string, limit = 50): Promise<FormSubmission[]> {
    return unwrap(
      (
        await apiClient.get<ApiResponse<FormSubmission[]>>(
          `${root(companyId)}/${encodeURIComponent(formId)}/submissions?limit=${limit}`,
        )
      ).data,
    )
  },

  // Public Form methods (accessible outside workspace / unauthenticated)
  async getPublic(formKey: string): Promise<PublicForm> {
    return unwrap(
      (await apiClient.get<ApiResponse<PublicForm>>(`/api/v1/public/crm/forms/${encodeURIComponent(formKey)}`)).data,
    )
  },

  async submitPublic(formKey: string, payload: Record<string, unknown>): Promise<SubmitPublicResult> {
    return unwrap(
      (
        await apiClient.post<ApiResponse<SubmitPublicResult>>(
          `/api/v1/public/crm/forms/${encodeURIComponent(formKey)}/submit`,
          payload,
        )
      ).data,
    )
  },
}
