import { useSnackbarState } from '../feedback/useSnackbarState'
import {
  useEffect,
  useMemo,
  useState,
  type Dispatch,
  type FormEvent,
  type SetStateAction,
} from 'react'
import { FiPlus, FiTrash2, FiX } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdAccount,
  AdConversion,
  CreateGoogleAppCampaignInput,
  GoogleAppBiddingGoal,
  GoogleAppCampaignSubtype,
  GoogleAppStore,
  ReachlyoAudienceLocationInput,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'

type Props = {
  companyId: string
  account: AdAccount
  open: boolean
  saving: boolean
  onClose: () => void
  onCreate: (input: CreateGoogleAppCampaignInput) => Promise<void>
}

type LocationRow = ReachlyoAudienceLocationInput & { id: string }

const newLocation = (): LocationRow => ({
  id: crypto.randomUUID(),
  countryCode: 'IN',
  region: '',
  city: '',
  postalCode: '',
})

const toMinor = (value: string, currency?: string | null) => {
  const numeric = Number(value)
  if (!Number.isFinite(numeric) || numeric <= 0) return null
  if (!currency) return Math.round(numeric)
  try {
    const digits =
      new Intl.NumberFormat(undefined, { style: 'currency', currency }).resolvedOptions()
        .maximumFractionDigits ?? 2
    return Math.round(numeric * 10 ** digits)
  } catch {
    return Math.round(numeric)
  }
}

const normalizeLocation = (value: LocationRow): ReachlyoAudienceLocationInput => ({
  countryCode: value.countryCode.trim().toUpperCase(),
  region: value.region?.trim() || null,
  city: value.city?.trim() || null,
  postalCode: value.postalCode?.trim() || null,
})

const GOALS: Array<{
  value: GoogleAppBiddingGoal
  label: string
  subtype: GoogleAppCampaignSubtype[]
}> = [
  {
    value: 'OPTIMIZE_INSTALLS_TARGET_INSTALL_COST',
    label: 'Installs · target cost per install',
    subtype: ['APP_CAMPAIGN'],
  },
  {
    value: 'OPTIMIZE_INSTALLS_WITHOUT_TARGET_INSTALL_COST',
    label: 'Installs · maximize volume',
    subtype: ['APP_CAMPAIGN'],
  },
  {
    value: 'OPTIMIZE_IN_APP_CONVERSIONS_TARGET_INSTALL_COST',
    label: 'Install + in-app action · target install cost',
    subtype: ['APP_CAMPAIGN'],
  },
  {
    value: 'OPTIMIZE_IN_APP_CONVERSIONS_TARGET_CONVERSION_COST',
    label: 'In-app actions · target CPA',
    subtype: ['APP_CAMPAIGN', 'APP_CAMPAIGN_FOR_ENGAGEMENT'],
  },
  {
    value: 'OPTIMIZE_IN_APP_CONVERSIONS_WITHOUT_TARGET_CPA',
    label: 'In-app actions · maximize conversions',
    subtype: ['APP_CAMPAIGN'],
  },
  {
    value: 'OPTIMIZE_RETURN_ON_ADVERTISING_SPEND',
    label: 'In-app value · target ROAS',
    subtype: ['APP_CAMPAIGN'],
  },
  {
    value: 'OPTIMIZE_TOTAL_VALUE_WITHOUT_TARGET_ROAS',
    label: 'In-app value · maximize conversion value',
    subtype: ['APP_CAMPAIGN'],
  },
  {
    value: 'OPTIMIZE_PRE_REGISTRATION_CONVERSION_VOLUME',
    label: 'Pre-registration volume',
    subtype: ['APP_CAMPAIGN_FOR_PRE_REGISTRATION'],
  },
]

const needsTargetCpa = (goal: GoogleAppBiddingGoal) =>
  [
    'OPTIMIZE_INSTALLS_TARGET_INSTALL_COST',
    'OPTIMIZE_IN_APP_CONVERSIONS_TARGET_INSTALL_COST',
    'OPTIMIZE_IN_APP_CONVERSIONS_TARGET_CONVERSION_COST',
    'OPTIMIZE_PRE_REGISTRATION_CONVERSION_VOLUME',
  ].includes(goal)

const needsTargetRoas = (goal: GoogleAppBiddingGoal) =>
  goal === 'OPTIMIZE_RETURN_ON_ADVERTISING_SPEND'

const needsConversions = (subtype: GoogleAppCampaignSubtype, goal: GoogleAppBiddingGoal) =>
  subtype === 'APP_CAMPAIGN_FOR_ENGAGEMENT' ||
  [
    'OPTIMIZE_IN_APP_CONVERSIONS_TARGET_INSTALL_COST',
    'OPTIMIZE_IN_APP_CONVERSIONS_TARGET_CONVERSION_COST',
    'OPTIMIZE_IN_APP_CONVERSIONS_WITHOUT_TARGET_CPA',
    'OPTIMIZE_RETURN_ON_ADVERTISING_SPEND',
    'OPTIMIZE_TOTAL_VALUE_WITHOUT_TARGET_ROAS',
  ].includes(goal)

