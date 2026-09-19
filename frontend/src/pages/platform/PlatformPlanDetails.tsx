import { useEffect, useMemo, useState } from 'react'
import { FiArrowLeft, FiArchive, FiCheck, FiPlus, FiSave } from 'react-icons/fi'
import { useNavigate, useParams } from 'react-router'
import { adminBillingApi } from '../../features/platformBilling/adminBillingApi'
import type {
  BillingCategory,
  BillingCycle,
  BillingPlan,
  ChannelType,
  CommunicationDirection,
  CreatePlanPriceInput,
  CreateRateCardInput,
  EntitlementDefinition,
  PlanPrice,
  ProviderType,
  RateCard,
  UsageUnit,
} from '../../features/platformBilling/types'

type Tab = 'overview' | 'pricing' | 'entitlements' | 'usage'

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

const blankPrice: CreatePlanPriceInput = { billingCycle: 'MONTHLY', amount: 0, currency: 'INR' }
const blankRate = (planId: string): CreateRateCardInput => ({
  planId,
  channel: 'SMS',
  provider: 'MSG91',
  billingCategory: 'SMS_TRANSACTIONAL',
  direction: 'OUTBOUND',
  unit: 'MESSAGE',
  price: 0,
  currency: 'INR',
})

const money = (amount: number, currency: string) => {
  try {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency,
      maximumFractionDigits: 6,
    }).format(amount)
  } catch {
    return `${currency} ${amount}`
  }
}

