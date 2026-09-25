import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import type { AsyncJob } from './types'

export const asyncJobApi = {
  async get(jobId: string) {
    const response = await apiClient.get<ApiResponse<AsyncJob>>(`/api/v1/jobs/${jobId}`)
    if (!response.data.data) {
      throw new Error(response.data.error_message ?? 'Async job not found')
    }
    return response.data.data
  },
}
