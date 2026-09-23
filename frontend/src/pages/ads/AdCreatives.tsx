import { useEffect, useMemo, useState } from 'react'
import { FiImage, FiPlus, FiRefreshCw, FiVideo } from 'react-icons/fi'
import MetaCreativeModal from '../../components/ads/MetaCreativeModal'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdAccount,
  AdAccountResource,
  AdCreative,
  CreateMetaCreativeInput,
} from '../../features/ads/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'

const formatLabel = (value?: string | null) =>
  value ? value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase()) : 'Creative'

export default function AdCreatives() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const { can } = useIam()
  const [accounts, setAccounts] = useState<AdAccount[]>([])
  const [resources, setResources] = useState<AdAccountResource[]>([])
  const [creatives, setCreatives] = useState<AdCreative[]>([])
  const [selectedAccountId, setSelectedAccountId] = useState('')
  const [loading, setLoading] = useState(true)
  const [syncing, setSyncing] = useState(false)
  const [creating, setCreating] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const managedAccounts = useMemo(
    () =>
      accounts.filter(
        (account) => account.provider === 'META_ADS' && account.managed && account.accessible,
      ),
    [accounts],
  )

  const selectedAccount =
    managedAccounts.find((account) => account.id === selectedAccountId) ??
    managedAccounts.find((account) => account.primaryAccount) ??
    managedAccounts[0] ??
    null

  const loadForAccount = async (accountId: string) => {
    if (!selectedCompany) return
    const [creativeData, resourceData] = await Promise.all([
      adCenterApi.creatives(selectedCompany.companyId, accountId),
      adCenterApi.resources(selectedCompany.companyId, accountId),
    ])
    setCreatives(creativeData)
    setResources(resourceData)
  }

  const load = async () => {
    if (!selectedCompany) return
    setLoading(true)
    setError('')
    try {
      const accountData = await adCenterApi.accounts(selectedCompany.companyId)
      setAccounts(accountData)
      const eligible = accountData.filter(
        (account) => account.provider === 'META_ADS' && account.managed && account.accessible,
      )
      const preferred = eligible.find((account) => account.primaryAccount) ?? eligible[0]
      if (preferred) {
        setSelectedAccountId(preferred.id)
        await loadForAccount(preferred.id)
      } else {
        setCreatives([])
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
    setSelectedAccountId(accountId)
    setLoading(true)
    setError('')
    try {
      await loadForAccount(accountId)
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
      const result = await adCenterApi.syncMetaCreatives(
        selectedCompany.companyId,
        selectedAccount.id,
      )
      await loadForAccount(selectedAccount.id)
      setNotice(`Synchronized ${result.synchronizedCount} creative(s) from Meta.`)
    } catch (syncError) {
      setError(apiErrorMessage(syncError, 'Unable to synchronize Meta creatives'))
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
      setCreatives((current) => [
        created,
        ...current.filter((item) => item.id !== created.id),
      ])
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
              Build reusable Meta image, video, and carousel creatives before attaching them to ads.
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
                {syncing ? 'Syncing…' : 'Sync Meta'}
              </button>
            )}
            {can(PERMISSIONS.ADS_CREATE) && (
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

        <div className="mt-6 max-w-sm">
          <label className="text-xs font-semibold text-slate-700">
            Meta ad account
            <select
              className="form-input mt-2"
              value={selectedAccount?.id ?? ''}
              disabled={!managedAccounts.length}
              onChange={(event) => void chooseAccount(event.target.value)}
            >
              {managedAccounts.length === 0 ? (
                <option value="">No managed Meta account</option>
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
            Connect and manage a Meta ad account before creating creatives.
          </div>
        ) : loading ? (
          <div className="panel mt-6 p-10 text-center text-sm text-slate-500">
            Loading creatives…
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
                    <img
                      src={creative.imageUrl}
                      alt=""
                      className="h-full w-full object-cover"
                    />
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

      {selectedAccount && (
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
