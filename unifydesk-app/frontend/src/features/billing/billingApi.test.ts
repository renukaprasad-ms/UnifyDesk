import { beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from '../../lib/api/apiClient'
import { billingApi } from './billingApi'
vi.mock('../../lib/api/apiClient', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
const response = <T,>(data:T) => Promise.resolve({data:{data}})
describe('billingApi R1 contracts', () => {
 beforeEach(() => vi.clearAllMocks())
 it('loads the backend-authoritative company catalog', async () => { vi.mocked(apiClient.get).mockReturnValue(response([]) as never); await billingApi.eligiblePlans('company-a'); expect(apiClient.get).toHaveBeenCalledWith('/api/v1/companies/company-a/plans') })
 it('sends upgrade target and idempotency key to the canonical change endpoint', async () => { vi.mocked(apiClient.post).mockReturnValue(response({changeId:'1'}) as never); await billingApi.requestUpgrade('company-a',{targetPlanId:'growth',targetPlanPriceId:'price-growth',idempotencyKey:'upgrade:key'}); expect(apiClient.post).toHaveBeenCalledWith('/api/v1/companies/company-a/subscription/upgrade',{targetPlanId:'growth',targetPlanPriceId:'price-growth',idempotencyKey:'upgrade:key'}) })
 it('always requests customer cancellation with period-end semantics', async () => { vi.mocked(apiClient.post).mockReturnValue(response({subscriptionId:'1'}) as never); await billingApi.cancelAtPeriodEnd('company-a'); expect(apiClient.post).toHaveBeenCalledWith('/api/v1/companies/company-a/subscription/cancel',undefined,{params:{atPeriodEnd:true}}) })
 it('returns the backend checkout amount without deriving a frontend amount', async () => { vi.mocked(apiClient.post).mockReturnValue(response({paymentId:'pay-1',invoiceId:'inv-1',amount:1234.56,currency:'INR',status:'CREATED'}) as never); const checkout=await billingApi.initialCheckout('company-a',{planId:'growth',planPriceId:'price-growth',idempotencyKey:'initial:key',customer:{email:'billing@example.com',phone:'9876543210'}}); expect(checkout.amount).toBe(1234.56) })
})
