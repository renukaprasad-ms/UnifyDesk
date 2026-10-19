import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useMemo, useState } from 'react'
import { FiRefreshCw, FiZap } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type { GoogleAiMaxCombinationReport as Report } from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { TableSkeleton } from '../ui/Skeleton'

type Props = {
  companyId: string
  campaignId: string
}

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

export default function GoogleAiMaxCombinationReport({ companyId, campaignId }: Props) {
  const initial = useMemo(() => defaultRange(), [])
  const [from, setFrom] = useState(initial.from)
  const [to, setTo] = useState(initial.to)
  const [report, setReport] = useState<Report | null>(null)
  const [loading, setLoading] = useState(true)
  const [, setError] = useSnackbarState('', 'error')

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      setReport(await adCenterApi.googleAiMaxReport(companyId, campaignId, from, to))
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load AI Max reporting'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [companyId, campaignId])

  const money = (value: number | null | undefined) => {
    try {
      return new Intl.NumberFormat(undefined, {
        style: 'currency',
        currency: report?.currency || 'INR',
        maximumFractionDigits: 2,
      }).format(value ?? 0)
    } catch {
      return String(value ?? 0)
    }
  }

  const number = (value: number | null | undefined, digits = 0) =>
    new Intl.NumberFormat(undefined, { maximumFractionDigits: digits }).format(value ?? 0)

  return (
    <section className="panel mt-5 overflow-hidden">
      <div className="flex flex-wrap items-start justify-between gap-4 border-b border-slate-100 px-5 py-4">
        <div>
          <div className="flex items-center gap-2">
            <FiZap className="text-indigo-600" aria-hidden="true" />
            <h2 className="text-base font-bold text-slate-950">AI Max transparency</h2>
          </div>
          <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-500">
            Google’s AI Max combination view shows the search term, headline, and landing
            page that served together, with performance for the selected date range.
          </p>
        </div>
        <div className="flex flex-wrap items-end gap-2">
          <label className="text-[11px] font-semibold text-slate-600">
            From
            <input
              type="date"
              className="form-input mt-1 w-36"
              value={from}
              max={to}
              onChange={(event) => setFrom(event.target.value)}
            />
          </label>
          <label className="text-[11px] font-semibold text-slate-600">
            To
            <input
              type="date"
              className="form-input mt-1 w-36"
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



      {loading ? (
        <div className="p-4"><TableSkeleton rows={6} columns={5} /></div>
      ) : !report || report.rows.length === 0 ? (
        <div className="p-8 text-center text-sm text-slate-500">
          No AI Max search-term/ad combinations were reported for this range. This is
          expected when AI Max is disabled or has not served eligible traffic yet.
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-[1100px] w-full text-left text-xs">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-4 py-3 font-semibold">Search term</th>
                <th className="px-4 py-3 font-semibold">Headline</th>
                <th className="px-4 py-3 font-semibold">Landing page</th>
                <th className="px-4 py-3 font-semibold">Ad group</th>
                <th className="px-4 py-3 text-right font-semibold">Spend</th>
                <th className="px-4 py-3 text-right font-semibold">Clicks</th>
                <th className="px-4 py-3 text-right font-semibold">Conv.</th>
                <th className="px-4 py-3 text-right font-semibold">CPA</th>
                <th className="px-4 py-3 text-right font-semibold">ROAS</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {report.rows.map((row, index) => (
                <tr
                  key={[
                    row.externalAdGroupId ?? '',
                    row.searchTerm ?? '',
                    row.landingPage ?? '',
                    row.headline ?? '',
                    index,
                  ].join('|')}
                >
                  <td className="max-w-xs px-4 py-3 font-semibold text-slate-900">
                    {row.searchTerm || '—'}
                  </td>
                  <td className="max-w-sm px-4 py-3 text-slate-600">
                    {row.headline || '—'}
                  </td>
                  <td className="max-w-sm px-4 py-3">
                    {row.landingPage ? (
                      <a
                        className="break-all font-medium text-indigo-600 hover:underline"
                        href={row.landingPage}
                        target="_blank"
                        rel="noreferrer"
                      >
                        {row.landingPage}
                      </a>
                    ) : (
                      '—'
                    )}
                  </td>
                  <td className="px-4 py-3 text-slate-600">
                    {row.adGroupName || row.externalAdGroupId || '—'}
                  </td>
                  <td className="px-4 py-3 text-right font-semibold text-slate-800">
                    {money(row.spend)}
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
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
