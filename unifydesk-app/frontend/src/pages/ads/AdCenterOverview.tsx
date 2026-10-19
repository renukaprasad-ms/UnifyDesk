import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useEffect, useState } from 'react'
import AdFeedbackAlert from '../../components/ads/AdFeedbackAlert'
import { showGlobalSnackbar } from '../../components/feedback/SnackbarProvider'
import {
  FiAlertTriangle,
  FiCheck,
  FiCheckCircle,
  FiExternalLink,
  FiRefreshCw,
  FiX,
  FiZap,
} from 'react-icons/fi'
import { Link } from 'react-router'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdAccount,
  AdAccountResource,
  AdProviderConnection,
  AdProviderInfo,
  AdRecommendationSummary,
} from '../../features/ads/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'

const providerCopy: Record<AdProviderInfo['provider'], string> = {
  GOOGLE_ADS:
    'Manage Google Ads campaigns, assets, conversions, reporting, and supported billing operations.',
  META_ADS: 'Manage Meta campaigns, ad sets, ads, audiences, pixels, creatives, and reporting.',
}

export default function AdCenterOverview() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const { can } = useIam()
  const [providers, setProviders] = useState<AdProviderInfo[]>([])
  const [connections, setConnections] = useState<AdProviderConnection[]>([])
  const [accounts, setAccounts] = useState<AdAccount[]>([])
  const [resourcesByAccount, setResourcesByAccount] = useState<Record<string, AdAccountResource[]>>(
    {},
  )
  const [recommendationSummary, setRecommendationSummary] =
    useState<AdRecommendationSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [connectingProvider, setConnectingProvider] = useState<string | null>(null)
  const [workingAccountId, setWorkingAccountId] = useState<string | null>(null)
  const [syncingResourceAccountId, setSyncingResourceAccountId] = useState<string | null>(null)
  const [workingConnectionId, setWorkingConnectionId] = useState<string | null>(null)
  const [error, setError] = useSnackbarState('', 'error')
  const [notice, setNotice] = useSnackbarState('', 'success')

  const load = async () => {
    if (!selectedCompany) return
    setLoading(true)
    try {
      const [providerData, connectionData, accountData, resourceData, recommendationData] =
        await Promise.all([
          adCenterApi.providers(selectedCompany.companyId),
          adCenterApi.connections(selectedCompany.companyId),
          adCenterApi.accounts(selectedCompany.companyId),
          adCenterApi.allResources(selectedCompany.companyId),
          adCenterApi.recommendationSummary(selectedCompany.companyId).catch(() => null),
        ])
      setProviders(providerData)
      setConnections(connectionData)
      setAccounts(accountData)
      setRecommendationSummary(recommendationData)
      setResourcesByAccount(
        resourceData.reduce<Record<string, AdAccountResource[]>>((grouped, resource) => {
          grouped[resource.adAccountId] = [...(grouped[resource.adAccountId] ?? []), resource]
          return grouped
        }, {}),
      )
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load Ad Center'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [selectedCompany?.companyId])

  useEffect(() => {
    const refreshRecommendations = async () => {
      if (!selectedCompany) return
      try {
        setRecommendationSummary(await adCenterApi.recommendationSummary(selectedCompany.companyId))
      } catch {
        // Recommendation summary must not block provider/account management.
      }
    }

    const handleChanged = () => void refreshRecommendations()
    window.addEventListener('ad-center:recommendations-changed', handleChanged)
    return () => window.removeEventListener('ad-center:recommendations-changed', handleChanged)
  }, [selectedCompany?.companyId])

  useEffect(() => {
    const url = new URL(window.location.href)
    const metaResult = url.searchParams.get('metaAds')
    const googleResult = url.searchParams.get('googleAds')

    if (!metaResult && !googleResult) return

    if (metaResult) {
      if (metaResult === 'connected') {
        setNotice(
          'Meta Ads connected. Select which discovered ad accounts this workspace may manage.',
        )
      } else if (metaResult === 'cancelled') {
        setNotice('Meta Ads connection was cancelled.')
      } else {
        const message =
          'Unable to connect Meta Ads. Check the Meta app configuration and permissions.'
        setError(message)
        showGlobalSnackbar(message, { variant: 'warning', title: 'Action needed' })
      }
      url.searchParams.delete('metaAds')
    }

    if (googleResult) {
      if (googleResult === 'connected') {
        setNotice(
          'Google Ads connected. Select which discovered advertiser accounts this workspace may manage.',
        )
      } else if (googleResult === 'cancelled') {
        setNotice('Google Ads connection was cancelled.')
      } else {
        const message =
          'Unable to connect Google Ads. Check the Google OAuth client and Google Ads API access for the Cloud project.'
        setError(message)
        showGlobalSnackbar(message, { variant: 'warning', title: 'Action needed' })
      }
      url.searchParams.delete('googleAds')
    }

    window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`)
    void load()
  }, [])

  const connect = async (provider: AdProviderInfo) => {
    if (!selectedCompany) return

    setConnectingProvider(provider.provider)
    setError('')
    setNotice('')

    try {
      const authorization =
        provider.provider === 'META_ADS'
          ? await adCenterApi.authorizeMeta(selectedCompany.companyId)
          : await adCenterApi.authorizeGoogle(selectedCompany.companyId)

      window.location.assign(authorization.authorizationUrl)
    } catch (connectError) {
      setError(apiErrorMessage(connectError, `Unable to start ${provider.displayName} connection`))
      setConnectingProvider(null)
    }
  }

  const disconnect = async (connection: AdProviderConnection) => {
    if (!selectedCompany || connection.status !== 'ACTIVE') return

    setWorkingConnectionId(connection.id)
    setError('')
    setNotice('')
    try {
      const updated = await adCenterApi.disconnectConnection(
        selectedCompany.companyId,
        connection.id,
      )
      setConnections((current) => current.map((item) => (item.id === updated.id ? updated : item)))
      setAccounts((current) =>
        current.map((account) =>
          account.connectionId === updated.id
            ? { ...account, accessible: false, managed: false, primaryAccount: false }
            : account,
        ),
      )
      setNotice(`${connection.displayName || 'Advertising provider'} disconnected.`)
      window.dispatchEvent(new Event('ad-center:recommendations-changed'))
    } catch (disconnectError) {
      setError(apiErrorMessage(disconnectError, 'Unable to disconnect advertising provider'))
    } finally {
      setWorkingConnectionId(null)
    }
  }

  const syncResources = async (account: AdAccount) => {
    if (!selectedCompany || account.provider !== 'META_ADS' || !account.managed) return

    setSyncingResourceAccountId(account.id)
    setError('')
    try {
      const result = await adCenterApi.syncMetaResources(selectedCompany.companyId, account.id)
      setResourcesByAccount((current) => ({
        ...current,
        [account.id]: result.resources,
      }))
      setNotice(
        `Synced ${result.pages} Facebook page(s), ${result.instagramAccounts} Instagram account(s), and ${result.pixels} pixel(s) for ${account.name}.`,
      )
      window.dispatchEvent(new Event('ad-center:recommendations-changed'))
    } catch (syncError) {
      setError(apiErrorMessage(syncError, 'Unable to sync Meta Ads account resources'))
    } finally {
      setSyncingResourceAccountId(null)
    }
  }

  const syncGoogleAccount = async (account: AdAccount) => {
    if (
      !selectedCompany ||
      account.provider !== 'GOOGLE_ADS' ||
      !account.managed ||
      !account.accessible
    ) {
      return
    }

    setSyncingResourceAccountId(account.id)
    setError('')
    setNotice('')
    try {
      const result = await adCenterApi.syncGoogleAll(selectedCompany.companyId, account.id)
      const failed = result.failedOperations ?? []
      setNotice(
        failed.length === 0
          ? `Google Ads synchronized: ${result.campaigns} campaign(s), ${result.adGroups} ad group(s), ${result.keywords} keyword(s), ${result.ads} ad(s), ${result.conversions} conversion action(s).`
          : `Google Ads core data synchronized with ${failed.length} unavailable operation(s): ${failed.join(', ')}.`,
      )
      window.dispatchEvent(new Event('ad-center:recommendations-changed'))
    } catch (syncError) {
      setError(apiErrorMessage(syncError, 'Unable to synchronize Google Ads account'))
    } finally {
      setSyncingResourceAccountId(null)
    }
  }

  const setManaged = async (account: AdAccount, managed: boolean) => {
    if (!selectedCompany) return

    setWorkingAccountId(account.id)
    setError('')
    setNotice('')
    let updated: AdAccount | null = null

    try {
      updated = await adCenterApi.updateAccount(selectedCompany.companyId, account.id, managed)
      setAccounts((current) => current.map((item) => (item.id === updated?.id ? updated : item)))

      if (managed && updated.provider === 'META_ADS') {
        const result = await adCenterApi.syncMetaResources(selectedCompany.companyId, updated.id)
        setResourcesByAccount((current) => ({
          ...current,
          [updated!.id]: result.resources,
        }))
        setNotice(
          `${updated.name} is managed. Found ${result.pages} Facebook page(s), ${result.instagramAccounts} Instagram account(s), and ${result.pixels} pixel(s).`,
        )
      } else if (managed && updated.provider === 'GOOGLE_ADS') {
        const result = await adCenterApi.syncGoogleAll(selectedCompany.companyId, updated.id)
        const failed = result.failedOperations ?? []
        setNotice(
          failed.length === 0
            ? `${updated.name} is managed and Google Ads data is synchronized.`
            : `${updated.name} is managed. Initial Google sync completed with ${failed.length} unavailable operation(s): ${failed.join(', ')}.`,
        )
      } else if (managed) {
        setNotice(`${updated.name} is now managed by Ad Center.`)
      } else {
        setNotice(`${updated.name} was removed from active Ad Center management.`)
      }
      window.dispatchEvent(new Event('ad-center:recommendations-changed'))
    } catch (updateError) {
      setError(
        apiErrorMessage(
          updateError,
          updated
            ? 'Ad account selection was saved, but provider resources could not be synchronized'
            : 'Unable to update ad account selection',
        ),
      )
    } finally {
      setWorkingAccountId(null)
    }
  }

  if (!selectedCompany) {
    return (
      <section className="px-4 py-6 sm:px-6 lg:px-8">
        <div className="panel mx-auto max-w-6xl p-8 text-center text-sm text-slate-500">
          Select a workspace to open Ad Center.
        </div>
      </section>
    )
  }

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-6xl">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Advertising</p>
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
              Ad Center
            </h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
              Connect advertising providers, choose the accounts this workspace may manage, and run
              campaigns from one place.
            </p>
          </div>
          <button
            type="button"
            className="secondary-button inline-flex items-center gap-2"
            onClick={() => void load()}
            disabled={loading}
          >
            <FiRefreshCw aria-hidden="true" />
            Refresh
          </button>
        </header>
        <AdFeedbackAlert error={error} notice={notice} />

        {recommendationSummary && recommendationSummary.managedAccountCount > 0 && (
          <div
            className={`panel mt-7 overflow-hidden border ${
              recommendationSummary.attentionCount > 0 ? 'border-amber-200' : 'border-emerald-200'
            }`}
          >
            <div
              className={`flex flex-wrap items-start justify-between gap-4 px-5 py-5 ${
                recommendationSummary.attentionCount > 0 ? 'bg-amber-50/70' : 'bg-emerald-50/70'
              }`}
            >
              <div className="flex items-start gap-3">
                {recommendationSummary.attentionCount > 0 ? (
                  <FiAlertTriangle className="mt-0.5 shrink-0 text-amber-600" aria-hidden="true" />
                ) : (
                  <FiCheckCircle className="mt-0.5 shrink-0 text-emerald-600" aria-hidden="true" />
                )}
                <div>
                  <h2 className="font-bold text-slate-950">
                    {recommendationSummary.attentionCount > 0
                      ? 'Needs attention'
                      : 'No urgent Ad Center issues'}
                  </h2>
                  <p className="mt-1 text-sm leading-6 text-slate-600">
                    {recommendationSummary.attentionCount > 0
                      ? `${recommendationSummary.criticalCount} critical and ${recommendationSummary.warningCount} warning item(s) across managed ad accounts.`
                      : `${recommendationSummary.opportunityCount} optimization opportunity item(s) are currently available.`}
                  </p>
                </div>
              </div>

              <Link
                to="/ad-center/recommendations"
                className="secondary-button inline-flex items-center gap-2"
              >
                <FiZap aria-hidden="true" />
                View recommendations
              </Link>
            </div>

            {recommendationSummary.topItems.length > 0 && (
              <div className="divide-y divide-slate-100">
                {recommendationSummary.topItems.slice(0, 4).map((entry) => (
                  <div
                    key={`${entry.adAccountId}-${entry.item.key}`}
                    className="flex flex-wrap items-center justify-between gap-3 px-5 py-4"
                  >
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <span
                          className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${
                            entry.item.severity === 'CRITICAL'
                              ? 'bg-red-50 text-red-700'
                              : entry.item.severity === 'WARNING'
                                ? 'bg-amber-50 text-amber-700'
                                : entry.item.severity === 'OPPORTUNITY'
                                  ? 'bg-indigo-50 text-indigo-700'
                                  : 'bg-slate-100 text-slate-600'
                          }`}
                        >
                          {entry.item.severity}
                        </span>
                        <span className="text-xs font-semibold text-slate-400">
                          {entry.accountName}
                        </span>
                      </div>
                      <p className="mt-2 font-semibold text-slate-950">{entry.item.title}</p>
                    </div>

                    <Link
                      to={`/ad-center/recommendations?accountId=${encodeURIComponent(
                        entry.adAccountId,
                      )}`}
                      className="text-sm font-semibold text-indigo-600 hover:text-indigo-700"
                    >
                      Review →
                    </Link>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        <div className="mt-7 grid gap-4 md:grid-cols-2">
          {providers.map((provider) => {
            const providerAccounts = accounts.filter(
              (account) => account.provider === provider.provider,
            )
            const accessibleAccounts = providerAccounts.filter((account) => account.accessible)
            const managedCount = accessibleAccounts.filter((account) => account.managed).length
            const isMeta = provider.provider === 'META_ADS'
            const connectable = provider.configured && can(PERMISSIONS.ADS_CONNECT)

            return (
              <article key={provider.provider} className="panel p-5">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                      Provider
                    </p>
                    <h2 className="mt-1 text-lg font-bold text-slate-950">
                      {provider.displayName}
                    </h2>
                  </div>
                  <span
                    className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
                      provider.configured
                        ? 'bg-emerald-50 text-emerald-700'
                        : 'bg-slate-100 text-slate-600'
                    }`}
                  >
                    {provider.configured ? 'Available' : 'Not configured'}
                  </span>
                </div>

                <p className="mt-3 text-sm leading-6 text-slate-500">
                  {providerCopy[provider.provider]}
                </p>

                <div className="mt-5 flex items-end justify-between gap-4 border-t border-slate-100 pt-4">
                  <div>
                    <p className="text-xs font-semibold text-slate-400">Accounts</p>
                    <p className="mt-1 text-xl font-bold text-slate-950">
                      {managedCount}
                      <span className="ml-1 text-xs font-medium text-slate-400">
                        managed / {accessibleAccounts.length} accessible
                      </span>
                    </p>
                  </div>
                  <button
                    type="button"
                    className="secondary-button inline-flex items-center gap-2"
                    disabled={!connectable || connectingProvider !== null}
                    title={
                      !provider.configured
                        ? `Configure the ${provider.displayName} environment variables first`
                        : undefined
                    }
                    onClick={() => void connect(provider)}
                  >
                    <FiExternalLink aria-hidden="true" />
                    {connectingProvider === provider.provider
                      ? `Opening ${isMeta ? 'Meta' : 'Google'}…`
                      : providerAccounts.length > 0
                        ? 'Reconnect'
                        : 'Connect'}
                  </button>
                </div>
              </article>
            )
          })}
        </div>

        <div className="panel mt-6 overflow-hidden">
          <div className="border-b border-slate-100 px-5 py-4">
            <h2 className="text-base font-bold text-slate-950">Provider connections</h2>
            <p className="mt-1 text-xs text-slate-500">
              OAuth identities currently linked to this workspace.
            </p>
          </div>
          {connections.length === 0 ? (
            <div className="p-6 text-sm text-slate-500">
              No advertising provider is connected yet.
            </div>
          ) : (
            <div className="divide-y divide-slate-100">
              {connections.map((connection) => (
                <div
                  key={connection.id}
                  className="flex flex-wrap items-center justify-between gap-4 px-5 py-4"
                >
                  <div>
                    <p className="font-semibold text-slate-950">
                      {connection.displayName ||
                        (connection.provider === 'META_ADS' ? 'Meta Ads' : 'Google Ads')}
                    </p>
                    <p className="mt-1 text-xs text-slate-500">
                      {connection.provider === 'META_ADS' ? 'Meta Ads' : 'Google Ads'} ·{' '}
                      {connection.status}
                    </p>
                  </div>
                  {connection.status === 'ACTIVE' && can(PERMISSIONS.ADS_DISCONNECT) && (
                    <button
                      type="button"
                      className="secondary-button inline-flex items-center gap-2"
                      disabled={workingConnectionId !== null}
                      onClick={() => void disconnect(connection)}
                    >
                      <FiX aria-hidden="true" />
                      {workingConnectionId === connection.id ? 'Disconnecting…' : 'Disconnect'}
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="panel mt-6 overflow-hidden">
          <div className="border-b border-slate-100 px-5 py-4">
            <h2 className="text-base font-bold text-slate-950">Ad accounts</h2>
            <p className="mt-1 text-xs text-slate-500">
              Accounts discovered from connected providers. Only opted-in accounts are used for
              campaign management.
            </p>
          </div>

          {accounts.length === 0 ? (
            <div className="p-8 text-center text-sm text-slate-500">
              No advertising accounts have been discovered yet. Connect an advertising provider to
              import the accounts available to your authorized user.
            </div>
          ) : (
            <div className="divide-y divide-slate-100">
              {accounts.map((account) => {
                const resources = resourcesByAccount[account.id] ?? []
                const accessibleResources = resources.filter((resource) => resource.accessible)
                const pages = accessibleResources.filter(
                  (resource) => resource.resourceType === 'FACEBOOK_PAGE',
                ).length
                const instagramAccounts = accessibleResources.filter(
                  (resource) => resource.resourceType === 'INSTAGRAM_ACCOUNT',
                ).length
                const pixels = accessibleResources.filter(
                  (resource) => resource.resourceType === 'META_PIXEL',
                ).length

                return (
                  <div
                    key={account.id}
                    className="flex flex-wrap items-center justify-between gap-4 px-5 py-4"
                  >
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <p className="font-semibold text-slate-950">{account.name}</p>
                        {account.primaryAccount && (
                          <span className="rounded-full bg-indigo-50 px-2 py-0.5 text-[11px] font-semibold text-indigo-700">
                            Primary
                          </span>
                        )}
                      </div>
                      <p className="mt-1 text-xs text-slate-500">
                        {account.provider === 'META_ADS' ? 'Meta Ads' : 'Google Ads'} ·{' '}
                        {account.externalAccountId}
                      </p>
                      <p className="mt-1 text-xs text-slate-400">
                        {[account.currency, account.timezone].filter(Boolean).join(' · ') ||
                          'Provider settings'}
                      </p>
                      {resources.length > 0 && (
                        <div className="mt-2 flex flex-wrap gap-2">
                          <span className="rounded-full bg-slate-50 px-2 py-1 text-[11px] font-semibold text-slate-600">
                            {pages} Page{pages === 1 ? '' : 's'}
                          </span>
                          <span className="rounded-full bg-slate-50 px-2 py-1 text-[11px] font-semibold text-slate-600">
                            {instagramAccounts} Instagram
                          </span>
                          <span className="rounded-full bg-slate-50 px-2 py-1 text-[11px] font-semibold text-slate-600">
                            {pixels} Pixel{pixels === 1 ? '' : 's'}
                          </span>
                        </div>
                      )}
                    </div>

                    <div className="flex flex-wrap items-center justify-end gap-2">
                      <span
                        className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
                          account.accessible
                            ? 'bg-slate-100 text-slate-600'
                            : 'bg-amber-50 text-amber-700'
                        }`}
                      >
                        {account.accessible ? account.status : 'ACCESS REMOVED'}
                      </span>
                      {account.provider === 'META_ADS' &&
                        account.managed &&
                        account.accessible &&
                        can(PERMISSIONS.ADS_MODIFY) && (
                          <button
                            type="button"
                            className="secondary-button inline-flex items-center gap-2"
                            disabled={syncingResourceAccountId !== null}
                            onClick={() => void syncResources(account)}
                          >
                            <FiRefreshCw aria-hidden="true" />
                            {syncingResourceAccountId === account.id
                              ? 'Syncing…'
                              : 'Sync resources'}
                          </button>
                        )}
                      {account.provider === 'GOOGLE_ADS' &&
                        account.managed &&
                        account.accessible &&
                        can(PERMISSIONS.ADS_MODIFY) && (
                          <button
                            type="button"
                            className="secondary-button inline-flex items-center gap-2"
                            disabled={syncingResourceAccountId !== null}
                            onClick={() => void syncGoogleAccount(account)}
                          >
                            <FiRefreshCw aria-hidden="true" />
                            {syncingResourceAccountId === account.id ? 'Syncing…' : 'Sync Google'}
                          </button>
                        )}
                      {can(PERMISSIONS.ADS_MODIFY) && (
                        <button
                          type="button"
                          className={
                            account.managed
                              ? 'secondary-button inline-flex items-center gap-2'
                              : 'primary-button inline-flex items-center gap-2'
                          }
                          disabled={
                            workingAccountId !== null ||
                            account.status === 'CLOSED' ||
                            (!account.accessible && !account.managed)
                          }
                          onClick={() => void setManaged(account, !account.managed)}
                        >
                          {account.managed ? (
                            <>
                              <FiX aria-hidden="true" />
                              Stop managing
                            </>
                          ) : (
                            <>
                              <FiCheck aria-hidden="true" />
                              Manage account
                            </>
                          )}
                        </button>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </div>
    </section>
  )
}
