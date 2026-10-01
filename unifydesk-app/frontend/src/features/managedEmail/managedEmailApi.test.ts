import { beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from '../../lib/api/apiClient'
import { managedEmailApi } from './managedEmailApi'

vi.mock('../../lib/api/apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}))

describe('managedEmailApi safe usage and deliverability endpoints', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads customer plan consumption from /managed-email/usage', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        status: true,
        status_code: 200,
        data: {
          periodStart: '2026-09-01T00:00:00Z',
          periodEndExclusive: '2026-10-01T00:00:00Z',
          accepted: 100,
          reserved: 5,
          adjustments: 0,
          billable: 105,
          limit: 1000,
          remaining: 895,
          limitReached: false,
        },
      },
    } as never)

    await managedEmailApi.usage('company-1')

    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/companies/company-1/managed-email/usage')
  })

  it('loads customer-safe health only from /managed-email/deliverability', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        status: true,
        status_code: 200,
        data: {
          status: 'HEALTHY',
          windowStart: '2026-09-19T00:00:00Z',
          windowEnd: '2026-09-26T00:00:00Z',
          accepted: 100,
          hardBounces: 1,
          complaints: 0,
          hardBounceRate: 0.01,
          complaintRate: 0,
          sendingRestricted: false,
          notice: null,
        },
      },
    } as never)

    await managedEmailApi.deliverability('company-1')

    expect(apiClient.get).toHaveBeenCalledWith(
      '/api/v1/companies/company-1/managed-email/deliverability',
    )
  })
})
