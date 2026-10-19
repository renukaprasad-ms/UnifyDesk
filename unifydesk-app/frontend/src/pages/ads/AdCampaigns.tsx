import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useEffect, useMemo, useState } from 'react'
import { FiPlay, FiPlus, FiRefreshCw, FiPause } from 'react-icons/fi'
import { Link, useSearchParams } from 'react-router'
import AdFeedbackAlert from '../../components/ads/AdFeedbackAlert'
import MetaCampaignModal from '../../components/ads/MetaCampaignModal'
import GoogleCampaignModal from '../../components/ads/GoogleCampaignModal'
import GoogleAdvancedCampaignModal from '../../components/ads/GoogleAdvancedCampaignModal'
import GooglePerformanceMaxCampaignModal from '../../components/ads/GooglePerformanceMaxCampaignModal'
import GoogleAppCampaignModal from '../../components/ads/GoogleAppCampaignModal'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdAccount,
  AdCampaign,
  CreateGoogleAdvancedCampaignInput,
  CreateGoogleCampaignInput,
  CreateGooglePerformanceMaxCampaignInput,
  CreateGoogleAppCampaignInput,
  CreateMetaCampaignInput,
} from '../../features/ads/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'
import { TableSkeleton } from '../../components/ui/Skeleton'

const objectiveLabel = (objective?: string | null) =>
  (objective ?? 'Unknown')
    .replace(/^OUTCOME_/, '')
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/\b\w/g, (character) => character.toUpperCase())

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

const statusClass = (status: string) => {
  if (status === 'ACTIVE') return 'bg-emerald-50 text-emerald-700'
  if (status === 'PAUSED') return 'bg-amber-50 text-amber-700'
  return 'bg-slate-100 text-slate-600'
}