export default function GoogleAppCampaignModal({
  companyId,
  account,
  open,
  saving,
  onClose,
  onCreate,
}: Props) {
  const [name, setName] = useState('')
  const [subtype, setSubtype] = useState<GoogleAppCampaignSubtype>('APP_CAMPAIGN')
  const [appStore, setAppStore] = useState<GoogleAppStore>('GOOGLE_APP_STORE')
  const [appId, setAppId] = useState('')
  const [goal, setGoal] = useState<GoogleAppBiddingGoal>('OPTIMIZE_INSTALLS_TARGET_INSTALL_COST')
  const [budget, setBudget] = useState('')
  const [targetCpa, setTargetCpa] = useState('')
  const [targetRoas, setTargetRoas] = useState('')
  const [conversions, setConversions] = useState<AdConversion[]>([])
  const [selectedConversions, setSelectedConversions] = useState<string[]>([])
  const [locations, setLocations] = useState<LocationRow[]>([newLocation()])
  const [excludedLocations, setExcludedLocations] = useState<LocationRow[]>([])
  const [languages, setLanguages] = useState('en')
  const [containsEuPoliticalAdvertising, setContainsEuPoliticalAdvertising] = useState<
    'YES' | 'NO' | ''
  >('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [loadError, setLoadError] = useSnackbarState('', 'error')

  const appConversions = useMemo(
    () =>
      conversions.filter(
        (conversion) =>
          conversion.accessible &&
          !conversion.archived &&
          !conversion.unavailable &&
          conversion.eventSourceType === 'APP',
      ),
    [conversions],
  )

  const goalOptions = useMemo(
    () => GOALS.filter((option) => option.subtype.includes(subtype)),
    [subtype],
  )

  useEffect(() => {
    if (!open) return
    setLoadError('')
    void adCenterApi
      .conversions(companyId, account.id)
      .then(setConversions)
      .catch((error) =>
        setLoadError(
          apiErrorMessage(
            error,
            'Unable to load Google app conversion actions. Sync conversions in Ad Center and try again.',
          ),
        ),
      )
  }, [open, companyId, account.id])

  useEffect(() => {
    const first = goalOptions[0]?.value
    if (!goalOptions.some((option) => option.value === goal) && first) {
      setGoal(first)
    }
    if (subtype === 'APP_CAMPAIGN_FOR_PRE_REGISTRATION') {
      setAppStore('GOOGLE_APP_STORE')
      setSelectedConversions([])
    }
  }, [subtype, goalOptions])

  if (!open) return null

  const changeLocation = (
    setter: Dispatch<SetStateAction<LocationRow[]>>,
    id: string,
    patch: Partial<LocationRow>,
  ) => setter((current) => current.map((item) => (item.id === id ? { ...item, ...patch } : item)))

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const dailyBudgetMinor = toMinor(budget, account.currency)
    if (dailyBudgetMinor == null) return
    const targetCpaMinor = targetCpa ? toMinor(targetCpa, account.currency) : null
    await onCreate({
      name: name.trim(),
      advertisingChannelSubType: subtype,
      appId: appId.trim(),
      appStore,
      biddingStrategyGoalType: goal,
      dailyBudgetMinor,
      targetCpaMinor,
      targetRoas: targetRoas ? Number(targetRoas) : null,
      conversionActionIds: selectedConversions,
      locations: locations.map(normalizeLocation),
      excludedLocations: excludedLocations.map(normalizeLocation),
      languages: languages
        .split(',')
        .map((item) => item.trim())
        .filter(Boolean),
      containsEuPoliticalAdvertising: containsEuPoliticalAdvertising === 'YES',
      startDate: startDate || null,
      endDate: endDate || null,
    })
  }

  const complete =
    name.trim() &&
    appId.trim() &&
    budget &&
    containsEuPoliticalAdvertising &&
    (!needsTargetCpa(goal) || targetCpa) &&
    (!needsTargetRoas(goal) || targetRoas) &&
    (!needsConversions(subtype, goal) || selectedConversions.length > 0)

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet max-h-[94vh] w-full max-w-4xl overflow-y-auto rounded-2xl bg-white shadow-2xl">
        <div className="sticky top-0 z-10 flex items-start justify-between border-b border-slate-100 bg-white px-6 py-5">
          <div>
            <p className="eyebrow">Google Ads · App</p>
            <h2 className="mt-1 text-xl font-bold text-slate-950">Create App campaign</h2>
            <p className="mt-1 text-sm text-slate-500">
              Installs, in-app actions/value, engagement, and Android pre-registration.
            </p>
          </div>
          <button
            type="button"
            className="rounded-lg p-2 text-slate-400 hover:bg-slate-100"
            onClick={onClose}
          >
            <FiX size={18} aria-hidden="true" />
          </button>
        </div>

        <form className="space-y-6 p-6" onSubmit={submit}>
          {loadError && (
            <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs text-amber-800">
              {loadError}
            </div>
          )}

          <div className="grid gap-4 sm:grid-cols-2">
            <label className="text-xs font-semibold text-slate-700">
              Campaign name
              <input
                className="form-input mt-2"
                value={name}
                maxLength={255}
                required
                autoFocus
                onChange={(event) => setName(event.target.value)}
              />
            </label>

            <label className="text-xs font-semibold text-slate-700">
              App campaign type
              <select
                className="form-input mt-2"
                value={subtype}
                onChange={(event) => setSubtype(event.target.value as GoogleAppCampaignSubtype)}
              >
                <option value="APP_CAMPAIGN">Installs / in-app actions</option>
                <option value="APP_CAMPAIGN_FOR_ENGAGEMENT">App engagement</option>
                <option value="APP_CAMPAIGN_FOR_PRE_REGISTRATION">Pre-registration</option>
              </select>
            </label>

            <label className="text-xs font-semibold text-slate-700">
              App store
              <select
                className="form-input mt-2"
                value={appStore}
                disabled={subtype === 'APP_CAMPAIGN_FOR_PRE_REGISTRATION'}
                onChange={(event) => setAppStore(event.target.value as GoogleAppStore)}
              >
                <option value="GOOGLE_APP_STORE">Google Play</option>
                <option value="APPLE_APP_STORE">Apple App Store</option>
              </select>
            </label>

            <label className="text-xs font-semibold text-slate-700">
              {appStore === 'GOOGLE_APP_STORE' ? 'Android package name' : 'Apple App Store ID'}
              <input
                className="form-input mt-2"
                value={appId}
                required
                placeholder={appStore === 'GOOGLE_APP_STORE' ? 'com.example.app' : '123456789'}
                onChange={(event) => setAppId(event.target.value)}
              />
            </label>

            <label className="text-xs font-semibold text-slate-700 sm:col-span-2">
              Optimization goal
              <select
                className="form-input mt-2"
                value={goal}
                onChange={(event) => setGoal(event.target.value as GoogleAppBiddingGoal)}
              >
                {goalOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>

            <label className="text-xs font-semibold text-slate-700">
              Daily budget {account.currency ? `(${account.currency})` : ''}
              <input
                className="form-input mt-2"
                type="number"
                min="0.01"
                step="0.01"
                value={budget}
                required
                onChange={(event) => setBudget(event.target.value)}
              />
            </label>

            {needsTargetCpa(goal) && (
              <label className="text-xs font-semibold text-slate-700">
                Target CPA / install cost {account.currency ? `(${account.currency})` : ''}
                <input
                  className="form-input mt-2"
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={targetCpa}
                  required
                  onChange={(event) => setTargetCpa(event.target.value)}
                />
              </label>
            )}

            {needsTargetRoas(goal) && (
              <label className="text-xs font-semibold text-slate-700">
                Target ROAS
                <input
                  className="form-input mt-2"
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={targetRoas}
                  required
                  placeholder="3.5"
                  onChange={(event) => setTargetRoas(event.target.value)}
                />
              </label>
            )}

            <label className="text-xs font-semibold text-slate-700">
              Start date
              <input
                className="form-input mt-2"
                type="date"
                value={startDate}
                onChange={(event) => setStartDate(event.target.value)}
              />
            </label>
            <label className="text-xs font-semibold text-slate-700">
              End date
              <input
                className="form-input mt-2"
                type="date"
                value={endDate}
                onChange={(event) => setEndDate(event.target.value)}
              />
            </label>
          </div>

          {subtype !== 'APP_CAMPAIGN_FOR_PRE_REGISTRATION' && (
            <div>
              <h3 className="text-sm font-bold text-slate-950">App conversion actions</h3>
              <p className="mt-1 text-xs text-slate-500">
                Required for engagement and in-app action/value goals. Google validates the
                conversion origin and eligibility during creation.
              </p>
              <div className="mt-3 grid gap-2 sm:grid-cols-2">
                {appConversions.length === 0 ? (
                  <p className="text-xs text-slate-500">
                    No active App-origin Google conversion actions are synchronized.
                  </p>
                ) : (
                  appConversions.map((conversion) => (
                    <label
                      key={conversion.id}
                      className="flex items-start gap-3 rounded-xl border border-slate-200 p-3"
                    >
                      <input
                        type="checkbox"
                        className="mt-1"
                        checked={selectedConversions.includes(conversion.externalConversionId)}
                        onChange={(event) =>
                          setSelectedConversions((current) =>
                            event.target.checked
                              ? [...current, conversion.externalConversionId]
                              : current.filter((item) => item !== conversion.externalConversionId),
                          )
                        }
                      />
                      <span>
                        <span className="block text-xs font-semibold text-slate-900">
                          {conversion.name}
                        </span>
                        <span className="mt-1 block text-[11px] text-slate-500">
                          {conversion.customEventType ?? 'App conversion'} ·{' '}
                          {conversion.externalConversionId}
                        </span>
                      </span>
                    </label>
                  ))
                )}
              </div>
            </div>
          )}

          <LocationEditor
            title="Included locations"
            rows={locations}
            onAdd={() => setLocations((current) => [...current, newLocation()])}
            onRemove={(id) => setLocations((current) => current.filter((item) => item.id !== id))}
            onChange={(id, patch) => changeLocation(setLocations, id, patch)}
          />

          <LocationEditor
            title="Excluded locations"
            rows={excludedLocations}
            onAdd={() => setExcludedLocations((current) => [...current, newLocation()])}
            onRemove={(id) =>
              setExcludedLocations((current) => current.filter((item) => item.id !== id))
            }
            onChange={(id, patch) => changeLocation(setExcludedLocations, id, patch)}
          />

          <label className="block text-xs font-semibold text-slate-700">
            Languages
            <input
              className="form-input mt-2"
              value={languages}
              placeholder="en, hi"
              onChange={(event) => setLanguages(event.target.value)}
            />
          </label>

          <label className="block rounded-xl border border-slate-200 p-4 text-xs font-semibold text-slate-700">
            EU political advertising
            <select
              className="form-input mt-2"
              value={containsEuPoliticalAdvertising}
              required
              onChange={(event) =>
                setContainsEuPoliticalAdvertising(event.target.value as 'YES' | 'NO' | '')
              }
            >
              <option value="">Select an answer</option>
              <option value="NO">No</option>
              <option value="YES">Yes</option>
            </select>
          </label>

          <div className="rounded-xl border border-indigo-200 bg-indigo-50 px-4 py-3 text-xs leading-5 text-indigo-800">
            App campaigns are created paused. After creation, add an App ad group and an asset-based
            App ad before enabling delivery. App Campaign Legacy remains Google-Ads-UI-only and is
            not created here.
          </div>

          <div className="flex justify-end gap-2 border-t border-slate-100 pt-5">
            <button type="button" className="secondary-button" onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="primary-button" disabled={saving || !complete}>
              {saving ? 'Creating…' : 'Create paused App campaign'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function LocationEditor({
  title,
  rows,
  onAdd,
  onRemove,
  onChange,
}: {
  title: string
  rows: LocationRow[]
  onAdd: () => void
  onRemove: (id: string) => void
  onChange: (id: string, patch: Partial<LocationRow>) => void
}) {
  return (
    <div>
      <div className="flex items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-bold text-slate-950">{title}</h3>
          <p className="mt-1 text-xs text-slate-500">
            Country is required; region, city, and postal code are optional.
          </p>
        </div>
        <button
          type="button"
          className="secondary-button inline-flex items-center gap-2 text-xs"
          onClick={onAdd}
        >
          <FiPlus aria-hidden="true" /> Add
        </button>
      </div>
      <div className="mt-3 space-y-2">
        {rows.map((row) => (
          <div
            key={row.id}
            className="grid gap-2 rounded-xl border border-slate-200 p-3 sm:grid-cols-[90px_1fr_1fr_1fr_auto]"
          >
            <input
              className="form-input"
              value={row.countryCode}
              maxLength={8}
              placeholder="IN"
              onChange={(event) => onChange(row.id, { countryCode: event.target.value })}
            />
            <input
              className="form-input"
              value={row.region ?? ''}
              placeholder="Region / state"
              onChange={(event) => onChange(row.id, { region: event.target.value })}
            />
            <input
              className="form-input"
              value={row.city ?? ''}
              placeholder="City"
              onChange={(event) => onChange(row.id, { city: event.target.value })}
            />
            <input
              className="form-input"
              value={row.postalCode ?? ''}
              placeholder="Postal code"
              onChange={(event) => onChange(row.id, { postalCode: event.target.value })}
            />
            <button
              type="button"
              className="rounded-lg p-2 text-slate-400 hover:bg-red-50 hover:text-red-600"
              onClick={() => onRemove(row.id)}
            >
              <FiTrash2 aria-hidden="true" />
            </button>
          </div>
        ))}
      </div>
    </div>
  )
}
