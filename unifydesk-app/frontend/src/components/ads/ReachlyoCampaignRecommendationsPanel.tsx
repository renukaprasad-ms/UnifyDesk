import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useMemo, useState } from 'react'
import { FiAlertTriangle, FiArrowRight, FiInfo, FiRefreshCw, FiTrendingUp } from 'react-icons/fi'
import { Link } from 'react-router'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdRecommendationSeverity,
  ReachlyoCampaignRecommendations,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'

type Props = {
  companyId: string
  campaignId: string
}

const pretty = (value?: string | null) =>
  value
    ? value
        .replaceAll('_', ' ')
        .toLowerCase()
        .replace(/\b\w/g, (character) => character.toUpperCase())
    : 'Unknown'

const severityClass: Record<AdRecommendationSeverity, string> = {
  CRITICAL: 'border-red-200 bg-red-50 text-red-800',
  WARNING: 'border-amber-200 bg-amber-50 text-amber-800',
  OPPORTUNITY: 'border-indigo-200 bg-indigo-50 text-indigo-800',
  INFO: 'border-slate-200 bg-slate-50 text-slate-700',
}

const severityIcon = (severity: AdRecommendationSeverity) => {
  if (severity === 'CRITICAL' || severity === 'WARNING') {
    return <FiAlertTriangle aria-hidden="true" />
  }
  if (severity === 'OPPORTUNITY') {
    return <FiTrendingUp aria-hidden="true" />
  }
  return <FiInfo aria-hidden="true" />
}

export default function ReachlyoCampaignRecommendationsPanel({ companyId, campaignId }: Props) {
  const [data, setData] = useState<ReachlyoCampaignRecommendations | null>(null)
  const [loading, setLoading] = useState(true)
  const [, setError] = useSnackbarState('', 'error')
  const [filter, setFilter] = useState<'ALL' | AdRecommendationSeverity>('ALL')

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      setData(await adCenterApi.reachlyoCampaignRecommendations(companyId, campaignId))
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load campaign recommendations'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [companyId, campaignId])

  const visible = useMemo(() => {
    const items = data?.items ?? []
    return filter === 'ALL' ? items : items.filter((item) => item.severity === filter)
  }, [data?.items, filter])

  return (
    <section className="panel overflow-hidden">
      <div className="flex flex-wrap items-start justify-between gap-4 border-b border-slate-100 px-5 py-4">
        <div>
          <h3 className="text-base font-bold text-slate-950">Campaign recommendations</h3>
          <p className="mt-1 max-w-2xl text-xs leading-5 text-slate-500">
            Combines provider diagnostics, provider-native campaign recommendations, deployment
            state, and cross-channel performance evidence. Nothing is changed automatically.
          </p>
        </div>
        <button
          type="button"
          className="secondary-button inline-flex items-center gap-2"
          disabled={loading}
          onClick={() => void load()}
        >
          <FiRefreshCw className={loading ? 'animate-spin' : ''} aria-hidden="true" />
          {loading ? 'Loading…' : 'Refresh'}
        </button>
      </div>



      {data && (
        <div className="border-b border-slate-100 px-5 py-4">
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <CountCard label="Critical" value={data.criticalCount} />
            <CountCard label="Warnings" value={data.warningCount} />
            <CountCard label="Opportunities" value={data.opportunityCount} />
            <CountCard label="Info" value={data.infoCount} />
          </div>

          <div className="mt-4 flex flex-wrap items-center gap-2">
            {(['ALL', 'CRITICAL', 'WARNING', 'OPPORTUNITY', 'INFO'] as const).map((value) => (
              <button
                key={value}
                type="button"
                className={
                  'rounded-full px-3 py-1.5 text-xs font-semibold ' +
                  (filter === value
                    ? 'bg-slate-900 text-white'
                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200')
                }
                onClick={() => setFilter(value)}
              >
                {pretty(value)}
              </button>
            ))}
          </div>
        </div>
      )}

      {loading ? (
        <div className="p-8 text-center text-sm text-slate-500">
          Evaluating campaign recommendations…
        </div>
      ) : visible.length === 0 ? (
        <div className="p-8 text-center text-sm text-slate-500">
          No recommendations match this filter. Recommendations will appear as provider diagnostics
          and campaign performance evidence become available.
        </div>
      ) : (
        <div className="grid gap-3 p-5">
          {visible.map((item) => (
            <article
              key={item.key}
              className={'rounded-xl border p-4 ' + severityClass[item.severity]}
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="flex min-w-0 gap-3">
                  <div className="mt-0.5 shrink-0 text-base">{severityIcon(item.severity)}</div>
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <h4 className="font-bold">{item.title}</h4>
                      <span className="rounded-full bg-white/70 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide">
                        {pretty(item.severity)}
                      </span>
                      {item.provider && (
                        <span className="rounded-full bg-white/70 px-2 py-0.5 text-[10px] font-bold">
                          {pretty(item.provider)}
                        </span>
                      )}
                    </div>
                    <p className="mt-1 text-xs leading-5 opacity-90">{item.description}</p>

                    {Object.keys(item.evidence ?? {}).length > 0 && (
                      <div className="mt-3 flex flex-wrap gap-2">
                        {Object.entries(item.evidence)
                          .filter(([, value]) => ['string', 'number'].includes(typeof value))
                          .slice(0, 5)
                          .map(([key, value]) => (
                            <span
                              key={key}
                              className="rounded-lg bg-white/70 px-2 py-1 text-[10px] font-semibold"
                            >
                              {pretty(key)}: {String(value)}
                            </span>
                          ))}
                      </div>
                    )}
                  </div>
                </div>

                {item.actionLabel && item.actionPath && (
                  <Link
                    to={item.actionPath}
                    className="secondary-button inline-flex shrink-0 items-center gap-1 bg-white"
                  >
                    {item.actionLabel}
                    <FiArrowRight aria-hidden="true" />
                  </Link>
                )}
              </div>
            </article>
          ))}
        </div>
      )}

      {data?.analyticsLastSyncAt && (
        <div className="border-t border-slate-100 px-5 py-3 text-[11px] text-slate-400">
          Performance evidence last synced {new Date(data.analyticsLastSyncAt).toLocaleString()}
        </div>
      )}
    </section>
  )
}

function CountCard({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white px-3 py-2.5">
      <p className="text-[10px] font-semibold uppercase tracking-wide text-slate-400">{label}</p>
      <p className="mt-1 text-lg font-bold text-slate-900">{value}</p>
    </div>
  )
}
