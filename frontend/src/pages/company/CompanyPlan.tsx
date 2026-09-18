import { useEffect, useState } from 'react'
import { FiCheck, FiCreditCard, FiRefreshCw } from 'react-icons/fi'
import { billingApi } from '../../features/billing/billingApi'
import type { CompanyPlanDetails } from '../../features/billing/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'

const entitlementLabel = (key: string, value: string) => {
  const labels: Record<string, string> = {
    MAX_WORKSPACE_USERS: `${value} workspace users`,
    BROADCAST_BATCH_LIMIT: `${value} recipients per broadcast`,
    EMAIL_TEMPLATE_LIMIT: `${value} email templates`,
    SOCIAL_TEMPLATE_LIMIT: `${value} social media templates`,
    AUTO_REPLY_ENABLED: value === 'true' ? 'Auto reply included' : 'Auto reply not included',
    USER_SIGNATURE_ENABLED:
      value === 'true' ? 'User signature included' : 'User signature not included',
  }
  return labels[key] ?? `${key.replaceAll('_', ' ').toLowerCase()}: ${value}`
}

export default function CompanyPlan() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const [details, setDetails] = useState<CompanyPlanDetails | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const loadPlan = async () => {
    if (!selectedCompany) return
    setError('')
    setLoading(true)
    try {
      setDetails(await billingApi.companyPlan(selectedCompany.companyId))
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load company plan'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadPlan()
  }, [selectedCompany?.companyId])

  const plan = details?.plan
  const subscription = details?.subscription

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-5xl">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="eyebrow">Company</p>
            <h1 className="mt-2 text-2xl font-bold text-slate-950">Plan details</h1>
          </div>
          <button type="button" className="secondary-button inline-flex" onClick={loadPlan}>
            <FiRefreshCw aria-hidden="true" />
            <span className="ml-2">Refresh</span>
          </button>
        </div>
        {error && (
          <div className="mt-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}
        {loading ? (
          <div className="panel mt-5 p-6 text-sm font-semibold text-slate-500">Loading plan</div>
        ) : plan && subscription ? (
          <div className="mt-5 grid gap-5 lg:grid-cols-[1fr_320px]">
            <article className="panel p-6">
              <div className="flex items-start gap-4">
                <span className="security-icon">
                  <FiCreditCard aria-hidden="true" />
                </span>
                <div>
                  <p className="text-xs font-semibold uppercase text-indigo-500">{plan.code}</p>
                  <h2 className="mt-1 text-2xl font-bold text-slate-950">{plan.name}</h2>
                  <p className="mt-3 text-3xl font-bold text-slate-950">
                    {Number(plan.basePrice) === 0
                      ? 'Free'
                      : `${plan.currency} ${plan.basePrice}/${plan.billingCycle.toLowerCase()}`}
                  </p>
                </div>
              </div>
              <div className="mt-6 grid gap-3 sm:grid-cols-2">
                {plan.entitlements.map((entitlement) => (
                  <div
                    key={entitlement.entitlementId}
                    className="flex gap-2 rounded-lg bg-slate-50 p-3 text-sm text-slate-700"
                  >
                    <FiCheck aria-hidden="true" className="mt-0.5 shrink-0 text-indigo-500" />
                    {entitlementLabel(entitlement.key, entitlement.value)}
                  </div>
                ))}
              </div>
            </article>
            <aside className="panel p-5">
              <h3 className="text-base font-bold text-slate-950">Subscription</h3>
              <dl className="mt-4 grid gap-3 text-sm">
                <div>
                  <dt className="text-xs font-semibold text-slate-500">Status</dt>
                  <dd className="mt-1 font-semibold text-slate-950">{subscription.status}</dd>
                </div>
                <div>
                  <dt className="text-xs font-semibold text-slate-500">Started</dt>
                  <dd className="mt-1 text-slate-700">
                    {new Date(subscription.startedAt).toLocaleDateString()}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs font-semibold text-slate-500">Ends</dt>
                  <dd className="mt-1 text-slate-700">
                    {subscription.endsAt
                      ? new Date(subscription.endsAt).toLocaleDateString()
                      : 'No end date'}
                  </dd>
                </div>
              </dl>
            </aside>
          </div>
        ) : (
          <div className="panel mt-5 p-6 text-sm font-semibold text-slate-500">
            No active plan found
          </div>
        )}
      </div>
    </section>
  )
}
