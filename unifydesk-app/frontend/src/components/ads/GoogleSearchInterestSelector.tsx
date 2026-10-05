import { useEffect, useMemo, useState } from 'react'
import { FiSearch } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  GoogleSearchInterestCatalog,
  GoogleSearchInterestOption,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { Skeleton } from '../ui/Skeleton'

type Props = {
  companyId: string
  accountId: string
  includedResourceNames: string[]
  excludedResourceNames: string[]
  setIncludedResourceNames: (value: string[]) => void
  setExcludedResourceNames: (value: string[]) => void
  disabled?: boolean
}

const pretty = (value?: string | null) =>
  value
    ? value
        .replaceAll('_', ' ')
        .toLowerCase()
        .replace(/\b\w/g, (character) => character.toUpperCase())
    : 'Audience segment'

export default function GoogleSearchInterestSelector({
  companyId,
  accountId,
  includedResourceNames,
  excludedResourceNames,
  setIncludedResourceNames,
  setExcludedResourceNames,
  disabled = false,
}: Props) {
  const [query, setQuery] = useState('')
  const [catalog, setCatalog] = useState<GoogleSearchInterestCatalog | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!accountId) {
      setCatalog(null)
      return
    }

    const handle = window.setTimeout(() => {
      setLoading(true)
      setError('')
      void adCenterApi
        .googleSearchInterestCatalog(companyId, accountId, query.trim(), 50)
        .then(setCatalog)
        .catch((catalogError) =>
          setError(apiErrorMessage(catalogError, 'Unable to load Google Search audience segments')),
        )
        .finally(() => setLoading(false))
    }, 250)

    return () => window.clearTimeout(handle)
  }, [companyId, accountId, query])

  const interests = useMemo(() => catalog?.interests ?? [], [catalog?.interests])

  const toggleIncluded = (option: GoogleSearchInterestOption, checked: boolean) => {
    const resource = option.resourceName
    setIncludedResourceNames(
      checked
        ? [...new Set([...includedResourceNames, resource])]
        : includedResourceNames.filter((value) => value !== resource),
    )
    if (checked) {
      setExcludedResourceNames(excludedResourceNames.filter((value) => value !== resource))
    }
  }

  const toggleExcluded = (option: GoogleSearchInterestOption, checked: boolean) => {
    const resource = option.resourceName
    setExcludedResourceNames(
      checked
        ? [...new Set([...excludedResourceNames, resource])]
        : excludedResourceNames.filter((value) => value !== resource),
    )
    if (checked) {
      setIncludedResourceNames(includedResourceNames.filter((value) => value !== resource))
    }
  }

  return (
    <div className="rounded-2xl border border-slate-200 p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-sm font-bold text-slate-900">Affinity &amp; In-market segments</p>
          <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-500">
            These are provider-backed Google segments. Search uses the same Observation/Targeting
            mode configured above.
          </p>
        </div>
        <span className="rounded-full bg-slate-100 px-2.5 py-1 text-[10px] font-semibold text-slate-600">
          {includedResourceNames.length} included · {excludedResourceNames.length} excluded
        </span>
      </div>

      <label className="relative mt-4 block">
        <FiSearch
          className="pointer-events-none absolute left-3 top-3.5 text-slate-400"
          aria-hidden="true"
        />
        <input
          className="form-input pl-9"
          value={query}
          disabled={disabled || !accountId}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search affinity or in-market segments"
        />
      </label>

      {!accountId && (
        <p className="mt-3 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
          Choose a Google Ads account first.
        </p>
      )}

      {error && (
        <p className="mt-3 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
          {error}
        </p>
      )}

      {catalog?.customSegmentsNote && (
        <p className="mt-3 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-[11px] leading-5 text-slate-600">
          {catalog.customSegmentsNote}
        </p>
      )}

      {loading ? (
        <div className="mt-4 space-y-2" role="status" aria-label="Loading Google segments">
        <Skeleton className="h-9 w-full" />
        <Skeleton className="h-9 w-full" />
        <Skeleton className="h-9 w-full" />
      </div>
      ) : interests.length === 0 && accountId ? (
        <p className="mt-4 text-xs text-slate-500">
          No Search-compatible Affinity or In-market segments matched this search.
        </p>
      ) : (
        <div className="mt-4 max-h-96 space-y-2 overflow-y-auto pr-1">
          {interests.map((option) => {
            const included = includedResourceNames.includes(option.resourceName)
            const excluded = excludedResourceNames.includes(option.resourceName)
            return (
              <div
                key={option.resourceName}
                className="flex flex-wrap items-start justify-between gap-3 rounded-xl border border-slate-200 px-3 py-3"
              >
                <div className="min-w-0">
                  <p className="text-xs font-semibold text-slate-800">{option.name ?? option.id}</p>
                  <p className="mt-1 text-[11px] text-slate-500">
                    {pretty(option.taxonomyType)}
                    {option.launchedToAll ? ' · All-channel availability' : ' · Search available'}
                  </p>
                </div>
                <div className="flex flex-wrap gap-3">
                  <label className="inline-flex items-center gap-2 text-xs font-medium text-slate-600">
                    <input
                      type="checkbox"
                      checked={included}
                      disabled={disabled}
                      onChange={(event) => toggleIncluded(option, event.target.checked)}
                    />
                    Include
                  </label>
                  <label className="inline-flex items-center gap-2 text-xs font-medium text-slate-600">
                    <input
                      type="checkbox"
                      checked={excluded}
                      disabled={disabled}
                      onChange={(event) => toggleExcluded(option, event.target.checked)}
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
  )
}
