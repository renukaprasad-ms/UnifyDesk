import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type {
  Page,
  PlatformAccess,
  PlatformAdmin,
  PlatformCompany,
  PlatformPermission,
  PlatformUser,
  PlatformPlan,
  PlatformPlanInput,
  PlatformWebhookCallback,
  ManagedProviderRoute,
  ManagedSmsReviewItem,
  WebhookCallbackStatus,
} from './types'

export const platformApi = {
  async managedSmsReviewQueue() {
    const r=await apiClient.get<ApiResponse<{items:ManagedSmsReviewItem[]}>>(
      '/api/v1/platform/managed-sms/review-queue')
    return r.data.data?.items ?? []
  },
  async decideManagedSmsReview(item:ManagedSmsReviewItem, input: {
    decision:'APPROVE'|'REJECT';version:number;reason:string;
    providerAccountRef:string|null;providerClientRef:string|null;
    providerTemplateRef:string|null;providerFlowRef:string|null;
    providerMappingRef:string|null;ctaPolicy?:unknown;
  }) {
    const resource=item.resourceType==='DLT_PROFILE'?'dlt-profiles':
      item.resourceType==='HEADER'?'headers':'templates'
    const r=await apiClient.post<ApiResponse<unknown>>(
      `/api/v1/platform/managed-sms/${resource}/${item.id}/decision`,input)
    return r.data.data
  },
  async managedSmsCompanyProvider(companyId: string) {
    const r=await apiClient.get<ApiResponse<string | null>>(
      `/api/v1/platform/managed-providers/sms/companies/${companyId}`)
    return r.data.data ?? null
  },
  async changeManagedSmsCompanyProvider(companyId: string, input: {
    provider: string | null; reason: string
  }) {
    const r=await apiClient.put<ApiResponse<string>>(
      `/api/v1/platform/managed-providers/sms/companies/${companyId}`,input)
    return r.data.data
  },
  async metaReachCompanyApproval(companyId: string) {
    const r=await apiClient.get<ApiResponse<{
      companyId: string; peId: string; evidenceReference: string; active: boolean; approvedAt: string
    } | null>>(`/api/v1/platform/managed-providers/sms/companies/${companyId}/approval`)
    return r.data.data ?? null
  },
  async changeMetaReachCompanyApproval(companyId: string, input: {
    active: boolean; peId: string; evidenceReference: string; reason: string
  }) {
    const r=await apiClient.put<ApiResponse<unknown>>(
      `/api/v1/platform/managed-providers/sms/companies/${companyId}/approval`,input)
    return r.data.data
  },
  async managedProviders() {
    const r = await apiClient.get<ApiResponse<ManagedProviderRoute[]>>('/api/v1/platform/managed-providers')
    if (!r.data.data) throw new Error(r.data.error_message ?? 'Unable to load managed providers')
    return r.data.data
  },
  async changeManagedProvider(channel: string, input: { provider: string; expectedRevision: number; reason: string }) {
    const r = await apiClient.put<ApiResponse<ManagedProviderRoute>>(`/api/v1/platform/managed-providers/${channel}`, input)
    if (!r.data.data) throw new Error(r.data.error_message ?? 'Unable to change managed provider')
    return r.data.data
  },
  async me() {
    const response = await apiClient.get<ApiResponse<PlatformAccess>>('/api/v1/platform/me')
    if (!response.data.data)
      throw new Error(response.data.error_message ?? 'Unable to load platform access')
    return response.data.data
  },
  async admins(search = '', page = 0) {
    const r = await apiClient.get<ApiResponse<Page<PlatformAdmin>>>('/api/v1/platform/admins', {
      params: { search, page, size: 20 },
    })
    if (!r.data.data) throw new Error(r.data.error_message ?? 'Unable to load admins')
    return r.data.data
  },
  async createAdmin(email: string, permissions: PlatformPermission[]) {
    const r = await apiClient.post<ApiResponse<PlatformAdmin>>('/api/v1/platform/admins', {
      email,
      permissions,
    })
    if (!r.data.data) throw new Error(r.data.error_message ?? 'Unable to create admin')
    return r.data.data
  },
  async companies(search = '', page = 0) {
    const r = await apiClient.get<ApiResponse<Page<PlatformCompany>>>(
      '/api/v1/platform/companies',
      { params: { search, page, size: 20 } },
    )
    if (!r.data.data) throw new Error(r.data.error_message ?? 'Unable to load companies')
    return r.data.data
  },
  async updateCompanyStatus(id: string, status: PlatformCompany['status']) {
    const r = await apiClient.patch<ApiResponse<PlatformCompany>>(
      `/api/v1/platform/companies/${id}/status`,
      { status },
    )
    if (!r.data.data) throw new Error(r.data.error_message ?? 'Unable to update company')
    return r.data.data
  },
  async users(search = '', page = 0) {
    const r = await apiClient.get<ApiResponse<Page<PlatformUser>>>('/api/v1/platform/users', {
      params: { search, page, size: 20 },
    })
    if (!r.data.data) throw new Error(r.data.error_message ?? 'Unable to load users')
    return r.data.data
  },
  async plans(search = '', page = 0) {
    const r = await apiClient.get<ApiResponse<Page<PlatformPlan>>>('/api/v1/platform/plans', {
      params: { search, page, size: 20 },
    })
    if (!r.data.data) throw new Error(r.data.error_message ?? 'Unable to load plans')
    return r.data.data
  },
  async createPlan(input: PlatformPlanInput) {
    const r = await apiClient.post<ApiResponse<PlatformPlan>>('/api/v1/platform/plans', input)
    if (!r.data.data) throw new Error(r.data.error_message ?? 'Unable to create plan')
    return r.data.data
  },
  async updatePlan(id: string, input: PlatformPlanInput) {
    const r = await apiClient.put<ApiResponse<PlatformPlan>>(`/api/v1/platform/plans/${id}`, input)
    if (!r.data.data) throw new Error(r.data.error_message ?? 'Unable to update plan')
    return r.data.data
  },
  async deletePlan(id: string) {
    await apiClient.delete(`/api/v1/platform/plans/${id}`)
  },
  async webhookCallbacks({
    search = '',
    provider = '',
    status = '',
    page = 0,
  }: {
    search?: string
    provider?: string
    status?: WebhookCallbackStatus | ''
    page?: number
  }) {
    const r = await apiClient.get<ApiResponse<Page<PlatformWebhookCallback>>>(
      '/api/v1/platform/channel-analytics/webhook-callbacks',
      {
        params: {
          search,
          page,
          size: 20,
          ...(provider ? { provider } : {}),
          ...(status ? { status } : {}),
        },
      },
    )
    if (!r.data.data) throw new Error(r.data.error_message ?? 'Unable to load webhook callbacks')
    return r.data.data
  },
}
