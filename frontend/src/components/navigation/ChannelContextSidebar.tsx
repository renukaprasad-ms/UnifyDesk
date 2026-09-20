import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
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
  FiSmartphone,
} from 'react-icons/fi'
import { SiGmail } from 'react-icons/si'
import { useLocation, useNavigate } from 'react-router'
import EmailProviderPicker from '../email/EmailProviderPicker'
import ConversationList from '../inbox/ConversationList'
import { channelApi } from '../../features/channel/channelApi'
import type { ChannelConnection, ChannelType, SenderIdentity } from '../../features/channel/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { inboxApi } from '../../features/inbox/inboxApi'
import type { InboxConversation } from '../../features/inbox/types'
import { realtimeClient } from '../../features/realtime/realtimeClient'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId } from '../../store/workspace/workspaceSlice'

type ChannelContextSidebarProps = {
  mode: 'inbox' | 'broadcast'
  onNavigate: () => void
}

type IdentityItem = {
  connection: ChannelConnection
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
  return <FiMail aria-hidden="true" />
}

export default function ChannelContextSidebar({ mode, onNavigate }: ChannelContextSidebarProps) {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const { can } = useIam()
  const location = useLocation()
  const navigate = useNavigate()
  const menuRef = useRef<HTMLDivElement | null>(null)
  const channelType = channelFromPath(location.pathname)
  const meta = channelMeta[channelType]
  const [connections, setConnections] = useState<ChannelConnection[]>([])
  const [senders, setSenders] = useState<Record<string, SenderIdentity[]>>({})
  const [loading, setLoading] = useState(true)
  const [menuOpen, setMenuOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [conversations, setConversations] = useState<InboxConversation[]>([])
  const [conversationLoading, setConversationLoading] = useState(false)
  const [conversationLoadingMore, setConversationLoadingMore] = useState(false)
  const [conversationPage, setConversationPage] = useState(0)
  const [conversationTotalPages, setConversationTotalPages] = useState(0)
  const [conversationTotalElements, setConversationTotalElements] = useState(0)

  const search = useMemo(() => new URLSearchParams(location.search), [location.search])
  const selectedConnectionId = search.get('connectionId')
  const selectedSenderId = search.get('senderId')
  const selectedConversationId = search.get('conversationId')
  const selectedFolder = search.get('folder') === 'SENT' ? 'SENT' : 'INBOX'

  useEffect(() => {
    if (!menuOpen) return
    const onPointerDown = (event: PointerEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) setMenuOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [menuOpen])

  useEffect(() => {
    if (!companyId) {
      setConnections([])
      setSenders({})
      setLoading(false)
      return
    }

    let active = true
    setLoading(true)
    setError(null)

    channelApi
      .list(companyId, channelType)
      .then(async (items) => {
        if (!active) return

        const connected = items.filter((item) => item.status === 'ACTIVE')
        setConnections(connected)

        if (channelType === 'EMAIL') {
          setSenders({})
          return
        }

        const senderEntries = await Promise.all(
          connected.map(async (connection) => {
            try {
              return [connection.id, await channelApi.senders(companyId, connection.id)] as const
            } catch {
              return [connection.id, []] as const
            }
          }),
        )

        if (active) setSenders(Object.fromEntries(senderEntries))
      })
      .catch((requestError) => {
        if (active) {
          setError(apiErrorMessage(requestError, `Unable to load connected ${meta.label}`))
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [channelType, companyId, meta.label])

  const identities = useMemo<IdentityItem[]>(() => {
    const result: IdentityItem[] = []

    for (const connection of connections) {
      if (channelType === 'EMAIL') {
        result.push({
          connection,
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
          connection,
          label: identityFallback(connection),
          secondary: connection.providerType.replaceAll('_', ' '),
        })
        continue
      }

      activeSenders.forEach((sender) =>
        result.push({
          connection,
          sender,
          label: sender.value,
          secondary:
            sender.identityType === 'SMS_HEADER' ? `${connection.name} · Header` : connection.name,
        }),
      )
    }

    return result
  }, [channelType, connections, senders])

  const selectedItem = useMemo(
    () =>
      identities.find(
        (item) =>
          item.connection.id === selectedConnectionId &&
          (!item.sender || !selectedSenderId || item.sender.id === selectedSenderId),
      ) ?? null,
    [identities, selectedConnectionId, selectedSenderId],
  )

  useEffect(() => {
    if (loading || identities.length === 0 || selectedItem) return

    const first = identities[0]
    const params = new URLSearchParams(location.search)
    params.set('connectionId', first.connection.id)
    if (channelType === 'EMAIL' && !params.get('folder')) params.set('folder', 'INBOX')
    if (first.sender) params.set('senderId', first.sender.id)
    else params.delete('senderId')
    params.delete('conversationId')

    navigate(
      {
        pathname: location.pathname,
        search: `?${params.toString()}`,
      },
      { replace: true },
    )
  }, [identities, loading, location.pathname, location.search, navigate, selectedItem])

  const loadConversations = useCallback(
    async (page = 0, append = false) => {
      if (
        !companyId ||
        mode !== 'inbox' ||
        channelType !== 'EMAIL' ||
        !selectedConnectionId
      ) {
        setConversations([])
        setConversationPage(0)
        setConversationTotalPages(0)
        setConversationTotalElements(0)
        return
      }

      if (append) setConversationLoadingMore(true)
      else setConversationLoading(true)

      try {
        const inbox = await inboxApi.conversations(
          companyId,
          page,
          30,
          selectedConnectionId,
          selectedFolder,
        )
        setConversations((current) => {
          if (!append) return inbox.items
          const byId = new Map(current.map((conversation) => [conversation.id, conversation]))
          inbox.items.forEach((conversation) => byId.set(conversation.id, conversation))
          return Array.from(byId.values())
        })
        setConversationPage(inbox.page)
        setConversationTotalPages(inbox.totalPages)
        setConversationTotalElements(inbox.totalElements)
      } catch (requestError) {
        setError(apiErrorMessage(requestError, 'Unable to load conversations'))
      } finally {
        setConversationLoading(false)
        setConversationLoadingMore(false)
      }
    },
    [channelType, companyId, mode, selectedConnectionId, selectedFolder],
  )

  useEffect(() => {
    void loadConversations(0, false)
  }, [loadConversations])

  useEffect(() => {
    if (
      mode !== 'inbox' ||
      channelType !== 'EMAIL' ||
      selectedConversationId ||
      conversations.length === 0
    ) {
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
    channelType,
    conversations,
    location.pathname,
    location.search,
    mode,
    navigate,
    selectedConversationId,
  ])

  useEffect(() => {
    if (mode !== 'inbox' || channelType !== 'EMAIL') return
    return realtimeClient.subscribe('inbox.email.changed', () => {
      void loadConversations(0, false)
    })
  }, [channelType, loadConversations, mode])

  const selectIdentity = (item: IdentityItem) => {
    const params = new URLSearchParams(location.search)
    params.set('connectionId', item.connection.id)
    if (channelType === 'EMAIL' && !params.get('folder')) params.set('folder', 'INBOX')
    if (item.sender) params.set('senderId', item.sender.id)
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

  const selectFolder = (folder: 'INBOX' | 'SENT') => {
    const params = new URLSearchParams(location.search)
    params.set('folder', folder)
    params.delete('conversationId')
    params.delete('compose')
    navigate({
      pathname: location.pathname,
      search: `?${params.toString()}`,
    })
  }

  const selectConversation = (conversation: InboxConversation) => {
    const params = new URLSearchParams(location.search)
    params.set('conversationId', conversation.id)
    params.delete('compose')
    navigate({
      pathname: location.pathname,
      search: `?${params.toString()}`,
    })
    onNavigate()
  }

  const connectAllowed =
    channelType === 'EMAIL'
      ? can(PERMISSIONS.EMAIL_CONNECT)
      : channelType === 'WHATSAPP'
        ? can(PERMISSIONS.WHATSAPP_CONNECT)
        : can(PERMISSIONS.SMS_CONNECT)

  const emptyListLabel =
    mode === 'inbox' && channelType === 'EMAIL'
      ? 'Conversations'
      : mode === 'inbox'
        ? 'Chats'
        : 'Broadcasts'

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

      <div className="mt-4">
        <p className="mb-2 px-1 text-[10px] font-bold uppercase tracking-[0.08em] text-slate-400">
          {channelType === 'EMAIL' ? 'Email account' : 'Sender'}
        </p>

        <div ref={menuRef} className="relative">
          <button
            type="button"
            className="workspace-switcher"
            disabled={loading || identities.length === 0}
            aria-haspopup="menu"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((current) => !current)}
          >
            <span className="flex size-8 shrink-0 items-center justify-center rounded-[8px] bg-indigo-50 text-indigo-500">
              {channelType === 'EMAIL' && selectedItem
                ? emailProviderIcon(selectedItem.connection.providerType)
                : channelType === 'WHATSAPP'
                  ? <FiRadio aria-hidden="true" />
                  : meta.icon}
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
                  key={`${item.connection.id}:${item.sender?.id ?? 'connection'}`}
                  type="button"
                  className="workspace-menu-item"
                  role="menuitem"
                  onClick={() => selectIdentity(item)}
                >
                  <span className="flex size-8 shrink-0 items-center justify-center rounded-[8px] bg-indigo-50 text-indigo-500">
                    {channelType === 'EMAIL'
                      ? emailProviderIcon(item.connection.providerType)
                      : channelType === 'WHATSAPP'
                        ? <FiRadio aria-hidden="true" />
                        : meta.icon}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate">{item.label}</span>
                    <span className="mt-0.5 block truncate text-[10px] font-medium text-slate-500">
                      {item.secondary}
                    </span>
                  </span>
                  {selectedItem?.connection.id === item.connection.id &&
                    selectedItem?.sender?.id === item.sender?.id && (
                      <FiCheck aria-hidden="true" className="shrink-0 text-indigo-500" />
                    )}
                </button>
              ))}

              {channelType === 'EMAIL' && connectAllowed && companyId && (
                <div className="border-t border-slate-100 pt-1">
                  <EmailProviderPicker
                    companyId={companyId}
                    returnTo={location.pathname}
                    label="Connect email"
                    className="workspace-menu-item justify-start"
                    buttonIcon="plus"
                    onClose={() => setMenuOpen(false)}
                    onError={setError}
                  />
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
                        Manage email accounts
                      </span>
                      <span className="mt-0.5 block text-[10px] font-medium text-slate-500">
                        Connections and provider settings
                      </span>
                    </span>
                  </button>
                </div>
              )}
            </div>
          )}
        </div>

        {error && <p className="mt-3 px-1 text-xs leading-5 text-rose-600">{error}</p>}
      </div>

      {mode === 'inbox' && channelType === 'EMAIL' && (
        <div className="email-folder-switcher" role="tablist" aria-label="Email folders">
          <button
            type="button"
            role="tab"
            aria-selected={selectedFolder === 'INBOX'}
            className={`email-folder-tab ${
              selectedFolder === 'INBOX' ? 'is-active' : ''
            }`}
            onClick={() => selectFolder('INBOX')}
          >
            <FiInbox aria-hidden="true" />
            <span>Inbox</span>
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={selectedFolder === 'SENT'}
            className={`email-folder-tab ${
              selectedFolder === 'SENT' ? 'is-active' : ''
            }`}
            onClick={() => selectFolder('SENT')}
          >
            <FiSend aria-hidden="true" />
            <span>Sent</span>
          </button>
        </div>
      )}

      <div className="mt-4 flex min-h-0 flex-1 flex-col border-t border-slate-100 pt-4">
        <div className="mb-2 flex items-center justify-between gap-2 px-1">
          <p className="text-[10px] font-bold uppercase tracking-[0.1em] text-slate-400">
            {emptyListLabel}
          </p>
          {mode === 'inbox' && channelType === 'EMAIL' && conversationTotalElements > 0 && (
            <span className="text-[10px] font-medium text-slate-400">
              {conversationTotalElements}
            </span>
          )}
        </div>

        {mode === 'inbox' && channelType === 'EMAIL' ? (
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
              onSelect={selectConversation}
              onLoadMore={() => void loadConversations(conversationPage + 1, true)}
            />
          </div>
        ) : (
          <div className="rounded-xl border border-dashed border-slate-200 bg-white/70 px-4 py-8 text-center">
            <p className="text-xs font-semibold text-slate-700">
              No {emptyListLabel.toLowerCase()} yet
            </p>
            <p className="mt-1 text-[11px] leading-5 text-slate-500">
              {mode === 'broadcast'
                ? 'Created broadcasts will appear in this list.'
                : 'Conversations will appear here when this channel is available.'}
            </p>
          </div>
        )}
      </div>

      {channelType !== 'EMAIL' && connectAllowed && (
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
