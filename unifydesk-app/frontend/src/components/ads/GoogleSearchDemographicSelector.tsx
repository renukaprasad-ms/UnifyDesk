import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useMemo, useState } from 'react'
import { FiSearch } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type { GoogleDetailedDemographicOption } from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { Skeleton } from '../ui/Skeleton'

type Props = {
  companyId: string
  accountId: string
  parentalStatusIncludes: string[]
  parentalStatusExcludes: string[]
  incomeRangeIncludes: string[]
  incomeRangeExcludes: string[]
  detailedDemographicIds: string[]
  excludedDetailedDemographicIds: string[]
  setParentalStatusIncludes: (value: string[]) => void
  setParentalStatusExcludes: (value: string[]) => void
  setIncomeRangeIncludes: (value: string[]) => void
  setIncomeRangeExcludes: (value: string[]) => void
  setDetailedDemographicIds: (value: string[]) => void
  setExcludedDetailedDemographicIds: (value: string[]) => void
  disabled?: boolean
}

const PARENTAL = [
  ['PARENT', 'Parents'],
  ['NOT_A_PARENT', 'Not a parent'],
  ['UNDETERMINED', 'Undetermined'],
] as const

const INCOME = [
  ['INCOME_RANGE_0_50', 'Lower 50%'],
  ['INCOME_RANGE_50_60', 'Top 41–50%'],
  ['INCOME_RANGE_60_70', 'Top 31–40%'],
  ['INCOME_RANGE_70_80', 'Top 21–30%'],
  ['INCOME_RANGE_80_90', 'Top 11–20%'],
  ['INCOME_RANGE_90_UP', 'Top 10%'],
  ['INCOME_RANGE_UNDETERMINED', 'Undetermined'],
] as const

const toggleExclusive = (
  value: string,
  checked: boolean,
  current: string[],
  opposite: string[],
  setCurrent: (value: string[]) => void,
  setOpposite: (value: string[]) => void,
) => {
  setCurrent(checked ? [...new Set([...current, value])] : current.filter((item) => item !== value))
  if (checked) {
    setOpposite(opposite.filter((item) => item !== value))
  }
}

