import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import {
  FiArrowLeft,
  FiCornerUpLeft,
  FiMail,
  FiRefreshCw,
  FiSend,
  FiShare2,
  FiUsers,
} from 'react-icons/fi'
import ConversationList, { conversationParticipant } from '../../components/inbox/ConversationList'
import EmailHtmlFrame from '../../components/inbox/EmailHtmlFrame'
import { asyncJobApi } from '../../features/asyncJob/asyncJobApi'
import type { AsyncJob } from '../../features/asyncJob/types'
import { channelApi } from '../../features/channel/channelApi'
import { gmailApi } from '../../features/channel/gmailApi'
import { microsoftApi } from '../../features/channel/microsoftApi'
import type { ChannelConnection } from '../../features/channel/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { inboxApi } from '../../features/inbox/inboxApi'
import type { InboxConversation, InboxMessage } from '../../features/inbox/types'
import { realtimeClient } from '../../features/realtime/realtimeClient'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId } from '../../store/workspace/workspaceSlice'

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

const recipientInput = (values: string[]) => values.join(', ')

const parseRecipients = (value: string) =>
  Array.from(
    new Set(
      value
        .split(/[;,]/)
        .map((item) => item.trim().toLowerCase())
        .filter(Boolean),
    ),
  )

const replySubject = (subject?: string | null) => {
  const value = subject?.trim() || ''
  return /^re:/i.test(value) ? value : `Re: ${value || '(no subject)'}`
}

