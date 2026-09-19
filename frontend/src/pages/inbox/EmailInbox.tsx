import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import { FiArrowLeft, FiInbox, FiMail, FiRefreshCw, FiSend } from 'react-icons/fi'
import EmailHtmlFrame from '../../components/inbox/EmailHtmlFrame'
import { asyncJobApi } from '../../features/asyncJob/asyncJobApi'
import type { AsyncJob } from '../../features/asyncJob/types'
import { channelApi } from '../../features/channel/channelApi'
import { gmailApi } from '../../features/channel/gmailApi'
import type { ChannelConnection } from '../../features/channel/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { inboxApi } from '../../features/inbox/inboxApi'
import type { InboxConversation, InboxMessage } from '../../features/inbox/types'
import { realtimeClient } from '../../features/realtime/realtimeClient'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId } from '../../store/workspace/workspaceSlice'

const formatDate = (value?: string | null) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''

  const today = new Date()
  if (date.toDateString() === today.toDateString()) {
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }

  return date.toLocaleDateString([], { month: 'short', day: 'numeric' })
}

const displayParticipant = (conversation: InboxConversation, ownAddress?: string) => {
  const participant = conversation.participants.find(
    (item) => item.address.toLowerCase() !== ownAddress?.toLowerCase(),
  )
  return participant?.displayName || participant?.address || ownAddress || 'Email'
}

const messageTitle = (message: InboxMessage) =>
  message.subject || message.snippet || (message.direction === 'OUTBOUND' ? 'Sent email' : 'Email')

const isHtmlBody = (value?: string | null) => {
  if (!value) return false
  const trimmed = value.trim()
  return (
    /^<!doctype html/i.test(trimmed) ||
    /^<html/i.test(trimmed) ||
    /<(body|table|div|p|br|head|style)[\\s>]/i.test(trimmed)
  )
}

const localDateInput = (date: Date) => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const defaultSyncRange = () => {
  const to = new Date()
  const from = new Date()
  from.setDate(from.getDate() - 30)
  return { from: localDateInput(from), to: localDateInput(to) }
}

