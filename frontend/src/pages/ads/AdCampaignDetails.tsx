import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { FiArrowLeft, FiEdit2, FiPause, FiPlay, FiSave, FiX } from 'react-icons/fi'
import { Link, useParams } from 'react-router'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type { AdAccount, AdCampaign, UpdateMetaCampaignInput } from '../../features/ads/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'

const displayValue = (value?: string | null) =>
  value ? value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase()) : '—'

const formatMinor = (value: number | null | undefined, currency?: string | null) => {
  if (value == null) return '—'
  if (!currency) return `${value} minor units`
  try {
    const formatter = new Intl.NumberFormat(undefined, { style: 'currency', currency })
    const digits = formatter.resolvedOptions().maximumFractionDigits
    return formatter.format(value / 10 ** digits)
  } catch {
    return `${value} ${currency}`
  }
}

const toMajor = (value: number | null | undefined, currency?: string | null) => {
  if (value == null) return ''
  if (!currency) return String(value)
  try {
    const digits = new Intl.NumberFormat(undefined, { style: 'currency', currency })
      .resolvedOptions()
      .maximumFractionDigits
    return String(value / 10 ** digits)
  } catch {
    return String(value)
  }
}

const toMinor = (value: string, currency?: string | null) => {
  const numeric = Number(value)
  if (!Number.isFinite(numeric) || numeric <= 0) return undefined
  if (!currency) return Math.round(numeric)
  try {
    const digits = new Intl.NumberFormat(undefined, { style: 'currency', currency })
      .resolvedOptions()
      .maximumFractionDigits
    return Math.round(numeric * 10 ** digits)
  } catch {
    return Math.round(numeric)
  }
}

