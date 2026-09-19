import { useEffect, useState } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router'
import { FiCheck } from 'react-icons/fi'
import { billingApi } from '../../features/billing/billingApi'
import type { Plan } from '../../features/billing/types'
import { workspaceApi } from '../../features/workspace/workspaceApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppDispatch } from '../../store/hooks'
import { selectCompany, setCompanies } from '../../store/workspace/workspaceSlice'

const entitlementLabel = (key: string, value: string) => {
  const labels: Record<string, string> = {
    MAX_WORKSPACE_USERS: `${value} workspace user`,
    BROADCAST_BATCH_LIMIT: `${value} recipients per broadcast`,
    EMAIL_TEMPLATE_LIMIT: `${value} email template`,
    SOCIAL_TEMPLATE_LIMIT: `${value} social media template`,
    AUTO_REPLY_ENABLED: value === 'true' ? 'Auto reply included' : 'Auto reply not included',
    USER_SIGNATURE_ENABLED:
      value === 'true' ? 'User signature included' : 'User signature not included',
  }
  return labels[key] ?? `${key.replaceAll('_', ' ').toLowerCase()}: ${value}`
}

export default function SelectPlan() {
  const { companyId } = useParams()
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const [plans, setPlans] = useState<Plan[]>([])
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

  return (
    <div className="min-h-dvh bg-white px-4 py-10 sm:px-6 sm:py-20">
      <main className="mx-auto w-full max-w-[760px]">
        <h1 className="text-[26px] font-bold leading-tight text-slate-950">Select a plan</h1>
        <p className="mt-2 text-sm text-slate-500">
          Choose how this workspace should start. You can change plans later.
        </p>
        {loading && <p className="mt-8 text-sm font-semibold text-slate-500">Loading...</p>}
        {error && <p className="mt-8 text-sm font-semibold text-red-600">{error}</p>}
        {!loading && !error && (
          <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {plans.map((plan) => (
              <article key={plan.planId} className="rounded-[8px] border border-slate-200 p-5">
                <p className="text-xs font-semibold uppercase text-indigo-500">{plan.code}</p>
                <h2 className="mt-2 text-xl font-bold text-slate-950">{plan.name}</h2>
                <p className="mt-3 text-2xl font-bold text-slate-950">
                  {Number(plan.basePrice) === 0
                    ? 'Free'
                    : `${plan.currency} ${plan.basePrice}/${plan.billingCycle.toLowerCase()}`}
                </p>
                <ul className="mt-5 space-y-2">
                  {plan.entitlements.map((entitlement) => (
                    <li
                      key={entitlement.entitlementId}
                      className="flex gap-2 text-xs leading-5 text-slate-600"
                    >
                      <FiCheck aria-hidden="true" className="mt-0.5 shrink-0 text-indigo-500" />
                      {entitlementLabel(entitlement.key, entitlement.value)}
                    </li>
                  ))}
                </ul>
                <button
                  type="button"
                  className="mt-6 flex h-10 w-full items-center justify-center rounded-[8px] bg-indigo-500 text-xs font-semibold text-white"
                  disabled={Boolean(submittingPlanId)}
                  onClick={() => choosePlan(plan)}
                >
                  {submittingPlanId === plan.planId ? 'Selecting...' : 'Select plan'}
                </button>
              </article>
            ))}
          </div>
        )}
      </main>
    </div>
  )
}
