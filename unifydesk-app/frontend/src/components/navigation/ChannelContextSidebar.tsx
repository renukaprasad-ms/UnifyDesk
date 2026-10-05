import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import {
  FiCheck,
  FiChevronDown,
  FiGrid,
  FiInbox,
  FiLink,
  FiMail,
  FiMessageSquare,
  FiPlus,
  FiRadio,
  FiSend,
  FiServer,
  FiSmartphone,
  FiUsers,
} from 'react-icons/fi'
import { SiGmail } from 'react-icons/si'
import { useLocation, useNavigate } from 'react-router'
import EmailProviderPicker from '../email/EmailProviderPicker'
import ConversationList from '../inbox/ConversationList'
import { channelApi } from '../../features/channel/channelApi'
import { broadcastApi } from '../../features/broadcast/broadcastApi'
import { fetchBroadcastGroups } from '../../store/broadcast/broadcastSlice'
import type { BroadcastSender } from '../../features/broadcast/types'
import {
  broadcastSenderConnectionDetails,
  broadcastSenderProviderLabel,
  isMessagingManagedSender,
} from '../../features/managedEmail/privacy'
import type { ChannelConnection, ChannelType, SenderIdentity } from '../../features/channel/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { useMediaQuery } from '../../hooks/useMediaQuery'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { fetchEmailConnections, fetchInboxConversations } from '../../store/inbox/inboxSlice'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import {
  selectSelectedCompanyId,
  selectWorkspaceEpoch,
} from '../../store/workspace/workspaceSlice'
import {
  isWorkspaceEpochCurrent,
  workspaceEpochToken,
} from '../../features/workspace/workspaceEpoch'
import { ListSkeleton } from '../ui/Skeleton'

type ChannelContextSidebarProps = {
  mode: 'inbox' | 'broadcast'
  onNavigate: () => void
}

type IdentityItem = {
  connectionId: string
  providerType: string
  senderId?: string
  sender?: SenderIdentity
  label: string
  secondary: string
}

type ChannelMeta = {
  label: string
  icon: ReactNode
  connectLabel: string
}

const channelMeta: Record<ChannelType, ChannelMeta> = {
  EMAIL: {
    label: 'Email',
    icon: <FiMail />,
    connectLabel: 'Add email',
  },
  WHATSAPP: {
    label: 'WhatsApp',
    icon: <FiMessageSquare />,
    connectLabel: 'Add WhatsApp',
  },
  SMS: {
    label: 'SMS',
    icon: <FiSmartphone />,
    connectLabel: 'Add SMS',
  },
}

const channelFromPath = (pathname: string): ChannelType => {
  if (pathname.endsWith('/email')) return 'EMAIL'
  if (pathname.endsWith('/social-media')) return 'WHATSAPP'
  return 'SMS'
}

const identityFallback = (connection: ChannelConnection) =>
  connection.externalAccountId || connection.name

const emailProviderIcon = (providerType: string) => {
  if (providerType === 'GMAIL') return <SiGmail aria-hidden="true" />
  if (providerType === 'MICROSOFT') return <FiGrid aria-hidden="true" />
  if (providerType === 'IMAP_SMTP') return <FiServer aria-hidden="true" />
  return <FiMail aria-hidden="true" />
}

