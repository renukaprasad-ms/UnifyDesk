import { useEffect, useMemo, useState } from 'react'
import { FiArrowLeft, FiArchive, FiCheck, FiSave, FiTrash2 } from 'react-icons/fi'
import { useNavigate, useParams } from 'react-router'
import { adminBillingApi } from '../../features/platformBilling/adminBillingApi'
import {
  buildPlanPriceInput,
  buildRateCardInput,
  instantToLocalDateTime,
} from '../../features/platformBilling/billingAdminModel'
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
  PlanVisibility,
  ProviderType,
  RateCard,
  RateCardStatus,
  UsageUnit,
} from '../../features/platformBilling/types'
import { PageSkeleton } from '../../components/ui/Skeleton'

type Tab = 'overview' | 'pricing' | 'entitlements' | 'usage'

const providersByChannel: Record<ChannelType, ProviderType[]> = {
  EMAIL: ['AMAZON_SES', 'SENDGRID', 'AZURE_EMAIL', 'MSG91_EMAIL', 'GMAIL', 'MICROSOFT', 'IMAP_SMTP', 'SMTP'],
  WHATSAPP: ['META_WHATSAPP'],
  SMS: ['MSG91_SMS'],
}

const categoriesByChannel: Record<ChannelType, BillingCategory[]> = {
  EMAIL: ['EMAIL_OUTBOUND', 'EMAIL_INBOUND'],
  WHATSAPP: ['WHATSAPP_MARKETING', 'WHATSAPP_UTILITY', 'WHATSAPP_AUTHENTICATION', 'WHATSAPP_SERVICE'],
  SMS: ['SMS_PROMOTIONAL', 'SMS_TRANSACTIONAL', 'SMS_OTP'],
}

export const PLATFORM_RATE_USAGE_UNITS: UsageUnit[] = [
  'MESSAGE',
  'SEGMENT',
  'CONVERSATION',
  'CLICK',
  'IMPRESSION',
]

export const defaultUsageUnitForChannel = (channel: ChannelType): UsageUnit =>
  channel === 'SMS' ? 'SEGMENT' : 'MESSAGE'

const blankPrice = {
  billingCycle: 'MONTHLY' as BillingCycle,
  amount: 0,
  currency: 'INR',
}

const blankRate = (planId: string) => ({
  planId,
  channel: 'SMS' as ChannelType,
  provider: 'MSG91_SMS' as ProviderType,
  billingCategory: 'SMS_TRANSACTIONAL' as BillingCategory,
  direction: 'OUTBOUND' as CommunicationDirection,
  unit: 'SEGMENT' as UsageUnit,
  price: 0,
  currency: 'INR',
  country: '',
  carrier: '',
})

