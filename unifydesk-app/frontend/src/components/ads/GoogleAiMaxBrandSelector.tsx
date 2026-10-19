import { useSnackbarState } from '../feedback/useSnackbarState'
import { useState } from 'react'
import { FiPlus, FiSearch, FiX } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type { GoogleBrandSuggestion } from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'

type Props = {
  companyId: string
  accountId: string
  inclusions: GoogleBrandSuggestion[]
  exclusions: GoogleBrandSuggestion[]
  setInclusions: (value: GoogleBrandSuggestion[]) => void
  setExclusions: (value: GoogleBrandSuggestion[]) => void
  disabled: boolean
}

export default function GoogleAiMaxBrandSelector(props: Props) {
  const [query, setQuery] = useState('')
  const [mode, setMode] = useState<'INCLUDE' | 'EXCLUDE'>('INCLUDE')
  const [suggestions, setSuggestions] = useState<GoogleBrandSuggestion[]>([])
  const [loading, setLoading] = useState(false)
  const [, setError] = useSnackbarState('', 'error')

  const selectedIds = [...props.inclusions, ...props.exclusions].map((item) => item.id)

  const search = async () => {
    const prefix = query.trim()
    if (!prefix) return
    setLoading(true)
    setError('')
    try {
      setSuggestions(
        await adCenterApi.suggestGoogleAiMaxBrands(
          props.companyId,
          props.accountId,
          prefix,
          selectedIds,
        ),
      )
    } catch (searchError) {
      setError(apiErrorMessage(searchError, 'Unable to suggest Google brands'))
    } finally {
      setLoading(false)
    }
  }

  const select = (brand: GoogleBrandSuggestion) => {
    if (mode === 'INCLUDE') {
      props.setInclusions([...props.inclusions, brand])
    } else {
      props.setExclusions([...props.exclusions, brand])
    }
    setSuggestions((current) => current.filter((item) => item.id !== brand.id))
  }

  const remove = (id: string, target: 'INCLUDE' | 'EXCLUDE') => {
    if (target === 'INCLUDE') {
      props.setInclusions(props.inclusions.filter((item) => item.id !== id))
    } else {
      props.setExclusions(props.exclusions.filter((item) => item.id !== id))
    }
  }

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4 sm:col-span-2">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-bold text-slate-800">Brand controls</p>
          <p className="mt-1 text-[11px] leading-5 text-slate-500">
            Select verified Google brands. Inclusions restrict this Search campaign to selected
            brands; exclusions prevent matching selected brands.
          </p>
        </div>
        <select
          className="form-input w-36"
          value={mode}
          disabled={props.disabled}
          onChange={(event) => setMode(event.target.value as 'INCLUDE' | 'EXCLUDE')}
        >
          <option value="INCLUDE">Add inclusion</option>
          <option value="EXCLUDE">Add exclusion</option>
        </select>
      </div>

      <div className="mt-3 flex gap-2">
        <div className="relative flex-1">
          <FiSearch
            className="pointer-events-none absolute left-3 top-3 text-slate-400"
            aria-hidden="true"
          />
          <input
            className="form-input pl-9"
            value={query}
            disabled={props.disabled}
            placeholder="Search verified Google brand"
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                event.preventDefault()
                void search()
              }
            }}
          />
        </div>
        <button
          type="button"
          className="secondary-button"
          disabled={props.disabled || loading || !query.trim()}
          onClick={() => void search()}
        >
          {loading ? 'Searching…' : 'Search'}
        </button>
      </div>



      {suggestions.length > 0 && (
        <div className="mt-3 grid gap-2 rounded-xl border border-slate-100 bg-slate-50 p-2">
          {suggestions.slice(0, 10).map((brand) => (
            <button
              key={brand.id}
              type="button"
              className="flex items-center justify-between gap-3 rounded-lg bg-white px-3 py-2 text-left hover:bg-slate-50"
              disabled={props.disabled}
              onClick={() => select(brand)}
            >
              <div className="min-w-0">
                <p className="truncate text-xs font-semibold text-slate-800">{brand.name}</p>
                <p className="mt-0.5 truncate text-[10px] text-slate-400">
                  {brand.urls?.[0] || brand.state || 'Verified Google brand'}
                </p>
              </div>
              <FiPlus className="shrink-0 text-indigo-600" aria-hidden="true" />
            </button>
          ))}
        </div>
      )}

      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        <BrandList
          title="Included brands"
          empty="No brand inclusions. The campaign is not restricted to named brands."
          values={props.inclusions}
          disabled={props.disabled}
          onRemove={(id) => remove(id, 'INCLUDE')}
        />
        <BrandList
          title="Excluded brands"
          empty="No brand exclusions."
          values={props.exclusions}
          disabled={props.disabled}
          onRemove={(id) => remove(id, 'EXCLUDE')}
        />
      </div>
    </div>
  )
}

function BrandList({
  title,
  empty,
  values,
  disabled,
  onRemove,
}: {
  title: string
  empty: string
  values: GoogleBrandSuggestion[]
  disabled: boolean
  onRemove: (id: string) => void
}) {
  return (
    <div className="rounded-xl border border-slate-100 p-3">
      <p className="text-[11px] font-bold text-slate-700">{title}</p>
      {values.length === 0 ? (
        <p className="mt-2 text-[11px] leading-5 text-slate-400">{empty}</p>
      ) : (
        <div className="mt-2 flex flex-wrap gap-2">
          {values.map((brand) => (
            <span
              key={brand.id}
              className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-semibold text-slate-700"
            >
              {brand.name}
              <button
                type="button"
                className="rounded-full p-0.5 hover:bg-slate-200"
                disabled={disabled}
                onClick={() => onRemove(brand.id)}
                aria-label={'Remove ' + brand.name}
              >
                <FiX aria-hidden="true" />
              </button>
            </span>
          ))}
        </div>
      )}
    </div>
  )
}
