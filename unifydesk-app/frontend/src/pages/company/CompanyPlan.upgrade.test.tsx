import { SnackbarProvider } from '../../components/feedback/SnackbarProviderComponent'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { billingApi } from '../../features/billing/billingApi'
import { launchRazorpayCheckout } from '../../features/billing/razorpayCheckout'
import { useIam } from '../../features/iam/useIam'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { useAppSelector } from '../../store/hooks'
import CompanyPlan from './CompanyPlan'

vi.mock('../../features/billing/billingApi', () => ({ billingApi:{ companyPlan:vi.fn(),eligiblePlans:vi.fn(),selectPlan:vi.fn(),requestUpgrade:vi.fn(),scheduleDowngrade:vi.fn(),cancelAtPeriodEnd:vi.fn(),initialCheckout:vi.fn(),upgradeCheckout:vi.fn(),verifyPayment:vi.fn(),setupAutoPay:vi.fn(),verifyAutoPay:vi.fn() } }))
vi.mock('../../features/billing/razorpayCheckout', () => ({ launchRazorpayCheckout:vi.fn(), launchRazorpayMandateCheckout:vi.fn() }))
vi.mock('../../features/iam/useIam', () => ({ useIam:vi.fn() }))
vi.mock('../../features/workspace/useWorkspaceEpochScope', () => ({ useWorkspaceEpochScope:vi.fn() }))
vi.mock('../../store/hooks', () => ({ useAppSelector:vi.fn() }))

const targetPrice = { planPriceId:'growth-price',planId:'growth',billingCycle:'MONTHLY' as const,amount:2000,currency:'INR',effectiveFrom:'2026-01-01T00:00:00Z',status:'ACTIVE' as const }
const targetPlan = { planId:'growth',code:'GROWTH',name:'Growth',status:'PUBLISHED' as const,visibility:'PUBLIC' as const,billingCycle:'MONTHLY' as const,basePrice:2000,currency:'INR',version:1,prices:[targetPrice],entitlements:[] }
const currentDetails = { plan:{planId:'starter',code:'STARTER',name:'Starter',status:'PUBLISHED' as const,visibility:'PUBLIC' as const,billingCycle:'MONTHLY' as const,basePrice:1000,currency:'INR',version:1,prices:[],entitlements:[]}, subscription:{subscriptionId:'sub',companyId:'company-a',planId:'starter',planCode:'STARTER',planName:'Starter',status:'ACTIVE' as const,startedAt:'2026-01-01T00:00:00Z',currentPeriodEnd:'2026-11-01T00:00:00Z',cancelAtPeriodEnd:false,paymentPolicy:'RAZORPAY' as const} }
const state = { workspace:{companies:[{companyId:'company-a',name:'Company A',slug:'a',status:'ACTIVE',role:'OWNER',businessEmail:'billing@example.com'}],selectedCompanyId:'company-a',epoch:1},iam:{owner:true,permissions:[],status:'ready',companyId:'company-a',error:null} }

describe('CompanyPlan upgrade authority', () => {
  beforeEach(() => { vi.clearAllMocks(); vi.mocked(useAppSelector).mockImplementation((selector) => selector(state as never)); vi.mocked(useIam).mockReturnValue({owner:true,permissions:[],status:'ready',companyId:'company-a',error:null,can:()=>true,canAny:()=>true,canAll:()=>true}); vi.mocked(useWorkspaceEpochScope).mockReturnValue({capture:()=>({companyId:'company-a',epoch:1}),isCurrent:()=>true}); vi.mocked(billingApi.companyPlan).mockResolvedValue(currentDetails); vi.mocked(billingApi.eligiblePlans).mockResolvedValue([targetPlan]) })
  const openAndSubmit = async () => { render(<SnackbarProvider>{<CompanyPlan />}</SnackbarProvider>); await screen.findByText('Growth'); fireEvent.click(screen.getByRole('button',{name:'Upgrade now'})); fireEvent.change(screen.getByPlaceholderText('Billing phone'),{target:{value:'9876543210'}}); fireEvent.click(screen.getByRole('button',{name:'Review upgrade amount'})) }
  it('APPLIED upgrade refreshes backend truth without checkout or Razorpay', async () => { vi.mocked(billingApi.requestUpgrade).mockResolvedValue({changeId:'c',companyId:'company-a',subscriptionId:'sub',fromPlanId:'starter',toPlanId:'growth',type:'UPGRADE',status:'APPLIED',requestedAt:'2026-10-03T00:00:00Z'}); await openAndSubmit(); expect(await screen.findByText(/Upgrade applied by backend/)).toBeInTheDocument(); expect(billingApi.upgradeCheckout).not.toHaveBeenCalled(); expect(launchRazorpayCheckout).not.toHaveBeenCalled(); expect(vi.mocked(billingApi.companyPlan).mock.calls.length).toBeGreaterThan(1) })
  it('PENDING_PAYMENT retains canonical backend checkout flow', async () => { vi.mocked(billingApi.requestUpgrade).mockResolvedValue({changeId:'c',companyId:'company-a',subscriptionId:'sub',fromPlanId:'starter',toPlanId:'growth',type:'UPGRADE',status:'PENDING_PAYMENT',requestedAt:'2026-10-03T00:00:00Z',quoteReference:'quote-1',amountDue:500,currency:'INR'}); vi.mocked(billingApi.upgradeCheckout).mockResolvedValue({paymentId:'p',invoiceId:'i',amount:500,currency:'INR',status:'CREATED'}); await openAndSubmit(); fireEvent.click(await screen.findByRole('button',{name:/Pay with Razorpay/})); await waitFor(() => expect(billingApi.upgradeCheckout).toHaveBeenCalledWith('company-a',expect.objectContaining({changeId:'c',quoteReference:'quote-1'}))); expect(launchRazorpayCheckout).not.toHaveBeenCalled() })
})