export default function EmailInbox() {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const { can } = useIam()
  const [searchParams, setSearchParams] = useSearchParams()
  const selectedConnectionId = searchParams.get('connectionId')
  const composeOpen = searchParams.get('compose') === '1'
  const [connections, setConnections] = useState<ChannelConnection[]>([])
  const [conversations, setConversations] = useState<InboxConversation[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [mobileConversationOpen, setMobileConversationOpen] = useState(false)
  const [messages, setMessages] = useState<InboxMessage[]>([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [currentPage, setCurrentPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [messagesLoading, setMessagesLoading] = useState(false)
  const [syncing, setSyncing] = useState(false)
  const [sending, setSending] = useState(false)
  const [showSyncDialog, setShowSyncDialog] = useState(false)
  const [composeTo, setComposeTo] = useState('')
  const [composeSubject, setComposeSubject] = useState('')
  const [composeBody, setComposeBody] = useState('')
  const initialSyncRange = useMemo(() => defaultSyncRange(), [])
  const [syncFrom, setSyncFrom] = useState(initialSyncRange.from)
  const [syncTo, setSyncTo] = useState(initialSyncRange.to)
  const [syncJobs, setSyncJobs] = useState<Record<string, AsyncJob>>({})
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [realtimeConnected, setRealtimeConnected] = useState(realtimeClient.isConnected())

  const selectedConnection = useMemo(
    () => connections.find((connection) => connection.id === selectedConnectionId) ?? null,
    [connections, selectedConnectionId],
  )

  const selectedConversation = useMemo(
    () => conversations.find((conversation) => conversation.id === selectedId) ?? null,
    [conversations, selectedId],
  )

  const ownAddress = useMemo(() => {
    if (!selectedConversation) return undefined
    return (
      connections.find((connection) => connection.id === selectedConversation.connectionId)
        ?.externalAccountId ?? undefined
    )
  }, [connections, selectedConversation])

  const loadConversations = useCallback(
    async (page = 0, append = false) => {
      if (!companyId) return
      setError(null)

      if (append) {
        setLoadingMore(true)
      } else {
        setLoading(true)
      }

      try {
        const emailConnections = await channelApi.list(companyId, 'EMAIL')
        setConnections(emailConnections)

        const activeConnections = emailConnections.filter(
          (connection) => connection.status === 'ACTIVE',
        )
        const selectedIsActive = activeConnections.some(
          (connection) => connection.id === selectedConnectionId,
        )

        if (!selectedConnectionId || !selectedIsActive) {
          const first = activeConnections[0]
          setConversations([])
          setSelectedId(null)
          setCurrentPage(0)
          setTotalPages(0)
          setTotalElements(0)

          if (first) {
            const params = new URLSearchParams(searchParams)
            params.set('connectionId', first.id)
            setSearchParams(params, { replace: true })
          }
          return
        }

        const inbox = await inboxApi.conversations(companyId, page, 30, selectedConnectionId)

        setConversations((current) => {
          if (!append) return inbox.items
          const byId = new Map(current.map((conversation) => [conversation.id, conversation]))
          inbox.items.forEach((conversation) => byId.set(conversation.id, conversation))
          return Array.from(byId.values())
        })
        setCurrentPage(inbox.page)
        setTotalPages(inbox.totalPages)
        setTotalElements(inbox.totalElements)

        setSelectedId((current) => {
          if (append && current) return current
          if (current && inbox.items.some((conversation) => conversation.id === current)) {
            return current
          }
          return inbox.items[0]?.id ?? null
        })
      } catch (requestError) {
        setError(apiErrorMessage(requestError, 'Unable to load email inbox'))
      } finally {
        setLoading(false)
        setLoadingMore(false)
      }
    },
    [companyId, searchParams, selectedConnectionId, setSearchParams],
  )

  useEffect(() => {
    setMobileConversationOpen(false)
    setSelectedId(null)
    void loadConversations(0, false)
  }, [companyId, selectedConnectionId, loadConversations])

  useEffect(() => {
    const gmailResult = searchParams.get('gmail')
    if (!gmailResult) return

    if (gmailResult === 'connected') {
      setNotice('Gmail connected successfully')
    } else if (gmailResult === 'cancelled') {
      setNotice('Gmail connection was cancelled')
    } else {
      setError('Unable to connect Gmail. Please try again.')
    }

    const params = new URLSearchParams(searchParams)
    params.delete('gmail')
    setSearchParams(params, { replace: true })
  }, [searchParams, setSearchParams])

  useEffect(() => {
    if (!companyId || !selectedId) {
      setMessages([])
      return
    }

    let active = true
    setMessagesLoading(true)
    inboxApi
      .messages(companyId, selectedId)
      .then((items) => {
        if (active) setMessages(items)
      })
      .catch((requestError) => {
        if (active) setError(apiErrorMessage(requestError, 'Unable to load conversation'))
      })
      .finally(() => {
        if (active) setMessagesLoading(false)
      })

    return () => {
      active = false
    }
  }, [companyId, selectedId])

  const trackedSyncJobs = useMemo(() => Object.values(syncJobs), [syncJobs])
  const activeSyncJobs = useMemo(
    () => trackedSyncJobs.filter((job) => job.status === 'QUEUED' || job.status === 'RUNNING'),
    [trackedSyncJobs],
  )
  const overallSyncProgress = useMemo(() => {
    if (trackedSyncJobs.length === 0) return 0
    return Math.round(
      trackedSyncJobs.reduce((total, job) => total + job.progressPercent, 0) /
        trackedSyncJobs.length,
    )
  }, [trackedSyncJobs])

  useEffect(
    () =>
      realtimeClient.subscribeConnection((connected) => {
        console.info('[WS_DIAG] inbox_connection_state', { connected })
        setRealtimeConnected(connected)
      }),
    [],
  )

  useEffect(() => {
    return realtimeClient.subscribe<AsyncJob>('async.job.progress', (event) => {
      const job = event.payload
      console.info('[GMAIL_SYNC_DIAG] realtime_progress', {
        jobId: job.jobId,
        status: job.status,
        progressPercent: job.progressPercent,
        completedUnits: job.completedUnits,
        totalUnits: job.totalUnits,
      })
      setSyncJobs((current) => {
        if (!current[job.jobId]) return current
        return { ...current, [job.jobId]: job }
      })

      if (job.status === 'COMPLETED') {
        void loadConversations(0, false)
      }
      if (job.status === 'FAILED') {
        setError(job.errorMessage || 'Gmail sync failed')
      }
    })
  }, [loadConversations])

  useEffect(() => {
    if (activeSyncJobs.length === 0 || realtimeConnected) return

    console.warn('[GMAIL_SYNC_DIAG] polling_fallback_enabled', {
      activeJobIds: activeSyncJobs.map((job) => job.jobId),
    })

    const timer = window.setInterval(() => {
      void Promise.all(
        activeSyncJobs.map((job) =>
          asyncJobApi
            .get(job.jobId)
            .then((latest) => {
              console.debug('[GMAIL_SYNC_DIAG] polling_progress', {
                jobId: latest.jobId,
                status: latest.status,
                progressPercent: latest.progressPercent,
              })
              setSyncJobs((current) => ({ ...current, [latest.jobId]: latest }))
            })
            .catch(() => undefined),
        ),
      )
    }, 3000)

    return () => window.clearInterval(timer)
  }, [activeSyncJobs, realtimeConnected])

  const syncGmail = async () => {
    if (!companyId || syncing) return
    if (!selectedConnection) {
      setNotice('Select an email account before syncing.')
      setShowSyncDialog(false)
      return
    }
    if (selectedConnection.providerType !== 'GMAIL' || selectedConnection.status !== 'ACTIVE') {
      setNotice('The selected email account is not an active Gmail connection.')
      setShowSyncDialog(false)
      return
    }
    if (!syncFrom || !syncTo) {
      setError('Choose both Gmail sync dates.')
      return
    }
    if (syncFrom > syncTo) {
      setError('From date must be before or equal to the to date.')
      return
    }

    setSyncing(true)
    setError(null)
    setNotice(null)

    try {
      console.info('[GMAIL_SYNC_DIAG] sync_request', {
        companyId,
        connectionId: selectedConnection.id,
        fromDate: syncFrom,
        toDate: syncTo,
        realtimeConnected,
      })

      const queued = await gmailApi.sync(companyId, selectedConnection.id, {
        fromDate: syncFrom,
        toDate: syncTo,
      })
      console.info('[GMAIL_SYNC_DIAG] sync_queued', {
        connectionId: queued.connectionId,
        jobId: queued.jobId,
        status: queued.status,
      })
      const job = await asyncJobApi.get(queued.jobId)
      console.info('[GMAIL_SYNC_DIAG] initial_job_state', {
        jobId: job.jobId,
        status: job.status,
        progressPercent: job.progressPercent,
      })
      setSyncJobs({ [job.jobId]: job })
      setShowSyncDialog(false)
      setNotice(
        `Gmail sync started for ${selectedConnection.externalAccountId || selectedConnection.name}.`,
      )
    } catch (requestError) {
      const message = apiErrorMessage(requestError, 'Unable to queue Gmail sync')
      console.error('[GMAIL_SYNC_DIAG] sync_request_failed', {
        companyId,
        connectionId: selectedConnection.id,
        message,
      })
      setError(message)
    } finally {
      setSyncing(false)
    }
  }

  const closeCompose = () => {
    const params = new URLSearchParams(searchParams)
    params.delete('compose')
    setSearchParams(params, { replace: true })
  }

  const openCompose = () => {
    if (!selectedConnection) {
      setNotice('Select an email account before composing.')
      return
    }
    const params = new URLSearchParams(searchParams)
    params.set('connectionId', selectedConnection.id)
    params.set('compose', '1')
    setSearchParams(params)
  }

  const sendEmail = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!companyId || !selectedConnection || sending) return
    if (selectedConnection.providerType !== 'GMAIL') {
      setError('Sending is currently available for Gmail connections.')
      return
    }

    setSending(true)
    setError(null)
    setNotice(null)
    try {
      const sent = await gmailApi.send(companyId, selectedConnection.id, {
        to: composeTo.trim(),
        subject: composeSubject.trim(),
        bodyText: composeBody,
      })
      setComposeTo('')
      setComposeSubject('')
      setComposeBody('')
      closeCompose()
      setNotice(`Email sent from ${sent.from} to ${sent.to}.`)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Unable to send email'))
    } finally {
      setSending(false)
    }
  }

  const canSendSelectedGmail =
    can(PERMISSIONS.EMAIL_SEND) && selectedConnection?.providerType === 'GMAIL'

  if (!companyId) {
    return (
      <section className="px-6 py-8">
        <div className="panel p-8 text-center text-sm text-slate-500">
          Select a workspace to open the email inbox.
        </div>
      </section>
    )
  }

  return (
    <section className="px-3 py-4 sm:px-5 sm:py-6 lg:px-6">
      <div className="mx-auto max-w-[1500px]">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3 sm:mb-5">
          <div>
            <p className="eyebrow">INBOX</p>
            <h1 className="mt-1 text-xl font-bold text-slate-950 sm:text-2xl">Email</h1>
            <p className="mt-1 text-sm text-slate-500">
              {selectedConnection
                ? `Showing conversations for ${selectedConnection.externalAccountId || selectedConnection.name}.`
                : 'Gmail conversations synchronized into your ReachFly workspace.'}
            </p>
          </div>
          <div className="flex w-full flex-wrap items-center gap-2 sm:w-auto">
            <button
              type="button"
              className="secondary-button flex-1 gap-2 sm:flex-none"
              onClick={() => void loadConversations(0, false)}
              disabled={loading || !selectedConnection}
            >
              <FiRefreshCw aria-hidden="true" />
              Refresh
            </button>
            <button
              type="button"
              className="secondary-button flex-1 gap-2 sm:flex-none"
              onClick={() => setShowSyncDialog(true)}
              disabled={
                !selectedConnection ||
                selectedConnection.providerType !== 'GMAIL' ||
                syncing ||
                activeSyncJobs.length > 0
              }
            >
              <FiMail aria-hidden="true" />
              {syncing ? 'Queuing...' : activeSyncJobs.length > 0 ? 'Syncing...' : 'Sync Gmail'}
            </button>
            {canSendSelectedGmail && (
              <button
                type="button"
                className="primary-button flex-1 gap-2 sm:flex-none"
                onClick={openCompose}
              >
                <FiSend aria-hidden="true" />
                Compose
              </button>
            )}
          </div>
        </div>

        {error && (
          <div className="mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}
        {notice && (
          <div className="mb-4 rounded-xl border border-indigo-100 bg-indigo-50 px-4 py-3 text-sm text-indigo-700">
            {notice}
          </div>
        )}

        {trackedSyncJobs.length > 0 && (
          <div className="panel mb-4 p-4">
            <div className="flex items-center justify-between gap-3">
              <div className="min-w-0">
                <p className="text-sm font-bold text-slate-900">
                  {activeSyncJobs.length > 0
                    ? 'Syncing Gmail'
                    : trackedSyncJobs.every((job) => job.status === 'COMPLETED')
                      ? 'Gmail sync complete'
                      : 'Gmail sync finished'}
                </p>
                <p className="mt-1 truncate text-xs text-slate-500">
                  {activeSyncJobs[0]?.statusMessage ||
                    trackedSyncJobs.at(-1)?.statusMessage ||
                    'Preparing sync'}
                </p>
              </div>
              <span className="text-sm font-bold text-indigo-600">{overallSyncProgress}%</span>
            </div>
            <div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-100">
              <div
                className="h-full rounded-full bg-indigo-500 transition-[width] duration-300"
                style={{ width: `${overallSyncProgress}%` }}
              />
            </div>
            {selectedConnection?.externalAccountId && (
              <p className="mt-2 text-[11px] text-slate-400">
                {selectedConnection.externalAccountId}
              </p>
            )}
          </div>
        )}

        {composeOpen && (
          <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4"
            role="presentation"
            onMouseDown={(event) => {
              if (event.currentTarget === event.target && !sending) closeCompose()
            }}
          >
            <form
              className="w-full max-w-xl rounded-2xl bg-white p-5 shadow-2xl sm:p-6"
              onSubmit={(event) => void sendEmail(event)}
              role="dialog"
              aria-modal="true"
              aria-labelledby="gmail-compose-title"
            >
              <p className="eyebrow">NEW EMAIL</p>
              <h2 id="gmail-compose-title" className="mt-2 text-lg font-bold text-slate-950">
                Compose email
              </h2>
              <p className="mt-2 text-xs text-slate-500">
                From: {selectedConnection?.externalAccountId || selectedConnection?.name || 'Email'}
              </p>

              <div className="mt-5 space-y-4">
                <label className="block text-xs font-semibold text-slate-600">
                  To
                  <input
                    type="email"
                    className="form-input mt-1.5"
                    value={composeTo}
                    onChange={(event) => setComposeTo(event.target.value)}
                    placeholder="recipient@example.com"
                    required
                    autoFocus
                  />
                </label>
                <label className="block text-xs font-semibold text-slate-600">
                  Subject
                  <input
                    type="text"
                    className="form-input mt-1.5"
                    value={composeSubject}
                    onChange={(event) => setComposeSubject(event.target.value)}
                    placeholder="Subject"
                    maxLength={998}
                  />
                </label>
                <label className="block text-xs font-semibold text-slate-600">
                  Message
                  <textarea
                    className="form-input mt-1.5 min-h-48"
                    value={composeBody}
                    onChange={(event) => setComposeBody(event.target.value)}
                    placeholder="Write your message..."
                    required
                    maxLength={100000}
                  />
                </label>
              </div>

              <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                <button
                  type="button"
                  className="secondary-button justify-center"
                  disabled={sending}
                  onClick={closeCompose}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="primary-button justify-center gap-2"
                  disabled={sending || !composeTo.trim() || !composeBody.trim()}
                >
                  <FiSend aria-hidden="true" />
                  {sending ? 'Sending...' : 'Send email'}
                </button>
              </div>
            </form>
          </div>
        )}

        {showSyncDialog && (
          <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4"
            role="presentation"
            onMouseDown={(event) => {
              if (event.currentTarget === event.target && !syncing) setShowSyncDialog(false)
            }}
          >
            <div
              className="w-full max-w-md rounded-2xl bg-white p-5 shadow-2xl sm:p-6"
              role="dialog"
              aria-modal="true"
              aria-labelledby="gmail-sync-title"
            >
              <p className="eyebrow">GMAIL SYNC</p>
              <h2 id="gmail-sync-title" className="mt-2 text-lg font-bold text-slate-950">
                Choose email date range
              </h2>
              <p className="mt-2 text-sm leading-6 text-slate-500">
                ReachFly will import Gmail messages within this range and show live progress.
              </p>

              <div className="mt-5 grid gap-4 sm:grid-cols-2">
                <label className="text-xs font-semibold text-slate-600">
                  From
                  <input
                    type="date"
                    className="form-input mt-1.5"
                    value={syncFrom}
                    max={syncTo}
                    onChange={(event) => setSyncFrom(event.target.value)}
                  />
                </label>
                <label className="text-xs font-semibold text-slate-600">
                  To
                  <input
                    type="date"
                    className="form-input mt-1.5"
                    value={syncTo}
                    min={syncFrom}
                    max={localDateInput(new Date())}
                    onChange={(event) => setSyncTo(event.target.value)}
                  />
                </label>
              </div>

              <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                <button
                  type="button"
                  className="secondary-button justify-center"
                  disabled={syncing}
                  onClick={() => setShowSyncDialog(false)}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  className="primary-button justify-center gap-2"
                  disabled={syncing}
                  onClick={() => void syncGmail()}
                >
                  <FiMail aria-hidden="true" />
                  {syncing ? 'Starting...' : 'Start sync'}
                </button>
              </div>
            </div>
          </div>
        )}

        <div className="panel grid min-h-[calc(100dvh-220px)] overflow-hidden md:grid-cols-[300px_minmax(0,1fr)] xl:grid-cols-[360px_minmax(0,1fr)]">
          <aside
            className={`${mobileConversationOpen ? 'hidden md:block' : 'block'} border-b border-slate-200 md:border-b-0 md:border-r`}
          >
            <div className="border-b border-slate-100 px-4 py-4">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <h2 className="text-sm font-bold text-slate-900">Conversations</h2>
                  <p className="mt-1 text-xs text-slate-500">
                    {conversations.length} of {totalElements} loaded
                  </p>
                </div>
                <FiInbox className="text-slate-400" aria-hidden="true" />
              </div>
            </div>

            <div className="max-h-[calc(100dvh-300px)] overflow-y-auto md:max-h-[calc(100dvh-210px)]">
              {loading ? (
                <div className="px-5 py-10 text-center text-sm text-slate-500">
                  Loading conversations...
                </div>
              ) : conversations.length === 0 ? (
                <div className="px-6 py-12 text-center">
                  <FiMail className="mx-auto text-3xl text-slate-300" aria-hidden="true" />
                  <p className="mt-4 text-sm font-semibold text-slate-800">No emails synced yet</p>
                  <p className="mt-2 text-xs leading-5 text-slate-500">
                    Click Sync Gmail to import recent conversations from your connected account.
                  </p>
                </div>
              ) : (
                conversations.map((conversation) => {
                  const connection = connections.find(
                    (item) => item.id === conversation.connectionId,
                  )
                  const counterpart = displayParticipant(
                    conversation,
                    connection?.externalAccountId ?? undefined,
                  )
                  const active = selectedId === conversation.id

                  return (
                    <button
                      key={conversation.id}
                      type="button"
                      onClick={() => {
                        setSelectedId(conversation.id)
                        setMobileConversationOpen(true)
                      }}
                      className={`w-full border-b border-slate-100 px-4 py-4 text-left transition ${
                        active ? 'bg-indigo-50' : 'bg-white hover:bg-slate-50'
                      }`}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <p className="truncate text-sm font-semibold text-slate-900">
                          {counterpart}
                        </p>
                        <span className="shrink-0 text-[11px] text-slate-400">
                          {formatDate(conversation.lastMessageAt)}
                        </span>
                      </div>
                      <p className="mt-1 truncate text-xs font-medium text-slate-700">
                        {conversation.subject || '(no subject)'}
                      </p>
                      <div className="mt-1 flex items-center gap-2">
                        <p className="min-w-0 flex-1 truncate text-xs text-slate-500">
                          {conversation.lastMessagePreview || 'No preview available'}
                        </p>
                        {conversation.unreadCount > 0 && (
                          <span className="inline-flex min-w-5 items-center justify-center rounded-full bg-indigo-500 px-1.5 py-0.5 text-[10px] font-bold text-white">
                            {conversation.unreadCount}
                          </span>
                        )}
                      </div>
                      {connection?.externalAccountId && (
                        <p className="mt-2 truncate text-[10px] font-medium uppercase tracking-wide text-slate-400">
                          {connection.externalAccountId}
                        </p>
                      )}
                    </button>
                  )
                })
              )}

              {!loading && currentPage + 1 < totalPages && (
                <div className="p-3">
                  <button
                    type="button"
                    className="secondary-button w-full justify-center"
                    onClick={() => void loadConversations(currentPage + 1, true)}
                    disabled={loadingMore}
                  >
                    {loadingMore ? 'Loading...' : 'Load more'}
                  </button>
                </div>
              )}
            </div>
          </aside>

          <main
            className={`${mobileConversationOpen ? 'block' : 'hidden md:block'} min-w-0 bg-slate-50/40`}
          >
            {!selectedConversation ? (
              <div className="flex min-h-[560px] items-center justify-center px-6 text-center">
                <div>
                  <FiMail className="mx-auto text-4xl text-slate-300" aria-hidden="true" />
                  <p className="mt-4 text-sm font-semibold text-slate-800">Select a conversation</p>
                  <p className="mt-2 text-xs text-slate-500">Email messages will appear here.</p>
                </div>
              </div>
            ) : (
              <>
                <header className="flex items-start gap-3 border-b border-slate-200 bg-white px-4 py-4 sm:px-5">
                  <button
                    type="button"
                    className="secondary-button shrink-0 px-3 md:hidden"
                    onClick={() => setMobileConversationOpen(false)}
                    aria-label="Back to conversations"
                  >
                    <FiArrowLeft aria-hidden="true" />
                  </button>
                  <div className="min-w-0">
                    <p className="truncate text-sm font-bold text-slate-950">
                      {selectedConversation.subject || '(no subject)'}
                    </p>
                    <p className="mt-1 truncate text-xs text-slate-500">
                      {displayParticipant(selectedConversation, ownAddress)}
                      {ownAddress ? ` · ${ownAddress}` : ''}
                    </p>
                  </div>
                </header>

                <div className="max-h-[calc(100dvh-290px)] space-y-4 overflow-y-auto p-3 sm:p-5 md:max-h-[calc(100dvh-210px)]">
                  {messagesLoading ? (
                    <p className="py-10 text-center text-sm text-slate-500">Loading messages...</p>
                  ) : messages.length === 0 ? (
                    <p className="py-10 text-center text-sm text-slate-500">
                      No messages found in this conversation.
                    </p>
                  ) : (
                    messages.map((message) => (
                      <article
                        key={message.id}
                        className={`w-full max-w-3xl rounded-xl border p-3 sm:p-4 ${
                          message.direction === 'OUTBOUND'
                            ? 'ml-auto border-indigo-100 bg-indigo-50'
                            : 'border-slate-200 bg-white'
                        }`}
                      >
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div className="flex items-center gap-2">
                            <span className="text-xs font-bold text-slate-800">
                              {message.direction === 'OUTBOUND' ? 'You' : 'Received'}
                            </span>
                            {!message.read && (
                              <span className="rounded-full bg-indigo-100 px-2 py-0.5 text-[10px] font-bold text-indigo-600">
                                UNREAD
                              </span>
                            )}
                          </div>
                          <time className="text-[11px] text-slate-400">
                            {message.sentAt ? new Date(message.sentAt).toLocaleString() : ''}
                          </time>
                        </div>

                        <p className="mt-3 text-xs font-semibold text-slate-700">
                          {messageTitle(message)}
                        </p>
                        {message.bodyHtml ||
                        (isHtmlBody(message.bodyText) ? message.bodyText : null) ? (
                          <div className="mt-3 overflow-hidden rounded-xl bg-white">
                            <EmailHtmlFrame
                              html={
                                message.bodyHtml ||
                                (isHtmlBody(message.bodyText) ? message.bodyText || '' : '')
                              }
                              title={message.subject || 'Email message'}
                            />
                          </div>
                        ) : (
                          <pre className="mt-3 whitespace-pre-wrap break-words font-sans text-sm leading-6 text-slate-700">
                            {message.bodyText || message.snippet || 'No message body available.'}
                          </pre>
                        )}

                        {message.attachments.length > 0 && (
                          <div className="mt-4 flex flex-wrap gap-2">
                            {message.attachments.map((attachment) => (
                              <span
                                key={attachment.id}
                                className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs text-slate-600"
                              >
                                {attachment.filename || 'Attachment'}
                              </span>
                            ))}
                          </div>
                        )}
                      </article>
                    ))
                  )}
                </div>
              </>
            )}
          </main>
        </div>
      </div>
    </section>
  )
}
