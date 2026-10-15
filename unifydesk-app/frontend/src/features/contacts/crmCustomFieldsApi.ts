import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'

export type CrmFieldType = 'TEXT' | 'NUMBER' | 'DATE' | 'BOOLEAN' | 'DROPDOWN' | 'MULTI_SELECT' | 'URL'
export type CrmEntityType = 'LEAD' | 'DEAL' | 'ACCOUNT'

export type CrmFieldDef = {
  id: string
  entityType: CrmEntityType
  fieldKey: string
  label: string
  fieldType: CrmFieldType
  options: string[] | null
  required: boolean
  displayOrder: number
  createdAt: string
}

export type CreateFieldInput = {
  entityType: CrmEntityType
  fieldKey: string
  label: string
  fieldType: CrmFieldType
  options?: string[]
  required: boolean
  displayOrder?: number
}

export type UpdateFieldInput = {
  label: string
  options?: string[]
  required: boolean
  displayOrder?: number
}

/** Custom field values are an open key→value map attached to Lead/Deal/Account */
export type CustomFieldValues = Record<string, string | number | boolean | string[] | null | undefined>

const root = (companyId: string) =>
  '/api/v1/companies/' + encodeURIComponent(companyId) + '/crm/custom-fields'

const unwrap = <T>(response: ApiResponse<T>): T => {
  if (response.data === undefined) throw new Error(response.error_message ?? 'Custom fields request failed')
  return response.data
}

export const crmCustomFieldsApi = {
  async list(companyId: string, entityType: CrmEntityType): Promise<CrmFieldDef[]> {
    return unwrap(
      (await apiClient.get<ApiResponse<CrmFieldDef[]>>(root(companyId) + '?entityType=' + entityType)).data,
    )
  },

  async create(companyId: string, input: CreateFieldInput): Promise<CrmFieldDef> {
    return unwrap(
      (await apiClient.post<ApiResponse<CrmFieldDef>>(root(companyId), input)).data,
    )
  },

  async update(companyId: string, fieldId: string, input: UpdateFieldInput): Promise<CrmFieldDef> {
    return unwrap(
      (await apiClient.patch<ApiResponse<CrmFieldDef>>(root(companyId) + '/' + fieldId, input)).data,
    )
  },

  async delete(companyId: string, fieldId: string): Promise<void> {
    await apiClient.delete(root(companyId) + '/' + fieldId)
  },
}
