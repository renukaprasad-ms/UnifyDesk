import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import {
  FiArrowLeft,
  FiEdit2,
  FiPause,
  FiPlay,
  FiPlus,
  FiRefreshCw,
  FiSave,
  FiX,
} from 'react-icons/fi'
import { Link, useParams } from 'react-router'
import MetaAdSetModal from '../../components/ads/MetaAdSetModal'
import GoogleAdGroupModal from '../../components/ads/GoogleAdGroupModal'
import GoogleAppAdGroupModal from '../../components/ads/GoogleAppAdGroupModal'
import GoogleAppCampaignPanel from '../../components/ads/GoogleAppCampaignPanel'
import GooglePerformanceMaxSignals from '../../components/ads/GooglePerformanceMaxSignals'
import GooglePerformanceMaxAssets from '../../components/ads/GooglePerformanceMaxAssets'
import GooglePerformanceMaxAutomationPanel from '../../components/ads/GooglePerformanceMaxAutomationPanel'
import GooglePerformanceMaxBrandGuidelinesPanel from '../../components/ads/GooglePerformanceMaxBrandGuidelinesPanel'
import GooglePerformanceMaxLifecyclePanel from '../../components/ads/GooglePerformanceMaxLifecyclePanel'
import GooglePortfolioBiddingPanel from '../../components/ads/GooglePortfolioBiddingPanel'
import GoogleAiAssetStudio from '../../components/ads/GoogleAiAssetStudio'
import GoogleNegativeKeywordManagement from '../../components/ads/GoogleNegativeKeywordManagement'
import GoogleSearchTermReport from '../../components/ads/GoogleSearchTermReport'
import GoogleAiMaxCombinationReport from '../../components/ads/GoogleAiMaxCombinationReport'
import GoogleReusableAssetsPanel from '../../components/ads/GoogleReusableAssetsPanel'
import GoogleSearchDemographicTargetingPanel from '../../components/ads/GoogleSearchDemographicTargetingPanel'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdAccount,
  AdAccountResource,
  AdAudience,
  AdCampaign,
  AdConversion,
  AdSet,
  GoogleAssetGroup,
  CreateMetaAdSetInput,
  CreateGoogleAdGroupInput,
  CreateGoogleAppAdGroupInput,
  UpdateMetaCampaignInput,
  UpdateGoogleCampaignInput,
} from '../../features/ads/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'
import { PageSkeleton } from '../../components/ui/Skeleton'

const displayValue = (value?: string | null) =>
  value
    ? value
        .replaceAll('_', ' ')
        .toLowerCase()
        .replace(/\b\w/g, (c) => c.toUpperCase())
    : '—'

type GoogleCampaignBidStrategy = NonNullable<UpdateGoogleCampaignInput['bidStrategy']>

const isGoogleCampaignBidStrategy = (value: string): value is GoogleCampaignBidStrategy =>
  (
    ['MANUAL_CPC', 'MAXIMIZE_CLICKS', 'MAXIMIZE_CONVERSIONS', 'MAXIMIZE_CONVERSION_VALUE'] as const
  ).some((strategy) => strategy === value)

const formatMinor = (value: number | null | undefined, currency?: string | null) => {
  if (value == null) return '—'
  if (!currency) return `${value} minor units`
  try {
    const formatter = new Intl.NumberFormat(undefined, { style: 'currency', currency })
    const digits = formatter.resolvedOptions().maximumFractionDigits ?? 2
    return formatter.format(value / 10 ** digits)
  } catch {
    return `${value} ${currency}`
  }
}

