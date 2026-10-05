import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  FiAlertTriangle,
  FiCheckCircle,
  FiGlobe,
  FiMail,
  FiPlus,
  FiRefreshCw,
  FiShield,
  FiTrash2,
} from 'react-icons/fi'
import { managedEmailApi, type ManagedEmailApi } from '../../features/managedEmail/managedEmailApi'
import { managedEmailErrorMessage } from '../../features/managedEmail/errors'
import ConfirmDialog from '../ui/ConfirmDialog'
import StatusBadge from '../ui/StatusBadge'
import { CardSkeleton } from '../ui/Skeleton'
import {
  deliverabilityCopy,
  domainStatusLabel,
  domainStatusTone,
  isNearUsageLimit,
  usageLimitLabel,
  usageRemainingLabel,
} from '../../features/managedEmail/managedEmailUi'
import type {
  ManagedEmailDeliverability,
  ManagedEmailDomain,
  ManagedEmailOverview,
  ManagedEmailSender,
  ManagedEmailUsage,
} from '../../features/managedEmail/types'

type Props = {
  companyId: string
  canModify: boolean
  onOpenSetup: (domainId?: string) => void
  refreshToken?: number
  client?: ManagedEmailApi
}

const formatDate = (value?: string | null) => {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(date)
}

const percent = (value: number) => `${(value * 100).toFixed(value * 100 >= 1 ? 1 : 2)}%`

