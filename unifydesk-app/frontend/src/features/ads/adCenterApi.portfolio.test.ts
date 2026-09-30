import { beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from '../../lib/api/apiClient'
import { adCenterApi } from './adCenterApi'

vi.mock('../../lib/api/apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

const ok = <T,>(data: T) => ({
  data: {
    status: true,
    status_code: 200,
    data,
  },
})

describe('adCenterApi portfolio bidding', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('accepts a successful null-data portfolio alignment response', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(ok(null) as never)

    await expect(
      adCenterApi.alignGooglePortfolioBudget('10', '20', '77', '88'),
    ).resolves.toBeUndefined()

    expect(apiClient.post).toHaveBeenCalledWith(
      '/api/v1/companies/10/ads/google/accounts/20/portfolio-alignment',
      { biddingStrategyId: '77', sharedBudgetId: '88' },
    )
  })

  it('sends both selected resources when updating a campaign assignment', async () => {
    vi.mocked(apiClient.put).mockResolvedValue(
      ok({
        campaignId: '404',
        biddingStrategyResourceName: 'customers/123/biddingStrategies/77',
        campaignBudgetResourceName: 'customers/123/campaignBudgets/88',
        biddingStrategyType: 'MAXIMIZE_CONVERSIONS',
        portfolioCompatible: true,
      }) as never,
    )

    await adCenterApi.updateGoogleCampaignPortfolioAssignment('10', '30', '77', '88')

    expect(apiClient.put).toHaveBeenCalledWith(
      '/api/v1/companies/10/ads/google/campaigns/30/portfolio-assignment',
      { biddingStrategyId: '77', sharedBudgetId: '88' },
    )
  })
})
