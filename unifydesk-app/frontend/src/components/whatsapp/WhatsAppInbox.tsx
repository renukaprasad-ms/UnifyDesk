import axios from 'axios'
import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent } from 'react'
import {
  FiAlertTriangle,
  FiArrowLeft,
  FiCheck,
  FiCheckCircle,
  FiClock,
  FiCornerUpLeft,
  FiFileText,
  FiImage,
  FiMessageCircle,
  FiRefreshCw,
  FiSend,
  FiX,
} from 'react-icons/fi'
import { Link, useSearchParams } from 'react-router'
import { channelApi } from '../../features/channel/channelApi'
import type { ChannelConnection } from '../../features/channel/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { inboxApi } from '../../features/inbox/inboxApi'
import type {
  InboxConversation,
  InboxMessage,
  InboxParticipant,
  InboxWhatsAppStatusChangedEvent,
} from '../../features/inbox/types'
import { mediaApi } from '../../features/media/mediaApi'
import { realtimeClient } from '../../features/realtime/realtimeClient'
import { whatsappApi } from '../../features/whatsapp/whatsappApi'
import type {
  WhatsAppComposerState,
  WhatsAppSendResult,
  WhatsAppSendStatus,
  WhatsAppTemplate,
  WhatsAppTemplateParameters,
} from '../../features/whatsapp/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import { useSnackbar } from '../feedback/SnackbarProvider'
import { useAppSelector } from '../../store/hooks'
import {
  selectSelectedCompanyId,
  selectWorkspaceEpoch,
} from '../../store/workspace/workspaceSlice'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { templateVariableCounts } from './whatsappTemplateUtils'
import { Skeleton } from '../ui/Skeleton'
import StatusBadge from '../ui/StatusBadge'

type WhatsAppInboxMessage = InboxMessage

const mediaTypes = new Set(['image/jpeg', 'image/png', 'image/webp', 'application/pdf'])
const counterpart = (conversation: InboxConversation) =>
  conversation.participants.find((participant) => participant.participantType === 'PHONE_NUMBER')
    ?.displayName ||
  conversation.participants.find((participant) => participant.participantType === 'PHONE_NUMBER')
    ?.address ||
  conversation.participants[0]?.displayName ||
  conversation.participants[0]?.address ||
  'WhatsApp customer'

const createContactHref = (participant: InboxParticipant) => {
  const params = new URLSearchParams({ new: '1' })
  if (participant.participantType === 'EMAIL_ADDRESS') params.set('email', participant.address)
  else params.set('phone', participant.address)
  return `/contacts?${params.toString()}`
}

const blankParameters = (template: WhatsAppTemplate): WhatsAppTemplateParameters => {
  const counts = templateVariableCounts(template)
  return {
    header: Array.from({ length: counts.header }, () => ''),
    body: Array.from({ length: counts.body }, () => ''),
    buttons: Array.from({ length: counts.buttons }, () => ''),
  }
}

const statusLabel: Record<WhatsAppSendStatus, string> = {
  PENDING: 'Sending',
  ACCEPTED: 'Accepted',
  SENT: 'Sent',
  DELIVERED: 'Delivered',
  READ: 'Read',
  FAILED: 'Failed',
  UNKNOWN: 'Delivery uncertain',
}

const StatusIcon = ({ status }: { status: WhatsAppSendStatus }) => {
  if (status === 'READ' || status === 'DELIVERED') {
    return <FiCheckCircle aria-hidden="true" />
  }
  if (status === 'FAILED' || status === 'UNKNOWN') {
    return <FiAlertTriangle aria-hidden="true" />
  }
  if (status === 'PENDING') return <FiClock aria-hidden="true" />
  return <FiCheck aria-hidden="true" />
}

const messageStatus = (
  message: WhatsAppInboxMessage,
  localResults: Record<string, WhatsAppSendResult>,
): WhatsAppSendStatus | null => {
  if (message.direction !== 'OUTBOUND') return null
  return (
    message.whatsappStatus ??
    localResults[message.id]?.status ??
    (message.status === 'FAILED' ? 'FAILED' : message.status === 'SENT' ? 'SENT' : null)
  )
}

