import { FiAlertCircle, FiCheckCircle, FiInfo, FiLock, FiPauseCircle } from 'react-icons/fi'
import type { AdAccount, ProviderCampaignPlan } from '../../features/ads/types'

type Props = {
  plans: ProviderCampaignPlan[]
  accounts: AdAccount[]
  loading?: boolean
}

const pretty = (value: string) =>
  value
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/\b\w/g, (character) => character.toUpperCase())

const money = (minor: number | null | undefined, currency?: string | null) => {
  if (minor == null) return '—'
  if (!currency) return `${minor} minor units`
  try {
    const formatter = new Intl.NumberFormat(undefined, { style: 'currency', currency })
    const digits = formatter.resolvedOptions().maximumFractionDigits ?? 2
    return formatter.format(minor / 10 ** digits)
  } catch {
    return `${minor} ${currency}`
  }
}

export default function ReachlyoProviderPlanReview({ plans, accounts, loading = false }: Props) {
  if (loading) {
    return (
      <div className="panel p-10 text-center text-sm text-slate-500">Compiling provider plans…</div>
    )
  }

  if (plans.length === 0) {
    return (
      <div className="panel p-10 text-center text-sm text-slate-500">
        Compile the selected channels to see exactly what Reachlyo will create in each provider.
      </div>
    )
  }

  return (
    <div className="grid gap-4">
      {plans.map((plan) => {
        const account = accounts.find((item) => item.id === plan.adAccountId)
        const errors = plan.issues.filter((issue) => issue.severity === 'ERROR')
        const warnings = plan.issues.filter((issue) => issue.severity === 'WARNING')
        return (
          <article key={plan.channelId} className="panel overflow-hidden">
            <div className="flex flex-wrap items-start justify-between gap-4 border-b border-slate-100 px-5 py-4">
              <div>
                <div className="flex flex-wrap items-center gap-2">
                  <h3 className="text-base font-bold text-slate-950">{pretty(plan.channelType)}</h3>
                  <span
                    className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${plan.deployable ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'}`}
                  >
                    {plan.deployable ? 'Compiler ready' : 'Needs attention'}
                  </span>
                </div>
                <p className="mt-1 text-xs text-slate-500">
                  {account?.name ?? 'Advertising account'} ·{' '}
                  {plan.provider === 'GOOGLE_ADS' ? 'Google Ads' : 'Meta'}
                </p>
              </div>
              <div className="text-right">
                <p className="text-sm font-bold text-slate-950">
                  {money(plan.effectiveBudgetMinor, account?.currency)}
                </p>
                <p className="mt-1 text-xs text-slate-400">
                  {plan.budgetPeriod ? pretty(plan.budgetPeriod) : 'Budget unresolved'}
                </p>
              </div>
            </div>

            <div className="grid gap-5 px-5 py-5 lg:grid-cols-[minmax(0,1fr)_minmax(280px,0.7fr)]">
              <div>
                <div className="flex items-center gap-2">
                  <FiLock className="text-slate-400" aria-hidden="true" />
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                    Execution plan
                  </p>
                </div>
                <div className="mt-3 grid gap-2">
                  {plan.operations.map((operation) => (
                    <div
                      key={operation.operationKey}
                      className="flex items-center gap-3 rounded-xl border border-slate-100 px-3 py-3"
                    >
                      <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-slate-100 text-xs font-bold text-slate-600">
                        {operation.sequence}
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-semibold text-slate-800">
                          {pretty(operation.operationKey)}
                        </p>
                        <p className="mt-0.5 text-xs text-slate-400">
                          {pretty(operation.action)} {pretty(operation.resource)}
                        </p>
                      </div>
                      {operation.createPaused && (
                        <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2 py-1 text-[10px] font-semibold text-amber-700">
                          <FiPauseCircle aria-hidden="true" />
                          Paused
                        </span>
                      )}
                    </div>
                  ))}
                </div>
              </div>

              <div>
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                  Mapping & validation
                </p>
                {plan.audienceMappingStatus && (
                  <div className="mt-3 rounded-xl border border-slate-100 p-3">
                    <p className="text-xs font-semibold text-slate-700">
                      Audience mapping · {pretty(plan.audienceMappingStatus)}
                    </p>
                    {plan.audienceMappingNote && (
                      <p className="mt-1 text-xs leading-5 text-slate-500">
                        {plan.audienceMappingNote}
                      </p>
                    )}
                  </div>
                )}

                <div className="mt-3 grid gap-2">
                  {errors.map((issue) => (
                    <Issue
                      key={issue.code}
                      tone="error"
                      title={issue.code}
                      message={issue.message}
                    />
                  ))}
                  {warnings.map((issue) => (
                    <Issue
                      key={issue.code}
                      tone="warning"
                      title={issue.code}
                      message={issue.message}
                    />
                  ))}
                  {errors.length === 0 && warnings.length === 0 && (
                    <div className="flex gap-2 rounded-xl border border-emerald-100 bg-emerald-50 p-3">
                      <FiCheckCircle
                        className="mt-0.5 shrink-0 text-emerald-600"
                        aria-hidden="true"
                      />
                      <p className="text-xs leading-5 text-emerald-700">
                        No compiler issues for this channel.
                      </p>
                    </div>
                  )}
                </div>

                {plan.requiredCapabilities.length > 0 && (
                  <div className="mt-4">
                    <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                      Required capabilities
                    </p>
                    <div className="mt-2 flex flex-wrap gap-1.5">
                      {plan.requiredCapabilities.map((capability) => (
                        <span
                          key={capability}
                          className="rounded-full bg-slate-100 px-2 py-1 text-[10px] font-semibold text-slate-600"
                        >
                          {pretty(capability)}
                        </span>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>
          </article>
        )
      })}
    </div>
  )
}

function Issue({
  tone,
  title,
  message,
}: {
  tone: 'error' | 'warning'
  title: string
  message: string
}) {
  const error = tone === 'error'
  return (
    <div
      className={`flex gap-2 rounded-xl border p-3 ${error ? 'border-red-100 bg-red-50' : 'border-amber-100 bg-amber-50'}`}
    >
      {error ? (
        <FiAlertCircle className="mt-0.5 shrink-0 text-red-600" aria-hidden="true" />
      ) : (
        <FiInfo className="mt-0.5 shrink-0 text-amber-600" aria-hidden="true" />
      )}
      <div>
        <p className={`text-xs font-semibold ${error ? 'text-red-800' : 'text-amber-800'}`}>
          {pretty(title)}
        </p>
        <p className={`mt-1 text-xs leading-5 ${error ? 'text-red-700' : 'text-amber-700'}`}>
          {message}
        </p>
      </div>
    </div>
  )
}
