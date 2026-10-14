import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type { CustomFieldValues } from './crmCustomFieldsApi'

export const dealStages = ['NEW', 'QUALIFIED', 'PROPOSAL', 'NEGOTIATION', 'WON', 'LOST'] as const
export type DealStage = string

export type PipelineStage = {
  id: string
  key: string
  name: string
  category: 'OPEN' | 'WON' | 'LOST'
  probability: number
  displayOrder: number
  color: string
}

export type Pipeline = {
  id: string
  name: string
  description: string | null
  isDefault: boolean
  displayOrder: number
  stages: PipelineStage[]
}

export type CreatePipelineInput = {
  name: string
  description?: string | null
  isDefault?: boolean
  stages?: Array<{
    key?: string
    name: string
    category?: 'OPEN' | 'WON' | 'LOST'
    probability?: number
    color?: string
  }>
}

export type UpdatePipelineInput = {
  name?: string
  description?: string | null
  isDefault?: boolean
  displayOrder?: number
  stages?: Array<{
    id?: string
    key?: string
    name: string
    category?: 'OPEN' | 'WON' | 'LOST'
    probability?: number
    color?: string
  }>
}

export type DealFields = {
  title: string
  description: string | null
  amount: string
  currency: string
  expectedCloseDate: string | null
  ownerUserId: string | null
  contactId: string | null
  accountId: string | null
  pipelineId?: string | null
  stage?: string
  customFields?: CustomFieldValues
}

export type Deal = DealFields & {
  id: string
  stage: string
  pipelineId: string | null
  pipelineName: string | null
  ownerName: string | null
  contactName: string | null
  accountName: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export type DealEvent = {
  id: string
  eventType: string
  previousStage: string | null
  newStage: string
  changedFields: string | null
  actorName: string | null
  actorUsername: string | null
  occurredAt: string
}

export type CrmActivity = {
  id: string
  source: string
  title: string
  detail: string
  actorName: string | null
  actorUsername: string | null
  contactId: string | null
  occurredAt: string
  href: string | null
}

export type CrmPage<T> = { items: T[]; nextCursor: string | null; hasMore: boolean }

export type CrmReport = {
  days: number
  leads: { label: string; value: number }[]
  deals: { label: string; value: number }[]
  tasks: { label: string; value: number }[]
  overdueTasks: number
  values: { currency: string; openValue: string; wonValue: string; lostValue: string }[]
  trend: { date: string; leads: number; deals: number; completedTasks: number }[]
  team: { userId: string; name: string; actions: number }[]
  generatedAt: string
}

const root = (company: string) => `/api/v1/companies/${encodeURIComponent(company)}/crm`

const unwrap = <T>(response: ApiResponse<T>) => {
  if (response.data === undefined) throw new Error(response.error_message || 'CRM request failed')
  return response.data
}

export const crmSalesApi = {
  async deals(
    company: string,
    params: { pipelineId?: string; stage?: string; before?: string; q?: string } = {},
  ) {
    return unwrap(
      (await apiClient.get<ApiResponse<CrmPage<Deal>>>(root(company) + '/deals', { params })).data,
    )
  },

  async deal(company: string, id: string) {
    return unwrap(
      (await apiClient.get<ApiResponse<Deal>>(root(company) + '/deals/' + encodeURIComponent(id)))
        .data,
    )
  },

  async createDeal(company: string, fields: DealFields) {
    return unwrap((await apiClient.post<ApiResponse<Deal>>(root(company) + '/deals', fields)).data)
  },

  async editDeal(company: string, deal: Deal, fields: DealFields) {
    return unwrap(
      (
        await apiClient.patch<ApiResponse<Deal>>(
          root(company) + '/deals/' + encodeURIComponent(deal.id),
          { version: deal.version, fields },
        )
      ).data,
    )
  },

  async moveDeal(company: string, deal: Deal, stage: string) {
    return unwrap(
      (
        await apiClient.patch<ApiResponse<Deal>>(
          root(company) + '/deals/' + encodeURIComponent(deal.id) + '/stage',
          { version: deal.version, stage },
        )
      ).data,
    )
  },

  async history(company: string, id: string) {
    return unwrap(
      (
        await apiClient.get<ApiResponse<DealEvent[]>>(
          root(company) + '/deals/' + encodeURIComponent(id) + '/history',
        )
      ).data,
    )
  },

  async pipelines(company: string): Promise<Pipeline[]> {
    return unwrap(
      (await apiClient.get<ApiResponse<Pipeline[]>>(root(company) + '/pipelines')).data,
    )
  },

  async pipeline(company: string, id: string): Promise<Pipeline> {
    return unwrap(
      (await apiClient.get<ApiResponse<Pipeline>>(root(company) + '/pipelines/' + encodeURIComponent(id))).data,
    )
  },

  async createPipeline(company: string, input: CreatePipelineInput): Promise<Pipeline> {
    return unwrap(
      (await apiClient.post<ApiResponse<Pipeline>>(root(company) + '/pipelines', input)).data,
    )
  },

  async updatePipeline(company: string, id: string, input: UpdatePipelineInput): Promise<Pipeline> {
    return unwrap(
      (
        await apiClient.patch<ApiResponse<Pipeline>>(
          root(company) + '/pipelines/' + encodeURIComponent(id),
          input,
        )
      ).data,
    )
  },

  async deletePipeline(company: string, id: string): Promise<void> {
    await apiClient.delete(root(company) + '/pipelines/' + encodeURIComponent(id))
  },

  async activities(
    company: string,
    params: {
      source?: string
      person?: string
      contactId?: string
      from?: string
      to?: string
      cursor?: string
    },
  ) {
    return unwrap(
      (
        await apiClient.get<ApiResponse<CrmPage<CrmActivity>>>(root(company) + '/activities', {
          params,
        })
      ).data,
    )
  },

  async note(company: string, body: string, contactId: string | null) {
    await apiClient.post(root(company) + '/activities/notes', { body, contactId })
  },

  async report(company: string, days: number) {
    return unwrap(
      (
        await apiClient.get<ApiResponse<CrmReport>>(root(company) + '/reports', {
          params: { days },
        })
      ).data,
    )
  },
}

export const salesLabel = (value: string) =>
  value
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/^./, (c) => c.toUpperCase())

