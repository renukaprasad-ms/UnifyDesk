import { useEffect, useState } from 'react'
import { FiCheck, FiCreditCard, FiRefreshCw } from 'react-icons/fi'
import { billingApi } from '../../features/billing/billingApi'
import type { CompanyPlanDetails } from '../../features/billing/types'
import { storageApi } from '../../features/storage/storageApi'
import type { StorageUsage } from '../../features/storage/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompany } from '../../store/workspace/workspaceSlice'

const entitlementLabel = (key: string, value: string) => {
  const labels: Record<string, string> = {
    MAX_WORKSPACE_USERS: `${value} workspace users`,
    BROADCAST_BATCH_LIMIT: `${value} recipients per broadcast`,
    EMAIL_TEMPLATE_LIMIT: `${value} email templates`,
    SOCIAL_TEMPLATE_LIMIT: `${value} social media templates`,
    AUTO_REPLY_ENABLED: value === 'true' ? 'Auto reply included' : 'Auto reply not included',
    USER_SIGNATURE_ENABLED:
      value === 'true' ? 'User signature included' : 'User signature not included',
    STORAGE_GB: `${value} GiB workspace storage`,
  }
  return labels[key] ?? `${key.replaceAll('_', ' ').toLowerCase()}: ${value}`
}

const storageTypeLabel: Record<string, string> = {
  MESSAGE: 'Messages',
  MESSAGE_ATTACHMENT: 'Message attachments',
  EMAIL_ATTACHMENT: 'Email attachments',
  WHATSAPP_MEDIA: 'WhatsApp media',
  SMS_ATTACHMENT: 'SMS attachments',
  TEMPLATE_MEDIA: 'Template media',
  COMPANY_MEDIA: 'Company media',
  CONTACT_DATA: 'Contacts',
  AD_CREATIVE: 'Ad creatives',
  EXPORT: 'Exports',
  OTHER: 'Other',
}

const formatBytes = (bytes: number) => {
  if (bytes < 0) return 'Unlimited'
  if (bytes === 0) return '0 B'
  const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB']
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  return `${(bytes / 1024 ** index).toFixed(index >= 3 ? 2 : 1)} ${units[index]}`
}

export default function CompanyPlan() {
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const [details, setDetails] = useState<CompanyPlanDetails | null>(null)
  const [loading, setLoading] = useState(true)
  const [storage, setStorage] = useState<StorageUsage | null>(null)
  const [error, setError] = useState('')

  const loadPlan = async () => {
    if (!selectedCompany) return
    setError('')
    setLoading(true)
    try {
      const [planDetails, storageUsage] = await Promise.all([
        billingApi.companyPlan(selectedCompany.companyId),
        storageApi.usage(selectedCompany.companyId).catch(() => null),
      ])
      setDetails(planDetails)
      setStorage(storageUsage)
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load company plan'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadPlan()
  }, [selectedCompany?.companyId])

  const plan = details?.plan
  const subscription = details?.subscription

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-5xl">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="eyebrow">Company</p>
            <h1 className="mt-2 text-2xl font-bold text-slate-950">Plan details</h1>
          </div>
          <button type="button" className="secondary-button inline-flex" onClick={loadPlan}>
            <FiRefreshCw aria-hidden="true" />
            <span className="ml-2">Refresh</span>
          </button>
        </div>
        {error && (
          <div className="mt-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}
        {loading ? (
          <div className="panel mt-5 p-6 text-sm font-semibold text-slate-500">Loading plan</div>
        ) : plan && subscription ? (
          <div className="mt-5 grid gap-5 lg:grid-cols-[1fr_320px]">
            <article className="panel p-6">
              <div className="flex items-start gap-4">
                <span className="security-icon">
                  <FiCreditCard aria-hidden="true" />
                </span>
                <div>
                  <p className="text-xs font-semibold uppercase text-indigo-500">{plan.code}</p>
                  <h2 className="mt-1 text-2xl font-bold text-slate-950">{plan.name}</h2>
                  <p className="mt-3 text-3xl font-bold text-slate-950">
                    {Number(plan.basePrice) === 0
                      ? 'Free'
                      : `${plan.currency} ${plan.basePrice}/${plan.billingCycle.toLowerCase()}`}
                  </p>
                </div>
              </div>
              <div className="mt-6 grid gap-3 sm:grid-cols-2">
                {plan.entitlements.map((entitlement) => (
                  <div
                    key={entitlement.entitlementId}
                    className="flex gap-2 rounded-lg bg-slate-50 p-3 text-sm text-slate-700"
                  >
                    <FiCheck aria-hidden="true" className="mt-0.5 shrink-0 text-indigo-500" />
                    {entitlementLabel(entitlement.key, entitlement.value)}
                  </div>
                ))}
              </div>
            </article>
            <aside className="panel p-5">
              <h3 className="text-base font-bold text-slate-950">Subscription</h3>
              <dl className="mt-4 grid gap-3 text-sm">
                <div>
                  <dt className="text-xs font-semibold text-slate-500">Status</dt>
                  <dd className="mt-1 font-semibold text-slate-950">{subscription.status}</dd>
                </div>
                <div>
                  <dt className="text-xs font-semibold text-slate-500">Started</dt>
                  <dd className="mt-1 text-slate-700">
                    {new Date(subscription.startedAt).toLocaleDateString()}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs font-semibold text-slate-500">Ends</dt>
                  <dd className="mt-1 text-slate-700">
                    {subscription.endsAt
                      ? new Date(subscription.endsAt).toLocaleDateString()
                      : 'No end date'}
                  </dd>
                </div>
              </dl>
              {storage && (
                <div className="mt-6 border-t border-slate-100 pt-5">
                  <div className="flex items-center justify-between gap-3">
                    <h3 className="text-sm font-bold text-slate-950">Storage</h3>
                    <span className="text-xs font-semibold text-slate-500">
                      {storage.usedPercent.toFixed(1)}%
                    </span>
                  </div>
                  <div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-100">
                    <div
                      className="h-full rounded-full bg-indigo-500"
                      style={{ width: `${Math.min(storage.usedPercent, 100)}%` }}
                    />
                  </div>
                  <p className="mt-2 text-xs text-slate-500">
                    {formatBytes(storage.usedBytes)} used of {formatBytes(storage.limitBytes)}
                  </p>
                  {storage.reservedBytes > 0 && (
                    <p className="mt-1 text-[11px] text-slate-400">
                      {formatBytes(storage.reservedBytes)} reserved by pending uploads
                    </p>
                  )}
                  {storage.breakdown.length > 0 && (
                    <div className="mt-4 space-y-2 border-t border-slate-100 pt-4">
                      {storage.breakdown.map((item) => (
                        <div
                          key={item.type}
                          className="flex items-center justify-between gap-3 text-xs"
                        >
                          <span className="text-slate-500">
                            {storageTypeLabel[item.type] ?? item.type}
                          </span>
                          <span className="font-semibold text-slate-700">
                            {formatBytes(item.usedBytes + item.reservedBytes)}
                          </span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </aside>
          </div>
        ) : (
          <div className="panel mt-5 p-6 text-sm font-semibold text-slate-500">
            No active plan found
          </div>
        )}
      </div>
    </section>
  )
}
