import { useEffect, useMemo, useState } from 'react'
import { FiActivity, FiChevronDown, FiChevronUp, FiRefreshCw } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type { AdAccount, AdActivityLog, PageResponse } from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'
import { ListSkeleton } from '../../components/ui/Skeleton'

const pretty = (value: string) =>
  value.replaceAll('_', ' ').replace(/\b\w/g, (character) => character.toUpperCase())

const safeJson = (value?: string | null) => {
  if (!value) return null
  try {
    return JSON.parse(value) as unknown
  } catch {
    return value
  }
}

const formatPayload = (value?: string | null) => {
  const parsed = safeJson(value)
  if (parsed == null) return null
  return typeof parsed === 'string' ? parsed : JSON.stringify(parsed, null, 2)
}

export default function AdActivity() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const [accounts, setAccounts] = useState<AdAccount[]>([])
  const [accountId, setAccountId] = useState('')
  const [pageData, setPageData] = useState<PageResponse<AdActivityLog> | null>(null)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [error, setError] = useState('')

  const accountById = useMemo(
    () =>
      accounts.reduce<Record<string, AdAccount>>((map, account) => {
        map[account.id] = account
        return map
      }, {}),
    [accounts],
  )

  const load = async (nextPage = page, nextAccountId = accountId) => {
    if (!selectedCompany) return
    setLoading(true)
    setError('')
    try {
      const [accountData, activityData] = await Promise.all([
        adCenterApi.accounts(selectedCompany.companyId),
        adCenterApi.activity(selectedCompany.companyId, nextAccountId || undefined, nextPage, 30),
      ])
      setAccounts(accountData)
      setPageData(activityData)
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load Ad Center activity'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    setPage(0)
    void load(0, '')
  }, [selectedCompany?.companyId])

  const chooseAccount = async (value: string) => {
    setAccountId(value)
    setPage(0)
    setExpandedId(null)
    await load(0, value)
  }

  const changePage = async (nextPage: number) => {
    setPage(nextPage)
    setExpandedId(null)
    await load(nextPage)
  }

  if (!selectedCompany) return null

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Ad Center</p>
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
              Activity
            </h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
              Audit provider-affecting Ad Center changes with actor, account, entity, and
              before/after state.
            </p>
          </div>

          <button
            type="button"
            className="secondary-button inline-flex items-center gap-2"
            disabled={loading}
            onClick={() => void load()}
          >
            <FiRefreshCw aria-hidden="true" />
            Refresh
          </button>
        </header>

        {error && (
          <div className="mt-6 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
            {error}
          </div>
        )}

        <div className="mt-6 max-w-sm">
          <label className="text-xs font-semibold text-slate-700">
            Ad account
            <select
              className="form-input mt-2"
              value={accountId}
              onChange={(event) => void chooseAccount(event.target.value)}
            >
              <option value="">All ad accounts</option>
              {accounts.map((account) => (
                <option key={account.id} value={account.id}>
                  {account.name}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div className="panel mt-6 overflow-hidden">
          <div className="border-b border-slate-100 px-5 py-4">
            <div className="flex items-center gap-2">
              <FiActivity className="text-indigo-500" aria-hidden="true" />
              <h2 className="text-base font-bold text-slate-950">Audit history</h2>
            </div>
            <p className="mt-1 text-xs text-slate-500">
              New Ad Center mutation flows can reuse this same activity stream.
            </p>
          </div>

          {loading ? (
            <div className="p-4"><ListSkeleton rows={7} /></div>
          ) : !pageData || pageData.content.length === 0 ? (
            <div className="p-10 text-center">
              <FiActivity className="mx-auto text-3xl text-slate-300" aria-hidden="true" />
              <h3 className="mt-3 text-base font-bold text-slate-950">No activity recorded yet</h3>
              <p className="mt-2 text-sm text-slate-500">
                Provider-affecting mutations such as Meta account spend-cap changes will appear
                here.
              </p>
            </div>
          ) : (
            <>
              <div className="divide-y divide-slate-100">
                {pageData.content.map((item) => {
                  const expanded = expandedId === item.id
                  const account = item.adAccountId ? accountById[item.adAccountId] : undefined
                  const before = formatPayload(item.beforeJson)
                  const after = formatPayload(item.afterJson)
                  const metadata = formatPayload(item.metadataJson)
                  const hasDetails = before || after || metadata

                  return (
                    <article key={item.id}>
                      <button
                        type="button"
                        className="flex w-full items-start justify-between gap-4 px-5 py-4 text-left hover:bg-slate-50"
                        onClick={() =>
                          hasDetails &&
                          setExpandedId((current) => (current === item.id ? null : item.id))
                        }
                      >
                        <div className="min-w-0">
                          <div className="flex flex-wrap items-center gap-2">
                            <p className="font-semibold text-slate-950">{pretty(item.action)}</p>
                            <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-semibold text-slate-600">
                              {item.provider === 'META_ADS' ? 'Meta Ads' : 'Google Ads'}
                            </span>
                          </div>
                          <p className="mt-1 text-sm text-slate-500">
                            {account?.name || item.adAccountId || 'Workspace'}
                            {' · '}
                            {pretty(item.entityType)}
                            {item.entityId ? ` · ${item.entityId}` : ''}
                          </p>
                          <p className="mt-2 text-xs text-slate-400">
                            {new Date(item.createdAt).toLocaleString()}
                            {item.actorUserId ? ` · Actor ${item.actorUserId}` : ''}
                          </p>
                        </div>

                        {hasDetails && (
                          <span className="mt-1 shrink-0 text-slate-400">
                            {expanded ? <FiChevronUp /> : <FiChevronDown />}
                          </span>
                        )}
                      </button>

                      {expanded && hasDetails && (
                        <div className="border-t border-slate-100 bg-slate-50/60 px-5 py-5">
                          <div className="grid gap-4 xl:grid-cols-3">
                            {before && (
                              <div>
                                <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                                  Before
                                </p>
                                <pre className="mt-2 max-h-80 overflow-auto rounded-xl border border-slate-200 bg-white p-3 text-xs leading-5 text-slate-600">
                                  {before}
                                </pre>
                              </div>
                            )}
                            {after && (
                              <div>
                                <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                                  After
                                </p>
                                <pre className="mt-2 max-h-80 overflow-auto rounded-xl border border-slate-200 bg-white p-3 text-xs leading-5 text-slate-600">
                                  {after}
                                </pre>
                              </div>
                            )}
                            {metadata && (
                              <div>
                                <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                                  Metadata
                                </p>
                                <pre className="mt-2 max-h-80 overflow-auto rounded-xl border border-slate-200 bg-white p-3 text-xs leading-5 text-slate-600">
                                  {metadata}
                                </pre>
                              </div>
                            )}
                          </div>
                        </div>
                      )}
                    </article>
                  )
                })}
              </div>

              <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 px-5 py-4 text-xs text-slate-500">
                <span>
                  {pageData.totalElements} event{pageData.totalElements === 1 ? '' : 's'}
                  {' · '}
                  Page {pageData.number + 1} of {Math.max(1, pageData.totalPages)}
                </span>
                <div className="flex gap-2">
                  <button
                    type="button"
                    className="secondary-button px-3 py-2 text-xs"
                    disabled={pageData.first}
                    onClick={() => void changePage(pageData.number - 1)}
                  >
                    Previous
                  </button>
                  <button
                    type="button"
                    className="secondary-button px-3 py-2 text-xs"
                    disabled={pageData.last}
                    onClick={() => void changePage(pageData.number + 1)}
                  >
                    Next
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </section>
  )
}
