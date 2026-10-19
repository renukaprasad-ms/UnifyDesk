import { useSnackbarState } from '../feedback/useSnackbarState'
import { useState } from 'react'
import { FiSearch, FiX } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  MetaDetailedTargetingSuggestion,
  MetaTargetingInterest,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'

type Props = {
  companyId: string
  accountId: string
  selected: MetaTargetingInterest[]
  disabled?: boolean
  onChange: (value: MetaTargetingInterest[]) => void
}

export default function MetaDetailedTargetingEditor({
  companyId,
  accountId,
  selected,
  disabled,
  onChange,
}: Props) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<MetaDetailedTargetingSuggestion[]>([])
  const [loading, setLoading] = useState(false)
  const [, setError] = useSnackbarState('', 'error')

  const search = async () => {
    const normalized = query.trim()
    if (!normalized) return
    setLoading(true)
    setError('')
    try {
      const suggestions = await adCenterApi.metaInterestSuggestions(
        companyId,
        accountId,
        normalized,
      )
      setResults(suggestions)
    } catch (searchError) {
      setError(apiErrorMessage(searchError, 'Unable to search Meta interests'))
    } finally {
      setLoading(false)
    }
  }

  const add = (suggestion: MetaDetailedTargetingSuggestion) => {
    if (selected.some((item) => item.id === suggestion.id)) return
    onChange([...selected, { id: suggestion.id, name: suggestion.name }])
  }

  const remove = (id: string) => {
    onChange(selected.filter((item) => item.id !== id))
  }

  return (
    <section className="rounded-2xl border border-slate-200 p-4">
      <div>
        <p className="text-sm font-bold text-slate-900">Meta interests</p>
        <p className="mt-1 text-xs leading-5 text-slate-500">
          Search Meta's provider-backed targeting interests instead of relying on free-text labels.
          Selected interests are sent to Meta during unified paused launch.
        </p>
      </div>

      {selected.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-2">
          {selected.map((item) => (
            <span
              key={item.id}
              className="inline-flex items-center gap-2 rounded-full bg-indigo-50 px-3 py-1.5 text-xs font-semibold text-indigo-700"
            >
              {item.name}
              <button
                type="button"
                disabled={disabled}
                className="text-indigo-400 hover:text-indigo-700 disabled:opacity-40"
                onClick={() => remove(item.id)}
                aria-label={'Remove ' + item.name}
              >
                <FiX aria-hidden="true" />
              </button>
            </span>
          ))}
        </div>
      )}

      <div className="mt-4 flex gap-2">
        <input
          className="form-input flex-1"
          value={query}
          disabled={disabled}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              event.preventDefault()
              void search()
            }
          }}
          placeholder="Search interests, e.g. fitness, SaaS, fashion"
        />
        <button
          type="button"
          className="secondary-button inline-flex items-center gap-2"
          disabled={disabled || loading || !query.trim()}
          onClick={() => void search()}
        >
          <FiSearch aria-hidden="true" />
          {loading ? 'Searching…' : 'Search'}
        </button>
      </div>



      {results.length > 0 && (
        <div className="mt-3 max-h-56 divide-y divide-slate-100 overflow-y-auto rounded-xl border border-slate-200">
          {results.map((item) => {
            const active = selected.some((selectedItem) => selectedItem.id === item.id)
            return (
              <button
                key={item.id}
                type="button"
                disabled={disabled || active}
                className="flex w-full items-center justify-between gap-3 px-3 py-3 text-left hover:bg-slate-50 disabled:cursor-not-allowed disabled:bg-slate-50"
                onClick={() => add(item)}
              >
                <span>
                  <span className="block text-sm font-semibold text-slate-800">{item.name}</span>
                  {(item.audienceSizeLowerBound != null || item.audienceSizeUpperBound != null) && (
                    <span className="mt-0.5 block text-[11px] text-slate-400">
                      Estimated Meta audience size:{' '}
                      {item.audienceSizeLowerBound?.toLocaleString() ?? '—'} –{' '}
                      {item.audienceSizeUpperBound?.toLocaleString() ?? '—'}
                    </span>
                  )}
                </span>
                <span className="text-xs font-semibold text-indigo-600">
                  {active ? 'Selected' : 'Add'}
                </span>
              </button>
            )
          })}
        </div>
      )}
    </section>
  )
}
