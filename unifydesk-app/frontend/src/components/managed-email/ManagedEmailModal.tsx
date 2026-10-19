import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import {
  FiArrowLeft,
  FiCheckCircle,
  FiClipboard,
  FiClock,
  FiCloud,
  FiCopy,
  FiGlobe,
  FiRefreshCw,
  FiShield,
  FiX,
} from 'react-icons/fi'
import { managedEmailApi, type ManagedEmailApi } from '../../features/managedEmail/managedEmailApi'
import { managedEmailErrorMessage } from '../../features/managedEmail/errors'
import {
  domainStatusLabel,
  domainStatusTone,
  isBareDomainInput,
  isValidSubdomain,
  normalizeBareDomain,
} from '../../features/managedEmail/managedEmailUi'
import {
  managedEmailPollingDelay,
  shouldPollManagedEmailDomain,
} from '../../features/managedEmail/polling'
import type {
  ManagedEmailDomain,
  ManagedEmailOverview,
  ManagedEmailSender,
} from '../../features/managedEmail/types'
import { CardSkeleton } from '../ui/Skeleton'

type Stage = 'landing' | 'customer-domain' | 'hosted-subdomain' | 'creating-hosted' | 'domain'

type Props = {
  companyId: string
  onClose: () => void
  onChanged?: () => void
  initialDomainId?: string | null
  canModify?: boolean
  client?: ManagedEmailApi
}

const idempotencyKey = () =>
  globalThis.crypto?.randomUUID?.() ??
  `managed-email-${Date.now()}-${Math.random().toString(36).slice(2)}`

const validLocalPart = (value: string) => {
  const trimmed = value.trim()
  return Boolean(trimmed) && !trimmed.includes('@') && !/\s/.test(trimmed) && trimmed.length <= 64
}