const money = (amount: number, currency: string) =>
  new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency,
    maximumFractionDigits: 6,
  }).format(amount)

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
  const [notice, setNotice] = useState('')
  const [priceForm, setPriceForm] = useState(blankPrice)
  const [priceFrom, setPriceFrom] = useState('')
  const [priceTo, setPriceTo] = useState('')
  const [editingPriceId, setEditingPriceId] = useState<string | null>(null)
  const [priceStatus, setPriceStatus] = useState<RateCardStatus>('ACTIVE')
  const [rateForm, setRateForm] = useState(() => blankRate(planId))
  const [rateFrom, setRateFrom] = useState('')
  const [rateTo, setRateTo] = useState('')
  const [editingRateId, setEditingRateId] = useState<string | null>(null)
  const [rateStatus, setRateStatus] = useState<RateCardStatus>('ACTIVE')
  const [entitlementValues, setEntitlementValues] = useState<Record<string, string>>({})
  const [savedEntitlementValues, setSavedEntitlementValues] = useState<Record<string, string>>({})
  const [overview, setOverview] = useState({
    name: '',
    description: '',
    billingCycle: 'MONTHLY' as BillingCycle,
    basePrice: 0,
    currency: 'INR',
    visibility: 'PUBLIC' as PlanVisibility,
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
      setDefinitions(defs.filter((item) => item.active))
      setPrices(loadedPrices)
      setRateCards(loadedRates)
      setOverview({
        name: current.name,
        description: current.description ?? '',
        billingCycle: current.billingCycle,
        basePrice: current.basePrice,
        currency: current.currency,
        visibility: current.visibility,
      })
      const loadedEntitlements = Object.fromEntries(
        defs.map((definition) => [
          definition.key,
          current.entitlements.find((item) => item.key === definition.key)?.value ?? definition.defaultValue,
        ]),
      )
      setEntitlementValues(loadedEntitlements)
      setSavedEntitlementValues(loadedEntitlements)
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

  const mutable = plan?.status === 'DRAFT'
  const entitlementsByCategory = useMemo(
    () => definitions.reduce<Record<string, EntitlementDefinition[]>>((groups, item) => {
      ;(groups[item.category] ??= []).push(item)
      return groups
    }, {}),
    [definitions],
  )
  const dirtyEntitlementKeys = useMemo(
    () =>
      definitions
        .filter(
          (definition) =>
            (entitlementValues[definition.key] ?? definition.defaultValue) !==
            (savedEntitlementValues[definition.key] ?? definition.defaultValue),
        )
        .map((definition) => definition.key),
    [definitions, entitlementValues, savedEntitlementValues],
  )

  const updateEntitlementValue = (key: string, value: string) => {
    setNotice('')
    setError('')
    setEntitlementValues((current) => ({ ...current, [key]: value }))
  }

  const validateEntitlementValue = (definition: EntitlementDefinition, value: string) => {
    const normalized = value.trim()
    if (definition.valueType === 'BOOLEAN') {
      return normalized === 'true' || normalized === 'false'
    }
    if (definition.valueType === 'INTEGER') {
      if (normalized.toLowerCase() === 'unlimited') return true
      const parsed = Number(normalized)
      return Number.isInteger(parsed) && parsed >= -1
    }
    if (definition.valueType === 'DECIMAL') {
      const parsed = Number(normalized)
      return Number.isFinite(parsed) && parsed >= 0
    }
    return normalized.length > 0
  }

  const saveOverview = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!mutable) return
    setSaving(true)
    try {
      await adminBillingApi.updatePlan(planId, {
        ...overview,
        name: overview.name.trim(),
        description: overview.description.trim(),
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
    if (status === 'PUBLISHED' && dirtyEntitlementKeys.length > 0) {
      setError('Save all entitlement changes before publishing this plan.')
      return
    }
    setSaving(true)
    try {
      await adminBillingApi.updatePlan(planId, { status })
      await load()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to update status')
    } finally {
      setSaving(false)
    }
  }

  const resetPrice = () => {
    setEditingPriceId(null)
    setPriceForm({ ...blankPrice, currency: plan?.currency ?? 'INR' })
    setPriceFrom('')
    setPriceTo('')
    setPriceStatus('ACTIVE')
  }

  const editPrice = (price: PlanPrice) => {
    setEditingPriceId(price.planPriceId)
    setPriceForm({
      billingCycle: price.billingCycle,
      amount: price.amount,
      currency: price.currency,
    })
    setPriceFrom(instantToLocalDateTime(price.effectiveFrom))
    setPriceTo(instantToLocalDateTime(price.effectiveTo))
    setPriceStatus(price.status)
  }

  const savePrice = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!mutable) return
    setSaving(true)
    setError('')
    try {
      const input: CreatePlanPriceInput = buildPlanPriceInput(priceForm, priceFrom, priceTo)
      if (editingPriceId) {
        await adminBillingApi.updatePlanPrice(editingPriceId, {
          amount: input.amount,
          currency: input.currency,
          effectiveFrom: input.effectiveFrom,
          effectiveTo: input.effectiveTo,
          status: priceStatus,
        })
      } else {
        await adminBillingApi.createPlanPrice(planId, input)
      }
      resetPrice()
      await load()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to save price')
    } finally {
      setSaving(false)
    }
  }

  const resetRate = () => {
    setEditingRateId(null)
    setRateForm({ ...blankRate(planId), currency: plan?.currency ?? 'INR' })
    setRateFrom('')
    setRateTo('')
    setRateStatus('ACTIVE')
  }

  const editRate = (rate: RateCard) => {
    setEditingRateId(rate.rateCardId)
    setRateForm({
      planId: rate.planId,
      channel: rate.channel,
      provider: rate.provider,
      billingCategory: rate.billingCategory,
      direction: rate.direction,
      unit: rate.unit,
      price: rate.price,
      currency: rate.currency,
      country: rate.country ?? '',
      carrier: rate.carrier ?? '',
    })
    setRateFrom(instantToLocalDateTime(rate.effectiveFrom))
    setRateTo(instantToLocalDateTime(rate.effectiveTo))
    setRateStatus(rate.status)
  }

  const saveRate = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!mutable) return
    setSaving(true)
    setError('')
    try {
      const input: CreateRateCardInput = buildRateCardInput(rateForm, rateFrom, rateTo)
      if (editingRateId) {
        await adminBillingApi.updateRateCard(editingRateId, {
          price: input.price,
          effectiveFrom: input.effectiveFrom,
          effectiveTo: input.effectiveTo,
          status: rateStatus,
        })
      } else {
        await adminBillingApi.createRateCard(input)
      }
      resetRate()
      await load()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to save rate')
    } finally {
      setSaving(false)
    }
  }

  const saveAllEntitlements = async () => {
    if (!mutable || dirtyEntitlementKeys.length === 0 || saving) return
    const changedDefinitions = definitions.filter((definition) =>
      dirtyEntitlementKeys.includes(definition.key),
    )
    const invalid = changedDefinitions.find(
      (definition) =>
        !validateEntitlementValue(
          definition,
          entitlementValues[definition.key] ?? definition.defaultValue,
        ),
    )
    if (invalid) {
      setError(`Invalid value for ${invalid.name}`)
      return
    }

    setSaving(true)
    setError('')
    setNotice('')
    try {
      await Promise.all(
        changedDefinitions.map((definition) =>
          adminBillingApi.saveEntitlement(
            planId,
            definition.key,
            (entitlementValues[definition.key] ?? definition.defaultValue).trim(),
          ),
        ),
      )
      await load()
      setNotice(
        `Saved ${changedDefinitions.length} entitlement${changedDefinitions.length === 1 ? '' : 's'}.`,
      )
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to save entitlements')
    } finally {
      setSaving(false)
    }
  }

  const removeEntitlement = async (key: string) => {
    if (!mutable) return
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

  const changeRateChannel = (channel: ChannelType) =>
    setRateForm((current) => ({
      ...current,
      channel,
      provider: providersByChannel[channel][0],
      billingCategory: categoriesByChannel[channel][0],
      unit: defaultUsageUnitForChannel(channel),
    }))

  if (loading) return <div className="p-6"><PageSkeleton /></div>
  if (!plan) return <div className="p-6 text-red-600">{error || 'Plan not found'}</div>

  return (
    <section className="p-4 sm:p-6 lg:p-8">
      <div className="mx-auto max-w-7xl">
        <button
          className="mb-5 inline-flex items-center gap-2 text-xs font-semibold text-slate-500"
          onClick={() => navigate('/platform/plans')}
        >
          <FiArrowLeft /> Back to plans
        </button>

        <header className="flex flex-col gap-4 lg:flex-row lg:justify-between">
          <div>
            <p className="eyebrow">{plan.code} · VERSION {plan.version}</p>
            <h1 className="mt-2 text-2xl font-bold">{plan.name}</h1>
            <p className="mt-1 text-sm text-slate-500">{plan.description || 'No plan description yet.'}</p>
          </div>
          <div>
            {plan.status === 'DRAFT' && (
              <button className="primary-button gap-2" onClick={() => void setStatus('PUBLISHED')}>
                <FiCheck /> Publish
              </button>
            )}
            {plan.status === 'PUBLISHED' && (
              <button className="secondary-button gap-2" onClick={() => void setStatus('RETIRED')}>
                <FiArchive /> Retire
              </button>
            )}
          </div>
        </header>

        {!mutable && (
          <div className="mt-5 rounded-xl border border-indigo-100 bg-indigo-50 p-4 text-sm text-indigo-900">
            Published commercial terms are immutable. Create a new plan version to change commercial terms.
          </div>
        )}
        {error && (
          <div className="mt-5 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            {error}
          </div>
        )}
        {notice && (
          <div className="mt-5 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-700">
            {notice}
          </div>
        )}

        <div className="mt-7 flex overflow-x-auto border-b border-slate-200">
          {([
            ['overview', 'Overview'],
            ['pricing', 'Subscription pricing'],
            ['entitlements', 'Limits & features'],
            ['usage', 'Managed-Service pricing'],
          ] as const).map(([key, label]) => (
            <button
              key={key}
              className={`settings-tab whitespace-nowrap ${tab === key ? 'is-active' : ''}`}
              onClick={() => setTab(key)}
            >
              {label}
            </button>
          ))}
        </div>

        {tab === 'overview' && (
          <form onSubmit={saveOverview} className="panel mt-6 p-5">
            <fieldset disabled={!mutable || saving} className="grid gap-4 md:grid-cols-2">
              <label className="text-xs font-semibold">
                Name
                <input className="form-input mt-1.5" value={overview.name} onChange={(e) => setOverview({ ...overview, name: e.target.value })} />
              </label>
              <label className="text-xs font-semibold">
                Visibility
                <select className="form-input mt-1.5" value={overview.visibility} onChange={(e) => setOverview({ ...overview, visibility: e.target.value as PlanVisibility })}>
                  <option>PUBLIC</option><option>PRIVATE</option><option>INTERNAL</option>
                </select>
              </label>
              <label className="text-xs font-semibold">
                Cycle
                <select className="form-input mt-1.5" value={overview.billingCycle} onChange={(e) => setOverview({ ...overview, billingCycle: e.target.value as BillingCycle })}>
                  <option>FREE</option><option>MONTHLY</option><option>YEARLY</option>
                </select>
              </label>
              <label className="text-xs font-semibold">
                Base price
                <input className="form-input mt-1.5" type="number" value={overview.basePrice} onChange={(e) => setOverview({ ...overview, basePrice: Number(e.target.value) })} />
              </label>
              <label className="text-xs font-semibold">
                Currency
                <input className="form-input mt-1.5" value={overview.currency} onChange={(e) => setOverview({ ...overview, currency: e.target.value })} />
              </label>
              <label className="text-xs font-semibold md:col-span-2">
                Description
                <textarea className="form-input mt-1.5" value={overview.description} onChange={(e) => setOverview({ ...overview, description: e.target.value })} />
              </label>
            </fieldset>
            {mutable && <button className="primary-button mt-5 gap-2"><FiSave /> Save draft</button>}
          </form>
        )}

        {tab === 'pricing' && (
          <div className="mt-6 grid gap-6 xl:grid-cols-[380px_1fr]">
            <form className="panel p-5" onSubmit={savePrice}>
              <h2 className="font-bold">{editingPriceId ? 'Edit subscription price' : 'Add subscription price'}</h2>
              <fieldset disabled={!mutable || saving} className="mt-4 space-y-3">
                <select className="form-input" disabled={Boolean(editingPriceId)} value={priceForm.billingCycle} onChange={(e) => setPriceForm({ ...priceForm, billingCycle: e.target.value as BillingCycle })}>
                  <option>FREE</option><option>MONTHLY</option><option>YEARLY</option>
                </select>
                <input className="form-input" type="number" min="0" value={priceForm.amount} onChange={(e) => setPriceForm({ ...priceForm, amount: Number(e.target.value) })} />
                <input className="form-input" maxLength={3} value={priceForm.currency} onChange={(e) => setPriceForm({ ...priceForm, currency: e.target.value.toUpperCase() })} />
                <label className="block text-xs font-semibold">Effective from *<input required type="datetime-local" className="form-input mt-1" value={priceFrom} onChange={(e) => setPriceFrom(e.target.value)} /></label>
                <label className="block text-xs font-semibold">Effective to<input type="datetime-local" className="form-input mt-1" value={priceTo} onChange={(e) => setPriceTo(e.target.value)} /></label>
                {editingPriceId && (
                  <select className="form-input" value={priceStatus} onChange={(e) => setPriceStatus(e.target.value as RateCardStatus)}>
                    <option>ACTIVE</option><option>ARCHIVED</option>
                  </select>
                )}
              </fieldset>
              {mutable && (
                <div className="mt-4 flex gap-2">
                  <button className="primary-button gap-2"><FiSave /> {editingPriceId ? 'Update price' : 'Add price'}</button>
                  {editingPriceId && <button type="button" className="secondary-button" onClick={resetPrice}>Cancel</button>}
                </div>
              )}
            </form>
            <div className="panel">
              {prices.map((price) => (
                <div key={price.planPriceId} className="flex items-center justify-between gap-4 border-b p-4">
                  <span>{price.billingCycle} · {price.status}<small className="block text-slate-500">{price.effectiveFrom ?? 'Missing effective start'} → {price.effectiveTo ?? 'open'}</small></span>
                  <div className="flex items-center gap-3"><strong>{money(price.amount, price.currency)}</strong>{mutable && <button type="button" className="secondary-button" onClick={() => editPrice(price)}>Edit</button>}</div>
                </div>
              ))}
            </div>
          </div>
        )}

        {tab === 'entitlements' && (
          <div className="mt-6 space-y-5">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h2 className="text-lg font-bold">Limits & features</h2>
                <p className="text-xs text-slate-500">
                  {mutable
                    ? 'Edit multiple limits and feature switches, then save the complete set of changes.'
                    : 'This published plan version is read-only.'}
                </p>
              </div>
              {mutable && (
                <div className="flex items-center gap-3">
                  <span className="text-xs text-slate-500">
                    {dirtyEntitlementKeys.length === 0
                      ? 'No unsaved changes'
                      : `${dirtyEntitlementKeys.length} unsaved change${dirtyEntitlementKeys.length === 1 ? '' : 's'}`}
                  </span>
                  <button
                    type="button"
                    className="primary-button gap-2"
                    disabled={saving || dirtyEntitlementKeys.length === 0}
                    onClick={() => void saveAllEntitlements()}
                  >
                    <FiSave /> {saving ? 'Saving…' : 'Save all'}
                  </button>
                </div>
              )}
            </div>
            {Object.entries(entitlementsByCategory).map(([category, items]) => (
              <div key={category} className="panel">
                <h2 className="border-b p-4 font-bold">{category}</h2>
                {items.map((definition) => {
                  const assigned = plan.entitlements.some((item) => item.key === definition.key)
                  const value = entitlementValues[definition.key] ?? definition.defaultValue
                  return (
                    <div key={definition.key} className="grid gap-3 border-b p-4 lg:grid-cols-[1fr_260px_auto]">
                      <div>
                        <strong>{definition.name}</strong>
                        <p className="text-xs text-slate-500">{definition.description || definition.key}</p>
                        <p className="mt-1 font-mono text-[10px] text-slate-400">{definition.key}</p>
                      </div>
                      {definition.valueType === 'BOOLEAN' ? (
                        <select
                          aria-label={definition.name}
                          disabled={!mutable || saving}
                          className="form-input"
                          value={value}
                          onChange={(event) => updateEntitlementValue(definition.key, event.target.value)}
                        >
                          <option value="true">Enabled</option>
                          <option value="false">Disabled</option>
                        </select>
                      ) : definition.valueType === 'INTEGER' || definition.valueType === 'DECIMAL' ? (
                        <input
                          aria-label={definition.name}
                          disabled={!mutable || saving}
                          className="form-input"
                          type="number"
                          min={definition.valueType === 'INTEGER' ? -1 : 0}
                          step={definition.valueType === 'INTEGER' ? 1 : 'any'}
                          value={value}
                          onChange={(event) => updateEntitlementValue(definition.key, event.target.value)}
                        />
                      ) : (
                        <input
                          aria-label={definition.name}
                          disabled={!mutable || saving}
                          className="form-input"
                          value={value}
                          onChange={(event) => updateEntitlementValue(definition.key, event.target.value)}
                        />
                      )}
                      {mutable && (
                        <div className="flex items-center justify-end gap-2">
                          {assigned && (
                            <button
                              type="button"
                              className="secondary-button text-rose-600"
                              disabled={saving}
                              onClick={() => void removeEntitlement(definition.key)}
                            >
                              <FiTrash2 /> Remove
                            </button>
                          )}
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>
            ))}
          </div>
        )}

        {tab === 'usage' && (
          <div className="mt-6 grid gap-6 xl:grid-cols-[400px_1fr]">
            <form className="panel p-5" onSubmit={saveRate}>
              <h2 className="font-bold">{editingRateId ? 'Edit Managed-Service rate' : 'Add Managed-Service rate'}</h2>
              <fieldset disabled={!mutable || saving} className="mt-4 space-y-3">
                <select className="form-input" disabled={Boolean(editingRateId)} value={rateForm.channel} onChange={(e) => changeRateChannel(e.target.value as ChannelType)}><option>SMS</option><option>WHATSAPP</option><option>EMAIL</option></select>
                <select className="form-input" disabled={Boolean(editingRateId)} value={rateForm.provider} onChange={(e) => setRateForm({ ...rateForm, provider: e.target.value as ProviderType })}>{providersByChannel[rateForm.channel].map((provider) => <option key={provider}>{provider}</option>)}</select>
                <select className="form-input" disabled={Boolean(editingRateId)} value={rateForm.billingCategory} onChange={(e) => setRateForm({ ...rateForm, billingCategory: e.target.value as BillingCategory })}>{categoriesByChannel[rateForm.channel].map((category) => <option key={category}>{category}</option>)}</select>
                <select className="form-input" disabled={Boolean(editingRateId)} value={rateForm.direction} onChange={(e) => setRateForm({ ...rateForm, direction: e.target.value as CommunicationDirection })}><option>OUTBOUND</option><option>INBOUND</option></select>
                <select className="form-input" disabled={Boolean(editingRateId)} value={rateForm.unit} onChange={(e) => setRateForm({ ...rateForm, unit: e.target.value as UsageUnit })}>{PLATFORM_RATE_USAGE_UNITS.map((unit) => <option key={unit}>{unit}</option>)}</select>
                <input className="form-input" disabled={Boolean(editingRateId)} placeholder="Country (optional)" value={rateForm.country ?? ''} onChange={(e) => setRateForm({ ...rateForm, country: e.target.value })} />
                <input className="form-input" disabled={Boolean(editingRateId)} placeholder="Carrier (optional)" value={rateForm.carrier ?? ''} onChange={(e) => setRateForm({ ...rateForm, carrier: e.target.value })} />
                <input className="form-input" type="number" min="0" value={rateForm.price} onChange={(e) => setRateForm({ ...rateForm, price: Number(e.target.value) })} />
                <input className="form-input" disabled={Boolean(editingRateId)} maxLength={3} value={rateForm.currency} onChange={(e) => setRateForm({ ...rateForm, currency: e.target.value.toUpperCase() })} />
                <label className="block text-xs font-semibold">Effective from *<input required type="datetime-local" className="form-input mt-1" value={rateFrom} onChange={(e) => setRateFrom(e.target.value)} /></label>
                <label className="block text-xs font-semibold">Effective to<input type="datetime-local" className="form-input mt-1" value={rateTo} onChange={(e) => setRateTo(e.target.value)} /></label>
                {editingRateId && <select className="form-input" value={rateStatus} onChange={(e) => setRateStatus(e.target.value as RateCardStatus)}><option>ACTIVE</option><option>ARCHIVED</option></select>}
              </fieldset>
              {mutable && (
                <div className="mt-4 flex gap-2">
                  <button className="primary-button gap-2"><FiSave /> {editingRateId ? 'Update rate' : 'Add rate'}</button>
                  {editingRateId && <button type="button" className="secondary-button" onClick={resetRate}>Cancel</button>}
                </div>
              )}
            </form>
            <div className="panel overflow-x-auto">
              <table className="mobile-card-table w-full min-w-[650px] text-left text-sm">
                <tbody>
                  {rateCards.map((rate) => (
                    <tr key={rate.rateCardId} className="border-b">
                      <td className="p-4"><strong>{rate.channel} · {rate.provider}</strong><small className="block text-slate-500">{rate.country || 'all countries'} · {rate.carrier || 'all carriers'}</small></td>
                      <td className="p-4">{rate.billingCategory} · {rate.direction}</td>
                      <td className="p-4">{rate.unit}</td>
                      <td className="p-4 font-bold">{money(rate.price, rate.currency)}</td>
                      <td className="p-4">{mutable && <button type="button" className="secondary-button" onClick={() => editRate(rate)}>Edit</button>}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </section>
  )
}
