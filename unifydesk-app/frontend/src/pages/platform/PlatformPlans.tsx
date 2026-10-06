import { useEffect, useMemo, useState } from 'react'
import { FiArrowRight, FiLayers, FiPlus, FiX } from 'react-icons/fi'
import { useNavigate } from 'react-router'
import { platformApi } from '../../features/platform/platformApi'
import { adminBillingApi } from '../../features/platformBilling/adminBillingApi'
import type {
  BillingPlan,
  BillingCycle,
  CreateBillingPlanInput,
  PlanStatus,
} from '../../features/platformBilling/types'
import { CardSkeleton } from '../../components/ui/Skeleton'

const emptyPlan: CreateBillingPlanInput = {
  code: '',
  name: '',
  description: '',
  billingCycle: 'MONTHLY',
  basePrice: 0,
  currency: 'INR',
  version: 1,
}

const statusClass: Record<PlanStatus, string> = {
  DRAFT: 'bg-amber-50 text-amber-700',
  PUBLISHED: 'bg-emerald-50 text-emerald-700',
  RETIRED: 'bg-slate-100 text-slate-600',
}

const money = (amount: number, currency: string) => {
  try {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency,
      maximumFractionDigits: 2,
    }).format(amount)
  } catch {
    return `${currency} ${amount}`
  }
}

