import { act, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { billingApi } from '../../features/billing/billingApi'
import {
  readPendingBillingCheckout,
  savePendingBillingCheckout,
} from '../../features/billing/pendingCheckout'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { useAppSelector } from '../../store/hooks'
import PaymentReturnPage from './PaymentReturnPage'

vi.mock('../../features/billing/billingApi', () => ({ billingApi: { companyPlan: vi.fn() } }))
vi.mock('../../features/workspace/useWorkspaceEpochScope', () => ({ useWorkspaceEpochScope: vi.fn() }))
vi.mock('../../store/hooks', () => ({ useAppSelector: vi.fn() }))

let companyId = 'company-a'
let epoch = 1
let scopeCurrent = true
const active = (planId: string, subscriptionId = 'sub-1') => ({
  plan: { planId, code: 'PLAN', name: 'Plan', status: 'PUBLISHED', visibility: 'PUBLIC', billingCycle: 'MONTHLY', basePrice: 1000, currency: 'INR', version: 1, prices: [], entitlements: [] },
  subscription: { subscriptionId, companyId, planId, planCode: 'PLAN', planName: 'Plan', status: 'ACTIVE', startedAt: '2020-01-01T00:00:00Z', cancelAtPeriodEnd: false, paymentPolicy: 'CASHFREE' },
}) as const
const saveInitial = (overrides = {}) => savePendingBillingCheckout({
  companyId: 'company-a', targetPlanId: 'growth', kind: 'INITIAL', paymentId: 'pay-1',
  invoiceId: 'inv-1', previousSubscriptionId: null, createdAt: '2030-01-01T00:00:00Z', ...overrides,
})

describe('PaymentReturnPage', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    window.sessionStorage.clear()
    companyId = 'company-a'
    epoch = 1
    scopeCurrent = true
    vi.mocked(useAppSelector).mockImplementation((selector) => selector({ workspace: { selectedCompanyId: companyId, epoch } } as never))
    vi.mocked(useWorkspaceEpochScope).mockImplementation(() => ({
      capture: () => ({ companyId, epoch }),
      isCurrent: () => scopeCurrent,
    }))
  })
  afterEach(() => vi.useRealTimers())

  it('does not treat paymentSuccess query state as authoritative', async () => {
    vi.mocked(billingApi.companyPlan).mockResolvedValue(active('starter') as never)
    render(<MemoryRouter initialEntries={['/billing/payment-return?paymentSuccess=true']}><PaymentReturnPage /></MemoryRouter>)
    await act(async () => { await vi.runAllTimersAsync() })
    expect(screen.queryByText('Subscription activated')).not.toBeInTheDocument()
    expect(screen.getByText('Billing activation is being verified')).toBeInTheDocument()
  })

  it('confirms valid INITIAL activation from the no-subscription baseline without clock comparison and clears pending state', async () => {
    saveInitial()
    vi.mocked(billingApi.companyPlan).mockResolvedValue(active('growth') as never)
    render(<MemoryRouter><PaymentReturnPage /></MemoryRouter>)
    await act(async () => { await Promise.resolve() })
    expect(await screen.findByText('Subscription activated')).toBeInTheDocument()
    expect(readPendingBillingCheckout()).toBeNull()
  })

  it('does not confirm a stale pre-existing target subscription baseline', async () => {
    saveInitial({ previousSubscriptionId: 'existing-sub' })
    vi.mocked(billingApi.companyPlan).mockResolvedValue(active('growth', 'existing-sub') as never)
    render(<MemoryRouter><PaymentReturnPage /></MemoryRouter>)
    await act(async () => { await vi.runAllTimersAsync() })
    expect(screen.queryByText('Subscription activated')).not.toBeInTheDocument()
  })

  it('does not confirm wrong company or wrong target plan', async () => {
    saveInitial()
    companyId = 'company-b'
    vi.mocked(billingApi.companyPlan).mockResolvedValue(active('growth') as never)
    const view = render(<MemoryRouter><PaymentReturnPage /></MemoryRouter>)
    await act(async () => { await vi.runAllTimersAsync() })
    expect(screen.queryByText('Subscription activated')).not.toBeInTheDocument()
    view.unmount()

    companyId = 'company-a'
    vi.mocked(billingApi.companyPlan).mockResolvedValue(active('starter') as never)
    render(<MemoryRouter><PaymentReturnPage /></MemoryRouter>)
    await act(async () => { await vi.runAllTimersAsync() })
    expect(screen.queryByText('Subscription activated')).not.toBeInTheDocument()
  })

  it('keeps UPGRADE return conservative even when plan state matches', async () => {
    saveInitial({ kind: 'UPGRADE', previousSubscriptionId: 'starter-sub' })
    vi.mocked(billingApi.companyPlan).mockResolvedValue(active('growth') as never)
    render(<MemoryRouter><PaymentReturnPage /></MemoryRouter>)
    await act(async () => { await vi.runAllTimersAsync() })
    expect(screen.queryByText('Subscription activated')).not.toBeInTheDocument()
  })

  it('workspace epoch invalidation prevents a stale async response from confirming', async () => {
    saveInitial()
    let resolve!: (value: ReturnType<typeof active>) => void
    vi.mocked(billingApi.companyPlan).mockReturnValue(new Promise((r) => { resolve = r }) as never)
    render(<MemoryRouter><PaymentReturnPage /></MemoryRouter>)
    scopeCurrent = false
    await act(async () => { resolve(active('growth')); await Promise.resolve() })
    expect(screen.queryByText('Subscription activated')).not.toBeInTheDocument()
    expect(readPendingBillingCheckout()).not.toBeNull()
  })
})
