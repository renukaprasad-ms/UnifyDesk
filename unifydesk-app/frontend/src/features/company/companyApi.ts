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
