import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type { Page, PlatformAccess, PlatformAdmin, PlatformCompany, PlatformPermission } from './types'

export const platformApi = {
  async me() {
    const response = await apiClient.get<ApiResponse<PlatformAccess>>('/api/v1/platform/me')
    if (!response.data.data) throw new Error(response.data.error_message ?? 'Unable to load platform access')
    return response.data.data
  },
  async admins(search = '', page = 0) {
    const r = await apiClient.get<ApiResponse<Page<PlatformAdmin>>>('/api/v1/platform/admins', { params: { search, page, size: 20 } })
    if (!r.data.data) throw new Error(r.data.error_message ?? 'Unable to load admins')
    return r.data.data
  },
  async createAdmin(email: string, permissions: PlatformPermission[]) {
    const r = await apiClient.post<ApiResponse<PlatformAdmin>>('/api/v1/platform/admins', { email, permissions })
    if (!r.data.data) throw new Error(r.data.error_message ?? 'Unable to create admin')
    return r.data.data
  },
  async companies(search = '', page = 0) {
    const r = await apiClient.get<ApiResponse<Page<PlatformCompany>>>('/api/v1/platform/companies', { params: { search, page, size: 20 } })
    if (!r.data.data) throw new Error(r.data.error_message ?? 'Unable to load companies')
    return r.data.data
  },
  async updateCompanyStatus(id: string, status: PlatformCompany['status']) {
    const r = await apiClient.patch<ApiResponse<PlatformCompany>>(`/api/v1/platform/companies/${id}/status`, { status })
    if (!r.data.data) throw new Error(r.data.error_message ?? 'Unable to update company')
    return r.data.data
  },
}
