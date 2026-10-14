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
  attributionChannel?: string | null
  broadcastMessageId?: string | null
  adCampaignId?: string | null
  leadFormId?: string | null
  utmSource?: string | null
  utmMedium?: string | null
  utmCampaign?: string | null
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
  attributionChannel: string
  broadcastMessageId: string | null
  broadcastSubject: string | null
  adCampaignId: string | null
  adCampaignName: string | null
  leadFormId: string | null
  leadFormTitle: string | null
  utmSource: string | null
  utmMedium: string | null
  utmCampaign: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export type DealAttachment = {
  id: string
  dealId: string
  mediaId: string
  filename: string
  contentType: string | null
  sizeBytes: number
  uploaderUserId: string | null
  uploaderName: string | null
  createdAt: string
}

export type AttachDealDocumentInput = {
  mediaId: string
  filename?: string
  contentType?: string
  sizeBytes?: number
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

export type CrmForecastSummary = {
  totalOpenDeals: number
  unweightedPipeline: number | string
  weightedPipeline: number | string
  wonDealsCount: number
  wonAmount: number | string
  lostDealsCount: number
  lostAmount: number | string
  totalForecast: number | string
  targetQuota: number | string
  quotaAttainmentPct: number
  projectedAttainmentPct: number
  winRatePct: number
  currency: string
}

export type CrmForecastStage = {
  stageKey: string
  stageName: string
  category: string
  probability: number
  color: string
  dealsCount: number
  totalAmount: number | string
  weightedAmount: number | string
}

export type CrmForecastTimeline = {
  periodKey: string
  periodLabel: string
  openDealsCount: number
  unweightedAmount: number | string
  weightedAmount: number | string
  wonDealsCount: number
  wonAmount: number | string
}

export type CrmForecastRep = {
  userId: string
  userName: string
  openDealsCount: number
  unweightedAmount: number | string
  weightedAmount: number | string
  wonDealsCount: number
  wonAmount: number | string
  targetQuota: number | string
  quotaAttainmentPct: number
  projectedAttainmentPct: number
}

export type CrmForecastDeal = {
  id: string
  title: string
  stage: string
  stageName: string
  category: string
  probability: number
  amount: number | string
  weightedAmount: number | string
  currency: string
  expectedCloseDate: string | null
  ownerUserId: string | null
  ownerName: string
  accountName: string | null
  contactName: string | null
}

export type CrmForecastResponse = {
  period: string
  periodLabel: string
  startDate: string | null
  endDate: string | null
  pipelineId: number | null
  pipelineName: string
  summary: CrmForecastSummary
  stages: CrmForecastStage[]
  timeline: CrmForecastTimeline[]
  reps: CrmForecastRep[]
  deals: CrmForecastDeal[]
  generatedAt: string
}

export type CrmSalesQuota = {
  id: string
  userId: string
  userName: string
  periodType: string
  periodKey: string
  targetAmount: number | string
  currency: string
  updatedAt: string
}

export type SetQuotaInput = {
  userId: string | number
  periodType: string
  periodKey: string
  targetAmount: number
  currency?: string
}

export type ChannelAttribution = {
  channel: string
  channelLabel: string
  dealsCount: number
  openAmount: number | string
  wonDealsCount: number
  wonAmount: number | string
  lostDealsCount: number
  winRatePct: number
}

export type CampaignRoi = {
  id: string
  title: string
  campaignType: string // 'BROADCAST', 'AD_CAMPAIGN', 'WEB_FORM'
  subtype: string
  dealsCount: number
  openAmount: number | string
  wonDealsCount: number
  wonAmount: number | string
}

export type AttributionReport = {
  period: string
  periodLabel: string
  totalWonRevenue: number | string
  marketingAttributedWon: number | string
  marketingContributionPct: number
  totalAttributedDeals: number
  channels: ChannelAttribution[]
  topBroadcasts: CampaignRoi[]
  topAdCampaigns: CampaignRoi[]
  topLeadForms: CampaignRoi[]
  generatedAt: string
}

export type BroadcastOption = {
  id: string
  subject: string
  channelType: string
}

export type AdCampaignOption = {
  id: string
  name: string
  provider: string
}

export type LeadFormOption = {
  id: string
  title: string
  formKey: string
}

export type AttributionOptions = {
  broadcasts: BroadcastOption[]
  adCampaigns: AdCampaignOption[]
  leadForms: LeadFormOption[]
  standardChannels: string[]
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

  async deleteDeal(company: string, id: string): Promise<void> {
    await apiClient.delete(root(company) + '/deals/' + encodeURIComponent(id))
  },

  async dealComments(company: string, dealId: string) {
    return unwrap(
      (
        await apiClient.get<
          ApiResponse<
            Array<{
              id: string
              entityType: string
              entityId: string
              body: string
              authorUserId: string | null
              authorName: string | null
              authorEmail: string | null
              createdAt: string
              updatedAt: string
            }>
          >
        >(root(company) + '/deals/' + encodeURIComponent(dealId) + '/comments')
      ).data,
    )
  },

  async addDealComment(company: string, dealId: string, body: string) {
    return unwrap(
      (
        await apiClient.post<
          ApiResponse<{
            id: string
            entityType: string
            entityId: string
            body: string
            authorUserId: string | null
            authorName: string | null
            authorEmail: string | null
            createdAt: string
            updatedAt: string
          }>
        >(root(company) + '/deals/' + encodeURIComponent(dealId) + '/comments', { body })
      ).data,
    )
  },

  async deleteDealComment(company: string, dealId: string, commentId: string): Promise<void> {
    await apiClient.delete(
      root(company) +
        '/deals/' +
        encodeURIComponent(dealId) +
        '/comments/' +
        encodeURIComponent(commentId),
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

  async listAttachments(company: string, dealId: string): Promise<DealAttachment[]> {
    return unwrap(
      (
        await apiClient.get<ApiResponse<DealAttachment[]>>(
          `${root(company)}/deals/${encodeURIComponent(dealId)}/attachments`,
        )
      ).data,
    )
  },

  async attachDocument(
    company: string,
    dealId: string,
    input: AttachDealDocumentInput,
  ): Promise<DealAttachment> {
    return unwrap(
      (
        await apiClient.post<ApiResponse<DealAttachment>>(
          `${root(company)}/deals/${encodeURIComponent(dealId)}/attachments`,
          input,
        )
      ).data,
    )
  },

  async deleteAttachment(
    company: string,
    dealId: string,
    attachmentId: string,
  ): Promise<void> {
    await apiClient.delete(
      `${root(company)}/deals/${encodeURIComponent(dealId)}/attachments/${encodeURIComponent(attachmentId)}`,
    )
  },

  async getForecast(
    company: string,
    params: { period?: string; pipelineId?: string; ownerUserId?: string } = {},
  ): Promise<CrmForecastResponse> {
    return unwrap(
      (
        await apiClient.get<ApiResponse<CrmForecastResponse>>(root(company) + '/forecast', {
          params,
        })
      ).data,
    )
  },

  async getQuotas(
    company: string,
    params: { periodType?: string; periodKey?: string } = {},
  ): Promise<CrmSalesQuota[]> {
    return unwrap(
      (
        await apiClient.get<ApiResponse<CrmSalesQuota[]>>(root(company) + '/quotas', {
          params,
        })
      ).data,
    )
  },

  async setQuota(company: string, input: SetQuotaInput): Promise<CrmSalesQuota> {
    return unwrap(
      (
        await apiClient.put<ApiResponse<CrmSalesQuota>>(root(company) + '/quotas', input)
      ).data,
    )
  },

  async getAttributionReport(
    company: string,
    params: { period?: string } = {},
  ): Promise<AttributionReport> {
    return unwrap(
      (
        await apiClient.get<ApiResponse<AttributionReport>>(root(company) + '/reports/attribution', {
          params,
        })
      ).data,
    )
  },

  async getAttributionOptions(company: string): Promise<AttributionOptions> {
    return unwrap(
      (
        await apiClient.get<ApiResponse<AttributionOptions>>(root(company) + '/attribution/options')
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
