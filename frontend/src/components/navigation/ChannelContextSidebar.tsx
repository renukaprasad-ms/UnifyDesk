import { useEffect, useMemo, useState } from 'react'
import {
  FiArrowLeft,
  FiHash,
  FiLink,
  FiMail,
  FiMessageSquare,
  FiRadio,
  FiSmartphone,
} from 'react-icons/fi'
import { Link, useLocation } from 'react-router'
import { channelApi } from '../../features/channel/channelApi'
import type { ChannelConnection, ChannelType, SenderIdentity } from '../../features/channel/types'
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

const channelOrder: ChannelType[] = ['EMAIL', 'WHATSAPP', 'SMS']

const channelMeta: Record<
  ChannelType,
  { label: string; icon: React.ReactNode; path: { inbox: string; broadcast: string } }
> = {
  EMAIL: {
    label: 'Email',
    icon: <FiMail />,
    path: { inbox: '/inbox/email', broadcast: '/broadcast/email' },
  },
  WHATSAPP: {
    label: 'WhatsApp',
    icon: <FiMessageSquare />,
    path: { inbox: '/inbox/social-media', broadcast: '/broadcast/social-media' },
  },
  SMS: {
    label: 'SMS',
    icon: <FiSmartphone />,
    path: { inbox: '/inbox/sms', broadcast: '/broadcast/sms' },
  },
}

const identityFallback = (connection: ChannelConnection) => {
  if (connection.channelType === 'EMAIL') {
    return connection.externalAccountId || connection.name
  }
  return connection.name
}

export default function ChannelContextSidebar({ mode, onNavigate }: ChannelContextSidebarProps) {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const location = useLocation()
  const [connections, setConnections] = useState<ChannelConnection[]>([])
  const [senders, setSenders] = useState<Record<string, SenderIdentity[]>>({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const search = useMemo(() => new URLSearchParams(location.search), [location.search])
  const selectedConnectionId = search.get('connectionId')
  const selectedSenderId = search.get('senderId')

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
      .list(companyId)
      .then(async (items) => {
        if (!active) return
        const connected = items.filter((item) => item.status === 'ACTIVE')
        setConnections(connected)

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
        if (active) setError(apiErrorMessage(requestError, 'Unable to load connected channels'))
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [companyId])

  const identitiesByChannel = useMemo(() => {
    const grouped: Record<ChannelType, IdentityItem[]> = {
      EMAIL: [],
      WHATSAPP: [],
      SMS: [],
    }

    for (const connection of connections) {
      const identities = (senders[connection.id] ?? []).filter(
        (sender) => sender.status === 'ACTIVE' || sender.status === 'PENDING',
      )

      if (identities.length === 0) {
        grouped[connection.channelType].push({
          connection,
          label: identityFallback(connection),
          secondary: connection.providerType.replaceAll('_', ' '),
        })
        continue
      }

      for (const sender of identities) {
        grouped[connection.channelType].push({
          connection,
          sender,
          label: sender.value,
          secondary:
            sender.identityType === 'SMS_HEADER' ? `${connection.name} · Header` : connection.name,
        })
      }
    }

    return grouped
  }, [connections, senders])

  const buildHref = (item: IdentityItem) => {
    const params = new URLSearchParams()
    params.set('connectionId', item.connection.id)
    if (item.sender) params.set('senderId', item.sender.id)
    return `${channelMeta[item.connection.channelType].path[mode]}?${params.toString()}`
  }

  const isActive = (item: IdentityItem) => {
    const expectedPath = channelMeta[item.connection.channelType].path[mode]
    return (
      location.pathname === expectedPath &&
      selectedConnectionId === item.connection.id &&
      (!item.sender || !selectedSenderId || selectedSenderId === item.sender.id)
    )
  }

  return (
    <>
      <div className="admin-sidebar-header">
        <div className="min-w-0">
          <p className="eyebrow">WORKSPACE</p>
          <h2 className="mt-1 text-[15px] font-bold text-slate-950">
            {mode === 'inbox' ? 'Inbox' : 'Broadcast'}
          </h2>
          <p className="mt-1 text-[11px] leading-4 text-slate-500">
            {mode === 'inbox' ? 'Connected channels' : 'Send from connected channels'}
          </p>
        </div>
        <Link
          to="/workspace"
          className="admin-back-button"
          onClick={onNavigate}
          title="Back to workspace"
        >
          <FiArrowLeft aria-hidden="true" />
        </Link>
      </div>

      <div className="mt-5">
        {loading ? (
          <p className="px-2 py-4 text-xs text-slate-500">Loading connected channels...</p>
        ) : error ? (
          <p className="px-2 py-4 text-xs leading-5 text-rose-600">{error}</p>
        ) : (
          <nav aria-label={mode === 'inbox' ? 'Inbox accounts' : 'Broadcast senders'}>
            {channelOrder.map((channelType) => {
              const items = identitiesByChannel[channelType]
              const meta = channelMeta[channelType]

              return (
                <div key={channelType} className="mb-5">
                  <div className="mb-2 flex items-center justify-between px-2">
                    <div className="flex items-center gap-2 text-[10px] font-bold uppercase tracking-[0.08em] text-slate-400">
                      <span className="inline-flex text-slate-400">{meta.icon}</span>
                      <span>{meta.label}</span>
                    </div>
                    <span className="text-[10px] font-semibold text-slate-400">{items.length}</span>
                  </div>

                  {items.length === 0 ? (
                    <Link
                      to="/company/connections"
                      onClick={onNavigate}
                      className="flex items-center gap-2 rounded-lg px-2 py-2 text-[11px] font-medium text-slate-400 hover:bg-white hover:text-indigo-500"
                    >
                      <FiLink aria-hidden="true" />
                      <span>Connect {meta.label}</span>
                    </Link>
                  ) : (
                    <div className="space-y-1">
                      {items.map((item) => {
                        const activeItem = isActive(item)
                        return (
                          <Link
                            key={`${item.connection.id}:${item.sender?.id ?? 'connection'}`}
                            to={buildHref(item)}
                            onClick={onNavigate}
                            className={`block rounded-lg px-3 py-2.5 transition ${
                              activeItem
                                ? 'bg-white text-indigo-600 shadow-[inset_0_0_0_1px_#e7e9ff]'
                                : 'text-slate-600 hover:bg-white hover:text-indigo-600'
                            }`}
                          >
                            <div className="flex min-w-0 items-center gap-2">
                              <span
                                className={`inline-flex size-7 shrink-0 items-center justify-center rounded-lg ${
                                  activeItem
                                    ? 'bg-indigo-50 text-indigo-600'
                                    : 'bg-white text-slate-400'
                                }`}
                              >
                                {channelType === 'SMS' &&
                                item.sender?.identityType === 'SMS_HEADER' ? (
                                  <FiHash aria-hidden="true" />
                                ) : channelType === 'WHATSAPP' ? (
                                  <FiRadio aria-hidden="true" />
                                ) : (
                                  meta.icon
                                )}
                              </span>
                              <span className="min-w-0">
                                <span className="block truncate text-[12px] font-semibold">
                                  {item.label}
                                </span>
                                <span className="mt-0.5 block truncate text-[10px] text-slate-400">
                                  {item.secondary}
                                </span>
                              </span>
                            </div>
                          </Link>
                        )
                      })}
                    </div>
                  )}
                </div>
              )
            })}
          </nav>
        )}
      </div>
    </>
  )
}
