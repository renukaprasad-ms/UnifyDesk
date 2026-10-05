import { useEffect, useMemo, useState } from 'react'
import AdFeedbackAlert from '../../components/ads/AdFeedbackAlert'
import {
  FiAlertTriangle,
  FiCheckCircle,
  FiFacebook,
  FiInstagram,
  FiRefreshCw,
  FiShield,
  FiTarget,
  FiZap,
} from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdAccount,
  AdAccountHealth,
  AdAccountResource,
  AdProviderConnection,
  MetaDeliveryCheck,
} from '../../features/ads/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'
import { PageSkeleton } from '../../components/ui/Skeleton'

const emptyHealth = (account: AdAccount): AdAccountHealth => ({
  synced: false,
  companyId: account.companyId,
  adAccountId: account.id,
  provider: account.provider,
  capabilities: [],
  allCapabilities: [],
  userTasks: [],
  failedDeliveryChecks: [],
})

const boolLabel = (value?: boolean | null) =>
  value == null ? 'Not reported' : value ? 'Yes' : 'No'

const statusTone = (value?: boolean | null) =>
  value === true
    ? 'bg-emerald-50 text-emerald-700'
    : value === false
      ? 'bg-amber-50 text-amber-700'
      : 'bg-slate-100 text-slate-600'

const pretty = (value: string) =>
  value
    .replaceAll('_', ' ')
    .replace(/\b\w/g, (character) => character.toUpperCase())

const resourceLabel = (resource: AdAccountResource) => {
  if (resource.resourceType === 'FACEBOOK_PAGE') return 'Facebook Page'
  if (resource.resourceType === 'INSTAGRAM_ACCOUNT') return 'Instagram'
  return 'Meta Pixel'
}

const resourceIcon = (resource: AdAccountResource) => {
  if (resource.resourceType === 'FACEBOOK_PAGE') return <FiFacebook />
  if (resource.resourceType === 'INSTAGRAM_ACCOUNT') return <FiInstagram />
  return <FiTarget />
}

const checkName = (check: MetaDeliveryCheck) =>
  check.summary || check.check_name || 'Provider delivery check'

const providerName = (account?: AdAccount | null) =>
  account?.provider === 'GOOGLE_ADS' ? 'Google Ads' : 'Meta Ads'

const accessExpiryState = (expiresAt?: string | null) => {
  if (!expiresAt) return { expired: false, soon: false }
  const expiresAtMs = new Date(expiresAt).getTime()
  const now = Date.now()
  return {
    expired: expiresAtMs < now,
    soon: expiresAtMs >= now && expiresAtMs - now < 7 * 24 * 60 * 60 * 1000,
  }
}