export default function ChannelContextSidebar({ mode, onNavigate }: ChannelContextSidebarProps) {
  const dispatch = useAppDispatch()
  const companyId = useAppSelector(selectSelectedCompanyId)
  const workspaceEpoch = useAppSelector(selectWorkspaceEpoch)
  const workspaceScopeRef = useRef({ companyId, epoch: workspaceEpoch })
  workspaceScopeRef.current = { companyId, epoch: workspaceEpoch }
  const inbox = useAppSelector((state) => state.inbox)
  const broadcast = useAppSelector((state) => state.broadcast)
  const { can } = useIam()
  const location = useLocation()
  const navigate = useNavigate()
  const desktop = useMediaQuery('(min-width: 1024px)')
  const menuRef = useRef<HTMLDivElement | null>(null)
  const channelType = channelFromPath(location.pathname)
  const meta = channelMeta[channelType]
  const [localConnections, setLocalConnections] = useState<ChannelConnection[]>([])
  const [senders, setSenders] = useState<Record<string, SenderIdentity[]>>({})
  const [broadcastSenders, setBroadcastSenders] = useState<BroadcastSender[]>([])
  const [broadcastSenderLoading, setBroadcastSenderLoading] = useState(false)
  const [localLoading, setLocalLoading] = useState(true)
  const [menuOpen, setMenuOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const search = useMemo(() => new URLSearchParams(location.search), [location.search])
  const selectedConnectionId = search.get('connectionId')
  const selectedSenderId = search.get('senderId')
  const selectedConversationId = search.get('conversationId')
  const selectedGroupId = search.get('groupId')
  const legacyBroadcastMode = search.get('legacy') === '1' || Boolean(selectedGroupId)
  const selectedFolder = search.get('folder') === 'SENT' ? 'SENT' : 'INBOX'

  const emailMode = mode === 'inbox' && channelType === 'EMAIL'
  const broadcastMode = mode === 'broadcast'
  const broadcastGroups =
    broadcast.groupsCompanyId === companyId && broadcast.groupsChannelType === channelType
      ? broadcast.groups
      : []
  const emailConnections = inbox.connectionsCompanyId === companyId ? inbox.connections : []
  const connections = (
    channelType === 'EMAIL'
      ? emailConnections.filter((connection) => connection.capabilities.includes('INBOX'))
      : localConnections
  ).filter((connection) => connection.status === 'ACTIVE')
  const loading =
    broadcastMode && channelType !== 'WHATSAPP'
      ? broadcastSenderLoading
      : channelType === 'EMAIL'
        ? inbox.connectionsCompanyId === companyId && inbox.connectionsStatus === 'loading'
        : localLoading
  const sameConversationScope =
    emailMode &&
    inbox.scopeCompanyId === companyId &&
    inbox.scopeConnectionId === selectedConnectionId &&
    inbox.scopeFolder === selectedFolder
  const conversations = sameConversationScope ? inbox.conversations : []
  const conversationLoading = sameConversationScope && inbox.conversationsStatus === 'loading'
  const conversationLoadingMore = sameConversationScope && inbox.loadingMore
  const conversationPage = sameConversationScope ? inbox.conversationPage : 0
  const conversationTotalPages = sameConversationScope ? inbox.conversationTotalPages : 0
  const conversationTotalElements = sameConversationScope ? inbox.conversationTotalElements : 0

  useEffect(() => {
    if (!menuOpen) return
    const onPointerDown = (event: PointerEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) setMenuOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [menuOpen])

  useEffect(() => {
    if (broadcastMode) return
    if (channelType !== 'EMAIL') return
    if (!companyId) return
    void dispatch(fetchEmailConnections({ companyId, epoch: workspaceEpoch }))
  }, [broadcastMode, channelType, companyId, dispatch, workspaceEpoch])

  useEffect(() => {
    if (broadcastMode) {
      setLocalConnections([])
      setSenders({})
      setLocalLoading(false)
      return
    }
    if (channelType === 'EMAIL') {
      setLocalConnections([])
      setSenders({})
      setLocalLoading(false)
      return
    }

    if (!companyId) {
      setLocalConnections([])
      setSenders({})
      setLocalLoading(false)
      return
    }

    let active = true
    const token = workspaceEpochToken(companyId, workspaceEpoch)
    const current = () =>
      active &&
      isWorkspaceEpochCurrent(
        token,
        workspaceScopeRef.current.companyId,
        workspaceScopeRef.current.epoch,
      )
    setLocalLoading(true)
    setError(null)

    channelApi
      .list(companyId, channelType)
      .then(async (items) => {
        if (!current()) return

        const connected = items.filter((item) => item.status === 'ACTIVE')
        setLocalConnections(connected)

        const senderEntries = await Promise.all(
          connected.map(async (connection) => {
            try {
              return [connection.id, await channelApi.senders(companyId, connection.id)] as const
            } catch {
              return [connection.id, []] as const
            }
          }),
        )

        if (current()) setSenders(Object.fromEntries(senderEntries))
      })
      .catch((requestError) => {
        if (current()) {
          setError(apiErrorMessage(requestError, `Unable to load connected ${meta.label}`))
        }
      })
      .finally(() => {
        if (current()) setLocalLoading(false)
      })

    return () => {
      active = false
    }
  }, [broadcastMode, channelType, companyId, meta.label, workspaceEpoch])

  useEffect(() => {
    if (!companyId || !broadcastMode || channelType === 'WHATSAPP') {
      setBroadcastSenders([])
      setBroadcastSenderLoading(false)
      return
    }

    let active = true
    const token = workspaceEpochToken(companyId, workspaceEpoch)
    const current = () =>
      active &&
      isWorkspaceEpochCurrent(
        token,
        workspaceScopeRef.current.companyId,
        workspaceScopeRef.current.epoch,
      )
    setBroadcastSenderLoading(true)
    setError(null)
    broadcastApi
      .senders(companyId)
      .then((items) => {
        if (current()) setBroadcastSenders(items)
      })
      .catch((requestError) => {
        if (current()) setError(apiErrorMessage(requestError, 'Unable to load Broadcast senders'))
      })
      .finally(() => {
        if (current()) setBroadcastSenderLoading(false)
      })

    return () => {
      active = false
    }
  }, [broadcastMode, channelType, companyId, workspaceEpoch])

  useEffect(() => {
    if (!companyId || !broadcastMode) return
    void dispatch(fetchBroadcastGroups({ companyId, epoch: workspaceEpoch, channelType }))
  }, [broadcastMode, channelType, companyId, dispatch])

  useEffect(() => {
    if (!companyId || !emailMode || !selectedConnectionId) {
      return
    }

    void dispatch(
      fetchInboxConversations({
        companyId,
        epoch: workspaceEpoch,
        connectionId: selectedConnectionId,
        folder: selectedFolder,
      }),
    )
  }, [companyId, dispatch, emailMode, selectedConnectionId, selectedFolder, workspaceEpoch])

  const identities = useMemo<IdentityItem[]>(() => {
    if (broadcastMode && channelType !== 'WHATSAPP') {
      return broadcastSenders
        .filter(
          (sender) =>
            sender.status === 'ACTIVE' &&
            sender.sendingEnabled &&
            (channelType === 'SMS'
              ? sender.provider === 'MSG91_SMS'
              : sender.provider !== 'MSG91_SMS'),
        )
        .map((sender) => ({
          connectionId: sender.channelConnectionId,
          providerType: sender.provider,
          senderId: sender.id,
          label: sender.fromAddress,
          secondary: isMessagingManagedSender(sender)
            ? broadcastSenderProviderLabel(sender)
            : sender.provider === 'MSG91_SMS'
              ? `MSG91 SMS · ${sender.connectionName}`
              : sender.provider === 'MSG91_EMAIL'
                ? `MSG91 Email · ${sender.connectionName}`
                : [
                    broadcastSenderProviderLabel(sender),
                    ...broadcastSenderConnectionDetails(sender),
                    sender.provider === 'AMAZON_SES' && !sender.productionAccessEnabled
                      ? 'Sandbox'
                      : null,
                  ]
                    .filter(Boolean)
                    .join(' · '),
        }))
    }

    const result: IdentityItem[] = []

    for (const connection of connections) {
      if (channelType === 'EMAIL') {
        result.push({
          connectionId: connection.id,
          providerType: connection.providerType,
          label: identityFallback(connection),
          secondary: connection.providerType.replaceAll('_', ' '),
        })
        continue
      }

      const activeSenders = (senders[connection.id] ?? []).filter(
        (sender) => sender.status === 'ACTIVE' || sender.status === 'PENDING',
      )

      if (activeSenders.length === 0) {
        result.push({
          connectionId: connection.id,
          providerType: connection.providerType,
          label: identityFallback(connection),
          secondary: connection.providerType.replaceAll('_', ' '),
        })
        continue
      }

      activeSenders.forEach((sender) =>
        result.push({
          connectionId: connection.id,
          providerType: connection.providerType,
          senderId: sender.id,
          sender,
          label: sender.value,
          secondary:
            sender.identityType === 'SMS_HEADER' ? `${connection.name} · Header` : connection.name,
        }),
      )
    }

    return result
  }, [broadcastMode, broadcastSenders, channelType, connections, senders])

  const selectedItem = useMemo(
    () =>
      identities.find(
        (item) =>
          item.connectionId === selectedConnectionId &&
          (item.senderId ? item.senderId === selectedSenderId : !selectedSenderId),
      ) ?? null,
    [identities, selectedConnectionId, selectedSenderId],
  )

  useEffect(() => {
    if (loading || identities.length === 0 || selectedItem) return

    const first = identities[0]
    const params = new URLSearchParams(location.search)
    params.set('connectionId', first.connectionId)
    if (emailMode && !params.get('folder')) params.set('folder', 'INBOX')
    if (first.senderId) params.set('senderId', first.senderId)
    else params.delete('senderId')
    params.delete('conversationId')

    navigate(
      {
        pathname: location.pathname,
        search: `?${params.toString()}`,
      },
      { replace: true },
    )
  }, [emailMode, identities, loading, location.pathname, location.search, navigate, selectedItem])

  useEffect(() => {
    if (!desktop || !emailMode || selectedConversationId || conversations.length === 0) {
      return
    }

    const params = new URLSearchParams(location.search)
    params.set('conversationId', conversations[0].id)
    navigate(
      {
        pathname: location.pathname,
        search: `?${params.toString()}`,
      },
      { replace: true },
    )
  }, [
    conversations,
    desktop,
    emailMode,
    location.pathname,
    location.search,
    navigate,
    selectedConversationId,
  ])

  const selectIdentity = (item: IdentityItem) => {
    const params = new URLSearchParams(location.search)
    params.set('connectionId', item.connectionId)
    if (emailMode && !params.get('folder')) params.set('folder', 'INBOX')
    if (item.senderId) params.set('senderId', item.senderId)
    else params.delete('senderId')
    params.delete('conversationId')
    params.delete('compose')

    setMenuOpen(false)
    navigate({
      pathname: location.pathname,
      search: `?${params.toString()}`,
    })
    onNavigate()
  }

  useEffect(() => {
    if (
      !desktop ||
      !broadcastMode ||
      !legacyBroadcastMode ||
      selectedGroupId ||
      broadcastGroups.length === 0
    ) {
      return
    }

    const params = new URLSearchParams(location.search)
    params.set('groupId', broadcastGroups[0].id)
    params.delete('broadcastId')
    params.delete('compose')
    params.delete('newGroup')
    navigate({ pathname: location.pathname, search: `?${params.toString()}` }, { replace: true })
  }, [
    broadcastGroups,
    broadcastMode,
    desktop,
    legacyBroadcastMode,
    location.pathname,
    location.search,
    navigate,
    selectedGroupId,
  ])

  const selectBroadcastGroup = (groupId: string) => {
    const params = new URLSearchParams(location.search)
    params.set('groupId', groupId)
    params.set('legacy', '1')
    params.delete('broadcastId')
    params.delete('compose')
    params.delete('newGroup')
    navigate({ pathname: location.pathname, search: `?${params.toString()}` })
    onNavigate()
  }

  const createBroadcastGroup = () => {
    if (channelType === 'WHATSAPP') return
    const params = new URLSearchParams(location.search)
    params.set('newGroup', '1')
    params.set('legacy', '1')
    params.delete('broadcastId')
    navigate({ pathname: location.pathname, search: `?${params.toString()}` })
    onNavigate()
  }

  const loadMoreBroadcastGroups = () => {
    if (
      !companyId ||
      broadcast.loadingMoreGroups ||
      broadcast.groupsPage + 1 >= broadcast.groupsTotalPages
    ) {
      return
    }
    void dispatch(
      fetchBroadcastGroups({
        companyId,
        epoch: workspaceEpoch,
        channelType,
        page: broadcast.groupsPage + 1,
        append: true,
      }),
    )
  }

  const selectFolder = (folder: 'INBOX' | 'SENT') => {
    const params = new URLSearchParams(location.search)
    params.set('folder', folder)
    params.delete('conversationId')
    params.delete('compose')
    navigate({
      pathname: location.pathname,
      search: `?${params.toString()}`,
    })
    onNavigate()
  }

  const selectConversation = (conversationId: string) => {
    const params = new URLSearchParams(location.search)
    params.set('conversationId', conversationId)
    params.delete('compose')
    navigate({
      pathname: location.pathname,
      search: `?${params.toString()}`,
    })
    onNavigate()
  }

  const loadMoreConversations = () => {
    if (
      !companyId ||
      !selectedConnectionId ||
      conversationLoadingMore ||
      conversationPage + 1 >= conversationTotalPages
    ) {
      return
    }
    void dispatch(
      fetchInboxConversations({
        companyId,
        epoch: workspaceEpoch,
        connectionId: selectedConnectionId,
        folder: selectedFolder,
        page: conversationPage + 1,
        append: true,
      }),
    )
  }

  const connectAllowed =
    channelType === 'EMAIL'
      ? can(PERMISSIONS.EMAIL_CONNECT)
      : channelType === 'WHATSAPP'
        ? can(PERMISSIONS.WHATSAPP_CONNECT)
        : can(PERMISSIONS.SMS_CONNECT)

  const emptyListLabel = emailMode ? 'Conversations' : mode === 'inbox' ? 'Chats' : 'Broadcasts'

  const effectiveError =
    error ||
    (!broadcastMode && channelType === 'EMAIL' && inbox.connectionsStatus === 'error'
      ? 'Unable to load connected email accounts.'
      : emailMode && sameConversationScope && inbox.conversationsStatus === 'error'
        ? 'Unable to load conversations.'
        : null)

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="admin-sidebar-header">
        <div className="min-w-0">
          <p className="eyebrow">{mode === 'inbox' ? 'INBOX' : 'BROADCAST'}</p>
          <h2 className="mt-1 text-[15px] font-bold text-slate-950">{meta.label}</h2>
        </div>
        <span className="admin-back-button" aria-hidden="true">
          {meta.icon}
        </span>
      </div>

      {(!broadcastMode || channelType !== 'WHATSAPP') && (
        <div className="mt-4">
          <p className="mb-2 px-1 text-[10px] font-bold uppercase tracking-[0.08em] text-slate-400">
            {broadcastMode
              ? 'Broadcast account'
              : channelType === 'EMAIL'
                ? 'Email account'
                : 'Sender'}
          </p>

          <div ref={menuRef} className="relative">
            <button
              type="button"
              className="workspace-switcher"
              disabled={loading || (!broadcastMode && identities.length === 0)}
              aria-haspopup="menu"
              aria-expanded={menuOpen}
              onClick={() => setMenuOpen((current) => !current)}
            >
              <span className="flex size-8 shrink-0 items-center justify-center rounded-[8px] bg-indigo-50 text-indigo-500">
                {channelType === 'EMAIL' && selectedItem ? (
                  emailProviderIcon(selectedItem.providerType)
                ) : channelType === 'WHATSAPP' ? (
                  <FiRadio aria-hidden="true" />
                ) : (
                  meta.icon
                )}
              </span>
              <span className="workspace-switcher-copy min-w-0 flex-1 text-left">
                <span className="block truncate text-[12px] font-semibold text-slate-950">
                  {loading
                    ? 'Loading...'
                    : selectedItem?.label || `No ${meta.label.toLowerCase()} connected`}
                </span>
                <span className="mt-0.5 block truncate text-[10px] font-medium text-slate-500">
                  {selectedItem?.secondary || `${identities.length} connected`}
                </span>
              </span>
              <FiChevronDown
                aria-hidden="true"
                className="workspace-switcher-chevron shrink-0 text-slate-500"
              />
            </button>

            {menuOpen && (
              <div className="workspace-menu left-0 right-auto" role="menu">
                {identities.map((item) => (
                  <button
                    key={`${item.connectionId}:${item.senderId ?? 'connection'}`}
                    type="button"
                    className="workspace-menu-item"
                    role="menuitem"
                    onClick={() => selectIdentity(item)}
                  >
                    <span className="flex size-8 shrink-0 items-center justify-center rounded-[8px] bg-indigo-50 text-indigo-500">
                      {channelType === 'EMAIL' ? (
                        emailProviderIcon(item.providerType)
                      ) : channelType === 'WHATSAPP' ? (
                        <FiRadio aria-hidden="true" />
                      ) : (
                        meta.icon
                      )}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate">{item.label}</span>
                      <span className="mt-0.5 block truncate text-[10px] font-medium text-slate-500">
                        {item.secondary}
                      </span>
                    </span>
                    {selectedItem?.connectionId === item.connectionId &&
                      selectedItem?.senderId === item.senderId && (
                        <FiCheck aria-hidden="true" className="shrink-0 text-indigo-500" />
                      )}
                  </button>
                ))}

                {channelType === 'EMAIL' && companyId && (
                  <div className="border-t border-slate-100 pt-1">
                    {!broadcastMode && connectAllowed && (
                      <EmailProviderPicker
                        companyId={companyId}
                        returnTo={location.pathname}
                        label="Connect inbox email"
                        className="workspace-menu-item justify-start"
                        buttonIcon="plus"
                        onClose={() => setMenuOpen(false)}
                        onError={setError}
                        onConnected={() => {
                          setMenuOpen(false)
                          void dispatch(fetchEmailConnections({ companyId, epoch: workspaceEpoch, force: true }))
                        }}
                      />
                    )}
                    {broadcastMode ? (
                      can(PERMISSIONS.BROADCAST_MODIFY) && (
                        <>
                          <div className="px-3 pb-1 pt-2 text-[9px] font-bold uppercase tracking-[0.08em] text-slate-400">
                            Broadcast email setup
                          </div>
                          <button
                            type="button"
                            className="workspace-menu-item justify-start"
                            role="menuitem"
                            onClick={() => {
                              setMenuOpen(false)
                              navigate('/company/connections?connect=broadcast-email')
                              onNavigate()
                            }}
                          >
                            <span className="flex size-8 shrink-0 items-center justify-center rounded-[8px] bg-indigo-50 text-indigo-600">
                              <FiPlus aria-hidden="true" />
                            </span>
                            <span className="text-left">
                              <span className="block text-[12px] font-semibold text-slate-800">
                                Connect broadcast email
                              </span>
                              <span className="mt-0.5 block text-[10px] font-medium text-slate-500">
                                Guided setup for SES, SendGrid, Azure or MSG91
                              </span>
                            </span>
                          </button>
                          <button
                            type="button"
                            className="workspace-menu-item justify-start"
                            role="menuitem"
                            onClick={() => {
                              setMenuOpen(false)
                              navigate('/company/connections')
                              onNavigate()
                            }}
                          >
                            <span className="flex size-8 shrink-0 items-center justify-center rounded-[8px] bg-slate-50 text-slate-500">
                              <FiLink aria-hidden="true" />
                            </span>
                            <span className="text-left">
                              <span className="block text-[12px] font-semibold text-slate-800">
                                Manage connections
                              </span>
                              <span className="mt-0.5 block text-[10px] font-medium text-slate-500">
                                View connected senders and provider status
                              </span>
                            </span>
                          </button>
                        </>
                      )
                    ) : (
                      <button
                        type="button"
                        className="workspace-menu-item justify-start"
                        role="menuitem"
                        onClick={() => {
                          setMenuOpen(false)
                          navigate('/company/connections')
                          onNavigate()
                        }}
                      >
                        <span className="flex size-8 shrink-0 items-center justify-center rounded-[8px] bg-slate-50 text-slate-500">
                          <FiPlus aria-hidden="true" />
                        </span>
                        <span className="text-left">
                          <span className="block text-[12px] font-semibold text-slate-800">
                            Manage email connections
                          </span>
                          <span className="mt-0.5 block text-[10px] font-medium text-slate-500">
                            Inbox connections and provider settings
                          </span>
                        </span>
                      </button>
                    )}
                  </div>
                )}

                {channelType === 'SMS' &&
                  broadcastMode &&
                  companyId &&
                  can(PERMISSIONS.BROADCAST_MODIFY) && (
                    <div className="border-t border-slate-100 pt-1">
                      <div className="px-3 pb-1 pt-2 text-[9px] font-bold uppercase tracking-[0.08em] text-slate-400">
                        Connect SMS provider
                      </div>
                      <button
                        type="button"
                        className="workspace-menu-item justify-start"
                        role="menuitem"
                        onClick={() => {
                          setMenuOpen(false)
                          navigate('/company/connections?connect=msg91-sms')
                          onNavigate()
                        }}
                      >
                        <span className="flex size-8 shrink-0 items-center justify-center rounded-[8px] bg-slate-50 text-slate-500">
                          <FiSmartphone aria-hidden="true" />
                        </span>
                        <span className="text-left">
                          <span className="block text-[12px] font-semibold text-slate-800">
                            MSG91 SMS
                          </span>
                          <span className="mt-0.5 block text-[10px] font-medium text-slate-500">
                            Guided Flow, Header and template setup
                          </span>
                        </span>
                      </button>
                    </div>
                  )}
              </div>
            )}
          </div>

          {effectiveError && (
            <p className="mt-3 px-1 text-xs leading-5 text-rose-600">{effectiveError}</p>
          )}
        </div>
      )}

      {broadcastMode && legacyBroadcastMode && (
        <div className="mt-4">
          <button
            type="button"
            className="secondary-button w-full gap-2"
            disabled={channelType === 'WHATSAPP' || !can(PERMISSIONS.BROADCAST_CREATE)}
            onClick={createBroadcastGroup}
          >
            <FiPlus aria-hidden="true" />
            <span>New legacy group</span>
          </button>
          <p className="mt-2 px-1 text-[10px] leading-4 text-slate-500">
            Legacy groups are kept for existing history. New Broadcasts should use Contacts, Lists
            or Segments.
          </p>
        </div>
      )}

      {emailMode && (
        <div className="email-folder-switcher" role="tablist" aria-label="Email folders">
          <button
            type="button"
            role="tab"
            aria-selected={selectedFolder === 'INBOX'}
            className={`email-folder-tab ${selectedFolder === 'INBOX' ? 'is-active' : ''}`}
            onClick={() => selectFolder('INBOX')}
          >
            <FiInbox aria-hidden="true" />
            <span>Inbox</span>
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={selectedFolder === 'SENT'}
            className={`email-folder-tab ${selectedFolder === 'SENT' ? 'is-active' : ''}`}
            onClick={() => selectFolder('SENT')}
          >
            <FiSend aria-hidden="true" />
            <span>Sent</span>
          </button>
        </div>
      )}

      <div className="context-conversation-section mt-4 flex min-h-0 flex-1 flex-col border-t border-slate-100 pt-4">
        <div className="mb-2 flex items-center justify-between gap-2 px-1">
          <p className="text-[10px] font-bold uppercase tracking-[0.1em] text-slate-400">
            {emptyListLabel}
          </p>
          {emailMode && conversationTotalElements > 0 && (
            <span className="text-[10px] font-medium text-slate-400">
              {conversationTotalElements}
            </span>
          )}
        </div>

        {emailMode ? (
          <div className="-mx-3 min-h-0 flex-1 overflow-y-auto border-t border-slate-100">
            <ConversationList
              compact
              conversations={conversations}
              connections={connections}
              selectedId={selectedConversationId}
              loading={conversationLoading}
              loadingMore={conversationLoadingMore}
              totalElements={conversationTotalElements}
              hasMore={conversationPage + 1 < conversationTotalPages}
              onSelect={(conversation) => selectConversation(conversation.id)}
              onLoadMore={loadMoreConversations}
            />
          </div>
        ) : broadcastMode && !legacyBroadcastMode ? (
          <div className="rounded-xl border border-indigo-100 bg-indigo-50/50 px-4 py-5">
            <p className="text-xs font-semibold text-slate-800">Canonical audience</p>
            <p className="mt-1 text-[11px] leading-5 text-slate-600">
              Choose Contacts, a List or a Segment in the Broadcast composer. Recipient eligibility
              is resolved server-side before launch.
            </p>
            {channelType !== 'WHATSAPP' && (
              <button
                type="button"
                className="secondary-button mt-3 w-full"
                onClick={() => {
                  const params = new URLSearchParams(location.search)
                  params.set('legacy', '1')
                  navigate({ pathname: location.pathname, search: `?${params.toString()}` })
                }}
              >
                View legacy group history
              </button>
            )}
          </div>
        ) : broadcastMode ? (
          <div className="-mx-2 min-h-0 flex-1 overflow-y-auto px-2 pb-2">
            {broadcast.groupsStatus === 'loading' && broadcastGroups.length === 0 ? (
              <div className="px-2 py-3"><ListSkeleton rows={5} /></div>
            ) : broadcastGroups.length === 0 ? (
              <div className="rounded-xl border border-dashed border-slate-200 bg-white/70 px-4 py-8 text-center">
                <FiUsers className="mx-auto text-2xl text-slate-300" aria-hidden="true" />
                <p className="mt-3 text-xs font-semibold text-slate-700">No groups yet</p>
                <p className="mt-1 text-[11px] leading-5 text-slate-500">
                  Create a reusable recipient group, then send broadcasts to it whenever needed.
                </p>
              </div>
            ) : (
              <div className="grid gap-1.5">
                {broadcastGroups.map((group) => (
                  <button
                    key={group.id}
                    type="button"
                    className={`broadcast-group-nav-item ${
                      selectedGroupId === group.id ? 'is-active' : ''
                    }`}
                    onClick={() => selectBroadcastGroup(group.id)}
                  >
                    <span className="flex size-9 shrink-0 items-center justify-center rounded-[9px] bg-indigo-50 text-indigo-500">
                      <FiUsers aria-hidden="true" />
                    </span>
                    <span className="min-w-0 flex-1 text-left">
                      <span className="block truncate text-xs font-semibold text-slate-800">
                        {group.name}
                      </span>
                      <span className="mt-0.5 block text-[10px] font-medium text-slate-500">
                        {group.memberCount.toLocaleString()} recipients
                      </span>
                    </span>
                  </button>
                ))}
                {broadcast.groupsPage + 1 < broadcast.groupsTotalPages && (
                  <button
                    type="button"
                    className="secondary-button mt-2 w-full"
                    onClick={loadMoreBroadcastGroups}
                    disabled={broadcast.loadingMoreGroups}
                  >
                    {broadcast.loadingMoreGroups ? 'Loading…' : 'Load more'}
                  </button>
                )}
              </div>
            )}
          </div>
        ) : (
          <div className="rounded-xl border border-dashed border-slate-200 bg-white/70 px-4 py-8 text-center">
            <p className="text-xs font-semibold text-slate-700">
              No {emptyListLabel.toLowerCase()} yet
            </p>
            <p className="mt-1 text-[11px] leading-5 text-slate-500">
              Conversations will appear here when this channel is available.
            </p>
          </div>
        )}
      </div>

      {!broadcastMode && channelType !== 'EMAIL' && connectAllowed && (
        <div className="mt-4 border-t border-slate-100 pt-4">
          <button
            type="button"
            className="admin-nav-link w-full text-left"
            onClick={() => {
              navigate('/company/connections')
              onNavigate()
            }}
          >
            <span className="admin-nav-icon">
              <FiLink aria-hidden="true" />
            </span>
            <span>{meta.connectLabel}</span>
          </button>
        </div>
      )}
    </div>
  )
}
