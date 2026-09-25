import { useEffect, useState } from 'react'
import { FiCheck } from 'react-icons/fi'
import { Navigate, useNavigate, useParams } from 'react-router'
import { billingApi } from '../../features/billing/billingApi'
import type { Plan } from '../../features/billing/types'
import { workspaceApi } from '../../features/workspace/workspaceApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppDispatch } from '../../store/hooks'
import { selectCompany, setCompanies } from '../../store/workspace/workspaceSlice'

const COLLAPSED_ENTITLEMENT_COUNT = 7

const entitlementPriority = [
  'MAX_WORKSPACE_USERS',
  'CONTACT_LIMIT',
  'SEGMENT_LIMIT',
  'BROADCAST_BATCH_LIMIT',
  'BROADCAST_RECIPIENTS_PER_MONTH',
  'EMAIL_ACCOUNTS',
  'WHATSAPP_NUMBERS',
  'BROADCASTS_PER_DAY',
  'BROADCASTS_PER_MONTH',
  'BROADCAST_RECIPIENTS_PER_DAY',
  'BROADCAST_GROUP_LIMIT',
  'WHATSAPP_ACCOUNTS',
  'SMS_ACCOUNTS',
  'SMS_SENDER_IDS',
  'EMAIL_TEMPLATE_LIMIT',
  'WHATSAPP_TEMPLATE_LIMIT',
  'SMS_TEMPLATE_LIMIT',
  'STORAGE_GB',
  'SCHEDULED_BROADCASTS_ENABLED',
  'AUTO_REPLY_ENABLED',
  'USER_SIGNATURE_ENABLED',
  'API_ACCESS_ENABLED',
]

const pluralize = (value: string, singular: string, plural = `${singular}s`) =>
  `${value} ${value === '1' ? singular : plural}`

const entitlementLabel = (key: string, value: string) => {
  const unlimited = value === '-1' || value.toLowerCase() === 'unlimited'
  const amount = unlimited ? 'Unlimited' : value

  const labels: Record<string, string> = {
    MAX_WORKSPACE_USERS: unlimited
      ? 'Unlimited workspace users'
      : pluralize(value, 'workspace user'),
    CONTACT_LIMIT: unlimited ? 'Unlimited contacts' : pluralize(value, 'contact'),
    SEGMENT_LIMIT: unlimited ? 'Unlimited segments' : pluralize(value, 'segment'),
    BROADCAST_GROUP_LIMIT: unlimited
      ? 'Unlimited broadcast groups'
      : pluralize(value, 'broadcast group'),
    EMAIL_ACCOUNTS: unlimited ? 'Unlimited email accounts' : pluralize(value, 'email account'),
    WHATSAPP_ACCOUNTS: unlimited
      ? 'Unlimited WhatsApp accounts'
      : pluralize(value, 'WhatsApp account'),
    WHATSAPP_NUMBERS: unlimited
      ? 'Unlimited WhatsApp numbers'
      : pluralize(value, 'WhatsApp number'),
    SMS_ACCOUNTS: unlimited ? 'Unlimited SMS accounts' : pluralize(value, 'SMS account'),
    SMS_SENDER_IDS: unlimited ? 'Unlimited SMS sender IDs' : pluralize(value, 'SMS sender ID'),
    BROADCAST_BATCH_LIMIT: unlimited
      ? 'Unlimited recipients per broadcast'
      : `${value} recipients per broadcast`,
    BROADCASTS_PER_DAY: unlimited ? 'Unlimited broadcasts per day' : `${value} broadcasts per day`,
    BROADCASTS_PER_MONTH: unlimited
      ? 'Unlimited broadcasts per month'
      : `${value} broadcasts per month`,
    BROADCAST_RECIPIENTS_PER_DAY: unlimited
      ? 'Unlimited broadcast recipients per day'
      : `${value} broadcast recipients per day`,
    BROADCAST_RECIPIENTS_PER_MONTH: unlimited
      ? 'Unlimited broadcast recipients per month'
      : `${value} broadcast recipients per month`,
    EMAIL_TEMPLATE_LIMIT: unlimited
      ? 'Unlimited email templates'
      : pluralize(value, 'email template'),
    WHATSAPP_TEMPLATE_LIMIT: unlimited
      ? 'Unlimited WhatsApp templates'
      : pluralize(value, 'WhatsApp template'),
    SMS_TEMPLATE_LIMIT: unlimited ? 'Unlimited SMS templates' : pluralize(value, 'SMS template'),
    STORAGE_GB: unlimited ? 'Unlimited workspace storage' : `${amount} GiB workspace storage`,
    SCHEDULED_BROADCASTS_ENABLED:
      value === 'true' ? 'Scheduled broadcasts included' : 'Scheduled broadcasts not included',
    AUTO_REPLY_ENABLED: value === 'true' ? 'Auto reply included' : 'Auto reply not included',
    USER_SIGNATURE_ENABLED:
      value === 'true' ? 'User signatures included' : 'User signatures not included',
    API_ACCESS_ENABLED: value === 'true' ? 'API access included' : 'API access not included',
  }

  return labels[key] ?? `${key.replaceAll('_', ' ').toLowerCase()}: ${value}`
}

