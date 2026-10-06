import { useEffect, useMemo, useState } from 'react'
import AdFeedbackAlert from '../../components/ads/AdFeedbackAlert'
import { FiImage, FiPlus, FiRefreshCw, FiVideo } from 'react-icons/fi'
import { Link } from 'react-router'
import MetaCreativeModal from '../../components/ads/MetaCreativeModal'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  Ad,
  AdAccount,
  AdAccountResource,
  AdCreative,
  GoogleAsset,
  CreateMetaCreativeInput,
} from '../../features/ads/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'
import { PageSkeleton } from '../../components/ui/Skeleton'

const formatLabel = (value?: string | null) =>
  value
    ? value
        .replaceAll('_', ' ')
        .toLowerCase()
        .replace(/\b\w/g, (c) => c.toUpperCase())
    : 'Creative'

export default function AdCreatives() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const { can } = useIam()
  const [accounts, setAccounts] = useState<AdAccount[]>([])
  const [resources, setResources] = useState<AdAccountResource[]>([])
  const [creatives, setCreatives] = useState<AdCreative[]>([])
  const [googleAds, setGoogleAds] = useState<Ad[]>([])
  const [googleAssets, setGoogleAssets] = useState<GoogleAsset[]>([])
  const [selectedAccountId, setSelectedAccountId] = useState('')
  const [loading, setLoading] = useState(true)
  const [syncing, setSyncing] = useState(false)
  const [creating, setCreating] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const managedAccounts = useMemo(
    () => accounts.filter((account) => account.managed && account.accessible),
    [accounts],
  )

  const selectedAccount =
    managedAccounts.find((account) => account.id === selectedAccountId) ??
    managedAccounts.find((account) => account.primaryAccount) ??
    managedAccounts[0] ??
    null

  const loadForAccount = async (account: AdAccount) => {
    if (!selectedCompany) return
    if (account.provider === 'GOOGLE_ADS') {
      const [adData, assetData] = await Promise.all([
        adCenterApi.accountAds(selectedCompany.companyId, account.id),
        adCenterApi.googleAssets(selectedCompany.companyId, account.id),
      ])
      setGoogleAds(adData)
      setGoogleAssets(assetData)
      setCreatives([])
      setResources([])
      return
    }

    const [creativeData, resourceData] = await Promise.all([
      adCenterApi.creatives(selectedCompany.companyId, account.id),
      adCenterApi.resources(selectedCompany.companyId, account.id),
    ])
    setCreatives(creativeData)
    setGoogleAds([])
    setGoogleAssets([])
    setResources(resourceData)
  }

  const load = async () => {
    if (!selectedCompany) return
    setLoading(true)
    setError('')
    try {
      const accountData = await adCenterApi.accounts(selectedCompany.companyId)
      setAccounts(accountData)
      const eligible = accountData.filter((account) => account.managed && account.accessible)
      const preferred = eligible.find((account) => account.primaryAccount) ?? eligible[0]
      if (preferred) {
        setSelectedAccountId(preferred.id)
        await loadForAccount(preferred)
      } else {
        setCreatives([])
        setGoogleAds([])
        setGoogleAssets([])
        setResources([])
      }
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load creatives'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [selectedCompany?.companyId])

  const chooseAccount = async (accountId: string) => {
    const account = managedAccounts.find((item) => item.id === accountId)
    if (!account) return
    setSelectedAccountId(accountId)
    setLoading(true)
    setError('')
    try {
      await loadForAccount(account)
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load creatives'))
    } finally {
      setLoading(false)
    }
  }

  const sync = async () => {
    if (!selectedCompany || !selectedAccount) return
    setSyncing(true)
    setError('')
    setNotice('')
    try {
      if (selectedAccount.provider === 'GOOGLE_ADS') {
        const campaigns = await adCenterApi.campaigns(
          selectedCompany.companyId,
          selectedAccount.id,
          0,
          100,
        )
        let synchronized = 0
        for (const campaign of campaigns.content) {
          if (campaign.objective === 'PERFORMANCE_MAX') continue
          await adCenterApi.syncGoogleAdGroups(selectedCompany.companyId, campaign.id)
          const groups = await adCenterApi.adSets(selectedCompany.companyId, campaign.id)
          for (const group of groups) {
            const result = await adCenterApi.syncGoogleAds(selectedCompany.companyId, group.id)
            synchronized += result.synchronizedCount
          }
        }
        await loadForAccount(selectedAccount)
        setNotice(`Synchronized ${synchronized} Google ad creative unit(s).`)
      } else {
        const result = await adCenterApi.syncMetaCreatives(
          selectedCompany.companyId,
          selectedAccount.id,
        )
        await loadForAccount(selectedAccount)
        setNotice(`Synchronized ${result.synchronizedCount} creative(s) from Meta.`)
      }
    } catch (syncError) {
      setError(apiErrorMessage(syncError, 'Unable to synchronize advertising creative content'))
    } finally {
      setSyncing(false)
    }
  }

  const create = async (input: CreateMetaCreativeInput) => {
    if (!selectedCompany || !selectedAccount) return
    setCreating(true)
    setError('')
    setNotice('')
    try {
      const created = await adCenterApi.createMetaCreative(
        selectedCompany.companyId,
        selectedAccount.id,
        input,
      )
      setCreatives((current) => [created, ...current.filter((item) => item.id !== created.id)])
      setModalOpen(false)
      setNotice(`${created.name} was created and is ready to use in an ad.`)
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create Meta creative'))
    } finally {
      setCreating(false)
    }
  }

  if (!selectedCompany) return null

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Ad Center</p>
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
              Creatives
            </h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
              {selectedAccount?.provider === 'GOOGLE_ADS'
                ? 'Inspect the Google asset library used by Performance Max and the responsive search ads created inside Ad Groups.'
                : 'Build reusable Meta image, video, and carousel creatives before attaching them to ads.'}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
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
            {can(PERMISSIONS.ADS_CREATE) && selectedAccount?.provider === 'META_ADS' && (
              <button
                type="button"
                className="primary-button inline-flex items-center gap-2"
                disabled={!selectedAccount}
                onClick={() => setModalOpen(true)}
              >
                <FiPlus aria-hidden="true" />
                Create creative
              </button>
            )}
          </div>
        </header>
        <AdFeedbackAlert error={error} notice={notice} />

        <div className="mt-6 max-w-sm">
          <label className="text-xs font-semibold text-slate-700">
            Ad account
            <select
              className="form-input mt-2"
              value={selectedAccount?.id ?? ''}
              disabled={!managedAccounts.length}
              onChange={(event) => void chooseAccount(event.target.value)}
            >
              {managedAccounts.length === 0 ? (
                <option value="">No managed ad account</option>
              ) : (
                managedAccounts.map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.name}
                  </option>
                ))
              )}
            </select>
          </label>
        </div>

        {!loading && managedAccounts.length === 0 ? (
          <div className="panel mt-6 p-10 text-center text-sm text-slate-500">
            Connect and manage an ad account before using creative management.
          </div>
        ) : loading ? (
          <div className="mt-6"><PageSkeleton /></div>
        ) : selectedAccount?.provider === 'GOOGLE_ADS' ? (
          <div className="mt-6 space-y-6">
            <div className="panel overflow-hidden">
              <div className="border-b border-slate-100 px-5 py-4">
                <h2 className="text-base font-bold text-slate-950">Google asset library</h2>
                <p className="mt-1 text-xs text-slate-500">
                  Reusable text, image, and YouTube assets available to Performance Max and other
                  Google campaign types.
                </p>
              </div>

              {googleAssets.length === 0 ? (
                <div className="p-8 text-center text-sm text-slate-500">
                  No reusable Google assets are stored yet. Create a Performance Max campaign to add
                  its first asset set.
                </div>
              ) : (
                <div className="grid gap-4 p-5 md:grid-cols-2 xl:grid-cols-3">
                  {googleAssets.map((asset) => (
                    <article
                      key={asset.resourceName ?? asset.id ?? asset.name}
                      className="overflow-hidden rounded-xl border border-slate-200 bg-white"
                    >
                      {asset.imageUrl ? (
                        <div className="flex aspect-[16/9] items-center justify-center bg-slate-100">
                          <img
                            src={asset.imageUrl}
                            alt=""
                            className="h-full w-full object-contain"
                          />
                        </div>
                      ) : (
                        <div className="flex aspect-[16/9] items-center justify-center bg-slate-50">
                          {asset.type === 'YOUTUBE_VIDEO' ? (
                            <FiVideo className="text-3xl text-slate-400" aria-hidden="true" />
                          ) : (
                            <FiImage className="text-3xl text-slate-400" aria-hidden="true" />
                          )}
                        </div>
                      )}
                      <div className="p-4">
                        <div className="flex items-start justify-between gap-3">
                          <p className="min-w-0 truncate font-semibold text-slate-950">
                            {asset.name || asset.text || 'Google asset'}
                          </p>
                          <span className="rounded-full bg-indigo-50 px-2 py-0.5 text-[11px] font-semibold text-indigo-700">
                            {formatLabel(asset.type)}
                          </span>
                        </div>
                        {asset.text && (
                          <p className="mt-2 line-clamp-3 text-sm leading-6 text-slate-600">
                            {asset.text}
                          </p>
                        )}
                        {asset.youtubeVideoId && (
                          <p className="mt-2 text-xs text-slate-500">
                            YouTube {asset.youtubeVideoId}
                          </p>
                        )}
                        {asset.imageWidth && asset.imageHeight && (
                          <p className="mt-2 text-xs text-slate-400">
                            {asset.imageWidth}×{asset.imageHeight}
                          </p>
                        )}
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </div>

            <div className="panel overflow-hidden">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-5 py-4">
                <div>
                  <h2 className="text-base font-bold text-slate-950">Responsive Search Ads</h2>
                  <p className="mt-1 text-xs text-slate-500">
                    Search creative units remain attached to their Google Ad Groups.
                  </p>
                </div>
                <Link
                  to="/ad-center/campaigns"
                  className="secondary-button inline-flex items-center gap-2"
                >
                  <FiPlus aria-hidden="true" />
                  Open campaigns
                </Link>
              </div>

              {googleAds.length === 0 ? (
                <div className="p-8 text-center text-sm text-slate-500">
                  No Responsive Search Ads are stored yet.
                </div>
              ) : (
                <div className="divide-y divide-slate-100">
                  {googleAds.map((ad) => (
                    <div
                      key={ad.id}
                      className="flex flex-wrap items-center justify-between gap-4 px-5 py-4"
                    >
                      <div className="min-w-0">
                        <p className="font-bold text-slate-950">{ad.name}</p>
                        <p className="mt-1 text-xs text-slate-500">
                          Responsive Search Ad · {ad.externalAdId}
                        </p>
                      </div>
                      <div className="flex items-center gap-3">
                        <span
                          className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
                            ad.status === 'ACTIVE'
                              ? 'bg-emerald-50 text-emerald-700'
                              : 'bg-slate-100 text-slate-600'
                          }`}
                        >
                          {ad.status}
                        </span>
                        <Link
                          to={`/ad-center/ad-sets/${ad.adSetId}`}
                          className="secondary-button text-xs"
                        >
                          Open ad group
                        </Link>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        ) : creatives.length === 0 ? (
          <div className="panel mt-6 p-10 text-center">
            <FiImage className="mx-auto text-2xl text-slate-300" aria-hidden="true" />
            <h2 className="mt-3 text-base font-bold text-slate-950">No creatives yet</h2>
            <p className="mt-2 text-sm text-slate-500">
              Sync existing Meta creatives or create the first reusable creative.
            </p>
          </div>
        ) : (
          <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {creatives.map((creative) => (
              <article key={creative.id} className="panel overflow-hidden">
                <div className="flex aspect-[16/9] items-center justify-center bg-slate-100">
                  {creative.imageUrl ? (
                    <img src={creative.imageUrl} alt="" className="h-full w-full object-cover" />
                  ) : creative.creativeFormat === 'VIDEO' ? (
                    <FiVideo className="text-3xl text-slate-400" aria-hidden="true" />
                  ) : (
                    <FiImage className="text-3xl text-slate-400" aria-hidden="true" />
                  )}
                </div>
                <div className="p-5">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <h2 className="truncate font-bold text-slate-950">{creative.name}</h2>
                      <p className="mt-1 text-xs text-slate-500">
                        {formatLabel(creative.creativeFormat)}
                      </p>
                    </div>
                    <span
                      className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
                        creative.accessible
                          ? 'bg-emerald-50 text-emerald-700'
                          : 'bg-slate-100 text-slate-500'
                      }`}
                    >
                      {creative.accessible ? 'Available' : 'Unavailable'}
                    </span>
                  </div>
                  {creative.primaryText && (
                    <p className="mt-3 line-clamp-2 text-sm leading-6 text-slate-600">
                      {creative.primaryText}
                    </p>
                  )}
                  <div className="mt-4 border-t border-slate-100 pt-4 text-xs text-slate-400">
                    Meta creative {creative.externalCreativeId}
                  </div>
                </div>
              </article>
            ))}
          </div>
        )}
      </div>

      {selectedAccount?.provider === 'META_ADS' && (
        <MetaCreativeModal
          account={selectedAccount}
          resources={resources}
          open={modalOpen}
          saving={creating}
          onClose={() => setModalOpen(false)}
          onCreate={create}
        />
      )}
    </section>
  )
}