export default function PlatformPlanDetails() {
  const { planId = '' } = useParams()
  const navigate = useNavigate()
  const [plan, setPlan] = useState<BillingPlan | null>(null)
  const [definitions, setDefinitions] = useState<EntitlementDefinition[]>([])
  const [prices, setPrices] = useState<PlanPrice[]>([])
  const [rateCards, setRateCards] = useState<RateCard[]>([])
  const [tab, setTab] = useState<Tab>('overview')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [entitlementValues, setEntitlementValues] = useState<Record<string, string>>({})
  const [priceForm, setPriceForm] = useState<CreatePlanPriceInput>(blankPrice)
  const [rateForm, setRateForm] = useState<CreateRateCardInput>(() => blankRate(planId))
  const [overview, setOverview] = useState({
    name: '',
    description: '',
    billingCycle: 'MONTHLY' as BillingCycle,
    basePrice: 0,
    currency: 'INR',
  })

  const load = async () => {
    if (!planId) return
    setLoading(true)
    setError('')
    try {
      const [plans, defs, loadedPrices, loadedRates] = await Promise.all([
        adminBillingApi.plans(),
        adminBillingApi.entitlementDefinitions(),
        adminBillingApi.planPrices(planId),
        adminBillingApi.rateCards(planId),
      ])
      const current = plans.find((item) => item.planId === planId)
      if (!current) throw new Error('Plan not found')
      setPlan(current)
      setDefinitions(defs.filter((definition) => definition.active))
      setPrices(loadedPrices)
      setRateCards(loadedRates)
      setOverview({
        name: current.name,
        description: current.description ?? '',
        billingCycle: current.billingCycle,
        basePrice: current.basePrice,
        currency: current.currency,
      })
      const values: Record<string, string> = {}
      defs.forEach((definition) => {
        values[definition.key] =
          current.entitlements.find((item) => item.key === definition.key)?.value ??
          definition.defaultValue
      })
      setEntitlementValues(values)
      setPriceForm((value) => ({ ...value, currency: current.currency }))
      setRateForm((value) => ({ ...value, planId, currency: current.currency }))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to load plan')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [planId])

  const entitlementsByCategory = useMemo(() => {
    return definitions.reduce<Record<string, EntitlementDefinition[]>>((groups, definition) => {
      ;(groups[definition.category] ??= []).push(definition)
      return groups
    }, {})
  }, [definitions])

  const saveOverview = async (event: React.FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    try {
      await adminBillingApi.updatePlan(planId, {
        name: overview.name.trim(),
        description: overview.description.trim(),
        billingCycle: overview.billingCycle,
        basePrice: overview.basePrice,
        currency: overview.currency.toUpperCase(),
      })
      await load()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to update plan')
    } finally {
      setSaving(false)
    }
  }

  const setStatus = async (status: 'PUBLISHED' | 'RETIRED') => {
    setSaving(true)
    setError('')
    try {
      await adminBillingApi.updatePlan(planId, { status })
      await load()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to update plan status')
    } finally {
      setSaving(false)
    }
  }

  const addPrice = async (event: React.FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    try {
      await adminBillingApi.createPlanPrice(planId, {
        ...priceForm,
        currency: priceForm.currency.toUpperCase(),
      })
      setPriceForm({ ...blankPrice, currency: plan?.currency ?? 'INR' })
      await load()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to create price')
    } finally {
      setSaving(false)
    }
  }

  const saveEntitlement = async (definition: EntitlementDefinition) => {
    setSaving(true)
    setError('')
    try {
      await adminBillingApi.saveEntitlement(
        planId,
        definition.key,
        entitlementValues[definition.key] ?? definition.defaultValue,
      )
      await load()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to save entitlement')
    } finally {
      setSaving(false)
    }
  }

  const removeEntitlement = async (key: string) => {
    const current = plan?.entitlements.find((item) => item.key === key)
    if (!current) return
    setSaving(true)
    try {
      await adminBillingApi.deleteEntitlement(current.entitlementId)
      await load()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to remove entitlement')
    } finally {
      setSaving(false)
    }
  }

  const addRate = async (event: React.FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    try {
      await adminBillingApi.createRateCard({
        ...rateForm,
        country: rateForm.country?.trim().toUpperCase() || undefined,
        carrier: rateForm.carrier?.trim() || undefined,
        currency: rateForm.currency.toUpperCase(),
      })
      setRateForm(blankRate(planId))
      await load()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to create rate card')
    } finally {
      setSaving(false)
    }
  }

  const changeRateChannel = (channel: ChannelType) => {
    const provider = providersByChannel[channel][0]
    const billingCategory = categoriesByChannel[channel][0]
    setRateForm((current) => ({ ...current, channel, provider, billingCategory }))
  }

  if (loading)
    return (
      <div className="p-4 sm:p-6 lg:p-8 text-sm font-medium text-slate-500">
        Loading plan configuration…
      </div>
    )
  if (!plan)
    return (
      <div className="p-4 sm:p-6 lg:p-8 text-sm font-medium text-red-600">
        {error || 'Plan not found'}
      </div>
    )

  return (
    <section className="p-4 sm:p-6 lg:p-8">
      <div className="mx-auto max-w-7xl">
        <button
          type="button"
          className="mb-5 inline-flex items-center gap-2 text-xs font-semibold text-slate-500 hover:text-indigo-500"
          onClick={() => navigate('/platform/plans')}
        >
          <FiArrowLeft />
          Back to plans
        </button>
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <p className="eyebrow">
              {plan.code} · VERSION {plan.version}
            </p>
            <h1 className="mt-2 text-2xl font-bold text-slate-950">{plan.name}</h1>
            <p className="mt-1 max-w-2xl text-sm text-slate-500">
              {plan.description || 'No plan description yet.'}
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-full bg-slate-100 px-3 py-1.5 text-[10px] font-bold text-slate-600">
              {plan.status}
            </span>
            {plan.status === 'DRAFT' && (
              <button
                type="button"
                className="primary-button gap-2"
                disabled={saving}
                onClick={() => void setStatus('PUBLISHED')}
              >
                <FiCheck />
                Publish
              </button>
            )}
            {plan.status === 'PUBLISHED' && (
              <button
                type="button"
                className="secondary-button gap-2 text-rose-600"
                disabled={saving}
                onClick={() => void setStatus('RETIRED')}
              >
                <FiArchive />
                Retire
              </button>
            )}
          </div>
        </div>

        {error && (
          <div className="mt-5 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
            {error}
          </div>
        )}

        <div className="mt-7 flex overflow-x-auto border-b border-slate-200">
          {(
            [
              ['overview', 'Overview'],
              ['pricing', 'Subscription pricing'],
              ['entitlements', 'Limits & features'],
              ['usage', 'Channel pricing'],
            ] as const
          ).map(([key, label]) => (
            <button
              key={key}
              type="button"
              className={`settings-tab whitespace-nowrap ${tab === key ? 'is-active' : ''}`}
              onClick={() => setTab(key)}
            >
              {label}
            </button>
          ))}
        </div>

        {tab === 'overview' && (
          <form onSubmit={saveOverview} className="panel mt-6 p-5">
            <div className="grid gap-4 md:grid-cols-2">
              <label className="text-xs font-semibold text-slate-600">
                Name
                <input
                  className="form-input mt-1.5"
                  value={overview.name}
                  onChange={(e) => setOverview({ ...overview, name: e.target.value })}
                  required
                />
              </label>
              <label className="text-xs font-semibold text-slate-600">
                Default billing cycle
                <select
                  className="form-input mt-1.5"
                  value={overview.billingCycle}
                  onChange={(e) =>
                    setOverview({ ...overview, billingCycle: e.target.value as BillingCycle })
                  }
                >
                  <option value="FREE">Free</option>
                  <option value="MONTHLY">Monthly</option>
                  <option value="YEARLY">Yearly</option>
                </select>
              </label>
              <label className="text-xs font-semibold text-slate-600">
                Base price
                <input
                  className="form-input mt-1.5"
                  type="number"
                  min="0"
                  step="0.01"
                  value={overview.basePrice}
                  onChange={(e) => setOverview({ ...overview, basePrice: Number(e.target.value) })}
                />
              </label>
              <label className="text-xs font-semibold text-slate-600">
                Currency
                <input
                  className="form-input mt-1.5"
                  minLength={3}
                  maxLength={3}
                  value={overview.currency}
                  onChange={(e) =>
                    setOverview({ ...overview, currency: e.target.value.toUpperCase() })
                  }
                />
              </label>
              <label className="text-xs font-semibold text-slate-600 md:col-span-2">
                Description
                <textarea
                  className="form-input mt-1.5"
                  maxLength={500}
                  value={overview.description}
                  onChange={(e) => setOverview({ ...overview, description: e.target.value })}
                />
              </label>
            </div>
            <div className="mt-5 flex justify-end">
              <button className="primary-button gap-2" disabled={saving}>
                <FiSave />
                Save overview
              </button>
            </div>
          </form>
        )}

        {tab === 'pricing' && (
          <div className="mt-6 grid gap-6 xl:grid-cols-[360px_1fr]">
            <form onSubmit={addPrice} className="panel h-fit p-5">
              <h2 className="text-sm font-bold text-slate-950">Add subscription price</h2>
              <p className="mt-1 text-xs text-slate-500">
                Keep monthly and yearly prices as separate effective price records.
              </p>
              <div className="mt-5 space-y-4">
                <label className="block text-xs font-semibold text-slate-600">
                  Cycle
                  <select
                    className="form-input mt-1.5"
                    value={priceForm.billingCycle}
                    onChange={(e) =>
                      setPriceForm({ ...priceForm, billingCycle: e.target.value as BillingCycle })
                    }
                  >
                    <option value="FREE">Free</option>
                    <option value="MONTHLY">Monthly</option>
                    <option value="YEARLY">Yearly</option>
                  </select>
                </label>
                <label className="block text-xs font-semibold text-slate-600">
                  Amount
                  <input
                    className="form-input mt-1.5"
                    type="number"
                    min="0"
                    step="0.01"
                    value={priceForm.amount}
                    onChange={(e) => setPriceForm({ ...priceForm, amount: Number(e.target.value) })}
                    required
                  />
                </label>
                <label className="block text-xs font-semibold text-slate-600">
                  Currency
                  <input
                    className="form-input mt-1.5"
                    minLength={3}
                    maxLength={3}
                    value={priceForm.currency}
                    onChange={(e) =>
                      setPriceForm({ ...priceForm, currency: e.target.value.toUpperCase() })
                    }
                    required
                  />
                </label>
              </div>
              <button className="primary-button mt-5 w-full gap-2" disabled={saving}>
                <FiPlus />
                Add price
              </button>
            </form>
            <div className="panel overflow-hidden">
              <div className="border-b border-slate-100 px-5 py-4">
                <h2 className="text-sm font-bold text-slate-950">Effective prices</h2>
              </div>
              {prices.length === 0 ? (
                <p className="px-5 py-10 text-center text-sm text-slate-500">
                  No subscription prices configured.
                </p>
              ) : (
                prices.map((price) => (
                  <div
                    key={price.planPriceId}
                    className="flex flex-col gap-3 border-b border-slate-100 px-5 py-4 last:border-0 sm:flex-row sm:items-center sm:justify-between"
                  >
                    <div>
                      <p className="font-bold text-slate-950">{price.billingCycle}</p>
                      <p className="mt-1 text-xs text-slate-500">
                        {price.status} ·{' '}
                        {price.effectiveFrom
                          ? `from ${new Date(price.effectiveFrom).toLocaleDateString()}`
                          : 'effective immediately'}
                      </p>
                    </div>
                    <div className="flex items-center gap-3">
                      <span className="font-bold text-slate-900">
                        {money(price.amount, price.currency)}
                      </span>
                      {price.status === 'ACTIVE' && (
                        <button
                          type="button"
                          className="text-xs font-semibold text-rose-600"
                          onClick={async () => {
                            await adminBillingApi.updatePlanPrice(price.planPriceId, {
                              status: 'ARCHIVED',
                            })
                            await load()
                          }}
                        >
                          Archive
                        </button>
                      )}
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        )}

        {tab === 'entitlements' && (
          <div className="mt-6 space-y-6">
            {Object.entries(entitlementsByCategory).map(([category, items]) => (
              <div key={category} className="panel overflow-hidden">
                <div className="border-b border-slate-100 px-5 py-4">
                  <h2 className="text-sm font-bold text-slate-950">
                    {category.replaceAll('_', ' ')}
                  </h2>
                  <p className="mt-1 text-xs text-slate-500">
                    Plan limits and feature switches are sourced from the entitlement catalogue.
                  </p>
                </div>
                <div className="divide-y divide-slate-100">
                  {items.map((definition) => {
                    const assigned = plan.entitlements.some((item) => item.key === definition.key)
                    const value = entitlementValues[definition.key] ?? definition.defaultValue
                    return (
                      <div
                        key={definition.key}
                        className="grid gap-4 px-5 py-4 lg:grid-cols-[1fr_260px_auto] lg:items-center"
                      >
                        <div>
                          <p className="text-sm font-bold text-slate-900">{definition.name}</p>
                          <p className="mt-1 text-xs text-slate-500">
                            {definition.description || definition.key}
                          </p>
                          <p className="mt-1 text-[10px] font-mono text-slate-400">
                            {definition.key}
                          </p>
                        </div>
                        {definition.valueType === 'BOOLEAN' ? (
                          <select
                            className="form-input"
                            value={value}
                            onChange={(e) =>
                              setEntitlementValues({
                                ...entitlementValues,
                                [definition.key]: e.target.value,
                              })
                            }
                          >
                            <option value="true">Enabled</option>
                            <option value="false">Disabled</option>
                          </select>
                        ) : (
                          <input
                            className="form-input"
                            type={
                              definition.valueType === 'INTEGER' ||
                              definition.valueType === 'DECIMAL'
                                ? 'number'
                                : 'text'
                            }
                            step={definition.valueType === 'DECIMAL' ? '0.000001' : undefined}
                            value={value}
                            onChange={(e) =>
                              setEntitlementValues({
                                ...entitlementValues,
                                [definition.key]: e.target.value,
                              })
                            }
                          />
                        )}
                        <div className="flex gap-2">
                          <button
                            type="button"
                            className="secondary-button"
                            disabled={saving}
                            onClick={() => void saveEntitlement(definition)}
                          >
                            Save
                          </button>
                          {assigned && (
                            <button
                              type="button"
                              className="px-2 text-xs font-semibold text-rose-600"
                              disabled={saving}
                              onClick={() => void removeEntitlement(definition.key)}
                            >
                              Remove
                            </button>
                          )}
                        </div>
                      </div>
                    )
                  })}
                </div>
              </div>
            ))}
          </div>
        )}

        {tab === 'usage' && (
          <div className="mt-6 grid gap-6 xl:grid-cols-[380px_1fr]">
            <form onSubmit={addRate} className="panel h-fit p-5">
              <h2 className="text-sm font-bold text-slate-950">Add channel rate</h2>
              <p className="mt-1 text-xs text-slate-500">
                Configure what this plan charges for provider usage by channel, category and
                direction.
              </p>
              <div className="mt-5 grid gap-4 sm:grid-cols-2 xl:grid-cols-1">
                <label className="text-xs font-semibold text-slate-600">
                  Channel
                  <select
                    className="form-input mt-1.5"
                    value={rateForm.channel}
                    onChange={(e) => changeRateChannel(e.target.value as ChannelType)}
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
                    value={rateForm.provider}
                    onChange={(e) =>
                      setRateForm({ ...rateForm, provider: e.target.value as ProviderType })
                    }
                  >
                    {providersByChannel[rateForm.channel].map((provider) => (
                      <option key={provider} value={provider}>
                        {provider}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="text-xs font-semibold text-slate-600">
                  Category
                  <select
                    className="form-input mt-1.5"
                    value={rateForm.billingCategory}
                    onChange={(e) =>
                      setRateForm({
                        ...rateForm,
                        billingCategory: e.target.value as BillingCategory,
                      })
                    }
                  >
                    {categoriesByChannel[rateForm.channel].map((category) => (
                      <option key={category} value={category}>
                        {category}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="text-xs font-semibold text-slate-600">
                  Direction
                  <select
                    className="form-input mt-1.5"
                    value={rateForm.direction}
                    onChange={(e) =>
                      setRateForm({
                        ...rateForm,
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
                    value={rateForm.unit}
                    onChange={(e) =>
                      setRateForm({ ...rateForm, unit: e.target.value as UsageUnit })
                    }
                  >
                    <option value="MESSAGE">Message</option>
                    <option value="CONVERSATION">Conversation</option>
                    <option value="CLICK">Click</option>
                    <option value="IMPRESSION">Impression</option>
                  </select>
                </label>
                <label className="text-xs font-semibold text-slate-600">
                  Price
                  <input
                    className="form-input mt-1.5"
                    type="number"
                    min="0"
                    step="0.000001"
                    value={rateForm.price}
                    onChange={(e) => setRateForm({ ...rateForm, price: Number(e.target.value) })}
                    required
                  />
                </label>
                <label className="text-xs font-semibold text-slate-600">
                  Currency
                  <input
                    className="form-input mt-1.5"
                    minLength={3}
                    maxLength={3}
                    value={rateForm.currency}
                    onChange={(e) =>
                      setRateForm({ ...rateForm, currency: e.target.value.toUpperCase() })
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
                    value={rateForm.country ?? ''}
                    onChange={(e) => setRateForm({ ...rateForm, country: e.target.value })}
                  />
                </label>
                <label className="text-xs font-semibold text-slate-600">
                  Carrier
                  <input
                    className="form-input mt-1.5"
                    placeholder="Optional"
                    value={rateForm.carrier ?? ''}
                    onChange={(e) => setRateForm({ ...rateForm, carrier: e.target.value })}
                  />
                </label>
              </div>
              <button className="primary-button mt-5 w-full gap-2" disabled={saving}>
                <FiPlus />
                Add rate card
              </button>
            </form>
            <div className="panel overflow-hidden">
              <div className="border-b border-slate-100 px-5 py-4">
                <h2 className="text-sm font-bold text-slate-950">Channel rate cards</h2>
                <p className="mt-1 text-xs text-slate-500">
                  More specific country/carrier rates can coexist with generic provider rates.
                </p>
              </div>
              {rateCards.length === 0 ? (
                <p className="px-5 py-10 text-center text-sm text-slate-500">
                  No usage pricing configured.
                </p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[760px] text-left text-sm">
                    <thead className="bg-slate-50 text-[10px] uppercase tracking-wide text-slate-500">
                      <tr>
                        <th className="px-4 py-3">Route</th>
                        <th className="px-4 py-3">Category</th>
                        <th className="px-4 py-3">Price</th>
                        <th className="px-4 py-3">Status</th>
                        <th className="px-4 py-3"></th>
                      </tr>
                    </thead>
                    <tbody>
                      {rateCards.map((rate) => (
                        <tr key={rate.rateCardId} className="border-t border-slate-100">
                          <td className="px-4 py-4">
                            <p className="font-bold text-slate-900">
                              {rate.channel} · {rate.provider}
                            </p>
                            <p className="mt-1 text-xs text-slate-500">
                              {rate.country || 'ALL'}
                              {rate.carrier ? ` · ${rate.carrier}` : ''} · {rate.direction}
                            </p>
                          </td>
                          <td className="px-4 py-4 text-xs text-slate-600">
                            {rate.billingCategory}
                            <br />
                            per {rate.unit.toLowerCase()}
                          </td>
                          <td className="px-4 py-4 font-bold text-slate-900">
                            {money(rate.price, rate.currency)}
                          </td>
                          <td className="px-4 py-4 text-xs font-semibold text-slate-600">
                            {rate.status}
                          </td>
                          <td className="px-4 py-4 text-right">
                            {rate.status === 'ACTIVE' && (
                              <button
                                type="button"
                                className="text-xs font-semibold text-rose-600"
                                onClick={async () => {
                                  await adminBillingApi.updateRateCard(rate.rateCardId, {
                                    status: 'ARCHIVED',
                                  })
                                  await load()
                                }}
                              >
                                Archive
                              </button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </section>
  )
}
