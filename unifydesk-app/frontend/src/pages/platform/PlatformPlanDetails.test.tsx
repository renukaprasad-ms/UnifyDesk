import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { adminBillingApi } from '../../features/platformBilling/adminBillingApi'
import type {
  BillingPlan,
  EntitlementDefinition,
  PlanPrice,
  RateCard,
} from '../../features/platformBilling/types'
import PlatformPlanDetails from './PlatformPlanDetails'

vi.mock('../../features/platformBilling/adminBillingApi', () => ({
  adminBillingApi: {
    plans: vi.fn(),
    entitlementDefinitions: vi.fn(),
    planPrices: vi.fn(),
    rateCards: vi.fn(),
    updatePlan: vi.fn(),
    updatePlanPrice: vi.fn(),
    createPlanPrice: vi.fn(),
    updateRateCard: vi.fn(),
    createRateCard: vi.fn(),
    saveEntitlement: vi.fn(),
    deleteEntitlement: vi.fn(),
  },
}))

const price: PlanPrice = {
  planPriceId: 'price-1',
  planId: 'plan-1',
  billingCycle: 'MONTHLY',
  amount: 999,
  currency: 'INR',
  effectiveFrom: '2026-10-03T10:00:00.000Z',
  effectiveTo: '2026-11-03T10:00:00.000Z',
  status: 'ACTIVE',
}

const rate: RateCard = {
  rateCardId: 'rate-1',
  planId: 'plan-1',
  channel: 'SMS',
  provider: 'MSG91_SMS',
  country: 'IN',
  carrier: 'Airtel',
  billingCategory: 'SMS_TRANSACTIONAL',
  direction: 'OUTBOUND',
  unit: 'SEGMENT',
  price: 0.5,
  currency: 'INR',
  effectiveFrom: '2026-10-03T10:00:00.000Z',
  effectiveTo: null,
  status: 'ACTIVE',
}

const definition: EntitlementDefinition = {
  entitlementDefinitionId: 'definition-1',
  key: 'broadcast.limit',
  name: 'Broadcast limit',
  description: 'Monthly broadcast limit',
  valueType: 'INTEGER',
  category: 'BROADCAST',
  defaultValue: '100',
  active: true,
}

const plan = (status: BillingPlan['status']): BillingPlan => ({
  planId: 'plan-1',
  code: 'GROWTH',
  name: 'Growth',
  description: 'Growth plan',
  status,
  visibility: 'PUBLIC',
  billingCycle: 'MONTHLY',
  basePrice: 999,
  currency: 'INR',
  version: 1,
  prices: [price],
  entitlements: [{ entitlementId: 'entitlement-1', key: definition.key, value: '500' }],
})

const renderPlan = async (status: BillingPlan['status'] = 'DRAFT') => {
  vi.mocked(adminBillingApi.plans).mockResolvedValue([plan(status)])
  vi.mocked(adminBillingApi.entitlementDefinitions).mockResolvedValue([definition])
  vi.mocked(adminBillingApi.planPrices).mockResolvedValue([price])
  vi.mocked(adminBillingApi.rateCards).mockResolvedValue([rate])
  vi.mocked(adminBillingApi.updatePlanPrice).mockResolvedValue(price)
  vi.mocked(adminBillingApi.updateRateCard).mockResolvedValue(rate)
  vi.mocked(adminBillingApi.deleteEntitlement).mockResolvedValue(undefined)

  render(
    <MemoryRouter initialEntries={['/platform/plans/plan-1']}>
      <Routes>
        <Route path="/platform/plans/:planId" element={<PlatformPlanDetails />} />
      </Routes>
    </MemoryRouter>,
  )
  await screen.findByText('Growth plan')
}

const openTab = (name: string) => fireEvent.click(screen.getByRole('button', { name }))