export default function GoogleSearchDemographicSelector(props: Props) {
  const [query, setQuery] = useState('')
  const [options, setOptions] = useState<GoogleDetailedDemographicOption[]>([])
  const [loading, setLoading] = useState(false)
  const [, setError] = useSnackbarState('', 'error')

  useEffect(() => {
    if (!props.accountId) {
      setOptions([])
      return
    }

    const handle = window.setTimeout(() => {
      setLoading(true)
      setError('')
      void adCenterApi
        .googleDetailedDemographics(props.companyId, props.accountId, query.trim(), 50)
        .then((result) => setOptions(result.demographics))
        .catch((loadError) =>
          setError(apiErrorMessage(loadError, 'Unable to load Google detailed demographics')),
        )
        .finally(() => setLoading(false))
    }, 250)

    return () => window.clearTimeout(handle)
  }, [props.companyId, props.accountId, query])

  const selectedCount = useMemo(
    () =>
      props.parentalStatusIncludes.length +
      props.parentalStatusExcludes.length +
      props.incomeRangeIncludes.length +
      props.incomeRangeExcludes.length +
      props.detailedDemographicIds.length +
      props.excludedDetailedDemographicIds.length,
    [
      props.parentalStatusIncludes,
      props.parentalStatusExcludes,
      props.incomeRangeIncludes,
      props.incomeRangeExcludes,
      props.detailedDemographicIds,
      props.excludedDetailedDemographicIds,
    ],
  )

  return (
    <div className="rounded-2xl border border-slate-200 p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-sm font-bold text-slate-900">Search demographics</p>
          <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-500">
            Google Search demographic controls for parental status, household income, and
            provider-backed detailed demographic segments.
          </p>
        </div>
        <span className="rounded-full bg-slate-100 px-2.5 py-1 text-[10px] font-semibold text-slate-600">
          {selectedCount} selections
        </span>
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <div>
          <p className="text-xs font-semibold text-slate-700">Parental status</p>
          <div className="mt-2 space-y-2">
            {PARENTAL.map(([value, label]) => (
              <div
                key={value}
                className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 px-3 py-2.5"
              >
                <span className="text-xs text-slate-700">{label}</span>
                <div className="flex gap-3">
                  <label className="inline-flex items-center gap-2 text-xs text-slate-600">
                    <input
                      type="checkbox"
                      checked={props.parentalStatusIncludes.includes(value)}
                      disabled={props.disabled}
                      onChange={(event) =>
                        toggleExclusive(
                          value,
                          event.target.checked,
                          props.parentalStatusIncludes,
                          props.parentalStatusExcludes,
                          props.setParentalStatusIncludes,
                          props.setParentalStatusExcludes,
                        )
                      }
                    />
                    Include
                  </label>
                  <label className="inline-flex items-center gap-2 text-xs text-slate-600">
                    <input
                      type="checkbox"
                      checked={props.parentalStatusExcludes.includes(value)}
                      disabled={props.disabled}
                      onChange={(event) =>
                        toggleExclusive(
                          value,
                          event.target.checked,
                          props.parentalStatusExcludes,
                          props.parentalStatusIncludes,
                          props.setParentalStatusExcludes,
                          props.setParentalStatusIncludes,
                        )
                      }
                    />
                    Exclude
                  </label>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div>
          <p className="text-xs font-semibold text-slate-700">Household income</p>
          <div className="mt-2 space-y-2">
            {INCOME.map(([value, label]) => (
              <div
                key={value}
                className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 px-3 py-2.5"
              >
                <span className="text-xs text-slate-700">{label}</span>
                <div className="flex gap-3">
                  <label className="inline-flex items-center gap-2 text-xs text-slate-600">
                    <input
                      type="checkbox"
                      checked={props.incomeRangeIncludes.includes(value)}
                      disabled={props.disabled}
                      onChange={(event) =>
                        toggleExclusive(
                          value,
                          event.target.checked,
                          props.incomeRangeIncludes,
                          props.incomeRangeExcludes,
                          props.setIncomeRangeIncludes,
                          props.setIncomeRangeExcludes,
                        )
                      }
                    />
                    Include
                  </label>
                  <label className="inline-flex items-center gap-2 text-xs text-slate-600">
                    <input
                      type="checkbox"
                      checked={props.incomeRangeExcludes.includes(value)}
                      disabled={props.disabled}
                      onChange={(event) =>
                        toggleExclusive(
                          value,
                          event.target.checked,
                          props.incomeRangeExcludes,
                          props.incomeRangeIncludes,
                          props.setIncomeRangeExcludes,
                          props.setIncomeRangeIncludes,
                        )
                      }
                    />
                    Exclude
                  </label>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="mt-5 border-t border-slate-100 pt-5">
        <p className="text-xs font-semibold text-slate-700">Detailed demographics</p>
        <p className="mt-1 text-[11px] leading-5 text-slate-500">
          Examples can include education, homeownership, marital status, and other long-term life
          facts. Only segments Google reports as available for Search are shown.
        </p>

        <label className="relative mt-3 block">
          <FiSearch
            className="pointer-events-none absolute left-3 top-3.5 text-slate-400"
            aria-hidden="true"
          />
          <input
            className="form-input pl-9"
            value={query}
            disabled={props.disabled || !props.accountId}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search detailed demographics"
          />
        </label>



        {loading ? (
          <div className="mt-3 space-y-2" role="status" aria-label="Loading demographics">
          <Skeleton className="h-9 w-full" />
          <Skeleton className="h-9 w-full" />
          <Skeleton className="h-9 w-full" />
        </div>
        ) : options.length === 0 && props.accountId ? (
          <p className="mt-3 text-xs text-slate-500">
            No Search-compatible detailed demographics matched this search.
          </p>
        ) : (
          <div className="mt-3 max-h-80 space-y-2 overflow-y-auto pr-1">
            {options.map((option) => {
              const included = props.detailedDemographicIds.includes(option.id)
              const excluded = props.excludedDetailedDemographicIds.includes(option.id)
              return (
                <div
                  key={option.id}
                  className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 px-3 py-3"
                >
                  <div>
                    <p className="text-xs font-semibold text-slate-800">
                      {option.name ?? option.id}
                    </p>
                    <p className="mt-1 text-[11px] text-slate-500">
                      Google detailed demographic · ID {option.id}
                    </p>
                  </div>
                  <div className="flex gap-3">
                    <label className="inline-flex items-center gap-2 text-xs text-slate-600">
                      <input
                        type="checkbox"
                        checked={included}
                        disabled={props.disabled}
                        onChange={(event) =>
                          toggleExclusive(
                            option.id,
                            event.target.checked,
                            props.detailedDemographicIds,
                            props.excludedDetailedDemographicIds,
                            props.setDetailedDemographicIds,
                            props.setExcludedDetailedDemographicIds,
                          )
                        }
                      />
                      Include
                    </label>
                    <label className="inline-flex items-center gap-2 text-xs text-slate-600">
                      <input
                        type="checkbox"
                        checked={excluded}
                        disabled={props.disabled}
                        onChange={(event) =>
                          toggleExclusive(
                            option.id,
                            event.target.checked,
                            props.excludedDetailedDemographicIds,
                            props.detailedDemographicIds,
                            props.setExcludedDetailedDemographicIds,
                            props.setDetailedDemographicIds,
                          )
                        }
                      />
                      Exclude
                    </label>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
