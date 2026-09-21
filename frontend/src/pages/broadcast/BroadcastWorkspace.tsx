import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import {
  FiAlertCircle,
  FiArrowLeft,
  FiCheckCircle,
  FiClock,
  FiEdit2,
  FiMail,
  FiPause,
  FiPlay,
  FiRefreshCw,
  FiSend,
  FiUpload,
  FiUsers,
  FiX,
} from 'react-icons/fi'
import { useLocation, useSearchParams } from 'react-router'
import AmazonSesConnectModal from '../../components/broadcast/AmazonSesConnectModal'
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
import { apiErrorMessage } from '../../lib/api/apiClient'
import {
  applyBroadcastMutation,
  applyBroadcastProgress,
  fetchBroadcastGroups,
  fetchBroadcastHistory,
  upsertBroadcastGroup,
} from '../../store/broadcast/broadcastSlice'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId } from '../../store/workspace/workspaceSlice'

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
  const terminal = ['COMPLETED', 'PARTIALLY_FAILED', 'CANCELLED', 'FAILED'].includes(
    message.status,
  )
  const value = Math.floor((statsProcessed(message.stats) * 100) / message.stats.total)
  return Math.min(terminal ? 100 : 99, value)
}

const statusLabel = (status: BroadcastMessageStatus) =>
  status
    .toLowerCase()
    .replaceAll('_', ' ')
    .replace(/\b\w/g, (char) => char.toUpperCase())

const statusBadge = (status: BroadcastMessageStatus) => {
  if (status === 'COMPLETED') return 'bg-emerald-50 text-emerald-700'
  if (status === 'RUNNING') return 'bg-indigo-50 text-indigo-700'
  if (status === 'SCHEDULED' || status === 'QUEUED') return 'bg-blue-50 text-blue-700'
  if (status === 'PAUSED') return 'bg-amber-50 text-amber-700'
  if (status === 'PARTIALLY_FAILED') return 'bg-orange-50 text-orange-700'
  if (status === 'FAILED' || status === 'CANCELLED') return 'bg-rose-50 text-rose-700'
  return 'bg-slate-100 text-slate-600'
}

const recipientStatusLabel = (status: BroadcastRecipientStatus) => {
  if (status === 'ACCEPTED_BY_PROVIDER') return 'Sent'
  if (status === 'RETRY') return 'Retrying'
  return status
    .toLowerCase()
    .replaceAll('_', ' ')
    .replace(/\b\w/g, (char) => char.toUpperCase())
}

const providerLabel = (provider?: string | null) => {
  if (!provider) return 'Legacy mailbox'
  if (provider === 'AMAZON_SES') return 'Amazon SES'
  if (provider === 'AZURE_EMAIL') return 'Azure Email'
  if (provider === 'SENDGRID') return 'SendGrid'
  return provider.replaceAll('_', ' ')
}

const senderLabel = (sender?: BroadcastSender | null) =>
  sender?.displayName ? `${sender.displayName} <${sender.fromAddress}>` : sender?.fromAddress || 'Legacy mailbox sender'