const toMajor = (value: number | null | undefined, currency?: string | null) => {
  if (value == null) return ''
  if (!currency) return String(value)
  try {
    const digits =
      new Intl.NumberFormat(undefined, { style: 'currency', currency }).resolvedOptions()
        .maximumFractionDigits ?? 2
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
    const digits =
      new Intl.NumberFormat(undefined, { style: 'currency', currency }).resolvedOptions()
        .maximumFractionDigits ?? 2
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
  const [assetGroups, setAssetGroups] = useState<GoogleAssetGroup[]>([])
  const [resources, setResources] = useState<AdAccountResource[]>([])
  const [audiences, setAudiences] = useState<AdAudience[]>([])
  const [conversions, setConversions] = useState<AdConversion[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [statusWorking, setStatusWorking] = useState(false)
  const [syncingAdSets, setSyncingAdSets] = useState(false)
  const [creatingAdSet, setCreatingAdSet] = useState(false)
  const [workingAdSetId, setWorkingAdSetId] = useState<string | null>(null)
  const [workingAssetGroupId, setWorkingAssetGroupId] = useState<string | null>(null)
  const [adSetModalOpen, setAdSetModalOpen] = useState(false)
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState('')
  const [bidStrategy, setBidStrategy] = useState('')
  const [budget, setBudget] = useState('')
  const [error, setError] = useSnackbarState('', 'error')
  const [, setNotice] = useSnackbarState('', 'success')

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
      const isPerformanceMax =
        campaignData.provider === 'GOOGLE_ADS' && campaignData.objective === 'PERFORMANCE_MAX'

      const [adSetData, assetGroupData] = await Promise.all([
        isPerformanceMax
          ? Promise.resolve([])
          : adCenterApi.adSets(selectedCompany.companyId, campaignId),
        isPerformanceMax
          ? adCenterApi.googleAssetGroups(selectedCompany.companyId, campaignId)
          : Promise.resolve([]),
      ])

      const accountValue = accountData.find((item) => item.id === campaignData.adAccountId) ?? null
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
      setAssetGroups(assetGroupData)
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

    const minor = toMinor(budget, account?.currency)

    try {
      const updated =
        campaign.provider === 'GOOGLE_ADS'
          ? await adCenterApi.updateGoogleCampaign(selectedCompany.companyId, campaign.id, {
              name: name.trim(),
              ...(campaign.objective !== 'APP' && isGoogleCampaignBidStrategy(bidStrategy)
                ? { bidStrategy }
                : {}),
              ...(minor ? { dailyBudgetMinor: minor } : {}),
            } satisfies UpdateGoogleCampaignInput)
          : await adCenterApi.updateMetaCampaign(selectedCompany.companyId, campaign.id, {
              name: name.trim(),
              ...(bidStrategy ? { bidStrategy } : {}),
              ...(minor
                ? campaign.lifetimeBudgetMinor != null
                  ? { lifetimeBudgetMinor: minor }
                  : { dailyBudgetMinor: minor }
                : {}),
            } satisfies UpdateMetaCampaignInput)
      setCampaign(updated)
      syncForm(updated, account)
      setEditing(false)
      try {
        await adCenterApi.reconcileRecommendations(selectedCompany.companyId, updated.adAccountId)
      } catch {
        // Meta accepted the campaign edit; lifecycle reconciliation is best effort.
      }
      window.dispatchEvent(new Event('ad-center:recommendations-changed'))
      setNotice(
        `Campaign settings updated in ${campaign.provider === 'GOOGLE_ADS' ? 'Google Ads' : 'Meta'}.`,
      )
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
      const isPerformanceMax =
        campaign.provider === 'GOOGLE_ADS' && campaign.objective === 'PERFORMANCE_MAX'

      if (isPerformanceMax) {
        const groups = await adCenterApi.googleAssetGroups(selectedCompany.companyId, campaign.id)
        setAssetGroups(groups)
        setNotice(`Loaded ${groups.length} Performance Max asset group(s).`)
        return
      }

      const result =
        campaign.provider === 'GOOGLE_ADS'
          ? await adCenterApi.syncGoogleAdGroups(selectedCompany.companyId, campaign.id)
          : await adCenterApi.syncMetaAdSets(selectedCompany.companyId, campaign.id)
      const refreshed = await adCenterApi.adSets(selectedCompany.companyId, campaign.id)
      setAdSets(refreshed)
      setNotice(
        `Synchronized ${result.synchronizedCount} ${campaign.provider === 'GOOGLE_ADS' ? 'ad group' : 'ad set'}(s).`,
      )
    } catch (syncError) {
      setError(apiErrorMessage(syncError, 'Unable to synchronize delivery groups'))
    } finally {
      setSyncingAdSets(false)
    }
  }

  const setAssetGroupStatus = async (group: GoogleAssetGroup, status: 'ACTIVE' | 'PAUSED') => {
    if (!selectedCompany || !campaign || !group.id) return

    setWorkingAssetGroupId(group.id)
    setError('')
    setNotice('')
    try {
      const updated = await adCenterApi.setGoogleAssetGroupStatus(
        selectedCompany.companyId,
        campaign.id,
        group.id,
        status,
      )
      setAssetGroups((current) => current.map((item) => (item.id === updated.id ? updated : item)))
      setNotice(`${updated.name ?? 'Performance Max asset group'} is now ${status.toLowerCase()}.`)
    } catch (statusError) {
      setError(apiErrorMessage(statusError, 'Unable to update Performance Max asset group status'))
    } finally {
      setWorkingAssetGroupId(null)
    }
  }

  const createMetaAdSet = async (input: CreateMetaAdSetInput) => {
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
      setAdSets((current) => [created, ...current.filter((item) => item.id !== created.id)])
      setAdSetModalOpen(false)
      setNotice(`${created.name} was created in paused state.`)
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create Meta ad set'))
    } finally {
      setCreatingAdSet(false)
    }
  }

  const createGoogleAdGroup = async (input: CreateGoogleAdGroupInput) => {
    if (!selectedCompany || !campaign) return
    setCreatingAdSet(true)
    setError('')
    setNotice('')
    try {
      const created = await adCenterApi.createGoogleAdGroup(
        selectedCompany.companyId,
        campaign.id,
        input,
      )
      setAdSets((current) => [created, ...current.filter((item) => item.id !== created.id)])
      setAdSetModalOpen(false)
      setNotice(`${created.name} was created in paused state.`)
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create Google Ads ad group'))
    } finally {
      setCreatingAdSet(false)
    }
  }

  const createGoogleAppAdGroup = async (input: CreateGoogleAppAdGroupInput) => {
    if (!selectedCompany || !campaign) return
    setCreatingAdSet(true)
    setError('')
    setNotice('')
    try {
      const created = await adCenterApi.createGoogleAppAdGroup(
        selectedCompany.companyId,
        campaign.id,
        input,
      )
      setAdSets((current) => [created, ...current.filter((item) => item.id !== created.id)])
      setAdSetModalOpen(false)
      setNotice(`${created.name} was created as a paused App ad group.`)
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create Google App ad group'))
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
      const updated =
        adSet.provider === 'GOOGLE_ADS'
          ? await adCenterApi.setGoogleAdGroupStatus(selectedCompany.companyId, adSet.id, status)
          : await adCenterApi.setMetaAdSetStatus(selectedCompany.companyId, adSet.id, status)
      setAdSets((current) => current.map((item) => (item.id === updated.id ? updated : item)))
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
      const updated =
        campaign.provider === 'GOOGLE_ADS'
          ? await adCenterApi.setGoogleCampaignStatus(
              selectedCompany.companyId,
              campaign.id,
              status,
            )
          : await adCenterApi.setMetaCampaignStatus(selectedCompany.companyId, campaign.id, status)
      setCampaign(updated)
      try {
        await adCenterApi.reconcileRecommendations(selectedCompany.companyId, updated.adAccountId)
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
          <div className="mt-6"><PageSkeleton /></div>
        ) : campaign ? (
          <>
            <header className="mt-5 flex flex-wrap items-end justify-between gap-4">
              <div>
                <p className="eyebrow">
                  {campaign.provider === 'GOOGLE_ADS' ? 'Google Ads campaign' : 'Meta campaign'}
                </p>
                <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
                  {campaign.name}
                </h1>
                <p className="mt-2 text-sm text-slate-500">
                  {account?.name ??
                    (campaign.provider === 'GOOGLE_ADS' ? 'Google Ads' : 'Meta Ads')}{' '}
                  · {campaign.externalCampaignId}
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                {can(PERMISSIONS.ADS_MODIFY) &&
                  campaign.objective !== 'VIDEO' &&
                  (campaign.status === 'ACTIVE' ? (
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
                      disabled={statusWorking || campaign.status === 'ARCHIVED'}
                      onClick={() => void setStatus('ACTIVE')}
                    >
                      <FiPlay aria-hidden="true" />
                      {statusWorking ? 'Updating…' : 'Start campaign'}
                    </button>
                  ))}
                {!editing && campaign.objective !== 'VIDEO' && can(PERMISSIONS.ADS_MODIFY) && (
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

            {campaign.objective === 'VIDEO' && (
              <div className="mt-6 rounded-xl border border-indigo-200 bg-indigo-50 px-4 py-3 text-sm text-indigo-800">
                Google Ads API exposes Video campaigns for synchronization and reporting only.
                Campaign, ad group, and ad mutations remain managed in Google Ads. Demand Gen is
                available when API-managed YouTube/video delivery is required.
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

            {campaign.provider === 'GOOGLE_ADS' && campaign.objective === 'VIDEO' && (
              <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-800">
                Google Ads API exposes standalone Video campaigns for sync and reporting only.
                Create or modify standalone Video campaigns in Google Ads. For programmatic
                YouTube/video creation inside Reachlyo, use Demand Gen.
              </div>
            )}

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
                    disabled={!editing || campaign.objective === 'VIDEO'}
                    maxLength={255}
                    onChange={(event) => setName(event.target.value)}
                  />
                </label>

                <label className="block text-xs font-semibold text-slate-700">
                  {campaign.objective === 'APP' ? 'Optimization bidding' : 'Bid strategy'}
                  {campaign.objective === 'APP' ? (
                    <input
                      className="form-input mt-2"
                      value="Managed by App campaign optimization goal"
                      disabled
                    />
                  ) : (
                    <select
                      className="form-input mt-2"
                      value={bidStrategy}
                      disabled={!editing || campaign.objective === 'VIDEO'}
                      onChange={(event) => setBidStrategy(event.target.value)}
                    >
                      {campaign.provider === 'GOOGLE_ADS' ? (
                        campaign.objective === 'PERFORMANCE_MAX' ? (
                          <>
                            <option value="MAXIMIZE_CONVERSIONS">Maximize conversions</option>
                            <option value="MAXIMIZE_CONVERSION_VALUE">
                              Maximize conversion value
                            </option>
                          </>
                        ) : (
                          <>
                            <option value="MANUAL_CPC">Manual CPC</option>
                            <option value="MAXIMIZE_CLICKS">Maximize clicks</option>
                            <option value="MAXIMIZE_CONVERSIONS">Maximize conversions</option>
                            <option value="MAXIMIZE_CONVERSION_VALUE">
                              Maximize conversion value
                            </option>
                          </>
                        )
                      ) : (
                        <>
                          <option value="">Meta default</option>
                          <option value="LOWEST_COST_WITHOUT_CAP">Lowest cost</option>
                          <option value="LOWEST_COST_WITH_BID_CAP">Bid cap</option>
                          <option value="COST_CAP">Cost cap</option>
                        </>
                      )}
                    </select>
                  )}
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
                    disabled={!editing || campaign.objective === 'VIDEO'}
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
                    {campaign.startAt
                      ? new Date(campaign.startAt).toLocaleString()
                      : 'Not scheduled'}
                  </p>
                </div>
                <div>
                  <p className="text-xs font-semibold text-slate-400">Provider last updated</p>
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
                    {saving
                      ? 'Saving…'
                      : `Save to ${campaign.provider === 'GOOGLE_ADS' ? 'Google' : 'Meta'}`}
                  </button>
                </div>
              )}
            </form>

            {campaign.provider === 'GOOGLE_ADS' && campaign.objective === 'APP' && (
              <GoogleAppCampaignPanel
                companyId={selectedCompany.companyId}
                campaignId={campaign.id}
                currency={account?.currency}
              />
            )}

            {campaign.provider === 'GOOGLE_ADS' &&
              account &&
              campaign.objective &&
              ['SEARCH', 'DISPLAY', 'SHOPPING'].includes(campaign.objective) && (
                <GooglePortfolioBiddingPanel
                  companyId={selectedCompany.companyId}
                  accountId={account.id}
                  campaignId={campaign.id}
                  currency={account.currency}
                  canCreate={can(PERMISSIONS.ADS_CREATE)}
                  canModify={can(PERMISSIONS.ADS_MODIFY)}
                />
              )}

            {campaign.provider === 'GOOGLE_ADS' && campaign.objective === 'SEARCH' && account && (
              <>
                <GoogleSearchTermReport
                  companyId={selectedCompany.companyId}
                  campaign={campaign}
                  canCreate={can(PERMISSIONS.ADS_CREATE)}
                  canModify={can(PERMISSIONS.ADS_MODIFY)}
                />
                <GoogleAiMaxCombinationReport
                  companyId={selectedCompany.companyId}
                  campaignId={campaign.id}
                />
                <GoogleNegativeKeywordManagement
                  companyId={selectedCompany.companyId}
                  campaign={campaign}
                  account={account}
                  canCreate={can(PERMISSIONS.ADS_CREATE)}
                  canModify={can(PERMISSIONS.ADS_MODIFY)}
                />
              </>
            )}

            {campaign.provider === 'GOOGLE_ADS' &&
              account &&
              campaign.objective &&
              ['SEARCH', 'PERFORMANCE_MAX', 'DISPLAY', 'DEMAND_GEN'].includes(
                campaign.objective,
              ) && (
                <GoogleAiAssetStudio
                  companyId={selectedCompany.companyId}
                  accountId={account.id}
                  channelType={
                    campaign.objective as 'SEARCH' | 'PERFORMANCE_MAX' | 'DISPLAY' | 'DEMAND_GEN'
                  }
                  canCreate={can(PERMISSIONS.ADS_CREATE)}
                />
              )}

            {campaign.provider === 'GOOGLE_ADS' && campaign.objective === 'SEARCH' && account && (
              <GoogleSearchDemographicTargetingPanel
                companyId={selectedCompany.companyId}
                accountId={account.id}
                campaignId={campaign.id}
                canModify={can(PERMISSIONS.ADS_MODIFY)}
              />
            )}

            {campaign.provider === 'GOOGLE_ADS' &&
              campaign.objective &&
              !['VIDEO', 'APP'].includes(campaign.objective) &&
              account && (
                <GoogleReusableAssetsPanel
                  companyId={selectedCompany.companyId}
                  accountId={account.id}
                  campaignId={campaign.id}
                  canCreate={can(PERMISSIONS.ADS_CREATE)}
                  canModify={can(PERMISSIONS.ADS_MODIFY)}
                />
              )}

            {campaign.provider === 'GOOGLE_ADS' && campaign.objective === 'PERFORMANCE_MAX' && (
              <GooglePerformanceMaxBrandGuidelinesPanel
                companyId={selectedCompany.companyId}
                campaignId={campaign.id}
                canModify={can(PERMISSIONS.ADS_MODIFY)}
              />
            )}

            {campaign.provider === 'GOOGLE_ADS' &&
              campaign.objective === 'PERFORMANCE_MAX' &&
              account && (
                <GooglePerformanceMaxLifecyclePanel
                  companyId={selectedCompany.companyId}
                  accountId={account.id}
                  campaignId={campaign.id}
                  canModify={can(PERMISSIONS.ADS_MODIFY)}
                />
              )}

            {campaign.provider === 'GOOGLE_ADS' && campaign.objective === 'PERFORMANCE_MAX' && (
              <GooglePerformanceMaxAutomationPanel
                companyId={selectedCompany.companyId}
                campaignId={campaign.id}
                canModify={can(PERMISSIONS.ADS_MODIFY)}
              />
            )}

            <div className="panel mt-5 overflow-hidden">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-5 py-4">
                <div>
                  <h2 className="text-base font-bold text-slate-950">
                    {campaign.provider === 'GOOGLE_ADS' && campaign.objective === 'PERFORMANCE_MAX'
                      ? 'Asset groups'
                      : campaign.provider === 'GOOGLE_ADS'
                        ? 'Ad groups'
                        : 'Ad sets'}
                  </h2>
                  <p className="mt-1 text-xs text-slate-500">
                    {campaign.provider === 'GOOGLE_ADS' && campaign.objective === 'PERFORMANCE_MAX'
                      ? 'Creative assets, final URLs, ad strength, and delivery status for Performance Max.'
                      : 'Targeting, placements, schedule, bidding, budget, and conversion settings.'}
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
                      {syncingAdSets
                        ? 'Syncing…'
                        : campaign.provider === 'GOOGLE_ADS' &&
                            campaign.objective === 'PERFORMANCE_MAX'
                          ? 'Refresh asset groups'
                          : `Sync ${campaign.provider === 'GOOGLE_ADS' ? 'ad groups' : 'ad sets'}`}
                    </button>
                  )}
                  {can(PERMISSIONS.ADS_CREATE) &&
                    account &&
                    campaign.objective !== 'VIDEO' &&
                    !(
                      campaign.provider === 'GOOGLE_ADS' && campaign.objective === 'PERFORMANCE_MAX'
                    ) && (
                      <button
                        type="button"
                        className="primary-button inline-flex items-center gap-2"
                        onClick={() => setAdSetModalOpen(true)}
                      >
                        <FiPlus aria-hidden="true" />
                        Create {campaign.provider === 'GOOGLE_ADS' ? 'ad group' : 'ad set'}
                      </button>
                    )}
                </div>
              </div>

              {campaign.provider === 'GOOGLE_ADS' && campaign.objective === 'PERFORMANCE_MAX' ? (
                assetGroups.length === 0 ? (
                  <div className="p-8 text-center text-sm text-slate-500">
                    No Performance Max asset groups were returned. Refresh after Google finishes
                    processing the campaign assets.
                  </div>
                ) : (
                  <div className="divide-y divide-slate-100">
                    {assetGroups.map((group) => (
                      <div key={group.resourceName ?? group.id}>
                        <div className="flex flex-wrap items-center justify-between gap-4 px-5 py-4">
                          <div className="min-w-0">
                            <p className="font-semibold text-slate-950">
                              {group.name ?? 'Performance Max asset group'}
                            </p>
                            <p className="mt-1 text-xs text-slate-500">
                              Ad strength {displayValue(group.adStrength)}
                            </p>
                            <p className="mt-1 break-all text-xs text-slate-400">
                              {group.finalUrls?.join(', ') || 'No final URL returned'}
                            </p>
                          </div>
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600">
                              {group.status ?? 'UNKNOWN'}
                            </span>
                            {group.primaryStatus && group.primaryStatus !== group.status && (
                              <span className="rounded-full bg-indigo-50 px-2.5 py-1 text-xs font-semibold text-indigo-700">
                                {displayValue(group.primaryStatus)}
                              </span>
                            )}
                            {can(PERMISSIONS.ADS_MODIFY) &&
                              group.id &&
                              (group.status === 'ENABLED' ? (
                                <button
                                  type="button"
                                  className="secondary-button inline-flex items-center gap-2"
                                  disabled={workingAssetGroupId !== null}
                                  onClick={() => void setAssetGroupStatus(group, 'PAUSED')}
                                >
                                  <FiPause aria-hidden="true" />
                                  Pause group
                                </button>
                              ) : (
                                <button
                                  type="button"
                                  className="secondary-button inline-flex items-center gap-2"
                                  disabled={workingAssetGroupId !== null}
                                  onClick={() => void setAssetGroupStatus(group, 'ACTIVE')}
                                >
                                  <FiPlay aria-hidden="true" />
                                  Start group
                                </button>
                              ))}
                          </div>
                        </div>

                        {group.id && account && (
                          <GooglePerformanceMaxAssets
                            companyId={selectedCompany.companyId}
                            campaignId={campaign.id}
                            accountId={account.id}
                            assetGroup={group}
                            canModify={can(PERMISSIONS.ADS_MODIFY)}
                          />
                        )}

                        {group.id && (
                          <GooglePerformanceMaxSignals
                            companyId={selectedCompany.companyId}
                            campaignId={campaign.id}
                            assetGroup={group}
                            audiences={audiences}
                            canModify={can(PERMISSIONS.ADS_MODIFY)}
                          />
                        )}
                      </div>
                    ))}
                  </div>
                )
              ) : adSets.length === 0 ? (
                <div className="p-8 text-center text-sm text-slate-500">
                  No {campaign.provider === 'GOOGLE_ADS' ? 'ad groups' : 'ad sets'} are stored for
                  this campaign yet. Sync the provider or create the first paused{' '}
                  {campaign.provider === 'GOOGLE_ADS' ? 'ad group' : 'ad set'}.
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
                        {campaign.objective !== 'VIDEO' &&
                          can(PERMISSIONS.ADS_MODIFY) &&
                          (adSet.status === 'ACTIVE' ? (
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
                              disabled={workingAdSetId !== null || adSet.status === 'ARCHIVED'}
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

            {account && campaign.provider === 'META_ADS' && (
              <MetaAdSetModal
                companyId={selectedCompany.companyId}
                account={account}
                campaign={campaign}
                resources={resources}
                audiences={audiences}
                conversions={conversions}
                open={adSetModalOpen}
                saving={creatingAdSet}
                onClose={() => setAdSetModalOpen(false)}
                onCreate={createMetaAdSet}
              />
            )}
            {account && campaign.provider === 'GOOGLE_ADS' && campaign.objective === 'APP' && (
              <GoogleAppAdGroupModal
                companyId={selectedCompany.companyId}
                account={account}
                campaign={campaign}
                open={adSetModalOpen}
                saving={creatingAdSet}
                onClose={() => setAdSetModalOpen(false)}
                onCreate={createGoogleAppAdGroup}
              />
            )}
            {account && campaign.provider === 'GOOGLE_ADS' && campaign.objective !== 'APP' && (
              <GoogleAdGroupModal
                account={account}
                campaign={campaign}
                open={adSetModalOpen}
                saving={creatingAdSet}
                onClose={() => setAdSetModalOpen(false)}
                onCreate={createGoogleAdGroup}
              />
            )}
          </>
        ) : null}
      </div>
    </section>
  )
}
