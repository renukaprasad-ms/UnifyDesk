import { useSnackbarState } from '../feedback/useSnackbarState'
import { useMemo, useState } from 'react'
import { FiCheck, FiChevronDown, FiRefreshCw, FiSearch, FiTrendingUp } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  GoogleKeywordPlannerResponse,
  GoogleKeywordPlannerRow,
  ReachlyoAudienceLocationInput,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import GoogleKeywordForecastPanel from './GoogleKeywordForecastPanel'

type Props = {
  companyId: string
  accountId: string
  landingPageUrl: string
  locations: ReachlyoAudienceLocationInput[]
  language?: string | null
  includeSearchPartners: boolean
  currentKeywords: string[]
  disabled: boolean
  onApply: (keywords: string[]) => void
}

const values = (input: string) =>
  input
    .split(/\r?\n|,/)
    .map((value) => value.trim())
    .filter(Boolean)

const dedupe = (items: string[]) => {
  const seen = new Set<string>()
  return items.filter((item) => {
    const key = item.trim().toLowerCase()
    if (!key || seen.has(key)) return false
    seen.add(key)
    return true
  })
}

export default function GoogleKeywordPlannerPanel(props: Props) {
  const [seedText, setSeedText] = useState(props.currentKeywords.join('\n'))
  const [url, setUrl] = useState(props.landingPageUrl)
  const [useEntireSite, setUseEntireSite] = useState(false)
  const [language, setLanguage] = useState(props.language ?? '')
  const [network, setNetwork] = useState<'GOOGLE_SEARCH' | 'GOOGLE_SEARCH_AND_PARTNERS'>(
    props.includeSearchPartners ? 'GOOGLE_SEARCH_AND_PARTNERS' : 'GOOGLE_SEARCH',
  )
  const [report, setReport] = useState<GoogleKeywordPlannerResponse | null>(null)
  const [selected, setSelected] = useState<string[]>([])
  const [filter, setFilter] = useState('')
  const [loading, setLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [mode, setMode] = useState<'IDEAS' | 'HISTORICAL'>('IDEAS')
  const [, setError] = useSnackbarState('', 'error')

  const filteredRows = useMemo(() => {
    const query = filter.trim().toLowerCase()
    const rows = query
      ? (report?.rows ?? []).filter((row) => row.text.toLowerCase().includes(query))
      : report?.rows ?? []
    return [...rows].sort(
      (left, right) =>
        (right.averageMonthlySearches ?? -1) - (left.averageMonthlySearches ?? -1),
    )
  }, [report?.rows, filter])

  const plannerLocations = props.locations.slice(0, 10)

  const loadIdeas = async (pageToken?: string | null, append = false) => {
    const seeds = useEntireSite ? [] : values(seedText).slice(0, 20)
    if (seeds.length === 0 && !url.trim()) {
      setError('Enter seed keywords, a website URL, or both.')
      return
    }
    if (useEntireSite && !url.trim()) {
      setError('Entire-site planning requires a website URL.')
      return
    }

    if (append) setLoadingMore(true)
    else setLoading(true)
    setError('')
    try {
      const result = await adCenterApi.googleKeywordPlannerIdeas(
        props.companyId,
        props.accountId,
        {
          seedKeywords: seeds,
          url: url.trim() || null,
          useEntireSite,
          locations: plannerLocations,
          language: language.trim() || null,
          network,
          includeAdultKeywords: false,
          pageSize: 250,
          pageToken: pageToken || null,
        },
      )
      setMode('IDEAS')
      setReport((current) =>
        append && current
          ? {
              ...result,
              rows: dedupeRows([...current.rows, ...result.rows]),
            }
          : result,
      )
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to generate keyword ideas'))
    } finally {
      setLoading(false)
      setLoadingMore(false)
    }
  }

  const loadHistorical = async () => {
    const keywords = values(seedText)
    if (keywords.length === 0) {
      setError('Enter keywords to analyze historical metrics.')
      return
    }
    setLoading(true)
    setError('')
    try {
      const result = await adCenterApi.googleKeywordPlannerHistoricalMetrics(
        props.companyId,
        props.accountId,
        {
          keywords,
          locations: plannerLocations,
          language: language.trim() || null,
          network,
          includeAdultKeywords: false,
        },
      )
      setMode('HISTORICAL')
      setReport(result)
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load historical keyword metrics'))
    } finally {
      setLoading(false)
    }
  }

  const toggleSelected = (keyword: string) => {
    setSelected((current) =>
      current.some((item) => item.toLowerCase() === keyword.toLowerCase())
        ? current.filter((item) => item.toLowerCase() !== keyword.toLowerCase())
        : [...current, keyword],
    )
  }

  const applySelected = () => {
    const next = dedupe([...props.currentKeywords, ...selected])
    props.onApply(next)
    setSeedText(next.join('\n'))
  }

  const selectVisible = () => {
    setSelected((current) => dedupe([...current, ...filteredRows.map((row) => row.text)]))
  }

  const money = (value?: number | null) => {
    if (value == null) return '—'
    try {
      return new Intl.NumberFormat(undefined, {
        style: 'currency',
        currency: report?.currency || 'INR',
        maximumFractionDigits: 2,
      }).format(value)
    } catch {
      return String(value)
    }
  }

  return (
    <section className="mt-4 overflow-hidden rounded-2xl border border-indigo-100 bg-indigo-50/30 sm:col-span-2">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-indigo-100 px-4 py-4">
        <div>
          <div className="flex items-center gap-2">
            <FiTrendingUp className="text-indigo-600" aria-hidden="true" />
            <h4 className="text-sm font-bold text-slate-950">Google Keyword Planner</h4>
          </div>
          <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-500">
            Discover Search keywords from your website or seed themes, compare historical
            demand and bid ranges, then add selected ideas to this campaign.
          </p>
        </div>
        <div className="flex flex-wrap gap-2 text-[11px]">
          {report?.cached && (
            <span className="rounded-full bg-emerald-100 px-2.5 py-1 font-semibold text-emerald-700">
              Cached result
            </span>
          )}
          <span className="rounded-full bg-white px-2.5 py-1 font-semibold text-slate-500">
            Ideas · history · forecasts
          </span>
        </div>
      </div>

      <div className="grid gap-3 p-4 lg:grid-cols-2">
        <label className="text-xs font-semibold text-slate-700">
          Seed keywords
          <textarea
            className="form-input mt-2 min-h-28"
            value={seedText}
            disabled={props.disabled || useEntireSite}
            onChange={(event) => setSeedText(event.target.value)}
            placeholder={'customer support software\nomnichannel inbox\nhelp desk'}
          />
          <span className="mt-1 block text-[11px] font-normal text-slate-400">
            Up to 20 seed keywords for idea generation. Historical analysis can use a larger list.
          </span>
        </label>

        <div className="grid content-start gap-3">
          <label className="text-xs font-semibold text-slate-700">
            Website URL
            <input
              className="form-input mt-2"
              value={url}
              disabled={props.disabled}
              onChange={(event) => setUrl(event.target.value)}
              placeholder="https://example.com/product"
            />
          </label>
          <label className="inline-flex items-start gap-2 text-xs text-slate-600">
            <input
              type="checkbox"
              className="mt-0.5"
              checked={useEntireSite}
              disabled={props.disabled || !url.trim()}
              onChange={(event) => setUseEntireSite(event.target.checked)}
            />
            <span>
              Use the entire website as the seed
              <span className="mt-0.5 block text-[11px] text-slate-400">
                Site mode cannot be combined with seed keywords.
              </span>
            </span>
          </label>
        </div>

        <label className="text-xs font-semibold text-slate-700">
          Planning language
          <input
            className="form-input mt-2"
            value={language}
            disabled={props.disabled}
            onChange={(event) => setLanguage(event.target.value)}
            placeholder="en or English"
          />
          <span className="mt-1 block text-[11px] font-normal text-slate-400">
            Google Keyword Planner analyzes one language at a time.
          </span>
        </label>

        <label className="text-xs font-semibold text-slate-700">
          Search network
          <select
            className="form-input mt-2"
            value={network}
            disabled={props.disabled}
            onChange={(event) =>
              setNetwork(
                event.target.value as 'GOOGLE_SEARCH' | 'GOOGLE_SEARCH_AND_PARTNERS',
              )
            }
          >
            <option value="GOOGLE_SEARCH">Google Search</option>
            <option value="GOOGLE_SEARCH_AND_PARTNERS">
              Google Search + Search Partners
            </option>
          </select>
          <span className="mt-1 block text-[11px] font-normal text-slate-400">
            Geo defaults come from the audience above. Radius targets must be converted to a
            discrete country, region, city, or postal target for planning.
          </span>
        </label>
      </div>

      <div className="flex flex-wrap items-center gap-2 border-t border-indigo-100 px-4 py-3">
        <button
          type="button"
          className="primary-button inline-flex items-center gap-2"
          disabled={props.disabled || loading}
          onClick={() => void loadIdeas()}
        >
          <FiSearch aria-hidden="true" />
          {loading && mode === 'IDEAS' ? 'Finding ideas…' : 'Find keyword ideas'}
        </button>
        <button
          type="button"
          className="secondary-button inline-flex items-center gap-2"
          disabled={props.disabled || loading || values(seedText).length === 0}
          onClick={() => void loadHistorical()}
        >
          <FiRefreshCw className={loading && mode === 'HISTORICAL' ? 'animate-spin' : ''} aria-hidden="true" />
          Analyze entered keywords
        </button>
        <span className="ml-auto text-[11px] text-slate-400">
          {props.locations.length > 10
            ? 'Using the first 10 audience locations for Google planning.'
            : props.locations.length > 0
              ? `Using ${props.locations.length} audience location target(s).`
              : 'No geo filter: Google may return global planning data.'}
        </span>
      </div>



      {report && (
        <div className="border-t border-indigo-100 bg-white">
          <div className="flex flex-wrap items-center gap-3 px-4 py-3">
            <div>
              <p className="text-xs font-bold text-slate-800">
                {mode === 'IDEAS' ? 'Keyword ideas' : 'Historical metrics'}
              </p>
              <p className="mt-0.5 text-[11px] text-slate-400">
                {report.rows.length.toLocaleString()} loaded
                {report.totalSize > report.rows.length
                  ? ` of ${report.totalSize.toLocaleString()} available`
                  : ''}
              </p>
            </div>
            <div className="relative ml-auto min-w-52 flex-1 sm:max-w-xs">
              <FiSearch
                className="pointer-events-none absolute left-3 top-2.5 text-slate-400"
                aria-hidden="true"
              />
              <input
                className="form-input h-9 pl-9 text-xs"
                value={filter}
                onChange={(event) => setFilter(event.target.value)}
                placeholder="Filter loaded keywords"
              />
            </div>
            <button
              type="button"
              className="secondary-button"
              disabled={props.disabled || filteredRows.length === 0}
              onClick={selectVisible}
            >
              Select visible
            </button>
            <button
              type="button"
              className="primary-button inline-flex items-center gap-2"
              disabled={props.disabled || selected.length === 0}
              onClick={applySelected}
            >
              <FiCheck aria-hidden="true" />
              Add {selected.length || ''} to campaign
            </button>
          </div>

          {report.rows.length === 0 ? (
            <div className="px-4 py-8 text-center text-xs text-slate-500">
              Google returned no keyword metrics for this request.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-[980px] w-full text-left text-xs">
                <thead className="bg-slate-50 text-slate-500">
                  <tr>
                    <th className="w-10 px-3 py-3"></th>
                    <th className="px-3 py-3 font-semibold">Keyword</th>
                    <th className="px-3 py-3 text-right font-semibold">Avg. monthly</th>
                    <th className="px-3 py-3 font-semibold">Competition</th>
                    <th className="px-3 py-3 text-right font-semibold">Index</th>
                    <th className="px-3 py-3 text-right font-semibold">Top bid low</th>
                    <th className="px-3 py-3 text-right font-semibold">Top bid high</th>
                    <th className="px-3 py-3 text-right font-semibold">Avg. CPC</th>
                    <th className="px-3 py-3 font-semibold">Theme</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {filteredRows.map((row) => {
                    const checked = selected.some(
                      (item) => item.toLowerCase() === row.text.toLowerCase(),
                    )
                    return (
                      <KeywordRow
                        key={row.text.toLowerCase()}
                        row={row}
                        checked={checked}
                        disabled={props.disabled}
                        money={money}
                        onToggle={() => toggleSelected(row.text)}
                      />
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}

          {mode === 'IDEAS' && report.nextPageToken && (
            <div className="flex justify-center border-t border-slate-100 px-4 py-3">
              <button
                type="button"
                className="secondary-button inline-flex items-center gap-2"
                disabled={props.disabled || loadingMore}
                onClick={() => void loadIdeas(report.nextPageToken, true)}
              >
                <FiChevronDown aria-hidden="true" />
                {loadingMore ? 'Loading…' : 'Load more ideas'}
              </button>
            </div>
          )}
        </div>
      )}

      <GoogleKeywordForecastPanel
        companyId={props.companyId}
        accountId={props.accountId}
        keywords={
          selected.length > 0
            ? selected
            : props.currentKeywords.length > 0
              ? props.currentKeywords
              : values(seedText)
        }
        locations={plannerLocations}
        language={language.trim() || null}
        disabled={props.disabled}
      />
    </section>
  )
}

function KeywordRow({
  row,
  checked,
  disabled,
  money,
  onToggle,
}: {
  row: GoogleKeywordPlannerRow
  checked: boolean
  disabled: boolean
  money: (value?: number | null) => string
  onToggle: () => void
}) {
  const concept = row.concepts.find((item) => item.groupName || item.name)

  return (
    <tr className={checked ? 'bg-indigo-50/40' : undefined}>
      <td className="px-3 py-3 align-top">
        <input
          type="checkbox"
          checked={checked}
          disabled={disabled}
          onChange={onToggle}
        />
      </td>
      <td className="max-w-sm px-3 py-3 align-top">
        <p className="font-semibold text-slate-900">{row.text}</p>
        {row.closeVariants.length > 0 && (
          <p className="mt-1 truncate text-[10px] text-slate-400">
            Variants: {row.closeVariants.slice(0, 3).join(', ')}
          </p>
        )}
      </td>
      <td className="px-3 py-3 text-right align-top font-semibold text-slate-800">
        {row.averageMonthlySearches?.toLocaleString() ?? '—'}
      </td>
      <td className="px-3 py-3 align-top text-slate-600">
        {row.competition
          ? row.competition.replaceAll('_', ' ').toLowerCase()
          : '—'}
      </td>
      <td className="px-3 py-3 text-right align-top text-slate-600">
        {row.competitionIndex ?? '—'}
      </td>
      <td className="px-3 py-3 text-right align-top text-slate-600">
        {money(row.lowTopOfPageBid)}
      </td>
      <td className="px-3 py-3 text-right align-top text-slate-600">
        {money(row.highTopOfPageBid)}
      </td>
      <td className="px-3 py-3 text-right align-top text-slate-600">
        {money(row.averageCpc)}
      </td>
      <td className="max-w-xs px-3 py-3 align-top text-slate-600">
        {concept?.groupName || concept?.name || '—'}
      </td>
    </tr>
  )
}

function dedupeRows(rows: GoogleKeywordPlannerRow[]) {
  const seen = new Set<string>()
  return rows.filter((row) => {
    const key = row.text.trim().toLowerCase()
    if (!key || seen.has(key)) return false
    seen.add(key)
    return true
  })
}