export default function WhatsAppInbox() {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const workspaceEpoch = useAppSelector(selectWorkspaceEpoch)
  const { capture: captureScope, isCurrent: isCurrentScope } = useWorkspaceEpochScope(
    companyId,
    workspaceEpoch,
  )
  const { can } = useIam()
  const snackbar = useSnackbar()
  const [searchParams, setSearchParams] = useSearchParams()
  const selectedConnectionId = searchParams.get('connectionId')
  const selectedConversationId = searchParams.get('conversationId')
  const [connections, setConnections] = useState<ChannelConnection[]>([])
  const [conversations, setConversations] = useState<InboxConversation[]>([])
  const [messages, setMessages] = useState<WhatsAppInboxMessage[]>([])
  const [loading, setLoading] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [composer, setComposer] = useState<WhatsAppComposerState | null>(null)
  const [composerLoading, setComposerLoading] = useState(false)
  const [text, setText] = useState('')
  const [replyTo, setReplyTo] = useState<WhatsAppInboxMessage | null>(null)
  const [templates, setTemplates] = useState<WhatsAppTemplate[]>([])
  const [templateMode, setTemplateMode] = useState(false)
  const [selectedTemplate, setSelectedTemplate] = useState<WhatsAppTemplate | null>(null)
  const [parameters, setParameters] = useState<WhatsAppTemplateParameters>({
    header: [],
    body: [],
    buttons: [],
  })
  const [sending, setSending] = useState(false)
  const [syncingTemplates, setSyncingTemplates] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [sendResults, setSendResults] = useState<Record<string, WhatsAppSendResult>>({})
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const realtimeConnectedRef = useRef(realtimeClient.isConnected())

  const selectedConnection = useMemo(
    () => connections.find((connection) => connection.id === selectedConnectionId) ?? null,
    [connections, selectedConnectionId],
  )
  const selectedConversation = useMemo(
    () => conversations.find((conversation) => conversation.id === selectedConversationId) ?? null,
    [conversations, selectedConversationId],
  )
  const contactParticipant = useMemo(
    () =>
      selectedConversation?.participants.find((participant) => participant.contactId) ??
      selectedConversation?.participants.find(
        (participant) => participant.contactResolution !== 'AMBIGUOUS',
      ) ??
      null,
    [selectedConversation],
  )

  const loadConnections = useCallback(async () => {
    if (!companyId || !can(PERMISSIONS.WHATSAPP_READ)) {
      setConnections([])
      return
    }
    const token = captureScope()
    try {
      const items = (await channelApi.list(companyId, 'WHATSAPP')).filter(
        (item) => item.status === 'ACTIVE' && item.capabilities.includes('INBOX'),
      )
      if (!isCurrentScope(token)) return
      setConnections(items)
      const connectionIsCurrent =
        Boolean(selectedConnectionId) && items.some((item) => item.id === selectedConnectionId)
      if (!connectionIsCurrent) {
        const params = new URLSearchParams(searchParams)
        if (items[0]) params.set('connectionId', items[0].id)
        else params.delete('connectionId')
        params.delete('conversationId')
        setSearchParams(params, { replace: true })
      }
    } catch (requestError) {
      if (isCurrentScope(token)) {
        snackbar.error(apiErrorMessage(requestError, 'Unable to load WhatsApp connections'))
      }
    }
  }, [
    can,
    captureScope,
    companyId,
    isCurrentScope,
    searchParams,
    selectedConnectionId,
    setSearchParams,
    workspaceEpoch,
  ])

  const loadConversations = useCallback(async () => {
    if (
      !companyId ||
      !selectedConnectionId ||
      !selectedConnection ||
      !can(PERMISSIONS.WHATSAPP_READ)
    ) {
      setConversations([])
      return
    }
    const token = captureScope()
    setLoading(true)
    try {
      const page = await inboxApi.conversations(companyId, 0, 50, selectedConnectionId, 'INBOX')
      if (isCurrentScope(token)) setConversations(page.items)
    } catch (requestError) {
      if (isCurrentScope(token)) {
        snackbar.error(apiErrorMessage(requestError, 'Unable to load WhatsApp chats'))
      }
    } finally {
      if (isCurrentScope(token)) setLoading(false)
    }
  }, [
    can,
    captureScope,
    companyId,
    isCurrentScope,
    selectedConnection,
    selectedConnectionId,
    workspaceEpoch,
  ])

  const loadDetail = useCallback(async () => {
    if (
      !companyId ||
      !selectedConversationId ||
      !selectedConversation ||
      !can(PERMISSIONS.WHATSAPP_READ)
    ) {
      setMessages([])
      setComposer(null)
      return
    }

    const token = captureScope()
    setDetailLoading(true)
    setComposerLoading(true)
    try {
      const [detail, state] = await Promise.all([
        inboxApi.conversationDetail(companyId, selectedConversationId),
        can(PERMISSIONS.WHATSAPP_SEND)
          ? whatsappApi.composer(companyId, selectedConversationId)
          : Promise.resolve(null),
      ])
      if (!isCurrentScope(token)) return
      const nextMessages = detail.messages as WhatsAppInboxMessage[]
      setMessages(nextMessages)
      const durableMessageIds = new Set(
        nextMessages
          .filter((message) => message.whatsappStatus != null)
          .map((message) => message.id),
      )
      if (durableMessageIds.size > 0) {
        setSendResults((current) => {
          const next = { ...current }
          let changed = false
          durableMessageIds.forEach((messageId) => {
            if (messageId in next) {
              delete next[messageId]
              changed = true
            }
          })
          return changed ? next : current
        })
      }
      setComposer(state)
      setTemplateMode(Boolean(state?.templateRequired))
    } catch (requestError) {
      if (isCurrentScope(token)) {
        snackbar.error(apiErrorMessage(requestError, 'Unable to load WhatsApp conversation'))
      }
    } finally {
      if (isCurrentScope(token)) {
        setDetailLoading(false)
        setComposerLoading(false)
      }
    }
  }, [
    can,
    captureScope,
    companyId,
    isCurrentScope,
    selectedConversation,
    selectedConversationId,
    workspaceEpoch,
  ])

  const loadTemplates = useCallback(async () => {
    if (
      !companyId ||
      !selectedConnectionId ||
      !selectedConnection ||
      !can(PERMISSIONS.WHATSAPP_TEMPLATE_READ)
    ) {
      setTemplates([])
      return
    }
    const token = captureScope()
    try {
      const items = await whatsappApi.templates(companyId, selectedConnectionId)
      if (isCurrentScope(token)) {
        setTemplates(items.filter((item) => item.status === 'APPROVED'))
      }
    } catch (requestError) {
      if (isCurrentScope(token)) {
        snackbar.error(apiErrorMessage(requestError, 'Unable to load WhatsApp templates'))
      }
    }
  }, [
    can,
    captureScope,
    companyId,
    isCurrentScope,
    selectedConnection,
    selectedConnectionId,
    workspaceEpoch,
  ])

  useEffect(() => {
    void loadConnections()
  }, [loadConnections])

  useEffect(() => {
    setConversations([])
    setMessages([])
    setComposer(null)
    setReplyTo(null)
    setTemplates([])
    setTemplateMode(false)
    setSelectedTemplate(null)
    setParameters({ header: [], body: [], buttons: [] })
    setText('')
    setSendResults({})
    void loadConversations()
  }, [loadConversations])

  useEffect(() => {
    void loadDetail()
  }, [loadDetail])

  useEffect(() => {
    if (templateMode || composer?.templateRequired) void loadTemplates()
  }, [composer?.templateRequired, loadTemplates, templateMode])

  useEffect(() => {
    if (
      !companyId ||
      !selectedConnection ||
      !selectedConversation ||
      !selectedConnectionId ||
      !selectedConversationId
    ) return
    const token = captureScope()

    return realtimeClient.subscribe<InboxWhatsAppStatusChangedEvent>(
      'inbox.whatsapp.status.changed',
      (event) => {
        const change = event.payload
        if (
          !isCurrentScope(token) ||
          !change ||
          change.connectionId !== selectedConnectionId ||
          change.conversationId !== selectedConversationId
        ) {
          return
        }

        void inboxApi
          .realtimeSnapshot(companyId, {
            connectionId: selectedConnectionId,
            folder: 'INBOX',
            detailConversationId: selectedConversationId,
            conversationIds: [selectedConversationId],
            messageIds: [change.messageId],
          })
          .then((snapshot) => {
            if (!isCurrentScope(token)) return
            const refreshedMessage = snapshot.messages.find(
              (message) => message.id === change.messageId,
            )
            if (!refreshedMessage) {
              void loadDetail()
              return
            }

            setMessages((current) =>
              current.map((message) =>
                message.id === refreshedMessage.id
                  ? (refreshedMessage as WhatsAppInboxMessage)
                  : message,
              ),
            )

            if (refreshedMessage.whatsappStatus != null) {
              setSendResults((current) => {
                if (!(refreshedMessage.id in current)) return current
                const next = { ...current }
                delete next[refreshedMessage.id]
                return next
              })
            }
          })
          .catch(() => {
            if (isCurrentScope(token)) void loadDetail()
          })
      },
    )
  }, [
    captureScope,
    companyId,
    isCurrentScope,
    loadDetail,
    selectedConnection,
    selectedConnectionId,
    selectedConversation,
    selectedConversationId,
    workspaceEpoch,
  ])

  useEffect(() => {
    if (!selectedConversationId || !selectedConversation) return
    const token = captureScope()

    const onFocus = () => {
      if (isCurrentScope(token)) void loadDetail()
    }

    window.addEventListener('focus', onFocus)
    const unsubscribeConnection = realtimeClient.subscribeConnection((connected) => {
      const wasConnected = realtimeConnectedRef.current
      realtimeConnectedRef.current = connected
      if (connected && !wasConnected && isCurrentScope(token)) {
        void loadDetail()
      }
    })

    return () => {
      window.removeEventListener('focus', onFocus)
      unsubscribeConnection()
    }
  }, [
    captureScope,
    isCurrentScope,
    loadDetail,
    selectedConversation,
    selectedConversationId,
    workspaceEpoch,
  ])

  const selectConversation = (conversation: InboxConversation) => {
    const params = new URLSearchParams(searchParams)
    params.set('conversationId', conversation.id)
    setSearchParams(params)
  }

  const refresh = async () => {
    await loadConversations()
    await loadDetail()
  }

  const rememberResult = (result: WhatsAppSendResult) => {
    setSendResults((current) => ({ ...current, [result.messageId]: result }))
    if (result.status === 'UNKNOWN') {
      snackbar.warning(
        'Delivery is uncertain. Do not resend immediately; refresh the chat and wait for provider status.',
      )
    } else if (result.status === 'FAILED') {
      snackbar.error('WhatsApp could not send this message.')
    } else {
      snackbar.success('WhatsApp message accepted.')
    }
  }

  const sendText = async () => {
    if (
      !companyId ||
      !selectedConversationId ||
      !selectedConversation ||
      !text.trim() ||
      sending
    ) return
    if (!composer?.freeFormAllowed) {
      setTemplateMode(true)
      snackbar.info('An approved template is required outside the customer-service window.')
      return
    }

    const token = captureScope()
    setSending(true)
    try {
      const result = await whatsappApi.send(companyId, selectedConversationId, {
        clientRequestId: crypto.randomUUID(),
        type: 'TEXT',
        text: text.trim(),
        replyToMessageId: replyTo?.id,
      })
      if (!isCurrentScope(token)) return
      rememberResult(result)
      setText('')
      setReplyTo(null)
      await refresh()
    } catch (requestError) {
      if (!isCurrentScope(token)) return
      const message = apiErrorMessage(requestError, 'Unable to send WhatsApp message')
      const templateRequired =
        axios.isAxiosError<ApiResponse<never>>(requestError) &&
        requestError.response?.data?.error_message === 'WHATSAPP_TEMPLATE_REQUIRED'

      if (templateRequired || message.toUpperCase().includes('TEMPLATE')) {
        setTemplateMode(true)
      }
      snackbar.error(message)
    } finally {
      if (isCurrentScope(token)) setSending(false)
    }
  }

  const sendTemplate = async () => {
    if (
      !companyId ||
      !selectedConversationId ||
      !selectedConversation ||
      !selectedTemplate ||
      sending
    ) return
    if (
      [...parameters.header, ...parameters.body, ...parameters.buttons].some(
        (value) => !value.trim(),
      )
    ) {
      snackbar.warning('Complete every required template variable.')
      return
    }

    const token = captureScope()
    setSending(true)
    try {
      const result = await whatsappApi.send(companyId, selectedConversationId, {
        clientRequestId: crypto.randomUUID(),
        type: 'TEMPLATE',
        templateId: selectedTemplate.id,
        parameters,
      })
      if (!isCurrentScope(token)) return
      rememberResult(result)
      setSelectedTemplate(null)
      setParameters({ header: [], body: [], buttons: [] })
      if (composer?.freeFormAllowed) setTemplateMode(false)
      await refresh()
    } catch (requestError) {
      if (isCurrentScope(token)) {
        snackbar.error(apiErrorMessage(requestError, 'Unable to send WhatsApp template'))
      }
    } finally {
      if (isCurrentScope(token)) setSending(false)
    }
  }

  const uploadAndSend = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (
      !file ||
      !companyId ||
      !selectedConversationId ||
      !selectedConversation ||
      uploading ||
      sending
    ) return

    if (!mediaTypes.has(file.type)) {
      snackbar.warning('WhatsApp attachments support JPEG, PNG, WebP images and PDF documents.')
      return
    }
    const token = captureScope()
    setUploading(true)
    try {
      const upload = await mediaApi.initiateUpload(file, 'WHATSAPP_MEDIA', companyId)
      if (!isCurrentScope(token)) return
      await mediaApi.uploadFileToStorage(upload, file)
      if (!isCurrentScope(token)) return
      await mediaApi.completeUpload(upload.mediaId)
      if (!isCurrentScope(token)) return

      const result = await whatsappApi.send(companyId, selectedConversationId, {
        clientRequestId: crypto.randomUUID(),
        type: 'MEDIA',
        mediaId: upload.mediaId,
        caption: text.trim() || undefined,
        replyToMessageId: replyTo?.id,
      })
      if (!isCurrentScope(token)) return
      rememberResult(result)
      setText('')
      setReplyTo(null)
      await refresh()
    } catch (requestError) {
      if (isCurrentScope(token)) {
        snackbar.error(apiErrorMessage(requestError, 'Unable to send WhatsApp attachment'))
      }
    } finally {
      if (isCurrentScope(token)) setUploading(false)
    }
  }

  const syncTemplates = async () => {
    if (
      !companyId ||
      !selectedConnectionId ||
      !selectedConnection ||
      syncingTemplates ||
      !can(PERMISSIONS.WHATSAPP_TEMPLATE_MODIFY)
    ) {
      return
    }
    const token = captureScope()
    setSyncingTemplates(true)
    try {
      const result = await whatsappApi.syncTemplates(companyId, selectedConnectionId)
      if (!isCurrentScope(token)) return
      snackbar.success(`Synced ${result.synced} templates; ${result.approved} approved.`)
      await loadTemplates()
    } catch (requestError) {
      if (isCurrentScope(token)) {
        snackbar.error(apiErrorMessage(requestError, 'Unable to sync WhatsApp templates'))
      }
    } finally {
      if (isCurrentScope(token)) setSyncingTemplates(false)
    }
  }

  if (!companyId) {
    return (
      <section className="px-6 py-8">
        <div className="panel p-8 text-center text-sm text-slate-500">
          Select a workspace to open WhatsApp Inbox.
        </div>
      </section>
    )
  }

  if (!can(PERMISSIONS.WHATSAPP_READ)) {
    return (
      <section className="px-6 py-8">
        <div className="panel p-8 text-center text-sm text-slate-500">
          You do not have permission to read WhatsApp conversations.
        </div>
      </section>
    )
  }

  return (
    <section className="channel-workspace-page">
      <div className="channel-workspace-toolbar gap-3">
        <div className="min-w-0">
          <p className="eyebrow">WHATSAPP INBOX</p>
          <p className="mt-1 truncate text-xs font-semibold text-slate-700">
            {selectedConnection?.externalAccountId || selectedConnection?.name || 'Select a sender'}
          </p>
        </div>
        <button
          type="button"
          className="secondary-button ml-auto gap-2"
          disabled={!selectedConnection || loading || detailLoading}
          onClick={() => void refresh()}
          aria-label="Refresh WhatsApp chat"
        >
          <FiRefreshCw aria-hidden="true" />
          Refresh
        </button>
      </div>

      <div className="channel-workspace-body grid min-h-[calc(100dvh-155px)] gap-4 lg:grid-cols-[300px_minmax(0,1fr)]">
        <aside
          className={`panel min-h-0 overflow-hidden ${
            selectedConversationId ? 'hidden lg:block' : 'block'
          }`}
          aria-label="WhatsApp chats"
        >
          <div className="border-b border-slate-100 px-4 py-3">
            <p className="text-xs font-bold text-slate-900">Chats</p>
            <p className="mt-1 text-[11px] text-slate-500">
              {loading ? 'Loading…' : `${conversations.length} conversations`}
            </p>
          </div>
          <div className="max-h-[calc(100dvh-240px)] overflow-y-auto">
            {conversations.length === 0 && !loading ? (
              <div className="px-4 py-12 text-center">
                <FiMessageCircle className="mx-auto text-2xl text-slate-300" aria-hidden="true" />
                <p className="mt-3 text-xs font-semibold text-slate-700">No WhatsApp chats yet</p>
                <p className="mt-1 text-[11px] leading-5 text-slate-500">
                  Customer messages will appear here when webhooks create Inbox conversations.
                </p>
              </div>
            ) : (
              conversations.map((conversation) => (
                <button
                  key={conversation.id}
                  type="button"
                  className={`w-full border-b border-slate-100 px-4 py-3 text-left hover:bg-slate-50 ${
                    selectedConversationId === conversation.id ? 'bg-indigo-50' : ''
                  }`}
                  onClick={() => selectConversation(conversation)}
                >
                  <div className="flex items-start justify-between gap-2">
                    <p className="min-w-0 flex-1 truncate text-sm font-semibold text-slate-900">
                      {counterpart(conversation)}
                    </p>
                    {conversation.unreadCount > 0 && (
                      <span className="rounded-full bg-indigo-600 px-2 py-0.5 text-[9px] font-bold text-white">
                        {conversation.unreadCount > 99 ? '99+' : conversation.unreadCount}
                      </span>
                    )}
                  </div>
                  <p className="mt-1 truncate text-[11px] text-slate-500">
                    {conversation.lastMessagePreview || 'No preview available'}
                  </p>
                </button>
              ))
            )}
          </div>
        </aside>

        <main
          className={`panel flex min-h-0 flex-col overflow-hidden ${
            selectedConversationId ? 'flex' : 'hidden lg:flex'
          }`}
        >
          {!selectedConversationId ? (
            <div className="flex flex-1 items-center justify-center px-6 text-center">
              <div>
                <FiMessageCircle className="mx-auto text-4xl text-slate-300" aria-hidden="true" />
                <p className="mt-4 text-sm font-semibold text-slate-800">Select a WhatsApp chat</p>
                <p className="mt-2 text-xs text-slate-500">
                  Messages, replies, templates and supported media will appear here.
                </p>
              </div>
            </div>
          ) : (
            <>
              <header className="flex items-center gap-3 border-b border-slate-200 bg-white px-4 py-3">
                <button
                  type="button"
                  className="secondary-button px-3 lg:hidden"
                  aria-label="Back to WhatsApp chats"
                  onClick={() => {
                    const params = new URLSearchParams(searchParams)
                    params.delete('conversationId')
                    setSearchParams(params)
                  }}
                >
                  <FiArrowLeft aria-hidden="true" />
                </button>
                <div className="min-w-0">
                  <p className="truncate text-sm font-bold text-slate-950">
                    {selectedConversation ? counterpart(selectedConversation) : 'WhatsApp chat'}
                  </p>
                  <p className="mt-1 text-[11px] text-slate-500">
                    {composerLoading
                      ? 'Checking messaging availability…'
                      : composer?.templateRequired
                        ? 'Approved template required'
                        : composer?.customerServiceExpiresAt
                          ? `Free-form available until ${new Date(
                              composer.customerServiceExpiresAt,
                            ).toLocaleString()}`
                          : 'WhatsApp conversation'}
                  </p>
                </div>
                {contactParticipant?.contactId && can(PERMISSIONS.CONTACT_READ) && (
                  <Link
                    className="secondary-button shrink-0 px-3"
                    to={`/contacts/${contactParticipant.contactId}`}
                  >
                    View contact
                  </Link>
                )}
                {contactParticipant &&
                  !contactParticipant.contactId &&
                  contactParticipant.contactResolution !== 'AMBIGUOUS' &&
                  can(PERMISSIONS.CONTACT_CREATE) && (
                    <Link
                      className="secondary-button shrink-0 px-3"
                      to={createContactHref(contactParticipant)}
                    >
                      Add to contacts
                    </Link>
                  )}
                {contactParticipant?.contactResolution === 'AMBIGUOUS' &&
                  can(PERMISSIONS.CONTACT_READ) && (
                    <Link
                      className="secondary-button shrink-0 px-3"
                      to={`/contacts?q=${encodeURIComponent(contactParticipant.address)}`}
                    >
                      Review contacts
                    </Link>
                  )}
              </header>

              <div className="min-h-0 flex-1 space-y-3 overflow-y-auto bg-slate-50/60 p-3 sm:p-5">
                {detailLoading ? (
                  <div className="space-y-3" role="status" aria-label="Loading WhatsApp messages">
                    {Array.from({ length: 4 }).map((_, index) => (
                      <div key={index} className={`max-w-[80%] rounded-2xl border border-slate-200 bg-white p-3 ${index % 2 ? 'ml-auto' : ''}`}>
                        <Skeleton className="h-3 w-24" />
                        <Skeleton className="mt-2 h-3 w-full" />
                        <Skeleton className="mt-2 h-3 w-2/3" />
                      </div>
                    ))}
                  </div>
                ) : messages.length === 0 ? (
                  <p className="py-10 text-center text-sm text-slate-500">No messages yet.</p>
                ) : (
                  messages.map((message) => {
                    const status = messageStatus(message, sendResults)
                    return (
                      <article
                        key={message.id}
                        className={`max-w-[88%] rounded-2xl border px-3 py-2.5 sm:max-w-[72%] ${
                          message.direction === 'OUTBOUND'
                            ? 'ml-auto border-emerald-100 bg-emerald-50'
                            : 'mr-auto border-slate-200 bg-white'
                        }`}
                      >
                        <div className="whitespace-pre-wrap break-words text-sm leading-6 text-slate-800">
                          {message.bodyText ||
                            message.snippet ||
                            (message.attachments.length > 0
                              ? 'Attachment'
                              : 'Unsupported WhatsApp message')}
                        </div>

                        {message.attachments.length > 0 && (
                          <div className="mt-2 space-y-1">
                            {message.attachments.map((attachment) => (
                              <div
                                key={attachment.id}
                                className="flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-2.5 py-2 text-xs text-slate-600"
                              >
                                {attachment.mimeType?.startsWith('image/') ? (
                                  <FiImage aria-hidden="true" />
                                ) : (
                                  <FiFileText aria-hidden="true" />
                                )}
                                <span className="truncate">
                                  {attachment.filename || attachment.mimeType || 'WhatsApp attachment'}
                                </span>
                              </div>
                            ))}
                          </div>
                        )}

                        <div className="mt-2 flex items-center justify-end gap-2 text-[10px] text-slate-500">
                          {message.sentAt && (
                            <time>{new Date(message.sentAt).toLocaleTimeString()}</time>
                          )}
                          {status && (
                            <StatusBadge status={status} className="gap-1" >
                              <StatusIcon status={status} />
                              {statusLabel[status]}
                            </StatusBadge>
                          )}
                        </div>

                        {status === 'UNKNOWN' && (
                          <div className="mt-2 rounded-lg bg-amber-50 px-2.5 py-2 text-[11px] leading-5 text-amber-800">
                            Delivery is uncertain. Refresh for provider status before deciding whether
                            to send another message.
                          </div>
                        )}

                        {can(PERMISSIONS.WHATSAPP_SEND) && (
                          <button
                            type="button"
                            className="mt-2 inline-flex items-center gap-1 text-[11px] font-semibold text-indigo-600 hover:text-indigo-700"
                            onClick={() => {
                              setReplyTo(message)
                              setTemplateMode(false)
                            }}
                          >
                            <FiCornerUpLeft aria-hidden="true" />
                            Reply
                          </button>
                        )}
                      </article>
                    )
                  })
                )}
              </div>

              {can(PERMISSIONS.WHATSAPP_SEND) && (
                <div className="border-t border-slate-200 bg-white p-3 sm:p-4">
                  {replyTo && (
                    <div className="mb-3 flex items-start gap-2 rounded-xl border border-indigo-100 bg-indigo-50 px-3 py-2">
                      <FiCornerUpLeft className="mt-0.5 shrink-0 text-indigo-600" aria-hidden="true" />
                      <div className="min-w-0 flex-1">
                        <p className="text-[10px] font-bold uppercase tracking-wide text-indigo-600">Replying</p>
                        <p className="mt-1 truncate text-xs text-slate-600">
                          {replyTo.bodyText || replyTo.snippet || 'WhatsApp message'}
                        </p>
                      </div>
                      <button type="button" aria-label="Cancel reply" onClick={() => setReplyTo(null)}>
                        <FiX aria-hidden="true" />
                      </button>
                    </div>
                  )}

                  {(composer?.templateRequired || templateMode) ? (
                    <div className="space-y-3">
                      <div className="flex flex-wrap items-center gap-2">
                        <div className="min-w-0 flex-1">
                          <p className="text-xs font-bold text-slate-900">Approved template</p>
                          <p className="mt-1 text-[11px] text-slate-500">
                            Free-form messaging is not currently available for this customer.
                          </p>
                        </div>
                        {can(PERMISSIONS.WHATSAPP_TEMPLATE_MODIFY) && (
                          <button
                            type="button"
                            className="secondary-button gap-2 px-3 py-2 text-xs"
                            disabled={syncingTemplates}
                            onClick={() => void syncTemplates()}
                          >
                            <FiRefreshCw aria-hidden="true" />
                            {syncingTemplates ? 'Syncing…' : 'Sync templates'}
                          </button>
                        )}
                        {composer?.freeFormAllowed && (
                          <button
                            type="button"
                            className="secondary-button px-3 py-2 text-xs"
                            onClick={() => setTemplateMode(false)}
                          >
                            Text
                          </button>
                        )}
                      </div>

                      <label className="block text-xs font-semibold text-slate-600">
                        Template
                        <select
                          className="form-input mt-1.5"
                          value={selectedTemplate?.id ?? ''}
                          onChange={(event) => {
                            const template =
                              templates.find((item) => item.id === event.target.value) ?? null
                            setSelectedTemplate(template)
                            setParameters(
                              template
                                ? blankParameters(template)
                                : { header: [], body: [], buttons: [] },
                            )
                          }}
                        >
                          <option value="">Choose an approved template</option>
                          {templates.map((template) => (
                            <option key={template.id} value={template.id}>
                              {template.name} · {template.language} · {template.category}
                            </option>
                          ))}
                        </select>
                      </label>

                      {selectedTemplate && (
                        <div className="grid gap-3 sm:grid-cols-2">
                          {(['header', 'body', 'buttons'] as const).flatMap((section) =>
                            parameters[section].map((value, index) => (
                              <label key={`${section}:${index}`} className="text-xs font-semibold text-slate-600">
                                {section === 'body' ? 'Body' : section === 'header' ? 'Header' : 'Button'} variable {index + 1}
                                <input
                                  className="form-input mt-1.5"
                                  value={value}
                                  maxLength={1024}
                                  onChange={(event) =>
                                    setParameters((current) => ({
                                      ...current,
                                      [section]: current[section].map((item, itemIndex) =>
                                        itemIndex === index ? event.target.value : item,
                                      ),
                                    }))
                                  }
                                />
                              </label>
                            )),
                          )}
                        </div>
                      )}

                      <button
                        type="button"
                        className="primary-button w-full justify-center gap-2 sm:w-auto"
                        disabled={!selectedTemplate || sending}
                        onClick={() => void sendTemplate()}
                      >
                        <FiSend aria-hidden="true" />
                        {sending ? 'Sending…' : 'Send template'}
                      </button>
                    </div>
                  ) : (
                    <div>
                      <label className="sr-only" htmlFor="whatsapp-message">
                        WhatsApp message
                      </label>
                      <textarea
                        id="whatsapp-message"
                        className="form-input min-h-24 resize-y"
                        placeholder="Write a WhatsApp message…"
                        value={text}
                        maxLength={4096}
                        disabled={sending || uploading || !composer?.freeFormAllowed}
                        onChange={(event) => setText(event.target.value)}
                      />
                      <div className="mt-2 flex flex-wrap items-center gap-2">
                        <input
                          ref={fileInputRef}
                          type="file"
                          className="sr-only"
                          accept="image/jpeg,image/png,image/webp,application/pdf"
                          onChange={(event) => void uploadAndSend(event)}
                        />
                        {can(PERMISSIONS.MEDIA_UPLOAD) &&
                          composer?.supportedMedia?.some((item) => item === 'IMAGE' || item === 'DOCUMENT') && (
                            <button
                              type="button"
                              className="secondary-button gap-2 px-3 py-2 text-xs"
                              disabled={uploading || sending}
                              onClick={() => fileInputRef.current?.click()}
                            >
                              <FiImage aria-hidden="true" />
                              {uploading ? 'Uploading…' : 'Image / PDF'}
                            </button>
                          )}
                        {can(PERMISSIONS.WHATSAPP_TEMPLATE_READ) && (
                          <button
                            type="button"
                            className="secondary-button px-3 py-2 text-xs"
                            disabled={sending || uploading}
                            onClick={() => setTemplateMode(true)}
                          >
                            Template
                          </button>
                        )}
                        <button
                          type="button"
                          className="primary-button ml-auto gap-2"
                          disabled={sending || uploading || !text.trim() || !composer?.freeFormAllowed}
                          onClick={() => void sendText()}
                        >
                          <FiSend aria-hidden="true" />
                          {sending ? 'Sending…' : 'Send'}
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              )}
            </>
          )}
        </main>
      </div>
    </section>
  )
}
