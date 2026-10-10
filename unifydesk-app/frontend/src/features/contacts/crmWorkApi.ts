import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'

export type CrmAccount = {
  id: string; name: string; website: string | null; notes: string | null
  ownerUserId: string | null; version: number; createdAt: string
}
export type CrmTask = {
  id: string; title: string; details: string | null
  status: 'OPEN' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED'
  priority: 'LOW' | 'NORMAL' | 'HIGH'; dueAt: string | null
  assigneeUserId: string; contactId: string | null; accountId: string | null
  version: number
}
export type Page<T> = { items: T[]; nextCursor: string | null; hasMore: boolean }
const root = (companyId: string) => '/api/v1/companies/' + companyId + '/crm'
const unwrap = <T>(response: ApiResponse<T>): T => {
  if (response.data === undefined) throw new Error(response.error_message ?? 'CRM request failed')
  return response.data
}
export const crmWorkApi = {
  async accounts(companyId: string, before?: string | null) {
    const result = await apiClient.get<ApiResponse<Page<CrmAccount>>>(root(companyId) + '/accounts', {
      params: { before: before || undefined },
    })
    return unwrap(result.data)
  },
  async addAccount(companyId: string, name: string) {
    const result = await apiClient.post<ApiResponse<CrmAccount>>(root(companyId) + '/accounts', { name })
    return unwrap(result.data)
  },
  async tasks(companyId: string, before?: string | null) {
    const result = await apiClient.get<ApiResponse<Page<CrmTask>>>(root(companyId) + '/tasks', {
      params: { before: before || undefined },
    })
    return unwrap(result.data)
  },
  async addTask(companyId: string, title: string, dueAt?: string) {
    const result = await apiClient.post<ApiResponse<CrmTask>>(root(companyId) + '/tasks', {
      title, dueAt: dueAt || null,
    })
    return unwrap(result.data)
  },
  async taskStatus(companyId: string, task: CrmTask, status: CrmTask['status']) {
    const result = await apiClient.patch<ApiResponse<CrmTask>>(
      root(companyId) + '/tasks/' + encodeURIComponent(task.id) + '/status',
      { version: task.version, status },
    )
    return unwrap(result.data)
  },
}
