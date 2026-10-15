import { apiClient } from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'

export type TriggerType =
  | 'LEAD_ENROLLED'
  | 'LEAD_STAGE_CHANGED'
  | 'FORM_SUBMITTED'
  | 'DEAL_CREATED'
  | 'DEAL_STAGE_CHANGED'

export type ActionType =
  | 'CREATE_TASK'
  | 'ASSIGN_OWNER'
  | 'UPDATE_LEAD_STAGE'

export interface AutomationRule {
  id: string
  companyId: string
  name: string
  description?: string | null
  isEnabled: boolean
  triggerType: TriggerType
  triggerConfig: Record<string, any>
  actionType: ActionType
  actionConfig: Record<string, any>
  executionCount: number
  lastExecutedAt?: string | null
  createdByUserId?: string | null
  createdAt: string
  updatedAt: string
}

export interface AutomationLog {
  id: string
  companyId: string
  ruleId: string
  ruleName: string
  triggerType: TriggerType
  entityType: string
  entityId?: string | null
  actionType: ActionType
  status: 'SUCCESS' | 'FAILED' | 'SKIPPED'
  details: string
  executedAt: string
}

export interface SaveRuleInput {
  name: string
  description?: string | null
  isEnabled?: boolean
  triggerType: TriggerType
  triggerConfig: Record<string, any>
  actionType: ActionType
  actionConfig: Record<string, any>
}

const root = (companyId: string) => `/api/v1/companies/${companyId}/crm/automation`

const unwrap = <T>(res: ApiResponse<T>) => {
  if (res.data === undefined) throw new Error(res.error_message || 'CRM automation request failed')
  return res.data
}

export const crmAutomationApi = {
  async listRules(companyId: string): Promise<AutomationRule[]> {
    return unwrap(
      (await apiClient.get<ApiResponse<AutomationRule[]>>(`${root(companyId)}/rules`)).data
    )
  },

  async createRule(companyId: string, input: SaveRuleInput): Promise<AutomationRule> {
    return unwrap(
      (await apiClient.post<ApiResponse<AutomationRule>>(`${root(companyId)}/rules`, input)).data
    )
  },

  async updateRule(
    companyId: string,
    ruleId: string,
    input: SaveRuleInput
  ): Promise<AutomationRule> {
    return unwrap(
      (await apiClient.put<ApiResponse<AutomationRule>>(`${root(companyId)}/rules/${ruleId}`, input)).data
    )
  },

  async toggleRule(
    companyId: string,
    ruleId: string,
    isEnabled: boolean
  ): Promise<AutomationRule> {
    return unwrap(
      (await apiClient.patch<ApiResponse<AutomationRule>>(`${root(companyId)}/rules/${ruleId}/toggle`, {
        isEnabled,
      })).data
    )
  },

  async deleteRule(companyId: string, ruleId: string): Promise<void> {
    await apiClient.delete(`${root(companyId)}/rules/${ruleId}`)
  },

  async listLogs(
    companyId: string,
    ruleId?: string,
    limit: number = 50
  ): Promise<AutomationLog[]> {
    const params = new URLSearchParams()
    if (ruleId) params.set('ruleId', ruleId)
    params.set('limit', String(limit))
    return unwrap(
      (await apiClient.get<ApiResponse<AutomationLog[]>>(`${root(companyId)}/logs?${params.toString()}`)).data
    )
  },
}
