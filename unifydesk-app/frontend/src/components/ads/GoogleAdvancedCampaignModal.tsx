import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { FiX } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdAccount,
  CreateGoogleAdvancedCampaignInput,
  GoogleMerchantAccount,
} from '../../features/ads/types'
import { apiErrorMessage } from '../../lib/api/apiClient'

type Props = {
  companyId: string
  account: AdAccount
  open: boolean
  saving: boolean
  onClose: () => void
  onCreate: (input: CreateGoogleAdvancedCampaignInput) => Promise<void>
}

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

export default function GoogleAdvancedCampaignModal({
  companyId,
  account,
  open,
  saving,
  onClose,
  onCreate,
}: Props) {
  const [channelType, setChannelType] =
    useState<CreateGoogleAdvancedCampaignInput['channelType']>('DISPLAY')
  const [name, setName] = useState('')
  const [budget, setBudget] = useState('')
  const [budgetMode, setBudgetMode] = useState<'DAILY' | 'TOTAL'>('DAILY')
  const [bidStrategy, setBidStrategy] =
    useState<CreateGoogleAdvancedCampaignInput['bidStrategy']>('MAXIMIZE_CONVERSIONS')
  const [targetCpa, setTargetCpa] = useState('')
  const [targetRoas, setTargetRoas] = useState('')
  const [merchantId, setMerchantId] = useState('')
  const [merchantAccounts, setMerchantAccounts] = useState<GoogleMerchantAccount[]>([])
  const [loadingMerchants, setLoadingMerchants] = useState(false)
  const [merchantError, setMerchantError] = useSnackbarState('', 'error')
  const [campaignPriority, setCampaignPriority] = useState('0')
  const [enableLocal, setEnableLocal] = useState(false)
  const [containsEuPoliticalAdvertising, setContainsEuPoliticalAdvertising] = useState<
    'YES' | 'NO' | ''
  >('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')

  const strategyOptions = useMemo(() => {
    if (channelType === 'DISPLAY') {
      return [
        ['MAXIMIZE_CONVERSIONS', 'Maximize conversions'],
        ['TARGET_CPA', 'Target CPA'],
        ['MANUAL_CPC', 'Manual CPC'],
      ] as const
    }
    if (channelType === 'DEMAND_GEN') {
      return [
        ['MAXIMIZE_CONVERSIONS', 'Maximize conversions'],
        ['MAXIMIZE_CLICKS', 'Maximize clicks'],
        ['TARGET_CPA', 'Target CPA'],
        ['TARGET_ROAS', 'Target ROAS'],
      ] as const
    }
    return [['MANUAL_CPC', 'Manual CPC']] as const
  }, [channelType])

  useEffect(() => {
    if (!open || channelType !== 'SHOPPING') return
    let active = true
    setLoadingMerchants(true)
    setMerchantError('')
    void adCenterApi
      .googleMerchantAccounts(companyId, account.id)
      .then((merchants) => {
        if (!active) return
        setMerchantAccounts(merchants)
        setMerchantId((current) =>
          merchants.some((merchant) => merchant.accountId === current)
            ? current
            : merchants[0]?.accountId || '',
        )
      })
      .catch((loadError) => {
        if (!active) return
        setMerchantAccounts([])
        setMerchantId('')
        setMerchantError(
          apiErrorMessage(
            loadError,
            'Unable to load Merchant Center accounts. Reconnect Google Ads if this connection predates Merchant API access.',
          ),
        )
      })
      .finally(() => {
        if (active) setLoadingMerchants(false)
      })
    return () => {
      active = false
    }
  }, [open, channelType, companyId, account.id])

  if (!open) return null

  const changeChannel = (next: CreateGoogleAdvancedCampaignInput['channelType']) => {
    setChannelType(next)
    setTargetCpa('')
    setTargetRoas('')
    if (next !== 'DEMAND_GEN') setBudgetMode('DAILY')
    if (next === 'SHOPPING') setBidStrategy('MANUAL_CPC')
    else setBidStrategy('MAXIMIZE_CONVERSIONS')
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const budgetMinor = toMinor(budget, account.currency)
    if (budgetMinor == null) return

    const targetCpaMinor = targetCpa ? toMinor(targetCpa, account.currency) : null

    await onCreate({
      name: name.trim(),
      channelType,
      dailyBudgetMinor: budgetMode === 'DAILY' ? budgetMinor : null,
      totalBudgetMinor: budgetMode === 'TOTAL' ? budgetMinor : null,
      bidStrategy,
      targetCpaMinor,
      targetRoas: targetRoas ? Number(targetRoas) : null,
      merchantId: channelType === 'SHOPPING' ? merchantId.trim() : null,
      campaignPriority: channelType === 'SHOPPING' ? Number(campaignPriority) : null,
      enableLocal: channelType === 'SHOPPING' ? enableLocal : null,
      containsEuPoliticalAdvertising: containsEuPoliticalAdvertising === 'YES',
      startDate: startDate || null,
      endDate: endDate || null,
    })
  }

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white shadow-2xl">
        <div className="sticky top-0 z-10 flex items-start justify-between border-b border-slate-100 bg-white px-6 py-5">
          <div>
            <p className="eyebrow">Google Ads</p>
            <h2 className="mt-1 text-xl font-bold text-slate-950">Create campaign</h2>
            <p className="mt-1 text-sm text-slate-500">
              Display, Demand Gen, and Standard Shopping campaigns are created paused.
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

        <form className="space-y-5 p-6" onSubmit={submit}>
          <div className="grid grid-cols-3 gap-2 rounded-xl bg-slate-100 p-1">
            {(['DISPLAY', 'DEMAND_GEN', 'SHOPPING'] as const).map((type) => (
              <button
                key={type}
                type="button"
                className={`rounded-lg px-3 py-2 text-xs font-semibold ${
                  channelType === type ? 'bg-white text-slate-950 shadow-sm' : 'text-slate-500'
                }`}
                onClick={() => changeChannel(type)}
              >
                {type === 'DEMAND_GEN'
                  ? 'Demand Gen'
                  : type.charAt(0) + type.slice(1).toLowerCase()}
              </button>
            ))}
          </div>

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
              {budgetMode === 'TOTAL' ? 'Total campaign budget' : 'Daily budget'}{' '}
              {account.currency ? `(${account.currency})` : ''}
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

            {channelType === 'DEMAND_GEN' && (
              <label className="text-xs font-semibold text-slate-700">
                Budget period
                <select
                  className="form-input mt-2"
                  value={budgetMode}
                  onChange={(event) => setBudgetMode(event.target.value as 'DAILY' | 'TOTAL')}
                >
                  <option value="DAILY">Average daily budget</option>
                  <option value="TOTAL">Campaign total budget</option>
                </select>
                {budgetMode === 'TOTAL' && (
                  <span className="mt-1 block font-normal text-slate-400">
                    Total budget requires both start and end dates and cannot be changed to daily
                    after creation.
                  </span>
                )}
              </label>
            )}

            <label className="text-xs font-semibold text-slate-700">
              Bid strategy
              <select
                className="form-input mt-2"
                value={bidStrategy}
                onChange={(event) =>
                  setBidStrategy(
                    event.target.value as CreateGoogleAdvancedCampaignInput['bidStrategy'],
                  )
                }
              >
                {strategyOptions.map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>

            {bidStrategy === 'TARGET_CPA' && (
              <label className="text-xs font-semibold text-slate-700">
                Target CPA {account.currency ? `(${account.currency})` : ''}
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

            {bidStrategy === 'TARGET_ROAS' && (
              <label className="text-xs font-semibold text-slate-700">
                Target ROAS
                <input
                  className="form-input mt-2"
                  type="number"
                  min="0.01"
                  step="0.01"
                  placeholder="3.5"
                  value={targetRoas}
                  required
                  onChange={(event) => setTargetRoas(event.target.value)}
                />
                <span className="mt-1 block font-normal text-slate-400">
                  Enter a multiplier, for example 3.5 for 350%.
                </span>
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

          {channelType === 'SHOPPING' && (
            <div className="grid gap-4 rounded-xl border border-slate-200 bg-slate-50 p-4 sm:grid-cols-2">
              <label className="text-xs font-semibold text-slate-700">
                Merchant Center account
                <select
                  className="form-input mt-2"
                  value={merchantId}
                  required
                  disabled={loadingMerchants || merchantAccounts.length === 0}
                  onChange={(event) => setMerchantId(event.target.value)}
                >
                  {merchantAccounts.length === 0 ? (
                    <option value="">
                      {loadingMerchants
                        ? 'Loading Merchant accounts…'
                        : 'No accessible Merchant account'}
                    </option>
                  ) : (
                    merchantAccounts.map((merchant) => (
                      <option key={merchant.accountId} value={merchant.accountId}>
                        {(merchant.accountName || 'Merchant Center') + ' · ' + merchant.accountId}
                      </option>
                    ))
                  )}
                </select>
                {merchantError && (
                  <span className="mt-1 block font-normal text-red-600">{merchantError}</span>
                )}
              </label>

              <label className="text-xs font-semibold text-slate-700">
                Campaign priority
                <select
                  className="form-input mt-2"
                  value={campaignPriority}
                  onChange={(event) => setCampaignPriority(event.target.value)}
                >
                  <option value="0">Low · 0</option>
                  <option value="1">Medium · 1</option>
                  <option value="2">High · 2</option>
                </select>
              </label>

              <label className="flex items-center gap-2 text-xs font-semibold text-slate-700 sm:col-span-2">
                <input
                  type="checkbox"
                  checked={enableLocal}
                  onChange={(event) => setEnableLocal(event.target.checked)}
                />
                Enable local inventory
              </label>

              <p className="text-xs leading-5 text-slate-500 sm:col-span-2">
                The Google Ads customer must already be linked to this Merchant Center account.
                Reachlyo also creates the initial paused Shopping Product Ad Group, Product Ad, and
                root listing group.
              </p>
            </div>
          )}

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
              <option value="NO">
                No, this campaign does not contain EU political advertising
              </option>
              <option value="YES">Yes, this campaign contains EU political advertising</option>
            </select>
            <span className="mt-1 block font-normal text-slate-400">
              Google requires an explicit declaration for every new campaign.
            </span>
          </label>

          {channelType === 'DEMAND_GEN' && (
            <div className="rounded-xl border border-indigo-200 bg-indigo-50 px-4 py-3 text-xs leading-5 text-indigo-800">
              After creation, add a Demand Gen Ad Group and creative assets before starting
              delivery.
            </div>
          )}

          {channelType === 'DISPLAY' && (
            <div className="rounded-xl border border-indigo-200 bg-indigo-50 px-4 py-3 text-xs leading-5 text-indigo-800">
              After creation, add a Display Ad Group and Responsive Display Ad before starting
              delivery.
            </div>
          )}

          <div className="flex justify-end gap-2 border-t border-slate-100 pt-5">
            <button type="button" className="secondary-button" onClick={onClose} disabled={saving}>
              Cancel
            </button>
            <button
              type="submit"
              className="primary-button"
              disabled={
                saving ||
                !name.trim() ||
                !budget ||
                !containsEuPoliticalAdvertising ||
                (channelType === 'SHOPPING' &&
                  (!merchantId || loadingMerchants || merchantAccounts.length === 0)) ||
                (channelType === 'DEMAND_GEN' && budgetMode === 'TOTAL' && (!startDate || !endDate))
              }
            >
              {saving
                ? 'Creating…'
                : `Create paused ${channelType === 'DEMAND_GEN' ? 'Demand Gen' : channelType.toLowerCase()}`}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
