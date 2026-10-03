import apiClient, { type ApiRequestConfig } from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'

export const EMAIL_UNSUBSCRIBE_ENDPOINT = '/api/v1/public/recipient-actions/email/unsubscribe'

export const unsubscribeApi = {
  async unsubscribe(token: string) {
    await apiClient.post<ApiResponse<void>>(
      `${EMAIL_UNSUBSCRIBE_ENDPOINT}/${encodeURIComponent(token)}`,
      undefined,
      {
        _skipAuthRefresh: true,
        _skipErrorSnackbar: true,
      } satisfies ApiRequestConfig,
    )
  },
}
