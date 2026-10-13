import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'

export const dealStages = ['NEW', 'QUALIFIED', 'PROPOSAL', 'NEGOTIATION', 'WON', 'LOST'] as const
export type DealStage = (typeof dealStages)[number]
export type DealFields = {
  title: string
  description: string | null
  amount: string
  currency: string
  expectedCloseDate: string | null
  ownerUserId: string | null
  contactId: string | null
  accountId: string | null
}
export type Deal = DealFields & {
  id: string
  stage: DealStage
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
  previousStage: DealStage | null
  newStage: DealStage
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
  async deals(company: string, params: { stage?: DealStage; before?: string; q?: string } = {}) {
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
  async moveDeal(company: string, deal: Deal, stage: DealStage) {
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
export const dealStageColor = (stage: string) =>
  (
    ({
      NEW: 'bg-sky-50 text-sky-700',
      QUALIFIED: 'bg-indigo-50 text-indigo-700',
      PROPOSAL: 'bg-violet-50 text-violet-700',
      NEGOTIATION: 'bg-amber-50 text-amber-700',
      WON: 'bg-emerald-50 text-emerald-700',
      LOST: 'bg-rose-50 text-rose-700',
    }) as Record<string, string>
  )[stage] || 'bg-slate-100 text-slate-600'
