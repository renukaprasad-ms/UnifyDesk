import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useEffect, useMemo, useState } from 'react'
import AdFeedbackAlert from '../../components/ads/AdFeedbackAlert'
import { FiPlus, FiRefreshCw, FiUploadCloud, FiUsers } from 'react-icons/fi'
import MetaAudienceModal from '../../components/ads/MetaAudienceModal'
import GoogleAudienceModal from '../../components/ads/GoogleAudienceModal'
import GoogleCustomerMatchUploadModal from '../../components/ads/GoogleCustomerMatchUploadModal'
import MetaCustomerListUploadModal from '../../components/ads/MetaCustomerListUploadModal'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdAccount,
  AdAccountResource,
  AdAudience,
  CreateMetaAudienceInput,
  CreateGoogleAudienceInput,
  GoogleCustomerMatchUploadInput,
  MetaCustomerListMembersInput,
} from '../../features/ads/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'
import { PageSkeleton } from '../../components/ui/Skeleton'

const formatSubtype = (value?: string | null) =>
  value
    ? value
        .replaceAll('_', ' ')
        .toLowerCase()
        .replace(/\b\w/g, (c) => c.toUpperCase())
    : 'Audience'

const countLabel = (audience: AdAudience) => {
  const lower = audience.approximateCountLowerBound
  const upper = audience.approximateCountUpperBound
  if (lower == null && upper == null) return 'Size unavailable'
  const formatter = new Intl.NumberFormat(undefined, { notation: 'compact' })
  if (lower != null && upper != null) {
    return `${formatter.format(lower)}–${formatter.format(upper)} people`
  }
  return `≈ ${formatter.format(lower ?? upper ?? 0)} people`
}

const statusLabel = (json?: string | null) => {
  if (!json) return 'Unknown'
  try {
    const parsed = JSON.parse(json) as { description?: string; code?: number }
    return parsed.description || (parsed.code != null ? `Code ${parsed.code}` : 'Unknown')
  } catch {
    return 'Unknown'
  }
}

