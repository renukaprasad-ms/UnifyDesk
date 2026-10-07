import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import {
  FiClock,
  FiEdit2,
  FiPause,
  FiPlay,
  FiPlus,
  FiRefreshCw,
  FiSend,
  FiUpload,
  FiUsers,
  FiX,
} from 'react-icons/fi'
import { Link, useLocation, useSearchParams } from 'react-router'
import { broadcastApi } from '../../features/broadcast/broadcastApi'
import type {
  BroadcastMember,
  BroadcastMemberImportResult,
  BroadcastMessage,
  BroadcastMessageStatus,
  BroadcastMessageSummary,
  BroadcastProgressEvent,
  BroadcastRecipient,
  BroadcastRecipientStatus,
  BroadcastSender,
  BroadcastStats,
  Page,
} from '../../features/broadcast/types'
import type { ChannelType } from '../../features/channel/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { realtimeClient } from '../../features/realtime/realtimeClient'
import { emailTemplateApi } from '../../features/emailTemplate/emailTemplateApi'
import type {
  EmailTemplateProviderBinding,
  EmailTemplateSummary,
} from '../../features/emailTemplate/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useSnackbar } from '../../components/feedback/SnackbarProvider'
import BroadcastSenderProviderMeta from '../../components/managed-email/BroadcastSenderProviderMeta'
import CanonicalBroadcastJourney from '../../components/broadcast/CanonicalBroadcastJourney'
import { managedSmsApi } from '../../features/managedSms/managedSmsApi'
import {
  isManagedSmsSender,
  managedSmsTemplateReadyForHeader,
  managedSmsVariableErrors,
} from '../../features/managedSms/composePolicy'
import { managedSmsErrorMessage } from '../../features/managedSms/copy'
import type {
  ManagedSmsHeader,
  ManagedSmsPreview,
  ManagedSmsTemplate,
} from '../../features/managedSms/types'
import {
  applyBroadcastMutation,
  applyBroadcastProgress,
  fetchBroadcastGroups,
  fetchBroadcastHistory,
  upsertBroadcastGroup,
} from '../../store/broadcast/broadcastSlice'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import {
  selectSelectedCompanyId,
  selectWorkspaceEpoch,
} from '../../store/workspace/workspaceSlice'
import { workspaceEpochToken } from '../../features/workspace/workspaceEpoch'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import StatusBadge from '../../components/ui/StatusBadge'
import { CardSkeleton, TableSkeleton } from '../../components/ui/Skeleton'

const channelFromPath = (pathname: string): ChannelType => {
  if (pathname.endsWith('/email')) return 'EMAIL'
  if (pathname.endsWith('/social-media')) return 'WHATSAPP'
  return 'SMS'
}

const labelForChannel = (channelType: ChannelType) =>
  channelType === 'EMAIL' ? 'Email' : channelType === 'WHATSAPP' ? 'WhatsApp' : 'SMS'

const statsProcessed = (stats: BroadcastStats) =>
  stats.accepted + stats.failed + stats.bounced + stats.unknown + stats.skipped

const progressFor = (message: BroadcastMessageSummary | BroadcastMessage) => {
  if (message.progressPercent !== undefined) return message.progressPercent
  if (message.stats.total <= 0) return 0
  const terminal = ['COMPLETED', 'PARTIALLY_FAILED', 'CANCELLED', 'FAILED'].includes(message.status)
  const value = Math.floor((statsProcessed(message.stats) * 100) / message.stats.total)
  return Math.min(terminal ? 100 : 99, value)
}

const statusLabel = (status: BroadcastMessageStatus) =>
  status
    .toLowerCase()
    .replaceAll('_', ' ')
    .replace(/\b\w/g, (char) => char.toUpperCase())

const recipientStatusLabel = (status: BroadcastRecipientStatus) => {
  if (status === 'ACCEPTED_BY_PROVIDER') return 'Accepted'
  if (status === 'RETRY') return 'Retrying'
  return status
    .toLowerCase()
    .replaceAll('_', ' ')
    .replace(/\b\w/g, (char) => char.toUpperCase())
}

const msg91TemplateStatusLabel = (binding?: EmailTemplateProviderBinding | null) => {
  if (!binding) return 'not synced'
  if (binding.sendable || binding.status === 'READY') return 'ready'
  if (binding.status === 'PENDING_REVIEW') return 'pending approval'
  if (binding.status === 'SYNCING') return 'syncing'
  if (binding.status === 'REJECTED') return 'rejected'
  if (binding.status === 'FAILED') return 'sync failed'
  return 'status not confirmed'
}

const senderLabel = (sender?: BroadcastSender | null) => {
  if (sender?.provider === 'MSG91_SMS') return sender.fromAddress
  return sender?.displayName
    ? `${sender.displayName} <${sender.fromAddress}>`
    : sender?.fromAddress || 'Legacy mailbox sender'
}

