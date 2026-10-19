import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useMemo, useState } from 'react'
import { FiPlus, FiSearch, FiTrash2 } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  MetaGeoLocationSuggestion,
  MetaGeoTargetType,
  MetaTargetingLocation,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'

type Props = {
  companyId: string
  accountId: string
  locations: MetaTargetingLocation[]
  onChange: (locations: MetaTargetingLocation[]) => void
  disabled?: boolean
}

const identity = (location: MetaTargetingLocation) => {
  const type = location.type ?? 'COUNTRY'
  if (type === 'CUSTOM') {
    return `CUSTOM|${location.latitude ?? ''}|${location.longitude ?? ''}|${location.radiusKm ?? ''}`
  }
  return `${type}|${location.key ?? location.countryCode ?? location.name ?? ''}`
}

const prettyType = (type?: string | null) =>
  (type ?? 'COUNTRY')
    .toLowerCase()
    .replaceAll('_', ' ')
    .replace(/\b\w/g, (value) => value.toUpperCase())

export default function MetaGeoTargetingEditor({
  companyId,
  accountId,
  locations,
  onChange,
  disabled = false,
}: Props) {
  const [countryCode, setCountryCode] = useState('IN')
  const [searchCountryCode, setSearchCountryCode] = useState('IN')
  const [searchType, setSearchType] = useState<'REGION' | 'CITY' | 'ZIP'>('CITY')
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<MetaGeoLocationSuggestion[]>([])
  const [searching, setSearching] = useState(false)
  const [, setError] = useSnackbarState('', 'error')
  const [latitude, setLatitude] = useState('')
  const [longitude, setLongitude] = useState('')
  const [radius, setRadius] = useState('10')
  const [radiusName, setRadiusName] = useState('')

  const locationTypes = useMemo<Array<'home' | 'recent'>>(() => {
    const values = new Set<'home' | 'recent'>()
    locations.forEach((location) =>
      (location.locationTypes ?? []).forEach((value) => {
        if (value === 'home' || value === 'recent') values.add(value)
      }),
    )
    return values.size ? [...values] : ['home', 'recent']
  }, [locations])

  useEffect(() => {
    const firstCountry = locations.find((location) => location.countryCode)?.countryCode
    if (firstCountry) {
      setCountryCode(firstCountry)
      setSearchCountryCode(firstCountry)
    }
  }, [locations])

  const replaceLocationTypes = (next: Array<'home' | 'recent'>) => {
    if (next.length === 0) return
    onChange(
      locations.map((location) => ({
        ...location,
        locationTypes: next,
      })),
    )
  }

  const addLocation = (location: MetaTargetingLocation) => {
    const next = {
      ...location,
      locationTypes,
    }
    const key = identity(next)
    if (locations.some((item) => identity(item) === key)) return
    onChange([...locations, next])
  }

  const addCountry = () => {
    const normalized = countryCode.trim().toUpperCase()
    if (!/^[A-Z]{2}$/.test(normalized)) {
      setError('Use a two-letter ISO country code such as IN or US.')
      return
    }
    setError('')
    addLocation({
      countryCode: normalized,
      type: 'COUNTRY',
      key: normalized,
      name: normalized,
    })
  }

  const search = async () => {
    const normalizedCountry = searchCountryCode.trim().toUpperCase()
    if (query.trim().length < 2) {
      setError('Enter at least two characters to search Meta locations.')
      return
    }
    if (normalizedCountry && !/^[A-Z]{2}$/.test(normalizedCountry)) {
      setError('Country filter must be a two-letter ISO code.')
      return
    }
    setSearching(true)
    setError('')
    try {
      setResults(
        await adCenterApi.metaGeoLocations(
          companyId,
          accountId,
          query.trim(),
          [searchType],
          normalizedCountry || undefined,
          20,
        ),
      )
    } catch (searchError) {
      setError(apiErrorMessage(searchError, 'Unable to search Meta locations'))
      setResults([])
    } finally {
      setSearching(false)
    }
  }

  const addSuggestion = (suggestion: MetaGeoLocationSuggestion) => {
    const type = suggestion.type.toUpperCase() as MetaGeoTargetType
    if (!['COUNTRY', 'REGION', 'CITY', 'ZIP'].includes(type)) return
    addLocation({
      countryCode: suggestion.countryCode ?? (searchCountryCode.trim().toUpperCase() || null),
      type,
      key: suggestion.key,
      name: suggestion.name,
    })
  }

  const addRadius = () => {
    const lat = Number(latitude)
    const lng = Number(longitude)
    const radiusKm = Number(radius)
    if (
      !Number.isFinite(lat) ||
      lat < -90 ||
      lat > 90 ||
      !Number.isFinite(lng) ||
      lng < -180 ||
      lng > 180 ||
      !Number.isFinite(radiusKm) ||
      radiusKm <= 0
    ) {
      setError('Enter valid latitude, longitude, and a positive radius.')
      return
    }
    const normalizedCountry = countryCode.trim().toUpperCase()
    if (normalizedCountry && !/^[A-Z]{2}$/.test(normalizedCountry)) {
      setError('Country code must be a two-letter ISO code.')
      return
    }
    setError('')
    addLocation({
      countryCode: normalizedCountry || null,
      type: 'CUSTOM',
      name: radiusName.trim() || `${lat}, ${lng}`,
      latitude: lat,
      longitude: lng,
      radiusKm,
    })
    setLatitude('')
    setLongitude('')
    setRadiusName('')
  }

  return (
    <div className="rounded-2xl border border-slate-200 p-4 sm:col-span-2">
      <div>
        <h4 className="text-sm font-bold text-slate-900">Geo targeting</h4>
        <p className="mt-1 text-xs leading-5 text-slate-500">
          Add countries directly, search Meta for provider-valid regions/cities/postal codes, or
          target a coordinate radius.
        </p>
      </div>



      <div className="mt-4 rounded-xl bg-slate-50 p-3">
        <p className="text-xs font-semibold text-slate-700">People in these locations</p>
        <div className="mt-2 flex flex-wrap gap-4">
          {(['home', 'recent'] as const).map((value) => {
            const checked = locationTypes.includes(value)
            return (
              <label key={value} className="inline-flex items-center gap-2 text-sm text-slate-600">
                <input
                  type="checkbox"
                  checked={checked}
                  disabled={disabled}
                  onChange={(event) => {
                    const next = event.target.checked
                      ? [...new Set([...locationTypes, value])]
                      : locationTypes.filter((item) => item !== value)
                    replaceLocationTypes(next)
                  }}
                />
                {value === 'home' ? 'Lives here' : 'Recently in this location'}
              </label>
            )
          })}
        </div>
      </div>

      <div className="mt-4 grid gap-3 sm:grid-cols-[120px_1fr_auto]">
        <select className="form-input" value="COUNTRY" disabled>
          <option value="COUNTRY">Country</option>
        </select>
        <input
          className="form-input uppercase"
          value={countryCode}
          maxLength={2}
          disabled={disabled}
          placeholder="IN"
          onChange={(event) => setCountryCode(event.target.value.toUpperCase())}
        />
        <button
          type="button"
          className="secondary-button inline-flex items-center justify-center gap-2"
          disabled={disabled}
          onClick={addCountry}
        >
          <FiPlus aria-hidden="true" />
          Add
        </button>
      </div>

      <div className="mt-4 border-t border-slate-100 pt-4">
        <p className="text-xs font-semibold text-slate-700">Search Meta locations</p>
        <div className="mt-2 grid gap-2 sm:grid-cols-[120px_100px_1fr_auto]">
          <select
            className="form-input"
            value={searchType}
            disabled={disabled}
            onChange={(event) => setSearchType(event.target.value as 'REGION' | 'CITY' | 'ZIP')}
          >
            <option value="REGION">Region</option>
            <option value="CITY">City</option>
            <option value="ZIP">Postal</option>
          </select>
          <input
            className="form-input uppercase"
            value={searchCountryCode}
            maxLength={2}
            disabled={disabled}
            placeholder="IN"
            onChange={(event) => setSearchCountryCode(event.target.value.toUpperCase())}
          />
          <input
            className="form-input"
            value={query}
            disabled={disabled}
            placeholder={searchType === 'ZIP' ? '560001' : 'Bengaluru'}
            onChange={(event) => setQuery(event.target.value)}
          />
          <button
            type="button"
            className="secondary-button inline-flex items-center justify-center gap-2"
            disabled={disabled || searching}
            onClick={() => void search()}
          >
            <FiSearch aria-hidden="true" />
            {searching ? 'Searching…' : 'Search'}
          </button>
        </div>

        {results.length > 0 && (
          <div className="mt-3 max-h-52 overflow-y-auto rounded-xl border border-slate-200 bg-white">
            {results.map((result) => (
              <button
                key={result.type + '-' + result.key}
                type="button"
                className="flex w-full items-start justify-between gap-3 border-b border-slate-100 px-3 py-2.5 text-left last:border-b-0 hover:bg-slate-50"
                disabled={disabled}
                onClick={() => addSuggestion(result)}
              >
                <span>
                  <span className="block text-sm font-semibold text-slate-800">{result.name}</span>
                  <span className="mt-0.5 block text-[11px] text-slate-400">
                    {[result.region, result.countryName ?? result.countryCode]
                      .filter(Boolean)
                      .join(' · ')}
                  </span>
                </span>
                <span className="rounded-full bg-slate-100 px-2 py-1 text-[10px] font-semibold text-slate-500">
                  {prettyType(result.type)}
                </span>
              </button>
            ))}
          </div>
        )}
      </div>

      <div className="mt-4 border-t border-slate-100 pt-4">
        <p className="text-xs font-semibold text-slate-700">Radius targeting</p>
        <div className="mt-2 grid gap-2 sm:grid-cols-2 lg:grid-cols-[1fr_1fr_1fr_1fr_auto]">
          <input
            className="form-input"
            value={radiusName}
            disabled={disabled}
            placeholder="Optional label"
            onChange={(event) => setRadiusName(event.target.value)}
          />
          <input
            className="form-input"
            type="number"
            min="-90"
            max="90"
            step="0.000001"
            value={latitude}
            disabled={disabled}
            placeholder="Latitude"
            onChange={(event) => setLatitude(event.target.value)}
          />
          <input
            className="form-input"
            type="number"
            min="-180"
            max="180"
            step="0.000001"
            value={longitude}
            disabled={disabled}
            placeholder="Longitude"
            onChange={(event) => setLongitude(event.target.value)}
          />
          <input
            className="form-input"
            type="number"
            min="0.1"
            step="0.1"
            value={radius}
            disabled={disabled}
            placeholder="Radius km"
            onChange={(event) => setRadius(event.target.value)}
          />
          <button
            type="button"
            className="secondary-button inline-flex items-center justify-center gap-2"
            disabled={disabled}
            onClick={addRadius}
          >
            <FiPlus aria-hidden="true" />
            Radius
          </button>
        </div>
      </div>

      <div className="mt-4 border-t border-slate-100 pt-4">
        <p className="text-xs font-semibold text-slate-700">
          Selected locations ({locations.length})
        </p>
        {locations.length === 0 ? (
          <p className="mt-2 rounded-xl bg-slate-50 px-3 py-3 text-xs text-slate-500">
            Add at least one location.
          </p>
        ) : (
          <div className="mt-2 grid gap-2 sm:grid-cols-2">
            {locations.map((location, index) => (
              <div
                key={identity(location) + '-' + index}
                className="flex items-start justify-between gap-3 rounded-xl border border-slate-200 px-3 py-2.5"
              >
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold text-slate-800">
                    {location.name ??
                      location.countryCode ??
                      (location.type === 'CUSTOM'
                        ? `${location.latitude}, ${location.longitude}`
                        : location.key)}
                  </p>
                  <p className="mt-0.5 text-[11px] text-slate-400">
                    {prettyType(location.type)}
                    {location.type === 'CUSTOM' && location.radiusKm
                      ? ` · ${location.radiusKm} km`
                      : ''}
                    {location.countryCode ? ` · ${location.countryCode}` : ''}
                  </p>
                </div>
                <button
                  type="button"
                  className="rounded-lg p-2 text-slate-400 hover:bg-red-50 hover:text-red-600"
                  disabled={disabled}
                  aria-label="Remove location"
                  onClick={() => onChange(locations.filter((_, itemIndex) => itemIndex !== index))}
                >
                  <FiTrash2 aria-hidden="true" />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
