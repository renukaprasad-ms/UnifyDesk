import { act, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { billingApi } from '../../features/billing/billingApi'
import { savePendingBillingCheckout } from '../../features/billing/pendingCheckout'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { useAppSelector } from '../../store/hooks'
import PaymentReturnPage from './PaymentReturnPage'
vi.mock('../../features/billing/billingApi', () => ({ billingApi: { companyPlan: vi.fn() } }))
vi.mock('../../features/workspace/useWorkspaceEpochScope', () => ({ useWorkspaceEpochScope: vi.fn() }))
vi.mock('../../store/hooks', () => ({ useAppSelector: vi.fn() }))
const active=(planId:string)=>({plan:{planId,code:'PLAN',name:'Plan',status:'PUBLISHED',visibility:'PUBLIC',billingCycle:'MONTHLY',basePrice:1000,currency:'INR',version:1,prices:[],entitlements:[]},subscription:{subscriptionId:'sub-1',companyId:'company-a',planId,planCode:'PLAN',planName:'Plan',status:'ACTIVE',startedAt:'2026-10-03T00:00:00Z',cancelAtPeriodEnd:false,paymentPolicy:'CASHFREE'}}) as const
describe('PaymentReturnPage',()=>{beforeEach(()=>{vi.useFakeTimers();window.sessionStorage.clear();vi.mocked(useAppSelector).mockImplementation((selector)=>selector({workspace:{selectedCompanyId:'company-a',epoch:1}} as never));vi.mocked(useWorkspaceEpochScope).mockReturnValue({capture:()=>({companyId:'company-a',epoch:1}),isCurrent:()=>true})});afterEach(()=>vi.useRealTimers());it('does not treat paymentSuccess query state as authoritative',async()=>{vi.mocked(billingApi.companyPlan).mockResolvedValue(active('starter') as never);render(<MemoryRouter initialEntries={['/billing/payment-return?paymentSuccess=true']}><PaymentReturnPage /></MemoryRouter>);await act(async()=>{await vi.runAllTimersAsync()});expect(screen.queryByText('Payment confirmed')).not.toBeInTheDocument();expect(screen.getByText('Payment is being verified')).toBeInTheDocument()});it('renders confirmed only after authoritative backend state matches the pending target plan',async()=>{savePendingBillingCheckout({companyId:'company-a',targetPlanId:'growth',kind:'INITIAL',paymentId:'pay-1',invoiceId:'inv-1',createdAt:'2026-10-03T00:00:00Z'});vi.mocked(billingApi.companyPlan).mockResolvedValue(active('growth') as never);render(<MemoryRouter><PaymentReturnPage /></MemoryRouter>);await act(async()=>{await Promise.resolve()});expect(await screen.findByText('Payment confirmed')).toBeInTheDocument()})})
