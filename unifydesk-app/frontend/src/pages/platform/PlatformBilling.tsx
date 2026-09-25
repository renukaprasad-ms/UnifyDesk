import { useEffect, useMemo, useState } from 'react'
import { FiCreditCard, FiPlus, FiRefreshCw, FiSliders } from 'react-icons/fi'
import { billingApi } from '../../features/billing/billingApi'
import type { CompanyPlanDetails } from '../../features/billing/types'
import type { Company } from '../../features/company/types'
import { platformApi } from '../../features/platform/platformApi'
import { adminBillingApi } from '../../features/platformBilling/adminBillingApi'
import type {
  BillingCategory,
  BillingPlan,
  ChannelType,
  CompanyRateOverride,
  CommunicationDirection,
  CreateCompanyRateOverrideInput,
  ProviderType,
  UsageUnit,
} from '../../features/platformBilling/types'
import { workspaceApi } from '../../features/workspace/workspaceApi'

const providersByChannel: Record<ChannelType, ProviderType[]> = {
  EMAIL: ['GMAIL', 'MICROSOFT', 'SMTP'],
  WHATSAPP: ['META_WHATSAPP'],
  SMS: ['MSG91'],
}

const categoriesByChannel: Record<ChannelType, BillingCategory[]> = {
  EMAIL: ['EMAIL_OUTBOUND', 'EMAIL_INBOUND'],
  WHATSAPP: [
    'WHATSAPP_MARKETING',
    'WHATSAPP_UTILITY',
    'WHATSAPP_AUTHENTICATION',
    'WHATSAPP_SERVICE',
  ],
  SMS: ['SMS_PROMOTIONAL', 'SMS_TRANSACTIONAL', 'SMS_OTP'],
}

const blankOverride = (): CreateCompanyRateOverrideInput => ({
  channel: 'SMS',
  provider: 'MSG91',
  billingCategory: 'SMS_TRANSACTIONAL',
  direction: 'OUTBOUND',
  unit: 'MESSAGE',
  price: 0,
  currency: 'INR',
  reason: '',
})

