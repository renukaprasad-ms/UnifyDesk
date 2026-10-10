import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'

export type CrmAccount = {
  id: string
  name: string
  website: string | null
  notes: string | null
  ownerUserId: string | null
  version: number
  createdAt: string
}

export type CrmTask = {
  id: string
  title: string
  details: string | null
  status: 'OPEN' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED'
  priority: 'LOW' | 'NORMAL' | 'HIGH'
  dueAt: string | null
  assigneeUserId: string
  contactId: string | null
  accountId: string | null
  version: number
}

export type Contact360 = {
  lead: { stage: string; version: number } | null
  accounts: CrmAccount[]
  tasks: CrmTask[]
  moreAccounts: boolean
  moreTasks: boolean
}

export type AccountContact = { contactId: string; displayName: string | null }
export type Page<T> = { items: T[]; nextCursor: string | null; hasMore: boolean }

const root = (companyId: string) => '/api/v1/companies/' + encodeURIComponent(companyId) + '/crm'

const unwrap = <T>(response: ApiResponse<T>): T => {
  if (response.data === undefined) {
    throw new Error(response.error_message ?? 'CRM request failed')
  }
  return response.data
}

export const crmWorkApi = {
  async contact360(companyId: string, contactId: string) {
    const result = await apiClient.get<ApiResponse<Contact360>>(
      root(companyId) + '/contacts/' + encodeURIComponent(contactId) + '/overview',
    )
    return unwrap(result.data)
  },

  async linkContact(companyId: string, accountId: string, contactId: string) {
    await apiClient.post(
      root(companyId) + '/accounts/' + encodeURIComponent(accountId) +
        '/contacts/' + encodeURIComponent(contactId),
    )
  },

  async unlinkContact(companyId: string, accountId: string, contactId: string) {
    await apiClient.delete(
      root(companyId) + '/accounts/' + encodeURIComponent(accountId) +
        '/contacts/' + encodeURIComponent(contactId),
    )
  },

  async addContactTask(companyId: string, contactId: string, title: string) {
    const result = await apiClient.post<ApiResponse<CrmTask>>(root(companyId) + '/tasks', {
      title,
      contactId,
      priority: 'NORMAL',
    })
    return unwrap(result.data)
  },

  async accounts(companyId: string, before?: string | null) {
    const result = await apiClient.get<ApiResponse<Page<CrmAccount>>>(root(companyId) + '/accounts', {
      params: { before: before || undefined },
    })
    return unwrap(result.data)
  },

  async getAccount(companyId: string, accountId: string) {
    const result = await apiClient.get<ApiResponse<CrmAccount>>(
      root(companyId) + '/accounts/' + encodeURIComponent(accountId),
    )
    return unwrap(result.data)
  },

  async accountContactSummaries(companyId: string, accountId: string) {
    const result = await apiClient.get<ApiResponse<AccountContact[]>>(
      root(companyId) + '/accounts/' + encodeURIComponent(accountId) + '/contact-summaries',
    )
    return unwrap(result.data)
  },

  async addAccount(companyId: string, name: string) {
    const result = await apiClient.post<ApiResponse<CrmAccount>>(root(companyId) + '/accounts', {
      name,
    })
    return unwrap(result.data)
  },

  async updateAccount(companyId: string, account: CrmAccount, changes: {
    name: string
    website: string | null
    notes: string | null
    ownerUserId: string | null
  }) {
    const response = await apiClient.patch<ApiResponse<CrmAccount>>(
      root(companyId) + '/accounts/' + encodeURIComponent(account.id),
      { ...changes, version: account.version },
    )
    return unwrap(response.data)
  },

  async tasks(companyId: string, before?: string | null) {
    const result = await apiClient.get<ApiResponse<Page<CrmTask>>>(root(companyId) + '/tasks', {
      params: { before: before || undefined },
    })
    return unwrap(result.data)
  },

  async addTask(companyId: string, title: string, dueAt?: string) {
    const result = await apiClient.post<ApiResponse<CrmTask>>(root(companyId) + '/tasks', {
      title,
      dueAt: dueAt || null,
    })
    return unwrap(result.data)
  },

  async updateTask(companyId: string, task: CrmTask, changes: {
    title: string
    details: string | null
    priority: CrmTask['priority']
    dueAt: string | null
    assigneeUserId: string
    contactId: string | null
    accountId: string | null
  }) {
    const response = await apiClient.patch<ApiResponse<CrmTask>>(
      root(companyId) + '/tasks/' + encodeURIComponent(task.id),
      { ...changes, version: task.version },
    )
    return unwrap(response.data)
  },

  async taskStatus(companyId: string, task: CrmTask, status: CrmTask['status']) {
    const result = await apiClient.patch<ApiResponse<CrmTask>>(
      root(companyId) + '/tasks/' + encodeURIComponent(task.id) + '/status',
      { version: task.version, status },
    )
    return unwrap(result.data)
  },
}
