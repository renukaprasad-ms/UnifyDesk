import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useEffect, useMemo, useState } from 'react'
import AdFeedbackAlert from '../../components/ads/AdFeedbackAlert'
import {
  FiAlertTriangle,
  FiCreditCard,
  FiDollarSign,
  FiExternalLink,
  FiRefreshCw,
  FiShield,
} from 'react-icons/fi'
import MetaSpendCapModal from '../../components/ads/MetaSpendCapModal'
import { adCenterApi } from '../../features/ads/adCenterApi'
import type { AdAccount, AdAccountBilling } from '../../features/ads/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'
import { PageSkeleton } from '../../components/ui/Skeleton'

const fractionDigits = (currency?: string | null) => {
  if (!currency) return 2
  try {
    return (
      new Intl.NumberFormat(undefined, {
        style: 'currency',
        currency,
      }).resolvedOptions().maximumFractionDigits ?? 2
    )
  } catch {
    return 2
  }
}

const formatMinor = (value?: number | null, currency?: string | null) => {
  if (value == null) return '—'
  const major = value / 10 ** fractionDigits(currency)
  if (!currency) {
    return new Intl.NumberFormat(undefined, {
      maximumFractionDigits: fractionDigits(currency),
    }).format(major)
  }

  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency,
      maximumFractionDigits: fractionDigits(currency),
    }).format(major)
  } catch {
    return `${major.toLocaleString()} ${currency}`
  }
}

const paymentModel = (prepay?: boolean | null) => {
  if (prepay === true) return 'Prepaid'
  if (prepay === false) return 'Automatic / invoice / other'
  return 'Not reported'
}

const taxStatus = (value?: number | null, provider?: AdAccount['provider']) =>
  value == null
    ? 'Not reported'
    : provider === 'META_ADS'
      ? `Meta status code ${value}`
      : `Provider status code ${value}`

const billingUrl = (account: AdAccount) =>
  account.provider === 'GOOGLE_ADS'
    ? 'https://ads.google.com/aw/billing/settings'
    : `https://adsmanager.facebook.com/adsmanager/manage/billing?act=${encodeURIComponent(account.externalAccountId)}`

const providerName = (account?: AdAccount | null) =>
  account?.provider === 'GOOGLE_ADS' ? 'Google Ads' : 'Meta Ads'

const emptyBilling = (account: AdAccount): AdAccountBilling => ({
  synced: false,
  companyId: account.companyId,
  adAccountId: account.id,
  provider: account.provider,
  currency: account.currency,
  spendCapEnabled: false,
})

