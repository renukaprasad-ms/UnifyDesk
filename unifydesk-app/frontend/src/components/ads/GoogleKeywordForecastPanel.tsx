import { useSnackbarState } from '../feedback/useSnackbarState'
import { useMemo, useState } from 'react'
import { FiActivity, FiRefreshCw } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  GoogleKeywordForecastBiddingStrategy,
  GoogleKeywordForecastMatchType,
  GoogleKeywordForecastResponse,
  ReachlyoAudienceLocationInput,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'

type Props = {
  companyId: string
  accountId: string
  keywords: string[]
  locations: ReachlyoAudienceLocationInput[]
  language?: string | null
  disabled: boolean
}

const dateFromToday = (days: number) => {
  const date = new Date()
  date.setDate(date.getDate() + days)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export default function GoogleKeywordForecastPanel(props: Props) {
  const [matchType, setMatchType] = useState<GoogleKeywordForecastMatchType>('PHRASE')
  const [strategy, setStrategy] = useState<GoogleKeywordForecastBiddingStrategy>('MAXIMIZE_CLICKS')
  const [dailyBudget, setDailyBudget] = useState('100')
  const [maxCpcBid, setMaxCpcBid] = useState('5')
  const [maxCpcBidCeiling, setMaxCpcBidCeiling] = useState('')
  const [startDate, setStartDate] = useState(() => dateFromToday(1))
  const [endDate, setEndDate] = useState(() => dateFromToday(30))
  const [result, setResult] = useState<GoogleKeywordForecastResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [, setError] = useSnackbarState('', 'error')

  const keywords = useMemo(() => {
    const seen = new Set<string>()
    return props.keywords
      .map((value) => value.trim())
      .filter((value) => {
        const key = value.toLowerCase()
        if (!key || seen.has(key)) return false
        seen.add(key)
        return true
      })
      .slice(0, 10_000)
  }, [props.keywords])

  const numeric = (value: string) => {
    const parsed = Number(value)
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null
  }

  const generate = async () => {
    if (keywords.length === 0) {
      setError('Select or enter at least one keyword before forecasting.')
      return
    }
    const budget = numeric(dailyBudget)
    const bid = numeric(maxCpcBid)
    const ceiling = numeric(maxCpcBidCeiling)

    if (strategy !== 'MANUAL_CPC' && budget == null) {
      setError('This bidding strategy requires a positive daily budget.')
      return
    }
    if (strategy === 'MANUAL_CPC' && bid == null) {
      setError('Manual CPC forecasting requires a positive max CPC bid.')
      return
    }

    setLoading(true)
    setError('')
    try {
      setResult(
        await adCenterApi.googleKeywordForecast(props.companyId, props.accountId, {
          keywords: keywords.map((text) => ({ text, matchType })),
          locations: props.locations.slice(0, 10),
          language: props.language || null,
          startDate,
          endDate,
          biddingStrategy: strategy,
          dailyBudget: budget,
          maxCpcBid: strategy === 'MANUAL_CPC' ? bid : null,
          maxCpcBidCeiling: strategy === 'MAXIMIZE_CLICKS' ? ceiling : null,
        }),
      )
    } catch (forecastError) {
      setError(apiErrorMessage(forecastError, 'Unable to generate Google forecast'))
    } finally {
      setLoading(false)
    }
  }

  const money = (value?: number | null) => {
    if (value == null) return '—'
    try {
      return new Intl.NumberFormat(undefined, {
        style: 'currency',
        currency: result?.currency || 'INR',
        maximumFractionDigits: 2,
      }).format(value)
    } catch {
      return String(value)
    }
  }

  return (
    <div className="border-t border-indigo-100 bg-slate-50/70 px-4 py-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <FiActivity className="text-indigo-600" aria-hidden="true" />
            <h5 className="text-sm font-bold text-slate-900">Forecast selected keywords</h5>
          </div>
          <p className="mt-1 max-w-3xl text-[11px] leading-5 text-slate-500">
            Estimate future traffic or conversions for the current keyword set. Google factors in
            the account, targeting, bids, dates, seasonality and other signals. Forecasts are
            estimates, not guaranteed outcomes.
          </p>
        </div>
        <span className="rounded-full bg-white px-2.5 py-1 text-[11px] font-semibold text-slate-500">
          {keywords.length.toLocaleString()} keyword{keywords.length === 1 ? '' : 's'}
        </span>
      </div>

      <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <label className="text-xs font-semibold text-slate-700">
          Match type
          <select
            className="form-input mt-2"
            value={matchType}
            disabled={props.disabled}
            onChange={(event) => setMatchType(event.target.value as GoogleKeywordForecastMatchType)}
          >
            <option value="BROAD">Broad</option>
            <option value="PHRASE">Phrase</option>
            <option value="EXACT">Exact</option>
          </select>
        </label>

        <label className="text-xs font-semibold text-slate-700">
          Bidding strategy
          <select
            className="form-input mt-2"
            value={strategy}
            disabled={props.disabled}
            onChange={(event) =>
              setStrategy(event.target.value as GoogleKeywordForecastBiddingStrategy)
            }
          >
            <option value="MAXIMIZE_CLICKS">Maximize clicks</option>
            <option value="MAXIMIZE_CONVERSIONS">Maximize conversions</option>
            <option value="MANUAL_CPC">Manual CPC</option>
          </select>
        </label>

        <label className="text-xs font-semibold text-slate-700">
          Daily budget
          <input
            className="form-input mt-2"
            type="number"
            min="0.000001"
            step="0.01"
            value={dailyBudget}
            disabled={props.disabled}
            onChange={(event) => setDailyBudget(event.target.value)}
          />
          <span className="mt-1 block text-[10px] font-normal text-slate-400">
            Required for automated bidding; optional campaign budget for Manual CPC.
          </span>
        </label>

        {strategy === 'MANUAL_CPC' && (
          <label className="text-xs font-semibold text-slate-700">
            Max CPC bid
            <input
              className="form-input mt-2"
              type="number"
              min="0.000001"
              step="0.01"
              value={maxCpcBid}
              disabled={props.disabled}
              onChange={(event) => setMaxCpcBid(event.target.value)}
            />
          </label>
        )}

        {strategy === 'MAXIMIZE_CLICKS' && (
          <label className="text-xs font-semibold text-slate-700">
            Max CPC ceiling
            <input
              className="form-input mt-2"
              type="number"
              min="0.000001"
              step="0.01"
              value={maxCpcBidCeiling}
              disabled={props.disabled}
              onChange={(event) => setMaxCpcBidCeiling(event.target.value)}
              placeholder="Optional"
            />
          </label>
        )}

        <label className="text-xs font-semibold text-slate-700">
          Forecast start
          <input
            className="form-input mt-2"
            type="date"
            value={startDate}
            min={dateFromToday(1)}
            disabled={props.disabled}
            onChange={(event) => setStartDate(event.target.value)}
          />
        </label>

        <label className="text-xs font-semibold text-slate-700">
          Forecast end
          <input
            className="form-input mt-2"
            type="date"
            value={endDate}
            min={startDate}
            disabled={props.disabled}
            onChange={(event) => setEndDate(event.target.value)}
          />
        </label>
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <button
          type="button"
          className="primary-button inline-flex items-center gap-2"
          disabled={props.disabled || loading || keywords.length === 0}
          onClick={() => void generate()}
        >
          <FiRefreshCw className={loading ? 'animate-spin' : ''} aria-hidden="true" />
          {loading ? 'Generating forecast…' : 'Generate forecast'}
        </button>
        {result?.cached && (
          <span className="text-[11px] font-semibold text-emerald-700">
            Reused a recent matching forecast
          </span>
        )}
      </div>



      {result && (
        <div className="mt-4">
          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-5">
            <Metric label="Estimated clicks" value={number(result.clicks)} />
            <Metric label="Estimated cost" value={money(result.cost)} />
            <Metric label="Average CPC" value={money(result.averageCpc)} />
            <Metric label="Estimated conversions" value={number(result.conversions)} />
            <Metric label="Average CPA" value={money(result.averageCpa)} />
          </div>
          <p className="mt-2 text-[10px] leading-4 text-slate-400">
            Google returns click metrics for Manual CPC / Maximize Clicks and conversion metrics for
            Maximize Conversions. Metrics unavailable for the selected strategy are shown as —.
            Forecast period: {result.startDate} to {result.endDate}.
          </p>
        </div>
      )}
    </div>
  )
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white px-3 py-3">
      <p className="text-[10px] font-semibold uppercase tracking-wide text-slate-400">{label}</p>
      <p className="mt-1 text-base font-bold text-slate-900">{value}</p>
    </div>
  )
}

function number(value?: number | null) {
  return value == null
    ? '—'
    : new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(value)
}