export default function AdSettings() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const { can } = useIam()

  const [accounts, setAccounts] = useState<AdAccount[]>([])
  const [connections, setConnections] = useState<AdProviderConnection[]>([])
  const [resources, setResources] = useState<AdAccountResource[]>([])
  const [health, setHealth] = useState<AdAccountHealth | null>(null)
  const [selectedAccountId, setSelectedAccountId] = useState('')
  const [loading, setLoading] = useState(true)
  const [syncingHealth, setSyncingHealth] = useState(false)
  const [syncingResources, setSyncingResources] = useState(false)
  const [savingPrimary, setSavingPrimary] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const managedAccounts = useMemo(
    () =>
      accounts.filter((account) => account.managed && account.accessible),
    [accounts],
  )

  const selectedAccount =
    managedAccounts.find((account) => account.id === selectedAccountId) ??
    managedAccounts.find((account) => account.primaryAccount) ??
    managedAccounts[0] ??
    null

  const connection = selectedAccount
    ? connections.find((item) => item.id === selectedAccount.connectionId) ?? null
    : null

  const failedChecks = Array.isArray(health?.failedDeliveryChecks)
    ? health.failedDeliveryChecks
    : []

  const loadAccountContext = async (account: AdAccount) => {
    if (!selectedCompany) return
    const [resourceData, healthData] = await Promise.all([
      adCenterApi.resources(selectedCompany.companyId, account.id),
      adCenterApi.health(selectedCompany.companyId, account.id),
    ])
    setResources(resourceData)
    setHealth(healthData)
  }

  const load = async () => {
    if (!selectedCompany) return
    setLoading(true)
    setError('')
    try {
      const [accountData, connectionData] = await Promise.all([
        adCenterApi.accounts(selectedCompany.companyId),
        adCenterApi.connections(selectedCompany.companyId),
      ])
      setAccounts(accountData)
      setConnections(connectionData)

      const eligible = accountData.filter(
        (account) => account.managed && account.accessible,
      )
      const preferred =
        eligible.find((account) => account.primaryAccount) ?? eligible[0]
      if (preferred) {
        setSelectedAccountId(preferred.id)
        await loadAccountContext(preferred)
      } else {
        setResources([])
        setHealth(null)
      }
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load Ad Center settings'))
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
    setNotice('')
    try {
      await loadAccountContext(account)
    } catch (loadError) {
      setHealth(emptyHealth(account))
      setResources([])
      setError(apiErrorMessage(loadError, 'Unable to load account settings'))
    } finally {
      setLoading(false)
    }
  }

  const syncHealth = async () => {
    if (!selectedCompany || !selectedAccount) return
    setSyncingHealth(true)
    setError('')
    setNotice('')
    try {
      const updated =
        selectedAccount.provider === 'GOOGLE_ADS'
          ? await adCenterApi.syncGoogleHealth(
              selectedCompany.companyId,
              selectedAccount.id,
            )
          : await adCenterApi.syncMetaHealth(
              selectedCompany.companyId,
              selectedAccount.id,
            )
      setHealth(updated)
      setNotice(`${providerName(selectedAccount)} account health synchronized.`)
      try {
        await adCenterApi.reconcileRecommendations(
          selectedCompany.companyId,
          selectedAccount.id,
        )
      } catch {
        // Health sync succeeded; recommendation lifecycle reconciliation is best effort.
      }
      window.dispatchEvent(new Event('ad-center:recommendations-changed'))
    } catch (syncError) {
      setError(apiErrorMessage(syncError, 'Unable to synchronize provider account health'))
    } finally {
      setSyncingHealth(false)
    }
  }

  const syncResources = async () => {
    if (!selectedCompany || !selectedAccount || selectedAccount.provider !== 'META_ADS') return
    setSyncingResources(true)
    setError('')
    setNotice('')
    try {
      const result = await adCenterApi.syncMetaResources(
        selectedCompany.companyId,
        selectedAccount.id,
      )
      setResources(result.resources)
      try {
        await adCenterApi.reconcileRecommendations(
          selectedCompany.companyId,
          selectedAccount.id,
        )
      } catch {
        // Resource sync succeeded; recommendation lifecycle reconciliation is best effort.
      }
      window.dispatchEvent(new Event('ad-center:recommendations-changed'))
      setNotice(
        `Synced ${result.pages} Facebook page(s), ${result.instagramAccounts} Instagram account(s), and ${result.pixels} pixel(s).`,
      )
    } catch (syncError) {
      setError(apiErrorMessage(syncError, 'Unable to synchronize Meta resources'))
    } finally {
      setSyncingResources(false)
    }
  }

  const makePrimary = async () => {
    if (!selectedCompany || !selectedAccount || selectedAccount.primaryAccount) return
    setSavingPrimary(true)
    setError('')
    setNotice('')
    try {
      const updated = await adCenterApi.updateAccount(
        selectedCompany.companyId,
        selectedAccount.id,
        true,
        true,
      )
      setAccounts((current) =>
        current.map((account) => ({
          ...account,
          primaryAccount:
            account.provider === updated.provider
              ? account.id === updated.id
              : account.primaryAccount,
        })),
      )
      setNotice(`${updated.name} is now the primary ${providerName(updated)} account.`)
      window.dispatchEvent(new Event('ad-center:recommendations-changed'))
    } catch (updateError) {
      setError(apiErrorMessage(updateError, 'Unable to update primary ad account'))
    } finally {
      setSavingPrimary(false)
    }
  }

  if (!selectedCompany) return null

  const accessExpiry = health?.accessExpiresAt
    ? new Date(health.accessExpiresAt)
    : null
  const { expired: accessExpired, soon: accessSoon } = accessExpiryState(
    health?.accessExpiresAt,
  )

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Ad Center</p>
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
              Settings
            </h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
              Inspect provider connection state, delivery health, account capabilities, and connected advertising resources.
            </p>
          </div>

          <div className="flex flex-wrap gap-2">
            {can(PERMISSIONS.ADS_MODIFY) && (
              <>
                {selectedAccount?.provider === 'META_ADS' && (
                  <button
                    type="button"
                    className="secondary-button inline-flex items-center gap-2"
                    disabled={!selectedAccount || syncingResources}
                    onClick={() => void syncResources()}
                  >
                    <FiRefreshCw aria-hidden="true" />
                    {syncingResources ? 'Syncing resources…' : 'Sync resources'}
                  </button>
                )}
                <button
                  type="button"
                  className="primary-button inline-flex items-center gap-2"
                  disabled={!selectedAccount || syncingHealth}
                  onClick={() => void syncHealth()}
                >
                  <FiRefreshCw aria-hidden="true" />
                  {syncingHealth ? 'Checking provider…' : 'Sync account health'}
                </button>
              </>
            )}
          </div>
        </header>
        <AdFeedbackAlert error={error} notice={notice} />

        <div className="mt-6 max-w-sm">
          <label className="text-xs font-semibold text-slate-700">
            Managed ad account
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

        {managedAccounts.length === 0 ? (
          <div className="panel mt-6 p-10 text-center text-sm text-slate-500">
            Connect an advertising provider and enable at least one managed ad account before using Settings.
          </div>
        ) : loading || !selectedAccount ? (
          <div className="mt-6"><PageSkeleton /></div>
        ) : (
          <>
            <div className="mt-6 grid gap-6 lg:grid-cols-2">
              <div className="panel p-5 sm:p-6">
                <div className="flex items-center gap-2">
                  <FiZap className="text-indigo-500" aria-hidden="true" />
                  <h2 className="text-base font-bold text-slate-950">
                    Connection & account
                  </h2>
                </div>

                <dl className="mt-5 divide-y divide-slate-100 text-sm">
                  <div className="flex items-start justify-between gap-4 py-3">
                    <dt className="text-slate-500">Connection</dt>
                    <dd className="text-right font-semibold text-slate-900">
                      {connection?.displayName || providerName(selectedAccount)} · {connection?.status || 'UNKNOWN'}
                    </dd>
                  </div>
                  <div className="flex items-start justify-between gap-4 py-3">
                    <dt className="text-slate-500">Ad account</dt>
                    <dd className="text-right font-semibold text-slate-900">
                      {selectedAccount.name}
                    </dd>
                  </div>
                  <div className="flex items-start justify-between gap-4 py-3">
                    <dt className="text-slate-500">Provider account ID</dt>
                    <dd className="font-mono text-xs font-semibold text-slate-700">
                      {selectedAccount.externalAccountId}
                    </dd>
                  </div>
                  {selectedAccount.provider === 'META_ADS' && (
                    <div className="flex items-start justify-between gap-4 py-3">
                      <dt className="text-slate-500">Business ID</dt>
                      <dd className="font-mono text-xs font-semibold text-slate-700">
                        {selectedAccount.externalBusinessId || 'Not reported'}
                      </dd>
                    </div>
                  )}
                  <div className="flex items-start justify-between gap-4 py-3">
                    <dt className="text-slate-500">Account status</dt>
                    <dd className="text-right font-semibold text-slate-900">
                      {selectedAccount.status}
                    </dd>
                  </div>
                  <div className="flex items-start justify-between gap-4 py-3">
                    <dt className="text-slate-500">Currency / timezone</dt>
                    <dd className="text-right font-semibold text-slate-900">
                      {selectedAccount.currency || '—'} · {selectedAccount.timezone || '—'}
                    </dd>
                  </div>
                  <div className="flex items-start justify-between gap-4 py-3">
                    <dt className="text-slate-500">Primary account</dt>
                    <dd className="text-right">
                      {selectedAccount.primaryAccount ? (
                        <span className="rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700">
                          Primary
                        </span>
                      ) : can(PERMISSIONS.ADS_MODIFY) ? (
                        <button
                          type="button"
                          className="secondary-button px-3 py-2 text-xs"
                          disabled={savingPrimary}
                          onClick={() => void makePrimary()}
                        >
                          {savingPrimary ? 'Saving…' : 'Make primary'}
                        </button>
                      ) : (
                        <span className="text-slate-400">No</span>
                      )}
                    </dd>
                  </div>
                </dl>
              </div>

              <div className="panel p-5 sm:p-6">
                <div className="flex items-center gap-2">
                  <FiShield className="text-indigo-500" aria-hidden="true" />
                  <h2 className="text-base font-bold text-slate-950">
                    Account health
                  </h2>
                </div>

                {!health?.synced ? (
                  <div className="mt-5 rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm leading-6 text-slate-500">
                    Health has not been synchronized yet. Run <strong>Sync account health</strong> to read provider delivery checks, capabilities, and account state.
                  </div>
                ) : (
                  <div className="mt-5 grid gap-3 sm:grid-cols-2">
                    {selectedAccount.provider === 'META_ADS' &&
                      [
                        ['Page authorized', health.pageAuthorized],
                        ['Pixel terms accepted', health.offsitePixelsTosAccepted],
                        ['Notifications enabled', health.notificationsEnabled],
                        ['Direct deals enabled', health.directDealsEnabled],
                      ].map(([label, value]) => (
                        <div key={String(label)} className="rounded-xl border border-slate-100 p-4">
                          <p className="text-xs font-semibold text-slate-400">{String(label)}</p>
                          <span
                            className={`mt-2 inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${statusTone(
                              value as boolean | null | undefined,
                            )}`}
                          >
                            {boolLabel(value as boolean | null | undefined)}
                          </span>
                        </div>
                      ))}

                    <div className="rounded-xl border border-slate-100 p-4">
                      <p className="text-xs font-semibold text-slate-400">Opportunity score</p>
                      <p className="mt-2 text-lg font-bold text-slate-950">
                        {health.opportunityScore == null ? 'Not reported' : health.opportunityScore}
                      </p>
                    </div>

                    <div className="rounded-xl border border-slate-100 p-4">
                      <p className="text-xs font-semibold text-slate-400">Access expiry</p>
                      <p
                        className={`mt-2 text-sm font-semibold ${
                          accessExpired
                            ? 'text-red-700'
                            : accessSoon
                              ? 'text-amber-700'
                              : 'text-slate-900'
                        }`}
                      >
                        {accessExpiry ? accessExpiry.toLocaleString() : 'Not reported'}
                      </p>
                    </div>
                  </div>
                )}

                {health?.synced && (
                  <p className="mt-4 text-xs text-slate-400">
                    Last checked {health.lastSyncAt ? new Date(health.lastSyncAt).toLocaleString() : '—'}
                  </p>
                )}
              </div>
            </div>

            {health?.synced && failedChecks.length > 0 && (
              <div className="panel mt-6 overflow-hidden">
                <div className="border-b border-red-100 bg-red-50 px-5 py-4">
                  <div className="flex items-center gap-2">
                    <FiAlertTriangle className="text-red-600" aria-hidden="true" />
                    <h2 className="font-bold text-red-900">
                      {providerName(selectedAccount)} delivery checks requiring attention
                    </h2>
                  </div>
                  <p className="mt-1 text-xs text-red-700">
                    These checks are reported or derived from the selected advertising provider.
                  </p>
                </div>
                <div className="divide-y divide-slate-100">
                  {failedChecks.map((check, index) => (
                    <div key={`${check.check_name ?? 'check'}-${index}`} className="p-5">
                      <p className="font-semibold text-slate-950">{checkName(check)}</p>
                      {check.description && (
                        <p className="mt-2 text-sm leading-6 text-slate-500">
                          {check.description}
                        </p>
                      )}
                      {check.check_name && (
                        <p className="mt-2 font-mono text-xs text-slate-400">
                          {check.check_name}
                        </p>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            )}

            <div className="mt-6 grid gap-6 lg:grid-cols-2">
              <div className="panel p-5 sm:p-6">
                <h2 className="text-base font-bold text-slate-950">
                  Provider capabilities & tasks
                </h2>
                <p className="mt-1 text-xs leading-5 text-slate-500">
                  Provider-reported account capabilities and tasks available to the authorized user.
                </p>

                <div className="mt-5">
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                    User tasks
                  </p>
                  <div className="mt-2 flex flex-wrap gap-2">
                    {(health?.userTasks ?? []).length > 0 ? (
                      health!.userTasks.map((task) => (
                        <span
                          key={task}
                          className="rounded-full bg-indigo-50 px-2.5 py-1 text-xs font-semibold text-indigo-700"
                        >
                          {pretty(task)}
                        </span>
                      ))
                    ) : (
                      <span className="text-sm text-slate-400">No tasks reported</span>
                    )}
                  </div>
                </div>

                <div className="mt-5">
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                    Capabilities
                  </p>
                  <div className="mt-2 flex max-h-48 flex-wrap gap-2 overflow-y-auto">
                    {(health?.capabilities ?? []).length > 0 ? (
                      health!.capabilities.map((capability) => (
                        <span
                          key={capability}
                          className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600"
                        >
                          {pretty(capability)}
                        </span>
                      ))
                    ) : (
                      <span className="text-sm text-slate-400">No capabilities reported</span>
                    )}
                  </div>
                </div>

                <div className="mt-5">
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                    All provider capabilities
                  </p>
                  <div className="mt-2 flex max-h-48 flex-wrap gap-2 overflow-y-auto">
                    {(health?.allCapabilities ?? []).length > 0 ? (
                      health!.allCapabilities.map((capability) => (
                        <span
                          key={capability}
                          className="rounded-full border border-slate-200 bg-white px-2.5 py-1 text-xs font-semibold text-slate-500"
                        >
                          {pretty(capability)}
                        </span>
                      ))
                    ) : (
                      <span className="text-sm text-slate-400">
                        No broader capability set reported
                      </span>
                    )}
                  </div>
                </div>
              </div>

              <div className="panel p-5 sm:p-6">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <h2 className="text-base font-bold text-slate-950">
                      {selectedAccount.provider === 'GOOGLE_ADS' ? 'Google account context' : 'Connected resources'}
                    </h2>
                    <p className="mt-1 text-xs leading-5 text-slate-500">
                      {selectedAccount.provider === 'GOOGLE_ADS'
                        ? 'Manager/test-account context is stored with the connected Google Ads account; Google campaign resources are synchronized in their dedicated Ad Center sections.'
                        : 'Facebook Pages, Instagram accounts, and Pixels discovered for this ad account.'}
                    </p>
                  </div>
                  <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600">
                    {resources.length}
                  </span>
                </div>

                {selectedAccount.provider === 'GOOGLE_ADS' ? (
                  <div className="mt-5 rounded-xl border border-slate-200 bg-slate-50 p-5 text-sm leading-6 text-slate-600">
                    Account ID <span className="font-mono font-semibold">{selectedAccount.externalAccountId}</span>.
                    Campaigns, ad groups, keywords, ads, audiences, conversions, billing, and analytics are managed through their dedicated provider-aware sections.
                  </div>
                ) : resources.length === 0 ? (
                  <div className="mt-5 rounded-xl border border-dashed border-slate-200 p-5 text-sm text-slate-500">
                    No resources are currently synchronized.
                  </div>
                ) : (
                  <div className="mt-5 divide-y divide-slate-100">
                    {resources.map((resource) => (
                      <div
                        key={resource.id}
                        className="flex items-center justify-between gap-4 py-3"
                      >
                        <div className="flex min-w-0 items-center gap-3">
                          <span className="rounded-lg bg-slate-100 p-2 text-slate-600">
                            {resourceIcon(resource)}
                          </span>
                          <div className="min-w-0">
                            <p className="truncate font-semibold text-slate-950">
                              {resource.name || resource.externalResourceId}
                            </p>
                            <p className="mt-1 text-xs text-slate-400">
                              {resourceLabel(resource)} · {resource.externalResourceId}
                            </p>
                          </div>
                        </div>
                        {resource.accessible ? (
                          <FiCheckCircle
                            className="shrink-0 text-emerald-500"
                            aria-label="Accessible"
                          />
                        ) : (
                          <FiAlertTriangle
                            className="shrink-0 text-amber-500"
                            aria-label="Unavailable"
                          />
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>

            {health?.synced && (
              <div className="panel mt-6 p-5 sm:p-6">
                <h2 className="text-base font-bold text-slate-950">
                  Provider diagnostics
                </h2>
                <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                  <div>
                    <p className="text-xs text-slate-400">Provider account status code</p>
                    <p className="mt-1 font-semibold text-slate-800">
                      {health.accountStatus ?? '—'}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-slate-400">Disable reason code</p>
                    <p className="mt-1 font-semibold text-slate-800">
                      {health.disableReason ?? '—'}
                    </p>
                  </div>
                  {selectedAccount.provider === 'META_ADS' && (
                    <div>
                      <p className="text-xs text-slate-400">Offsite CLO signal</p>
                      <p className="mt-1 font-semibold text-slate-800">
                        {health.offsiteCloSignalStatus ?? '—'}
                      </p>
                    </div>
                  )}
                  <div>
                    <p className="text-xs text-slate-400">Failed delivery checks</p>
                    <p className="mt-1 font-semibold text-slate-800">
                      {failedChecks.length}
                    </p>
                  </div>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </section>
  )
}