export default function AdBilling() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const { can } = useIam()

  const [accounts, setAccounts] = useState<AdAccount[]>([])
  const [selectedAccountId, setSelectedAccountId] = useState('')
  const [billing, setBilling] = useState<AdAccountBilling | null>(null)
  const [loading, setLoading] = useState(true)
  const [syncing, setSyncing] = useState(false)
  const [savingCap, setSavingCap] = useState(false)
  const [capModalOpen, setCapModalOpen] = useState(false)
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

  const loadBilling = async (account: AdAccount) => {
    if (!selectedCompany) return
    const data = await adCenterApi.billing(selectedCompany.companyId, account.id)
    setBilling(data)
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
        await loadBilling(preferred)
      } else {
        setBilling(null)
      }
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load advertising billing'))
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
      await loadBilling(account)
    } catch (loadError) {
      setBilling(emptyBilling(account))
      setError(apiErrorMessage(loadError, 'Unable to load advertising billing'))
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
      const updated =
        selectedAccount.provider === 'GOOGLE_ADS'
          ? await adCenterApi.syncGoogleBilling(selectedCompany.companyId, selectedAccount.id)
          : await adCenterApi.syncMetaBilling(selectedCompany.companyId, selectedAccount.id)
      setBilling(updated)
      setNotice(`${providerName(selectedAccount)} billing state synchronized.`)
      try {
        await adCenterApi.reconcileRecommendations(selectedCompany.companyId, selectedAccount.id)
      } catch {
        // Billing succeeded; lifecycle reconciliation is best effort here.
      }
      window.dispatchEvent(new Event('ad-center:recommendations-changed'))
    } catch (syncError) {
      setError(apiErrorMessage(syncError, 'Unable to synchronize provider billing'))
    } finally {
      setSyncing(false)
    }
  }

  const updateSpendCap = async (spendCapMinor: number) => {
    if (!selectedCompany || !selectedAccount) return
    setSavingCap(true)
    setError('')
    setNotice('')
    try {
      const updated = await adCenterApi.updateMetaSpendCap(
        selectedCompany.companyId,
        selectedAccount.id,
        spendCapMinor,
      )
      setBilling(updated)
      setCapModalOpen(false)
      try {
        await adCenterApi.reconcileRecommendations(selectedCompany.companyId, selectedAccount.id)
      } catch {
        // Meta accepted the cap change; lifecycle reconciliation is best effort.
      }
      window.dispatchEvent(new Event('ad-center:recommendations-changed'))
      setNotice(
        spendCapMinor === 0
          ? 'Meta account spending limit removed.'
          : 'Meta account spending limit updated.',
      )
    } catch (updateError) {
      setError(apiErrorMessage(updateError, 'Unable to update Meta account spending limit'))
    } finally {
      setSavingCap(false)
    }
  }

  if (!selectedCompany) return null

  const currency = billing?.currency ?? selectedAccount?.currency
  const capProgress =
    billing?.spendCapEnabled &&
    billing.spendCapMinor &&
    billing.spendCapMinor > 0 &&
    billing.amountSpentMinor != null
      ? Math.min(100, (billing.amountSpentMinor / billing.spendCapMinor) * 100)
      : null

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Ad Center</p>
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
              Billing
            </h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
              Monitor provider billing setup, payment-account information, and available account
              budget limits.
            </p>
          </div>

          <div className="flex flex-wrap gap-2">
            {selectedAccount && can(PERMISSIONS.ADS_BALANCE_ADD) && (
              <a
                className="secondary-button inline-flex items-center gap-2"
                href={billingUrl(selectedAccount)}
                target="_blank"
                rel="noreferrer"
              >
                <FiExternalLink aria-hidden="true" />
                {selectedAccount.provider === 'GOOGLE_ADS'
                  ? 'Open Google Ads billing'
                  : billing?.prepayAccount
                    ? 'Add funds in Meta'
                    : 'Open Meta billing'}
              </a>
            )}

            {can(PERMISSIONS.ADS_MODIFY) && (
              <button
                type="button"
                className="primary-button inline-flex items-center gap-2"
                disabled={!selectedAccount || syncing}
                onClick={() => void sync()}
              >
                <FiRefreshCw aria-hidden="true" />
                {syncing ? 'Syncing…' : 'Sync billing'}
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

        {managedAccounts.length === 0 ? (
          <div className="panel mt-6 p-10 text-center text-sm text-slate-500">
            Connect and manage an ad account before using billing.
          </div>
        ) : loading ? (
          <div className="mt-6"><PageSkeleton /></div>
        ) : !billing?.synced ? (
          <div className="panel mt-6 p-10 text-center">
            <FiCreditCard className="mx-auto text-3xl text-slate-300" aria-hidden="true" />
            <h2 className="mt-3 text-base font-bold text-slate-950">
              Billing has not been synchronized
            </h2>
            <p className="mx-auto mt-2 max-w-lg text-sm leading-6 text-slate-500">
              Sync this account to read the billing and payment information exposed by the selected
              advertising provider.
            </p>
            {can(PERMISSIONS.ADS_MODIFY) && (
              <button
                type="button"
                className="primary-button mt-5 inline-flex items-center gap-2"
                onClick={() => void sync()}
              >
                <FiRefreshCw aria-hidden="true" />
                Sync {providerName(selectedAccount)} billing
              </button>
            )}
          </div>
        ) : (
          <>
            <div className="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <article className="panel p-5">
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                  {selectedAccount?.provider === 'GOOGLE_ADS' ? 'Provider balance' : 'Meta balance'}
                </p>
                <p className="mt-2 text-2xl font-bold tracking-tight text-slate-950">
                  {formatMinor(billing.balanceMinor, currency)}
                </p>
                <p className="mt-2 text-xs leading-5 text-slate-500">
                  Provider-reported balance when the selected advertising API exposes one.
                </p>
              </article>

              <article className="panel p-5">
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                  Amount spent
                </p>
                <p className="mt-2 text-2xl font-bold tracking-tight text-slate-950">
                  {formatMinor(billing.amountSpentMinor, currency)}
                </p>
                <p className="mt-2 text-xs leading-5 text-slate-500">
                  Provider-reported account-level amount spent when available.
                </p>
              </article>

              <article className="panel p-5">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                      Account spend cap
                    </p>
                    <p className="mt-2 text-2xl font-bold tracking-tight text-slate-950">
                      {billing.spendCapEnabled
                        ? formatMinor(billing.spendCapMinor, currency)
                        : 'No limit'}
                    </p>
                  </div>
                  {selectedAccount?.provider === 'META_ADS' && can(PERMISSIONS.ADS_MODIFY) && (
                    <button
                      type="button"
                      className="secondary-button px-3 py-2 text-xs"
                      onClick={() => setCapModalOpen(true)}
                    >
                      Edit
                    </button>
                  )}
                </div>
                <p className="mt-2 text-xs leading-5 text-slate-500">
                  {selectedAccount?.provider === 'GOOGLE_ADS'
                    ? 'Google account-budget spending limit when exposed by the billing setup.'
                    : 'Account-level limit across Meta campaigns.'}
                </p>
              </article>

              <article className="panel p-5">
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                  Remaining to cap
                </p>
                <p className="mt-2 text-2xl font-bold tracking-tight text-slate-950">
                  {billing.spendCapEnabled
                    ? formatMinor(billing.remainingSpendCapMinor, currency)
                    : 'Unlimited'}
                </p>
                {capProgress != null ? (
                  <div className="mt-4">
                    <div className="h-2 overflow-hidden rounded-full bg-slate-100">
                      <div
                        className="h-full rounded-full bg-indigo-500"
                        style={{ width: `${capProgress}%` }}
                      />
                    </div>
                    <p className="mt-2 text-xs text-slate-500">
                      {capProgress.toFixed(1)}% of reported cap consumed
                    </p>
                  </div>
                ) : (
                  <p className="mt-2 text-xs leading-5 text-slate-500">
                    No account spend cap is currently reported.
                  </p>
                )}
              </article>
            </div>

            <div className="mt-6 grid gap-6 lg:grid-cols-2">
              <div className="panel p-5 sm:p-6">
                <div className="flex items-center gap-2">
                  <FiCreditCard className="text-indigo-500" aria-hidden="true" />
                  <h2 className="text-base font-bold text-slate-950">Payment setup</h2>
                </div>

                <dl className="mt-5 divide-y divide-slate-100 text-sm">
                  <div className="flex items-start justify-between gap-4 py-3">
                    <dt className="text-slate-500">Payment model</dt>
                    <dd className="text-right font-semibold text-slate-900">
                      {selectedAccount?.provider === 'GOOGLE_ADS'
                        ? billing.fundingSourceId
                          ? 'Google payments account'
                          : 'Not reported'
                        : paymentModel(billing.prepayAccount)}
                    </dd>
                  </div>
                  <div className="flex items-start justify-between gap-4 py-3">
                    <dt className="text-slate-500">Funding source</dt>
                    <dd className="max-w-xs text-right font-semibold text-slate-900">
                      {billing.fundingSourceDisplay ||
                        (billing.fundingSourceId
                          ? selectedAccount?.provider === 'GOOGLE_ADS'
                            ? 'Configured in Google Ads'
                            : 'Configured in Meta'
                          : 'Not reported')}
                    </dd>
                  </div>
                  <div className="flex items-start justify-between gap-4 py-3">
                    <dt className="text-slate-500">Funding source type</dt>
                    <dd className="text-right font-semibold text-slate-900">
                      {billing.fundingSourceType != null
                        ? selectedAccount?.provider === 'GOOGLE_ADS'
                          ? `Provider type ${billing.fundingSourceType}`
                          : `Meta type ${billing.fundingSourceType}`
                        : 'Not reported'}
                    </dd>
                  </div>
                  <div className="flex items-start justify-between gap-4 py-3">
                    <dt className="text-slate-500">Account currency</dt>
                    <dd className="text-right font-semibold text-slate-900">
                      {currency || 'Not reported'}
                    </dd>
                  </div>
                </dl>

                {can(PERMISSIONS.ADS_BALANCE_ADD) && selectedAccount && (
                  <a
                    className="secondary-button mt-5 inline-flex items-center gap-2"
                    href={billingUrl(selectedAccount)}
                    target="_blank"
                    rel="noreferrer"
                  >
                    <FiExternalLink aria-hidden="true" />
                    Manage payment settings in {providerName(selectedAccount)}
                  </a>
                )}
              </div>

              <div className="panel p-5 sm:p-6">
                <div className="flex items-center gap-2">
                  <FiDollarSign className="text-indigo-500" aria-hidden="true" />
                  <h2 className="text-base font-bold text-slate-950">Account limits</h2>
                </div>

                <dl className="mt-5 divide-y divide-slate-100 text-sm">
                  <div className="flex items-start justify-between gap-4 py-3">
                    <dt className="text-slate-500">Minimum daily budget</dt>
                    <dd className="text-right font-semibold text-slate-900">
                      {formatMinor(billing.minDailyBudgetMinor, currency)}
                    </dd>
                  </div>
                  <div className="flex items-start justify-between gap-4 py-3">
                    <dt className="text-slate-500">Minimum campaign-group spend cap</dt>
                    <dd className="text-right font-semibold text-slate-900">
                      {formatMinor(billing.minCampaignGroupSpendCapMinor, currency)}
                    </dd>
                  </div>
                  <div className="flex items-start justify-between gap-4 py-3">
                    <dt className="text-slate-500">Ad account status</dt>
                    <dd className="text-right font-semibold text-slate-900">
                      {selectedAccount?.status ?? 'UNKNOWN'}
                    </dd>
                  </div>
                  <div className="flex items-start justify-between gap-4 py-3">
                    <dt className="text-slate-500">Disable reason</dt>
                    <dd className="text-right font-semibold text-slate-900">
                      {billing.disableReason != null
                        ? selectedAccount?.provider === 'GOOGLE_ADS'
                          ? `Provider reason code ${billing.disableReason}`
                          : `Meta reason code ${billing.disableReason}`
                        : 'None reported'}
                    </dd>
                  </div>
                </dl>
              </div>
            </div>

            <div className="mt-6 grid gap-6 lg:grid-cols-2">
              <div className="panel p-5 sm:p-6">
                <div className="flex items-center gap-2">
                  <FiShield className="text-indigo-500" aria-hidden="true" />
                  <h2 className="text-base font-bold text-slate-950">Tax & account information</h2>
                </div>

                <dl className="mt-5 divide-y divide-slate-100 text-sm">
                  <div className="flex items-start justify-between gap-4 py-3">
                    <dt className="text-slate-500">Business country</dt>
                    <dd className="text-right font-semibold text-slate-900">
                      {billing.businessCountryCode || 'Not reported'}
                    </dd>
                  </div>
                  <div className="flex items-start justify-between gap-4 py-3">
                    <dt className="text-slate-500">Tax ID required</dt>
                    <dd className="text-right font-semibold text-slate-900">
                      {billing.taxIdRequired == null
                        ? 'Not reported'
                        : billing.taxIdRequired
                          ? 'Yes'
                          : 'No'}
                    </dd>
                  </div>
                  <div className="flex items-start justify-between gap-4 py-3">
                    <dt className="text-slate-500">Tax ID type</dt>
                    <dd className="text-right font-semibold text-slate-900">
                      {billing.taxIdType || 'Not reported'}
                    </dd>
                  </div>
                  <div className="flex items-start justify-between gap-4 py-3">
                    <dt className="text-slate-500">Tax status</dt>
                    <dd className="text-right font-semibold text-slate-900">
                      {taxStatus(billing.taxIdStatus, selectedAccount?.provider)}
                    </dd>
                  </div>
                </dl>
              </div>

              <div className="panel p-5 sm:p-6">
                <div className="flex items-start gap-3">
                  <FiAlertTriangle className="mt-0.5 shrink-0 text-amber-600" aria-hidden="true" />
                  <div>
                    <h2 className="text-base font-bold text-slate-950">
                      Payment actions stay with the provider
                    </h2>
                    <p className="mt-2 text-sm leading-6 text-slate-500">
                      Ad Center reads the billing state exposed by the provider. Meta spend-cap
                      changes are supported where available; Google payment methods, funds,
                      invoices, and checkout remain provider-hosted operations.
                    </p>
                  </div>
                </div>

                {selectedAccount && can(PERMISSIONS.ADS_BALANCE_ADD) && (
                  <a
                    className="primary-button mt-5 inline-flex items-center gap-2"
                    href={billingUrl(selectedAccount)}
                    target="_blank"
                    rel="noreferrer"
                  >
                    <FiExternalLink aria-hidden="true" />
                    Open {providerName(selectedAccount)} billing
                  </a>
                )}

                <p className="mt-4 text-xs text-slate-400">
                  Last synchronized:{' '}
                  {billing.lastSyncAt ? new Date(billing.lastSyncAt).toLocaleString() : 'Never'}
                </p>
              </div>
            </div>
          </>
        )}
      </div>

      {selectedAccount?.provider === 'META_ADS' && (
        <MetaSpendCapModal
          open={capModalOpen}
          saving={savingCap}
          currency={currency}
          currentSpendCapMinor={billing?.spendCapMinor}
          amountSpentMinor={billing?.amountSpentMinor}
          onClose={() => setCapModalOpen(false)}
          onSave={updateSpendCap}
        />
      )}
    </section>
  )
}
