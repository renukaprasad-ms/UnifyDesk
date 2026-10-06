import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type {
  AcceptCompanyInvitationResponse,
  CompanyInvitation,
  CompanyUser,
  CreateCompanyInvitationPayload,
  Company,
  UpdateCompanyPayload,
} from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (!response.data) {
    throw new Error(response.error_message ?? 'Empty API response')
  }
  return response.data
}

export const companyApi = {
  async get(companyId: string) {
    const response = await apiClient.get<ApiResponse<Company>>(`/api/v1/companies/${companyId}`)
    return unwrap(response.data)
  },

  async update(companyId: string, payload: UpdateCompanyPayload) {
    const response = await apiClient.put<ApiResponse<Company>>(
      `/api/v1/companies/${companyId}`,
      payload,
    )
    return unwrap(response.data)
  },

  async delete(companyId: string) {
    await apiClient.delete(`/api/v1/companies/${companyId}`)
  },

  async updateMemberRole(companyId: string, userId: string, role: 'MANAGER' | 'MEMBER') {
    const response = await apiClient.patch<ApiResponse<CompanyUser>>(
      `/api/v1/companies/${companyId}/members/${userId}/role`,
      { role },
    )
    return unwrap(response.data)
  },

  async updateMemberStatus(companyId: string, userId: string, status: 'ACTIVE' | 'SUSPENDED') {
    const response = await apiClient.patch<ApiResponse<CompanyUser>>(
      `/api/v1/companies/${companyId}/members/${userId}/status`,
      { status },
    )
    return unwrap(response.data)
  },

  async removeMember(companyId: string, userId: string) {
    await apiClient.delete(`/api/v1/companies/${companyId}/members/${userId}`)
  },

  async leave(companyId: string) {
    await apiClient.post(`/api/v1/companies/${companyId}/members/me/leave`)
  },

  async transferOwnership(companyId: string, targetUserId: string) {
    await apiClient.post(`/api/v1/companies/${companyId}/ownership-transfer`, { targetUserId })
  },

  async users(companyId: string) {
    const response = await apiClient.get<ApiResponse<CompanyUser[]>>(
      `/api/v1/companies/${companyId}/users`,
    )
    return unwrap(response.data)
  },

  async invitations(companyId: string) {
    const response = await apiClient.get<ApiResponse<CompanyInvitation[]>>(
      `/api/v1/companies/${companyId}/invitations`,
    )
    return unwrap(response.data)
  },

  async myInvitations() {
    const response = await apiClient.get<ApiResponse<CompanyInvitation[]>>('/api/v1/invitations/me')
    return unwrap(response.data)
  },

  async acceptMyInvitation(invitationId: string) {
    const response = await apiClient.post<ApiResponse<AcceptCompanyInvitationResponse>>(
      `/api/v1/invitations/me/${invitationId}/accept`,
    )
    return unwrap(response.data)
  },

  async invite(companyId: string, payload: CreateCompanyInvitationPayload) {
    const response = await apiClient.post<ApiResponse<CompanyInvitation>>(
      `/api/v1/companies/${companyId}/invitations`,
      payload,
    )
    return unwrap(response.data)
  },

  async resendInvitation(companyId: string, invitationId: string) {
    const response = await apiClient.post<ApiResponse<CompanyInvitation>>(
      `/api/v1/companies/${companyId}/invitations/${invitationId}/resend`,
    )
    return unwrap(response.data)
  },

  async revokeInvitation(companyId: string, invitationId: string) {
    await apiClient.delete(`/api/v1/companies/${companyId}/invitations/${invitationId}`)
  },

  async acceptInvitation(token: string) {
    const response = await apiClient.post<ApiResponse<AcceptCompanyInvitationResponse>>(
      `/api/v1/invitations/${encodeURIComponent(token)}/accept`,
    )
    return unwrap(response.data)
  },
}