export const dealMoney = (amount: string, currency: string) => {
  const [whole, fraction] = amount.split('.')
  return `${currency} ${BigInt(whole || '0').toLocaleString()}${fraction ? '.' + fraction : ''}`
}

export const dealStageColor = (stage: string, color?: string) => {
  if (color) {
    const map: Record<string, string> = {
      sky: 'bg-sky-50 text-sky-700 dark:bg-sky-950/60 dark:text-sky-300',
      indigo: 'bg-indigo-50 text-indigo-700 dark:bg-indigo-950/60 dark:text-indigo-300',
      violet: 'bg-violet-50 text-violet-700 dark:bg-violet-950/60 dark:text-violet-300',
      amber: 'bg-amber-50 text-amber-700 dark:bg-amber-950/60 dark:text-amber-300',
      emerald: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300',
      rose: 'bg-rose-50 text-rose-700 dark:bg-rose-950/60 dark:text-rose-300',
      blue: 'bg-blue-50 text-blue-700 dark:bg-blue-950/60 dark:text-blue-300',
      teal: 'bg-teal-50 text-teal-700 dark:bg-teal-950/60 dark:text-teal-300',
      purple: 'bg-purple-50 text-purple-700 dark:bg-purple-950/60 dark:text-purple-300',
      slate: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300',
    }
    if (map[color.toLowerCase()]) return map[color.toLowerCase()]
  }

  const legacyMap: Record<string, string> = {
    NEW: 'bg-sky-50 text-sky-700 dark:bg-sky-950/60 dark:text-sky-300',
    QUALIFIED: 'bg-indigo-50 text-indigo-700 dark:bg-indigo-950/60 dark:text-indigo-300',
    PROPOSAL: 'bg-violet-50 text-violet-700 dark:bg-violet-950/60 dark:text-violet-300',
    NEGOTIATION: 'bg-amber-50 text-amber-700 dark:bg-amber-950/60 dark:text-amber-300',
    WON: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300',
    LOST: 'bg-rose-50 text-rose-700 dark:bg-rose-950/60 dark:text-rose-300',
  }
  return legacyMap[stage.toUpperCase()] || 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300'
}
