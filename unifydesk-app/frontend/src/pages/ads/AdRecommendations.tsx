import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import AdFeedbackAlert from '../../components/ads/AdFeedbackAlert'
import {
  FiAlertTriangle,
  FiCheckCircle,
  FiInfo,
  FiRefreshCw,
  FiTrendingUp,
  FiZap,
} from 'react-icons/fi'
import { Link } from 'react-router'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdAccount,
  AdRecommendationItem,
  AdRecommendationSeverity,
  AdRecommendations as AdRecommendationsData,
  GoogleNativeRecommendation,
} from '../../features/ads/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'

type Filter = 'ALL' | 'HIDDEN' | AdRecommendationSeverity

const isoDate = (date: Date) => {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 10)
}

const lastSevenDays = () => {
  const to = new Date()
  const from = new Date()
  from.setDate(from.getDate() - 6)
  return { from: isoDate(from), to: isoDate(to) }
}

const pretty = (value: string) =>
  value
    .replaceAll('_', ' ')
    .replace(/\b\w/g, (character) => character.toUpperCase())

const severityTone: Record<
  AdRecommendationSeverity,
  { badge: string; border: string; icon: ReactNode }
> = {
  CRITICAL: {
    badge: 'bg-red-50 text-red-700',
    border: 'border-red-200',
    icon: <FiAlertTriangle className="text-red-600" />,
  },
  WARNING: {
    badge: 'bg-amber-50 text-amber-700',
    border: 'border-amber-200',
    icon: <FiAlertTriangle className="text-amber-600" />,
  },
  OPPORTUNITY: {
    badge: 'bg-indigo-50 text-indigo-700',
    border: 'border-indigo-200',
    icon: <FiTrendingUp className="text-indigo-600" />,
  },
  INFO: {
    badge: 'bg-slate-100 text-slate-600',
    border: 'border-slate-200',
    icon: <FiInfo className="text-slate-500" />,
  },
}

const freshness = (value?: string | null) =>
  value ? new Date(value).toLocaleString() : 'Not synchronized'

const snoozeUntilTomorrow = () =>
  new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString()

const emptyRecommendations = (accountId: string): AdRecommendationsData => ({
  adAccountId: accountId,
  criticalCount: 0,
  warningCount: 0,
  opportunityCount: 0,
  infoCount: 0,
  dismissedCount: 0,
  snoozedCount: 0,
  generatedAt: new Date().toISOString(),
  items: [],
  suppressedItems: [],
})