const formatDate = (value?: string | null) => {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

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
  const broadcast = useAppSelector((state) => state.broadcast)
  const { can } = useIam()
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()
  const channelType = channelFromPath(location.pathname)
  const channelLabel = labelForChannel(channelType)
  const selectedGroupId = searchParams.get('groupId')
  const selectedBroadcastId = searchParams.get('broadcastId')
  const newGroupOpen = searchParams.get('newGroup') === '1'
  const editGroupOpen = searchParams.get('editGroup') === '1'
  const composeOpen = searchParams.get('compose') === '1'
  const editBroadcastId = searchParams.get('editBroadcastId')

  const groups =
    broadcast.groupsCompanyId === companyId && broadcast.groupsChannelType === channelType
      ? broadcast.groups
      : []
  const selectedGroup = groups.find((group) => group.id === selectedGroupId) ?? null
  const history =
    broadcast.historyCompanyId === companyId && broadcast.historyGroupId === selectedGroupId
      ? broadcast.messages
      : []

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
  const [sesConnectOpen, setSesConnectOpen] = useState(false)
  const [composeSubject, setComposeSubject] = useState('')
  const [composeBody, setComposeBody] = useState('')
  const [composeScheduleAt, setComposeScheduleAt] = useState('')
  const [composeBusy, setComposeBusy] = useState(false)
  const [actionBusy, setActionBusy] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [recipientsLoading, setRecipientsLoading] = useState(false)
  const [realtimeConnected, setRealtimeConnected] = useState(realtimeClient.isConnected())
  const recipientRefreshTimer = useRef<number | null>(null)
  const lastRecipientRefreshAt = useRef(0)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const hasActiveHistory = history.some((message) =>
    ['QUEUED', 'RUNNING', 'PAUSED'].includes(message.status),
  )
  const selectedMessageActive =
    selectedMessage !== null &&
    ['QUEUED', 'RUNNING', 'PAUSED'].includes(selectedMessage.status)

  const activeSenders = useMemo(
    () =>
      senders.filter(
        (sender) =>
          sender.status === 'ACTIVE' &&
          sender.sendingEnabled &&
          sender.provider === 'AMAZON_SES',
      ),
    [senders],
  )

  const sendersById = useMemo(
    () => new Map(senders.map((sender) => [sender.id, sender])),
    [senders],
  )

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
      await dispatch(fetchBroadcastGroups({ companyId, channelType, force }))
    },
    [channelType, companyId, dispatch],
  )

  const refreshHistory = useCallback(
    async (force = true) => {
      if (!companyId || !selectedGroupId) return
      await dispatch(
        fetchBroadcastHistory({ companyId, groupId: selectedGroupId, force }),
      )
    },
    [companyId, dispatch, selectedGroupId],
  )

  useEffect(() => {
    if (!companyId) return
    void dispatch(fetchBroadcastGroups({ companyId, channelType }))
  }, [channelType, companyId, dispatch])

  const refreshSenders = useCallback(async () => {
    if (!companyId || channelType !== 'EMAIL') {
      setSenders([])
      return
    }
    try {
      setSenders(await broadcastApi.senders(companyId))
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Unable to load Amazon SES senders'))
    }
  }, [channelType, companyId])

  useEffect(() => {
    void refreshSenders()
  }, [refreshSenders])

  useEffect(() => {
    if (!companyId || !selectedGroupId || selectedGroup) return
    let active = true
    broadcastApi
      .group(companyId, selectedGroupId)
      .then((group) => {
        if (active) dispatch(upsertBroadcastGroup(group))
      })
      .catch((requestError) => {
        if (active) setError(apiErrorMessage(requestError, 'Unable to load broadcast group'))
      })
    return () => {
      active = false
    }
  }, [companyId, dispatch, selectedGroup, selectedGroupId])

  useEffect(() => {
    if (!companyId || !selectedGroupId) return
    void dispatch(fetchBroadcastHistory({ companyId, groupId: selectedGroupId }))
  }, [companyId, dispatch, selectedGroupId])

  useEffect(() => {
    if (!companyId || !selectedBroadcastId) {
      setSelectedMessage(null)
      setRecipients(emptyPage())
      return
    }
    let active = true
    setDetailLoading(true)
    broadcastApi
      .message(companyId, selectedBroadcastId)
      .then(async (message) => {
        if (!active) return
        setSelectedMessage(message)
        dispatch(applyBroadcastMutation(message))
        if (message.runId) {
          setRecipientsLoading(true)
          try {
            const page = await broadcastApi.recipients(companyId, selectedBroadcastId)
            if (active) setRecipients(page)
          } finally {
            if (active) setRecipientsLoading(false)
          }
        } else {
          setRecipients(emptyPage())
        }
      })
      .catch((requestError) => {
        if (active) setError(apiErrorMessage(requestError, 'Unable to load broadcast details'))
      })
      .finally(() => {
        if (active) setDetailLoading(false)
      })
    return () => {
      active = false
    }
  }, [companyId, dispatch, selectedBroadcastId])

  useEffect(
    () => realtimeClient.subscribeConnection(setRealtimeConnected),
    [],
  )

  useEffect(() => {
    if (!companyId) return

    const scheduleRecipientRefresh = (broadcastId: string) => {
      if (broadcastId !== selectedBroadcastId) return
      const now = Date.now()
      const delay = Math.max(250, 1500 - (now - lastRecipientRefreshAt.current))
      if (recipientRefreshTimer.current !== null) return

      recipientRefreshTimer.current = window.setTimeout(() => {
        recipientRefreshTimer.current = null
        lastRecipientRefreshAt.current = Date.now()
        void broadcastApi
          .recipients(companyId, broadcastId, recipients.number)
          .then(setRecipients)
          .catch(() => undefined)
      }, delay)
    }

    const unsubscribe = realtimeClient.subscribe<BroadcastProgressEvent>(
      'broadcast.progress.changed',
      (event) => {
        dispatch(applyBroadcastProgress(event.payload))
        setSelectedMessage((current) => {
          if (!current || current.id !== event.payload.broadcastId) return current
          return {
            ...current,
            runId: event.payload.runId,
            status:
              event.payload.status === 'PREPARING' ? 'QUEUED' : event.payload.status,
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
  }, [companyId, dispatch, recipients.number, selectedBroadcastId])

  useEffect(() => {
    if (realtimeConnected || !companyId) return

    if (!selectedMessageActive && !hasActiveHistory) return

    let stopped = false
    const poll = async () => {
      if (stopped) return
      try {
        if (selectedBroadcastId) {
          const message = await broadcastApi.message(companyId, selectedBroadcastId)
          if (stopped) return
          setSelectedMessage(message)
          dispatch(applyBroadcastMutation(message))
          if (message.runId) {
            const page = await broadcastApi.recipients(
              companyId,
              selectedBroadcastId,
              recipients.number,
            )
            if (!stopped) setRecipients(page)
          }
        }
        if (selectedGroupId) {
          await dispatch(
            fetchBroadcastHistory({
              companyId,
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
  ])

  useEffect(() => {
    if (!composeOpen) return
    let active = true

    const reset = () => {
      setComposeSubject('')
      setComposeBody('')
      setComposeScheduleAt('')
      setComposeSenderId((current) =>
        activeSenders.some((connection) => connection.id === current)
          ? current
          : activeSenders[0]?.id ?? '',
      )
    }

    if (!editBroadcastId || !companyId) {
      reset()
      return
    }

    setComposeBusy(true)
    broadcastApi
      .message(companyId, editBroadcastId)
      .then((message) => {
        if (!active) return
        setComposeSenderId(message.senderId ?? '')
        setComposeSubject(message.subject ?? '')
        setComposeBody(message.bodyText)
        setComposeScheduleAt(
          message.scheduledAt ? new Date(message.scheduledAt).toISOString().slice(0, 16) : '',
        )
      })
      .catch((requestError) => {
        if (active) setError(apiErrorMessage(requestError, 'Unable to load broadcast draft'))
      })
      .finally(() => {
        if (active) setComposeBusy(false)
      })

    return () => {
      active = false
    }
  }, [activeSenders, companyId, composeOpen, editBroadcastId])

  const handleSesConnected = (sender: BroadcastSender) => {
    setSenders((current) => [sender, ...current.filter((item) => item.id !== sender.id)])
    setComposeSenderId(sender.id)
    setSesConnectOpen(false)
    setNotice(
      sender.productionAccessEnabled
        ? 'Amazon SES connected and ready for broadcasts.'
        : 'Amazon SES connected in sandbox mode. Request production access in AWS before sending to unverified recipients.',
    )
  }

  const openNewGroup = () => {
    setGroupName('')
    setGroupDescription('')
    updateSearch({
      newGroup: '1',
      editGroup: null,
      broadcastId: null,
      compose: null,
      editBroadcastId: null,
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

  const createGroup = async (event: FormEvent) => {
    event.preventDefault()
    if (!companyId || !groupName.trim() || channelType !== 'EMAIL') return
    setGroupBusy(true)
    setError(null)
    try {
      const group = await broadcastApi.createGroup(companyId, {
        name: groupName.trim(),
        description: groupDescription.trim() || null,
        channelType,
      })
      dispatch(upsertBroadcastGroup(group))
      updateSearch({ newGroup: null, groupId: group.id, broadcastId: null })
      setNotice('Broadcast group created. Add recipients when you are ready.')
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Unable to create broadcast group'))
    } finally {
      setGroupBusy(false)
    }
  }

  const updateGroup = async (event: FormEvent) => {
    event.preventDefault()
    if (!companyId || !selectedGroupId || !groupName.trim()) return
    setGroupBusy(true)
    setError(null)
    try {
      const group = await broadcastApi.updateGroup(companyId, selectedGroupId, {
        name: groupName.trim(),
        description: groupDescription.trim() || null,
      })
      dispatch(upsertBroadcastGroup(group))
      updateSearch({ editGroup: null })
      setNotice('Broadcast group updated.')
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Unable to update broadcast group'))
    } finally {
      setGroupBusy(false)
    }
  }

  const openMembers = async () => {
    if (!companyId || !selectedGroupId) return
    setMembersOpen(true)
    setMembers(emptyPage())
    try {
      setMembers(await broadcastApi.members(companyId, selectedGroupId))
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Unable to load group members'))
    }
  }

  const loadMembersPage = async (page: number) => {
    if (!companyId || !selectedGroupId) return
    setMemberBusy(true)
    try {
      setMembers(await broadcastApi.members(companyId, selectedGroupId, page))
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Unable to load group members'))
    } finally {
      setMemberBusy(false)
    }
  }

  const afterMemberChange = async (result?: BroadcastMemberImportResult) => {
    await Promise.all([loadMembersPage(0), refreshGroups(true)])
    if (result) {
      setNotice(
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

    setMemberBusy(true)
    setError(null)
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
        processed += result.processed
        insertedOrReactivated += result.insertedOrReactivated
        duplicates += result.duplicates
        invalid += result.invalid
      }
      setMemberEmails('')
      await afterMemberChange({
        processed,
        insertedOrReactivated,
        duplicates,
        invalid,
      })
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Unable to add recipients'))
    } finally {
      setMemberBusy(false)
    }
  }

  const importCsv = async () => {
    if (!companyId || !selectedGroupId || !memberCsv) return
    setMemberBusy(true)
    setError(null)
    try {
      const result = await broadcastApi.importMembers(companyId, selectedGroupId, memberCsv)
      setMemberCsv(null)
      await afterMemberChange(result)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Unable to import CSV'))
    } finally {
      setMemberBusy(false)
    }
  }

  const removeMember = async (memberId: string) => {
    if (!companyId || !selectedGroupId) return
    setMemberBusy(true)
    try {
      await broadcastApi.removeMember(companyId, selectedGroupId, memberId)
      await afterMemberChange()
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Unable to remove recipient'))
    } finally {
      setMemberBusy(false)
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
    if (!companyId || !selectedGroupId || !composeSenderId || !composeBody.trim()) {
      throw new Error('Choose a sender and enter a message')
    }
    const input = {
      senderId: composeSenderId,
      subject: composeSubject.trim() || null,
      bodyText: composeBody,
    }
    const message = editBroadcastId
      ? await broadcastApi.updateMessage(companyId, editBroadcastId, input)
      : await broadcastApi.createMessage(companyId, selectedGroupId, input)
    dispatch(applyBroadcastMutation(message))
    return message
  }

  const submitCompose = async (mode: 'draft' | 'send' | 'schedule') => {
    if (!companyId) return
    setComposeBusy(true)
    setError(null)
    try {
      const draft = await saveDraft()
      let result = draft
      if (mode === 'send') {
        result = await broadcastApi.send(companyId, draft.id)
        setNotice('Broadcast queued. Delivery progress will update live.')
      } else if (mode === 'schedule') {
        if (!composeScheduleAt) throw new Error('Choose a schedule time')
        const scheduleDate = new Date(composeScheduleAt)
        if (Number.isNaN(scheduleDate.getTime()) || scheduleDate.getTime() <= Date.now()) {
          throw new Error('Schedule time must be in the future')
        }
        result = await broadcastApi.schedule(
          companyId,
          draft.id,
          scheduleDate.toISOString(),
        )
        setNotice('Broadcast scheduled.')
      } else {
        setNotice('Broadcast draft saved.')
      }
      dispatch(applyBroadcastMutation(result))
      closeComposer()
      await refreshHistory(true)
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : apiErrorMessage(requestError, 'Unable to save broadcast'),
      )
    } finally {
      setComposeBusy(false)
    }
  }

  const runAction = async (
    action: 'pause' | 'resume' | 'cancel' | 'send',
    messageId: string,
  ) => {
    if (!companyId) return
    setActionBusy(true)
    setError(null)
    try {
      const result =
        action === 'pause'
          ? await broadcastApi.pause(companyId, messageId)
          : action === 'resume'
            ? await broadcastApi.resume(companyId, messageId)
            : action === 'cancel'
              ? await broadcastApi.cancel(companyId, messageId)
              : await broadcastApi.send(companyId, messageId)
      dispatch(applyBroadcastMutation(result))
      setSelectedMessage((current) => (current?.id === result.id ? result : current))
      await refreshHistory(true)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, `Unable to ${action} broadcast`))
    } finally {
      setActionBusy(false)
    }
  }

  const loadRecipientsPage = async (page: number) => {
    if (!companyId || !selectedBroadcastId) return
    setRecipientsLoading(true)
    try {
      setRecipients(
        await broadcastApi.recipients(companyId, selectedBroadcastId, page),
      )
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Unable to load recipients'))
    } finally {
      setRecipientsLoading(false)
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
        groupId: selectedGroupId,
        page: broadcast.historyPage + 1,
        append: true,
      }),
    )
  }

  if (channelType !== 'EMAIL') {
    return (
      <section className="channel-workspace-page">
        <div className="channel-workspace-toolbar">
          <div className="min-w-0">
            <p className="eyebrow">BROADCAST</p>
            <p className="mt-1 text-xs font-semibold text-slate-700">{channelLabel}</p>
          </div>
        </div>
        <div className="channel-workspace-body">
          <main className="channel-message-surface flex items-center justify-center px-6 text-center">
            <div className="max-w-md py-14">
              <FiSend className="mx-auto text-4xl text-slate-300" aria-hidden="true" />
              <h1 className="mt-4 text-base font-bold text-slate-900">
                {channelLabel} Broadcast is coming next
              </h1>
              <p className="mt-2 text-sm leading-6 text-slate-500">
                The production broadcast engine is live for email first. The same reusable group
                and execution model is ready for {channelLabel.toLowerCase()} transport later.
              </p>
            </div>
          </main>
        </div>
      </section>
    )
  }

  return (
    <section className="channel-workspace-page">
      <div className="channel-workspace-toolbar">
        <div className="min-w-0">
          <p className="eyebrow">BROADCAST</p>
          <p className="mt-1 truncate text-xs font-semibold text-slate-700">
            {selectedGroup?.name ?? 'Email groups'}
          </p>
        </div>

        <div className="inbox-toolbar-actions ml-auto flex items-center gap-2">
          <button
            type="button"
            className="secondary-button gap-2"
            onClick={() => {
              void refreshGroups(true)
              void refreshHistory(true)
              void refreshSenders()
            }}
          >
            <FiRefreshCw aria-hidden="true" />
            <span className="inbox-action-label">Refresh</span>
          </button>

          {selectedGroup && can(PERMISSIONS.BROADCAST_MODIFY) && (
            <button type="button" className="secondary-button gap-2" onClick={() => void openMembers()}>
              <FiUsers aria-hidden="true" />
              <span className="inbox-action-label">Members</span>
            </button>
          )}

          {selectedGroup && can(PERMISSIONS.BROADCAST_CREATE) ? (
            activeSenders.length > 0 ? (
              <button type="button" className="primary-button gap-2" onClick={() => openComposer()}>
                <FiSend aria-hidden="true" />
                <span className="inbox-action-label">New broadcast</span>
              </button>
            ) : (
              <button type="button" className="primary-button gap-2" onClick={() => setSesConnectOpen(true)}>
                <FiMail aria-hidden="true" />
                <span className="inbox-action-label">Connect SES</span>
              </button>
            )
          ) : (
            <button type="button" className="primary-button gap-2" onClick={openNewGroup}>
              <FiUsers aria-hidden="true" />
              <span className="inbox-action-label">New group</span>
            </button>
          )}
        </div>
      </div>

      {(error || notice) && (
        <div className="px-3 pt-3">
          {error && (
            <div className="flex items-start gap-2 rounded-xl border border-rose-100 bg-rose-50 px-3 py-2.5 text-xs text-rose-700">
              <FiAlertCircle className="mt-0.5 shrink-0" aria-hidden="true" />
              <span className="min-w-0 flex-1">{error}</span>
              <button type="button" onClick={() => setError(null)} aria-label="Dismiss error">
                <FiX aria-hidden="true" />
              </button>
            </div>
          )}
          {notice && (
            <div className="mt-2 flex items-start gap-2 rounded-xl border border-emerald-100 bg-emerald-50 px-3 py-2.5 text-xs text-emerald-700">
              <FiCheckCircle className="mt-0.5 shrink-0" aria-hidden="true" />
              <span className="min-w-0 flex-1">{notice}</span>
              <button type="button" onClick={() => setNotice(null)} aria-label="Dismiss notice">
                <FiX aria-hidden="true" />
              </button>
            </div>
          )}
        </div>
      )}

      <div className="channel-workspace-body">
        <main className="channel-message-surface">
          {!selectedGroupId ? (
            <div className="flex min-h-[calc(100dvh-210px)] items-center justify-center px-6 text-center">
              <div className="max-w-md">
                <FiUsers className="mx-auto text-4xl text-slate-300" aria-hidden="true" />
                <h1 className="mt-4 text-base font-bold text-slate-900">
                  Create a reusable broadcast group
                </h1>
                <p className="mt-2 text-sm leading-6 text-slate-500">
                  Save recipients once, reuse the same group for future broadcasts, and keep every
                  send with its own immutable recipient snapshot and delivery history.
                </p>
                {can(PERMISSIONS.BROADCAST_CREATE) && (
                  <button type="button" className="primary-button mt-5 gap-2" onClick={openNewGroup}>
                    <FiUsers aria-hidden="true" />
                    Create group
                  </button>
                )}
              </div>
            </div>
          ) : selectedBroadcastId ? (
            <BroadcastDetail
              message={selectedMessage}
              sender={
                selectedMessage?.senderId
                  ? sendersById.get(selectedMessage.senderId) ?? null
                  : null
              }
              loading={detailLoading}
              recipients={recipients}
              recipientsLoading={recipientsLoading}
              actionBusy={actionBusy}
              canSend={
                can(PERMISSIONS.BROADCAST_SEND) && Boolean(selectedMessage?.senderId)
              }
              canModify={can(PERMISSIONS.BROADCAST_MODIFY)}
              onBack={() => updateSearch({ broadcastId: null })}
              onEdit={(id) => openComposer(id)}
              onAction={(action, id) => void runAction(action, id)}
              onRecipientsPage={(page) => void loadRecipientsPage(page)}
            />
          ) : (
            <div className="channel-message-scroll min-h-[calc(100dvh-214px)] p-4 sm:p-5">
              <div className="mx-auto max-w-4xl">
                <div className="rounded-2xl border border-slate-200 bg-white p-4">
                  <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
                    <div className="min-w-0">
                      <p className="text-base font-bold text-slate-950">{selectedGroup?.name}</p>
                      <p className="mt-1 text-xs leading-5 text-slate-500">
                        {selectedGroup?.memberCount.toLocaleString() ?? 0} recipients
                        {selectedGroup?.description ? ` · ${selectedGroup.description}` : ''}
                      </p>
                    </div>

                    <div className="flex min-w-0 flex-col gap-3 sm:flex-row sm:items-end sm:justify-end">
                      <label className="min-w-0 sm:w-80">
                        <span className="mb-1.5 flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wide text-slate-500">
                          <FiMail aria-hidden="true" />
                          Send from Amazon SES
                        </span>
                        <select
                          className="form-input w-full"
                          value={composeSenderId}
                          onChange={(event) => setComposeSenderId(event.target.value)}
                          disabled={activeSenders.length === 0}
                          aria-label="Select Amazon SES sender"
                        >
                          {activeSenders.length === 0 ? (
                            <option value="">No Amazon SES sender connected</option>
                          ) : (
                            <>
                              <option value="">Choose SES sender</option>
                              {activeSenders.map((sender) => (
                                <option key={sender.id} value={sender.id}>
                                  {sender.fromAddress} · {sender.region}
                                  {sender.productionAccessEnabled ? '' : ' · Sandbox'}
                                </option>
                              ))}
                            </>
                          )}
                        </select>
                      </label>

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
                        {can(PERMISSIONS.BROADCAST_MODIFY) && (
                          <button
                            type="button"
                            className="secondary-button gap-2"
                            onClick={() => setSesConnectOpen(true)}
                          >
                            <FiMail aria-hidden="true" />
                            Connect Amazon SES
                          </button>
                        )}
                        {can(PERMISSIONS.BROADCAST_CREATE) && (
                          <button
                            type="button"
                            className="primary-button gap-2"
                            onClick={() => openComposer()}
                            disabled={activeSenders.length === 0}
                            title={
                              activeSenders.length === 0
                                ? 'Connect Amazon SES before creating a broadcast'
                                : undefined
                            }
                          >
                            <FiSend aria-hidden="true" />
                            New broadcast
                          </button>
                        )}
                      </div>
                    </div>
                  </div>

                  {activeSenders.length > 0 && !composeSenderId && (
                    <p className="mt-3 text-xs font-medium text-amber-700">
                      Choose an Amazon SES sender before creating the broadcast.
                    </p>
                  )}
                </div>

                <div className="mt-5">
                  <div className="mb-3 flex items-center justify-between gap-3">
                    <div>
                      <p className="text-sm font-bold text-slate-900">Broadcast history</p>
                      <p className="mt-0.5 text-xs text-slate-500">
                        Every send keeps the recipient snapshot used at that time.
                      </p>
                    </div>
                  </div>

                  {broadcast.historyStatus === 'loading' && history.length === 0 ? (
                    <div className="panel px-5 py-12 text-center text-sm text-slate-500">
                      Loading broadcasts…
                    </div>
                  ) : history.length === 0 ? (
                    <div className="panel px-5 py-12 text-center">
                      <FiSend className="mx-auto text-3xl text-slate-300" aria-hidden="true" />
                      <p className="mt-3 text-sm font-semibold text-slate-800">No broadcasts yet</p>
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
                          sender={message.senderId ? sendersById.get(message.senderId) ?? null : null}
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
          )}
        </main>
      </div>

      {newGroupOpen && (
        <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4">
          <form className="mobile-sheet w-full max-w-md rounded-2xl bg-white p-5 shadow-xl" onSubmit={createGroup}>
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
              <button type="button" className="secondary-button" onClick={() => updateSearch({ newGroup: null })}>
                Cancel
              </button>
              <button type="submit" className="primary-button" disabled={groupBusy || !groupName.trim()}>
                {groupBusy ? 'Creating…' : 'Create group'}
              </button>
            </div>
          </form>
        </div>
      )}

      {editGroupOpen && selectedGroup && (
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

      {membersOpen && selectedGroup && (
        <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4">
          <div className="mobile-sheet flex max-h-[90dvh] w-full max-w-4xl flex-col rounded-2xl bg-white shadow-xl">
            <div className="flex items-start justify-between gap-4 border-b border-slate-100 p-5">
              <div>
                <p className="text-base font-bold text-slate-950">{selectedGroup.name} members</p>
                <p className="mt-1 text-xs text-slate-500">
                  {selectedGroup.memberCount.toLocaleString()} active recipients
                </p>
              </div>
              <button type="button" className="admin-back-button" onClick={() => setMembersOpen(false)} aria-label="Close">
                <FiX aria-hidden="true" />
              </button>
            </div>

            <div className="grid min-h-0 flex-1 gap-4 overflow-y-auto p-5 lg:grid-cols-[minmax(0,320px)_minmax(0,1fr)]">
              <div className="space-y-4">
                <div className="rounded-xl border border-slate-200 p-4">
                  <p className="text-xs font-bold text-slate-800">Paste email addresses</p>
                  <p className="mt-1 text-[11px] leading-5 text-slate-500">
                    Separate with new lines, commas or semicolons. Requests are automatically
                    chunked to 1,000 recipients.
                  </p>
                  <textarea
                    className="form-input mt-3 min-h-36"
                    value={memberEmails}
                    onChange={(event) => setMemberEmails(event.target.value)}
                    placeholder={'alice@example.com\nbob@example.com'}
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
                    Best for large lists. Include an <strong>email</strong> or{' '}
                    <strong>destination</strong> header. The server streams the file in bounded
                    batches.
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

      {composeOpen && selectedGroup && (
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
              <button type="button" className="admin-back-button" onClick={closeComposer} aria-label="Close">
                <FiX aria-hidden="true" />
              </button>
            </div>

            {activeSenders.length === 0 ? (
              <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 px-4 py-4 text-xs leading-5 text-amber-800">
                <p className="font-bold">Amazon SES is required for email Broadcast.</p>
                <p className="mt-1">
                  Gmail, Microsoft and SMTP remain available for Inbox and normal email, but are no
                  longer used for high-volume Broadcast.
                </p>
                {can(PERMISSIONS.BROADCAST_MODIFY) && (
                  <button
                    type="button"
                    className="secondary-button mt-3 gap-2"
                    onClick={() => setSesConnectOpen(true)}
                  >
                    <FiMail aria-hidden="true" />
                    Connect Amazon SES
                  </button>
                )}
              </div>
            ) : (
              <>
                <div className="mt-5">
                  <div className="flex items-end justify-between gap-3">
                    <div>
                      <p className="text-xs font-semibold text-slate-700">Send from Amazon SES</p>
                      <p className="mt-1 text-[11px] leading-4 text-slate-500">
                        Choose a verified SES sender. AWS bills the connected AWS account directly.
                      </p>
                    </div>
                    <span className="text-[11px] text-slate-400">
                      {activeSenders.length} connected
                    </span>
                  </div>
                  <div className="mt-2 grid max-h-48 gap-2 overflow-y-auto pr-1 sm:grid-cols-2">
                    {activeSenders.map((sender) => {
                      const selected = composeSenderId === sender.id
                      return (
                        <button
                          key={sender.id}
                          type="button"
                          className={`flex min-w-0 items-center gap-3 rounded-xl border px-3 py-3 text-left transition ${
                            selected
                              ? 'border-indigo-300 bg-indigo-50 ring-1 ring-indigo-100'
                              : 'border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50'
                          }`}
                          onClick={() => setComposeSenderId(sender.id)}
                          aria-pressed={selected}
                        >
                          <span
                            className={`flex size-9 shrink-0 items-center justify-center rounded-lg ${
                              selected
                                ? 'bg-orange-100 text-orange-600'
                                : 'bg-slate-100 text-slate-500'
                            }`}
                          >
                            <FiMail aria-hidden="true" />
                          </span>
                          <span className="min-w-0 flex-1">
                            <span className="block truncate text-xs font-bold text-slate-900">
                              {senderLabel(sender)}
                            </span>
                            <span className="mt-0.5 block truncate text-[11px] text-slate-500">
                              Amazon SES · {sender.region}
                              {sender.maxSendRate ? ` · up to ${sender.maxSendRate}/sec` : ''}
                            </span>
                            {!sender.productionAccessEnabled && (
                              <span className="mt-1 block text-[10px] font-semibold text-amber-700">
                                SES sandbox — production access required for normal recipient lists
                              </span>
                            )}
                          </span>
                          {selected && (
                            <FiCheckCircle
                              className="shrink-0 text-indigo-600"
                              aria-label="Selected sender"
                            />
                          )}
                        </button>
                      )
                    })}
                  </div>
                </div>

                <label className="mt-4 block text-xs font-semibold text-slate-700">
                  Subject
                  <input
                    className="form-input mt-2"
                    value={composeSubject}
                    maxLength={998}
                    onChange={(event) => setComposeSubject(event.target.value)}
                  />
                </label>

                <label className="mt-4 block text-xs font-semibold text-slate-700">
                  Message
                  <textarea
                    className="form-input mt-2 min-h-52"
                    value={composeBody}
                    maxLength={500000}
                    onChange={(event) => setComposeBody(event.target.value)}
                    required
                  />
                </label>

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
                    disabled={composeBusy || !composeSenderId || !composeBody.trim()}
                    onClick={() => void submitCompose('draft')}
                  >
                    Save draft
                  </button>
                  <button
                    type="button"
                    className="secondary-button gap-2"
                    disabled={
                      composeBusy ||
                      !composeSenderId ||
                      !composeBody.trim() ||
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
                      composeBusy ||
                      !composeSenderId ||
                      !composeBody.trim() ||
                      !can(PERMISSIONS.BROADCAST_SEND)
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
      {sesConnectOpen && companyId && (
        <AmazonSesConnectModal
          companyId={companyId}
          onClose={() => setSesConnectOpen(false)}
          onConnected={handleSesConnected}
        />
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
            <span className={`rounded-full px-2 py-1 text-[10px] font-bold ${statusBadge(message.status)}`}>
              {statusLabel(message.status)}
            </span>
          </div>
          <p className="mt-1 line-clamp-2 text-xs leading-5 text-slate-500">
            {message.bodyPreview || 'No message preview'}
          </p>
          <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-slate-500">
            <span>
              From {senderLabel(sender)} · {providerLabel(sender?.provider)}
            </span>
            <span>{formatDate(message.scheduledAt || message.createdAt)}</span>
            {message.stats.total > 0 && (
              <>
                <span>Sent {message.stats.accepted.toLocaleString()}</span>
                <span>Failed {message.stats.failed.toLocaleString()}</span>
                <span>Bounced {message.stats.bounced.toLocaleString()}</span>
              </>
            )}
          </div>
          {message.stats.total > 0 && (
            <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-slate-100">
              <div className="h-full rounded-full bg-indigo-500" style={{ width: `${progress}%` }} />
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
  canModify,
  onBack,
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
  canModify: boolean
  onBack: () => void
  onEdit: (id: string) => void
  onAction: (
    action: 'pause' | 'resume' | 'cancel' | 'send',
    id: string,
  ) => void
  onRecipientsPage: (page: number) => void
}) {
  if (loading && !message) {
    return <div className="px-6 py-16 text-center text-sm text-slate-500">Loading broadcast…</div>
  }

  if (!message) {
    return <div className="px-6 py-16 text-center text-sm text-slate-500">Broadcast not found.</div>
  }

  const progress = progressFor(message)
  return (
    <div className="channel-message-scroll min-h-[calc(100dvh-214px)] p-4 sm:p-5">
      <div className="mx-auto max-w-5xl">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <button type="button" className="secondary-button gap-2" onClick={onBack}>
            <FiArrowLeft aria-hidden="true" />
            Back to group
          </button>
          <div className="flex flex-wrap gap-2">
            {(message.status === 'DRAFT' || message.status === 'SCHEDULED') && canModify && (
              <button type="button" className="secondary-button gap-2" onClick={() => onEdit(message.id)}>
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
            {message.status === 'RUNNING' && canModify && (
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
            {message.status === 'PAUSED' && canModify && (
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
            {['SCHEDULED', 'QUEUED', 'RUNNING', 'PAUSED'].includes(message.status) && canModify && (
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
              <span className={`rounded-full px-2 py-1 text-[10px] font-bold ${statusBadge(message.status)}`}>
                {statusLabel(message.status)}
              </span>
              <h1 className="mt-3 text-xl font-bold text-slate-950">
                {message.subject || '(No subject)'}
              </h1>
              <p className="mt-1 text-xs text-slate-500">
                From {senderLabel(sender)} ·{' '}
                {providerLabel(sender?.provider)}
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
              <div className="h-full rounded-full bg-indigo-500" style={{ width: `${progress}%` }} />
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
              <StatCard label="Sent" value={message.stats.accepted} />
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
                    Provider acceptance is shown as Sent. Bounces can update later from delivery reports.
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
                        <td colSpan={5} className="px-4 py-10 text-center text-slate-500">
                          Loading recipients…
                        </td>
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
                            <span className="font-semibold text-slate-700">
                              {recipientStatusLabel(recipient.status)}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-slate-500" data-label="Attempts">
                            {recipient.attemptCount}
                          </td>
                          <td className="px-4 py-3 text-slate-500" data-label="Accepted">
                            {formatDate(recipient.acceptedAt)}
                          </td>
                          <td className="px-4 py-3 text-slate-500" data-label="Result">
                            {recipient.failureReason || recipient.failureCode || '—'}
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