const formatDate = (value?: string | null) => {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

const automaticTemplateVariables = new Set([
  'destination',
  'email',
  'phone',
  'mobile',
  'current_date',
  'current_year',
])

const recipientNameVariables = new Set([
  'name',
  'display_name',
  'full_name',
  'first_name',
  'last_name',
])

const emptyPage = <T,>(): Page<T> => ({
  content: [],
  number: 0,
  size: 50,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
})

export default function BroadcastWorkspace() {
  const dispatch = useAppDispatch()
  const companyId = useAppSelector(selectSelectedCompanyId)
  const workspaceEpoch = useAppSelector(selectWorkspaceEpoch)
  const { isCurrent: isWorkspaceTokenCurrent } = useWorkspaceEpochScope(
    companyId,
    workspaceEpoch,
  )
  const isCurrentScope = useCallback(
    (capturedCompanyId: string, capturedEpoch: number) =>
      isWorkspaceTokenCurrent(workspaceEpochToken(capturedCompanyId, capturedEpoch)),
    [isWorkspaceTokenCurrent],
  )
  const broadcast = useAppSelector((state) => state.broadcast)
  const { can } = useIam()
  const snackbar = useSnackbar()
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()
  const channelType = channelFromPath(location.pathname)
  const channelLabel = labelForChannel(channelType)
  const selectedGroupId = searchParams.get('groupId')
  const selectedBroadcastId = searchParams.get('broadcastId')
  const selectedSenderId = searchParams.get('senderId')
  const newGroupOpen = searchParams.get('newGroup') === '1'
  const editGroupOpen = searchParams.get('editGroup') === '1'
  const composeOpen = searchParams.get('compose') === '1'
  const editBroadcastId = searchParams.get('editBroadcastId')
  const createBroadcastOpen = searchParams.get('create') === '1'
  const legacyUiEnabled = false

  const groups =
    broadcast.groupsCompanyId === companyId &&
    broadcast.groupsEpoch === workspaceEpoch &&
    broadcast.groupsChannelType === channelType
      ? broadcast.groups
      : []
  const selectedGroup = groups.find((group) => group.id === selectedGroupId) ?? null
  const history =
    broadcast.historyCompanyId === companyId &&
    broadcast.historyEpoch === workspaceEpoch &&
    broadcast.historyGroupId === selectedGroupId
      ? broadcast.messages
      : []

  useEffect(() => {
    const legacyKeys = ['groupId', 'legacy', 'newGroup', 'editGroup', 'compose', 'editBroadcastId', 'view']
    if (!legacyKeys.some((key) => searchParams.has(key))) return
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      legacyKeys.forEach((key) => next.delete(key))
      return next
    }, { replace: true })
  }, [location.search, searchParams, setSearchParams])

  const [senders, setSenders] = useState<BroadcastSender[]>([])
  const [selectedMessage, setSelectedMessage] = useState<BroadcastMessage | null>(null)
  const [recipients, setRecipients] = useState<Page<BroadcastRecipient>>(emptyPage())
  const [members, setMembers] = useState<Page<BroadcastMember>>(emptyPage())
  const [membersOpen, setMembersOpen] = useState(false)
  const [memberEmails, setMemberEmails] = useState('')
  const [memberCsv, setMemberCsv] = useState<File | null>(null)
  const [memberBusy, setMemberBusy] = useState(false)
  const [groupName, setGroupName] = useState('')
  const [groupDescription, setGroupDescription] = useState('')
  const [groupBusy, setGroupBusy] = useState(false)
  const [composeSenderId, setComposeSenderId] = useState('')
  const [composeTemplates, setComposeTemplates] = useState<EmailTemplateSummary[]>([])
  const [composeProviderBindings, setComposeProviderBindings] = useState<
    EmailTemplateProviderBinding[]
  >([])
  const [composeTemplateId, setComposeTemplateId] = useState('')
  const [composeSubject, setComposeSubject] = useState('')
  const [composeBody, setComposeBody] = useState('')
  const [composeTemplateVariables, setComposeTemplateVariables] = useState<Record<string, string>>(
    {},
  )
  const [composeManagedHeaders, setComposeManagedHeaders] = useState<ManagedSmsHeader[]>([])
  const [composeManagedTemplates, setComposeManagedTemplates] = useState<ManagedSmsTemplate[]>([])
  const [composeManagedPreview, setComposeManagedPreview] = useState<ManagedSmsPreview | null>(null)
  const [composeManagedLoading, setComposeManagedLoading] = useState(false)
  const [composeScheduleAt, setComposeScheduleAt] = useState('')
  const [composeBusy, setComposeBusy] = useState(false)
  const [actionBusy, setActionBusy] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [recipientsLoading, setRecipientsLoading] = useState(false)
  const [realtimeConnected, setRealtimeConnected] = useState(realtimeClient.isConnected())
  const recipientRefreshTimer = useRef<number | null>(null)
  const lastRecipientRefreshAt = useRef(0)

  const hasActiveHistory = history.some((message) =>
    ['QUEUED', 'RUNNING', 'PAUSED'].includes(message.status),
  )
  const selectedMessageActive =
    selectedMessage !== null && ['QUEUED', 'RUNNING', 'PAUSED'].includes(selectedMessage.status)

  const activeSenders = useMemo(
    () =>
      senders.filter(
        (sender) =>
          sender.status === 'ACTIVE' &&
          sender.sendingEnabled &&
          (channelType === 'SMS'
            ? sender.provider === 'MSG91_SMS'
            : channelType === 'EMAIL'
              ? sender.provider !== 'MSG91_SMS' && sender.provider !== 'META_WHATSAPP'
              : sender.provider === 'META_WHATSAPP'),
      ),
    [channelType, senders],
  )

  const sendersById = useMemo(
    () => new Map(senders.map((sender) => [sender.id, sender])),
    [senders],
  )

  const selectedActiveSender = useMemo(
    () =>
      selectedSenderId
        ? (activeSenders.find((sender) => sender.id === selectedSenderId) ?? null)
        : null,
    [activeSenders, selectedSenderId],
  )

  const composeSender = composeSenderId ? (sendersById.get(composeSenderId) ?? null) : null
  const composeUsesMsg91Email = composeSender?.provider === 'MSG91_EMAIL'
  const composeUsesManagedSms = isManagedSmsSender(composeSender)
  const composeManagedHeader = composeUsesManagedSms
    ? (composeManagedHeaders.find((item) => item.header === composeSender?.fromAddress) ?? null)
    : null

  const composeBindingByTemplateVersion = useMemo(
    () =>
      new Map(
        composeProviderBindings.map((binding) => [
          `${binding.templateId}:${binding.templateVersion}`,
          binding,
        ]),
      ),
    [composeProviderBindings],
  )

  const msg91ComposeTemplates = useMemo(
    () =>
      composeTemplates.filter(
        (template) =>
          template.purpose === 'TRANSACTIONAL' &&
          template.publishedVersion !== null &&
          template.publishedVersion !== undefined,
      ),
    [composeTemplates],
  )

  const availableComposeTemplates = useMemo(() => {
    if (!composeUsesMsg91Email) return composeTemplates
    return msg91ComposeTemplates.filter((template) => {
      const binding = composeBindingByTemplateVersion.get(
        `${template.id}:${template.publishedVersion}`,
      )
      return binding?.sendable === true
    })
  }, [
    composeBindingByTemplateVersion,
    composeTemplates,
    composeUsesMsg91Email,
    msg91ComposeTemplates,
  ])

  const selectedComposeTemplate =
    composeTemplates.find((template) => template.id === composeTemplateId) ?? null

  const availableManagedSmsTemplates = useMemo(
    () =>
      composeManagedHeader
        ? composeManagedTemplates.filter((template) =>
            managedSmsTemplateReadyForHeader(template, composeManagedHeader.id),
          )
        : [],
    [composeManagedHeader, composeManagedTemplates],
  )

  const selectedManagedSmsTemplate =
    composeManagedTemplates.find((template) => template.id === composeTemplateId) ?? null

  const managedVariableErrors = useMemo(
    () => managedSmsVariableErrors(selectedManagedSmsTemplate, composeTemplateVariables),
    [composeTemplateVariables, selectedManagedSmsTemplate],
  )

  const selectedComposeTemplateReady =
    !composeUsesMsg91Email ||
    availableComposeTemplates.some((template) => template.id === composeTemplateId)

  const editableComposeVariables =
    selectedComposeTemplate?.variables.filter(
      (variable) => !automaticTemplateVariables.has(variable),
    ) ?? []

  const composeContentReady =
    Boolean(composeSenderId) &&
    (composeUsesManagedSms
      ? Boolean(composeTemplateId) &&
        availableManagedSmsTemplates.some((template) => template.id === composeTemplateId) &&
        Object.keys(managedVariableErrors).length === 0 &&
        (composeManagedPreview?.validationErrors.length ?? 0) === 0
      : composeUsesMsg91Email
        ? Boolean(composeTemplateId) && selectedComposeTemplateReady
        : Boolean(composeTemplateId || composeBody.trim()))

  const updateSearch = useCallback(
    (updates: Record<string, string | null>) => {
      const next = new URLSearchParams(searchParams)
      Object.entries(updates).forEach(([key, value]) => {
        if (value === null) next.delete(key)
        else next.set(key, value)
      })
      setSearchParams(next)
    },
    [searchParams, setSearchParams],
  )

  const refreshGroups = useCallback(
    async (force = true) => {
      if (!companyId) return
      await dispatch(fetchBroadcastGroups({ companyId, epoch: workspaceEpoch, channelType, force }))
    },
    [channelType, companyId, dispatch, workspaceEpoch],
  )

  const refreshHistory = useCallback(
    async (force = true) => {
      if (!companyId || !selectedGroupId) return
      await dispatch(fetchBroadcastHistory({ companyId, epoch: workspaceEpoch, groupId: selectedGroupId, force }))
    },
    [companyId, dispatch, selectedGroupId, workspaceEpoch],
  )

  useEffect(() => {
    setComposeManagedHeaders([])
    setComposeManagedTemplates([])
    setComposeManagedPreview(null)
    setComposeTemplateVariables({})
    setComposeTemplateId('')
  }, [companyId, workspaceEpoch])

  useEffect(() => {
    if (!legacyUiEnabled || !companyId) return
    void dispatch(fetchBroadcastGroups({ companyId, epoch: workspaceEpoch, channelType }))
  }, [channelType, companyId, dispatch, workspaceEpoch])

  const refreshSenders = useCallback(async () => {
    if (!companyId) {
      setSenders([])
      return
    }
    const capturedCompanyId = companyId
    const capturedEpoch = workspaceEpoch
    try {
      const nextSenders = await broadcastApi.senders(companyId)
      if (isCurrentScope(capturedCompanyId, capturedEpoch)) setSenders(nextSenders)
    } catch (requestError) {
      if (isCurrentScope(capturedCompanyId, capturedEpoch)) {
        snackbar.error(apiErrorMessage(requestError, 'Unable to load Broadcast senders'))
      }
    }
  }, [companyId, isCurrentScope, snackbar, workspaceEpoch])

  useEffect(() => {
    void refreshSenders()
  }, [refreshSenders])

  useEffect(() => {
    if (!legacyUiEnabled || !companyId || !selectedGroupId || selectedGroup) return
    let active = true
    const capturedEpoch = workspaceEpoch
    broadcastApi
      .group(companyId, selectedGroupId)
      .then((group) => {
        if (active && isCurrentScope(companyId, capturedEpoch)) {
          dispatch(upsertBroadcastGroup(group))
        }
      })
      .catch((requestError) => {
        if (active && isCurrentScope(companyId, capturedEpoch)) {
          snackbar.error(apiErrorMessage(requestError, 'Unable to load broadcast group'))
        }
      })
    return () => {
      active = false
    }
  }, [companyId, dispatch, isCurrentScope, selectedGroup, selectedGroupId, workspaceEpoch])

  useEffect(() => {
    if (!legacyUiEnabled || !companyId || !selectedGroupId) return
    void dispatch(fetchBroadcastHistory({ companyId, epoch: workspaceEpoch, groupId: selectedGroupId }))
  }, [companyId, dispatch, selectedGroupId, workspaceEpoch])

  useEffect(() => {
    if (!companyId || !selectedBroadcastId) {
      setSelectedMessage(null)
      setRecipients(emptyPage())
      return
    }
    let active = true
    const capturedEpoch = workspaceEpoch
    setDetailLoading(true)
    broadcastApi
      .message(companyId, selectedBroadcastId)
      .then(async (message) => {
        if (!active || !isCurrentScope(companyId, capturedEpoch)) return
        setSelectedMessage(message)
        dispatch(applyBroadcastMutation(message))
        if (message.runId) {
          setRecipientsLoading(true)
          try {
            const page = await broadcastApi.recipients(companyId, selectedBroadcastId)
            if (active && isCurrentScope(companyId, capturedEpoch)) setRecipients(page)
          } finally {
            if (active && isCurrentScope(companyId, capturedEpoch)) setRecipientsLoading(false)
          }
        } else {
          setRecipients(emptyPage())
        }
      })
      .catch((requestError) => {
        if (active && isCurrentScope(companyId, capturedEpoch))
          snackbar.error(apiErrorMessage(requestError, 'Unable to load broadcast details'))
      })
      .finally(() => {
        if (active && isCurrentScope(companyId, capturedEpoch)) setDetailLoading(false)
      })
    return () => {
      active = false
    }
  }, [companyId, dispatch, isCurrentScope, selectedBroadcastId, workspaceEpoch])

  useEffect(() => realtimeClient.subscribeConnection(setRealtimeConnected), [])

  useEffect(() => {
    if (!companyId) return
    const capturedEpoch = workspaceEpoch

    const scheduleRecipientRefresh = (broadcastId: string) => {
      if (broadcastId !== selectedBroadcastId) return
      const now = Date.now()
      const delay = Math.max(250, 1500 - (now - lastRecipientRefreshAt.current))
      if (recipientRefreshTimer.current !== null) return

      recipientRefreshTimer.current = window.setTimeout(() => {
        recipientRefreshTimer.current = null
        if (!isCurrentScope(companyId, capturedEpoch)) return
        lastRecipientRefreshAt.current = Date.now()
        void broadcastApi
          .recipients(companyId, broadcastId, recipients.number)
          .then((page) => {
            if (isCurrentScope(companyId, capturedEpoch)) setRecipients(page)
          })
          .catch(() => undefined)
      }, delay)
    }

    const unsubscribe = realtimeClient.subscribe<BroadcastProgressEvent>(
      'broadcast.progress.changed',
      (event) => {
        if (!isCurrentScope(companyId, capturedEpoch)) return
        dispatch(applyBroadcastProgress(event.payload))
        setSelectedMessage((current) => {
          if (!current || current.id !== event.payload.broadcastId) return current
          return {
            ...current,
            runId: event.payload.runId,
            status: event.payload.status === 'PREPARING' ? 'QUEUED' : event.payload.status,
            stats: event.payload.stats,
            progressPercent: event.payload.progressPercent,
          }
        })
        scheduleRecipientRefresh(event.payload.broadcastId)
      },
    )

    return () => {
      unsubscribe()
      if (recipientRefreshTimer.current !== null) {
        window.clearTimeout(recipientRefreshTimer.current)
        recipientRefreshTimer.current = null
      }
    }
  }, [
    companyId,
    dispatch,
    isCurrentScope,
    recipients.number,
    selectedBroadcastId,
    workspaceEpoch,
  ])

  useEffect(() => {
    if (realtimeConnected || !companyId) return

    if (!selectedMessageActive && !hasActiveHistory) return

    let stopped = false
    const capturedEpoch = workspaceEpoch
    const poll = async () => {
      if (stopped) return
      try {
        if (selectedBroadcastId) {
          const message = await broadcastApi.message(companyId, selectedBroadcastId)
          if (stopped || !isCurrentScope(companyId, capturedEpoch)) return
          setSelectedMessage(message)
          dispatch(applyBroadcastMutation(message))
          if (message.runId) {
            const page = await broadcastApi.recipients(
              companyId,
              selectedBroadcastId,
              recipients.number,
            )
            if (!stopped && isCurrentScope(companyId, capturedEpoch)) setRecipients(page)
          }
        }
        if (legacyUiEnabled && selectedGroupId) {
          await dispatch(
            fetchBroadcastHistory({
              companyId,
              epoch: workspaceEpoch,
              groupId: selectedGroupId,
              force: true,
            }),
          )
        }
      } catch {
        // WebSocket recovery polling is best-effort; normal request errors remain user-driven.
      }
    }

    void poll()
    const timer = window.setInterval(() => void poll(), 5000)
    return () => {
      stopped = true
      window.clearInterval(timer)
    }
  }, [
    companyId,
    dispatch,
    hasActiveHistory,
    realtimeConnected,
    recipients.number,
    selectedBroadcastId,
    selectedGroupId,
    selectedMessageActive,
    workspaceEpoch,
    isCurrentScope,
  ])

  useEffect(() => {
    if (!companyId || !composeOpen || channelType !== 'EMAIL') {
      setComposeTemplates([])
      return
    }

    let active = true
    const capturedEpoch = workspaceEpoch
    emailTemplateApi
      .published(companyId)
      .then((items) => {
        if (active && isCurrentScope(companyId, capturedEpoch)) setComposeTemplates(items)
      })
      .catch((requestError) => {
        if (active && isCurrentScope(companyId, capturedEpoch)) {
          snackbar.error(apiErrorMessage(requestError, 'Unable to load published email templates'))
        }
      })

    return () => {
      active = false
    }
  }, [channelType, companyId, composeOpen, isCurrentScope, snackbar, workspaceEpoch])

  useEffect(() => {
    if (!companyId || !composeOpen || !composeUsesManagedSms) {
      setComposeManagedHeaders([])
      setComposeManagedTemplates([])
      setComposeManagedPreview(null)
      return
    }

    let active = true
    const capturedEpoch = workspaceEpoch
    setComposeManagedHeaders([])
    setComposeManagedTemplates([])
    setComposeManagedPreview(null)
    setComposeManagedLoading(true)
    Promise.all([managedSmsApi.headers(companyId), managedSmsApi.templates(companyId)])
      .then(([nextHeaders, nextTemplates]) => {
        if (!active || !isCurrentScope(companyId, capturedEpoch)) return
        setComposeManagedHeaders(nextHeaders)
        setComposeManagedTemplates(nextTemplates)
      })
      .catch((requestError) => {
        if (active && isCurrentScope(companyId, capturedEpoch)) {
          snackbar.error(
            managedSmsErrorMessage(
              requestError,
              'Unable to load managed SMS Headers and templates.',
            ),
          )
        }
      })
      .finally(() => {
        if (active && isCurrentScope(companyId, capturedEpoch)) setComposeManagedLoading(false)
      })

    return () => {
      active = false
    }
  }, [companyId, composeOpen, composeUsesManagedSms, isCurrentScope, snackbar, workspaceEpoch])

  useEffect(() => {
    if (
      !companyId ||
      !composeOpen ||
      !composeUsesManagedSms ||
      !composeTemplateId ||
      !composeManagedHeader
    ) {
      setComposeManagedPreview(null)
      return
    }

    const capturedEpoch = workspaceEpoch
    const timer = window.setTimeout(() => {
      if (!isCurrentScope(companyId, capturedEpoch)) return
      void managedSmsApi
        .previewTemplate(companyId, composeTemplateId, {
          headerId: composeManagedHeader.id,
          variables: composeTemplateVariables,
        })
        .then((nextPreview) => {
          if (isCurrentScope(companyId, capturedEpoch)) setComposeManagedPreview(nextPreview)
        })
        .catch((requestError) => {
          if (!isCurrentScope(companyId, capturedEpoch)) return
          setComposeManagedPreview(null)
          snackbar.error(
            managedSmsErrorMessage(requestError, 'Unable to preview this managed SMS template.'),
          )
        })
    }, 250)

    return () => window.clearTimeout(timer)
  }, [
    companyId,
    composeManagedHeader,
    composeOpen,
    composeTemplateId,
    composeTemplateVariables,
    composeUsesManagedSms,
    snackbar,
    workspaceEpoch,
    isCurrentScope,
  ])

  useEffect(() => {
    if (!companyId || !composeOpen || !composeUsesMsg91Email || !composeSender?.connectionId) {
      setComposeProviderBindings([])
      return
    }

    let active = true
    const capturedEpoch = workspaceEpoch
    void (async () => {
      try {
        const items = await emailTemplateApi.providerBindings(companyId, {
          connectionId: composeSender.connectionId,
        })
        if (!active || !isCurrentScope(companyId, capturedEpoch)) return

        const refreshable = items.filter((binding) =>
          ['SYNCING', 'SYNCED', 'PENDING_REVIEW'].includes(binding.status),
        )
        if (refreshable.length === 0) {
          setComposeProviderBindings(items)
          return
        }

        const refreshed = await Promise.allSettled(
          refreshable.map((binding) =>
            emailTemplateApi.refreshProviderBinding(companyId, binding.id),
          ),
        )
        if (!active || !isCurrentScope(companyId, capturedEpoch)) return

        const updates = new Map(
          refreshed
            .filter(
              (result): result is PromiseFulfilledResult<EmailTemplateProviderBinding> =>
                result.status === 'fulfilled',
            )
            .map((result) => [result.value.id, result.value]),
        )
        setComposeProviderBindings(items.map((binding) => updates.get(binding.id) ?? binding))
      } catch (requestError) {
        if (active && isCurrentScope(companyId, capturedEpoch)) {
          snackbar.error(apiErrorMessage(requestError, 'Unable to load MSG91 template bindings'))
        }
      }
    })()

    return () => {
      active = false
    }
  }, [
    companyId,
    composeOpen,
    composeSender?.connectionId,
    composeUsesMsg91Email,
    isCurrentScope,
    snackbar,
    workspaceEpoch,
  ])

  useEffect(() => {
    if (!composeOpen) return
    let active = true
    const capturedEpoch = workspaceEpoch

    const reset = () => {
      setComposeSubject('')
      setComposeBody('')
      setComposeTemplateVariables({})
      setComposeScheduleAt('')
      setComposeTemplateId('')
      setComposeManagedPreview(null)
      setComposeSenderId(selectedActiveSender?.id ?? '')
    }

    if (!editBroadcastId || !companyId) {
      reset()
      return
    }

    setComposeBusy(true)
    broadcastApi
      .message(companyId, editBroadcastId)
      .then((message) => {
        if (!active || !isCurrentScope(companyId, capturedEpoch)) return
        setComposeSenderId(message.senderId ?? '')
        setComposeTemplateId(message.managedSmsTemplateId ?? message.sourceTemplateId ?? '')
        setComposeSubject(message.subject ?? '')
        setComposeBody(message.bodyText ?? '')
        setComposeTemplateVariables(message.templateVariables ?? {})
        setComposeScheduleAt(
          message.scheduledAt ? new Date(message.scheduledAt).toISOString().slice(0, 16) : '',
        )
      })
      .catch((requestError) => {
        if (active && isCurrentScope(companyId, capturedEpoch)) {
          snackbar.error(apiErrorMessage(requestError, 'Unable to load broadcast draft'))
        }
      })
      .finally(() => {
        if (active && isCurrentScope(companyId, capturedEpoch)) setComposeBusy(false)
      })

    return () => {
      active = false
    }
  }, [
    companyId,
    composeOpen,
    editBroadcastId,
    isCurrentScope,
    selectedActiveSender,
    workspaceEpoch,
  ])

  const openNewGroup = () => {
    updateSearch({
      legacy: '1',
      newGroup: null,
      broadcastId: null,
      compose: null,
      editBroadcastId: null,
      view: null,
    })
  }

  const openEditGroup = () => {
    if (!selectedGroup) return
    setGroupName(selectedGroup.name)
    setGroupDescription(selectedGroup.description ?? '')
    updateSearch({
      editGroup: '1',
      newGroup: null,
      broadcastId: null,
      compose: null,
      editBroadcastId: null,
    })
  }

  const mutationScope = () =>
    companyId ? { companyId, epoch: workspaceEpoch } : null

  const mutationScopeIsCurrent = (scope: { companyId: string; epoch: number } | null) =>
    Boolean(scope && isCurrentScope(scope.companyId, scope.epoch))

  const createGroup = async (event: FormEvent) => {
    event.preventDefault()
    if (!companyId || !groupName.trim() || channelType === 'WHATSAPP') return
    const scope = mutationScope()
    setGroupBusy(true)
    try {
      const group = await broadcastApi.createGroup(companyId, {
        name: groupName.trim(),
        description: groupDescription.trim() || null,
        channelType,
      })
      if (!mutationScopeIsCurrent(scope)) return
      dispatch(upsertBroadcastGroup(group))
      updateSearch({ newGroup: null, groupId: group.id, broadcastId: null })
      snackbar.success('Broadcast group created. Add recipients when you are ready.')
    } catch (requestError) {
      if (mutationScopeIsCurrent(scope)) {
        snackbar.error(apiErrorMessage(requestError, 'Unable to create broadcast group'))
      }
    } finally {
      if (mutationScopeIsCurrent(scope)) setGroupBusy(false)
    }
  }

  const updateGroup = async (event: FormEvent) => {
    event.preventDefault()
    if (!companyId || !selectedGroupId || !groupName.trim()) return
    const scope = mutationScope()
    setGroupBusy(true)
    try {
      const group = await broadcastApi.updateGroup(companyId, selectedGroupId, {
        name: groupName.trim(),
        description: groupDescription.trim() || null,
      })
      if (!mutationScopeIsCurrent(scope)) return
      dispatch(upsertBroadcastGroup(group))
      updateSearch({ editGroup: null })
      snackbar.success('Broadcast group updated.')
    } catch (requestError) {
      if (mutationScopeIsCurrent(scope)) {
        snackbar.error(apiErrorMessage(requestError, 'Unable to update broadcast group'))
      }
    } finally {
      if (mutationScopeIsCurrent(scope)) setGroupBusy(false)
    }
  }

  const openMembers = async () => {
    if (!companyId || !selectedGroupId) return
    const scope = mutationScope()
    setMembersOpen(true)
    setMembers(emptyPage())
    try {
      const nextMembers = await broadcastApi.members(companyId, selectedGroupId)
      if (mutationScopeIsCurrent(scope)) setMembers(nextMembers)
    } catch (requestError) {
      if (mutationScopeIsCurrent(scope)) {
        snackbar.error(apiErrorMessage(requestError, 'Unable to load group members'))
      }
    }
  }

  const loadMembersPage = async (page: number) => {
    if (!companyId || !selectedGroupId) return
    const scope = mutationScope()
    setMemberBusy(true)
    try {
      const nextMembers = await broadcastApi.members(companyId, selectedGroupId, page)
      if (mutationScopeIsCurrent(scope)) setMembers(nextMembers)
    } catch (requestError) {
      if (mutationScopeIsCurrent(scope)) {
        snackbar.error(apiErrorMessage(requestError, 'Unable to load group members'))
      }
    } finally {
      if (mutationScopeIsCurrent(scope)) setMemberBusy(false)
    }
  }

  const afterMemberChange = async (
    result?: BroadcastMemberImportResult,
    scope = mutationScope(),
  ) => {
    await Promise.all([loadMembersPage(0), refreshGroups(true)])
    if (!mutationScopeIsCurrent(scope)) return
    if (result) {
      snackbar.success(
        `Processed ${result.processed.toLocaleString()} · added ${result.insertedOrReactivated.toLocaleString()} · duplicates ${result.duplicates.toLocaleString()} · invalid ${result.invalid.toLocaleString()}`,
      )
    }
  }

  const addPastedMembers = async () => {
    if (!companyId || !selectedGroupId) return
    const values = Array.from(
      new Set(
        memberEmails
          .split(/[\n,;]+/)
          .map((value) => value.trim().toLowerCase())
          .filter(Boolean),
      ),
    )
    if (values.length === 0) return

    const scope = mutationScope()
    setMemberBusy(true)
    try {
      let processed = 0
      let insertedOrReactivated = 0
      let duplicates = 0
      let invalid = 0
      for (let offset = 0; offset < values.length; offset += 1000) {
        const chunk = values.slice(offset, offset + 1000)
        const result = await broadcastApi.addMembers(
          companyId,
          selectedGroupId,
          chunk.map((destination) => ({ destination })),
        )
        if (!mutationScopeIsCurrent(scope)) return
        processed += result.processed
        insertedOrReactivated += result.insertedOrReactivated
        duplicates += result.duplicates
        invalid += result.invalid
      }
      setMemberEmails('')
      await afterMemberChange(
        {
          processed,
          insertedOrReactivated,
          duplicates,
          invalid,
        },
        scope,
      )
    } catch (requestError) {
      if (mutationScopeIsCurrent(scope)) {
        snackbar.error(apiErrorMessage(requestError, 'Unable to add recipients'))
      }
    } finally {
      if (mutationScopeIsCurrent(scope)) setMemberBusy(false)
    }
  }

  const importCsv = async () => {
    if (!companyId || !selectedGroupId || !memberCsv) return
    const scope = mutationScope()
    setMemberBusy(true)
    try {
      const result = await broadcastApi.importMembers(companyId, selectedGroupId, memberCsv)
      if (!mutationScopeIsCurrent(scope)) return
      setMemberCsv(null)
      await afterMemberChange(result, scope)
    } catch (requestError) {
      if (mutationScopeIsCurrent(scope)) {
        snackbar.error(apiErrorMessage(requestError, 'Unable to import CSV'))
      }
    } finally {
      if (mutationScopeIsCurrent(scope)) setMemberBusy(false)
    }
  }

  const removeMember = async (memberId: string) => {
    if (!companyId || !selectedGroupId) return
    const scope = mutationScope()
    setMemberBusy(true)
    try {
      await broadcastApi.removeMember(companyId, selectedGroupId, memberId)
      if (!mutationScopeIsCurrent(scope)) return
      await afterMemberChange(undefined, scope)
    } catch (requestError) {
      if (mutationScopeIsCurrent(scope)) {
        snackbar.error(apiErrorMessage(requestError, 'Unable to remove recipient'))
      }
    } finally {
      if (mutationScopeIsCurrent(scope)) setMemberBusy(false)
    }
  }

  const openComposer = (broadcastId?: string) => {
    updateSearch({
      compose: '1',
      editBroadcastId: broadcastId ?? null,
      broadcastId: null,
      newGroup: null,
    })
  }

  const closeComposer = () => {
    updateSearch({ compose: null, editBroadcastId: null })
  }

  const saveDraft = async () => {
    if (!companyId || !selectedGroupId || !composeSenderId) {
      throw new Error('Choose a sender')
    }
    if (
      composeUsesManagedSms &&
      (!composeTemplateId ||
        !availableManagedSmsTemplates.some((template) => template.id === composeTemplateId))
    ) {
      throw new Error('Choose a ready managed SMS template for this Header')
    }
    if (composeUsesManagedSms && Object.keys(managedVariableErrors).length > 0) {
      throw new Error('Complete all required managed SMS template variables')
    }
    if (
      composeUsesMsg91Email &&
      (!composeTemplateId ||
        !availableComposeTemplates.some((template) => template.id === composeTemplateId))
    ) {
      throw new Error('Choose a transactional template synced to this MSG91 connection')
    }
    if (!composeUsesManagedSms && !composeTemplateId && !composeBody.trim()) {
      throw new Error('Choose a template or enter a message')
    }
    const input = composeUsesManagedSms
      ? {
          senderId: composeSenderId,
          managedSmsTemplateId: composeTemplateId,
          bodyText: null,
          subject: null,
          templateVariables: composeTemplateVariables,
        }
      : {
          templateId: composeTemplateId || null,
          senderId: composeSenderId,
          subject: channelType === 'EMAIL' ? composeSubject.trim() || null : null,
          bodyText: composeBody,
          templateVariables: composeTemplateId ? composeTemplateVariables : undefined,
        }
    const scope = mutationScope()
    const message = editBroadcastId
      ? await broadcastApi.updateMessage(companyId, editBroadcastId, input)
      : await broadcastApi.createMessage(companyId, selectedGroupId, input)
    if (!mutationScopeIsCurrent(scope)) throw new Error('Workspace changed while saving Broadcast')
    dispatch(applyBroadcastMutation(message))
    return message
  }

  const submitCompose = async (mode: 'draft' | 'send' | 'schedule') => {
    if (!companyId) return
    const scope = mutationScope()
    setComposeBusy(true)
    try {
      const draft = await saveDraft()
      if (!mutationScopeIsCurrent(scope)) return
      let result = draft
      if (mode === 'send') {
        result = await broadcastApi.send(companyId, draft.id)
        if (!mutationScopeIsCurrent(scope)) return
        snackbar.success('Broadcast queued. Provider acceptance and delivery events will update separately.')
      } else if (mode === 'schedule') {
        if (!composeScheduleAt) throw new Error('Choose a schedule time')
        const scheduleDate = new Date(composeScheduleAt)
        if (Number.isNaN(scheduleDate.getTime()) || scheduleDate.getTime() <= Date.now()) {
          throw new Error('Schedule time must be in the future')
        }
        result = await broadcastApi.schedule(companyId, draft.id, scheduleDate.toISOString())
        if (!mutationScopeIsCurrent(scope)) return
        snackbar.success('Broadcast scheduled.')
      } else {
        snackbar.success('Broadcast draft saved.')
      }
      if (!mutationScopeIsCurrent(scope)) return
      dispatch(applyBroadcastMutation(result))
      closeComposer()
      await refreshHistory(true)
    } catch (requestError) {
      if (!mutationScopeIsCurrent(scope)) return
      snackbar.error(
        composeUsesManagedSms
          ? managedSmsErrorMessage(
              requestError,
              requestError instanceof Error
                ? requestError.message
                : 'Unable to save managed SMS broadcast.',
            )
          : requestError instanceof Error
            ? requestError.message
            : apiErrorMessage(requestError, 'Unable to save broadcast'),
      )
    } finally {
      if (mutationScopeIsCurrent(scope)) setComposeBusy(false)
    }
  }

  const runAction = async (action: 'pause' | 'resume' | 'cancel' | 'send', messageId: string) => {
    if (!companyId) return
    const scope = mutationScope()
    setActionBusy(true)
    try {
      const result =
        action === 'pause'
          ? await broadcastApi.pause(companyId, messageId)
          : action === 'resume'
            ? await broadcastApi.resume(companyId, messageId)
            : action === 'cancel'
              ? await broadcastApi.cancel(companyId, messageId)
              : await broadcastApi.send(companyId, messageId)
      if (!mutationScopeIsCurrent(scope)) return
      dispatch(applyBroadcastMutation(result))
      setSelectedMessage((current) => (current?.id === result.id ? result : current))
      await refreshHistory(true)
    } catch (requestError) {
      if (!mutationScopeIsCurrent(scope)) return
      const actionSender = selectedMessage?.senderId
        ? (sendersById.get(selectedMessage.senderId) ?? null)
        : null
      snackbar.error(
        isManagedSmsSender(actionSender)
          ? managedSmsErrorMessage(requestError, `Unable to ${action} managed SMS broadcast.`)
          : apiErrorMessage(requestError, `Unable to ${action} broadcast`),
      )
    } finally {
      if (mutationScopeIsCurrent(scope)) setActionBusy(false)
    }
  }

  const loadRecipientsPage = async (page: number) => {
    if (!companyId || !selectedBroadcastId) return
    const scope = mutationScope()
    setRecipientsLoading(true)
    try {
      const nextRecipients = await broadcastApi.recipients(companyId, selectedBroadcastId, page)
      if (mutationScopeIsCurrent(scope)) setRecipients(nextRecipients)
    } catch (requestError) {
      if (mutationScopeIsCurrent(scope)) {
        snackbar.error(apiErrorMessage(requestError, 'Unable to load recipients'))
      }
    } finally {
      if (mutationScopeIsCurrent(scope)) setRecipientsLoading(false)
    }
  }

  const loadMoreHistory = () => {
    if (
      !companyId ||
      !selectedGroupId ||
      broadcast.loadingMoreHistory ||
      broadcast.historyPage + 1 >= broadcast.historyTotalPages
    ) {
      return
    }
    void dispatch(
      fetchBroadcastHistory({
        companyId,
        epoch: workspaceEpoch,
        groupId: selectedGroupId,
        page: broadcast.historyPage + 1,
        append: true,
      }),
    )
  }

  return (
    <section className="channel-workspace-page">
      <div className="channel-workspace-toolbar">
        <div className="min-w-0">
          <p className="eyebrow">BROADCAST</p>
          <p className="mt-1 truncate text-xs font-semibold text-slate-700">
            {createBroadcastOpen
              ? `Create ${channelLabel} Broadcast`
              : selectedBroadcastId
                ? `${channelLabel} Broadcast`
                : `${channelLabel} Broadcasts`}
          </p>
        </div>

        <div className="inbox-toolbar-actions ml-auto flex items-center gap-2">
          {can(PERMISSIONS.BROADCAST_CREATE) && (
            <button
              type="button"
              className="primary-button gap-2"
              disabled={createBroadcastOpen}
              onClick={() =>
                updateSearch({
                  create: '1',
                  broadcastId: null,
                  groupId: null,
                  legacy: null,
                  view: null,
                })
              }
            >
              <FiSend aria-hidden="true" />
              <span className="inbox-action-label">
                {createBroadcastOpen ? 'Creating broadcast' : 'Create broadcast'}
              </span>
            </button>
          )}
        </div>
      </div>

      <div className="channel-workspace-body">
        <main className="channel-message-surface">
          {selectedBroadcastId ? (
            <BroadcastDetail
              message={selectedMessage}
              sender={
                selectedMessage?.senderId
                  ? (sendersById.get(selectedMessage.senderId) ?? null)
                  : null
              }
              loading={detailLoading}
              recipients={recipients}
              recipientsLoading={recipientsLoading}
              actionBusy={actionBusy}
              canSend={
                can(PERMISSIONS.BROADCAST_SEND) &&
                Boolean(selectedMessage?.senderId) &&
                Boolean(selectedMessage?.groupId)
              }
              canEdit={can(PERMISSIONS.BROADCAST_MODIFY) && Boolean(selectedMessage?.groupId)}
              canControl={can(PERMISSIONS.BROADCAST_MODIFY)}
              onEdit={(id) => openComposer(id)}
              onAction={(action, id) => void runAction(action, id)}
              onRecipientsPage={(page) => void loadRecipientsPage(page)}
            />
          ) : !selectedGroupId || !legacyUiEnabled ? (
            createBroadcastOpen ? (
              <CanonicalBroadcastJourney
                channel={channelType}
                onCreated={(id) =>
                  updateSearch({
                    broadcastId: id,
                    create: null,
                    groupId: null,
                    compose: null,
                    view: null,
                  })
                }
              />
            ) : (
              <div className="channel-message-scroll min-h-[calc(100dvh-214px)] p-4 sm:p-5">
                <div className="flex min-h-[420px] w-full items-center justify-center rounded-2xl border border-dashed border-slate-200 bg-white/40 px-6 text-center">
                  <div className="max-w-md">
                    <FiSend className="mx-auto text-3xl text-slate-300" aria-hidden="true" />
                    <h1 className="mt-3 text-lg font-bold text-slate-900">Select a broadcast</h1>
                    <p className="mt-2 text-sm leading-6 text-slate-500">
                      Choose a broadcast from the history on the left, or use Create broadcast in
                      the header to start a new one.
                    </p>
                  </div>
                </div>
              </div>
            )
          ) : (
            <div className="channel-message-scroll min-h-[calc(100dvh-214px)] p-4 sm:p-5">
              <div className="w-full">
                <div className="rounded-2xl border border-slate-200 bg-white p-4">
                  <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
                    <div className="min-w-0">
                      <p className="eyebrow">PREVIOUS RECIPIENT GROUP</p>
                      <p className="mt-1 text-base font-bold text-slate-950">{selectedGroup?.name}</p>
                      <p className="mt-1 text-xs leading-5 text-slate-500">
                        {selectedGroup?.memberCount.toLocaleString() ?? 0} recipients
                        {selectedGroup?.description ? ` · ${selectedGroup.description}` : ''}
                      </p>
                    </div>

                    <div className="flex flex-wrap gap-2">
                      {can(PERMISSIONS.BROADCAST_MODIFY) && (
                        <>
                          <button
                            type="button"
                            className="secondary-button gap-2"
                            onClick={openEditGroup}
                          >
                            <FiEdit2 aria-hidden="true" />
                            Edit group
                          </button>
                          <button
                            type="button"
                            className="secondary-button gap-2"
                            onClick={() => void openMembers()}
                          >
                            <FiUsers aria-hidden="true" />
                            Manage members
                          </button>
                        </>
                      )}
                      {can(PERMISSIONS.BROADCAST_CREATE) && (
                        <button
                          type="button"
                          className="primary-button gap-2"
                          onClick={() => updateSearch({ groupId: null, legacy: null, view: null })}
                        >
                          <FiSend aria-hidden="true" />
                          Create with Contacts
                        </button>
                      )}
                    </div>
                  </div>

                  <div className="mt-5">
                    <div className="mb-3 flex items-center justify-between gap-3">
                      <div>
                        <p className="text-sm font-bold text-slate-900">Broadcast history</p>
                        <p className="mt-0.5 text-xs text-slate-500">
                          This older recipient group is kept for historical compatibility. Create new broadcasts from Contacts, Lists or Segments.
                        </p>
                      </div>
                    </div>

                    {broadcast.historyStatus === 'loading' && history.length === 0 ? (
                      <div className="space-y-3">
                        <CardSkeleton rows={4} />
                        <CardSkeleton rows={4} />
                        <CardSkeleton rows={4} />
                      </div>
                    ) : history.length === 0 ? (
                      <div className="panel px-5 py-12 text-center">
                        <FiSend className="mx-auto text-3xl text-slate-300" aria-hidden="true" />
                        <p className="mt-3 text-sm font-semibold text-slate-800">
                          No broadcasts yet
                        </p>
                        <p className="mt-1 text-xs text-slate-500">
                          This group is ready. Compose the first broadcast when you want to send.
                        </p>
                      </div>
                    ) : (
                      <div className="grid gap-3">
                        {history.map((message) => (
                          <BroadcastHistoryCard
                            key={message.id}
                            message={message}
                            sender={
                              message.senderId ? (sendersById.get(message.senderId) ?? null) : null
                            }
                            canModify={can(PERMISSIONS.BROADCAST_MODIFY)}
                            onOpen={() => updateSearch({ broadcastId: message.id })}
                            onEdit={() => openComposer(message.id)}
                          />
                        ))}
                        {broadcast.historyPage + 1 < broadcast.historyTotalPages && (
                          <button
                            type="button"
                            className="secondary-button mx-auto mt-2"
                            onClick={loadMoreHistory}
                            disabled={broadcast.loadingMoreHistory}
                          >
                            {broadcast.loadingMoreHistory ? 'Loading…' : 'Load older broadcasts'}
                          </button>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              </div>
            </div>
          )}
        </main>
      </div>

      {legacyUiEnabled && newGroupOpen && (
        <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4">
          <form
            className="mobile-sheet w-full max-w-md rounded-2xl bg-white p-5 shadow-xl"
            onSubmit={createGroup}
          >
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-base font-bold text-slate-950">New broadcast group</p>
                <p className="mt-1 text-xs leading-5 text-slate-500">
                  Recipients stay in this group so you can reuse them for future broadcasts.
                </p>
              </div>
              <button
                type="button"
                className="admin-back-button"
                onClick={() => updateSearch({ newGroup: null })}
                aria-label="Close"
              >
                <FiX aria-hidden="true" />
              </button>
            </div>
            <label className="mt-5 block text-xs font-semibold text-slate-700">
              Group name
              <input
                className="form-input mt-2"
                value={groupName}
                onChange={(event) => setGroupName(event.target.value)}
                maxLength={160}
                required
                autoFocus
              />
            </label>
            <label className="mt-4 block text-xs font-semibold text-slate-700">
              Description
              <textarea
                className="form-input mt-2 min-h-24"
                value={groupDescription}
                onChange={(event) => setGroupDescription(event.target.value)}
                maxLength={500}
              />
            </label>
            <div className="mt-5 flex justify-end gap-2">
              <button
                type="button"
                className="secondary-button"
                onClick={() => updateSearch({ newGroup: null })}
              >
                Cancel
              </button>
              <button
                type="submit"
                className="primary-button"
                disabled={groupBusy || !groupName.trim()}
              >
                {groupBusy ? 'Creating…' : 'Create group'}
              </button>
            </div>
          </form>
        </div>
      )}

      {legacyUiEnabled && editGroupOpen && selectedGroup && (
        <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4">
          <form
            className="mobile-sheet w-full max-w-md rounded-2xl bg-white p-5 shadow-xl"
            onSubmit={updateGroup}
          >
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-base font-bold text-slate-950">Edit broadcast group</p>
                <p className="mt-1 text-xs leading-5 text-slate-500">
                  Renaming the group does not change any previous broadcast recipient snapshots.
                </p>
              </div>
              <button
                type="button"
                className="admin-back-button"
                onClick={() => updateSearch({ editGroup: null })}
                aria-label="Close"
              >
                <FiX aria-hidden="true" />
              </button>
            </div>
            <label className="mt-5 block text-xs font-semibold text-slate-700">
              Group name
              <input
                className="form-input mt-2"
                value={groupName}
                onChange={(event) => setGroupName(event.target.value)}
                maxLength={160}
                required
                autoFocus
              />
            </label>
            <label className="mt-4 block text-xs font-semibold text-slate-700">
              Description
              <textarea
                className="form-input mt-2 min-h-24"
                value={groupDescription}
                onChange={(event) => setGroupDescription(event.target.value)}
                maxLength={500}
              />
            </label>
            <div className="mt-5 flex justify-end gap-2">
              <button
                type="button"
                className="secondary-button"
                onClick={() => updateSearch({ editGroup: null })}
              >
                Cancel
              </button>
              <button
                type="submit"
                className="primary-button"
                disabled={groupBusy || !groupName.trim()}
              >
                {groupBusy ? 'Saving…' : 'Save changes'}
              </button>
            </div>
          </form>
        </div>
      )}

      {legacyUiEnabled && membersOpen && selectedGroup && (
        <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4">
          <div className="mobile-sheet flex max-h-[90dvh] w-full max-w-4xl flex-col rounded-2xl bg-white shadow-xl">
            <div className="flex items-start justify-between gap-4 border-b border-slate-100 p-5">
              <div>
                <p className="text-base font-bold text-slate-950">{selectedGroup.name} members</p>
                <p className="mt-1 text-xs text-slate-500">
                  {selectedGroup.memberCount.toLocaleString()} active recipients
                </p>
              </div>
              <button
                type="button"
                className="admin-back-button"
                onClick={() => setMembersOpen(false)}
                aria-label="Close"
              >
                <FiX aria-hidden="true" />
              </button>
            </div>

            <div className="grid min-h-0 flex-1 gap-4 overflow-y-auto p-5 lg:grid-cols-[minmax(0,320px)_minmax(0,1fr)]">
              <div className="space-y-4">
                <div className="rounded-xl border border-slate-200 p-4">
                  <p className="text-xs font-bold text-slate-800">
                    {channelType === 'SMS' ? 'Paste phone numbers' : 'Paste email addresses'}
                  </p>
                  <p className="mt-1 text-[11px] leading-5 text-slate-500">
                    Separate with new lines, commas or semicolons. Requests are automatically
                    chunked to 1,000 recipients.
                  </p>
                  <textarea
                    className="form-input mt-3 min-h-36"
                    value={memberEmails}
                    onChange={(event) => setMemberEmails(event.target.value)}
                    placeholder={
                      channelType === 'SMS'
                        ? '+919876543210\n+14155552671'
                        : 'alice@example.com\nbob@example.com'
                    }
                  />
                  <button
                    type="button"
                    className="primary-button mt-3 w-full"
                    onClick={() => void addPastedMembers()}
                    disabled={memberBusy || !memberEmails.trim()}
                  >
                    Add recipients
                  </button>
                </div>

                <div className="rounded-xl border border-slate-200 p-4">
                  <p className="text-xs font-bold text-slate-800">Import CSV</p>
                  <p className="mt-1 text-[11px] leading-5 text-slate-500">
                    Best for large lists.{' '}
                    {channelType === 'SMS' ? (
                      <>
                        Include a <strong>phone</strong>, <strong>mobile</strong> or{' '}
                        <strong>destination</strong> header.
                      </>
                    ) : (
                      <>
                        Include an <strong>email</strong> or <strong>destination</strong> header.
                      </>
                    )}{' '}
                    The server streams the file in bounded batches.
                  </p>
                  <label className="mt-3 flex min-h-24 cursor-pointer flex-col items-center justify-center rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 text-center">
                    <FiUpload className="text-xl text-slate-400" aria-hidden="true" />
                    <span className="mt-2 text-xs font-semibold text-slate-700">
                      {memberCsv?.name ?? 'Choose CSV file'}
                    </span>
                    <input
                      type="file"
                      accept=".csv,text/csv"
                      className="sr-only"
                      onChange={(event) => setMemberCsv(event.target.files?.[0] ?? null)}
                    />
                  </label>
                  <button
                    type="button"
                    className="secondary-button mt-3 w-full"
                    onClick={() => void importCsv()}
                    disabled={memberBusy || !memberCsv}
                  >
                    {memberBusy ? 'Importing…' : 'Import CSV'}
                  </button>
                </div>
              </div>

              <div className="min-w-0">
                <div className="overflow-hidden rounded-xl border border-slate-200">
                  <div className="overflow-x-auto">
                    <table className="mobile-card-table w-full min-w-[620px] text-left text-xs">
                      <thead className="bg-slate-50 text-[10px] uppercase tracking-wide text-slate-500">
                        <tr>
                          <th className="px-4 py-3">Recipient</th>
                          <th className="px-4 py-3">Added</th>
                          <th className="px-4 py-3 text-right">Action</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100">
                        {members.content.length === 0 ? (
                          <tr>
                            <td colSpan={3} className="px-4 py-10 text-center text-slate-500">
                              No recipients in this group yet.
                            </td>
                          </tr>
                        ) : (
                          members.content.map((member) => (
                            <tr key={member.id}>
                              <td className="px-4 py-3" data-label="Recipient">
                                <p className="font-semibold text-slate-800">{member.destination}</p>
                                {member.displayName && (
                                  <p className="mt-0.5 text-[11px] text-slate-500">
                                    {member.displayName}
                                  </p>
                                )}
                              </td>
                              <td className="px-4 py-3 text-slate-500" data-label="Added">
                                {formatDate(member.createdAt)}
                              </td>
                              <td className="mobile-action-cell px-4 py-3 text-right">
                                <button
                                  type="button"
                                  className="text-xs font-semibold text-rose-600"
                                  disabled={memberBusy}
                                  onClick={() => void removeMember(member.id)}
                                >
                                  Remove
                                </button>
                              </td>
                            </tr>
                          ))
                        )}
                      </tbody>
                    </table>
                  </div>
                </div>

                {members.totalPages > 1 && (
                  <div className="mt-3 flex items-center justify-between gap-3">
                    <button
                      type="button"
                      className="secondary-button"
                      disabled={memberBusy || members.first}
                      onClick={() => void loadMembersPage(Math.max(0, members.number - 1))}
                    >
                      Previous
                    </button>
                    <span className="text-xs text-slate-500">
                      Page {members.number + 1} of {members.totalPages}
                    </span>
                    <button
                      type="button"
                      className="secondary-button"
                      disabled={memberBusy || members.last}
                      onClick={() => void loadMembersPage(members.number + 1)}
                    >
                      Next
                    </button>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {legacyUiEnabled && composeOpen && selectedGroup && (
        <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4">
          <div className="mobile-sheet w-full max-w-2xl rounded-2xl bg-white p-5 shadow-xl">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-base font-bold text-slate-950">
                  {editBroadcastId ? 'Edit broadcast' : 'New broadcast'}
                </p>
                <p className="mt-1 text-xs text-slate-500">
                  {selectedGroup.name} · {selectedGroup.memberCount.toLocaleString()} current
                  recipients
                </p>
              </div>
              <button
                type="button"
                className="admin-back-button"
                onClick={closeComposer}
                aria-label="Close"
              >
                <FiX aria-hidden="true" />
              </button>
            </div>

            {!composeSenderId ? (
              <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 px-4 py-4 text-xs leading-5 text-amber-800">
                <p className="font-bold">Choose a Broadcast account first.</p>
                <p className="mt-1">
                  Select a Broadcast sender from the secondary sidebar, then open the broadcast
                  composer again.
                </p>
              </div>
            ) : (
              <>
                <div className="mt-5 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3">
                  <p className="text-[10px] font-bold uppercase tracking-[0.08em] text-slate-400">
                    Sending from
                  </p>
                  <p className="mt-1 text-sm font-bold text-slate-900">
                    {senderLabel(sendersById.get(composeSenderId) ?? null)}
                  </p>
                  <p className="mt-1 text-[11px] text-slate-500">
                    <BroadcastSenderProviderMeta
                      sender={sendersById.get(composeSenderId)}
                      includeConnectionDetails
                    />
                  </p>
                </div>

                {composeUsesManagedSms && (
                  <div className="mt-4 grid gap-4">
                    <div className="rounded-xl border border-indigo-100 bg-indigo-50/60 p-4">
                      <p className="text-xs font-bold text-slate-900">Approved template required</p>
                      <p className="mt-1 text-[11px] leading-5 text-slate-600">
                        Managed SMS sends the exact DLT-approved template for this Header. The
                        message body cannot be edited freely.
                      </p>
                    </div>

                    {!composeManagedHeader ? (
                      <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-xs leading-5 text-amber-800">
                        This sender is not linked to a ready managed Header. Refresh Managed SMS
                        setup from Company Connections.
                      </div>
                    ) : (
                      <>
                        <label className="block text-xs font-semibold text-slate-700">
                          Managed SMS template
                          <select
                            className="form-input mt-2"
                            value={composeTemplateId}
                            disabled={composeManagedLoading}
                            onChange={(event) => {
                              const templateId = event.target.value
                              setComposeTemplateId(templateId)
                              setComposeManagedPreview(null)
                              const template = composeManagedTemplates.find(
                                (item) => item.id === templateId,
                              )
                              const next: Record<string, string> = {}
                              for (const variable of template?.activeVersion?.variables ?? []) {
                                const current = composeTemplateVariables[variable.key]
                                if (current) next[variable.key] = current
                              }
                              setComposeTemplateVariables(next)
                            }}
                          >
                            <option value="">
                              {composeManagedLoading
                                ? 'Loading templates…'
                                : 'Choose ready template'}
                            </option>
                            {availableManagedSmsTemplates.map((template) => (
                              <option key={template.id} value={template.id}>
                                {template.name} · {template.messageCategory.toLowerCase()}
                              </option>
                            ))}
                          </select>
                        </label>

                        {!composeManagedLoading && availableManagedSmsTemplates.length === 0 && (
                          <div className="rounded-xl border border-dashed border-slate-300 bg-slate-50 p-3 text-[11px] leading-5 text-slate-500">
                            No READY managed templates are approved for Header{' '}
                            <strong>{composeManagedHeader.header}</strong>. Finish template
                            verification in Company Connections before sending.
                          </div>
                        )}

                        {selectedManagedSmsTemplate?.activeVersion && (
                          <div className="rounded-xl border border-slate-200 bg-white p-4">
                            <div>
                              <p className="text-xs font-bold text-slate-900">Template variables</p>
                              <p className="mt-1 text-[11px] leading-5 text-slate-500">
                                Enter only the declared values. Fixed template text stays locked.
                              </p>
                            </div>
                            {selectedManagedSmsTemplate.activeVersion.variables.length === 0 ? (
                              <p className="mt-3 text-[11px] text-slate-400">
                                This approved template has no variables.
                              </p>
                            ) : (
                              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                                {selectedManagedSmsTemplate.activeVersion.variables.map(
                                  (variable) => (
                                    <label
                                      key={variable.key}
                                      className="text-[11px] font-semibold text-slate-700"
                                    >
                                      {variable.key}
                                      <input
                                        className="form-input mt-1.5"
                                        value={composeTemplateVariables[variable.key] ?? ''}
                                        maxLength={variable.maxLength}
                                        onChange={(event) =>
                                          setComposeTemplateVariables((current) => ({
                                            ...current,
                                            [variable.key]: event.target.value,
                                          }))
                                        }
                                        placeholder={`Up to ${variable.maxLength} characters`}
                                      />
                                      {managedVariableErrors[variable.key] && (
                                        <span className="mt-1 block text-[10px] font-medium text-rose-600">
                                          {managedVariableErrors[variable.key]}
                                        </span>
                                      )}
                                    </label>
                                  ),
                                )}
                              </div>
                            )}
                          </div>
                        )}

                        {selectedManagedSmsTemplate && (
                          <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
                            <p className="text-[10px] font-bold uppercase tracking-wide text-slate-400">
                              Message preview
                            </p>
                            <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-slate-700">
                              {composeManagedPreview?.renderedBody ||
                                composeManagedPreview?.bodyText ||
                                selectedManagedSmsTemplate.activeVersion?.bodyText ||
                                'Enter the required variables to preview this message.'}
                            </p>
                            {(composeManagedPreview?.validationErrors.length ?? 0) > 0 && (
                              <div className="mt-3 rounded-lg bg-rose-50 px-3 py-2 text-[11px] text-rose-700">
                                {composeManagedPreview?.validationErrors
                                  .map((item) => item.message)
                                  .join(' · ')}
                              </div>
                            )}
                          </div>
                        )}
                      </>
                    )}
                  </div>
                )}

                {!composeUsesManagedSms && (
                  <label className="mt-4 block text-xs font-semibold text-slate-700">
                    Use template
                    <select
                      className="form-input mt-2"
                      value={composeTemplateId}
                      onChange={(event) => {
                        const templateId = event.target.value
                        setComposeTemplateId(templateId)
                        if (!templateId) {
                          setComposeTemplateVariables({})
                          return
                        }
                        const template = composeTemplates.find((item) => item.id === templateId)
                        if (!template) return
                        setComposeSubject(template.subject ?? '')
                        setComposeBody('')
                        setComposeTemplateVariables((current) => {
                          const next: Record<string, string> = {}
                          template.variables.forEach((variable) => {
                            if (current[variable]) next[variable] = current[variable]
                          })
                          return next
                        })
                      }}
                    >
                      <option value="">
                        {composeUsesMsg91Email
                          ? 'Choose synced MSG91 template'
                          : 'Start from scratch'}
                      </option>
                      {(composeUsesMsg91Email
                        ? msg91ComposeTemplates
                        : availableComposeTemplates
                      ).map((template) => {
                        const binding =
                          composeUsesMsg91Email && template.publishedVersion !== null
                            ? composeBindingByTemplateVersion.get(
                                `${template.id}:${template.publishedVersion}`,
                              )
                            : null
                        const ready = !composeUsesMsg91Email || binding?.sendable === true
                        return (
                          <option key={template.id} value={template.id} disabled={!ready}>
                            {template.name} · {template.category.toLowerCase().replaceAll('_', ' ')}
                            {composeUsesMsg91Email ? ` · ${msg91TemplateStatusLabel(binding)}` : ''}
                          </option>
                        )
                      })}
                    </select>
                    {availableComposeTemplates.length === 0 && (
                      <div className="mt-2 rounded-xl border border-dashed border-slate-300 bg-slate-50 p-3">
                        <p className="text-[10px] font-medium leading-4 text-slate-500">
                          {composeUsesMsg91Email
                            ? msg91ComposeTemplates.length > 0
                              ? 'Your MSG91 templates are visible above but are not sendable yet. Messaging is refreshing their approval status automatically.'
                              : 'No transactional templates yet. Create one, publish it, then sync it to MSG91.'
                            : 'No published email templates yet. Create and publish one to reuse it here.'}
                        </p>
                        <Link
                          to={
                            composeUsesMsg91Email
                              ? '/templates?create=transactional'
                              : '/templates?create=marketing'
                          }
                          className="secondary-button mt-3 inline-flex items-center gap-2 px-3 py-2 text-xs"
                        >
                          <FiPlus aria-hidden="true" />
                          Create new template
                        </Link>
                      </div>
                    )}
                    {availableComposeTemplates.length > 0 && (
                      <Link
                        to={
                          composeUsesMsg91Email
                            ? '/templates?create=transactional'
                            : '/templates?create=marketing'
                        }
                        className="mt-2 inline-flex items-center gap-1.5 text-[11px] font-semibold text-indigo-600 hover:text-indigo-700"
                      >
                        <FiPlus aria-hidden="true" />
                        Create another template
                      </Link>
                    )}
                  </label>
                )}

                {composeUsesManagedSms ? null : composeTemplateId ? (
                  <>
                    <div className="mt-4 rounded-xl border border-indigo-100 bg-indigo-50/60 p-4">
                      <p className="text-xs font-bold text-slate-900">
                        Published template content is locked for this broadcast
                      </p>
                      <p className="mt-1 text-[11px] leading-5 text-slate-600">
                        {composeUsesMsg91Email
                          ? 'MSG91 will use the provider template synced for this exact published version.'
                          : 'Messaging will send the immutable published HTML and text version.'}
                      </p>
                    </div>

                    {selectedComposeTemplate && (
                      <div className="mt-4 rounded-xl border border-slate-200 bg-white p-4">
                        <div>
                          <p className="text-xs font-bold text-slate-900">Template variables</p>
                          <p className="mt-1 text-[11px] leading-5 text-slate-500">
                            Recipient name/metadata overrides these fallback values. Leave a value
                            blank only when every recipient provides that variable.
                          </p>
                        </div>

                        {selectedComposeTemplate.variables.length === 0 ? (
                          <p className="mt-3 text-[11px] text-slate-400">
                            This template has no variables.
                          </p>
                        ) : (
                          <div className="mt-4 grid gap-3 sm:grid-cols-2">
                            {selectedComposeTemplate.variables.map((variable) => {
                              if (automaticTemplateVariables.has(variable)) {
                                return (
                                  <div
                                    key={variable}
                                    className="rounded-lg border border-emerald-100 bg-emerald-50/60 px-3 py-2"
                                  >
                                    <p className="text-[11px] font-semibold text-emerald-800">
                                      {'{{'}
                                      {variable}
                                      {'}}'}
                                    </p>
                                    <p className="mt-0.5 text-[10px] text-emerald-600">
                                      Filled automatically for each recipient
                                    </p>
                                  </div>
                                )
                              }

                              const nameFallback = recipientNameVariables.has(variable)
                              return (
                                <label
                                  key={variable}
                                  className="text-[11px] font-semibold text-slate-700"
                                >
                                  {'{{'}
                                  {variable}
                                  {'}}'}
                                  <input
                                    className="form-input mt-1.5"
                                    value={composeTemplateVariables[variable] ?? ''}
                                    onChange={(event) =>
                                      setComposeTemplateVariables((current) => ({
                                        ...current,
                                        [variable]: event.target.value,
                                      }))
                                    }
                                    placeholder={
                                      nameFallback
                                        ? 'Optional fallback if recipient name is missing'
                                        : 'Enter ' + variable.replaceAll('_', ' ')
                                    }
                                    maxLength={10000}
                                  />
                                </label>
                              )
                            })}
                          </div>
                        )}

                        {editableComposeVariables.length > 0 && (
                          <p className="mt-3 text-[10px] leading-4 text-slate-400">
                            For personalized values, add matching columns/metadata to group members.
                            A recipient-specific value takes precedence over the fallback entered
                            here.
                          </p>
                        )}
                      </div>
                    )}
                  </>
                ) : (
                  <>
                    {channelType === 'EMAIL' && (
                      <label className="mt-4 block text-xs font-semibold text-slate-700">
                        Subject
                        <input
                          className="form-input mt-2"
                          value={composeSubject}
                          maxLength={998}
                          onChange={(event) => setComposeSubject(event.target.value)}
                        />
                      </label>
                    )}

                    <label className="mt-4 block text-xs font-semibold text-slate-700">
                      Message
                      {channelType === 'SMS' && (
                        <span className="ml-2 text-[10px] font-medium text-slate-400">
                          Sent into the configured MSG91 Flow variable
                        </span>
                      )}
                      <textarea
                        className="form-input mt-2 min-h-52"
                        value={composeBody}
                        maxLength={500000}
                        onChange={(event) => setComposeBody(event.target.value)}
                        required={!composeTemplateId}
                      />
                    </label>
                  </>
                )}

                <label className="mt-4 block text-xs font-semibold text-slate-700">
                  Schedule time
                  <input
                    type="datetime-local"
                    className="form-input mt-2"
                    value={composeScheduleAt}
                    onChange={(event) => setComposeScheduleAt(event.target.value)}
                  />
                </label>

                <div className="mt-5 flex flex-wrap justify-end gap-2">
                  <button type="button" className="secondary-button" onClick={closeComposer}>
                    Cancel
                  </button>
                  <button
                    type="button"
                    className="secondary-button"
                    disabled={composeBusy || !composeContentReady}
                    onClick={() => void submitCompose('draft')}
                  >
                    Save draft
                  </button>
                  <button
                    type="button"
                    className="secondary-button gap-2"
                    disabled={
                      composeBusy ||
                      !composeContentReady ||
                      !composeScheduleAt ||
                      !can(PERMISSIONS.BROADCAST_SEND)
                    }
                    onClick={() => void submitCompose('schedule')}
                  >
                    <FiClock aria-hidden="true" />
                    Schedule
                  </button>
                  <button
                    type="button"
                    className="primary-button gap-2"
                    disabled={
                      composeBusy || !composeContentReady || !can(PERMISSIONS.BROADCAST_SEND)
                    }
                    onClick={() => void submitCompose('send')}
                  >
                    <FiSend aria-hidden="true" />
                    {composeBusy ? 'Working…' : 'Send now'}
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </section>
  )
}

function BroadcastHistoryCard({
  message,
  sender,
  canModify,
  onOpen,
  onEdit,
}: {
  message: BroadcastMessageSummary
  sender: BroadcastSender | null
  canModify: boolean
  onOpen: () => void
  onEdit: () => void
}) {
  const progress = progressFor(message)
  return (
    <article
      role="button"
      tabIndex={0}
      className="broadcast-history-card"
      onClick={onOpen}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') onOpen()
      }}
    >
      <div className="flex min-w-0 flex-1 items-start gap-3">
        <span className="mt-0.5 flex size-10 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-indigo-500">
          <FiSend aria-hidden="true" />
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="min-w-0 truncate text-sm font-bold text-slate-900">
              {message.subject || '(No subject)'}
            </h2>
            <StatusBadge status={message.status}>{statusLabel(message.status)}</StatusBadge>
          </div>
          <p className="mt-1 line-clamp-2 text-xs leading-5 text-slate-500">
            {message.bodyPreview || 'No message preview'}
          </p>
          <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-slate-500">
            <span>
              From {senderLabel(sender)} · <BroadcastSenderProviderMeta sender={sender} />
            </span>
            <span>{formatDate(message.scheduledAt || message.createdAt)}</span>
            {message.stats.total > 0 && (
              <>
                <span>Accepted {message.stats.accepted.toLocaleString()}</span>
                <span>Failed {message.stats.failed.toLocaleString()}</span>
                <span>Bounced {message.stats.bounced.toLocaleString()}</span>
              </>
            )}
          </div>
          {message.stats.total > 0 && (
            <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-slate-100">
              <div
                className="h-full rounded-full bg-indigo-500"
                style={{ width: `${progress}%` }}
              />
            </div>
          )}
        </div>
      </div>
      {canModify && (message.status === 'DRAFT' || message.status === 'SCHEDULED') && (
        <button
          type="button"
          className="secondary-button shrink-0 gap-2"
          onClick={(event) => {
            event.stopPropagation()
            onEdit()
          }}
        >
          <FiEdit2 aria-hidden="true" />
          <span className="hidden sm:inline">Edit</span>
        </button>
      )}
    </article>
  )
}

function BroadcastDetail({
  message,
  sender,
  loading,
  recipients,
  recipientsLoading,
  actionBusy,
  canSend,
  canEdit,
  canControl,
  onEdit,
  onAction,
  onRecipientsPage,
}: {
  message: BroadcastMessage | null
  sender: BroadcastSender | null
  loading: boolean
  recipients: Page<BroadcastRecipient>
  recipientsLoading: boolean
  actionBusy: boolean
  canSend: boolean
  canEdit: boolean
  canControl: boolean
  onEdit: (id: string) => void
  onAction: (action: 'pause' | 'resume' | 'cancel' | 'send', id: string) => void
  onRecipientsPage: (page: number) => void
}) {
  if (loading && !message) {
    return <div className="p-5"><CardSkeleton rows={6} /></div>
  }

  if (!message) {
    return <div className="px-6 py-16 text-center text-sm text-slate-500">Broadcast not found.</div>
  }

  const progress = progressFor(message)
  const managedSms = isManagedSmsSender(sender)
  return (
    <div className="channel-message-scroll min-h-[calc(100dvh-214px)] p-4 sm:p-5">
      <div className="w-full">
        <div className="flex flex-wrap items-center justify-end gap-3">
          <div className="flex flex-wrap gap-2">
            {(message.status === 'DRAFT' || message.status === 'SCHEDULED') && canEdit && (
              <button
                type="button"
                className="secondary-button gap-2"
                onClick={() => onEdit(message.id)}
              >
                <FiEdit2 aria-hidden="true" />
                Edit
              </button>
            )}
            {(message.status === 'DRAFT' || message.status === 'SCHEDULED') && canSend && (
              <button
                type="button"
                className="primary-button gap-2"
                disabled={actionBusy}
                onClick={() => onAction('send', message.id)}
              >
                <FiSend aria-hidden="true" />
                Send now
              </button>
            )}
            {message.status === 'RUNNING' && canControl && (
              <button
                type="button"
                className="secondary-button gap-2"
                disabled={actionBusy}
                onClick={() => onAction('pause', message.id)}
              >
                <FiPause aria-hidden="true" />
                Pause
              </button>
            )}
            {message.status === 'PAUSED' && canControl && (
              <button
                type="button"
                className="secondary-button gap-2"
                disabled={actionBusy}
                onClick={() => onAction('resume', message.id)}
              >
                <FiPlay aria-hidden="true" />
                Resume
              </button>
            )}
            {['SCHEDULED', 'QUEUED', 'RUNNING', 'PAUSED'].includes(message.status) && canControl && (
              <button
                type="button"
                className="secondary-button text-rose-600"
                disabled={actionBusy}
                onClick={() => onAction('cancel', message.id)}
              >
                Cancel
              </button>
            )}
          </div>
        </div>

        <article className="mt-4 rounded-2xl border border-slate-200 bg-white p-5">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0">
              <StatusBadge status={message.status}>{statusLabel(message.status)}</StatusBadge>
              <h1 className="mt-3 text-xl font-bold text-slate-950">
                {message.subject || message.bodyText?.slice(0, 80) || `${message.channelType || 'Broadcast'} message`}
              </h1>
              <p className="mt-1 text-xs text-slate-500">
                From {senderLabel(sender)} · <BroadcastSenderProviderMeta sender={sender} />
              </p>
              <p className="mt-1 text-xs text-slate-500">
                Created {formatDate(message.createdAt)}
                {message.scheduledAt ? ` · scheduled ${formatDate(message.scheduledAt)}` : ''}
              </p>
            </div>
            {message.stats.total > 0 && (
              <div className="text-right">
                <p className="text-2xl font-bold text-slate-950">{progress}%</p>
                <p className="text-[11px] text-slate-500">processed</p>
              </div>
            )}
          </div>

          {message.stats.total > 0 && (
            <div className="mt-4 h-2 overflow-hidden rounded-full bg-slate-100">
              <div
                className="h-full rounded-full bg-indigo-500"
                style={{ width: `${progress}%` }}
              />
            </div>
          )}

          <div className="mt-5 whitespace-pre-wrap rounded-xl bg-slate-50 px-4 py-4 text-sm leading-6 text-slate-700">
            {message.bodyText}
          </div>
        </article>

        {message.runId && (
          <>
            <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-6">
              <StatCard label="Total" value={message.stats.total} />
              <StatCard label="Accepted" value={message.stats.accepted} />
              <StatCard label="Failed" value={message.stats.failed} />
              <StatCard label="Bounced" value={message.stats.bounced} />
              <StatCard label="Unknown" value={message.stats.unknown} />
              <StatCard label="Skipped" value={message.stats.skipped} />
            </div>

            <div className="mt-5 overflow-hidden rounded-2xl border border-slate-200 bg-white">
              <div className="flex items-start justify-between gap-3 border-b border-slate-100 px-4 py-3">
                <div>
                  <p className="text-sm font-bold text-slate-900">Recipients</p>
                  <p className="mt-0.5 text-xs text-slate-500">
                    Provider acceptance is shown as Accepted. Delivery and bounce status update only when provider events are available.
                  </p>
                </div>
                <button
                  type="button"
                  className="secondary-button shrink-0 gap-2"
                  disabled={recipientsLoading}
                  onClick={() => onRecipientsPage(recipients.number)}
                >
                  <FiRefreshCw aria-hidden="true" />
                  <span className="hidden sm:inline">
                    {recipientsLoading ? 'Refreshing…' : 'Refresh'}
                  </span>
                </button>
              </div>
              <div className="overflow-x-auto">
                <table className="mobile-card-table w-full min-w-[760px] text-left text-xs">
                  <thead className="bg-slate-50 text-[10px] uppercase tracking-wide text-slate-500">
                    <tr>
                      <th className="px-4 py-3">Recipient</th>
                      <th className="px-4 py-3">Status</th>
                      <th className="px-4 py-3">Attempts</th>
                      <th className="px-4 py-3">Accepted</th>
                      <th className="px-4 py-3">Result</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {recipientsLoading && recipients.content.length === 0 ? (
                      <tr>
                        <td colSpan={5} className="p-4"><TableSkeleton rows={6} columns={5} /></td>
                      </tr>
                    ) : recipients.content.length === 0 ? (
                      <tr>
                        <td colSpan={5} className="px-4 py-10 text-center text-slate-500">
                          Recipient snapshot is still being prepared.
                        </td>
                      </tr>
                    ) : (
                      recipients.content.map((recipient) => (
                        <tr key={recipient.id}>
                          <td className="px-4 py-3" data-label="Recipient">
                            <p className="font-semibold text-slate-800">{recipient.destination}</p>
                            {recipient.displayName && (
                              <p className="mt-0.5 text-[11px] text-slate-500">
                                {recipient.displayName}
                              </p>
                            )}
                          </td>
                          <td className="px-4 py-3" data-label="Status">
                            <StatusBadge status={recipient.status}>
                              {recipientStatusLabel(recipient.status)}
                            </StatusBadge>
                          </td>
                          <td className="px-4 py-3 text-slate-500" data-label="Attempts">
                            {recipient.attemptCount}
                          </td>
                          <td className="px-4 py-3 text-slate-500" data-label="Accepted">
                            {formatDate(recipient.acceptedAt)}
                          </td>
                          <td className="px-4 py-3 text-slate-500" data-label="Result">
                            {managedSms
                              ? recipient.status === 'FAILED'
                                ? 'Delivery failed'
                                : recipient.status === 'UNKNOWN'
                                  ? 'Delivery status pending'
                                  : '—'
                              : recipient.failureReason || recipient.failureCode || '—'}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>

              {recipients.totalPages > 1 && (
                <div className="flex items-center justify-between gap-3 border-t border-slate-100 px-4 py-3">
                  <button
                    type="button"
                    className="secondary-button"
                    disabled={recipientsLoading || recipients.first}
                    onClick={() => onRecipientsPage(Math.max(0, recipients.number - 1))}
                  >
                    Previous
                  </button>
                  <span className="text-xs text-slate-500">
                    Page {recipients.number + 1} of {recipients.totalPages}
                  </span>
                  <button
                    type="button"
                    className="secondary-button"
                    disabled={recipientsLoading || recipients.last}
                    onClick={() => onRecipientsPage(recipients.number + 1)}
                  >
                    Next
                  </button>
                </div>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  )
}

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white px-3 py-3">
      <p className="text-[10px] font-bold uppercase tracking-wide text-slate-400">{label}</p>
      <p className="mt-1 text-lg font-bold text-slate-900">{value.toLocaleString()}</p>
    </div>
  )
}