function RecommendationCard({
  item,
  canModify,
  working,
  onDismiss,
  onSnooze,
  onReopen,
}: {
  item: AdRecommendationItem
  canModify: boolean
  working: boolean
  onDismiss: () => void
  onSnooze: () => void
  onReopen: () => void
}) {
  const tone = severityTone[item.severity]

  return (
    <article className={`rounded-2xl border bg-white p-5 shadow-sm ${tone.border}`}>
      <div className="flex items-start gap-3">
        <span className="mt-0.5 shrink-0 rounded-xl bg-white p-2 shadow-sm">
          {tone.icon}
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${tone.badge}`}>
              {pretty(item.severity)}
            </span>
            <span className="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-semibold text-slate-600">
              {pretty(item.category)}
            </span>
            <span className="rounded-full border border-slate-200 px-2.5 py-1 text-[11px] font-semibold text-slate-500">
              {item.source === 'META_PROVIDER'
                ? 'Meta'
                : item.source === 'GOOGLE_PROVIDER'
                  ? 'Google Ads'
                  : 'Reachlyo diagnostic'}
            </span>
          </div>

          <h3 className="mt-3 text-base font-bold text-slate-950">{item.title}</h3>
          <p className="mt-2 text-sm leading-6 text-slate-500">{item.description}</p>

          {Object.keys(item.evidence ?? {}).length > 0 && (
            <div className="mt-4 flex flex-wrap gap-2">
              {Object.entries(item.evidence).map(([key, value]) => (
                <span
                  key={key}
                  className="rounded-lg bg-slate-50 px-2.5 py-1.5 text-[11px] font-medium text-slate-500"
                >
                  {pretty(key)}: {String(value)}
                </span>
              ))}
            </div>
          )}

          {item.lifecycleStatus === 'SNOOZED' && item.snoozedUntil && (
            <p className="mt-4 text-xs font-medium text-amber-700">
              Snoozed until {new Date(item.snoozedUntil).toLocaleString()}
            </p>
          )}

          <div className="mt-4 flex flex-wrap items-center gap-2">
            {item.actionPath && item.actionLabel && (
              <Link
                to={item.actionPath}
                className="text-sm font-semibold text-indigo-600 hover:text-indigo-700"
              >
                {item.actionLabel} →
              </Link>
            )}

            {canModify && item.lifecycleStatus === 'ACTIVE' && (
              <>
                {item.dismissible && (
                  <button
                    type="button"
                    className="secondary-button px-3 py-2 text-xs"
                    disabled={working}
                    onClick={onDismiss}
                  >
                    {working ? 'Saving…' : 'Dismiss'}
                  </button>
                )}
                {item.snoozable && (
                  <button
                    type="button"
                    className="secondary-button px-3 py-2 text-xs"
                    disabled={working}
                    onClick={onSnooze}
                  >
                    {working ? 'Saving…' : 'Snooze 24h'}
                  </button>
                )}
              </>
            )}

            {canModify && item.lifecycleStatus !== 'ACTIVE' && (
              <button
                type="button"
                className="secondary-button px-3 py-2 text-xs"
                disabled={working}
                onClick={onReopen}
              >
                {working ? 'Saving…' : 'Reopen'}
              </button>
            )}
          </div>
        </div>
      </div>
    </article>
  )
}

export default function AdRecommendations() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const { can } = useIam()

  const [accounts, setAccounts] = useState<AdAccount[]>([])
  const [selectedAccountId, setSelectedAccountId] = useState('')
  const [recommendations, setRecommendations] =
    useState<AdRecommendationsData | null>(null)
  const [filter, setFilter] = useState<Filter>('ALL')
  const [loading, setLoading] = useState(true)
  const [syncing, setSyncing] = useState(false)
  const [workingKey, setWorkingKey] = useState<string | null>(null)
  const [nativeRecommendations, setNativeRecommendations] = useState<
    GoogleNativeRecommendation[]
  >([])
  const [nativeWorking, setNativeWorking] = useState<string | null>(null)
  const [error, setError] = useSnackbarState('', 'error')
  const [notice, setNotice] = useSnackbarState('', 'success')

  const managedAccounts = useMemo(
    () =>
      accounts.filter((account) => account.managed && account.accessible),
    [accounts],
  )

  const selectedAccount =
    managedAccounts.find((account) => account.id === selectedAccountId) ??
    managedAccounts.find((account) => account.primaryAccount) ??
    managedAccounts[0] ??
    null

  const visibleItems = useMemo(() => {
    if (!recommendations) return []
    if (filter === 'HIDDEN') return recommendations.suppressedItems ?? []
    const items = recommendations.items ?? []
    return filter === 'ALL'
      ? items
      : items.filter((item) => item.severity === filter)
  }, [filter, recommendations])

  const loadRecommendations = async (accountId: string) => {
    if (!selectedCompany) return
    const data = await adCenterApi.recommendations(
      selectedCompany.companyId,
      accountId,
    )
    setRecommendations(data)
  }

  const loadNativeRecommendations = async (account: AdAccount) => {
    if (!selectedCompany || account.provider !== 'GOOGLE_ADS') {
      setNativeRecommendations([])
      return
    }
    setNativeRecommendations(
      await adCenterApi.googleNativeRecommendations(
        selectedCompany.companyId,
        account.id,
      ),
    )
  }

  const load = async () => {
    if (!selectedCompany) return
    setLoading(true)
    setError('')
    try {
      const accountData = await adCenterApi.accounts(selectedCompany.companyId)
      setAccounts(accountData)
      const eligible = accountData.filter(
        (account) => account.managed && account.accessible,
      )
      const requestedAccountId = new URLSearchParams(
        window.location.search,
      ).get('accountId')
      const preferred =
        eligible.find((account) => account.id === requestedAccountId) ??
        eligible.find((account) => account.primaryAccount) ??
        eligible[0]
      if (preferred) {
        setSelectedAccountId(preferred.id)
        await Promise.all([
          loadRecommendations(preferred.id),
          loadNativeRecommendations(preferred),
        ])
      } else {
        setRecommendations(null)
      }
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load recommendations'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [selectedCompany?.companyId])

  const chooseAccount = async (accountId: string) => {
    setSelectedAccountId(accountId)
    const url = new URL(window.location.href)
    url.searchParams.set('accountId', accountId)
    window.history.replaceState(
      {},
      '',
      `${url.pathname}${url.search}${url.hash}`,
    )
    setFilter('ALL')
    setNotice('')
    setError('')
    setLoading(true)
    try {
      const account = managedAccounts.find((item) => item.id === accountId)
      await Promise.all([
        loadRecommendations(accountId),
        account ? loadNativeRecommendations(account) : Promise.resolve(),
      ])
    } catch (loadError) {
      setRecommendations(emptyRecommendations(accountId))
      setError(apiErrorMessage(loadError, 'Unable to load recommendations'))
    } finally {
      setLoading(false)
    }
  }

  const updateLifecycle = async (
    item: AdRecommendationItem,
    action: 'DISMISS' | 'SNOOZE' | 'REOPEN',
  ) => {
    if (!selectedCompany || !selectedAccount) return

    setWorkingKey(item.key)
    setError('')
    setNotice('')
    try {
      const snoozedUntil =
        action === 'SNOOZE' ? snoozeUntilTomorrow() : undefined
      const updated = await adCenterApi.updateRecommendationState(
        selectedCompany.companyId,
        selectedAccount.id,
        item.key,
        action,
        snoozedUntil,
      )
      setRecommendations(updated)
      setNotice(
        action === 'DISMISS'
          ? 'Recommendation dismissed.'
          : action === 'SNOOZE'
            ? 'Recommendation snoozed for 24 hours.'
            : 'Recommendation reopened.',
      )
      window.dispatchEvent(new Event('ad-center:recommendations-changed'))
    } catch (updateError) {
      setError(
        apiErrorMessage(updateError, 'Unable to update recommendation state'),
      )
    } finally {
      setWorkingKey(null)
    }
  }

  const actOnNativeRecommendation = async (
    recommendation: GoogleNativeRecommendation,
    action: 'APPLY' | 'DISMISS',
  ) => {
    if (!selectedCompany || !selectedAccount) return
    setNativeWorking(recommendation.resourceName)
    setError('')
    setNotice('')
    try {
      const result =
        action === 'APPLY'
          ? await adCenterApi.applyGoogleNativeRecommendations(
              selectedCompany.companyId,
              selectedAccount.id,
              [recommendation.resourceName],
            )
          : await adCenterApi.dismissGoogleNativeRecommendations(
              selectedCompany.companyId,
              selectedAccount.id,
              [recommendation.resourceName],
            )
      await loadNativeRecommendations(selectedAccount)
      setNotice(
        `${result.processedCount} Google Ads recommendation(s) ${action === 'APPLY' ? 'applied' : 'dismissed'}.`,
      )
    } catch (actionError) {
      setError(
        apiErrorMessage(
          actionError,
          `Unable to ${action.toLowerCase()} Google Ads recommendation`,
        ),
      )
    } finally {
      setNativeWorking(null)
    }
  }

  const syncSignals = async () => {
    if (!selectedCompany || !selectedAccount) return

    setSyncing(true)
    setError('')
    setNotice('')

    const range = lastSevenDays()
    const tasks =
      selectedAccount.provider === 'GOOGLE_ADS'
        ? [
            {
              name: 'campaigns',
              run: () =>
                adCenterApi.syncGoogleCampaigns(
                  selectedCompany.companyId,
                  selectedAccount.id,
                ),
            },
            {
              name: 'account health',
              run: () =>
                adCenterApi.syncGoogleHealth(
                  selectedCompany.companyId,
                  selectedAccount.id,
                ),
            },
            {
              name: 'billing',
              run: () =>
                adCenterApi.syncGoogleBilling(
                  selectedCompany.companyId,
                  selectedAccount.id,
                ),
            },
            {
              name: 'audiences',
              run: () =>
                adCenterApi.syncGoogleAudiences(
                  selectedCompany.companyId,
                  selectedAccount.id,
                ),
            },
            {
              name: 'conversions',
              run: () =>
                adCenterApi.syncGoogleConversions(
                  selectedCompany.companyId,
                  selectedAccount.id,
                ),
            },
            {
              name: 'analytics',
              run: () =>
                adCenterApi.syncGoogleInsights(
                  selectedCompany.companyId,
                  selectedAccount.id,
                  range.from,
                  range.to,
                ),
            },
          ]
        : [
            {
              name: 'campaigns',
              run: () =>
                adCenterApi.syncMetaCampaigns(
                  selectedCompany.companyId,
                  selectedAccount.id,
                ),
            },
            {
              name: 'account health',
              run: () =>
                adCenterApi.syncMetaHealth(
                  selectedCompany.companyId,
                  selectedAccount.id,
                ),
            },
            {
              name: 'billing',
              run: () =>
                adCenterApi.syncMetaBilling(
                  selectedCompany.companyId,
                  selectedAccount.id,
                ),
            },
            {
              name: 'resources',
              run: () =>
                adCenterApi.syncMetaResources(
                  selectedCompany.companyId,
                  selectedAccount.id,
                ),
            },
            {
              name: 'conversions',
              run: () =>
                adCenterApi.syncMetaConversions(
                  selectedCompany.companyId,
                  selectedAccount.id,
                ),
            },
            {
              name: 'analytics',
              run: () =>
                adCenterApi.syncMetaInsights(
                  selectedCompany.companyId,
                  selectedAccount.id,
                  range.from,
                  range.to,
                ),
            },
          ]

    try {
      const results = await Promise.allSettled(tasks.map((task) => task.run()))
      const failed = results
        .map((result, index) =>
          result.status === 'rejected' ? tasks[index].name : null,
        )
        .filter((name): name is string => Boolean(name))

      const updated = await adCenterApi.reconcileRecommendations(
        selectedCompany.companyId,
        selectedAccount.id,
      )
      setRecommendations(updated)
      if (selectedAccount.provider === 'GOOGLE_ADS') {
        await loadNativeRecommendations(selectedAccount)
      }
      window.dispatchEvent(new Event('ad-center:recommendations-changed'))

      setNotice(
        failed.length === 0
          ? `${selectedAccount.provider === 'GOOGLE_ADS' ? 'Google Ads' : 'Meta'} account, delivery, billing, tracking, and seven-day performance signals synchronized and recommendation lifecycle reconciled.`
          : `Recommendations refreshed and reconciled. ${failed.length} signal source(s) could not be synchronized: ${failed.join(', ')}.`,
      )
    } catch (syncError) {
      setError(
        apiErrorMessage(syncError, 'Unable to refresh recommendation signals'),
      )
    } finally {
      setSyncing(false)
    }
  }

  if (!selectedCompany) return null

  const filters: Array<{ value: Filter; label: string; count: number }> = [
    {
      value: 'ALL',
      label: 'All',
      count: recommendations?.items.length ?? 0,
    },
    {
      value: 'CRITICAL',
      label: 'Critical',
      count: recommendations?.criticalCount ?? 0,
    },
    {
      value: 'WARNING',
      label: 'Warnings',
      count: recommendations?.warningCount ?? 0,
    },
    {
      value: 'OPPORTUNITY',
      label: 'Opportunities',
      count: recommendations?.opportunityCount ?? 0,
    },
    {
      value: 'INFO',
      label: 'Info',
      count: recommendations?.infoCount ?? 0,
    },
    {
      value: 'HIDDEN',
      label: 'Hidden',
      count:
        (recommendations?.dismissedCount ?? 0) +
        (recommendations?.snoozedCount ?? 0),
    },
  ]

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Ad Center</p>
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
              Recommendations
            </h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
              Prioritize provider delivery issues, account risks, tracking gaps, billing limits, and provable campaign-performance problems.
            </p>
          </div>

          {can(PERMISSIONS.ADS_MODIFY) && (
            <button
              type="button"
              className="primary-button inline-flex items-center gap-2"
              disabled={!selectedAccount || syncing}
              onClick={() => void syncSignals()}
            >
              <FiRefreshCw aria-hidden="true" />
              {syncing ? 'Syncing signals…' : 'Sync signals'}
            </button>
          )}
        </header>
        <AdFeedbackAlert error={error} notice={notice} />

        <div className="mt-6 max-w-sm">
          <label className="text-xs font-semibold text-slate-700">
            Managed ad account
            <select
              className="form-input mt-2"
              value={selectedAccount?.id ?? ''}
              disabled={!managedAccounts.length}
              onChange={(event) => void chooseAccount(event.target.value)}
            >
              {managedAccounts.length === 0 ? (
                <option value="">No managed ad account</option>
              ) : (
                managedAccounts.map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.name}
                  </option>
                ))
              )}
            </select>
          </label>
        </div>

        {managedAccounts.length === 0 ? (
          <div className="panel mt-6 p-10 text-center text-sm text-slate-500">
            Connect and manage an ad account before using recommendations.
          </div>
        ) : loading ? (
          <div className="panel mt-6 p-10 text-center text-sm text-slate-500">
            Evaluating account signals…
          </div>
        ) : recommendations ? (
          <>
            <div className="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
              <article className="panel p-5">
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                  {selectedAccount?.provider === 'GOOGLE_ADS' ? 'Google optimization score' : 'Meta opportunity score'}
                </p>
                <p className="mt-2 text-2xl font-bold text-slate-950">
                  {recommendations.metaOpportunityScore ?? '—'}
                </p>
                <p className="mt-2 text-xs text-slate-500">
                  Provider-reported score; Reachlyo does not re-score it.
                </p>
              </article>

              {[
                {
                  label: 'Critical',
                  value: recommendations.criticalCount,
                  className: 'text-red-700',
                },
                {
                  label: 'Warnings',
                  value: recommendations.warningCount,
                  className: 'text-amber-700',
                },
                {
                  label: 'Opportunities',
                  value: recommendations.opportunityCount,
                  className: 'text-indigo-700',
                },
                {
                  label: 'Info',
                  value: recommendations.infoCount,
                  className: 'text-slate-700',
                },
              ].map((card) => (
                <article key={card.label} className="panel p-5">
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                    {card.label}
                  </p>
                  <p className={`mt-2 text-2xl font-bold ${card.className}`}>
                    {card.value}
                  </p>
                  <p className="mt-2 text-xs text-slate-500">
                    Current diagnostic item{card.value === 1 ? '' : 's'}
                  </p>
                </article>
              ))}
            </div>

            <div className="panel mt-6 p-5">
              <div className="flex items-start gap-3">
                <FiZap className="mt-0.5 shrink-0 text-indigo-500" aria-hidden="true" />
                <div>
                  <h2 className="text-sm font-bold text-slate-950">
                    Signal freshness
                  </h2>
                  <div className="mt-3 grid gap-x-8 gap-y-2 text-xs text-slate-500 sm:grid-cols-3">
                    <p>
                      <span className="font-semibold text-slate-700">Health:</span>{' '}
                      {freshness(recommendations.healthLastSyncAt)}
                    </p>
                    <p>
                      <span className="font-semibold text-slate-700">Billing:</span>{' '}
                      {freshness(recommendations.billingLastSyncAt)}
                    </p>
                    <p>
                      <span className="font-semibold text-slate-700">Analytics:</span>{' '}
                      {freshness(recommendations.analyticsLastSyncAt)}
                    </p>
                  </div>
                </div>
              </div>
            </div>

            {selectedAccount?.provider === 'GOOGLE_ADS' && (
              <div className="panel mt-6 overflow-hidden">
                <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
                  <div>
                    <h2 className="text-base font-bold text-slate-950">
                      Native Google Ads recommendations
                    </h2>
                    <p className="mt-1 text-xs text-slate-500">
                      These come directly from Google RecommendationService. Apply changes only after reviewing the recommendation type and affected campaign.
                    </p>
                  </div>
                  <span className="rounded-full bg-indigo-50 px-2.5 py-1 text-xs font-semibold text-indigo-700">
                    {nativeRecommendations.length} active
                  </span>
                </div>

                {nativeRecommendations.length === 0 ? (
                  <div className="p-8 text-center text-sm text-slate-500">
                    Google did not return any active native recommendations for this account.
                  </div>
                ) : (
                  <div className="divide-y divide-slate-100">
                    {nativeRecommendations.map((recommendation) => (
                      <div
                        key={recommendation.resourceName}
                        className="flex flex-wrap items-start justify-between gap-4 px-5 py-4"
                      >
                        <div className="min-w-0 flex-1">
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="rounded-full bg-indigo-50 px-2.5 py-1 text-xs font-semibold text-indigo-700">
                              {pretty(recommendation.type)}
                            </span>
                            {recommendation.campaignResourceName && (
                              <span className="max-w-full truncate rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-500">
                                {recommendation.campaignResourceName.split('/').at(-1)}
                              </span>
                            )}
                          </div>
                          {recommendation.impactJson && (
                            <details className="mt-3">
                              <summary className="cursor-pointer text-xs font-semibold text-slate-500">
                                Provider impact details
                              </summary>
                              <pre className="mt-2 max-h-40 overflow-auto rounded-lg bg-slate-950 p-3 text-[11px] leading-5 text-slate-200">
                                {recommendation.impactJson}
                              </pre>
                            </details>
                          )}
                        </div>

                        {can(PERMISSIONS.ADS_MODIFY) && (
                          <div className="flex gap-2">
                            <button
                              type="button"
                              className="primary-button px-3 py-2 text-xs"
                              disabled={nativeWorking !== null}
                              onClick={() =>
                                void actOnNativeRecommendation(
                                  recommendation,
                                  'APPLY',
                                )
                              }
                            >
                              {nativeWorking === recommendation.resourceName
                                ? 'Working…'
                                : 'Apply in Google'}
                            </button>
                            <button
                              type="button"
                              className="secondary-button px-3 py-2 text-xs"
                              disabled={nativeWorking !== null}
                              onClick={() =>
                                void actOnNativeRecommendation(
                                  recommendation,
                                  'DISMISS',
                                )
                              }
                            >
                              Dismiss in Google
                            </button>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            <div className="mt-6 flex gap-2 overflow-x-auto pb-1">
              {filters.map((item) => (
                <button
                  key={item.value}
                  type="button"
                  className={`shrink-0 rounded-xl px-3 py-2 text-xs font-semibold transition ${
                    filter === item.value
                      ? 'bg-slate-950 text-white'
                      : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                  }`}
                  onClick={() => setFilter(item.value)}
                >
                  {item.label} · {item.count}
                </button>
              ))}
            </div>

            {visibleItems.length === 0 ? (
              <div className="panel mt-6 p-10 text-center">
                <FiCheckCircle
                  className="mx-auto text-4xl text-emerald-500"
                  aria-hidden="true"
                />
                <h2 className="mt-3 text-lg font-bold text-slate-950">
                  No matching issues
                </h2>
                <p className="mt-2 text-sm text-slate-500">
                  No {filter === 'ALL' ? 'current diagnostic' : filter === 'HIDDEN' ? 'hidden recommendation' : filter.toLowerCase()} items were found from the synchronized signals.
                </p>
              </div>
            ) : (
              <div className="mt-6 grid gap-4 xl:grid-cols-2">
                {visibleItems.map((item) => (
                  <RecommendationCard
                    key={item.key}
                    item={item}
                    canModify={can(PERMISSIONS.ADS_MODIFY)}
                    working={workingKey === item.key}
                    onDismiss={() => void updateLifecycle(item, 'DISMISS')}
                    onSnooze={() => void updateLifecycle(item, 'SNOOZE')}
                    onReopen={() => void updateLifecycle(item, 'REOPEN')}
                  />
                ))}
              </div>
            )}
          </>
        ) : null}
      </div>
    </section>
  )
}
