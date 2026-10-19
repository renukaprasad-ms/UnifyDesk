import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useEffect, useState } from 'react'
import { FiArrowRight, FiLayers, FiPlus, FiRefreshCw } from 'react-icons/fi'
import { Link } from 'react-router'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type { ReachlyoCampaignSummary } from '../../features/ads/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'
import { TableSkeleton } from '../../components/ui/Skeleton'

const pretty = (value: string) =>
  value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (character) => character.toUpperCase())

const statusClass = (status: string) => {
  if (status === 'ACTIVE' || status === 'PAUSED' || status === 'COMPLETED') return 'bg-emerald-50 text-emerald-700'
  if (status === 'FAILED' || status === 'PARTIAL_FAILURE') return 'bg-red-50 text-red-700'
  if (status === 'DEPLOYING' || status === 'VALIDATING') return 'bg-indigo-50 text-indigo-700'
  return 'bg-slate-100 text-slate-600'
}

export default function ReachlyoCampaigns() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const { can } = useIam()
  const [campaigns, setCampaigns] = useState<ReachlyoCampaignSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [, setError] = useSnackbarState('', 'error')

  const load = async (manual = false) => {
    if (!selectedCompany) return
    if (manual) setRefreshing(true)
    else setLoading(true)
    setError('')
    try {
      const page = await adCenterApi.reachlyoCampaigns(selectedCompany.companyId, 0, 100)
      setCampaigns(page.content)
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load unified campaigns'))
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }

  useEffect(() => {
    void load()
  }, [selectedCompany?.companyId])

  if (!selectedCompany) return null

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Ad Center</p>
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
              Unified campaigns
            </h1>
            <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
              Define the goal, budget, audience and creative once. Reachlyo compiles that campaign into provider-specific plans and launches supported channels in paused state for review.
            </p>
          </div>
          <div className="flex gap-2">
            <button type="button" className="secondary-button inline-flex items-center gap-2" disabled={refreshing} onClick={() => void load(true)}>
              <FiRefreshCw aria-hidden="true" />
              {refreshing ? 'Refreshing…' : 'Refresh'}
            </button>
            {can(PERMISSIONS.ADS_CREATE) && (
              <Link to="/ad-center/unified-campaigns/new" className="primary-button inline-flex items-center gap-2">
                <FiPlus aria-hidden="true" />
                Create unified campaign
              </Link>
            )}
          </div>
        </header>



        <div className="mt-6 grid gap-4 lg:grid-cols-[minmax(0,1fr)_320px]">
          <div className="panel overflow-hidden">
            <div className="border-b border-slate-100 px-5 py-4">
              <h2 className="text-base font-bold text-slate-950">Campaign workspace</h2>
              <p className="mt-1 text-xs text-slate-500">
                These are Reachlyo-owned campaigns. Raw Google and Meta objects remain available under Provider campaigns.
              </p>
            </div>

            {loading ? (
              <div className="p-4"><TableSkeleton rows={7} columns={5} /></div>
            ) : campaigns.length === 0 ? (
              <div className="p-12 text-center">
                <FiLayers className="mx-auto text-3xl text-slate-300" aria-hidden="true" />
                <h3 className="mt-4 text-lg font-bold text-slate-950">Create once, launch across providers</h3>
                <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-slate-500">
                  Start with a business goal instead of a provider form. Reachlyo keeps one canonical campaign and translates it into Google and Meta structures.
                </p>
                {can(PERMISSIONS.ADS_CREATE) && (
                  <Link to="/ad-center/unified-campaigns/new" className="primary-button mt-5 inline-flex items-center gap-2">
                    <FiPlus aria-hidden="true" />
                    Create campaign
                  </Link>
                )}
              </div>
            ) : (
              <div className="divide-y divide-slate-100">
                {campaigns.map((campaign) => (
                  <Link
                    key={campaign.id}
                    to={`/ad-center/unified-campaigns/${campaign.id}`}
                    className="group flex flex-wrap items-center gap-4 px-5 py-4 hover:bg-slate-50"
                  >
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <p className="truncate text-sm font-bold text-slate-950">{campaign.name}</p>
                        <span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${statusClass(campaign.status)}`}>
                          {pretty(campaign.status)}
                        </span>
                      </div>
                      <p className="mt-1 text-xs text-slate-500">
                        {pretty(campaign.objective)} · {pretty(campaign.businessGoal)} · {campaign.currency}
                      </p>
                    </div>
                    <div className="text-right text-xs text-slate-400">
                      <p>Updated {new Date(campaign.updatedAt).toLocaleString()}</p>
                    </div>
                    <FiArrowRight className="text-slate-300 transition group-hover:translate-x-0.5 group-hover:text-slate-500" aria-hidden="true" />
                  </Link>
                ))}
              </div>
            )}
          </div>

          <aside className="panel h-fit p-5">
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Why unified?</p>
            <h2 className="mt-2 text-base font-bold text-slate-950">Reachlyo handles provider translation</h2>
            <div className="mt-4 space-y-4 text-sm leading-6 text-slate-600">
              <p>One campaign brief becomes Google and Meta plans instead of rebuilding the same intent twice.</p>
              <p>Provider-specific requirements stay visible during review instead of leaking into every earlier step.</p>
              <p>Launch uses a persistent Saga, creates supported resources paused, and can retry only failed steps.</p>
            </div>
            <Link to="/ad-center/campaigns" className="secondary-button mt-5 inline-flex w-full justify-center">
              Open provider campaigns
            </Link>
          </aside>
        </div>
      </div>
    </section>
  )
}
