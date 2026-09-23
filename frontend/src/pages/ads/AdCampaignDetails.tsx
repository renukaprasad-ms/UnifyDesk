import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { FiArrowLeft, FiEdit2, FiPause, FiPlay, FiPlus, FiRefreshCw, FiSave, FiX } from 'react-icons/fi'
import { Link, useParams } from 'react-router'
import MetaAdSetModal from '../../components/ads/MetaAdSetModal'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdAccount,
  AdAccountResource,
  AdAudience,
  AdCampaign,
  AdConversion,
  AdSet,
  CreateMetaAdSetInput,
  UpdateMetaCampaignInput,
} from '../../features/ads/types'
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
  const [adSets, setAdSets] = useState<AdSet[]>([])
  const [resources, setResources] = useState<AdAccountResource[]>([])
  const [audiences, setAudiences] = useState<AdAudience[]>([])
  const [conversions, setConversions] = useState<AdConversion[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [statusWorking, setStatusWorking] = useState(false)
  const [syncingAdSets, setSyncingAdSets] = useState(false)
  const [creatingAdSet, setCreatingAdSet] = useState(false)
  const [workingAdSetId, setWorkingAdSetId] = useState<string | null>(null)
  const [adSetModalOpen, setAdSetModalOpen] = useState(false)
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
      const [campaignData, accountData, adSetData] = await Promise.all([
        adCenterApi.campaign(selectedCompany.companyId, campaignId),
        adCenterApi.accounts(selectedCompany.companyId),
        adCenterApi.adSets(selectedCompany.companyId, campaignId),
      ])
      const accountValue =
        accountData.find((item) => item.id === campaignData.adAccountId) ?? null
      const [resourceData, audienceData, conversionData] = accountValue
        ? await Promise.all([
            adCenterApi.resources(selectedCompany.companyId, accountValue.id),
            adCenterApi.audiences(selectedCompany.companyId, accountValue.id),
            adCenterApi.conversions(selectedCompany.companyId, accountValue.id),
          ])
        : [[], [], []]
      setCampaign(campaignData)
      setAccounts(accountData)
      setAdSets(adSetData)
      setResources(resourceData)
      setAudiences(audienceData)
      setConversions(conversionData)
      syncForm(campaignData, accountValue)
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
      try {
        await adCenterApi.reconcileRecommendations(
          selectedCompany.companyId,
          updated.adAccountId,
        )
      } catch {
        // Meta accepted the campaign edit; lifecycle reconciliation is best effort.
      }
      window.dispatchEvent(new Event('ad-center:recommendations-changed'))
      setNotice('Campaign settings updated in Meta.')
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Unable to update campaign'))
    } finally {
      setSaving(false)
    }
  }

  const syncAdSets = async () => {
    if (!selectedCompany || !campaign) return
    setSyncingAdSets(true)
    setError('')
    setNotice('')
    try {
      const result = await adCenterApi.syncMetaAdSets(
        selectedCompany.companyId,
        campaign.id,
      )
      const refreshed = await adCenterApi.adSets(
        selectedCompany.companyId,
        campaign.id,
      )
      setAdSets(refreshed)
      setNotice(`Synchronized ${result.synchronizedCount} ad set(s) from Meta.`)
    } catch (syncError) {
      setError(apiErrorMessage(syncError, 'Unable to synchronize Meta ad sets'))
    } finally {
      setSyncingAdSets(false)
    }
  }

  const createAdSet = async (input: CreateMetaAdSetInput) => {
    if (!selectedCompany || !campaign) return
    setCreatingAdSet(true)
    setError('')
    setNotice('')
    try {
      const created = await adCenterApi.createMetaAdSet(
        selectedCompany.companyId,
        campaign.id,
        input,
      )
      setAdSets((current) => [
        created,
        ...current.filter((item) => item.id !== created.id),
      ])
      setAdSetModalOpen(false)
      setNotice(`${created.name} was created in paused state.`)
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create Meta ad set'))
    } finally {
      setCreatingAdSet(false)
    }
  }

  const setAdSetStatus = async (adSet: AdSet, status: 'ACTIVE' | 'PAUSED') => {
    if (!selectedCompany) return
    setWorkingAdSetId(adSet.id)
    setError('')
    setNotice('')
    try {
      const updated = await adCenterApi.setMetaAdSetStatus(
        selectedCompany.companyId,
        adSet.id,
        status,
      )
      setAdSets((current) =>
        current.map((item) => (item.id === updated.id ? updated : item)),
      )
      setNotice(`${updated.name} is now ${status.toLowerCase()}.`)
    } catch (statusError) {
      setError(apiErrorMessage(statusError, 'Unable to update ad-set status'))
    } finally {
      setWorkingAdSetId(null)
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
      try {
        await adCenterApi.reconcileRecommendations(
          selectedCompany.companyId,
          updated.adAccountId,
        )
      } catch {
        // Meta accepted the status change; lifecycle reconciliation is best effort.
      }
      window.dispatchEvent(new Event('ad-center:recommendations-changed'))
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

            <div className="panel mt-5 overflow-hidden">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-5 py-4">
                <div>
                  <h2 className="text-base font-bold text-slate-950">Ad sets</h2>
                  <p className="mt-1 text-xs text-slate-500">
                    Targeting, placements, schedule, bidding, budget, and conversion settings.
                  </p>
                </div>
                <div className="flex flex-wrap gap-2">
                  {can(PERMISSIONS.ADS_MODIFY) && (
                    <button
                      type="button"
                      className="secondary-button inline-flex items-center gap-2"
                      disabled={syncingAdSets}
                      onClick={() => void syncAdSets()}
                    >
                      <FiRefreshCw aria-hidden="true" />
                      {syncingAdSets ? 'Syncing…' : 'Sync ad sets'}
                    </button>
                  )}
                  {can(PERMISSIONS.ADS_CREATE) && account && (
                    <button
                      type="button"
                      className="primary-button inline-flex items-center gap-2"
                      onClick={() => setAdSetModalOpen(true)}
                    >
                      <FiPlus aria-hidden="true" />
                      Create ad set
                    </button>
                  )}
                </div>
              </div>

              {adSets.length === 0 ? (
                <div className="p-8 text-center text-sm text-slate-500">
                  No ad sets are stored for this campaign yet. Sync Meta or create the first
                  paused ad set.
                </div>
              ) : (
                <div className="divide-y divide-slate-100">
                  {adSets.map((adSet) => (
                    <div
                      key={adSet.id}
                      className="flex flex-wrap items-center justify-between gap-4 px-5 py-4"
                    >
                      <div className="min-w-0">
                        <Link
                          to={`/ad-center/ad-sets/${adSet.id}`}
                          className="font-semibold text-slate-950 hover:text-indigo-600"
                        >
                          {adSet.name}
                        </Link>
                        <p className="mt-1 text-xs text-slate-500">
                          {displayValue(adSet.optimizationGoal)} ·{' '}
                          {displayValue(adSet.billingEvent)}
                        </p>
                        <p className="mt-1 text-xs text-slate-400">
                          {adSet.dailyBudgetMinor != null
                            ? `${formatMinor(adSet.dailyBudgetMinor, account?.currency)} / day`
                            : adSet.lifetimeBudgetMinor != null
                              ? `${formatMinor(adSet.lifetimeBudgetMinor, account?.currency)} lifetime`
                              : 'Uses campaign budget'}
                        </p>
                      </div>

                      <div className="flex flex-wrap items-center justify-end gap-2">
                        <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600">
                          {adSet.providerStatus ?? adSet.status}
                        </span>
                        {adSet.effectiveStatus &&
                          adSet.effectiveStatus !== adSet.providerStatus && (
                            <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-500">
                              {adSet.effectiveStatus}
                            </span>
                          )}
                        {can(PERMISSIONS.ADS_MODIFY) &&
                          (adSet.providerStatus === 'ACTIVE' ? (
                            <button
                              type="button"
                              className="secondary-button inline-flex items-center gap-2"
                              disabled={workingAdSetId !== null}
                              onClick={() => void setAdSetStatus(adSet, 'PAUSED')}
                            >
                              <FiPause aria-hidden="true" />
                              Pause
                            </button>
                          ) : (
                            <button
                              type="button"
                              className="secondary-button inline-flex items-center gap-2"
                              disabled={
                                workingAdSetId !== null ||
                                adSet.providerStatus === 'ARCHIVED'
                              }
                              onClick={() => void setAdSetStatus(adSet, 'ACTIVE')}
                            >
                              <FiPlay aria-hidden="true" />
                              Start
                            </button>
                          ))}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {account && (
              <MetaAdSetModal
                account={account}
                campaign={campaign}
                resources={resources}
                audiences={audiences}
                conversions={conversions}
                open={adSetModalOpen}
                saving={creatingAdSet}
                onClose={() => setAdSetModalOpen(false)}
                onCreate={createAdSet}
              />
            )}
          </>
        ) : null}
      </div>
    </section>
  )
}
