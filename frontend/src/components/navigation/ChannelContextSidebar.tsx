import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import {
  FiCheck,
  FiChevronDown,
  FiInbox,
  FiLink,
  FiMail,
  FiMessageSquare,
  FiPlus,
  FiRadio,
  FiSend,
  FiSmartphone,
} from 'react-icons/fi'
import { useLocation, useNavigate } from 'react-router'
import { channelApi } from '../../features/channel/channelApi'
import { gmailApi } from '../../features/channel/gmailApi'
import type { ChannelConnection, ChannelType, SenderIdentity } from '../../features/channel/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
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
  const [addingEmail, setAddingEmail] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const search = useMemo(() => new URLSearchParams(location.search), [location.search])
  const selectedConnectionId = search.get('connectionId')
  const selectedSenderId = search.get('senderId')

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
        if (active)
          setError(apiErrorMessage(requestError, `Unable to load connected ${meta.label}`))
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
    if (first.sender) params.set('senderId', first.sender.id)
    else params.delete('senderId')

    navigate(
      {
        pathname: location.pathname,
        search: `?${params.toString()}`,
      },
      { replace: true },
    )
  }, [identities, loading, location.pathname, location.search, navigate, selectedItem])

  const selectIdentity = (item: IdentityItem) => {
    const params = new URLSearchParams(location.search)
    params.set('connectionId', item.connection.id)
    if (item.sender) params.set('senderId', item.sender.id)
    else params.delete('senderId')
    params.delete('compose')

    setMenuOpen(false)
    navigate({
      pathname: location.pathname,
      search: `?${params.toString()}`,
    })
    onNavigate()
  }

  const openCompose = () => {
    if (!selectedItem || channelType !== 'EMAIL') return
    const params = new URLSearchParams(location.search)
    params.set('connectionId', selectedItem.connection.id)
    params.set('compose', '1')
    navigate({
      pathname: '/inbox/email',
      search: `?${params.toString()}`,
    })
    onNavigate()
  }

  const addEmail = async () => {
    if (!companyId || addingEmail) return
    setAddingEmail(true)
    setError(null)
    try {
      const authorization = await gmailApi.authorize(companyId, location.pathname)
      window.location.assign(authorization.authorizationUrl)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Unable to start Gmail connection'))
      setAddingEmail(false)
    }
  }

  const connectAllowed =
    channelType === 'EMAIL'
      ? can(PERMISSIONS.EMAIL_CONNECT)
      : channelType === 'WHATSAPP'
        ? can(PERMISSIONS.WHATSAPP_CONNECT)
        : can(PERMISSIONS.SMS_CONNECT)

  const canCompose =
    mode === 'inbox' &&
    channelType === 'EMAIL' &&
    Boolean(selectedItem) &&
    can(PERMISSIONS.EMAIL_SEND)

  return (
    <>
      <div className="admin-sidebar-header">
        <div className="min-w-0">
          <p className="eyebrow">WORKSPACE</p>
          <h2 className="mt-1 text-[15px] font-bold text-slate-950">{meta.label}</h2>
          <p className="mt-1 text-[11px] leading-4 text-slate-500">
            {mode === 'inbox' ? 'Inbox' : 'Broadcast'}
          </p>
        </div>
        <span className="admin-back-button" aria-hidden="true">
          {meta.icon}
        </span>
      </div>

      <div className="mt-5">
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
              {channelType === 'WHATSAPP' ? <FiRadio aria-hidden="true" /> : meta.icon}
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
                    {channelType === 'WHATSAPP' ? <FiRadio aria-hidden="true" /> : meta.icon}
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

              {channelType === 'EMAIL' && connectAllowed && (
                <button
                  type="button"
                  className="workspace-menu-item border-t border-slate-100 text-indigo-500"
                  role="menuitem"
                  disabled={addingEmail}
                  onClick={() => void addEmail()}
                >
                  <span className="flex items-center gap-2">
                    <FiPlus aria-hidden="true" />
                    {addingEmail ? 'Opening Google…' : 'Add email'}
                  </span>
                </button>
              )}
            </div>
          )}
        </div>

        {error && <p className="mt-3 px-1 text-xs leading-5 text-rose-600">{error}</p>}

        <nav className="mt-5 space-y-1" aria-label={`${meta.label} actions`}>
          <button type="button" className="admin-nav-link is-active w-full text-left">
            <span className="admin-nav-icon">
              {mode === 'inbox' ? <FiInbox aria-hidden="true" /> : <FiSend aria-hidden="true" />}
            </span>
            <span>{mode === 'inbox' ? 'Inbox' : 'Broadcast'}</span>
          </button>

          {canCompose && (
            <button type="button" className="admin-nav-link w-full text-left" onClick={openCompose}>
              <span className="admin-nav-icon">
                <FiSend aria-hidden="true" />
              </span>
              <span>Compose email</span>
            </button>
          )}

          {channelType === 'EMAIL' && connectAllowed && (
            <button
              type="button"
              className="admin-nav-link w-full text-left"
              disabled={addingEmail}
              onClick={() => void addEmail()}
            >
              <span className="admin-nav-icon">
                <FiPlus aria-hidden="true" />
              </span>
              <span>{addingEmail ? 'Opening Google…' : 'Add email'}</span>
            </button>
          )}

          {channelType !== 'EMAIL' && connectAllowed && (
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
          )}
        </nav>
      </div>
    </>
  )
}