export default function AdAudiences() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const { can } = useIam()
  const [accounts, setAccounts] = useState<AdAccount[]>([])
  const [audiences, setAudiences] = useState<AdAudience[]>([])
  const [resources, setResources] = useState<AdAccountResource[]>([])
  const [selectedAccountId, setSelectedAccountId] = useState('')
  const [loading, setLoading] = useState(true)
  const [syncing, setSyncing] = useState(false)
  const [creating, setCreating] = useState(false)
  const [uploadingMembers, setUploadingMembers] = useState(false)
  const [uploadAudience, setUploadAudience] = useState<AdAudience | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [error, setError] = useSnackbarState('', 'error')
  const [notice, setNotice] = useSnackbarState('', 'success')

  const managedAccounts = useMemo(
    () => accounts.filter((account) => account.managed && account.accessible),
    [accounts],
  )

  const selectedAccount =
    managedAccounts.find((account) => account.id === selectedAccountId) ??
    managedAccounts.find((account) => account.primaryAccount) ??
    managedAccounts[0] ??
    null

  const loadForAccount = async (accountId: string) => {
    if (!selectedCompany) return
    const [audienceData, resourceData] = await Promise.all([
      adCenterApi.audiences(selectedCompany.companyId, accountId),
      adCenterApi.resources(selectedCompany.companyId, accountId),
    ])
    setAudiences(audienceData)
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
        await loadForAccount(preferred.id)
      } else {
        setAudiences([])
        setResources([])
      }
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load audiences'))
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
      setError(apiErrorMessage(loadError, 'Unable to load audiences'))
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
      if (selectedAccount.provider === 'META_ADS') {
        await adCenterApi.syncMetaResources(selectedCompany.companyId, selectedAccount.id)
      }
      const result =
        selectedAccount.provider === 'GOOGLE_ADS'
          ? await adCenterApi.syncGoogleAudiences(selectedCompany.companyId, selectedAccount.id)
          : await adCenterApi.syncMetaAudiences(selectedCompany.companyId, selectedAccount.id)
      await loadForAccount(selectedAccount.id)
      setNotice(
        `Synchronized ${result.synchronizedCount} audience(s) from ${selectedAccount.provider === 'GOOGLE_ADS' ? 'Google Ads' : 'Meta'}.`,
      )
    } catch (syncError) {
      setError(apiErrorMessage(syncError, 'Unable to synchronize audiences'))
    } finally {
      setSyncing(false)
    }
  }

  const createMeta = async (input: CreateMetaAudienceInput) => {
    if (!selectedCompany || !selectedAccount) return
    setCreating(true)
    setError('')
    setNotice('')
    try {
      const created = await adCenterApi.createMetaAudience(
        selectedCompany.companyId,
        selectedAccount.id,
        input,
      )
      setAudiences((current) => [created, ...current.filter((item) => item.id !== created.id)])
      setModalOpen(false)
      setNotice(`${created.name} was created in Meta.`)
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create Meta audience'))
    } finally {
      setCreating(false)
    }
  }

  const createGoogle = async (input: CreateGoogleAudienceInput) => {
    if (!selectedCompany || !selectedAccount) return
    setCreating(true)
    setError('')
    setNotice('')
    try {
      const created = await adCenterApi.createGoogleAudience(
        selectedCompany.companyId,
        selectedAccount.id,
        input,
      )
      setAudiences((current) => [created, ...current.filter((item) => item.id !== created.id)])
      setModalOpen(false)
      setNotice(`${created.name} was created in Google Ads.`)
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create Google Ads audience'))
    } finally {
      setCreating(false)
    }
  }

  const uploadGoogleMembers = async (input: GoogleCustomerMatchUploadInput) => {
    if (!selectedCompany || !uploadAudience) return
    setUploadingMembers(true)
    setError('')
    setNotice('')
    try {
      const result = await adCenterApi.uploadGoogleCustomerMatchMembers(
        selectedCompany.companyId,
        uploadAudience.id,
        input,
      )
      setUploadAudience(null)
      setNotice(
        result.validateOnly
          ? `Validated ${result.submittedCount} Customer Match member(s).`
          : `Submitted ${result.submittedCount} Customer Match member(s) to Google Data Manager.`,
      )
      if (result.warnings.length > 0) {
        setNotice(
          `${result.validateOnly ? 'Validation' : 'Upload'} completed with ${result.warnings.length} warning(s).`,
        )
      }
    } catch (uploadError) {
      setError(apiErrorMessage(uploadError, 'Unable to upload Google Customer Match members'))
    } finally {
      setUploadingMembers(false)
    }
  }

  const uploadMetaMembers = async (input: MetaCustomerListMembersInput) => {
    if (!selectedCompany || !uploadAudience) return
    setUploadingMembers(true)
    setError('')
    setNotice('')
    try {
      const result = await adCenterApi.mutateMetaCustomerListMembers(
        selectedCompany.companyId,
        uploadAudience.id,
        input,
      )
      setUploadAudience(null)
      setNotice(
        `${result.operation === 'ADD' ? 'Submitted' : 'Removed'} ${result.submittedRows} Meta customer-list member row(s) in ${result.batchCount} batch(es). Meta reported ${result.providerInvalidRows} invalid row(s).`,
      )
    } catch (uploadError) {
      setError(apiErrorMessage(uploadError, 'Unable to update Meta customer-list members'))
    } finally {
      setUploadingMembers(false)
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
              Audiences
            </h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
              Sync and create reusable audiences across Meta Ads and Google Ads.
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
            {can(PERMISSIONS.ADS_CREATE) && (
              <button
                type="button"
                className="primary-button inline-flex items-center gap-2"
                disabled={!selectedAccount}
                onClick={() => setModalOpen(true)}
              >
                <FiPlus aria-hidden="true" />
                Create audience
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

        {loading ? (
          <div className="mt-6"><PageSkeleton /></div>
        ) : managedAccounts.length === 0 ? (
          <div className="panel mt-6 p-10 text-center text-sm text-slate-500">
            Connect and manage an ad account before using audiences.
          </div>
        ) : audiences.length === 0 ? (
          <div className="panel mt-6 p-10 text-center">
            <FiUsers className="mx-auto text-3xl text-slate-300" aria-hidden="true" />
            <h2 className="mt-3 text-base font-bold text-slate-950">No audiences yet</h2>
            <p className="mt-2 text-sm text-slate-500">
              Sync existing provider audiences or create a new audience.
            </p>
          </div>
        ) : (
          <div className="panel mt-6 overflow-hidden">
            <div className="hidden overflow-x-auto md:block">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="px-5 py-3">Audience</th>
                    <th className="px-5 py-3">Type</th>
                    <th className="px-5 py-3">Estimated size</th>
                    <th className="px-5 py-3">Delivery</th>
                    <th className="px-5 py-3">Retention</th>
                    {selectedAccount && <th className="px-5 py-3 text-right">Action</th>}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {audiences.map((audience) => (
                    <tr key={audience.id}>
                      <td className="px-5 py-4">
                        <p className="font-semibold text-slate-950">{audience.name}</p>
                        <p className="mt-1 text-xs text-slate-400">{audience.externalAudienceId}</p>
                      </td>
                      <td className="px-5 py-4 text-slate-600">
                        {formatSubtype(audience.subtype)}
                        {audience.customerFileSource && (
                          <div className="mt-1 text-[11px] text-slate-400">
                            {formatSubtype(audience.customerFileSource)}
                          </div>
                        )}
                      </td>
                      <td className="px-5 py-4 text-slate-600">{countLabel(audience)}</td>
                      <td className="px-5 py-4">
                        <span
                          className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
                            audience.accessible
                              ? 'bg-emerald-50 text-emerald-700'
                              : 'bg-slate-100 text-slate-500'
                          }`}
                        >
                          {audience.accessible
                            ? statusLabel(audience.deliveryStatusJson)
                            : 'Unavailable'}
                        </span>
                      </td>
                      <td className="px-5 py-4 text-slate-600">
                        {audience.retentionDays != null ? `${audience.retentionDays} days` : '—'}
                      </td>
                      {selectedAccount && (
                        <td className="px-5 py-4 text-right">
                          {can(PERMISSIONS.ADS_MODIFY) &&
                            audience.accessible &&
                            (selectedAccount.provider === 'GOOGLE_ADS' ||
                              (selectedAccount.provider === 'META_ADS' &&
                                audience.subtype === 'CUSTOM')) && (
                              <button
                                type="button"
                                className="secondary-button inline-flex items-center gap-2 text-xs"
                                onClick={() => setUploadAudience(audience)}
                              >
                                <FiUploadCloud aria-hidden="true" />
                                {selectedAccount.provider === 'META_ADS'
                                  ? 'Manage members'
                                  : 'Upload members'}
                              </button>
                            )}
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="divide-y divide-slate-100 md:hidden">
              {audiences.map((audience) => (
                <div key={audience.id} className="p-5">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="font-semibold text-slate-950">{audience.name}</p>
                      <p className="mt-1 text-xs text-slate-500">
                        {formatSubtype(audience.subtype)}
                        {audience.customerFileSource
                          ? ` · ${formatSubtype(audience.customerFileSource)}`
                          : ''}
                      </p>
                    </div>
                    <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600">
                      {statusLabel(audience.deliveryStatusJson)}
                    </span>
                  </div>
                  <p className="mt-3 text-xs text-slate-500">{countLabel(audience)}</p>
                  {can(PERMISSIONS.ADS_MODIFY) &&
                    audience.accessible &&
                    (selectedAccount?.provider === 'GOOGLE_ADS' ||
                      (selectedAccount?.provider === 'META_ADS' &&
                        audience.subtype === 'CUSTOM')) && (
                      <button
                        type="button"
                        className="secondary-button mt-4 inline-flex items-center gap-2 text-xs"
                        onClick={() => setUploadAudience(audience)}
                      >
                        <FiUploadCloud aria-hidden="true" />
                        {selectedAccount?.provider === 'META_ADS'
                          ? 'Manage members'
                          : 'Upload members'}
                      </button>
                    )}
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {selectedAccount?.provider === 'META_ADS' && (
        <MetaAudienceModal
          audiences={audiences}
          resources={resources}
          open={modalOpen}
          saving={creating}
          onClose={() => setModalOpen(false)}
          onCreate={createMeta}
        />
      )}
      {selectedAccount?.provider === 'GOOGLE_ADS' && (
        <GoogleCustomerMatchUploadModal
          audience={uploadAudience}
          open={uploadAudience !== null}
          saving={uploadingMembers}
          onClose={() => setUploadAudience(null)}
          onUpload={uploadGoogleMembers}
        />
      )}
      {selectedAccount?.provider === 'META_ADS' && (
        <MetaCustomerListUploadModal
          audience={uploadAudience}
          open={uploadAudience !== null}
          saving={uploadingMembers}
          onClose={() => setUploadAudience(null)}
          onSubmit={uploadMetaMembers}
        />
      )}

      {selectedAccount?.provider === 'GOOGLE_ADS' && (
        <GoogleAudienceModal
          open={modalOpen}
          saving={creating}
          onClose={() => setModalOpen(false)}
          onCreate={createGoogle}
        />
      )}
    </section>
  )
}