const forwardSubject = (subject?: string | null) => {
  const value = subject?.trim() || ''
  return /^fwd?:/i.test(value) ? value : `Fwd: ${value || '(no subject)'}`
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
  const selectedConversationId = searchParams.get('conversationId')
  const composeOpen = searchParams.get('compose') === '1'
  const [connections, setConnections] = useState<ChannelConnection[]>([])
  const [conversations, setConversations] = useState<InboxConversation[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(selectedConversationId)
  const [mobileConversationOpen, setMobileConversationOpen] = useState(Boolean(selectedConversationId))
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
  const [composeMode, setComposeMode] = useState<'compose' | 'reply' | 'replyAll' | 'forward'>(
    'compose',
  )
  const [composeTo, setComposeTo] = useState('')
  const [composeCc, setComposeCc] = useState('')
  const [composeBcc, setComposeBcc] = useState('')
  const [composeSubject, setComposeSubject] = useState('')
  const [composeBody, setComposeBody] = useState('')
  const [composeThreadId, setComposeThreadId] = useState('')
  const [composeInReplyTo, setComposeInReplyTo] = useState('')
  const [composeReferences, setComposeReferences] = useState('')
  const [composeProviderMessageId, setComposeProviderMessageId] = useState('')
  const initialSyncRange = useMemo(() => defaultSyncRange(), [])
  const [syncFrom, setSyncFrom] = useState(initialSyncRange.from)
  const [syncTo, setSyncTo] = useState(initialSyncRange.to)
  const [syncJobs, setSyncJobs] = useState<Record<string, AsyncJob>>({})
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [realtimeConnected, setRealtimeConnected] = useState(realtimeClient.isConnected())
  const inboxRefreshTimerRef = useRef<number | null>(null)

  const selectedConnection = useMemo(
    () => connections.find((connection) => connection.id === selectedConnectionId) ?? null,
    [connections, selectedConnectionId],
  )

  const selectedConversation = useMemo(
    () => conversations.find((conversation) => conversation.id === selectedId) ?? null,
    [conversations, selectedId],
  )

  const ownAddress = useMemo(() => {
    if (selectedConversation) {
      return (
        connections.find((connection) => connection.id === selectedConversation.connectionId)
          ?.externalAccountId ?? undefined
      )
    }
    return selectedConnection?.externalAccountId ?? undefined
  }, [connections, selectedConnection, selectedConversation])

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
            params.delete('conversationId')
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

        if (!append && !selectedConversationId && inbox.items[0]) {
          const params = new URLSearchParams(searchParams)
          params.set('conversationId', inbox.items[0].id)
          setSearchParams(params, { replace: true })
        }
      } catch (requestError) {
        setError(apiErrorMessage(requestError, 'Unable to load email inbox'))
      } finally {
        setLoading(false)
        setLoadingMore(false)
      }
    },
    [companyId, searchParams, selectedConnectionId, selectedConversationId, setSearchParams],
  )

  const scheduleInboxRefresh = useCallback(() => {
    if (inboxRefreshTimerRef.current !== null) return
    inboxRefreshTimerRef.current = window.setTimeout(() => {
      inboxRefreshTimerRef.current = null
      void loadConversations(0, false)
    }, 1000)
  }, [loadConversations])

  useEffect(
    () => () => {
      if (inboxRefreshTimerRef.current !== null) {
        window.clearTimeout(inboxRefreshTimerRef.current)
      }
    },
    [],
  )

  useEffect(() => {
    setMobileConversationOpen(Boolean(selectedConversationId))
    setSelectedId(selectedConversationId)
  }, [selectedConversationId])

  useEffect(() => {
    if (!selectedConversationId) {
      setMobileConversationOpen(false)
    }
    void loadConversations(0, false)
  }, [companyId, selectedConnectionId, loadConversations, selectedConversationId])

  useEffect(() => {
    if (
      !companyId ||
      !selectedConnectionId ||
      selectedConnection?.providerType !== 'GMAIL'
    ) {
      setSyncJobs({})
      return
    }

    let active = true
    gmailApi
      .activeSync(companyId, selectedConnectionId)
      .then((job) => {
        if (!active) return
        setSyncJobs(job ? { [job.jobId]: job } : {})
        if (job && job.completedUnits > 0) {
          scheduleInboxRefresh()
        }
      })
      .catch(() => {
        if (active) setSyncJobs({})
      })

    return () => {
      active = false
    }
  }, [companyId, selectedConnectionId, selectedConnection?.providerType, scheduleInboxRefresh])

  useEffect(() => {
    const gmailResult = searchParams.get('gmail')
    const microsoftResult = searchParams.get('microsoft')
    if (!gmailResult && !microsoftResult) return

    if (gmailResult === 'connected') {
      setNotice('Gmail connected successfully')
    } else if (gmailResult === 'cancelled') {
      setNotice('Gmail connection was cancelled')
    } else if (gmailResult) {
      setError('Unable to connect Gmail. Please try again.')
    }

    if (microsoftResult === 'connected') {
      setNotice('Microsoft 365 connected successfully')
    } else if (microsoftResult === 'cancelled') {
      setNotice('Microsoft 365 connection was cancelled')
    } else if (microsoftResult) {
      setError('Unable to connect Microsoft 365. Please try again.')
    }

    const params = new URLSearchParams(searchParams)
    params.delete('gmail')
    params.delete('microsoft')
    setSearchParams(params, { replace: true })
    void loadConversations(0, false)
  }, [loadConversations, searchParams, setSearchParams])

  useEffect(() => {
    if (!companyId || !selectedConversationId) {
      setMessages([])
      return
    }

    let active = true
    setMessagesLoading(true)
    inboxApi
      .messages(companyId, selectedConversationId)
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
  }, [companyId, selectedConversationId])

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

  useEffect(() => realtimeClient.subscribeConnection(setRealtimeConnected), [])

  useEffect(
    () =>
      realtimeClient.subscribe('inbox.email.changed', () => {
        scheduleInboxRefresh()
      }),
    [scheduleInboxRefresh],
  )

  useEffect(() => {
    return realtimeClient.subscribe<AsyncJob>('async.job.progress', (event) => {
      const job = event.payload
      setSyncJobs((current) => {
        if (!current[job.jobId]) return current
        return { ...current, [job.jobId]: job }
      })

      if (job.completedUnits > 0) {
        scheduleInboxRefresh()
      }
      if (job.status === 'FAILED') {
        setError(job.errorMessage || 'Gmail sync failed')
      }
    })
  }, [scheduleInboxRefresh])

  useEffect(() => {
    if (activeSyncJobs.length === 0 || realtimeConnected) return

    const timer = window.setInterval(() => {
      void Promise.all(
        activeSyncJobs.map((job) =>
          asyncJobApi
            .get(job.jobId)
            .then((latest) => setSyncJobs((current) => ({ ...current, [latest.jobId]: latest })))
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
      const queued = await gmailApi.sync(companyId, selectedConnection.id, {
        fromDate: syncFrom,
        toDate: syncTo,
      })
      const job = await asyncJobApi.get(queued.jobId)
      setSyncJobs({ [job.jobId]: job })
      setShowSyncDialog(false)
      setNotice(
        `Gmail sync started for ${selectedConnection.externalAccountId || selectedConnection.name}.`,
      )
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Unable to queue Gmail sync'))
    } finally {
      setSyncing(false)
    }
  }

  const syncMicrosoft = async () => {
    if (!companyId || syncing || !selectedConnection) return
    if (
      selectedConnection.providerType !== 'MICROSOFT' ||
      selectedConnection.status !== 'ACTIVE'
    ) {
      setNotice('The selected email account is not an active Microsoft 365 connection.')
      return
    }

    setSyncing(true)
    setError(null)
    setNotice(null)
    try {
      await microsoftApi.sync(companyId, selectedConnection.id)
      setNotice(
        `Microsoft 365 sync requested for ${selectedConnection.externalAccountId || selectedConnection.name}.`,
      )
      scheduleInboxRefresh()
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Unable to request Microsoft 365 sync'))
    } finally {
      setSyncing(false)
    }
  }

  const syncSelected = () => {
    if (!selectedConnection) {
      setNotice('Select an email account before syncing.')
      return
    }
    if (selectedConnection.providerType === 'GMAIL') {
      setShowSyncDialog(true)
      return
    }
    if (selectedConnection.providerType === 'MICROSOFT') {
      void syncMicrosoft()
    }
  }

  const selectConversation = (conversation: InboxConversation) => {
    const params = new URLSearchParams(searchParams)
    params.set('conversationId', conversation.id)
    params.delete('compose')
    setSearchParams(params)
    setSelectedId(conversation.id)
    setMobileConversationOpen(true)
  }

  const resetCompose = () => {
    setComposeMode('compose')
    setComposeTo('')
    setComposeCc('')
    setComposeBcc('')
    setComposeSubject('')
    setComposeBody('')
    setComposeThreadId('')
    setComposeInReplyTo('')
    setComposeReferences('')
    setComposeProviderMessageId('')
  }

  const showCompose = () => {
    if (!selectedConnection) {
      setNotice('Select an email account before composing.')
      return
    }
    const params = new URLSearchParams(searchParams)
    params.set('connectionId', selectedConnection.id)
    params.set('compose', '1')
    setSearchParams(params)
  }

  const closeCompose = () => {
    const params = new URLSearchParams(searchParams)
    params.delete('compose')
    setSearchParams(params, { replace: true })
  }

  const openCompose = () => {
    resetCompose()
    showCompose()
  }

  const replyRecipients = (message: InboxMessage, replyAll: boolean) => {
    const own = selectedConnection?.externalAccountId?.trim().toLowerCase()
    const inboundPrimary =
      message.replyToAddress?.trim().toLowerCase() || message.fromAddress?.trim().toLowerCase()
    const outboundPrimary = message.to.find((address) => address.toLowerCase() !== own)
    const primary = message.direction === 'INBOUND' ? inboundPrimary : outboundPrimary

    const candidates = replyAll
      ? [
          primary,
          message.fromAddress,
          message.replyToAddress,
          ...message.to,
          ...message.cc,
        ]
      : [primary]

    const unique = Array.from(
      new Set(
        candidates
          .filter((address): address is string => Boolean(address?.trim()))
          .map((address) => address.trim().toLowerCase())
          .filter((address) => address !== own),
      ),
    )

    return {
      to: unique.slice(0, 1),
      cc: replyAll ? unique.slice(1) : [],
    }
  }

  const openReply = (message: InboxMessage, replyAll: boolean) => {
    const recipients = replyRecipients(message, replyAll)
    if (recipients.to.length === 0) {
      setError('Unable to determine a recipient for this reply.')
      return
    }

    setComposeMode(replyAll ? 'replyAll' : 'reply')
    setComposeTo(recipientInput(recipients.to))
    setComposeCc(recipientInput(recipients.cc))
    setComposeBcc('')
    setComposeSubject(replySubject(message.subject || selectedConversation?.subject))
    setComposeBody('')
    setComposeThreadId(selectedConversation?.externalConversationId || '')
    setComposeInReplyTo(message.internetMessageId || '')
    setComposeReferences(
      [message.references?.trim(), message.internetMessageId?.trim()].filter(Boolean).join(' '),
    )
    setComposeProviderMessageId(message.externalMessageId)
    showCompose()
  }

  const openForward = (message: InboxMessage) => {
    const sentAt = message.sentAt ? new Date(message.sentAt).toLocaleString() : ''
    const to = message.to.join(', ')
    const originalBody = message.bodyText || message.snippet || ''

    setComposeMode('forward')
    setComposeTo('')
    setComposeCc('')
    setComposeBcc('')
    setComposeSubject(forwardSubject(message.subject))
    setComposeThreadId('')
    setComposeInReplyTo('')
    setComposeReferences('')
    setComposeProviderMessageId('')
    setComposeBody(
      [
        '',
        '',
        '---------- Forwarded message ----------',
        message.fromAddress ? `From: ${message.fromAddress}` : '',
        sentAt ? `Date: ${sentAt}` : '',
        message.subject ? `Subject: ${message.subject}` : '',
        to ? `To: ${to}` : '',
        '',
        originalBody,
      ]
        .filter((line, index) => line !== '' || index < 2 || index > 6)
        .join('\n'),
    )
    showCompose()
  }

  const sendEmail = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!companyId || !selectedConnection || sending) return
    if (!['GMAIL', 'MICROSOFT'].includes(selectedConnection.providerType)) {
      setError('Sending is not available for the selected email provider.')
      return
    }

    const to = parseRecipients(composeTo)
    const cc = parseRecipients(composeCc)
    const bcc = parseRecipients(composeBcc)
    if (to.length === 0 && cc.length === 0 && bcc.length === 0) {
      setError('Add at least one To, CC, or BCC recipient.')
      return
    }

    setSending(true)
    setError(null)
    setNotice(null)
    try {
      const sent =
        selectedConnection.providerType === 'GMAIL'
          ? await gmailApi.send(companyId, selectedConnection.id, {
              to,
              cc,
              bcc,
              subject: composeSubject.trim(),
              bodyText: composeBody,
              threadId: composeThreadId || null,
              inReplyTo: composeInReplyTo || null,
              references: composeReferences || null,
            })
          : await microsoftApi.send(companyId, selectedConnection.id, {
              to,
              cc,
              bcc,
              subject: composeSubject.trim(),
              bodyText: composeBody,
              replyToMessageId:
                composeMode === 'reply' || composeMode === 'replyAll'
                  ? composeProviderMessageId || null
                  : null,
              replyAll: composeMode === 'replyAll',
            })
      resetCompose()
      closeCompose()
      setNotice(
        `Email sent from ${sent.from} to ${[...sent.to, ...sent.cc, ...sent.bcc].join(', ')}.`,
      )
      await loadConversations(0, false)
      if (selectedConversationId) {
        const latestMessages = await inboxApi.messages(companyId, selectedConversationId)
        setMessages(latestMessages)
      }
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Unable to send email'))
    } finally {
      setSending(false)
    }
  }

  const canSendSelectedEmail =
    can(PERMISSIONS.EMAIL_SEND) &&
    (selectedConnection?.providerType === 'GMAIL' ||
      selectedConnection?.providerType === 'MICROSOFT')

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
    <section className="channel-workspace-page">
      <div className="channel-workspace-toolbar">
        <div className="min-w-0 lg:hidden">
          <p className="eyebrow">EMAIL</p>
          <p className="mt-1 truncate text-xs font-semibold text-slate-700">
            {selectedConnection?.externalAccountId || selectedConnection?.name || 'Inbox'}
          </p>
        </div>
        <div className="ml-auto flex w-full flex-wrap items-center justify-end gap-2 sm:w-auto">
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
              onClick={syncSelected}
              disabled={
                !selectedConnection ||
                syncing ||
                (selectedConnection.providerType === 'GMAIL' && activeSyncJobs.length > 0) ||
                !['GMAIL', 'MICROSOFT'].includes(selectedConnection.providerType)
              }
            >
              <FiMail aria-hidden="true" />
              {syncing
                ? 'Syncing...'
                : activeSyncJobs.length > 0
                  ? 'Syncing Gmail...'
                  : selectedConnection?.providerType === 'MICROSOFT'
                    ? 'Sync Microsoft'
                    : 'Sync Gmail'}
            </button>
            {canSendSelectedEmail && (
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

      <div className="channel-workspace-body">
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
                {trackedSyncJobs.at(-1)?.totalUnits ? (
                  <p className="mt-1 text-[11px] text-slate-400">
                    {trackedSyncJobs.at(-1)?.completedUnits.toLocaleString()} of{' '}
                    {trackedSyncJobs.at(-1)?.totalUnits.toLocaleString()} processed
                  </p>
                ) : null}
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
              aria-labelledby="email-compose-title"
            >
              <p className="eyebrow">
                {composeMode === 'reply'
                  ? 'REPLY'
                  : composeMode === 'replyAll'
                    ? 'REPLY ALL'
                    : composeMode === 'forward'
                      ? 'FORWARD'
                      : 'NEW EMAIL'}
              </p>
              <h2 id="email-compose-title" className="mt-2 text-lg font-bold text-slate-950">
                {composeMode === 'compose' ? 'Compose email' : composeSubject || 'Email'}
              </h2>
              <p className="mt-2 text-xs text-slate-500">
                From: {selectedConnection?.externalAccountId || selectedConnection?.name || 'Email'}
              </p>

              <div className="mt-5 space-y-4">
                <label className="block text-xs font-semibold text-slate-600">
                  To
                  <input
                    type="text"
                    className="form-input mt-1.5"
                    value={composeTo}
                    onChange={(event) => setComposeTo(event.target.value)}
                    placeholder="one@example.com, two@example.com"
                    autoFocus
                  />
                </label>
                <div className="grid gap-4 sm:grid-cols-2">
                  <label className="block text-xs font-semibold text-slate-600">
                    CC
                    <input
                      type="text"
                      className="form-input mt-1.5"
                      value={composeCc}
                      onChange={(event) => setComposeCc(event.target.value)}
                      placeholder="cc@example.com"
                    />
                  </label>
                  <label className="block text-xs font-semibold text-slate-600">
                    BCC
                    <input
                      type="text"
                      className="form-input mt-1.5"
                      value={composeBcc}
                      onChange={(event) => setComposeBcc(event.target.value)}
                      placeholder="bcc@example.com"
                    />
                  </label>
                </div>
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
                  disabled={
                    sending ||
                    (!composeTo.trim() && !composeCc.trim() && !composeBcc.trim()) ||
                    !composeBody.trim()
                  }
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

        <div
          className={`${mobileConversationOpen ? 'hidden' : 'block'} panel mb-4 overflow-hidden lg:hidden`}
        >
          <div className="border-b border-slate-100 px-4 py-3">
            <p className="text-xs font-bold text-slate-900">Chats</p>
            <p className="mt-1 text-[11px] text-slate-500">
              {conversations.length} of {totalElements} loaded
            </p>
          </div>
          <ConversationList
            conversations={conversations}
            connections={connections}
            selectedId={selectedConversationId}
            loading={loading}
            loadingMore={loadingMore}
            totalElements={totalElements}
            hasMore={currentPage + 1 < totalPages}
            onSelect={selectConversation}
            onLoadMore={() => void loadConversations(currentPage + 1, true)}
          />
        </div>

        <main
          className={`${mobileConversationOpen ? 'block' : 'hidden lg:block'} channel-message-surface`}
        >
          {!selectedConversationId ? (
            <div className="flex min-h-[calc(100dvh-210px)] items-center justify-center px-6 text-center">
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
                  className="secondary-button shrink-0 px-3 lg:hidden"
                  onClick={() => setMobileConversationOpen(false)}
                  aria-label="Back to conversations"
                >
                  <FiArrowLeft aria-hidden="true" />
                </button>
                <div className="min-w-0">
                  <p className="truncate text-sm font-bold text-slate-950">
                    {selectedConversation?.subject || messages[0]?.subject || '(no subject)'}
                  </p>
                  <p className="mt-1 truncate text-xs text-slate-500">
                    {selectedConversation
                      ? conversationParticipant(selectedConversation, ownAddress)
                      : ownAddress || selectedConnection?.externalAccountId || 'Email'}
                    {selectedConversation && ownAddress ? ` · ${ownAddress}` : ''}
                  </p>
                </div>
              </header>

              <div className="channel-message-scroll space-y-4 p-3 sm:p-5">
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
                      className={`w-full max-w-4xl rounded-xl border p-3 sm:p-4 ${
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

                      {canSendSelectedEmail && (
                        <div className="mt-4 flex flex-wrap gap-2 border-t border-slate-100 pt-3">
                          <button
                            type="button"
                            className="secondary-button gap-2 px-3 py-2 text-xs"
                            onClick={() => openReply(message, false)}
                          >
                            <FiCornerUpLeft aria-hidden="true" />
                            Reply
                          </button>
                          <button
                            type="button"
                            className="secondary-button gap-2 px-3 py-2 text-xs"
                            onClick={() => openReply(message, true)}
                          >
                            <FiUsers aria-hidden="true" />
                            Reply all
                          </button>
                          <button
                            type="button"
                            className="secondary-button gap-2 px-3 py-2 text-xs"
                            onClick={() => openForward(message)}
                          >
                            <FiShare2 aria-hidden="true" />
                            Forward
                          </button>
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
