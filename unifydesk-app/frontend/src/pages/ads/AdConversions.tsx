import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useEffect, useMemo, useState } from 'react'
import AdFeedbackAlert from '../../components/ads/AdFeedbackAlert'
import { FiActivity, FiPlus, FiRefreshCw, FiUploadCloud } from 'react-icons/fi'
import MetaConversionModal from '../../components/ads/MetaConversionModal'
import GoogleConversionModal from '../../components/ads/GoogleConversionModal'
import GoogleConversionEventUploadModal from '../../components/ads/GoogleConversionEventUploadModal'
import MetaPixelModal from '../../components/ads/MetaPixelModal'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdAccount,
  AdAccountResource,
  AdConversion,
  CreateMetaConversionInput,
  CreateGoogleConversionInput,
  GoogleConversionEventUploadInput,
} from '../../features/ads/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'
import { PageSkeleton } from '../../components/ui/Skeleton'

const formatValue = (value?: string | null) =>
  value
    ? value
        .replaceAll('_', ' ')
        .toLowerCase()
        .replace(/\b\w/g, (c) => c.toUpperCase())
    : '—'

export default function AdConversions() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const { can } = useIam()
  const [accounts, setAccounts] = useState<AdAccount[]>([])
  const [resources, setResources] = useState<AdAccountResource[]>([])
  const [conversions, setConversions] = useState<AdConversion[]>([])
  const [selectedAccountId, setSelectedAccountId] = useState('')
  const [loading, setLoading] = useState(true)
  const [syncing, setSyncing] = useState(false)
  const [creating, setCreating] = useState(false)
  const [creatingPixel, setCreatingPixel] = useState(false)
  const [uploadingEvent, setUploadingEvent] = useState(false)
  const [uploadConversion, setUploadConversion] = useState<AdConversion | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [pixelModalOpen, setPixelModalOpen] = useState(false)
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

  const pixels = resources.filter(
    (resource) => resource.resourceType === 'META_PIXEL' && resource.accessible,
  )

  const loadForAccount = async (accountId: string) => {
    if (!selectedCompany) return
    const [resourceData, conversionData] = await Promise.all([
      adCenterApi.resources(selectedCompany.companyId, accountId),
      adCenterApi.conversions(selectedCompany.companyId, accountId),
    ])
    setResources(resourceData)
    setConversions(conversionData)
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
        setResources([])
        setConversions([])
      }
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load conversions'))
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
      setError(apiErrorMessage(loadError, 'Unable to load conversions'))
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
        const conversionSync = await adCenterApi.syncGoogleConversions(
          selectedCompany.companyId,
          selectedAccount.id,
        )
        await loadForAccount(selectedAccount.id)
        setNotice(`Synchronized ${conversionSync.synchronizedCount} Google conversion action(s).`)
      } else {
        const resourceSync = await adCenterApi.syncMetaResources(
          selectedCompany.companyId,
          selectedAccount.id,
        )
        const conversionSync = await adCenterApi.syncMetaConversions(
          selectedCompany.companyId,
          selectedAccount.id,
        )
        await loadForAccount(selectedAccount.id)
        setNotice(
          `Synchronized ${resourceSync.pixels} pixel(s) and ${conversionSync.synchronizedCount} custom conversion(s).`,
        )
      }
    } catch (syncError) {
      setError(apiErrorMessage(syncError, 'Unable to synchronize conversions'))
    } finally {
      setSyncing(false)
    }
  }

  const createPixel = async (name: string) => {
    if (!selectedCompany || !selectedAccount) return
    setCreatingPixel(true)
    setError('')
    setNotice('')
    try {
      const created = await adCenterApi.createMetaPixel(
        selectedCompany.companyId,
        selectedAccount.id,
        name,
      )
      setResources((current) => [
        ...current.filter((resource) => resource.id !== created.id),
        created,
      ])
      setPixelModalOpen(false)
      setNotice(`${created.name || 'Meta Pixel'} was created and synchronized.`)
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create Meta pixel'))
    } finally {
      setCreatingPixel(false)
    }
  }

  const createMeta = async (input: CreateMetaConversionInput) => {
    if (!selectedCompany || !selectedAccount) return
    setCreating(true)
    setError('')
    setNotice('')
    try {
      const created = await adCenterApi.createMetaConversion(
        selectedCompany.companyId,
        selectedAccount.id,
        input,
      )
      setConversions((current) => [created, ...current.filter((item) => item.id !== created.id)])
      setModalOpen(false)
      setNotice(`${created.name} was created in Meta.`)
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create Meta custom conversion'))
    } finally {
      setCreating(false)
    }
  }

  const createGoogle = async (input: CreateGoogleConversionInput) => {
    if (!selectedCompany || !selectedAccount) return
    setCreating(true)
    setError('')
    setNotice('')
    try {
      const created = await adCenterApi.createGoogleConversion(
        selectedCompany.companyId,
        selectedAccount.id,
        input,
      )
      setConversions((current) => [created, ...current.filter((item) => item.id !== created.id)])
      setModalOpen(false)
      setNotice(`${created.name} was created in Google Ads.`)
    } catch (createError) {
      setError(apiErrorMessage(createError, 'Unable to create Google Ads conversion action'))
    } finally {
      setCreating(false)
    }
  }

  const uploadGoogleConversionEvent = async (input: GoogleConversionEventUploadInput) => {
    if (!selectedCompany || !uploadConversion) return
    setUploadingEvent(true)
    setError('')
    setNotice('')
    try {
      const result = await adCenterApi.uploadGoogleConversionEvents(
        selectedCompany.companyId,
        uploadConversion.id,
        input,
      )
      setUploadConversion(null)
      setNotice(
        result.validateOnly
          ? `Validated ${result.submittedCount} Google conversion event(s).`
          : `Submitted ${result.submittedCount} conversion event(s) to Google Data Manager.`,
      )
      if (result.warnings.length > 0) {
        setNotice(
          `${result.validateOnly ? 'Validation' : 'Upload'} completed with ${result.warnings.length} warning(s).`,
        )
      }
    } catch (uploadError) {
      setError(apiErrorMessage(uploadError, 'Unable to upload Google conversion events'))
    } finally {
      setUploadingEvent(false)
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
              Conversions
            </h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
              Inspect and manage conversion tracking for Meta Ads and Google Ads.
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
                disabled={
                  !selectedAccount ||
                  (selectedAccount.provider === 'META_ADS' && pixels.length === 0)
                }
                onClick={() => setModalOpen(true)}
              >
                <FiPlus aria-hidden="true" />
                Create conversion
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
            Connect and manage an advertising account before using conversions.
          </div>
        ) : (
          <>
            {selectedAccount?.provider === 'META_ADS' && (
              <>
                <div className="mt-6">
                  <div className="flex items-end justify-between gap-3">
                    <div>
                      <h2 className="text-lg font-bold text-slate-950">Pixels</h2>
                      <p className="mt-1 text-xs text-slate-500">
                        Event sources discovered from the selected Meta ad account.
                      </p>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-semibold text-slate-400">
                        {pixels.length} connected
                      </span>
                      {can(PERMISSIONS.ADS_CREATE) && (
                        <button
                          type="button"
                          className="secondary-button inline-flex items-center gap-2"
                          disabled={!selectedAccount}
                          onClick={() => setPixelModalOpen(true)}
                        >
                          <FiPlus aria-hidden="true" />
                          Create pixel
                        </button>
                      )}
                    </div>
                  </div>

                  {pixels.length === 0 ? (
                    <div className="panel mt-3 p-8 text-center text-sm text-slate-500">
                      No Meta pixels are accessible for this account. Sync Meta resources to refresh
                      the account assets.
                    </div>
                  ) : (
                    <div className="mt-3 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                      {pixels.map((pixel) => (
                        <div key={pixel.id} className="panel p-5">
                          <div className="flex items-start justify-between gap-3">
                            <div className="min-w-0">
                              <p className="truncate font-bold text-slate-950">
                                {pixel.name || 'Meta Pixel'}
                              </p>
                              <p className="mt-1 break-all text-xs text-slate-400">
                                {pixel.externalResourceId}
                              </p>
                            </div>
                            <span className="rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700">
                              Accessible
                            </span>
                          </div>
                          <p className="mt-4 text-xs text-slate-500">
                            Last resource sync:{' '}
                            {pixel.lastSyncAt
                              ? new Date(pixel.lastSyncAt).toLocaleString()
                              : 'Not synchronized'}
                          </p>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </>
            )}

            <div className="mt-8">
              <div>
                <h2 className="text-lg font-bold text-slate-950">
                  {selectedAccount?.provider === 'GOOGLE_ADS'
                    ? 'Conversion actions'
                    : 'Custom conversions'}
                </h2>
                <p className="mt-1 text-xs text-slate-500">
                  {selectedAccount?.provider === 'GOOGLE_ADS'
                    ? 'Google Ads conversion actions used for measurement and bidding.'
                    : 'Conversion definitions created from pixel event and URL rules.'}
                </p>
              </div>

              {conversions.length === 0 ? (
                <div className="panel mt-3 p-10 text-center">
                  <FiActivity className="mx-auto text-3xl text-slate-300" aria-hidden="true" />
                  <h3 className="mt-3 text-base font-bold text-slate-950">
                    {selectedAccount?.provider === 'GOOGLE_ADS'
                      ? 'No conversion actions yet'
                      : 'No custom conversions yet'}
                  </h3>
                  <p className="mt-2 text-sm text-slate-500">
                    {selectedAccount?.provider === 'GOOGLE_ADS'
                      ? 'Sync existing Google conversion actions or create the first one.'
                      : 'Sync existing conversions or create one from a connected pixel.'}
                  </p>
                </div>
              ) : (
                <div className="panel mt-3 overflow-hidden">
                  <div className="hidden overflow-x-auto md:block">
                    <table className="w-full text-left text-sm">
                      <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wide text-slate-500">
                        <tr>
                          <th className="px-5 py-3">Conversion</th>
                          <th className="px-5 py-3">Event</th>
                          <th className="px-5 py-3">
                            {selectedAccount?.provider === 'GOOGLE_ADS' ? 'Source' : 'Pixel'}
                          </th>
                          <th className="px-5 py-3">Last fired</th>
                          <th className="px-5 py-3">Status</th>
                          {selectedAccount?.provider === 'GOOGLE_ADS' && (
                            <th className="px-5 py-3 text-right">Action</th>
                          )}
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100">
                        {conversions.map((conversion) => (
                          <tr key={conversion.id}>
                            <td className="px-5 py-4">
                              <p className="font-semibold text-slate-950">{conversion.name}</p>
                              <p className="mt-1 text-xs text-slate-400">
                                {conversion.externalConversionId}
                              </p>
                            </td>
                            <td className="px-5 py-4 text-slate-600">
                              {formatValue(conversion.customEventType)}
                            </td>
                            <td className="px-5 py-4 text-slate-600">
                              {selectedAccount?.provider === 'GOOGLE_ADS'
                                ? formatValue(conversion.eventSourceType)
                                : (conversion.pixelId ?? '—')}
                            </td>
                            <td className="px-5 py-4 text-slate-600">
                              {conversion.lastFiredAt
                                ? new Date(conversion.lastFiredAt).toLocaleString()
                                : 'Never'}
                            </td>
                            <td className="px-5 py-4">
                              <span
                                className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
                                  conversion.unavailable || conversion.archived
                                    ? 'bg-slate-100 text-slate-500'
                                    : 'bg-emerald-50 text-emerald-700'
                                }`}
                              >
                                {conversion.unavailable
                                  ? 'Unavailable'
                                  : conversion.archived
                                    ? 'Archived'
                                    : 'Active'}
                              </span>
                            </td>
                            {selectedAccount?.provider === 'GOOGLE_ADS' && (
                              <td className="px-5 py-4 text-right">
                                {can(PERMISSIONS.ADS_MODIFY) &&
                                  conversion.accessible &&
                                  !conversion.archived && (
                                    <button
                                      type="button"
                                      className="secondary-button inline-flex items-center gap-2 text-xs"
                                      onClick={() => setUploadConversion(conversion)}
                                    >
                                      <FiUploadCloud aria-hidden="true" />
                                      Upload event
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
                    {conversions.map((conversion) => (
                      <div key={conversion.id} className="p-5">
                        <div className="flex items-start justify-between gap-3">
                          <div>
                            <p className="font-semibold text-slate-950">{conversion.name}</p>
                            <p className="mt-1 text-xs text-slate-500">
                              {formatValue(conversion.customEventType)}
                            </p>
                          </div>
                          <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600">
                            {conversion.archived ? 'Archived' : 'Active'}
                          </span>
                        </div>
                        <p className="mt-3 text-xs text-slate-400">
                          {selectedAccount?.provider === 'GOOGLE_ADS'
                            ? `Source ${formatValue(conversion.eventSourceType)}`
                            : `Pixel ${conversion.pixelId ?? '—'}`}
                        </p>
                        {selectedAccount?.provider === 'GOOGLE_ADS' &&
                          can(PERMISSIONS.ADS_MODIFY) &&
                          conversion.accessible &&
                          !conversion.archived && (
                            <button
                              type="button"
                              className="secondary-button mt-4 inline-flex items-center gap-2 text-xs"
                              onClick={() => setUploadConversion(conversion)}
                            >
                              <FiUploadCloud aria-hidden="true" />
                              Upload event
                            </button>
                          )}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </>
        )}
      </div>

      {selectedAccount?.provider === 'META_ADS' && (
        <MetaPixelModal
          open={pixelModalOpen}
          saving={creatingPixel}
          onClose={() => setPixelModalOpen(false)}
          onCreate={createPixel}
        />
      )}

      {selectedAccount?.provider === 'META_ADS' && (
        <MetaConversionModal
          resources={resources}
          open={modalOpen}
          saving={creating}
          onClose={() => setModalOpen(false)}
          onCreate={createMeta}
        />
      )}
      <GoogleConversionEventUploadModal
        conversion={uploadConversion}
        open={uploadConversion !== null}
        saving={uploadingEvent}
        defaultCurrency={selectedAccount?.currency}
        onClose={() => setUploadConversion(null)}
        onUpload={uploadGoogleConversionEvent}
      />

      {selectedAccount?.provider === 'GOOGLE_ADS' && (
        <GoogleConversionModal
          open={modalOpen}
          saving={creating}
          onClose={() => setModalOpen(false)}
          onCreate={createGoogle}
        />
      )}
    </section>
  )
}