export default function AdCampaignDetails() {
  const { campaignId = '' } = useParams()
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const { can } = useIam()
  const [campaign, setCampaign] = useState<AdCampaign | null>(null)
  const [accounts, setAccounts] = useState<AdAccount[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [statusWorking, setStatusWorking] = useState(false)
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState('')
  const [bidStrategy, setBidStrategy] = useState('')
  const [budget, setBudget] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const account = useMemo(
    () => accounts.find((item) => item.id === campaign?.adAccountId) ?? null,
    [accounts, campaign?.adAccountId],
  )

  const syncForm = (value: AdCampaign, accountValue?: AdAccount | null) => {
    setName(value.name)
    setBidStrategy(value.bidStrategy ?? '')
    setBudget(
      toMajor(
        value.dailyBudgetMinor ?? value.lifetimeBudgetMinor,
        accountValue?.currency ?? account?.currency,
      ),
    )
  }

  const load = async () => {
    if (!selectedCompany || !campaignId) return
    setLoading(true)
    setError('')
    try {
      const [campaignData, accountData] = await Promise.all([
        adCenterApi.campaign(selectedCompany.companyId, campaignId),
        adCenterApi.accounts(selectedCompany.companyId),
      ])
      setCampaign(campaignData)
      setAccounts(accountData)
      syncForm(
        campaignData,
        accountData.find((item) => item.id === campaignData.adAccountId),
      )
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load campaign'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [selectedCompany?.companyId, campaignId])

  const save = async (event: FormEvent) => {
    event.preventDefault()
    if (!selectedCompany || !campaign) return
    setSaving(true)
    setError('')
    setNotice('')

    const input: UpdateMetaCampaignInput = {
      name: name.trim(),
    }
    if (bidStrategy) input.bidStrategy = bidStrategy

    const minor = toMinor(budget, account?.currency)
    if (minor) {
      if (campaign.lifetimeBudgetMinor != null) input.lifetimeBudgetMinor = minor
      else if (campaign.dailyBudgetMinor != null) input.dailyBudgetMinor = minor
      else input.dailyBudgetMinor = minor
    }

    try {
      const updated = await adCenterApi.updateMetaCampaign(
        selectedCompany.companyId,
        campaign.id,
        input,
      )
      setCampaign(updated)
      syncForm(updated, account)
      setEditing(false)
      setNotice('Campaign settings updated in Meta.')
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to update campaign'))
    } finally {
      setSaving(false)
    }
  }

  const setStatus = async (status: 'ACTIVE' | 'PAUSED') => {
    if (!selectedCompany || !campaign) return
    setStatusWorking(true)
    setError('')
    setNotice('')
    try {
      const updated = await adCenterApi.setMetaCampaignStatus(
        selectedCompany.companyId,
        campaign.id,
        status,
      )
      setCampaign(updated)
      setNotice(`Campaign is now ${status.toLowerCase()}.`)
    } catch (statusError) {
      setError(apiErrorMessage(statusError, 'Unable to update campaign status'))
    } finally {
      setStatusWorking(false)
    }
  }

  if (!selectedCompany) return null

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-5xl">
        <Link
          to={account ? `/ad-center/campaigns?accountId=${account.id}` : '/ad-center/campaigns'}
          className="inline-flex items-center gap-2 text-sm font-semibold text-slate-500 hover:text-slate-900"
        >
          <FiArrowLeft aria-hidden="true" />
          Back to campaigns
        </Link>

        {error && !campaign && (
          <div className="mt-6 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
            {error}
          </div>
        )}

        {loading ? (
          <div className="panel mt-6 p-10 text-center text-sm text-slate-500">
            Loading campaign…
          </div>
        ) : campaign ? (
          <>
            <header className="mt-5 flex flex-wrap items-end justify-between gap-4">
              <div>
                <p className="eyebrow">Meta campaign</p>
                <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
                  {campaign.name}
                </h1>
                <p className="mt-2 text-sm text-slate-500">
                  {account?.name ?? 'Meta Ads'} · {campaign.externalCampaignId}
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                {can(PERMISSIONS.ADS_MODIFY) &&
                  (campaign.providerStatus === 'ACTIVE' ? (
                    <button
                      type="button"
                      className="secondary-button inline-flex items-center gap-2"
                      disabled={statusWorking}
                      onClick={() => void setStatus('PAUSED')}
                    >
                      <FiPause aria-hidden="true" />
                      {statusWorking ? 'Updating…' : 'Pause'}
                    </button>
                  ) : (
                    <button
                      type="button"
                      className="primary-button inline-flex items-center gap-2"
                      disabled={statusWorking || campaign.providerStatus === 'ARCHIVED'}
                      onClick={() => void setStatus('ACTIVE')}
                    >
                      <FiPlay aria-hidden="true" />
                      {statusWorking ? 'Updating…' : 'Start campaign'}
                    </button>
                  ))}
                {!editing && can(PERMISSIONS.ADS_MODIFY) && (
                  <button
                    type="button"
                    className="secondary-button inline-flex items-center gap-2"
                    onClick={() => setEditing(true)}
                  >
                    <FiEdit2 aria-hidden="true" />
                    Edit
                  </button>
                )}
              </div>
            </header>

            {(error || notice) && (
              <div
                className={`mt-6 rounded-xl border px-4 py-3 text-sm font-medium ${
                  error
                    ? 'border-red-200 bg-red-50 text-red-700'
                    : 'border-emerald-200 bg-emerald-50 text-emerald-700'
                }`}
              >
                {error || notice}
              </div>
            )}

            <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <div className="panel p-5">
                <p className="text-xs font-semibold text-slate-400">Configured status</p>
                <p className="mt-2 text-lg font-bold text-slate-950">
                  {campaign.providerStatus ?? campaign.status}
                </p>
              </div>
              <div className="panel p-5">
                <p className="text-xs font-semibold text-slate-400">Effective status</p>
                <p className="mt-2 text-lg font-bold text-slate-950">
                  {campaign.effectiveStatus ?? '—'}
                </p>
              </div>
              <div className="panel p-5">
                <p className="text-xs font-semibold text-slate-400">Objective</p>
                <p className="mt-2 text-lg font-bold text-slate-950">
                  {displayValue(campaign.objective)}
                </p>
              </div>
              <div className="panel p-5">
                <p className="text-xs font-semibold text-slate-400">Budget</p>
                <p className="mt-2 text-lg font-bold text-slate-950">
                  {campaign.dailyBudgetMinor != null
                    ? `${formatMinor(campaign.dailyBudgetMinor, account?.currency)} / day`
                    : campaign.lifetimeBudgetMinor != null
                      ? formatMinor(campaign.lifetimeBudgetMinor, account?.currency)
                      : 'Ad-set level'}
                </p>
              </div>
            </div>

            <form className="panel mt-5 p-6" onSubmit={save}>
              <div className="flex items-center justify-between gap-3">
                <div>
                  <h2 className="text-base font-bold text-slate-950">Campaign settings</h2>
                  <p className="mt-1 text-xs text-slate-500">
                    Objective and buying type are shown as provider-owned configuration in this
                    first management slice.
                  </p>
                </div>
              </div>

              <div className="mt-5 grid gap-5 sm:grid-cols-2">
                <label className="block text-xs font-semibold text-slate-700">
                  Name
                  <input
                    className="form-input mt-2"
                    value={name}
                    disabled={!editing}
                    maxLength={255}
                    onChange={(event) => setName(event.target.value)}
                  />
                </label>

                <label className="block text-xs font-semibold text-slate-700">
                  Bid strategy
                  <select
                    className="form-input mt-2"
                    value={bidStrategy}
                    disabled={!editing}
                    onChange={(event) => setBidStrategy(event.target.value)}
                  >
                    <option value="">Meta default</option>
                    <option value="LOWEST_COST_WITHOUT_CAP">Lowest cost</option>
                    <option value="LOWEST_COST_WITH_BID_CAP">Bid cap</option>
                    <option value="COST_CAP">Cost cap</option>
                  </select>
                </label>

                <label className="block text-xs font-semibold text-slate-700">
                  Buying type
                  <input
                    className="form-input mt-2"
                    value={displayValue(campaign.buyingType)}
                    disabled
                  />
                </label>

                <label className="block text-xs font-semibold text-slate-700">
                  {campaign.lifetimeBudgetMinor != null
                    ? 'Lifetime campaign budget'
                    : 'Daily campaign budget'}
                  <input
                    className="form-input mt-2"
                    type="number"
                    min="0.01"
                    step="0.01"
                    value={budget}
                    disabled={!editing}
                    onChange={(event) => setBudget(event.target.value)}
                    placeholder={
                      campaign.dailyBudgetMinor == null && campaign.lifetimeBudgetMinor == null
                        ? 'Optional — otherwise budget stays at ad-set level'
                        : undefined
                    }
                  />
                </label>
              </div>

              <div className="mt-5 grid gap-4 border-t border-slate-100 pt-5 sm:grid-cols-2">
                <div>
                  <p className="text-xs font-semibold text-slate-400">Started</p>
                  <p className="mt-1 text-sm text-slate-700">
                    {campaign.startAt ? new Date(campaign.startAt).toLocaleString() : 'Not scheduled'}
                  </p>
                </div>
                <div>
                  <p className="text-xs font-semibold text-slate-400">Meta last updated</p>
                  <p className="mt-1 text-sm text-slate-700">
                    {campaign.providerUpdatedAt
                      ? new Date(campaign.providerUpdatedAt).toLocaleString()
                      : '—'}
                  </p>
                </div>
              </div>

              {editing && (
                <div className="mt-6 flex justify-end gap-2 border-t border-slate-100 pt-5">
                  <button
                    type="button"
                    className="secondary-button inline-flex items-center gap-2"
                    disabled={saving}
                    onClick={() => {
                      syncForm(campaign, account)
                      setEditing(false)
                      setError('')
                    }}
                  >
                    <FiX aria-hidden="true" />
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="primary-button inline-flex items-center gap-2"
                    disabled={saving || !name.trim()}
                  >
                    <FiSave aria-hidden="true" />
                    {saving ? 'Saving…' : 'Save to Meta'}
                  </button>
                </div>
              )}
            </form>

            <div className="panel mt-5 p-6">
              <h2 className="text-base font-bold text-slate-950">Next layer</h2>
              <p className="mt-2 text-sm leading-6 text-slate-500">
                Ad sets will live under this campaign. That is where targeting, placements,
                optimization, schedule, conversion event, and ad-set-level budget are configured.
              </p>
            </div>
          </>
        ) : null}
      </div>
    </section>
  )
}
