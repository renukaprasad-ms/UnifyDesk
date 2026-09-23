import { useEffect, useState } from 'react'
import { FiCheck, FiExternalLink, FiRefreshCw, FiX } from 'react-icons/fi'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type {
  AdAccount,
  AdProviderConnection,
  AdProviderInfo,
} from '../../features/ads/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'

const providerCopy: Record<AdProviderInfo['provider'], string> = {
  GOOGLE_ADS:
    'Manage Google Ads campaigns, assets, conversions, reporting, and supported billing operations.',
  META_ADS:
    'Manage Meta campaigns, ad sets, ads, audiences, pixels, creatives, and reporting.',
}

export default function AdCenterOverview() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const { can } = useIam()
  const [providers, setProviders] = useState<AdProviderInfo[]>([])
  const [connections, setConnections] = useState<AdProviderConnection[]>([])
  const [accounts, setAccounts] = useState<AdAccount[]>([])
  const [loading, setLoading] = useState(true)
  const [connectingProvider, setConnectingProvider] = useState<string | null>(null)
  const [workingAccountId, setWorkingAccountId] = useState<string | null>(null)
  const [workingConnectionId, setWorkingConnectionId] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = async () => {
    if (!selectedCompany) return
    setLoading(true)
    try {
      const [providerData, connectionData, accountData] = await Promise.all([
        adCenterApi.providers(selectedCompany.companyId),
        adCenterApi.connections(selectedCompany.companyId),
        adCenterApi.accounts(selectedCompany.companyId),
      ])
      setProviders(providerData)
      setConnections(connectionData)
      setAccounts(accountData)
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
    const url = new URL(window.location.href)
    const result = url.searchParams.get('metaAds')
    if (!result) return

    if (result === 'connected') {
      setNotice('Meta Ads connected. Select which discovered ad accounts this workspace may manage.')
    } else if (result === 'cancelled') {
      setNotice('Meta Ads connection was cancelled.')
    } else {
      setError('Unable to connect Meta Ads. Check the Meta app configuration and permissions.')
    }

    url.searchParams.delete('metaAds')
    window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`)
    void load()
  }, [])

  const connect = async (provider: AdProviderInfo) => {
    if (!selectedCompany || provider.provider !== 'META_ADS') return

    setConnectingProvider(provider.provider)
    setError('')
    setNotice('')
    try {
      const authorization = await adCenterApi.authorizeMeta(selectedCompany.companyId)
      window.location.assign(authorization.authorizationUrl)
    } catch (connectError) {
      setError(apiErrorMessage(connectError, 'Unable to start Meta Ads connection'))
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
      setConnections((current) =>
        current.map((item) => (item.id === updated.id ? updated : item)),
      )
      setAccounts((current) =>
        current.map((account) =>
          account.connectionId === updated.id
            ? { ...account, accessible: false, managed: false, primaryAccount: false }
            : account,
        ),
      )
      setNotice(`${connection.displayName || 'Advertising provider'} disconnected.`)
    } catch (disconnectError) {
      setError(apiErrorMessage(disconnectError, 'Unable to disconnect advertising provider'))
    } finally {
      setWorkingConnectionId(null)
    }
  }

  const setManaged = async (account: AdAccount, managed: boolean) => {
    if (!selectedCompany) return

    setWorkingAccountId(account.id)
    setError('')
    setNotice('')
    try {
      const updated = await adCenterApi.updateAccount(
        selectedCompany.companyId,
        account.id,
        managed,
      )
      setAccounts((current) =>
        current.map((item) => (item.id === updated.id ? updated : item)),
      )
      setNotice(
        managed
          ? `${updated.name} is now managed in Ad Center.`
          : `${updated.name} was removed from active Ad Center management.`,
      )
    } catch (updateError) {
      setError(apiErrorMessage(updateError, 'Unable to update ad account selection'))
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
              Connect advertising providers, choose the accounts this workspace may manage, and
              run campaigns from one place.
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

        <div className="mt-7 grid gap-4 md:grid-cols-2">
          {providers.map((provider) => {
            const providerAccounts = accounts.filter(
              (account) => account.provider === provider.provider,
            )
            const accessibleAccounts = providerAccounts.filter((account) => account.accessible)
            const managedCount = accessibleAccounts.filter((account) => account.managed).length
            const isMeta = provider.provider === 'META_ADS'
            const connectable =
              isMeta && provider.configured && can(PERMISSIONS.ADS_CONNECT)

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
                      !isMeta
                        ? 'Google Ads connection will be implemented after Meta Ads'
                        : !provider.configured
                          ? 'Configure the Meta Ads environment variables first'
                          : undefined
                    }
                    onClick={() => void connect(provider)}
                  >
                    <FiExternalLink aria-hidden="true" />
                    {connectingProvider === provider.provider
                      ? 'Opening Meta…'
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
                      {connection.displayName || (connection.provider === 'META_ADS' ? 'Meta Ads' : 'Google Ads')}
                    </p>
                    <p className="mt-1 text-xs text-slate-500">
                      {connection.provider === 'META_ADS' ? 'Meta Ads' : 'Google Ads'} · {connection.status}
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
              No advertising accounts have been discovered yet. Connect Meta Ads to import the
              accounts available to your Meta user.
            </div>
          ) : (
            <div className="divide-y divide-slate-100">
              {accounts.map((account) => (
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
                  </div>

                  <div className="flex items-center gap-3">
                    <span
                      className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
                        account.accessible
                          ? 'bg-slate-100 text-slate-600'
                          : 'bg-amber-50 text-amber-700'
                      }`}
                    >
                      {account.accessible ? account.status : 'ACCESS REMOVED'}
                    </span>
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
              ))}
            </div>
          )}
        </div>
      </div>
    </section>
  )
}
