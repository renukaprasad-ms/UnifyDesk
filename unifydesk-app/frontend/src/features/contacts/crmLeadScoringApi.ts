import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'

export type ScoreRuleType =
  | 'HAS_PHONE'
  | 'HAS_EMAIL'
  | 'HAS_ORGANIZATION'
  | 'STAGE_MATCH'
  | 'FORM_SUBMISSION'
  | 'HAS_DEAL'
  | 'COMMUNICATION_ACTIVITY'
  | 'CUSTOM_FIELD_MATCH'

export type ScoreRule = {
  id: string
  ruleName: string
  description: string | null
  ruleType: ScoreRuleType
  conditionConfig: Record<string, any>
  points: number
  isEnabled: boolean
  displayOrder: number
  createdAt: string
  updatedAt: string
}

export type ScoreSettings = {
  hotThreshold: number
  warmThreshold: number
  updatedAt: string
}

export type ScoreBreakdownItem = {
  ruleId: string
  ruleName: string
  ruleType: string
  points: number
  reason: string
}

export type LeadScoreResult = {
  contactId: string
  score: number
  temperature: 'HOT' | 'WARM' | 'COLD'
  hotThreshold: number
  warmThreshold: number
  breakdown: ScoreBreakdownItem[]
  scoredAt: string
}

export type SaveRuleInput = {
  ruleName: string
  description?: string | null
  ruleType: ScoreRuleType
  conditionConfig?: Record<string, any>
  points: number
  isEnabled?: boolean
  displayOrder?: number
}

export type SaveSettingsInput = {
  hotThreshold: number
  warmThreshold: number
}

const root = (companyId: string) => `/api/v1/companies/${companyId}/crm/scoring`

const unwrap = <T>(res: ApiResponse<T>) => {
  if (res.data === undefined) throw new Error(res.error_message || 'Lead scoring request failed')
  return res.data
}

export const crmLeadScoringApi = {
  async getSettings(companyId: string): Promise<ScoreSettings> {
    return unwrap((await apiClient.get<ApiResponse<ScoreSettings>>(root(companyId) + '/settings')).data)
  },

  async updateSettings(companyId: string, input: SaveSettingsInput): Promise<ScoreSettings> {
    return unwrap(
      (await apiClient.put<ApiResponse<ScoreSettings>>(root(companyId) + '/settings', input)).data,
    )
  },

  async getRules(companyId: string): Promise<ScoreRule[]> {
    return unwrap((await apiClient.get<ApiResponse<ScoreRule[]>>(root(companyId) + '/rules')).data)
  },

  async createRule(companyId: string, input: SaveRuleInput): Promise<ScoreRule> {
    return unwrap(
      (await apiClient.post<ApiResponse<ScoreRule>>(root(companyId) + '/rules', input)).data,
    )
  },

  async updateRule(companyId: string, ruleId: string, input: SaveRuleInput): Promise<ScoreRule> {
    return unwrap(
      (
        await apiClient.put<ApiResponse<ScoreRule>>(
          `${root(companyId)}/rules/${encodeURIComponent(ruleId)}`,
          input,
        )
      ).data,
    )
  },

  async toggleRule(companyId: string, ruleId: string, enabled: boolean): Promise<ScoreRule> {
    return unwrap(
      (
        await apiClient.patch<ApiResponse<ScoreRule>>(
          `${root(companyId)}/rules/${encodeURIComponent(ruleId)}/toggle`,
          null,
          { params: { enabled } },
        )
      ).data,
    )
  },

  async deleteRule(companyId: string, ruleId: string): Promise<void> {
    await apiClient.delete(`${root(companyId)}/rules/${encodeURIComponent(ruleId)}`)
  },

  async recalculate(companyId: string): Promise<number> {
    return unwrap(
      (await apiClient.post<ApiResponse<number>>(root(companyId) + '/recalculate')).data,
    )
  },

  async getLeadScore(companyId: string, contactId: string): Promise<LeadScoreResult> {
    return unwrap(
      (
        await apiClient.get<ApiResponse<LeadScoreResult>>(
          `${root(companyId)}/leads/${encodeURIComponent(contactId)}`,
        )
      ).data,
    )
  },
}