export default function PlatformBilling() {
  const [companies, setCompanies] = useState<Company[]>([])
  const [plans, setPlans] = useState<BillingPlan[]>([])
  const [companyId, setCompanyId] = useState('')
  const [details, setDetails] = useState<CompanyPlanDetails | null>(null)
  const [overrides, setOverrides] = useState<CompanyRateOverride[]>([])
  const [selectedPlanId, setSelectedPlanId] = useState('')
  const [selectedPlanPriceId, setSelectedPlanPriceId] = useState('')
  const [overrideForm, setOverrideForm] = useState<CreateCompanyRateOverrideInput>(blankOverride)
  const [showOverrideForm, setShowOverrideForm] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [superAdmin, setSuperAdmin] = useState<boolean | null>(null)

  useEffect(() => {
    let active = true

    const load = async () => {
      setLoading(true)
      setError('')
      try {
        const access = await platformApi.me()
        if (!active) return
        const allowed = access.role === 'SUPER_ADMIN'
        setSuperAdmin(allowed)
        if (!allowed) return

        const [visibleCompanies, availablePlans] = await Promise.all([
          workspaceApi.adminVisible(),
          adminBillingApi.plans(),
        ])
        if (!active) return
        setCompanies(visibleCompanies)
        setPlans(availablePlans.filter((plan) => plan.status === 'PUBLISHED'))
        if (visibleCompanies[0]) setCompanyId(visibleCompanies[0].companyId)
      } catch (cause) {
        if (active)
          setError(cause instanceof Error ? cause.message : 'Unable to load billing operations')
      } finally {
        if (active) setLoading(false)
      }
    }

    void load()
    return () => {
      active = false
    }
  }, [])

  const loadCompany = async (id: string) => {
    if (!id) return
    setError('')
    const [overrideResult, planResult] = await Promise.allSettled([
      adminBillingApi.companyRateOverrides(id),
      billingApi.companyPlan(id),
    ])
    if (overrideResult.status === 'fulfilled') setOverrides(overrideResult.value)
    else
      setError(
        overrideResult.reason instanceof Error
          ? overrideResult.reason.message
          : 'Unable to load rate overrides',
      )
    if (planResult.status === 'fulfilled') {
      const nextDetails = planResult.value
      setDetails(nextDetails)
      if (nextDetails.plan && nextDetails.subscription) {
        setSelectedPlanId(nextDetails.plan.planId)
        setSelectedPlanPriceId(nextDetails.subscription.planPriceId ?? '')
      } else {
        setSelectedPlanId('')
        setSelectedPlanPriceId('')
      }
    } else {
      setDetails(null)
      setSelectedPlanId('')
      setSelectedPlanPriceId('')
    }
  }

  useEffect(() => {
    if (companyId && superAdmin) void loadCompany(companyId)
  }, [companyId, superAdmin])

  const selectedPlan = useMemo(
    () => plans.find((plan) => plan.planId === selectedPlanId) ?? null,
    [plans, selectedPlanId],
  )

  const assignPlan = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!companyId || !selectedPlanId) return
    setSaving(true)
    setError('')
    try {
      await adminBillingApi.assignCompanySubscription(companyId, {
        planId: selectedPlanId,
        planPriceId: selectedPlanPriceId || undefined,
        status: 'ACTIVE',
      })
      await loadCompany(companyId)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to assign subscription')
    } finally {
      setSaving(false)
    }
  }

  const createOverride = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!companyId) return
    setSaving(true)
    setError('')
    try {
      await adminBillingApi.createCompanyRateOverride(companyId, {
        ...overrideForm,
        country: overrideForm.country?.trim().toUpperCase() || undefined,
        carrier: overrideForm.carrier?.trim() || undefined,
        reason: overrideForm.reason?.trim() || undefined,
        currency: overrideForm.currency.toUpperCase(),
      })
      setOverrideForm(blankOverride())
      setShowOverrideForm(false)
      await loadCompany(companyId)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to create rate override')
    } finally {
      setSaving(false)
    }
  }

  const changeOverrideChannel = (channel: ChannelType) => {
    setOverrideForm((current) => ({
      ...current,
      channel,
      provider: providersByChannel[channel][0],
      billingCategory: categoriesByChannel[channel][0],
    }))
  }

  if (loading)
    return (
      <div className="p-4 sm:p-6 lg:p-8 text-sm font-medium text-slate-500">
        Loading billing operations…
      </div>
    )
  if (superAdmin === false)
    return (
      <section className="p-4 sm:p-6 lg:p-8">
        <div className="panel max-w-2xl p-6">
          <p className="eyebrow">BILLING</p>
          <h1 className="mt-2 text-xl font-bold">Super Admin access required</h1>
          <p className="mt-2 text-sm text-slate-500">
            Global subscriptions and negotiated rate overrides are restricted to Super Admins.
          </p>
        </div>
      </section>
    )

  const currentPlan = details?.plan ?? null
  const currentSubscription = details?.subscription ?? null

  return (
    <section className="p-4 sm:p-6 lg:p-8">
      <div className="mx-auto max-w-7xl">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p className="eyebrow">ADMIN BILLING</p>
            <h1 className="mt-2 text-2xl font-bold text-slate-950">Company billing operations</h1>
            <p className="mt-1 max-w-2xl text-sm text-slate-500">
              Assign subscriptions and maintain negotiated company-specific communication rates
              without changing the public plan catalog.
            </p>
          </div>
          <label className="w-full text-xs font-semibold text-slate-600 sm:w-auto sm:min-w-[280px]">
            Company
            <select
              className="form-input mt-1.5"
              value={companyId}
              onChange={(e) => setCompanyId(e.target.value)}
            >
              <option value="">Select company</option>
              {companies.map((company) => (
                <option key={company.companyId} value={company.companyId}>
                  {company.name}
                </option>
              ))}
            </select>
          </label>
        </div>

        {error && (
          <div className="mt-5 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
            {error}
          </div>
        )}

        {!companyId ? (
          <div className="panel mt-6 p-10 text-center text-sm text-slate-500">
            Choose a company to manage its subscription and negotiated rates.
          </div>
        ) : (
          <>
            <div className="mt-6 grid gap-6 xl:grid-cols-2">
              <article className="panel p-5">
                <div className="flex items-start justify-between gap-4">
                  <div className="flex items-start gap-3">
                    <span className="security-icon">
                      <FiCreditCard />
                    </span>
                    <div>
                      <h2 className="text-sm font-bold text-slate-950">Current subscription</h2>
                      <p className="mt-1 text-xs text-slate-500">
                        The plan currently used for limits and default pricing.
                      </p>
                    </div>
                  </div>
                  <button
                    type="button"
                    className="secondary-button"
                    onClick={() => void loadCompany(companyId)}
                  >
                    <FiRefreshCw />
                  </button>
                </div>
                {currentPlan && currentSubscription ? (
                  <div className="mt-5 rounded-xl bg-slate-50 p-4">
                    <div className="flex flex-wrap items-center justify-between gap-3">
                      <div>
                        <p className="text-base font-bold text-slate-950">{currentPlan.name}</p>
                        <p className="mt-1 text-xs text-slate-500">
                          {currentPlan.code} · v{currentPlan.version} · {currentSubscription.status}
                        </p>
                      </div>
                      <span className="rounded-full bg-emerald-50 px-2.5 py-1 text-[10px] font-bold text-emerald-700">
                        {currentSubscription.cancelAtPeriodEnd ? 'CANCELS AT PERIOD END' : 'ACTIVE'}
                      </span>
                    </div>
                    <div className="mt-4 flex flex-wrap gap-3 text-xs text-slate-600">
                      <span>
                        {currentPlan.currency} {currentPlan.basePrice}
                      </span>
                      <span>
                        Started {new Date(currentSubscription.startedAt).toLocaleDateString()}
                      </span>
                      {currentSubscription.currentPeriodEnd && (
                        <span>
                          Renews{' '}
                          {new Date(currentSubscription.currentPeriodEnd).toLocaleDateString()}
                        </span>
                      )}
                    </div>
                    {currentSubscription.status !== 'CANCELLED' && (
                      <button
                        type="button"
                        className="mt-4 text-xs font-semibold text-rose-600"
                        disabled={saving}
                        onClick={async () => {
                          await adminBillingApi.cancelCompanySubscription(companyId, true)
                          await loadCompany(companyId)
                        }}
                      >
                        Cancel at period end
                      </button>
                    )}
                  </div>
                ) : (
                  <div className="mt-5 rounded-xl border border-dashed border-slate-200 bg-slate-50 p-5">
                    <p className="text-sm font-semibold text-slate-800">No plan assigned</p>
                    <p className="mt-1 text-xs text-slate-500">
                      This company has no active subscription. Choose a published plan and assign it
                      when ready.
                    </p>
                  </div>
                )}
              </article>

              <form onSubmit={assignPlan} className="panel p-5">
                <h2 className="text-sm font-bold text-slate-950">Assign or change plan</h2>
                <p className="mt-1 text-xs text-slate-500">
                  Only published plans are available here. Select a specific price record when
                  needed.
                </p>
                <div className="mt-5 grid gap-4 sm:grid-cols-2">
                  <label className="text-xs font-semibold text-slate-600">
                    Plan
                    <select
                      className="form-input mt-1.5"
                      value={selectedPlanId}
                      onChange={(e) => {
                        setSelectedPlanId(e.target.value)
                        setSelectedPlanPriceId('')
                      }}
                      required
                    >
                      <option value="">Select plan</option>
                      {plans.map((plan) => (
                        <option key={plan.planId} value={plan.planId}>
                          {plan.name} · v{plan.version}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="text-xs font-semibold text-slate-600">
                    Price
                    <select
                      className="form-input mt-1.5"
                      value={selectedPlanPriceId}
                      onChange={(e) => setSelectedPlanPriceId(e.target.value)}
                    >
                      <option value="">Use plan base price</option>
                      {selectedPlan?.prices
                        .filter((price) => price.status === 'ACTIVE')
                        .map((price) => (
                          <option key={price.planPriceId} value={price.planPriceId}>
                            {price.billingCycle} · {price.currency} {price.amount}
                          </option>
                        ))}
                    </select>
                  </label>
                </div>
                <button className="primary-button mt-5" disabled={saving || !selectedPlanId}>
                  {saving ? 'Saving…' : 'Assign subscription'}
                </button>
              </form>
            </div>

            <div className="panel mt-6 overflow-hidden">
              <div className="flex flex-col gap-3 border-b border-slate-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
                <div className="flex items-start gap-3">
                  <span className="security-icon">
                    <FiSliders />
                  </span>
                  <div>
                    <h2 className="text-sm font-bold text-slate-950">Negotiated rate overrides</h2>
                    <p className="mt-1 text-xs text-slate-500">
                      Company overrides take precedence over the selected plan rate card.
                    </p>
                  </div>
                </div>
                <button
                  type="button"
                  className="secondary-button gap-2"
                  onClick={() => setShowOverrideForm((open) => !open)}
                >
                  <FiPlus />
                  Add override
                </button>
              </div>

              {showOverrideForm && (
                <form
                  onSubmit={createOverride}
                  className="border-b border-slate-100 bg-slate-50/60 p-5"
                >
                  <div className="grid gap-4 md:grid-cols-3 xl:grid-cols-4">
                    <label className="text-xs font-semibold text-slate-600">
                      Channel
                      <select
                        className="form-input mt-1.5"
                        value={overrideForm.channel}
                        onChange={(e) => changeOverrideChannel(e.target.value as ChannelType)}
                      >
                        <option value="SMS">SMS</option>
                        <option value="WHATSAPP">WhatsApp</option>
                        <option value="EMAIL">Email</option>
                      </select>
                    </label>
                    <label className="text-xs font-semibold text-slate-600">
                      Provider
                      <select
                        className="form-input mt-1.5"
                        value={overrideForm.provider}
                        onChange={(e) =>
                          setOverrideForm({
                            ...overrideForm,
                            provider: e.target.value as ProviderType,
                          })
                        }
                      >
                        {providersByChannel[overrideForm.channel].map((provider) => (
                          <option key={provider}>{provider}</option>
                        ))}
                      </select>
                    </label>
                    <label className="text-xs font-semibold text-slate-600">
                      Category
                      <select
                        className="form-input mt-1.5"
                        value={overrideForm.billingCategory}
                        onChange={(e) =>
                          setOverrideForm({
                            ...overrideForm,
                            billingCategory: e.target.value as BillingCategory,
                          })
                        }
                      >
                        {categoriesByChannel[overrideForm.channel].map((category) => (
                          <option key={category}>{category}</option>
                        ))}
                      </select>
                    </label>
                    <label className="text-xs font-semibold text-slate-600">
                      Direction
                      <select
                        className="form-input mt-1.5"
                        value={overrideForm.direction}
                        onChange={(e) =>
                          setOverrideForm({
                            ...overrideForm,
                            direction: e.target.value as CommunicationDirection,
                          })
                        }
                      >
                        <option value="OUTBOUND">Outbound</option>
                        <option value="INBOUND">Inbound</option>
                      </select>
                    </label>
                    <label className="text-xs font-semibold text-slate-600">
                      Unit
                      <select
                        className="form-input mt-1.5"
                        value={overrideForm.unit}
                        onChange={(e) =>
                          setOverrideForm({ ...overrideForm, unit: e.target.value as UsageUnit })
                        }
                      >
                        <option value="MESSAGE">Message</option>
                        <option value="CONVERSATION">Conversation</option>
                      </select>
                    </label>
                    <label className="text-xs font-semibold text-slate-600">
                      Price
                      <input
                        className="form-input mt-1.5"
                        type="number"
                        min="0"
                        step="0.000001"
                        value={overrideForm.price}
                        onChange={(e) =>
                          setOverrideForm({ ...overrideForm, price: Number(e.target.value) })
                        }
                        required
                      />
                    </label>
                    <label className="text-xs font-semibold text-slate-600">
                      Currency
                      <input
                        className="form-input mt-1.5"
                        maxLength={3}
                        value={overrideForm.currency}
                        onChange={(e) =>
                          setOverrideForm({
                            ...overrideForm,
                            currency: e.target.value.toUpperCase(),
                          })
                        }
                        required
                      />
                    </label>
                    <label className="text-xs font-semibold text-slate-600">
                      Country
                      <input
                        className="form-input mt-1.5"
                        maxLength={2}
                        placeholder="IN"
                        value={overrideForm.country ?? ''}
                        onChange={(e) =>
                          setOverrideForm({ ...overrideForm, country: e.target.value })
                        }
                      />
                    </label>
                    <label className="text-xs font-semibold text-slate-600">
                      Carrier
                      <input
                        className="form-input mt-1.5"
                        value={overrideForm.carrier ?? ''}
                        onChange={(e) =>
                          setOverrideForm({ ...overrideForm, carrier: e.target.value })
                        }
                      />
                    </label>
                    <label className="text-xs font-semibold text-slate-600 md:col-span-2 xl:col-span-3">
                      Reason
                      <input
                        className="form-input mt-1.5"
                        maxLength={500}
                        placeholder="Contract, promotion or negotiated commercial term"
                        value={overrideForm.reason ?? ''}
                        onChange={(e) =>
                          setOverrideForm({ ...overrideForm, reason: e.target.value })
                        }
                      />
                    </label>
                  </div>
                  <div className="mt-4 flex justify-end">
                    <button className="primary-button" disabled={saving}>
                      Save override
                    </button>
                  </div>
                </form>
              )}

              {overrides.length === 0 ? (
                <p className="px-5 py-10 text-center text-sm text-slate-500">
                  No company-specific pricing overrides.
                </p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="mobile-card-table w-full min-w-[800px] text-left text-sm">
                    <thead className="bg-slate-50 text-[10px] uppercase tracking-wide text-slate-500">
                      <tr>
                        <th className="px-4 py-3">Route</th>
                        <th className="px-4 py-3">Category</th>
                        <th className="px-4 py-3">Price</th>
                        <th className="px-4 py-3">Reason</th>
                        <th className="px-4 py-3">Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {overrides.map((override) => (
                        <tr key={override.overrideId} className="border-t border-slate-100">
                          <td className="px-4 py-4" data-label="Route">
                            <p className="font-bold text-slate-900">
                              {override.channel} · {override.provider}
                            </p>
                            <p className="mt-1 text-xs text-slate-500">
                              {override.country || 'ALL'}
                              {override.carrier ? ` · ${override.carrier}` : ''} ·{' '}
                              {override.direction}
                            </p>
                          </td>
                          <td className="px-4 py-4 text-xs text-slate-600" data-label="Category">
                            {override.billingCategory}
                            <br />
                            per {override.unit.toLowerCase()}
                          </td>
                          <td className="px-4 py-4 font-bold text-slate-950" data-label="Price">
                            {override.currency} {override.price}
                          </td>
                          <td className="max-w-[260px] px-4 py-4 text-xs text-slate-500" data-label="Reason">
                            {override.reason || '—'}
                          </td>
                          <td className="px-4 py-4 text-xs font-semibold text-slate-600" data-label="Status">
                            {override.status}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </>
        )}
      </div>
    </section>
  )
}
