import { useEffect, useMemo, useState } from 'react'
import { FiFilter, FiPlus, FiRefreshCw, FiSlash } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdCampaign,
  GoogleNegativeKeywordInput,
  GoogleSearchTermReport,
  GoogleSearchTermRow,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { TableSkeleton } from '../ui/Skeleton'

type Props = {
  companyId: string
  campaign: AdCampaign
  canCreate: boolean
  canModify: boolean
}

const MATCH_TYPES: GoogleNegativeKeywordInput['matchType'][] = [
  'EXACT',
  'PHRASE',
  'BROAD',
]

const isoDate = (date: Date) => {
  const offset = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offset).toISOString().slice(0, 10)
}

const defaultRange = () => {
  const to = new Date()
  const from = new Date(to)
  from.setDate(from.getDate() - 29)
  return { from: isoDate(from), to: isoDate(to) }
}

const pretty = (value?: string | null) =>
  value
    ? value
        .replaceAll('_', ' ')
        .toLowerCase()
        .replace(/\b\w/g, (character) => character.toUpperCase())
    : 'Unknown'

const excluded = (status?: string | null) =>
  status === 'EXCLUDED' || status === 'ADDED_EXCLUDED'

export default function GoogleSearchTermReport({
  companyId,
  campaign,
  canCreate,
  canModify,
}: Props) {
  const initial = useMemo(() => defaultRange(), [])
  const [from, setFrom] = useState(initial.from)
  const [to, setTo] = useState(initial.to)
  const [report, setReport] = useState<GoogleSearchTermReport | null>(null)
  const [query, setQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [zeroConversionsOnly, setZeroConversionsOnly] = useState(false)
  const [matchType, setMatchType] =
    useState<GoogleNegativeKeywordInput['matchType']>('EXACT')
  const [loading, setLoading] = useState(true)
  const [workingKey, setWorkingKey] = useState<string | null>(null)
  const [locallyExcluded, setLocallyExcluded] = useState<Set<string>>(new Set())
  const [locallyPromoted, setLocallyPromoted] = useState<Set<string>>(new Set())
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = async () => {
    setLoading(true)
    setError('')
    setNotice('')
    try {
      setReport(
        await adCenterApi.googleSearchTerms(
          companyId,
          campaign.id,
          from,
          to,
        ),
      )
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load Google search terms'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [companyId, campaign.id])

  const filtered = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    return (report?.rows ?? []).filter((row) => {
      if (
        normalized &&
        !row.searchTerm.toLowerCase().includes(normalized) &&
        !(row.adGroupName ?? '').toLowerCase().includes(normalized) &&
        !(row.matchedKeyword ?? '').toLowerCase().includes(normalized)
      ) {
        return false
      }
      if (statusFilter !== 'ALL' && row.targetingStatus !== statusFilter) {
        return false
      }
      if (zeroConversionsOnly && row.conversions > 0) return false
      return true
    })
  }, [report?.rows, query, statusFilter, zeroConversionsOnly])

  const currency = report?.currency ?? 'INR'
  const money = (value: number | null | undefined) => {
    try {
      return new Intl.NumberFormat(undefined, {
        style: 'currency',
        currency,
        maximumFractionDigits: 2,
      }).format(value ?? 0)
    } catch {
      return String(value ?? 0)
    }
  }

  const number = (value: number | null | undefined, digits = 0) =>
    new Intl.NumberFormat(undefined, {
      maximumFractionDigits: digits,
    }).format(value ?? 0)

  const localKey = (row: GoogleSearchTermRow, scope: 'CAMPAIGN' | 'AD_GROUP' | 'PROMOTE') =>
    `${scope}|${row.externalAdGroupId}|${row.searchTerm.toLowerCase()}`

  const promoteToKeyword = async (row: GoogleSearchTermRow) => {
    if (!row.adGroupId) return
    const key = localKey(row, 'PROMOTE')
    setWorkingKey(key)
    setError('')
    setNotice('')
    try {
      await adCenterApi.createGoogleKeyword(companyId, row.adGroupId, {
        text: row.searchTerm,
        matchType,
        negative: false,
      })
      setLocallyPromoted((current) => new Set([...current, key]))
      setNotice(
        `“${row.searchTerm}” added as a ${pretty(matchType).toLowerCase()} keyword in ${row.adGroupName ?? 'this ad group'}.`,
      )
    } catch (actionError) {
      setError(
        apiErrorMessage(
          actionError,
          'Unable to promote this search term to a keyword',
        ),
      )
    } finally {
      setWorkingKey(null)
    }
  }

  const addCampaignNegative = async (row: GoogleSearchTermRow) => {
    const key = localKey(row, 'CAMPAIGN')
    setWorkingKey(key)
    setError('')
    setNotice('')
    try {
      const result = await adCenterApi.addGoogleCampaignNegativeKeywords(
        companyId,
        campaign.id,
        [{ text: row.searchTerm, matchType }],
      )
      setLocallyExcluded((current) => new Set([...current, key]))
      setNotice(
        result.createdCount > 0
          ? `“${row.searchTerm}” added as a ${pretty(matchType).toLowerCase()} campaign negative.`
          : `“${row.searchTerm}” already exists as a campaign negative.`,
      )
    } catch (actionError) {
      setError(
        apiErrorMessage(
          actionError,
          'Unable to add this search term as a campaign negative',
        ),
      )
    } finally {
      setWorkingKey(null)
    }
  }

  const addAdGroupNegative = async (row: GoogleSearchTermRow) => {
    if (!row.adGroupId) return
    const key = localKey(row, 'AD_GROUP')
    setWorkingKey(key)
    setError('')
    setNotice('')
    try {
      await adCenterApi.createGoogleKeyword(companyId, row.adGroupId, {
        text: row.searchTerm,
        matchType,
        negative: true,
      })
      setLocallyExcluded((current) => new Set([...current, key]))
      setNotice(
        `“${row.searchTerm}” added as a ${pretty(matchType).toLowerCase()} negative in ${row.adGroupName ?? 'this ad group'}.`,
      )
    } catch (actionError) {
      setError(
        apiErrorMessage(
          actionError,
          'Unable to add this search term as an ad-group negative',
        ),
      )
    } finally {
      setWorkingKey(null)
    }
  }

  const statuses = useMemo(
    () =>
      [...new Set((report?.rows ?? []).map((row) => row.targetingStatus).filter(Boolean))]
        .sort() as string[],
    [report?.rows],
  )

  return (
    <section className="panel mt-5 overflow-hidden">
      <div className="flex flex-wrap items-start justify-between gap-4 border-b border-slate-100 px-5 py-4">
        <div>
          <h2 className="text-base font-bold text-slate-950">Search terms</h2>
          <p className="mt-1 max-w-2xl text-xs leading-5 text-slate-500">
            See the actual Google queries that triggered this Search campaign,
            then block irrelevant traffic at campaign or ad-group level. Google
            may omit low-volume queries from search-term reporting.
          </p>
        </div>
        <div className="flex flex-wrap items-end gap-2">
          <label className="text-[11px] font-semibold text-slate-600">
            From
            <input
              className="form-input mt-1 w-36"
              type="date"
              value={from}
              max={to}
              onChange={(event) => setFrom(event.target.value)}
            />
          </label>
          <label className="text-[11px] font-semibold text-slate-600">
            To
            <input
              className="form-input mt-1 w-36"
              type="date"
              value={to}
              min={from}
              max={isoDate(new Date())}
              onChange={(event) => setTo(event.target.value)}
            />
          </label>
          <button
            type="button"
            className="secondary-button inline-flex items-center gap-2"
            disabled={loading || !from || !to}
            onClick={() => void load()}
          >
            <FiRefreshCw className={loading ? 'animate-spin' : ''} aria-hidden="true" />
            {loading ? 'Loading…' : 'Apply'}
          </button>
        </div>
      </div>

      {(error || notice) && (
        <div
          className={
            'mx-5 mt-4 rounded-xl border px-4 py-3 text-sm font-medium ' +
            (error
              ? 'border-red-200 bg-red-50 text-red-700'
              : 'border-emerald-200 bg-emerald-50 text-emerald-700')
          }
        >
          {error || notice}
        </div>
      )}

      <div className="grid gap-3 border-b border-slate-100 px-5 py-4 md:grid-cols-[minmax(180px,1fr)_180px_160px_auto]">
        <label className="text-[11px] font-semibold text-slate-600">
          Search
          <div className="relative mt-1">
            <FiFilter
              className="pointer-events-none absolute left-3 top-3 text-slate-400"
              aria-hidden="true"
            />
            <input
              className="form-input pl-9"
              value={query}
              placeholder="Query or ad group"
              onChange={(event) => setQuery(event.target.value)}
            />
          </div>
        </label>
        <label className="text-[11px] font-semibold text-slate-600">
          Google status
          <select
            className="form-input mt-1"
            value={statusFilter}
            onChange={(event) => setStatusFilter(event.target.value)}
          >
            <option value="ALL">All statuses</option>
            {statuses.map((status) => (
              <option key={status} value={status}>
                {pretty(status)}
              </option>
            ))}
          </select>
        </label>
        <label className="text-[11px] font-semibold text-slate-600">
          Keyword / negative match type
          <select
            className="form-input mt-1"
            value={matchType}
            disabled={!canModify && !canCreate}
            onChange={(event) =>
              setMatchType(
                event.target.value as GoogleNegativeKeywordInput['matchType'],
              )
            }
          >
            {MATCH_TYPES.map((value) => (
              <option key={value} value={value}>
                {pretty(value)}
              </option>
            ))}
          </select>
        </label>
        <label className="flex items-center gap-2 self-end pb-2 text-xs font-semibold text-slate-600">
          <input
            type="checkbox"
            checked={zeroConversionsOnly}
            onChange={(event) => setZeroConversionsOnly(event.target.checked)}
          />
          Zero conversions only
        </label>
      </div>

      <div className="border-b border-slate-100 bg-slate-50 px-5 py-3 text-xs text-slate-600">
        <strong>{filtered.length}</strong> of {report?.rows.length ?? 0} reported
        queries shown. Exact is the safest default for exclusions because
        Phrase/Broad can block additional queries. The same selector is used when
        promoting a search term to a keyword.
      </div>

      {loading ? (
        <div className="p-4"><TableSkeleton rows={7} columns={6} /></div>
      ) : filtered.length === 0 ? (
        <div className="p-8 text-center text-sm text-slate-500">
          No search terms match this range and filter. Search-term visibility is
          also subject to Google reporting/privacy thresholds.
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-[1460px] w-full text-left text-xs">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-4 py-3 font-semibold">Search term</th>
                <th className="px-4 py-3 font-semibold">Matched by</th>
                <th className="px-4 py-3 font-semibold">Ad group</th>
                <th className="px-4 py-3 font-semibold">Status</th>
                <th className="px-4 py-3 text-right font-semibold">Spend</th>
                <th className="px-4 py-3 text-right font-semibold">Impr.</th>
                <th className="px-4 py-3 text-right font-semibold">Clicks</th>
                <th className="px-4 py-3 text-right font-semibold">Conv.</th>
                <th className="px-4 py-3 text-right font-semibold">CPA</th>
                <th className="px-4 py-3 text-right font-semibold">ROAS</th>
                <th className="px-4 py-3 text-right font-semibold">Conv. value</th>
                <th className="px-4 py-3 font-semibold">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filtered.map((row) => {
                const campaignKey = localKey(row, 'CAMPAIGN')
                const groupKey = localKey(row, 'AD_GROUP')
                const promoteKey = localKey(row, 'PROMOTE')
                const providerExcluded = excluded(row.targetingStatus)
                const providerTargeted = row.targetingStatus === 'ADDED'
                const reviewCandidate = row.clicks > 0 && row.conversions === 0
                return (
                  <tr
                    key={[
                      row.externalAdGroupId,
                      row.searchTerm,
                      row.matchedKeyword ?? '',
                      row.searchTermMatchType ?? '',
                      row.searchTermMatchSource ?? '',
                    ].join('|')}
                  >
                    <td className="px-4 py-3">
                      <p className="max-w-xs font-semibold text-slate-900">
                        {row.searchTerm}
                      </p>
                      {reviewCandidate && !providerExcluded && (
                        <span className="mt-1 inline-flex rounded-full bg-amber-50 px-2 py-0.5 text-[10px] font-semibold text-amber-700">
                          Review · clicks with no conversions
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <p className="font-semibold text-slate-700">
                        {row.matchedKeyword ?? pretty(row.searchTermMatchSource)}
                      </p>
                      <p className="mt-0.5 text-[10px] text-slate-400">
                        {row.matchedKeywordMatchType
                          ? `${pretty(row.matchedKeywordMatchType)} keyword`
                          : pretty(row.searchTermMatchType)}
                        {row.searchTermMatchSource
                          ? ` · ${pretty(row.searchTermMatchSource)}`
                          : ''}
                      </p>
                    </td>
                    <td className="px-4 py-3 text-slate-600">
                      {row.adGroupName ?? row.externalAdGroupId}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={
                          'rounded-full px-2 py-1 text-[10px] font-semibold ' +
                          (providerExcluded
                            ? 'bg-emerald-50 text-emerald-700'
                            : 'bg-slate-100 text-slate-600')
                        }
                      >
                        {pretty(row.targetingStatus)}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right font-semibold text-slate-800">
                      {money(row.spend)}
                    </td>
                    <td className="px-4 py-3 text-right text-slate-600">
                      {number(row.impressions)}
                    </td>
                    <td className="px-4 py-3 text-right text-slate-600">
                      {number(row.clicks)}
                    </td>
                    <td className="px-4 py-3 text-right text-slate-600">
                      {number(row.conversions, 2)}
                    </td>
                    <td className="px-4 py-3 text-right text-slate-600">
                      {row.cpa == null ? '—' : money(row.cpa)}
                    </td>
                    <td className="px-4 py-3 text-right text-slate-600">
                      {row.roas == null ? '—' : number(row.roas, 2) + '×'}
                    </td>
                    <td className="px-4 py-3 text-right text-slate-600">
                      {money(row.conversionValue)}
                    </td>
                    <td className="px-4 py-3">
                      {providerExcluded ? (
                        <span className="text-[11px] font-semibold text-emerald-700">
                          Already excluded
                        </span>
                      ) : (
                        <div className="flex flex-wrap gap-2">
                          {canCreate && row.adGroupId && !providerTargeted && (
                            <button
                              type="button"
                              className="secondary-button inline-flex items-center gap-1"
                              disabled={
                                workingKey !== null ||
                                locallyPromoted.has(promoteKey)
                              }
                              onClick={() => void promoteToKeyword(row)}
                            >
                              <FiPlus aria-hidden="true" />
                              {locallyPromoted.has(promoteKey)
                                ? 'Keyword added'
                                : 'Promote keyword'}
                            </button>
                          )}
                          {canCreate && row.adGroupId && (
                            <button
                              type="button"
                              className="secondary-button inline-flex items-center gap-1"
                              disabled={
                                workingKey !== null ||
                                locallyExcluded.has(groupKey)
                              }
                              onClick={() => void addAdGroupNegative(row)}
                            >
                              <FiSlash aria-hidden="true" />
                              {locallyExcluded.has(groupKey)
                                ? 'Ad group blocked'
                                : 'Block ad group'}
                            </button>
                          )}
                          {canModify && (
                            <button
                              type="button"
                              className="secondary-button inline-flex items-center gap-1"
                              disabled={
                                workingKey !== null ||
                                locallyExcluded.has(campaignKey)
                              }
                              onClick={() => void addCampaignNegative(row)}
                            >
                              <FiSlash aria-hidden="true" />
                              {locallyExcluded.has(campaignKey)
                                ? 'Campaign blocked'
                                : 'Block campaign'}
                            </button>
                          )}
                        </div>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