export default function AdCampaigns() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const { can } = useIam()
  const [searchParams, setSearchParams] = useSearchParams()
  const [accounts, setAccounts] = useState<AdAccount[]>([])
  const [campaigns, setCampaigns] = useState<AdCampaign[]>([])
  const [loading, setLoading] = useState(true)
  const [syncing, setSyncing] = useState(false)
  const [creating, setCreating] = useState(false)
  const [workingCampaignId, setWorkingCampaignId] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [googleCreateType, setGoogleCreateType] = useState<
    'SEARCH' | 'PERFORMANCE_MAX' | 'ADVANCED' | 'APP'
  >('SEARCH')
  const [error, setError] = useSnackbarState('', 'error')
  const [notice, setNotice] = useSnackbarState('', 'success')

  const managedAccounts = useMemo(
    () => accounts.filter((account) => account.managed && account.accessible),
    [accounts],
  )

  const requestedAccountId = searchParams.get('accountId')
  const selectedAccount =
    managedAccounts.find((account) => account.id === requestedAccountId) ??
    managedAccounts.find((account) => account.primaryAccount) ??
    managedAccounts[0] ??
    null

  const loadCampaigns = async (accountId: string | undefined) => {
    if (!selectedCompany) return
    const page = await adCenterApi.campaigns(selectedCompany.companyId, accountId, 0, 100)
    setCampaigns(page.content)
  }

  const load = async () => {
    if (!selectedCompany) return
    setLoading(true)
    setError('')
    try {
      const accountData = await adCenterApi.accounts(selectedCompany.companyId)
      setAccounts(accountData)
      const eligible = accountData.filter((account) => account.managed && account.accessible)
      const preferred =
        eligible.find((account) => account.id === requestedAccountId) ??
        eligible.find((account) => account.primaryAccount) ??
        eligible[0]
      if (preferred) {
        await loadCampaigns(preferred.id)
      } else {
        setCampaigns([])
      }
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load campaigns'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [selectedCompany?.companyId])

  useEffect(() => {
    if (!selectedCompany || !selectedAccount) return
    void loadCampaigns(selectedAccount.id).catch((loadError) =>
      setError(apiErrorMessage(loadError, 'Unable to load campaigns')),
    )
  }, [selectedAccount?.id])

  const chooseAccount = (accountId: string) => {
    const next = new URLSearchParams(searchParams)
    next.set('accountId', accountId)
    setSearchParams(next)
  }

  const sync = async () => {
    if (!selectedCompany || !selectedAccount) return
    setSyncing(true)
    setError('')
    setNotice('')
    try {
      const result =
        selectedAccount.provider === 'GOOGLE_ADS'
          ? await adCenterApi.syncGoogleCampaigns(selectedCompany.companyId, selectedAccount.id)
          : await adCenterApi.syncMetaCampaigns(selectedCompany.companyId, selectedAccount.id)
      await loadCampaigns(selectedAccount.id)
      try {
        await adCenterApi.reconcileRecommendations(selectedCompany.companyId, selectedAccount.id)
      } catch {
        // Campaign sync succeeded; recommendation lifecycle reconciliation is best effort.
      }
      window.dispatchEvent(new Event('ad-center:recommendations-changed'))
      setNotice(
        `Synchronized ${result.synchronizedCount} campaign(s) from ${selectedAccount.provider === 'GOOGLE_ADS' ? 'Google Ads' : 'Meta'}.`,
      )
    } catch (syncError) {
      setError(apiErrorMessage(syncError, 'Unable to synchronize advertising campaigns'))
    } finally {
      setSyncing(false)
    }
  }

  const createMeta = async (input: CreateMetaCampaignInput) => {
    if (!selectedCompany || !selectedAccount) return
    setCreating(true)
    setError('')
    setNotice('')
    try {
      const created = await adCenterApi.createMetaCampaign(
        selectedCompany.companyId,
        selectedAccount.id,
        input,
      )
      setCampaigns((current) => [created, ...current.filter((item) => item.id !== created.id)])
      setCreateOpen(false)
      setNotice(`${created.name} was created in paused state.`)
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create Meta campaign'))
    } finally {
      setCreating(false)
    }
  }

  const createGoogle = async (input: CreateGoogleCampaignInput) => {
    if (!selectedCompany || !selectedAccount) return
    setCreating(true)
    setError('')
    setNotice('')
    try {
      const created = await adCenterApi.createGoogleCampaign(
        selectedCompany.companyId,
        selectedAccount.id,
        input,
      )
      setCampaigns((current) => [created, ...current.filter((item) => item.id !== created.id)])
      setCreateOpen(false)
      setNotice(`${created.name} was created in paused state.`)
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create Google Ads campaign'))
    } finally {
      setCreating(false)
    }
  }

  const createGooglePerformanceMax = async (input: CreateGooglePerformanceMaxCampaignInput) => {
    if (!selectedCompany || !selectedAccount) return
    setCreating(true)
    setError('')
    setNotice('')
    try {
      const created = await adCenterApi.createGooglePerformanceMaxCampaign(
        selectedCompany.companyId,
        selectedAccount.id,
        input,
      )
      setCampaigns((current) => [created, ...current.filter((item) => item.id !== created.id)])
      setCreateOpen(false)
      setNotice(
        `${created.name} was created as a paused Performance Max campaign with its initial asset group.`,
      )
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create Google Performance Max campaign'))
    } finally {
      setCreating(false)
    }
  }

  const createGoogleApp = async (input: CreateGoogleAppCampaignInput) => {
    if (!selectedCompany || !selectedAccount) return
    setCreating(true)
    setError('')
    setNotice('')
    try {
      const created = await adCenterApi.createGoogleAppCampaign(
        selectedCompany.companyId,
        selectedAccount.id,
        input,
      )
      setCampaigns((current) => [created, ...current.filter((item) => item.id !== created.id)])
      setCreateOpen(false)
      setNotice(`${created.name} was created as a paused Google App campaign.`)
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create Google App campaign'))
    } finally {
      setCreating(false)
    }
  }

  const createGoogleAdvanced = async (input: CreateGoogleAdvancedCampaignInput) => {
    if (!selectedCompany || !selectedAccount) return
    setCreating(true)
    setError('')
    setNotice('')
    try {
      const created = await adCenterApi.createGoogleAdvancedCampaign(
        selectedCompany.companyId,
        selectedAccount.id,
        input,
      )
      setCampaigns((current) => [created, ...current.filter((item) => item.id !== created.id)])
      setCreateOpen(false)
      setNotice(
        `${created.name} was created as a paused ${objectiveLabel(created.objective)} campaign.`,
      )
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create Google Ads campaign'))
    } finally {
      setCreating(false)
    }
  }

  const setStatus = async (campaign: AdCampaign, status: 'ACTIVE' | 'PAUSED') => {
    if (!selectedCompany) return
    setWorkingCampaignId(campaign.id)
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
      setCampaigns((current) => current.map((item) => (item.id === updated.id ? updated : item)))
      if (selectedAccount) {
        try {
          await adCenterApi.reconcileRecommendations(selectedCompany.companyId, selectedAccount.id)
        } catch {
          // Campaign status update succeeded; lifecycle reconciliation is best effort.
        }
      }
      window.dispatchEvent(new Event('ad-center:recommendations-changed'))
      setNotice(`${updated.name} is now ${status.toLowerCase()}.`)
    } catch (statusError) {
      setError(apiErrorMessage(statusError, 'Unable to update campaign status'))
    } finally {
      setWorkingCampaignId(null)
    }
  }

  if (!selectedCompany) {
    return (
      <section className="px-4 py-6 sm:px-6 lg:px-8">
        <div className="panel mx-auto max-w-6xl p-8 text-center text-sm text-slate-500">
          Select a workspace to manage campaigns.
        </div>
      </section>
    )
  }

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Ad Center</p>
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
              Provider campaigns
            </h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
              Inspect and manage raw Google and Meta campaign objects. For one campaign brief that
              compiles across providers, use Unified campaigns.
            </p>
          </div>

          <div className="flex flex-wrap gap-2">
            {can(PERMISSIONS.ADS_CREATE) && (
              <Link
                to="/ad-center/unified-campaigns/new"
                className="primary-button inline-flex items-center gap-2"
              >
                <FiPlus aria-hidden="true" />
                Create unified campaign
              </Link>
            )}
            {can(PERMISSIONS.ADS_MODIFY) && (
              <button
                type="button"
                className="secondary-button inline-flex items-center gap-2"
                disabled={!selectedAccount || syncing}
                onClick={() => void sync()}
              >
                <FiRefreshCw aria-hidden="true" />
                {syncing
                  ? 'Syncing…'
                  : `Sync ${selectedAccount?.provider === 'GOOGLE_ADS' ? 'Google' : 'Meta'}`}
              </button>
            )}
            {can(PERMISSIONS.ADS_CREATE) &&
              (selectedAccount?.provider === 'GOOGLE_ADS' ? (
                <>
                  <button
                    type="button"
                    className="secondary-button inline-flex items-center gap-2"
                    disabled={!selectedAccount}
                    onClick={() => {
                      setGoogleCreateType('SEARCH')
                      setCreateOpen(true)
                    }}
                  >
                    <FiPlus aria-hidden="true" />
                    Create Search
                  </button>
                  <button
                    type="button"
                    className="primary-button inline-flex items-center gap-2"
                    disabled={!selectedAccount}
                    onClick={() => {
                      setGoogleCreateType('PERFORMANCE_MAX')
                      setCreateOpen(true)
                    }}
                  >
                    <FiPlus aria-hidden="true" />
                    Create Performance Max
                  </button>
                  <button
                    type="button"
                    className="secondary-button inline-flex items-center gap-2"
                    disabled={!selectedAccount}
                    onClick={() => {
                      setGoogleCreateType('ADVANCED')
                      setCreateOpen(true)
                    }}
                  >
                    <FiPlus aria-hidden="true" />
                    Display / Demand Gen / Shopping
                  </button>
                  <button
                    type="button"
                    className="secondary-button inline-flex items-center gap-2"
                    disabled={!selectedAccount}
                    onClick={() => {
                      setGoogleCreateType('APP')
                      setCreateOpen(true)
                    }}
                  >
                    <FiPlus aria-hidden="true" />
                    Create App
                  </button>
                </>
              ) : (
                <button
                  type="button"
                  className="primary-button inline-flex items-center gap-2"
                  disabled={!selectedAccount}
                  onClick={() => setCreateOpen(true)}
                >
                  <FiPlus aria-hidden="true" />
                  Create campaign
                </button>
              ))}
          </div>
        </header>

        <AdFeedbackAlert error={error} notice={notice} />

        <div className="mt-6 flex flex-wrap items-end gap-3">
          <label className="min-w-[260px] text-xs font-semibold text-slate-700">
            Ad account
            <select
              className="form-input mt-2"
              value={selectedAccount?.id ?? ''}
              disabled={managedAccounts.length === 0}
              onChange={(event) => chooseAccount(event.target.value)}
            >
              {managedAccounts.length === 0 ? (
                <option value="">No managed ad account</option>
              ) : (
                managedAccounts.map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.name}
                    {account.currency ? ` · ${account.currency}` : ''}
                  </option>
                ))
              )}
            </select>
          </label>
          {selectedAccount && (
            <p className="pb-2 text-xs text-slate-400">
              {selectedAccount.provider === 'GOOGLE_ADS' ? 'Google Ads' : 'Meta'} account{' '}
              {selectedAccount.externalAccountId}
            </p>
          )}
        </div>

        {!loading && managedAccounts.length === 0 ? (
          <div className="panel mt-6 p-10 text-center">
            <h2 className="text-lg font-bold text-slate-950">Choose an ad account first</h2>
            <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-slate-500">
              Connect an advertising provider in Ad Center, then mark at least one accessible ad
              account as managed before campaigns can be synchronized or created.
            </p>
            <Link to="/ad-center" className="primary-button mt-5 inline-flex">
              Open Ad Center
            </Link>
          </div>
        ) : (
          <div className="panel mt-6 overflow-hidden">
            <div className="border-b border-slate-100 px-5 py-4">
              <h2 className="text-base font-bold text-slate-950">
                {selectedAccount?.name ?? 'Campaigns'}
              </h2>
              <p className="mt-1 text-xs text-slate-500">
                Provider status and effective delivery status are shown separately.
              </p>
            </div>

            {loading ? (
              <div className="p-4"><TableSkeleton rows={7} columns={6} /></div>
            ) : error && campaigns.length === 0 ? (
              <div className="p-10 text-center text-sm text-slate-500">
                Campaign data could not be refreshed. Resolve the issue above and try again.
              </div>
            ) : campaigns.length === 0 ? (
              <div className="p-10 text-center text-sm text-slate-500">
                No campaigns are stored for this account yet. Sync the provider or create your first
                campaign.
              </div>
            ) : (
              <>
                <div className="hidden overflow-x-auto md:block">
                  <table className="w-full text-left text-sm">
                    <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wide text-slate-500">
                      <tr>
                        <th className="px-5 py-3">Campaign</th>
                        <th className="px-5 py-3">Objective</th>
                        <th className="px-5 py-3">Status</th>
                        <th className="px-5 py-3">Budget</th>
                        <th className="px-5 py-3 text-right">Action</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {campaigns.map((campaign) => (
                        <tr key={campaign.id}>
                          <td className="px-5 py-4">
                            <Link
                              to={`/ad-center/campaigns/${campaign.id}`}
                              className="font-semibold text-slate-950 hover:text-indigo-600"
                            >
                              {campaign.name}
                            </Link>
                            <p className="mt-1 text-xs text-slate-400">
                              {campaign.externalCampaignId}
                            </p>
                          </td>
                          <td className="px-5 py-4 text-slate-600">
                            {objectiveLabel(campaign.objective)}
                          </td>
                          <td className="px-5 py-4">
                            <div className="flex flex-wrap gap-2">
                              <span
                                className={`rounded-full px-2.5 py-1 text-xs font-semibold ${statusClass(
                                  campaign.providerStatus ?? campaign.status,
                                )}`}
                              >
                                {campaign.providerStatus ?? campaign.status}
                              </span>
                              {campaign.effectiveStatus &&
                                campaign.effectiveStatus !== campaign.providerStatus && (
                                  <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600">
                                    {campaign.effectiveStatus}
                                  </span>
                                )}
                            </div>
                          </td>
                          <td className="px-5 py-4 text-slate-600">
                            {campaign.dailyBudgetMinor != null
                              ? `${formatMinor(
                                  campaign.dailyBudgetMinor,
                                  selectedAccount?.currency,
                                )} / day`
                              : campaign.lifetimeBudgetMinor != null
                                ? `${formatMinor(
                                    campaign.lifetimeBudgetMinor,
                                    selectedAccount?.currency,
                                  )} lifetime`
                                : selectedAccount?.provider === 'GOOGLE_ADS'
                                  ? 'Campaign budget unavailable'
                                  : 'Ad-set budget'}
                          </td>
                          <td className="px-5 py-4 text-right">
                            {campaign.objective === 'VIDEO' ? (
                              <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-500">
                                Read only
                              </span>
                            ) : (
                              can(PERMISSIONS.ADS_MODIFY) &&
                              (campaign.status === 'ACTIVE' ? (
                                <button
                                  type="button"
                                  className="secondary-button inline-flex items-center gap-2"
                                  disabled={workingCampaignId !== null}
                                  onClick={() => void setStatus(campaign, 'PAUSED')}
                                >
                                  <FiPause aria-hidden="true" />
                                  Pause
                                </button>
                              ) : (
                                <button
                                  type="button"
                                  className="secondary-button inline-flex items-center gap-2"
                                  disabled={
                                    workingCampaignId !== null || campaign.status === 'ARCHIVED'
                                  }
                                  onClick={() => void setStatus(campaign, 'ACTIVE')}
                                >
                                  <FiPlay aria-hidden="true" />
                                  Start
                                </button>
                              ))
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                <div className="divide-y divide-slate-100 md:hidden">
                  {campaigns.map((campaign) => (
                    <div key={campaign.id} className="p-5">
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <Link
                            to={`/ad-center/campaigns/${campaign.id}`}
                            className="font-semibold text-slate-950"
                          >
                            {campaign.name}
                          </Link>
                          <p className="mt-1 text-xs text-slate-500">
                            {objectiveLabel(campaign.objective)}
                          </p>
                        </div>
                        <span
                          className={`rounded-full px-2.5 py-1 text-xs font-semibold ${statusClass(
                            campaign.providerStatus ?? campaign.status,
                          )}`}
                        >
                          {campaign.providerStatus ?? campaign.status}
                        </span>
                      </div>
                      <p className="mt-3 text-xs text-slate-500">
                        {campaign.dailyBudgetMinor != null
                          ? `${formatMinor(
                              campaign.dailyBudgetMinor,
                              selectedAccount?.currency,
                            )} / day`
                          : campaign.lifetimeBudgetMinor != null
                            ? `${formatMinor(
                                campaign.lifetimeBudgetMinor,
                                selectedAccount?.currency,
                              )} lifetime`
                            : selectedAccount?.provider === 'GOOGLE_ADS'
                              ? 'Campaign budget unavailable'
                              : 'Budget will be configured at ad-set level'}
                      </p>
                      {campaign.objective === 'VIDEO' ? (
                        <p className="mt-4 text-xs font-semibold text-slate-400">
                          Video campaign · read only in Google Ads API
                        </p>
                      ) : (
                        can(PERMISSIONS.ADS_MODIFY) && (
                          <button
                            type="button"
                            className="secondary-button mt-4 inline-flex items-center gap-2"
                            disabled={workingCampaignId !== null}
                            onClick={() =>
                              void setStatus(
                                campaign,
                                campaign.providerStatus === 'ACTIVE' ? 'PAUSED' : 'ACTIVE',
                              )
                            }
                          >
                            {campaign.providerStatus === 'ACTIVE' ? (
                              <FiPause aria-hidden="true" />
                            ) : (
                              <FiPlay aria-hidden="true" />
                            )}
                            {campaign.providerStatus === 'ACTIVE' ? 'Pause' : 'Start'}
                          </button>
                        )
                      )}
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>
        )}
      </div>

      {selectedAccount?.provider === 'META_ADS' && (
        <MetaCampaignModal
          account={selectedAccount}
          open={createOpen}
          saving={creating}
          onClose={() => setCreateOpen(false)}
          onCreate={createMeta}
        />
      )}
      {selectedAccount?.provider === 'GOOGLE_ADS' && googleCreateType === 'SEARCH' && (
        <GoogleCampaignModal
          account={selectedAccount}
          open={createOpen}
          saving={creating}
          onClose={() => setCreateOpen(false)}
          onCreate={createGoogle}
        />
      )}
      {selectedAccount?.provider === 'GOOGLE_ADS' && googleCreateType === 'APP' && (
        <GoogleAppCampaignModal
          companyId={selectedCompany.companyId}
          account={selectedAccount}
          open={createOpen}
          saving={creating}
          onClose={() => setCreateOpen(false)}
          onCreate={createGoogleApp}
        />
      )}
      {selectedAccount?.provider === 'GOOGLE_ADS' && googleCreateType === 'ADVANCED' && (
        <GoogleAdvancedCampaignModal
          companyId={selectedCompany.companyId}
          account={selectedAccount}
          open={createOpen}
          saving={creating}
          onClose={() => setCreateOpen(false)}
          onCreate={createGoogleAdvanced}
        />
      )}
      {selectedAccount?.provider === 'GOOGLE_ADS' && googleCreateType === 'PERFORMANCE_MAX' && (
        <GooglePerformanceMaxCampaignModal
          companyId={selectedCompany.companyId}
          account={selectedAccount}
          open={createOpen}
          saving={creating}
          onClose={() => setCreateOpen(false)}
          onCreate={createGooglePerformanceMax}
        />
      )}
    </section>
  )
}