export default function ManagedEmailModal({
  companyId,
  onClose,
  onChanged,
  initialDomainId = null,
  canModify = true,
  client = managedEmailApi,
}: Props) {
  const [stage, setStage] = useState<Stage>(initialDomainId ? 'domain' : 'landing')
  const [overview, setOverview] = useState<ManagedEmailOverview | null>(null)
  const [domain, setDomain] = useState<ManagedEmailDomain | null>(null)
  const [senders, setSenders] = useState<ManagedEmailSender[]>([])
  const [domainInput, setDomainInput] = useState('')
  const [subdomainInput, setSubdomainInput] = useState('')
  const [checkingAvailability, setCheckingAvailability] = useState(false)
  const [availabilityStatus, setAvailabilityStatus] = useState<{
    available: boolean
    message: string
  } | null>(null)
  const [localPart, setLocalPart] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [replyToAddress, setReplyToAddress] = useState('')
  const [senderFormOpen, setSenderFormOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [copiedId, setCopiedId] = useState<string | null>(null)
  const [, setError] = useSnackbarState<string | null>(null, 'error')
  const pollAttempt = useRef(0)

  useEffect(() => {
    let active = true
    void client
      .overview(companyId)
      .then((value) => active && setOverview(value))
      .catch((requestError) => {
        if (active) setError(managedEmailErrorMessage(requestError))
      })
    void client
      .senders(companyId)
      .then((value) => active && setSenders(value))
      .catch(() => active && setSenders([]))

    if (initialDomainId) {
      void client
        .domain(companyId, initialDomainId)
        .then((value) => active && setDomain(value))
        .catch((requestError) => {
          if (active) {
            setError(managedEmailErrorMessage(requestError, 'Unable to load this domain.'))
          }
        })
    }

    return () => {
      active = false
    }
  }, [client, companyId, initialDomainId])

  useEffect(() => {
    if (stage !== 'domain' || !domain || !shouldPollManagedEmailDomain(domain.status)) return

    let stopped = false
    const timer = window.setTimeout(() => {
      void client
        .domain(companyId, domain.id)
        .then((latest) => {
          if (stopped) return
          setDomain(latest)
          pollAttempt.current += 1
          if (latest.status === 'ACTIVE') onChanged?.()
        })
        .catch(() => undefined)
    }, managedEmailPollingDelay(pollAttempt.current))

    return () => {
      stopped = true
      window.clearTimeout(timer)
    }
  }, [client, companyId, domain, onChanged, stage])

  const domainSenders = useMemo(
    () =>
      domain
        ? senders.filter(
            (sender) =>
              sender.managedDomainId === domain.id ||
              (!sender.managedDomainId && sender.managedDomain === domain.domain),
          )
        : [],
    [domain, senders],
  )

  const showDomain = (value: ManagedEmailDomain) => {
    pollAttempt.current = 0
    setDomain(value)
    setStage('domain')
    setError(null)
  }

  const createCustomerDomain = async (event: FormEvent) => {
    event.preventDefault()
    const normalized = normalizeBareDomain(domainInput)
    if (!isBareDomainInput(normalized)) {
      setError(
        'Enter a bare domain such as example.com, without https://, a path, or an email address.',
      )
      return
    }

    setBusy(true)
    setError(null)
    try {
      const created = await client.createCustomerDomain(companyId, {
        domain: normalized,
        idempotencyKey: idempotencyKey(),
      })
      showDomain(created)
      onChanged?.()
    } catch (requestError) {
      setError(managedEmailErrorMessage(requestError, 'Unable to add this domain.'))
    } finally {
      setBusy(false)
    }
  }

  const checkSubdomainAvailability = async () => {
    const trimmed = subdomainInput.trim().toLowerCase()
    if (!isValidSubdomain(trimmed)) {
      setAvailabilityStatus({
        available: false,
        message: 'Subdomain must be between 2 and 63 lowercase letters, numbers, or hyphens.',
      })
      return
    }
    setCheckingAvailability(true)
    setError(null)
    try {
      const result = await client.checkSubdomain(companyId, trimmed)
      setAvailabilityStatus({
        available: result.available,
        message: result.message,
      })
    } catch (requestError) {
      setAvailabilityStatus({
        available: false,
        message: managedEmailErrorMessage(requestError, 'Unable to check availability right now.'),
      })
    } finally {
      setCheckingAvailability(false)
    }
  }

  const createHostedDomain = async (event?: FormEvent) => {
    event?.preventDefault()
    const trimmed = subdomainInput.trim().toLowerCase()
    if (!isValidSubdomain(trimmed)) {
      setError('Subdomain must be between 2 and 63 lowercase letters, numbers, or hyphens.')
      return
    }

    setBusy(true)
    setError(null)
    try {
      const created = await client.createHostedDomain(companyId, {
        desiredSubdomain: trimmed,
        idempotencyKey: idempotencyKey(),
      })
      showDomain(created)
      onChanged?.()
    } catch (requestError) {
      setError(
        managedEmailErrorMessage(requestError, 'Unable to submit your domain request.'),
      )
    } finally {
      setBusy(false)
    }
  }

  const refreshDomain = async () => {
    if (!domain || refreshing) return
    setRefreshing(true)
    setError(null)
    try {
      const latest = await client.refreshDomain(companyId, domain.id)
      pollAttempt.current = 0
      setDomain(latest)
      if (latest.status === 'ACTIVE') onChanged?.()
    } catch (requestError) {
      setError(managedEmailErrorMessage(requestError, 'Unable to recheck verification right now.'))
    } finally {
      setRefreshing(false)
    }
  }

  const copyRecord = async (recordId: string, value: string) => {
    try {
      await navigator.clipboard.writeText(value)
      setCopiedId(recordId)
      window.setTimeout(() => {
        setCopiedId((current) => (current === recordId ? null : current))
      }, 1800)
    } catch {
      setError('Unable to copy automatically. Select the value and copy it manually.')
    }
  }

  const createSender = async (event: FormEvent) => {
    event.preventDefault()
    if (!domain || domain.status !== 'ACTIVE') return
    if (!validLocalPart(localPart)) {
      setError('Enter only the part before @. Do not enter a full email address.')
      return
    }

    setBusy(true)
    setError(null)
    try {
      const created = await client.createSender(companyId, {
        domainId: domain.id,
        localPart: localPart.trim(),
        displayName: displayName.trim() || null,
        replyToAddress: replyToAddress.trim() || null,
      })
      setSenders((current) => [created, ...current.filter((sender) => sender.id !== created.id)])
      setLocalPart('')
      setDisplayName('')
      setReplyToAddress('')
      setSenderFormOpen(false)
      onChanged?.()
    } catch (requestError) {
      setError(managedEmailErrorMessage(requestError, 'Unable to create this sender.'))
    } finally {
      setBusy(false)
    }
  }

  const active = domain?.status === 'ACTIVE'
  const sendingAvailable =
    overview?.serviceStatus === 'ACTIVE' && overview?.sendingStatus === 'ENABLED'

  return (
    <div
      className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4"
      role="dialog"
      aria-modal="true"
      aria-label="UnifyDesk Managed Email setup"
    >
      <div className="mobile-sheet max-h-[92vh] w-full max-w-4xl overflow-y-auto rounded-2xl bg-white p-5 shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div className="flex min-w-0 items-start gap-2">
            {stage !== 'landing' && !initialDomainId && (
              <button
                type="button"
                className="admin-back-button shrink-0"
                onClick={() => {
                  setError(null)
                  setDomain(null)
                  setSenderFormOpen(false)
                  setStage('landing')
                }}
                aria-label="Back"
              >
                <FiArrowLeft aria-hidden="true" />
              </button>
            )}
            <div>
              <p className="text-base font-bold text-slate-950">UnifyDesk Managed Email</p>
              <p className="mt-1 text-xs leading-5 text-slate-500">
                UnifyDesk handles the email-sending infrastructure for you. No provider credentials
                are needed.
              </p>
            </div>
          </div>
          <button
            type="button"
            className="admin-back-button shrink-0"
            onClick={onClose}
            aria-label="Close"
          >
            <FiX aria-hidden="true" />
          </button>
        </div>



        {overview && !overview.enabled && (
          <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs text-amber-800">
            Managed Email is not enabled for this workspace.
          </div>
        )}

        {stage === 'landing' && (
          <div className="mt-6 grid gap-4 sm:grid-cols-2">
            <button
              type="button"
              className="rounded-2xl border border-indigo-200 bg-indigo-50/60 p-5 text-left transition hover:border-indigo-300 hover:bg-indigo-50 disabled:opacity-60"
              disabled={!canModify || overview?.enabled !== true}
              onClick={() => setStage('customer-domain')}
            >
              <span className="flex size-11 items-center justify-center rounded-xl bg-white text-indigo-600 shadow-sm">
                <FiGlobe aria-hidden="true" />
              </span>
              <span className="mt-4 block text-sm font-bold text-slate-950">
                I have my own domain
              </span>
              <span className="mt-1 block text-xs leading-5 text-slate-600">
                Use your business domain and add the DNS records UnifyDesk provides.
              </span>
            </button>

            <button
              type="button"
              className="rounded-2xl border border-slate-200 p-5 text-left transition hover:border-indigo-200 hover:bg-slate-50 disabled:opacity-60"
              disabled={!canModify || busy || overview?.enabled !== true}
              onClick={() => {
                setStage('hosted-subdomain')
                setSubdomainInput('')
                setAvailabilityStatus(null)
                setError(null)
              }}
            >
              <span className="flex size-11 items-center justify-center rounded-xl bg-slate-100 text-slate-700">
                <FiCloud aria-hidden="true" />
              </span>
              <span className="mt-4 block text-sm font-bold text-slate-950">
                Use an UnifyDesk domain
              </span>
              <span className="mt-1 block text-xs leading-5 text-slate-600">
                Request a custom branded subdomain (e.g. yourcompany.unifydesk.email) with no DNS setup needed.
              </span>
            </button>
          </div>
        )}

        {stage === 'customer-domain' && (
          <form className="mt-6" onSubmit={(event) => void createCustomerDomain(event)}>
            <label
              className="block text-xs font-semibold text-slate-700"
              htmlFor="managed-email-domain"
            >
              Business domain
            </label>
            <input
              id="managed-email-domain"
              className="form-input mt-2"
              placeholder="example.com"
              value={domainInput}
              onChange={(event) => setDomainInput(event.target.value)}
              autoComplete="off"
              autoFocus
            />
            <p className="mt-2 text-xs leading-5 text-slate-500">
              Enter only the domain, for example <strong>example.com</strong>. Do not enter https://
              or an email address.
            </p>
            <div className="mt-5 flex justify-end">
              <button type="submit" className="primary-button" disabled={busy}>
                {busy ? 'Adding domain…' : 'Add domain'}
              </button>
            </div>
          </form>
        )}

        {stage === 'hosted-subdomain' && (
          <form className="mt-6" onSubmit={(event) => void createHostedDomain(event)}>
            <label
              className="block text-xs font-semibold text-slate-700"
              htmlFor="managed-email-subdomain"
            >
              Choose your company subdomain
            </label>
            <p className="mt-1 text-xs text-slate-500">
              Pick the subdomain you want to keep. We check availability immediately before submitting your request for admin approval.
            </p>
            <div className="mt-3 flex items-center rounded-xl border border-slate-300 bg-white px-3 shadow-sm focus-within:border-indigo-500 focus-within:ring-1 focus-within:ring-indigo-500">
              <input
                id="managed-email-subdomain"
                className="w-full border-0 bg-transparent py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-0"
                placeholder="acme"
                value={subdomainInput}
                onChange={(event) => {
                  setSubdomainInput(event.target.value.toLowerCase().trim())
                  setAvailabilityStatus(null)
                  setError(null)
                }}
                disabled={busy}
                autoFocus
                autoComplete="off"
              />
              <span className="shrink-0 text-sm font-semibold text-slate-500 select-none">
                .unifydesk.email
              </span>
              <button
                type="button"
                className="ml-3 shrink-0 rounded-lg bg-slate-100 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-200 disabled:opacity-50"
                onClick={() => void checkSubdomainAvailability()}
                disabled={busy || checkingAvailability || !subdomainInput}
              >
                {checkingAvailability ? 'Checking…' : 'Check Availability'}
              </button>
            </div>

            {availabilityStatus && (
              <div
                className={`mt-2.5 flex items-center gap-2 rounded-lg px-3 py-2 text-xs font-medium ${
                  availabilityStatus.available
                    ? 'border border-emerald-200 bg-emerald-50 text-emerald-700'
                    : 'border border-rose-200 bg-rose-50 text-rose-700'
                }`}
              >
                {availabilityStatus.available ? (
                  <FiCheckCircle className="size-4 shrink-0 text-emerald-600" />
                ) : (
                  <FiX className="size-4 shrink-0 text-rose-600" />
                )}
                <span>{availabilityStatus.message}</span>
              </div>
            )}

            <div className="mt-6 flex justify-end gap-3">
              <button
                type="button"
                className="rounded-xl border border-slate-300 bg-white px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                onClick={() => setStage('landing')}
                disabled={busy}
              >
                Cancel
              </button>
              <button
                type="submit"
                className="primary-button"
                disabled={busy || !subdomainInput || availabilityStatus?.available === false}
              >
                {busy ? 'Submitting request…' : 'Submit Subdomain Request'}
              </button>
            </div>
          </form>
        )}

        {stage === 'creating-hosted' && (
          <div className="mt-8 rounded-2xl border border-slate-200 p-6 text-center">
            <FiRefreshCw
              className="mx-auto animate-spin text-indigo-600"
              aria-hidden="true"
              size={24}
            />
            <p className="mt-3 text-sm font-bold text-slate-900">Creating your email domain</p>
            <p className="mt-1 text-xs text-slate-500">
              UnifyDesk is configuring it automatically.
            </p>
          </div>
        )}

        {stage === 'domain' && !domain && (
          <div className="mt-6"><CardSkeleton rows={5} /></div>
        )}

        {stage === 'domain' && domain && (
          <div className="mt-6">
            <div className="flex flex-wrap items-start justify-between gap-3 rounded-2xl border border-slate-200 p-4">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="break-all text-base font-bold text-slate-950">{domain.domain}</p>
                  <span
                    className={`rounded-full px-2.5 py-1 text-[10px] font-bold ${domainStatusTone(domain.status)}`}
                  >
                    {domainStatusLabel(domain.status)}
                  </span>
                </div>
                <p className="mt-2 text-xs leading-5 text-slate-500">
                  {domain.status === 'PENDING_APPROVAL'
                    ? 'Your custom subdomain request is currently under review by platform administrators.'
                    : domain.status === 'REJECTED'
                      ? `This subdomain request was rejected${domain.rejectionReason ? `: "${domain.rejectionReason}"` : '.'}`
                      : domain.type === 'UNIFYDESK_HOSTED'
                        ? 'UnifyDesk configures this domain automatically. You do not need to change DNS.'
                        : domain.status === 'ACTIVE'
                          ? 'Your domain is verified and ready for Managed Email senders.'
                          : 'DNS changes may need time to propagate. UnifyDesk will keep checking while this setup is open.'}
                </p>
              </div>
              {canModify && !['DELETED', 'RETIRED'].includes(domain.status) && (
                <button
                  type="button"
                  className="secondary-button inline-flex items-center gap-2"
                  disabled={refreshing}
                  onClick={() => void refreshDomain()}
                >
                  <FiRefreshCw aria-hidden="true" />
                  {refreshing ? 'Checking…' : 'Recheck'}
                </button>
              )}
            </div>

            {domain.status === 'PENDING_APPROVAL' && (
              <div className="mt-4 rounded-xl border border-blue-200 bg-blue-50/70 p-4">
                <div className="flex items-start gap-3">
                  <FiClock className="mt-0.5 size-5 shrink-0 text-blue-600" />
                  <div>
                    <p className="text-xs font-bold text-blue-900">Request Pending Administrator Review</p>
                    <p className="mt-1 text-xs leading-5 text-blue-700">
                      Your requested subdomain <span className="font-semibold">{domain.domain}</span> has been submitted to platform administrators for verification. Once approved, DNS records and email sending capability will be provisioned automatically.
                    </p>
                  </div>
                </div>
              </div>
            )}

            {domain.status === 'REJECTED' && (
              <div className="mt-4 rounded-xl border border-rose-200 bg-rose-50/70 p-4">
                <div className="flex items-start gap-3">
                  <FiX className="mt-0.5 size-5 shrink-0 text-rose-600" />
                  <div>
                    <p className="text-xs font-bold text-rose-900">Subdomain Request Rejected</p>
                    <p className="mt-1 text-xs leading-5 text-rose-700">
                      {domain.rejectionReason
                        ? `Reason: ${domain.rejectionReason}`
                        : 'Your subdomain request could not be approved at this time.'}
                    </p>
                    {canModify && (
                      <button
                        type="button"
                        className="mt-3 rounded-lg bg-rose-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-rose-700"
                        onClick={() => {
                          setStage('hosted-subdomain')
                          setSubdomainInput('')
                          setAvailabilityStatus(null)
                          setError(null)
                        }}
                      >
                        Request a different subdomain
                      </button>
                    )}
                  </div>
                </div>
              </div>
            )}

            {domain.adminAssignedAlternative && (
              <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50/70 p-4 text-xs text-amber-800">
                Notice: An administrator assigned an alternative subdomain for this workspace.
              </div>
            )}

            {domain.type === 'CUSTOMER_DOMAIN' &&
              domain.dnsRecords.length > 0 &&
              domain.status !== 'ACTIVE' && (
                <div className="mt-5">
                  <div className="flex items-start gap-3 rounded-xl border border-sky-100 bg-sky-50/70 p-4">
                    <FiShield className="mt-0.5 shrink-0 text-sky-700" aria-hidden="true" />
                    <div>
                      <p className="text-xs font-bold text-sky-900">
                        Add these DNS records with your domain provider
                      </p>
                      <p className="mt-1 text-xs leading-5 text-sky-700">
                        Required records activate sending. Recommended records improve
                        deliverability or security but do not block activation unless the backend
                        marks them required.
                      </p>
                    </div>
                  </div>

                  <div className="mt-3 grid gap-3">
                    {domain.dnsRecords.map((record) => (
                      <div key={record.id} className="rounded-xl border border-slate-200 p-4">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="rounded bg-slate-100 px-2 py-1 text-[10px] font-bold text-slate-700">
                              {record.type}
                            </span>
                            <span
                              className={`rounded px-2 py-1 text-[10px] font-bold ${
                                record.required
                                  ? 'bg-amber-50 text-amber-700'
                                  : 'bg-sky-50 text-sky-700'
                              }`}
                            >
                              {record.required ? 'Required for activation' : 'Recommended'}
                            </span>
                            <span className="text-[10px] font-semibold text-slate-500">
                              {record.status}
                            </span>
                          </div>
                          <span className="text-[10px] font-semibold uppercase tracking-wide text-slate-400">
                            {record.purpose}
                          </span>
                        </div>
                        <div className="mt-3 grid gap-3 md:grid-cols-2">
                          <DnsValue
                            label="Host / Name"
                            value={record.name}
                            copied={copiedId === `${record.id}-name`}
                            onCopy={() => void copyRecord(`${record.id}-name`, record.name)}
                          />
                          <DnsValue
                            label="Value"
                            value={record.value}
                            copied={copiedId === `${record.id}-value`}
                            onCopy={() => void copyRecord(`${record.id}-value`, record.value)}
                          />
                        </div>
                        {record.priority !== null && record.priority !== undefined && (
                          <p className="mt-3 text-xs text-slate-600">
                            <span className="font-semibold">Priority:</span> {record.priority}
                          </p>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}

            {domain.type === 'UNIFYDESK_HOSTED' && domain.status !== 'ACTIVE' && (
              <div className="mt-5 rounded-xl border border-indigo-100 bg-indigo-50/60 p-4">
                <p className="text-xs font-bold text-indigo-900">Automatic setup in progress</p>
                <p className="mt-1 text-xs leading-5 text-indigo-700">
                  Your assigned domain is <strong>{domain.domain}</strong>. UnifyDesk is configuring
                  it automatically; no DNS action is required.
                </p>
              </div>
            )}

            {(domain.status === 'SUSPENDED_POLICY' ||
              domain.status === 'SUSPENDED_VERIFICATION' ||
              overview?.sendingStatus !== 'ENABLED') && (
              <div className="mt-5 rounded-xl border border-rose-200 bg-rose-50 p-4 text-xs leading-5 text-rose-700">
                Managed Email sending is not currently ready. Review the domain and workspace status
                before using this sender.
              </div>
            )}

            {active && (
              <div className="mt-5 rounded-2xl border border-emerald-200 bg-emerald-50/50 p-4">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <p className="flex items-center gap-2 text-sm font-bold text-emerald-900">
                      <FiCheckCircle aria-hidden="true" /> Domain active
                    </p>
                    <p className="mt-1 text-xs text-emerald-700">
                      Create a sender address under this verified domain.
                    </p>
                  </div>
                  {canModify && sendingAvailable && !senderFormOpen && (
                    <button
                      type="button"
                      className="primary-button"
                      onClick={() => setSenderFormOpen(true)}
                    >
                      Create sender
                    </button>
                  )}
                </div>
              </div>
            )}

            {canModify && senderFormOpen && active && sendingAvailable && (
              <form
                className="mt-5 rounded-2xl border border-slate-200 p-4"
                onSubmit={(event) => void createSender(event)}
              >
                <p className="text-sm font-bold text-slate-950">Create sender</p>
                <label
                  className="mt-4 block text-xs font-semibold text-slate-700"
                  htmlFor="managed-email-local-part"
                >
                  Email address
                </label>
                <div className="mt-2 flex overflow-hidden rounded-xl border border-slate-200 focus-within:border-indigo-300 focus-within:ring-2 focus-within:ring-indigo-100">
                  <input
                    id="managed-email-local-part"
                    className="min-w-0 flex-1 border-0 px-3 py-2.5 text-sm outline-none"
                    value={localPart}
                    onChange={(event) => setLocalPart(event.target.value)}
                    placeholder="offers"
                    autoComplete="off"
                  />
                  <span className="flex items-center border-l border-slate-200 bg-slate-50 px-3 text-sm font-semibold text-slate-600">
                    @ {domain.domain}
                  </span>
                </div>
                <p className="mt-1 text-[11px] text-slate-500">
                  Enter only the part before @. The domain is locked to this active Managed Email
                  domain.
                </p>

                <div className="mt-4 grid gap-4 sm:grid-cols-2">
                  <label className="text-xs font-semibold text-slate-700">
                    Display name
                    <input
                      className="form-input mt-2"
                      value={displayName}
                      onChange={(event) => setDisplayName(event.target.value)}
                      placeholder="Example Offers"
                    />
                  </label>
                  <label className="text-xs font-semibold text-slate-700">
                    Reply-to <span className="font-normal text-slate-400">(optional)</span>
                    <input
                      className="form-input mt-2"
                      type="email"
                      value={replyToAddress}
                      onChange={(event) => setReplyToAddress(event.target.value)}
                      placeholder={`support@${domain.domain}`}
                    />
                  </label>
                </div>
                <div className="mt-4 flex flex-wrap justify-end gap-2">
                  <button
                    type="button"
                    className="secondary-button"
                    disabled={busy}
                    onClick={() => setSenderFormOpen(false)}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="primary-button"
                    disabled={busy || !validLocalPart(localPart)}
                  >
                    {busy ? 'Creating…' : 'Create sender'}
                  </button>
                </div>
              </form>
            )}

            {domainSenders.length > 0 && (
              <div className="mt-5">
                <p className="text-xs font-bold uppercase tracking-[0.08em] text-slate-400">
                  Senders on this domain
                </p>
                <div className="mt-2 grid gap-2 sm:grid-cols-2">
                  {domainSenders.map((sender) => (
                    <div key={sender.id} className="rounded-xl border border-slate-200 px-4 py-3">
                      <p className="break-all text-sm font-bold text-slate-900">
                        {sender.fromAddress}
                      </p>
                      <p className="mt-1 text-xs text-slate-500">
                        {sender.displayName || 'No display name'} · UnifyDesk Managed Email
                      </p>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

function DnsValue({
  label,
  value,
  copied,
  onCopy,
}: {
  label: string
  value: string
  copied: boolean
  onCopy: () => void
}) {
  return (
    <div>
      <p className="text-[10px] font-bold uppercase tracking-wide text-slate-400">{label}</p>
      <div className="mt-1 flex items-start gap-2 rounded-lg bg-slate-50 px-3 py-2">
        <code className="min-w-0 flex-1 break-all text-xs text-slate-700">{value}</code>
        <button
          type="button"
          className="shrink-0 text-slate-500 hover:text-indigo-600"
          onClick={onCopy}
          aria-label={`Copy ${label}`}
        >
          {copied ? <FiClipboard aria-hidden="true" /> : <FiCopy aria-hidden="true" />}
        </button>
      </div>
      {copied && (
        <p className="mt-1 text-[10px] font-semibold text-emerald-600">Copied to clipboard</p>
      )}
    </div>
  )
}