describe('PlatformPlanDetails Billing Admin regressions', () => {
  beforeEach(() => vi.clearAllMocks())

  it('updates an existing DRAFT PlanPrice through the component flow', async () => {
    await renderPlan()
    openTab('Subscription pricing')
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }))

    const form = screen.getByRole('heading', { name: 'Edit subscription price' }).closest('form')!
    const amount = within(form).getByRole('spinbutton')
    fireEvent.change(amount, { target: { value: '1099' } })
    fireEvent.click(within(form).getByRole('button', { name: 'Update price' }))

    await waitFor(() => expect(adminBillingApi.updatePlanPrice).toHaveBeenCalledTimes(1))
    expect(adminBillingApi.updatePlanPrice).toHaveBeenCalledWith('price-1', {
      amount: 1099,
      currency: 'INR',
      effectiveFrom: '2026-10-03T10:00:00.000Z',
      effectiveTo: '2026-11-03T10:00:00.000Z',
      status: 'ACTIVE',
    })
  })

  it('keeps PUBLISHED PlanPrice read-only', async () => {
    await renderPlan('PUBLISHED')
    openTab('Subscription pricing')
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Add price|Update price/ })).not.toBeInTheDocument()
    expect(adminBillingApi.updatePlanPrice).not.toHaveBeenCalled()
  })

  it('updates an existing DRAFT RateCard through the component flow', async () => {
    await renderPlan()
    openTab('Managed-Service pricing')
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }))

    const form = screen.getByRole('heading', { name: 'Edit Managed-Service rate' }).closest('form')!
    const priceInput = within(form).getByRole('spinbutton')
    fireEvent.change(priceInput, { target: { value: '0.75' } })
    fireEvent.click(within(form).getByRole('button', { name: 'Update rate' }))

    await waitFor(() => expect(adminBillingApi.updateRateCard).toHaveBeenCalledTimes(1))
    expect(adminBillingApi.updateRateCard).toHaveBeenCalledWith('rate-1', {
      price: 0.75,
      effectiveFrom: '2026-10-03T10:00:00.000Z',
      effectiveTo: null,
      status: 'ACTIVE',
    })
  })

  it('keeps PUBLISHED RateCards read-only', async () => {
    await renderPlan('PUBLISHED')
    openTab('Managed-Service pricing')
    expect(screen.getByText(/SMS · MSG91_SMS/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Add rate|Update rate/ })).not.toBeInTheDocument()
    expect(adminBillingApi.updateRateCard).not.toHaveBeenCalled()
  })

  it('removes the exact assigned entitlement on a DRAFT plan and reloads authoritative data', async () => {
    await renderPlan()
    openTab('Limits & features')
    const callsBeforeRemove = vi.mocked(adminBillingApi.plans).mock.calls.length
    fireEvent.click(screen.getByRole('button', { name: /Remove/ }))

    await waitFor(() => expect(adminBillingApi.deleteEntitlement).toHaveBeenCalledWith('entitlement-1'))
    await waitFor(() => expect(adminBillingApi.plans).toHaveBeenCalledTimes(callsBeforeRemove + 1))
  })

  it('does not expose entitlement removal for a PUBLISHED plan', async () => {
    await renderPlan('PUBLISHED')
    openTab('Limits & features')
    expect(screen.getByText('Broadcast limit')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Remove/ })).not.toBeInTheDocument()
    expect(adminBillingApi.deleteEntitlement).not.toHaveBeenCalled()
  })

  it('exposes CLICK and IMPRESSION usage units in the actual DRAFT rate form', async () => {
    await renderPlan()
    openTab('Managed-Service pricing')
    const form = screen.getByRole('heading', { name: 'Add Managed-Service rate' }).closest('form')!
    expect(within(form).getByRole('option', { name: 'CLICK' })).toBeInTheDocument()
    expect(within(form).getByRole('option', { name: 'IMPRESSION' })).toBeInTheDocument()
  })

  it('defaults the new SMS Managed-Service rate to SEGMENT after channel switching', async () => {
    await renderPlan()
    openTab('Managed-Service pricing')
    const form = screen.getByRole('heading', { name: 'Add Managed-Service rate' }).closest('form')!
    const selects = within(form).getAllByRole('combobox')
    const channel = selects[0]
    const unit = selects[4] as HTMLSelectElement

    fireEvent.change(channel, { target: { value: 'EMAIL' } })
    expect(unit.value).toBe('MESSAGE')
    fireEvent.change(channel, { target: { value: 'SMS' } })
    expect(unit.value).toBe('SEGMENT')
  })
})