export default function PlatformPlans() {
  const navigate = useNavigate()
  const [plans, setPlans] = useState<BillingPlan[]>([])
  const [form, setForm] = useState<CreateBillingPlanInput>(emptyPlan)
  const [showCreate, setShowCreate] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [superAdmin, setSuperAdmin] = useState<boolean | null>(null)

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      const access = await platformApi.me()
      const allowed = access.role === 'SUPER_ADMIN'
      setSuperAdmin(allowed)
      if (!allowed) return
      setPlans(await adminBillingApi.plans())
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to load plans')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [])

  const summary = useMemo(
    () => ({
      total: plans.length,
      published: plans.filter((plan) => plan.status === 'PUBLISHED').length,
      drafts: plans.filter((plan) => plan.status === 'DRAFT').length,
    }),
    [plans],
  )

  const create = async (event: React.FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    try {
      const created = await adminBillingApi.createPlan({
        ...form,
        code: form.code.trim().toUpperCase().replace(/\s+/g, '_'),
        name: form.name.trim(),
        description: form.description?.trim(),
        currency: form.currency.trim().toUpperCase(),
      })
      setForm(emptyPlan)
      setShowCreate(false)
      await load()
      navigate(`/platform/plans/${created.planId}`)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to create plan')
    } finally {
      setSaving(false)
    }
  }

  if (loading)
    return (
      <div className="grid gap-4 p-4 sm:grid-cols-2 sm:p-6 lg:grid-cols-3 lg:p-8">
        <CardSkeleton rows={5} />
        <CardSkeleton rows={5} />
        <CardSkeleton rows={5} />
      </div>
    )

  if (superAdmin === false) {
    return (
      <section className="p-4 sm:p-6 lg:p-8">
        <div className="panel max-w-2xl p-6">
          <p className="eyebrow">PLANS & PRICING</p>
          <h1 className="mt-2 text-xl font-bold text-slate-950">Super Admin access required</h1>
          <p className="mt-2 text-sm leading-6 text-slate-500">
            Plan definitions, entitlements and platform rate cards are global billing configuration
            and are restricted to Super Admins.
          </p>
        </div>
      </section>
    )
  }

  return (
    <section className="p-4 sm:p-6 lg:p-8">
      <div className="mx-auto max-w-7xl">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="eyebrow">BILLING CATALOG</p>
            <h1 className="mt-2 text-2xl font-bold text-slate-950">Plans & pricing</h1>
            <p className="mt-1 max-w-2xl text-sm text-slate-500">
              Manage commercial plans, plan versions, subscription prices, feature limits and
              channel usage pricing from one place.
            </p>
          </div>
          <button
            type="button"
            className="primary-button gap-2"
            onClick={() => setShowCreate((open) => !open)}
          >
            {showCreate ? <FiX /> : <FiPlus />}
            {showCreate ? 'Close' : 'Create plan'}
          </button>
        </div>

        {error && (
          <div className="mt-5 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
            {error}
          </div>
        )}

        <div className="mt-6 grid gap-4 sm:grid-cols-3">
          <div className="panel p-4">
            <p className="text-xs font-semibold text-slate-500">Total plans</p>
            <p className="mt-2 text-2xl font-bold text-slate-950">{summary.total}</p>
          </div>
          <div className="panel p-4">
            <p className="text-xs font-semibold text-slate-500">Published</p>
            <p className="mt-2 text-2xl font-bold text-slate-950">{summary.published}</p>
          </div>
          <div className="panel p-4">
            <p className="text-xs font-semibold text-slate-500">Draft versions</p>
            <p className="mt-2 text-2xl font-bold text-slate-950">{summary.drafts}</p>
          </div>
        </div>

        {showCreate && (
          <form onSubmit={create} className="panel mt-6 p-5">
            <div className="flex items-start gap-3">
              <span className="security-icon">
                <FiLayers />
              </span>
              <div>
                <h2 className="text-base font-bold text-slate-950">Create plan version</h2>
                <p className="mt-1 text-xs text-slate-500">
                  New plans start as drafts. Configure prices and limits before publishing.
                </p>
              </div>
            </div>
            <div className="mt-5 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              <label className="text-xs font-semibold text-slate-600">
                Code
                <input
                  className="form-input mt-1.5"
                  placeholder="STARTER"
                  value={form.code}
                  onChange={(e) => setForm({ ...form, code: e.target.value })}
                  required
                />
              </label>
              <label className="text-xs font-semibold text-slate-600">
                Name
                <input
                  className="form-input mt-1.5"
                  placeholder="Starter"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  required
                />
              </label>
              <label className="text-xs font-semibold text-slate-600">
                Version
                <input
                  className="form-input mt-1.5"
                  type="number"
                  min="1"
                  value={form.version}
                  onChange={(e) => setForm({ ...form, version: Number(e.target.value) })}
                  required
                />
              </label>
              <label className="text-xs font-semibold text-slate-600">
                Default billing cycle
                <select
                  className="form-input mt-1.5"
                  value={form.billingCycle}
                  onChange={(e) =>
                    setForm({ ...form, billingCycle: e.target.value as BillingCycle })
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
                  value={form.basePrice}
                  onChange={(e) => setForm({ ...form, basePrice: Number(e.target.value) })}
                  required
                />
              </label>
              <label className="text-xs font-semibold text-slate-600">
                Currency
                <input
                  className="form-input mt-1.5"
                  minLength={3}
                  maxLength={3}
                  value={form.currency}
                  onChange={(e) => setForm({ ...form, currency: e.target.value.toUpperCase() })}
                  required
                />
              </label>
              <label className="text-xs font-semibold text-slate-600 md:col-span-2 xl:col-span-3">
                Description
                <textarea
                  className="form-input mt-1.5"
                  maxLength={500}
                  placeholder="Who this plan is for and what it includes"
                  value={form.description ?? ''}
                  onChange={(e) => setForm({ ...form, description: e.target.value })}
                />
              </label>
            </div>
            <div className="mt-5 flex justify-end">
              <button className="primary-button" disabled={saving}>
                {saving ? 'Creating…' : 'Create draft'}
              </button>
            </div>
          </form>
        )}

        <div className="panel mt-6 overflow-hidden">
          <div className="border-b border-slate-100 px-5 py-4">
            <h2 className="text-sm font-bold text-slate-950">Plan versions</h2>
            <p className="mt-1 text-xs text-slate-500">
              Open a plan to configure prices, entitlements and per-channel rate cards.
            </p>
          </div>
          {plans.length === 0 ? (
            <div className="px-5 py-12 text-center text-sm text-slate-500">
              No plans yet. Create the first draft to start the billing catalog.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="mobile-card-table w-full min-w-[780px] text-left text-sm">
                <thead className="bg-slate-50 text-[11px] uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="px-5 py-3">Plan</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3">Base price</th>
                    <th className="px-4 py-3">Prices</th>
                    <th className="px-4 py-3">Limits</th>
                    <th className="px-5 py-3 text-right">Manage</th>
                  </tr>
                </thead>
                <tbody>
                  {plans.map((plan) => (
                    <tr
                      key={plan.planId}
                      className="border-t border-slate-100 hover:bg-slate-50/60"
                    >
                      <td className="px-5 py-4" data-label="Plan">
                        <p className="font-bold text-slate-950">{plan.name}</p>
                        <p className="mt-0.5 text-xs text-slate-500">
                          {plan.code} · v{plan.version} · {plan.billingCycle}
                        </p>
                      </td>
                      <td className="px-4 py-4" data-label="Status">
                        <span
                          className={`rounded-full px-2.5 py-1 text-[10px] font-bold ${statusClass[plan.status]}`}
                        >
                          {plan.status}
                        </span>
                      </td>
                      <td
                        className="px-4 py-4 font-semibold text-slate-700"
                        data-label="Base price"
                      >
                        {money(plan.basePrice, plan.currency)}
                      </td>
                      <td className="px-4 py-4 text-slate-600" data-label="Prices">
                        {plan.prices.length}
                      </td>
                      <td className="px-4 py-4 text-slate-600" data-label="Limits">
                        {plan.entitlements.length}
                      </td>
                      <td className="mobile-action-cell px-5 py-4 text-right" data-label="Manage">
                        <button
                          type="button"
                          className="secondary-button gap-2"
                          onClick={() => navigate(`/platform/plans/${plan.planId}`)}
                        >
                          Configure <FiArrowRight />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </section>
  )
}