export default function ManagedEmailPanel({
  companyId,
  canModify,
  onOpenSetup,
  refreshToken = 0,
  client = managedEmailApi,
}: Props) {
  const [overview, setOverview] = useState<ManagedEmailOverview | null>(null)
  const [domains, setDomains] = useState<ManagedEmailDomain[]>([])
  const [senders, setSenders] = useState<ManagedEmailSender[]>([])
  const [usage, setUsage] = useState<ManagedEmailUsage | null>(null)
  const [deliverability, setDeliverability] = useState<ManagedEmailDeliverability | null>(null)
  const [loading, setLoading] = useState(true)
  const [workingId, setWorkingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [senderToDisable, setSenderToDisable] = useState<ManagedEmailSender | null>(null)
  const [domainToRetire, setDomainToRetire] = useState<ManagedEmailDomain | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)

    const results = await Promise.allSettled([
      client.overview(companyId),
      client.domains(companyId),
      client.senders(companyId),
      client.usage(companyId),
      client.deliverability(companyId),
    ])
    const [overviewResult, domainsResult, sendersResult, usageResult, deliverabilityResult] =
      results

    if (overviewResult.status === 'fulfilled') {
      setOverview(overviewResult.value)
    } else {
      setError(
        managedEmailErrorMessage(overviewResult.reason, 'Unable to load Managed Email status.'),
      )
    }
    if (domainsResult.status === 'fulfilled') setDomains(domainsResult.value)
    if (sendersResult.status === 'fulfilled') setSenders(sendersResult.value)
    if (usageResult.status === 'fulfilled') setUsage(usageResult.value)
    if (deliverabilityResult.status === 'fulfilled') {
      setDeliverability(deliverabilityResult.value)
    }

    setLoading(false)
  }, [client, companyId])

  useEffect(() => {
    void load()
  }, [load, refreshToken])

  const activeDomains = useMemo(
    () => domains.filter((domain) => !['DELETED', 'RETIRED'].includes(domain.status)),
    [domains],
  )

  const sendingRestricted =
    overview?.sendingStatus !== 'ENABLED' ||
    overview?.serviceStatus === 'SUSPENDED' ||
    deliverability?.sendingRestricted === true

  const removeSender = async (sender: ManagedEmailSender) => {
    if (!canModify || workingId) return
    setWorkingId(sender.id)
    setError(null)
    try {
      await client.removeSender(companyId, sender.id)
      setSenders((current) => current.filter((item) => item.id !== sender.id))
    } catch (requestError) {
      setError(managedEmailErrorMessage(requestError, 'Unable to disable this sender.'))
    } finally {
      setWorkingId(null)
      setSenderToDisable(null)
    }
  }

  const retireDomain = async (domain: ManagedEmailDomain) => {
    if (!canModify || workingId) return
    setWorkingId(domain.id)
    setError(null)
    try {
      const latest = await client.retireDomain(companyId, domain.id)
      if (latest) {
        setDomains((current) => current.map((item) => (item.id === latest.id ? latest : item)))
      } else {
        await load()
      }
    } catch (requestError) {
      setError(managedEmailErrorMessage(requestError, 'Unable to retire this domain.'))
    } finally {
      setWorkingId(null)
      setDomainToRetire(null)
    }
  }

  return (
    <div className="panel mt-6 overflow-hidden">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="text-sm font-bold text-slate-950">UnifyDesk Managed Email</h2>
            {overview && (
              <span
                className={`rounded-full px-2.5 py-1 text-[10px] font-bold ${
                  overview.enabled && overview.serviceStatus === 'ACTIVE'
                    ? 'bg-emerald-50 text-emerald-700'
                    : 'bg-amber-50 text-amber-700'
                }`}
              >
                {overview.enabled ? overview.serviceStatus.replaceAll('_', ' ') : 'NOT ENABLED'}
              </span>
            )}
          </div>
          <p className="mt-1 max-w-2xl text-xs leading-5 text-slate-500">
            Send Broadcast email without managing provider credentials. Add your own domain or let
            UnifyDesk create one for you.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className="secondary-button inline-flex items-center gap-2"
            disabled={loading}
            onClick={() => void load()}
          >
            <FiRefreshCw aria-hidden="true" /> Refresh
          </button>
          {canModify && (
            <button
              type="button"
              className="primary-button inline-flex items-center gap-2"
              onClick={() => onOpenSetup()}
            >
              <FiPlus aria-hidden="true" /> Add domain
            </button>
          )}
        </div>
      </div>

      {error && (
        <div
          className="border-b border-rose-100 bg-rose-50 px-5 py-3 text-xs text-rose-700"
          role="alert"
        >
          {error}
        </div>
      )}

      {overview?.globalNotice && (
        <div className="border-b border-amber-100 bg-amber-50 px-5 py-3 text-xs leading-5 text-amber-800">
          {overview.globalNotice}
        </div>
      )}

      {sendingRestricted && !loading && (
        <div className="border-b border-rose-100 bg-rose-50 px-5 py-3 text-xs leading-5 text-rose-700">
          <span className="inline-flex items-center gap-2 font-bold">
            <FiAlertTriangle aria-hidden="true" /> Sending needs attention
          </span>
          <p className="mt-1">
            Managed Email is not currently ready for sending. Review the domain and deliverability
            status below before using a sender.
          </p>
        </div>
      )}

      {loading ? (
        <div className="grid gap-4 p-5 md:grid-cols-2"><CardSkeleton rows={3} /><CardSkeleton rows={3} /></div>
      ) : (
        <>
          <div className="grid gap-3 border-b border-slate-100 p-5 md:grid-cols-2">
            <section
              className="rounded-xl border border-slate-200 p-4"
              aria-label="Managed Email usage"
            >
              <p className="text-[10px] font-bold uppercase tracking-[0.08em] text-slate-400">
                Current period usage
              </p>
              {usage ? (
                <>
                  <div className="mt-3 flex flex-wrap items-end justify-between gap-3">
                    <div>
                      <p className="text-2xl font-bold text-slate-950">
                        {usage.billable.toLocaleString()}
                      </p>
                      <p className="text-xs text-slate-500">messages used</p>
                    </div>
                    <div className="text-right text-xs text-slate-500">
                      <p>
                        <span className="font-semibold text-slate-700">
                          {usageRemainingLabel(usage)}
                        </span>{' '}
                        remaining
                      </p>
                      <p>Limit: {usageLimitLabel(usage)}</p>
                    </div>
                  </div>
                  <p className="mt-3 text-[11px] text-slate-500">
                    {formatDate(usage.periodStart)} – {formatDate(usage.periodEndExclusive)}
                  </p>
                  {usage.limitReached && (
                    <p className="mt-3 rounded-lg bg-rose-50 px-3 py-2 text-xs font-semibold text-rose-700">
                      Monthly sending limit reached.
                    </p>
                  )}
                  {isNearUsageLimit(usage) && (
                    <p className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-xs font-semibold text-amber-700">
                      You are close to this period&apos;s sending limit.
                    </p>
                  )}
                </>
              ) : (
                <p className="mt-3 text-xs text-slate-500">Usage is temporarily unavailable.</p>
              )}
            </section>

            <section
              className="rounded-xl border border-slate-200 p-4"
              aria-label="Managed Email deliverability"
            >
              <p className="text-[10px] font-bold uppercase tracking-[0.08em] text-slate-400">
                Deliverability
              </p>
              {deliverability ? (
                <>
                  <StatusBadge status={deliverability.status} className="mt-3">
                    {deliverability.status.replaceAll('_', ' ')}
                  </StatusBadge>
                  <p className="mt-2 text-xs leading-5 text-slate-600">
                    {deliverability.notice || deliverabilityCopy(deliverability.status)}
                  </p>
                  {deliverability.accepted > 0 && (
                    <div className="mt-3 grid grid-cols-2 gap-2 text-xs">
                      <div className="rounded-lg bg-slate-50 p-2">
                        <span className="block font-bold text-slate-900">
                          {percent(deliverability.hardBounceRate)}
                        </span>
                        <span className="text-slate-500">hard bounce rate</span>
                      </div>
                      <div className="rounded-lg bg-slate-50 p-2">
                        <span className="block font-bold text-slate-900">
                          {percent(deliverability.complaintRate)}
                        </span>
                        <span className="text-slate-500">complaint rate</span>
                      </div>
                    </div>
                  )}
                </>
              ) : (
                <p className="mt-3 text-xs text-slate-500">
                  Deliverability status is temporarily unavailable.
                </p>
              )}
            </section>
          </div>

          <div className="border-b border-slate-100 px-5 py-4">
            <p className="text-xs font-bold uppercase tracking-[0.08em] text-slate-400">Domains</p>
            <p className="mt-1 text-xs text-slate-500">
              {activeDomains.length}
              {overview ? ` of ${overview.domainLimit}` : ''} configured
            </p>

            {activeDomains.length === 0 ? (
              <div className="py-8 text-center">
                <FiGlobe className="mx-auto text-slate-300" size={26} aria-hidden="true" />
                <p className="mt-2 text-sm font-semibold text-slate-800">
                  No Managed Email domain yet
                </p>
                <p className="mt-1 text-xs text-slate-500">
                  Add your business domain or let UnifyDesk create one.
                </p>
              </div>
            ) : (
              <div className="mt-3 grid gap-3 lg:grid-cols-2">
                {activeDomains.map((domain) => (
                  <div key={domain.id} className="rounded-xl border border-slate-200 p-4">
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <div className="min-w-0">
                        <p className="break-all text-sm font-bold text-slate-900">
                          {domain.domain}
                        </p>
                        <p className="mt-1 text-[11px] text-slate-500">
                          {domain.type === 'UNIFYDESK_HOSTED' ? 'UnifyDesk domain' : 'Your domain'}
                        </p>
                      </div>
                      <span
                        className={`rounded-full px-2.5 py-1 text-[10px] font-bold ${domainStatusTone(domain.status)}`}
                      >
                        {domainStatusLabel(domain.status)}
                      </span>
                    </div>
                    <div className="mt-3 flex flex-wrap gap-2">
                      <button
                        type="button"
                        className="secondary-button text-xs"
                        onClick={() => onOpenSetup(domain.id)}
                      >
                        {domain.status === 'ACTIVE' ? 'Manage' : 'View setup'}
                      </button>
                      {canModify && domain.status !== 'DELETING' && (
                        <button
                          type="button"
                          className="secondary-button inline-flex items-center gap-1 text-xs text-rose-600"
                          disabled={workingId === domain.id}
                          onClick={() => setDomainToRetire(domain)}
                        >
                          <FiTrash2 aria-hidden="true" /> Retire
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="px-5 py-4">
            <p className="text-xs font-bold uppercase tracking-[0.08em] text-slate-400">
              Managed senders
            </p>
            <p className="mt-1 text-xs text-slate-500">
              {senders.length}
              {overview ? ` of ${overview.senderLimit}` : ''} configured
            </p>

            {senders.length === 0 ? (
              <div className="py-8 text-center">
                <FiMail className="mx-auto text-slate-300" size={26} aria-hidden="true" />
                <p className="mt-2 text-sm font-semibold text-slate-800">
                  No Managed Email sender yet
                </p>
                <p className="mt-1 text-xs text-slate-500">Open an active domain to create one.</p>
              </div>
            ) : (
              <div className="mt-3 divide-y divide-slate-100 rounded-xl border border-slate-200">
                {senders.map((sender) => (
                  <div
                    key={sender.id}
                    className="flex flex-col gap-3 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
                  >
                    <div className="min-w-0">
                      <p className="break-all text-sm font-bold text-slate-900">
                        {sender.fromAddress}
                      </p>
                      <p className="mt-1 text-xs text-slate-500">
                        {sender.displayName || 'No display name'} · UnifyDesk Managed Email
                      </p>
                      {!sender.sendingEnabled ? (
                        <p className="mt-1 inline-flex items-center gap-1 text-[11px] font-semibold text-amber-700">
                          <FiShield aria-hidden="true" /> Sending unavailable
                        </p>
                      ) : (
                        <p className="mt-1 inline-flex items-center gap-1 text-[11px] font-semibold text-emerald-700">
                          <FiCheckCircle aria-hidden="true" /> Ready
                        </p>
                      )}
                    </div>
                    {canModify && (
                      <button
                        type="button"
                        className="secondary-button inline-flex items-center gap-1 text-xs text-rose-600"
                        disabled={workingId === sender.id}
                        onClick={() => setSenderToDisable(sender)}
                      >
                        <FiTrash2 aria-hidden="true" /> Disable
                      </button>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        </>
      )}
      <ConfirmDialog
        open={Boolean(senderToDisable)}
        title="Disable this sender?"
        description={senderToDisable ? `Broadcast history for ${senderToDisable.fromAddress} will be kept, but new sending will be unavailable.` : ''}
        confirmLabel="Yes, disable sender"
        cancelLabel="No, keep sender"
        tone="warning"
        working={Boolean(senderToDisable && workingId === senderToDisable.id)}
        onCancel={() => setSenderToDisable(null)}
        onConfirm={() => senderToDisable && void removeSender(senderToDisable)}
      />
      <ConfirmDialog
        open={Boolean(domainToRetire)}
        title="Retire this email domain?"
        description={domainToRetire ? `Managed Email senders under ${domainToRetire.domain} will become unavailable.` : ''}
        confirmLabel="Yes, retire domain"
        cancelLabel="No, keep domain"
        tone="danger"
        kind="delete"
        working={Boolean(domainToRetire && workingId === domainToRetire.id)}
        onCancel={() => setDomainToRetire(null)}
        onConfirm={() => domainToRetire && void retireDomain(domainToRetire)}
      />
    </div>
  )
}
