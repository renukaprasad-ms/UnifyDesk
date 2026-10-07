import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import {
  FiArrowLeft,
  FiCheckCircle,
  FiCornerUpLeft,
  FiMail,
  FiPaperclip,
  FiRefreshCw,
  FiSend,
  FiShare2,
  FiUsers,
  FiX,
} from 'react-icons/fi'
import ConversationList from '../../components/inbox/ConversationList'
import EmailProviderPicker from '../../components/email/EmailProviderPicker'
import { conversationParticipant } from '../../components/inbox/conversationListUtils'
import WhatsAppInbox from '../../components/whatsapp/WhatsAppInbox'
import EmailHtmlFrame from '../../components/inbox/EmailHtmlFrame'
import { asyncJobApi } from '../../features/asyncJob/asyncJobApi'
import type { AsyncJob } from '../../features/asyncJob/types'
import { gmailApi } from '../../features/channel/gmailApi'
import { imapSmtpApi } from '../../features/channel/imapSmtpApi'
import { microsoftApi } from '../../features/channel/microsoftApi'
import type {
  EmailFolder,
  EmailSyncMode,
  EmailSyncRequest,
} from '../../features/channel/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { inboxApi } from '../../features/inbox/inboxApi'
import { mediaApi } from '../../features/media/mediaApi'
import type {
  InboxConversation,
  InboxEmailChangedEvent,
  InboxMessage,
  InboxParticipant,
} from '../../features/inbox/types'
import { realtimeClient } from '../../features/realtime/realtimeClient'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useSnackbar } from '../../components/feedback/SnackbarProvider'
import {
  applyConversationReadState,
  applyMessageReadState,
  applyRealtimeSnapshot,
  clearConversationDetail,
  fetchEmailConnections,
  fetchInboxConversationDetail,
  fetchInboxConversations,
} from '../../store/inbox/inboxSlice'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import {
  selectSelectedCompanyId,
  selectWorkspaceEpoch,
} from '../../store/workspace/workspaceSlice'
import {
  isWorkspaceEpochCurrent,
  workspaceEpochToken,
} from '../../features/workspace/workspaceEpoch'
import { Skeleton } from '../../components/ui/Skeleton'
import ProgressBar from '../../components/ui/ProgressBar'
import StatusBadge from '../../components/ui/StatusBadge'

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

const createContactHref = (participant: InboxParticipant) => {
  const params = new URLSearchParams({ new: '1' })
  if (participant.participantType === 'EMAIL_ADDRESS') params.set('email', participant.address)
  else params.set('phone', participant.address)
  return `/contacts?${params.toString()}`
}

type ComposeAttachment = {
  clientId: string
  file: File
  mediaId?: string
  progress: number
  status: 'UPLOADING' | 'READY' | 'FAILED'
  error?: string
}