const orderedEntitlements = (plan: Plan) =>
  [...plan.entitlements].sort((left, right) => {
    const leftIndex = entitlementPriority.indexOf(left.key)
    const rightIndex = entitlementPriority.indexOf(right.key)
    const leftRank = leftIndex === -1 ? entitlementPriority.length : leftIndex
    const rightRank = rightIndex === -1 ? entitlementPriority.length : rightIndex
    return leftRank - rightRank
  })

export default function SelectPlan() {
  const { companyId } = useParams()
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const [plans, setPlans] = useState<Plan[]>([])
  const [expandedPlans, setExpandedPlans] = useState<Record<string, boolean>>({})
  const [loading, setLoading] = useState(true)
  const [submittingPlanId, setSubmittingPlanId] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    billingApi
      .plans()
      .then((items) => {
        if (active) setPlans(items)
      })
      .catch((loadError) => {
        if (active) setError(apiErrorMessage(loadError, 'Unable to load plans'))
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [])

  if (!companyId) return <Navigate to="/select-workspace" replace />

  const choosePlan = async (plan: Plan) => {
    setError('')
    setSubmittingPlanId(plan.planId)
    try {
      await billingApi.selectPlan(companyId, plan.planId)
      const companies = await workspaceApi.list()
      dispatch(setCompanies(companies))
      dispatch(selectCompany(companyId))
      navigate('/workspace', { replace: true })
    } catch (selectError) {
      setError(apiErrorMessage(selectError, 'Unable to select plan'))
    } finally {
      setSubmittingPlanId('')
    }
  }

  const togglePlanFeatures = (planId: string) => {
    setExpandedPlans((current) => ({
      ...current,
      [planId]: !current[planId],
    }))
  }

  return (
    <div className="min-h-dvh bg-white px-4 py-10 sm:px-6 sm:py-20">
      <main className="mx-auto w-full max-w-[960px]">
        <h1 className="text-[26px] font-bold leading-tight text-slate-950">Select a plan</h1>
        <p className="mt-2 text-sm text-slate-500">
          Choose how this workspace should start. You can change plans later.
        </p>
        {loading && <p className="mt-8 text-sm font-semibold text-slate-500">Loading...</p>}
        {error && <p className="mt-8 text-sm font-semibold text-red-600">{error}</p>}
        {!loading && !error && (
          <div className="mt-8 grid items-start gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {plans.map((plan) => {
              const entitlements = orderedEntitlements(plan)
              const expanded = Boolean(expandedPlans[plan.planId])
              const visibleEntitlements = expanded
                ? entitlements
                : entitlements.slice(0, COLLAPSED_ENTITLEMENT_COUNT)
              const hiddenCount = Math.max(entitlements.length - COLLAPSED_ENTITLEMENT_COUNT, 0)

              return (
                <article key={plan.planId} className="rounded-[8px] border border-slate-200 p-5">
                  <p className="text-xs font-semibold uppercase text-indigo-500">{plan.code}</p>
                  <h2 className="mt-2 text-xl font-bold text-slate-950">{plan.name}</h2>
                  <p className="mt-3 text-2xl font-bold text-slate-950">
                    {Number(plan.basePrice) === 0
                      ? 'Free'
                      : `${plan.currency} ${plan.basePrice}/${plan.billingCycle.toLowerCase()}`}
                  </p>

                  <ul className="mt-5 space-y-2">
                    {visibleEntitlements.map((entitlement) => (
                      <li
                        key={entitlement.entitlementId}
                        className="flex gap-2 text-xs leading-5 text-slate-600"
                      >
                        <FiCheck aria-hidden="true" className="mt-0.5 shrink-0 text-indigo-500" />
                        {entitlementLabel(entitlement.key, entitlement.value)}
                      </li>
                    ))}
                  </ul>

                  {hiddenCount > 0 && (
                    <button
                      type="button"
                      className="mt-3 text-xs font-semibold text-indigo-600 hover:text-indigo-700"
                      onClick={() => togglePlanFeatures(plan.planId)}
                    >
                      {expanded ? 'Show less' : `+ ${hiddenCount} more`}
                    </button>
                  )}

                  <button
                    type="button"
                    className="mt-6 flex h-10 w-full items-center justify-center rounded-[8px] bg-indigo-500
                      text-xs font-semibold text-white"
                    disabled={Boolean(submittingPlanId)}
                    onClick={() => choosePlan(plan)}
                  >
                    {submittingPlanId === plan.planId ? 'Selecting...' : 'Select plan'}
                  </button>
                </article>
              )
            })}
          </div>
        )}
      </main>
    </div>
  )
}