const formatFileSize = (size: number) => {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

const defaultSyncRange = () => {
  const to = new Date()
  const from = new Date()
  from.setDate(from.getDate() - 30)
  return { from: localDateInput(from), to: localDateInput(to) }
}

function EmailInboxView() {
  const dispatch = useAppDispatch()
  const companyId = useAppSelector(selectSelectedCompanyId)
  const workspaceEpoch = useAppSelector(selectWorkspaceEpoch)
  const workspaceScopeRef = useRef({ companyId, epoch: workspaceEpoch })
  workspaceScopeRef.current = { companyId, epoch: workspaceEpoch }
  const inbox = useAppSelector((state) => state.inbox)
  const { can } = useIam()
  const snackbar = useSnackbar()
  const [searchParams, setSearchParams] = useSearchParams()
  const selectedConnectionId = searchParams.get('connectionId')
  const selectedConversationId = searchParams.get('conversationId')
  const selectedFolder = searchParams.get('folder') === 'SENT' ? 'SENT' : 'INBOX'
  const composeOpen = searchParams.get('compose') === '1'
  const [mobileConversationOpen, setMobileConversationOpen] = useState(
    Boolean(selectedConversationId),
  )
  const [syncing, setSyncing] = useState(false)
  const [sending, setSending] = useState(false)
  const [readUpdatingConversationId, setReadUpdatingConversationId] = useState<string | null>(null)
  const [readUpdatingMessageId, setReadUpdatingMessageId] = useState<string | null>(null)
  const [markAllStarting, setMarkAllStarting] = useState(false)
  const [markAllJob, setMarkAllJob] = useState<AsyncJob | null>(null)
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
  const [composeAttachments, setComposeAttachments] = useState<ComposeAttachment[]>([])
  const cancelledComposeAttachmentsRef = useRef(new Set<string>())
  const initialSyncRange = useMemo(() => defaultSyncRange(), [])
  const [syncFrom, setSyncFrom] = useState(initialSyncRange.from)
  const [syncTo, setSyncTo] = useState(initialSyncRange.to)
  const [syncMode, setSyncMode] = useState<EmailSyncMode>('DATE_RANGE')
  const [syncFolder, setSyncFolder] = useState<EmailFolder>('INBOX')
  const [syncMessageCount, setSyncMessageCount] = useState(100)
  const [syncJobs, setSyncJobs] = useState<Record<string, AsyncJob>>({})
  const [realtimeConnected, setRealtimeConnected] = useState(realtimeClient.isConnected())
  const completedSyncRefreshesRef = useRef(new Set<string>())
  const trackedSyncJobIdsRef = useRef(new Set<string>())
  const autoReadConversationRef = useRef<string | null>(null)
  const autoReadAttemptedRef = useRef(new Set<string>())
  const realtimeBatchRef = useRef<{
    conversationIds: Set<string>
    messageIds: Set<string>
    timer: number | null
    fullRefresh: boolean
  }>({
    conversationIds: new Set(),
    messageIds: new Set(),
    timer: null,
    fullRefresh: false,
  })

  const connections =
    inbox.connectionsCompanyId === companyId && inbox.connectionsEpoch === workspaceEpoch
      ? inbox.connections.filter(
          (connection) =>
            connection.status === 'ACTIVE' && connection.capabilities.includes('INBOX'),
        )
      : []
  const sameConversationScope =
    inbox.scopeCompanyId === companyId &&
    inbox.scopeEpoch === workspaceEpoch &&
    inbox.scopeConnectionId === selectedConnectionId &&
    inbox.scopeFolder === selectedFolder
  const conversations = sameConversationScope ? inbox.conversations : []
  const loading = sameConversationScope && inbox.conversationsStatus === 'loading'
  const loadingMore = sameConversationScope && inbox.loadingMore
  const currentPage = sameConversationScope ? inbox.conversationPage : 0
  const totalPages = sameConversationScope ? inbox.conversationTotalPages : 0
  const totalElements = sameConversationScope ? inbox.conversationTotalElements : 0
  const detailSelected =
    inbox.detailCompanyId === companyId &&
    inbox.detailEpoch === workspaceEpoch &&
    inbox.detailConversationId === selectedConversationId
  const selectedConversation = detailSelected ? inbox.selectedConversation : null
  const messages = detailSelected ? inbox.messages : []
  const messagesLoading = detailSelected && inbox.detailStatus === 'loading'

  const selectedConnection = useMemo(
    () => connections.find((connection) => connection.id === selectedConnectionId) ?? null,
    [connections, selectedConnectionId],
  )

  const selectedProviderType = selectedConnection?.providerType ?? null
  const connectionsReady =
    inbox.connectionsCompanyId === companyId &&
    inbox.connectionsEpoch === workspaceEpoch &&
    inbox.connectionsStatus === 'ready'
  const noInboxConnections = connectionsReady && connections.length === 0

  const ownAddress = useMemo(() => {
    if (selectedConversation) {
      return (
        connections.find((connection) => connection.id === selectedConversation.connectionId)
          ?.externalAccountId ?? undefined
      )
    }
    return selectedConnection?.externalAccountId ?? undefined
  }, [connections, selectedConnection, selectedConversation])

  const contactParticipant = useMemo(() => {
    if (!selectedConversation) return null
    const otherParticipants = selectedConversation.participants.filter(
      (participant) => participant.address.toLowerCase() !== ownAddress?.toLowerCase(),
    )
    return (
      otherParticipants.find((participant) => participant.contactId) ??
      otherParticipants.find((participant) => participant.contactResolution !== 'AMBIGUOUS') ??
      otherParticipants[0] ??
      null
    )
  }, [ownAddress, selectedConversation])

  const loadConversations = useCallback(
    async (page = 0, append = false, force = false) => {
      if (!companyId || !selectedConnectionId) return
      try {
        await dispatch(
          fetchInboxConversations({
            companyId,
            epoch: workspaceEpoch,
            connectionId: selectedConnectionId,
            folder: selectedFolder,
            page,
            append,
            force,
          }),
        ).unwrap()
      } catch (requestError) {
        snackbar.error(apiErrorMessage(requestError, 'Unable to load email inbox'))
      }
    },
    [companyId, dispatch, selectedConnectionId, selectedFolder, workspaceEpoch],
  )

  const refreshInbox = useCallback(
    async (includeDetail = true) => {
      if (!companyId || !selectedConnectionId) return
      await dispatch(
        fetchInboxConversations({
          companyId,
          epoch: workspaceEpoch,
          connectionId: selectedConnectionId,
          folder: selectedFolder,
          force: true,
        }),
      )
      if (includeDetail && selectedConversationId) {
        await dispatch(
          fetchInboxConversationDetail({
            companyId,
            epoch: workspaceEpoch,
            conversationId: selectedConversationId,
            force: true,
          }),
        )
      }
    },
    [
      companyId,
      dispatch,
      selectedConnectionId,
      selectedConversationId,
      selectedFolder,
      workspaceEpoch,
    ],
  )

  useEffect(() => {
    if (!companyId) return
    void dispatch(fetchEmailConnections({ companyId, epoch: workspaceEpoch }))
  }, [companyId, dispatch, workspaceEpoch])

  useEffect(() => {
    if (!companyId || !selectedConnectionId) return
    void dispatch(
      fetchInboxConversations({
        companyId,
        epoch: workspaceEpoch,
        connectionId: selectedConnectionId,
        folder: selectedFolder,
      }),
    )
  }, [companyId, dispatch, selectedConnectionId, selectedFolder, workspaceEpoch])

  useEffect(() => {
    setMobileConversationOpen(Boolean(selectedConversationId))
    if (!selectedConversationId) autoReadConversationRef.current = null
  }, [selectedConversationId])

  useEffect(() => {
    if (
      !companyId ||
      !selectedConnectionId ||
      !selectedProviderType ||
      !['GMAIL', 'MICROSOFT', 'IMAP_SMTP'].includes(selectedProviderType)
    ) {
      setSyncJobs({})
      return
    }

    let active = true
    const request =
      selectedProviderType === 'GMAIL'
        ? gmailApi.activeSync(companyId, selectedConnectionId)
        : selectedProviderType === 'MICROSOFT'
          ? microsoftApi.activeSync(companyId, selectedConnectionId)
          : imapSmtpApi.activeSync(companyId, selectedConnectionId)

    request
      .then((job) => {
        if (!active) return
        setSyncJobs(job ? { [job.jobId]: job } : {})
      })
      .catch(() => {
        if (active) setSyncJobs({})
      })

    return () => {
      active = false
    }
  }, [companyId, selectedConnectionId, selectedProviderType])

  useEffect(() => {
    const gmailResult = searchParams.get('gmail')
    const microsoftResult = searchParams.get('microsoft')
    if (!gmailResult && !microsoftResult) return

    if (gmailResult === 'connected') {
      snackbar.success('Gmail connected successfully')
    } else if (gmailResult === 'cancelled') {
      snackbar.info('Gmail connection was cancelled')
    } else if (gmailResult) {
      snackbar.error('Unable to connect Gmail. Please try again.')
    }

    if (microsoftResult === 'connected') {
      snackbar.success('Microsoft 365 connected successfully')
    } else if (microsoftResult === 'cancelled') {
      snackbar.info('Microsoft 365 connection was cancelled')
    } else if (microsoftResult) {
      snackbar.error('Unable to connect Microsoft 365. Please try again.')
    }

    const params = new URLSearchParams(searchParams)
    params.delete('gmail')
    params.delete('microsoft')
    setSearchParams(params, { replace: true })
    if (companyId) {
      void dispatch(fetchEmailConnections({ companyId, epoch: workspaceEpoch, force: true }))
    }
  }, [companyId, dispatch, searchParams, setSearchParams, workspaceEpoch])

  useEffect(() => {
    if (!companyId || !selectedConversationId) {
      dispatch(clearConversationDetail())
      return
    }

    void dispatch(
      fetchInboxConversationDetail({
        companyId,
        epoch: workspaceEpoch,
        conversationId: selectedConversationId,
      }),
    ).unwrap().catch((requestError) => {
      snackbar.error(apiErrorMessage(requestError, 'Unable to load conversation'))
    })
  }, [companyId, dispatch, selectedConversationId, workspaceEpoch])

  const trackedSyncJobs = useMemo(() => Object.values(syncJobs), [syncJobs])
  const activeSyncJobs = useMemo(
    () => trackedSyncJobs.filter((job) => job.status === 'QUEUED' || job.status === 'RUNNING'),
    [trackedSyncJobs],
  )
  useEffect(() => {
    trackedSyncJobIdsRef.current = new Set(trackedSyncJobs.map((job) => job.jobId))
  }, [trackedSyncJobs])
  const activeMarkAllJob =
    markAllJob?.status === 'QUEUED' || markAllJob?.status === 'RUNNING' ? markAllJob : null

  useEffect(() => realtimeClient.subscribeConnection(setRealtimeConnected), [])

  useEffect(() => {
    setMarkAllJob(null)
    setMarkAllStarting(false)
  }, [selectedConnectionId, selectedFolder])

  useEffect(() => {
    if (!companyId || !selectedConnectionId) return

    const token = workspaceEpochToken(companyId, workspaceEpoch)
    const current = () =>
      isWorkspaceEpochCurrent(
        token,
        workspaceScopeRef.current.companyId,
        workspaceScopeRef.current.epoch,
      )
    const batch = realtimeBatchRef.current

    const flush = async () => {
      batch.timer = null
      const conversationIds = Array.from(batch.conversationIds)
      const messageIds = Array.from(batch.messageIds)
      const fullRefresh = batch.fullRefresh
      batch.conversationIds.clear()
      batch.messageIds.clear()
      batch.fullRefresh = false

      if (!current()) return
      if (fullRefresh) {
        await refreshInbox(true)
        return
      }
      if (conversationIds.length === 0 && messageIds.length === 0) return

      try {
        const snapshot = await inboxApi.realtimeSnapshot(companyId, {
          connectionId: selectedConnectionId,
          folder: selectedFolder,
          detailConversationId: selectedConversationId,
          conversationIds,
          messageIds: selectedConversationId ? messageIds : [],
        })
        if (!current()) return
        dispatch(
          applyRealtimeSnapshot({
            companyId,
            epoch: workspaceEpoch,
            connectionId: selectedConnectionId,
            snapshot,
          }),
        )
      } catch (requestError) {
        if (current()) {
          snackbar.error(apiErrorMessage(requestError, 'Unable to apply realtime inbox update'))
        }
      }
    }

    const unsubscribe = realtimeClient.subscribe<InboxEmailChangedEvent>(
      'inbox.email.changed',
      (event) => {
        const change = event.payload
        if (!change || change.connectionId !== selectedConnectionId) return

        if (change.fullRefreshRequired) batch.fullRefresh = true
        change.conversationIds?.forEach((id) => batch.conversationIds.add(id))
        change.messageIds?.forEach((id) => batch.messageIds.add(id))
        change.deletedMessageIds?.forEach((id) => batch.messageIds.add(id))

        if (batch.timer === null) {
          batch.timer = window.setTimeout(() => void flush(), 100)
        }
      },
    )

    return () => {
      unsubscribe()
      if (batch.timer !== null) {
        window.clearTimeout(batch.timer)
        batch.timer = null
      }
      batch.conversationIds.clear()
      batch.messageIds.clear()
      batch.fullRefresh = false
    }
  }, [
    companyId,
    dispatch,
    refreshInbox,
    selectedConnectionId,
    selectedConversationId,
    selectedFolder,
  ])

  useEffect(() => {
    return realtimeClient.subscribe<AsyncJob>('async.job.progress', (event) => {
      const job = event.payload

      if (
        job.jobType === 'GMAIL_SYNC' ||
        job.jobType === 'MICROSOFT_SYNC' ||
        job.jobType === 'IMAP_SMTP_SYNC'
      ) {
        setSyncJobs((current) => {
          if (!current[job.jobId]) return current
          return { ...current, [job.jobId]: job }
        })
        if (
          job.status === 'COMPLETED' &&
          trackedSyncJobIdsRef.current.has(job.jobId) &&
          !completedSyncRefreshesRef.current.has(job.jobId)
        ) {
          completedSyncRefreshesRef.current.add(job.jobId)
          void refreshInbox(true)
        } else if (job.status === 'FAILED') {
          snackbar.error(job.errorMessage || 'Email sync failed')
        }
      }

      if (job.jobType === 'INBOX_READ_SYNC') {
        setMarkAllJob((current) => (current?.jobId === job.jobId ? job : current))
        if (job.status === 'COMPLETED') {
          snackbar.success('All unread messages in this inbox are now marked as read.')
        } else if (job.status === 'FAILED') {
          snackbar.error(job.errorMessage || 'Unable to mark all messages as read')
        }
      }
    })
  }, [refreshInbox])

  useEffect(() => {
    if (activeSyncJobs.length === 0 || realtimeConnected) return

    const timer = window.setInterval(() => {
      void Promise.all(
        activeSyncJobs.map((job) =>
          asyncJobApi
            .get(job.jobId)
            .then((latest) => {
              setSyncJobs((current) => ({ ...current, [latest.jobId]: latest }))
              if (
                latest.status === 'COMPLETED' &&
                !completedSyncRefreshesRef.current.has(latest.jobId)
              ) {
                completedSyncRefreshesRef.current.add(latest.jobId)
                void refreshInbox(true)
              }
            })
            .catch(() => undefined),
        ),
      )
    }, 3000)

    return () => window.clearInterval(timer)
  }, [activeSyncJobs, realtimeConnected, refreshInbox])

  useEffect(() => {
    if (!activeMarkAllJob || realtimeConnected) return

    const timer = window.setInterval(() => {
      void asyncJobApi
        .get(activeMarkAllJob.jobId)
        .then((latest) => {
          setMarkAllJob(latest)
          if (latest.status === 'COMPLETED') {
            void refreshInbox(true)
          }
          if (latest.status === 'COMPLETED') {
            snackbar.success('All unread messages in this inbox are now marked as read.')
          } else if (latest.status === 'FAILED') {
            snackbar.error(latest.errorMessage || 'Unable to mark all messages as read')
          }
        })
        .catch(() => undefined)
    }, 3000)

    return () => window.clearInterval(timer)
  }, [activeMarkAllJob, realtimeConnected, refreshInbox])

  const openSyncDialog = () => {
    if (!selectedConnection) {
      snackbar.info('Select an email account before syncing.')
      return
    }
    if (
      selectedConnection.status !== 'ACTIVE' ||
      !['GMAIL', 'MICROSOFT', 'IMAP_SMTP'].includes(selectedConnection.providerType)
    ) {
      snackbar.info('The selected email account does not support sync.')
      return
    }

    setSyncFolder(selectedFolder)
    setSyncMode('DATE_RANGE')
    setSyncMessageCount(100)
    setShowSyncDialog(true)
  }

  const syncSelected = async () => {
    if (!companyId || syncing || !selectedConnection) return

    if (
      selectedConnection.status !== 'ACTIVE' ||
      !['GMAIL', 'MICROSOFT', 'IMAP_SMTP'].includes(selectedConnection.providerType)
    ) {
      snackbar.warning('The selected email account is not active.')
      return
    }

    if (syncMode === 'DATE_RANGE') {
      if (!syncFrom || !syncTo) {
        snackbar.warning('Choose both sync dates.')
        return
      }
      if (syncFrom > syncTo) {
        snackbar.warning('From date must be before or equal to the to date.')
        return
      }
    } else if (!Number.isInteger(syncMessageCount) || syncMessageCount < 1 || syncMessageCount > 10000) {
      snackbar.warning('Choose a message count between 1 and 10,000.')
      return
    }

    const request: EmailSyncRequest =
      syncMode === 'DATE_RANGE'
        ? {
            mode: 'DATE_RANGE',
            folder: syncFolder,
            fromDate: syncFrom,
            toDate: syncTo,
          }
        : {
            mode: 'LATEST_MESSAGES',
            folder: syncFolder,
            messageCount: syncMessageCount,
          }

    setSyncing(true)

    try {
      const queued =
        selectedConnection.providerType === 'GMAIL'
          ? await gmailApi.sync(companyId, selectedConnection.id, request)
          : selectedConnection.providerType === 'MICROSOFT'
            ? await microsoftApi.sync(companyId, selectedConnection.id, request)
            : await imapSmtpApi.sync(companyId, selectedConnection.id, request)
      const job = await asyncJobApi.get(queued.jobId)
      setSyncJobs({ [job.jobId]: job })
      setShowSyncDialog(false)
      snackbar.info(
        `Email sync started for ${selectedConnection.externalAccountId || selectedConnection.name}.`,
      )
    } catch (requestError) {
      snackbar.error(apiErrorMessage(requestError, 'Unable to queue email sync'))
    } finally {
      setSyncing(false)
    }
  }

  const changeConversationReadState = useCallback(
    async (conversation: InboxConversation, read: boolean, silent = false) => {
      if (!companyId || !can(PERMISSIONS.EMAIL_MODIFY)) return
      if (readUpdatingConversationId === conversation.id) return

      setReadUpdatingConversationId(conversation.id)

      if (read) {
        dispatch(applyConversationReadState({ conversationId: conversation.id, read: true }))
      }

      try {
        const result = await inboxApi.setConversationReadState(companyId, conversation.id, read)
        if (!silent) {
          snackbar.success(
            result.updatedMessages === 0
              ? read
                ? 'Conversation is already read.'
                : 'Conversation is already unread.'
              : read
                ? 'Conversation marked as read.'
                : 'Conversation marked as unread.',
          )
        }
      } catch (requestError) {
        if (!silent) {
          snackbar.error(
            apiErrorMessage(requestError, 'Unable to update conversation read state'),
          )
        }
      } finally {
        setReadUpdatingConversationId(null)
      }
    },
    [
      can,
      companyId,
      dispatch,
      readUpdatingConversationId,
      snackbar,
    ],
  )

  useEffect(() => {
    if (
      !selectedConversation ||
      selectedFolder !== 'INBOX' ||
      selectedConversation.unreadCount <= 0 ||
      !can(PERMISSIONS.EMAIL_MODIFY) ||
      readUpdatingConversationId === selectedConversation.id ||
      autoReadConversationRef.current === selectedConversation.id ||
      autoReadAttemptedRef.current.has(selectedConversation.id)
    ) {
      return
    }

    autoReadConversationRef.current = selectedConversation.id
    autoReadAttemptedRef.current.add(selectedConversation.id)
    void changeConversationReadState(selectedConversation, true, true).finally(() => {
      if (autoReadConversationRef.current === selectedConversation.id) {
        autoReadConversationRef.current = null
      }
    })
  }, [
    can,
    changeConversationReadState,
    readUpdatingConversationId,
    selectedConversation,
    selectedFolder,
  ])

  const changeMessageReadState = async (message: InboxMessage, read: boolean) => {
    if (!companyId || !can(PERMISSIONS.EMAIL_MODIFY)) return
    if (readUpdatingMessageId === message.id) return

    setReadUpdatingMessageId(message.id)
    try {
      await inboxApi.setMessageReadState(companyId, message.id, read)
      dispatch(applyMessageReadState({ messageId: message.id, read }))
      snackbar.success(read ? 'Message marked as read.' : 'Message marked as unread.')
    } catch (requestError) {
      snackbar.error(apiErrorMessage(requestError, 'Unable to update message read state'))
    } finally {
      setReadUpdatingMessageId(null)
    }
  }

  const markAllAsRead = async () => {
    if (
      !companyId ||
      !selectedConnection ||
      selectedFolder !== 'INBOX' ||
      !can(PERMISSIONS.EMAIL_MODIFY) ||
      markAllStarting ||
      activeMarkAllJob
    ) {
      return
    }

    setMarkAllStarting(true)
    try {
      const job = await inboxApi.markAllRead(companyId, selectedConnection.id, 'INBOX')
      setMarkAllJob(job)
      snackbar.info(
        job.status === 'COMPLETED'
          ? 'This inbox has no unread messages.'
          : 'Mark all as read started. You can keep using the inbox while it finishes.',
      )
    } catch (requestError) {
      snackbar.error(apiErrorMessage(requestError, 'Unable to start mark all as read'))
    } finally {
      setMarkAllStarting(false)
    }
  }

  const selectConversation = (conversation: InboxConversation) => {
    const params = new URLSearchParams(searchParams)
    params.set('conversationId', conversation.id)
    params.delete('compose')
    setSearchParams(params)
    setMobileConversationOpen(true)

    // Opening a conversation is enough to trigger the single automatic read-state attempt.
    // Avoid firing the same mutation here and again from the selected-conversation effect.
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
    setComposeAttachments([])
  }

  const showCompose = () => {
    if (!selectedConnection) {
      snackbar.info('Select an email account before composing.')
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

  const uploadComposeAttachment = async (clientId: string, file: File) => {
    if (!companyId) return
    const token = workspaceEpochToken(companyId, workspaceEpoch)
    let mediaId: string | undefined

    const patch = (value: Partial<ComposeAttachment>) => {
      if (
        cancelledComposeAttachmentsRef.current.has(clientId) ||
        !isWorkspaceEpochCurrent(
          token,
          workspaceScopeRef.current.companyId,
          workspaceScopeRef.current.epoch,
        )
      ) {
        return
      }
      setComposeAttachments((current) =>
        current.map((item) => (item.clientId === clientId ? { ...item, ...value } : item)),
      )
    }

    try {
      const upload = await mediaApi.initiateEmailAttachmentUpload(file, companyId)
      mediaId = upload.mediaId
      if (cancelledComposeAttachmentsRef.current.has(clientId)) {
        await mediaApi.delete(mediaId).catch(() => undefined)
        return
      }
      patch({ mediaId, progress: 1 })
      await mediaApi.uploadFileToStorage(upload, file, (progress) => patch({ progress }))
      if (cancelledComposeAttachmentsRef.current.has(clientId)) {
        await mediaApi.delete(mediaId).catch(() => undefined)
        return
      }
      await mediaApi.completeUpload(mediaId)
      patch({ progress: 100, status: 'READY', error: undefined })
    } catch (requestError) {
      patch({
        status: 'FAILED',
        error: apiErrorMessage(requestError, 'Attachment upload failed'),
      })
    }
  }

  const addComposeAttachments = (files: FileList | null) => {
    if (!files || files.length === 0) return
    const available = Math.max(0, 10 - composeAttachments.length)
    const selected = Array.from(files).slice(0, available)
    if (selected.length < files.length) {
      snackbar.warning('You can attach up to 10 files to one email.')
    }

    const items = selected.map((file) => ({
      clientId: crypto.randomUUID(),
      file,
      progress: 0,
      status: 'UPLOADING' as const,
    }))
    setComposeAttachments((current) => [...current, ...items])
    items.forEach((item) => void uploadComposeAttachment(item.clientId, item.file))
  }

  const removeComposeAttachment = (clientId: string) => {
    cancelledComposeAttachmentsRef.current.add(clientId)
    const item = composeAttachments.find((attachment) => attachment.clientId === clientId)
    setComposeAttachments((current) =>
      current.filter((attachment) => attachment.clientId !== clientId),
    )
    if (item?.mediaId) void mediaApi.delete(item.mediaId).catch(() => undefined)
  }

  const retryComposeAttachment = (clientId: string) => {
    const item = composeAttachments.find((attachment) => attachment.clientId === clientId)
    if (!item) return
    cancelledComposeAttachmentsRef.current.delete(clientId)
    if (item.mediaId) void mediaApi.delete(item.mediaId).catch(() => undefined)
    setComposeAttachments((current) =>
      current.map((attachment) =>
        attachment.clientId === clientId
          ? { ...attachment, mediaId: undefined, progress: 0, status: 'UPLOADING', error: undefined }
          : attachment,
      ),
    )
    void uploadComposeAttachment(clientId, item.file)
  }

  const discardCompose = () => {
    composeAttachments.forEach((item) => {
      cancelledComposeAttachmentsRef.current.add(item.clientId)
      if (item.mediaId) void mediaApi.delete(item.mediaId).catch(() => undefined)
    })
    resetCompose()
    closeCompose()
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
      snackbar.warning('Unable to determine a recipient for this reply.')
      return
    }

    setComposeAttachments([])
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

    setComposeAttachments([])
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
    if (!['GMAIL', 'MICROSOFT', 'IMAP_SMTP'].includes(selectedConnection.providerType)) {
      snackbar.warning('Sending is not available for the selected email provider.')
      return
    }

    const to = parseRecipients(composeTo)
    const cc = parseRecipients(composeCc)
    const bcc = parseRecipients(composeBcc)
    if (to.length === 0 && cc.length === 0 && bcc.length === 0) {
      snackbar.warning('Add at least one To, CC, or BCC recipient.')
      return
    }
    if (composeAttachments.some((attachment) => attachment.status !== 'READY')) {
      snackbar.warning('Wait for attachments to finish uploading or remove failed attachments.')
      return
    }
    const attachmentMediaIds = composeAttachments
      .map((attachment) => attachment.mediaId)
      .filter((mediaId): mediaId is string => Boolean(mediaId))

    setSending(true)
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
              attachmentMediaIds,
            })
          : selectedConnection.providerType === 'MICROSOFT'
            ? await microsoftApi.send(companyId, selectedConnection.id, {
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
                attachmentMediaIds,
              })
            : await imapSmtpApi.send(companyId, selectedConnection.id, {
                to,
                cc,
                bcc,
                subject: composeSubject.trim(),
                bodyText: composeBody,
                inReplyTo: composeInReplyTo || null,
                references: composeReferences || null,
                attachmentMediaIds,
              })
      cancelledComposeAttachmentsRef.current.clear()
      resetCompose()
      closeCompose()
      snackbar.success(
        `Email sent from ${sent.from} to ${[...sent.to, ...sent.cc, ...sent.bcc].join(', ')}.`,
      )
      if (!realtimeConnected) {
        void refreshInbox(true)
      }
    } catch (requestError) {
      snackbar.error(apiErrorMessage(requestError, 'Unable to send email'))
    } finally {
      setSending(false)
    }
  }

  const canSendSelectedEmail =
    can(PERMISSIONS.EMAIL_SEND) &&
    (selectedConnection?.providerType === 'GMAIL' ||
      selectedConnection?.providerType === 'MICROSOFT' ||
      selectedConnection?.providerType === 'IMAP_SMTP')

  if (!companyId) {
    return (
      <section className="px-6 py-8">
        <div className="panel p-8 text-center text-sm text-slate-500">
          Select a workspace to open the email inbox.
        </div>
      </section>
    )
  }

  if (noInboxConnections) {
    return (
      <section className="channel-workspace-page">
        <div className="channel-workspace-toolbar">
          <div className="min-w-0">
            <p className="eyebrow">INBOX</p>
            <p className="mt-1 truncate text-xs font-semibold text-slate-700">Email</p>
          </div>
        </div>

        <div className="channel-workspace-body">
          <main className="channel-message-surface">
            <div className="flex min-h-[calc(100dvh-210px)] items-center justify-center px-5 py-8 text-center sm:px-8">
              <div className="w-full max-w-lg rounded-2xl border border-slate-200 bg-white p-7 shadow-sm sm:p-9">
                <span className="mx-auto flex size-14 items-center justify-center rounded-2xl bg-indigo-50 text-2xl text-indigo-600">
                  <FiMail aria-hidden="true" />
                </span>
                <p className="eyebrow mt-5">EMAIL INBOX</p>
                <h1 className="mt-2 text-xl font-bold text-slate-950">Connect your email account</h1>
                <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-slate-500">
                  Bring your mailbox into UnifyDesk to read and reply to customer conversations
                  from one shared workspace.
                </p>

                <div className="mt-5 flex flex-wrap justify-center gap-2">
                  {['Gmail', 'Microsoft 365', 'IMAP + SMTP'].map((provider) => (
                    <span
                      key={provider}
                      className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-[11px] font-semibold text-slate-600"
                    >
                      {provider}
                    </span>
                  ))}
                </div>

                {can(PERMISSIONS.EMAIL_CONNECT) ? (
                  <div className="mx-auto mt-6 max-w-xs">
                    <EmailProviderPicker
                      companyId={companyId}
                      returnTo="/inbox/email"
                      label="Connect email account"
                      className="primary-button w-full justify-center gap-2"
                      buttonIcon="mail"
                      onConnected={(connection) => {
                        const params = new URLSearchParams(searchParams)
                        params.set('connectionId', connection.connectionId)
                        params.set('folder', 'INBOX')
                        setSearchParams(params)
                        void dispatch(
                          fetchEmailConnections({
                            companyId,
                            epoch: workspaceEpoch,
                            force: true,
                          }),
                        )
                      }}
                    />
                  </div>
                ) : (
                  <p className="mt-6 text-xs font-semibold text-slate-500">
                    Ask a workspace administrator to connect an email account.
                  </p>
                )}

                <p className="mt-4 text-[11px] leading-5 text-slate-400">
                  After connection, UnifyDesk will sync your conversations and this screen will
                  become your shared Inbox.
                </p>
              </div>
            </div>
          </main>
        </div>
      </section>
    )
  }

  return (
    <section className="channel-workspace-page">
      <div className="channel-workspace-toolbar">
        <div className="min-w-0 lg:hidden">
          <p className="eyebrow">{selectedFolder === 'INBOX' ? 'INBOX' : 'SENT'}</p>
          <p className="mt-1 truncate text-xs font-semibold text-slate-700">
            {selectedConnection?.externalAccountId || selectedConnection?.name || 'Inbox'}
          </p>
        </div>
        <div className="inbox-toolbar-actions ml-auto flex w-full items-center justify-end gap-2 sm:w-auto">
            <button
              type="button"
              className="secondary-button flex-1 gap-2 sm:flex-none"
              onClick={() => void refreshInbox(true)}
              disabled={loading || !selectedConnection}
            >
              <FiRefreshCw aria-hidden="true" />
              <span className="inbox-action-label">Refresh</span>
            </button>
            {selectedFolder === 'INBOX' && can(PERMISSIONS.EMAIL_MODIFY) && (
              <button
                type="button"
                className="secondary-button flex-1 gap-2 sm:flex-none"
                onClick={() => void markAllAsRead()}
                disabled={!selectedConnection || markAllStarting || Boolean(activeMarkAllJob)}
              >
                <FiCheckCircle aria-hidden="true" />
                <span className="inbox-action-label">
                  {markAllStarting || activeMarkAllJob ? 'Marking…' : 'Mark all read'}
                </span>
              </button>
            )}
            <button
              type="button"
              className="secondary-button flex-1 gap-2 sm:flex-none"
              onClick={openSyncDialog}
              disabled={
                !selectedConnection ||
                syncing ||
                activeSyncJobs.length > 0 ||
                !['GMAIL', 'MICROSOFT', 'IMAP_SMTP'].includes(selectedConnection.providerType)
              }
            >
              <FiMail aria-hidden="true" />
              <span className="inbox-action-label">
                {syncing || activeSyncJobs.length > 0 ? 'Syncing…' : 'Sync'}
              </span>
            </button>
            {canSendSelectedEmail && (
              <button
                type="button"
                className="primary-button flex-1 gap-2 sm:flex-none"
                onClick={openCompose}
              >
                <FiSend aria-hidden="true" />
                <span className="inbox-action-label">Compose</span>
              </button>
            )}
          </div>
      </div>

      <div className="channel-workspace-body">
        {markAllJob && (
          <div className="panel mb-4 p-4">
            <div className="flex items-center justify-between gap-3">
              <div className="min-w-0">
                <p className="text-sm font-bold text-slate-900">
                  {activeMarkAllJob
                    ? 'Marking inbox as read'
                    : markAllJob.status === 'COMPLETED'
                      ? 'Inbox marked as read'
                      : 'Mark all as read finished'}
                </p>
                <p className="mt-1 truncate text-xs text-slate-500">
                  {markAllJob.statusMessage || 'Preparing read-state update'}
                </p>
                {markAllJob.totalUnits > 0 && (
                  <p className="mt-1 text-[11px] text-slate-400">
                    {markAllJob.completedUnits.toLocaleString()} of{' '}
                    {markAllJob.totalUnits.toLocaleString()} messages
                  </p>
                )}
              </div>
              <div className="flex items-center gap-2">
                <StatusBadge status={markAllJob.status}>{markAllJob.status.replaceAll('_', ' ')}</StatusBadge>
                <span className="text-sm font-bold tabular-nums text-slate-700">{markAllJob.progressPercent}%</span>
              </div>
            </div>
            <ProgressBar className="mt-3" value={markAllJob.progressPercent} label="Read-state update" />
          </div>
        )}

        {composeOpen && (
          <div
            className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4"
            role="presentation"
            onMouseDown={(event) => {
              if (event.currentTarget === event.target && !sending) discardCompose()
            }}
          >
            <form
              className="mobile-sheet w-full max-w-xl rounded-2xl bg-white p-5 shadow-2xl sm:p-6"
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

                <div>
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="text-xs font-semibold text-slate-600">Attachments</p>
                    <label className="secondary-button cursor-pointer gap-2 px-3 py-2 text-xs">
                      <FiPaperclip aria-hidden="true" />
                      Attach files
                      <input
                        type="file"
                        multiple
                        className="sr-only"
                        disabled={sending || composeAttachments.length >= 10}
                        onChange={(event) => {
                          addComposeAttachments(event.target.files)
                          event.currentTarget.value = ''
                        }}
                      />
                    </label>
                  </div>

                  {composeAttachments.length > 0 && (
                    <div className="mt-2 space-y-2">
                      {composeAttachments.map((attachment) => (
                        <div
                          key={attachment.clientId}
                          className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5"
                        >
                          <div className="flex items-start gap-3">
                            <FiPaperclip
                              aria-hidden="true"
                              className="mt-0.5 shrink-0 text-slate-400"
                            />
                            <div className="min-w-0 flex-1">
                              <div className="flex items-start justify-between gap-3">
                                <div className="min-w-0">
                                  <p className="truncate text-xs font-semibold text-slate-800">
                                    {attachment.file.name}
                                  </p>
                                  <p className="mt-0.5 text-[10px] text-slate-500">
                                    {formatFileSize(attachment.file.size)}
                                    {' · '}
                                    {attachment.status === 'READY'
                                      ? 'Ready'
                                      : attachment.status === 'FAILED'
                                        ? 'Upload failed'
                                        : `${attachment.progress}% uploaded`}
                                  </p>
                                </div>
                                <button
                                  type="button"
                                  className="rounded-md p-1 text-slate-400 hover:bg-white hover:text-slate-700"
                                  aria-label={`Remove ${attachment.file.name}`}
                                  onClick={() => removeComposeAttachment(attachment.clientId)}
                                  disabled={sending}
                                >
                                  <FiX aria-hidden="true" />
                                </button>
                              </div>

                              {attachment.status === 'UPLOADING' && (
                                <ProgressBar
                                  className="mt-2"
                                  value={attachment.progress}
                                  label={`Uploading ${attachment.file.name}`}
                                />
                              )}
                              {attachment.status === 'FAILED' && (
                                <div className="mt-2 flex items-center justify-between gap-3">
                                  <p className="text-[10px] text-rose-600">
                                    {attachment.error || 'Unable to upload attachment'}
                                  </p>
                                  <button
                                    type="button"
                                    className="text-[10px] font-bold text-indigo-600 hover:text-indigo-700"
                                    onClick={() => retryComposeAttachment(attachment.clientId)}
                                    disabled={sending}
                                  >
                                    Retry
                                  </button>
                                </div>
                              )}
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                <button
                  type="button"
                  className="secondary-button justify-center"
                  disabled={sending}
                  onClick={discardCompose}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="primary-button justify-center gap-2"
                  disabled={
                    sending ||
                    composeAttachments.some((attachment) => attachment.status !== 'READY') ||
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
            className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4"
            role="presentation"
            onMouseDown={(event) => {
              if (event.currentTarget === event.target && !syncing) setShowSyncDialog(false)
            }}
          >
            <div
              className="w-full max-w-lg rounded-2xl bg-white p-5 shadow-2xl sm:p-6"
              role="dialog"
              aria-modal="true"
              aria-labelledby="email-sync-title"
            >
              <p className="eyebrow">EMAIL SYNC</p>
              <h2 id="email-sync-title" className="mt-2 text-lg font-bold text-slate-950">
                Import mailbox history
              </h2>
              <p className="mt-2 text-sm leading-6 text-slate-500">
                {selectedConnection?.externalAccountId || selectedConnection?.name || 'Email'}
                {' · '}
                {selectedConnection?.providerType === 'MICROSOFT'
                  ? 'Microsoft'
                  : selectedConnection?.providerType === 'IMAP_SMTP'
                    ? 'IMAP + SMTP'
                    : 'Gmail'}
              </p>

              <div className="mt-5">
                <p className="text-xs font-semibold text-slate-600">Folder</p>
                <div className="mt-2 grid grid-cols-2 gap-2">
                  {(['INBOX', 'SENT'] as EmailFolder[]).map((folder) => (
                    <button
                      key={folder}
                      type="button"
                      className={`rounded-xl border px-4 py-3 text-sm font-semibold transition ${
                        syncFolder === folder
                          ? 'border-indigo-400 bg-indigo-50 text-indigo-700'
                          : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50'
                      }`}
                      onClick={() => setSyncFolder(folder)}
                    >
                      {folder === 'INBOX' ? 'Inbox' : 'Sent'}
                    </button>
                  ))}
                </div>
              </div>

              <div className="mt-5">
                <p className="text-xs font-semibold text-slate-600">Import by</p>
                <div className="mt-2 grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    className={`rounded-xl border px-4 py-3 text-sm font-semibold transition ${
                      syncMode === 'DATE_RANGE'
                        ? 'border-indigo-400 bg-indigo-50 text-indigo-700'
                        : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50'
                    }`}
                    onClick={() => setSyncMode('DATE_RANGE')}
                  >
                    Date range
                  </button>
                  <button
                    type="button"
                    className={`rounded-xl border px-4 py-3 text-sm font-semibold transition ${
                      syncMode === 'LATEST_MESSAGES'
                        ? 'border-indigo-400 bg-indigo-50 text-indigo-700'
                        : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50'
                    }`}
                    onClick={() => setSyncMode('LATEST_MESSAGES')}
                  >
                    Latest messages
                  </button>
                </div>
              </div>

              {syncMode === 'DATE_RANGE' ? (
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
              ) : (
                <label className="mt-5 block text-xs font-semibold text-slate-600">
                  Number of messages
                  <input
                    type="number"
                    className="form-input mt-1.5"
                    min={1}
                    max={10000}
                    step={1}
                    value={syncMessageCount}
                    onChange={(event) =>
                      setSyncMessageCount(Math.max(1, Number(event.target.value) || 1))
                    }
                  />
                  <span className="mt-1.5 block text-[11px] font-normal text-slate-400">
                    Imports the newest messages first. Maximum 10,000 per request.
                  </span>
                </label>
              )}

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
                  onClick={() => void syncSelected()}
                >
                  <FiMail aria-hidden="true" />
                  {syncing ? 'Starting…' : 'Start sync'}
                </button>
              </div>
            </div>
          </div>
        )}

        <div
          className={`${mobileConversationOpen ? 'hidden' : 'block'} mobile-inbox-list panel mb-4 overflow-hidden lg:hidden`}
        >
          <div className="border-b border-slate-100 px-4 py-3">
            <p className="text-xs font-bold text-slate-900">Chats</p>
            <p className="mt-1 text-[11px] text-slate-500">
              {conversations.length} of {totalElements} loaded
            </p>
          </div>
          <ConversationList
            compact
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
              <header className="inbox-thread-header flex items-start gap-3 border-b border-slate-200 bg-white px-4 py-4 sm:px-5">
                <button
                  type="button"
                  className="secondary-button shrink-0 px-3 lg:hidden"
                  onClick={() => {
                    const params = new URLSearchParams(searchParams)
                    params.delete('conversationId')
                    params.delete('compose')
                    setSearchParams(params)
                    setMobileConversationOpen(false)
                  }}
                  aria-label="Back to conversations"
                >
                  <FiArrowLeft aria-hidden="true" />
                </button>
                <div className="min-w-0 flex-1">
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
                {contactParticipant?.contactId && can(PERMISSIONS.CONTACT_READ) && (
                  <Link
                    className="secondary-button shrink-0 gap-2 px-3"
                    to={`/contacts/${contactParticipant.contactId}`}
                  >
                    <FiUsers aria-hidden="true" />
                    <span className="hidden sm:inline">View contact</span>
                  </Link>
                )}
                {contactParticipant &&
                  !contactParticipant.contactId &&
                  contactParticipant.contactResolution !== 'AMBIGUOUS' &&
                  can(PERMISSIONS.CONTACT_CREATE) && (
                    <Link
                      className="secondary-button shrink-0 gap-2 px-3"
                      to={createContactHref(contactParticipant)}
                    >
                      <FiUsers aria-hidden="true" />
                      <span className="hidden sm:inline">Add to contacts</span>
                    </Link>
                  )}
                {contactParticipant?.contactResolution === 'AMBIGUOUS' &&
                  can(PERMISSIONS.CONTACT_READ) && (
                    <Link
                      className="secondary-button shrink-0 gap-2 px-3"
                      to={`/contacts?q=${encodeURIComponent(contactParticipant.address)}`}
                    >
                      <FiUsers aria-hidden="true" />
                      <span className="hidden sm:inline">Review contacts</span>
                    </Link>
                  )}
                {selectedConversation &&
                  selectedFolder === 'INBOX' &&
                  can(PERMISSIONS.EMAIL_MODIFY) && (
                    <button
                      type="button"
                      className="secondary-button shrink-0 gap-2 px-3"
                      disabled={readUpdatingConversationId === selectedConversation.id}
                      onClick={() =>
                        void changeConversationReadState(
                          selectedConversation,
                          selectedConversation.unreadCount > 0,
                        )
                      }
                    >
                      <FiCheckCircle aria-hidden="true" />
                      {readUpdatingConversationId === selectedConversation.id
                        ? 'Updating…'
                        : selectedConversation.unreadCount > 0
                          ? 'Mark read'
                          : 'Mark unread'}
                    </button>
                  )}
              </header>

              <div className="channel-message-scroll space-y-4 p-3 sm:p-5">
                {messagesLoading ? (
                  <div className="space-y-4" role="status" aria-label="Loading messages">
                    {Array.from({ length: 3 }).map((_, index) => (
                      <div key={index} className="max-w-4xl rounded-xl border border-slate-200 bg-white p-4">
                        <div className="flex items-center justify-between gap-4">
                          <Skeleton className="h-4 w-28" />
                          <Skeleton className="h-3 w-24" />
                        </div>
                        <Skeleton className="mt-4 h-4 w-2/3" />
                        <Skeleton className="mt-3 h-3 w-full" />
                        <Skeleton className="mt-2 h-3 w-5/6" />
                        <Skeleton className="mt-2 h-3 w-3/4" />
                      </div>
                    ))}
                  </div>
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
                            <a
                              key={attachment.id}
                              href={inboxApi.attachmentContentUrl(companyId, attachment.id)}
                              target="_blank"
                              rel="noreferrer"
                              className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs text-slate-600 transition hover:border-slate-300 hover:bg-slate-50 hover:text-slate-900"
                              title="Download attachment"
                            >
                              {attachment.filename || 'Attachment'}
                            </a>
                          ))}
                        </div>
                      )}

                      {(canSendSelectedEmail ||
                        (message.direction === 'INBOUND' &&
                          can(PERMISSIONS.EMAIL_MODIFY))) && (
                        <div className="mt-4 flex flex-wrap gap-2 border-t border-slate-100 pt-3">
                          {message.direction === 'INBOUND' &&
                            can(PERMISSIONS.EMAIL_MODIFY) && (
                              <button
                                type="button"
                                className="secondary-button gap-2 px-3 py-2 text-xs"
                                disabled={readUpdatingMessageId === message.id}
                                onClick={() =>
                                  void changeMessageReadState(message, !message.read)
                                }
                              >
                                <FiCheckCircle aria-hidden="true" />
                                {readUpdatingMessageId === message.id
                                  ? 'Updating…'
                                  : message.read
                                    ? 'Mark unread'
                                    : 'Mark read'}
                              </button>
                            )}
                          {canSendSelectedEmail && (
                            <>
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
                            </>
                          )}
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
    </section>
  )
}


export default function EmailInbox() {
  const location = useLocation()

  if (location.pathname.endsWith('/social-media')) {
    return <WhatsAppInbox />
  }

  return <EmailInboxView />
}
