import { act, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import WorkspaceEpochBoundary from '../workspace/WorkspaceEpochBoundary'
import { channelApi } from '../../features/channel/channelApi'
import { inboxApi } from '../../features/inbox/inboxApi'
import { whatsappApi } from '../../features/whatsapp/whatsappApi'
import { useAppSelector } from '../../store/hooks'
import { useIam } from '../../features/iam/useIam'
import { realtimeClient } from '../../features/realtime/realtimeClient'
import type { ChannelConnection } from '../../features/channel/types'
import type { InboxConversation } from '../../features/inbox/types'
import WhatsAppInbox from './WhatsAppInbox'

const realtimeHarness = vi.hoisted(() => ({
  listeners: [] as Array<(event: { type: string; occurredAt: string; payload: unknown }) => void>,
}))

vi.mock('../../features/channel/channelApi', () => ({
  channelApi: { list: vi.fn() },
}))

vi.mock('../../features/inbox/inboxApi', () => ({
  inboxApi: {
    conversations: vi.fn(),
    conversationDetail: vi.fn(),
    realtimeSnapshot: vi.fn(),
  },
}))

vi.mock('../../features/whatsapp/whatsappApi', () => ({
  whatsappApi: {
    composer: vi.fn(),
    templates: vi.fn(),
    syncTemplates: vi.fn(),
    send: vi.fn(),
  },
}))

vi.mock('../../features/media/mediaApi', () => ({
  mediaApi: {
    initiateUpload: vi.fn(),
    uploadFileToStorage: vi.fn(),
    completeUpload: vi.fn(),
  },
}))

vi.mock('../../features/realtime/realtimeClient', () => ({
  realtimeClient: {
    isConnected: vi.fn(() => true),
    subscribe: vi.fn((_type: string, listener: typeof realtimeHarness.listeners[number]) => {
      realtimeHarness.listeners.push(listener)
      return () => undefined
    }),
    subscribeConnection: vi.fn((listener: (connected: boolean) => void) => {
      listener(true)
      return () => undefined
    }),
  },
}))

vi.mock('../../features/iam/useIam', () => ({ useIam: vi.fn() }))
vi.mock('../../store/hooks', () => ({ useAppSelector: vi.fn() }))
vi.mock('../feedback/SnackbarProvider', () => ({
  useSnackbar: () => ({
    success: vi.fn(),
    info: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
  }),
}))

let currentCompanyId: string | null = 'company-a'
let currentEpoch = 1

const fakeState = () =>
  ({
    workspace: {
      companies: [],
      selectedCompanyId: currentCompanyId,
      epoch: currentEpoch,
    },
  }) as never

const deferred = <T,>() => {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((res) => {
    resolve = res
  })
  return { promise, resolve }
}

const connection = (companyId: string, name: string): ChannelConnection => ({
  id: 'connection-1',
  companyId,
  channelType: 'WHATSAPP',
  providerType: 'META_WHATSAPP',
  name,
  externalAccountId: 'waba-1',
  status: 'ACTIVE',
  createdByUserId: 'user-1',
  createdAt: '2026-10-02T00:00:00Z',
  updatedAt: '2026-10-02T00:00:00Z',
  providerConfigured: true,
  capabilities: ['INBOX'],
})

const conversation = (preview: string): InboxConversation => ({
  id: 'conversation-1',
  connectionId: 'connection-1',
  externalConversationId: 'customer-1',
  providerType: 'META_WHATSAPP',
  lastMessagePreview: preview,
  lastMessageAt: '2026-10-02T00:00:00Z',
  unreadCount: 0,
  status: 'OPEN',
  participants: [
    {
      address: '+15550001111',
      displayName: preview,
      participantType: 'PHONE_NUMBER',
    },
  ],
})

const renderInbox = () =>
  render(
    <MemoryRouter
      initialEntries={[
        '/inbox/social-media?connectionId=connection-1&conversationId=conversation-1',
      ]}
    >
      <WorkspaceEpochBoundary>
        <WhatsAppInbox />
      </WorkspaceEpochBoundary>
    </MemoryRouter>,
  )

const rerenderInbox = (view: ReturnType<typeof renderInbox>) =>
  view.rerender(
    <MemoryRouter
      initialEntries={[
        '/inbox/social-media?connectionId=connection-1&conversationId=conversation-1',
      ]}
    >
      <WorkspaceEpochBoundary>
        <WhatsAppInbox />
      </WorkspaceEpochBoundary>
    </MemoryRouter>,
  )

describe('WhatsAppInbox workspace epoch remediation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    realtimeHarness.listeners.length = 0
    currentCompanyId = 'company-a'
    currentEpoch = 1
    vi.mocked(useAppSelector).mockImplementation((selector) => selector(fakeState()))
    vi.mocked(useIam).mockReturnValue({
      owner: false,
      permissions: [],
      status: 'ready',
      companyId: 'company-a',
      error: null,
      can: () => true,
      canAny: () => true,
      canAll: () => true,
    })
    vi.mocked(inboxApi.conversationDetail).mockImplementation((_companyId, conversationId) =>
      Promise.resolve({
        conversation: conversation(conversationId),
        messages: [],
      }),
    )
    vi.mocked(whatsappApi.composer).mockResolvedValue({
      freeFormAllowed: true,
      customerServiceExpiresAt: null,
      templateRequired: false,
      supportedMedia: [],
      connectionStatus: 'ACTIVE',
    })
    vi.mocked(whatsappApi.templates).mockResolvedValue([])
  })

  it('invalidates connections and ignores a delayed A1 connection result in A2', async () => {
    const oldA = deferred<ChannelConnection[]>()
    let aCalls = 0
    vi.mocked(channelApi.list).mockImplementation((companyId) => {
      if (companyId === 'company-a' && ++aCalls === 1) return oldA.promise
      return Promise.resolve([
        connection(
          companyId,
          companyId === 'company-b' ? 'B Connection' : 'A2 Connection',
        ),
      ])
    })
    vi.mocked(inboxApi.conversations).mockImplementation((companyId) =>
      Promise.resolve({
        items: [conversation(companyId === 'company-b' ? 'B Chat' : 'A2 Chat')],
        page: 0,
        size: 50,
        totalElements: 1,
        totalPages: 1,
      }),
    )

    const view = renderInbox()

    currentCompanyId = 'company-b'
    currentEpoch = 2
    rerenderInbox(view)
    expect(await screen.findByText('B Connection')).toBeInTheDocument()

    currentCompanyId = 'company-a'
    currentEpoch = 3
    rerenderInbox(view)
    expect(await screen.findByText('A2 Connection')).toBeInTheDocument()

    await act(async () => {
      oldA.resolve([connection('company-a', 'A1 stale Connection')])
      await oldA.promise
    })

    expect(screen.queryByText('A1 stale Connection')).not.toBeInTheDocument()
    expect(screen.getByText('A2 Connection')).toBeInTheDocument()
  })

  it('ignores a delayed A1 conversation result after A1 -> B -> A2', async () => {
    let aConnectionCalls = 0
    vi.mocked(channelApi.list).mockImplementation((companyId) => {
      if (companyId === 'company-a') aConnectionCalls += 1
      return Promise.resolve([
        connection(
          companyId,
          companyId === 'company-b' ? 'B Connection' : aConnectionCalls === 1 ? 'A1 Connection' : 'A2 Connection',
        ),
      ])
    })

    const oldConversation = deferred<{
      items: InboxConversation[]
      page: number
      size: number
      totalElements: number
      totalPages: number
    }>()
    let aConversationCalls = 0
    vi.mocked(inboxApi.conversations).mockImplementation((companyId) => {
      if (companyId === 'company-a' && ++aConversationCalls === 1) return oldConversation.promise
      return Promise.resolve({
        items: [conversation(companyId === 'company-b' ? 'B Chat' : 'A2 Chat')],
        page: 0,
        size: 50,
        totalElements: 1,
        totalPages: 1,
      })
    })

    const view = renderInbox()
    await screen.findByText('A1 Connection')
    await waitFor(() => expect(inboxApi.conversations).toHaveBeenCalledTimes(1))

    currentCompanyId = 'company-b'
    currentEpoch = 2
    rerenderInbox(view)
    expect(await screen.findByText('B Chat')).toBeInTheDocument()

    currentCompanyId = 'company-a'
    currentEpoch = 3
    rerenderInbox(view)
    expect(await screen.findByText('A2 Chat')).toBeInTheDocument()

    await act(async () => {
      oldConversation.resolve({
        items: [conversation('A1 stale Chat')],
        page: 0,
        size: 50,
        totalElements: 1,
        totalPages: 1,
      })
      await oldConversation.promise
    })

    expect(screen.queryByText('A1 stale Chat')).not.toBeInTheDocument()
    expect(screen.getByText('A2 Chat')).toBeInTheDocument()
  })

  it('ignores a realtime callback retained from the unmounted A1 epoch', async () => {
    let aCalls = 0
    vi.mocked(channelApi.list).mockImplementation((companyId) => {
      if (companyId === 'company-a') aCalls += 1
      return Promise.resolve([
        connection(
          companyId,
          companyId === 'company-b' ? 'B Connection' : aCalls === 1 ? 'A1 Connection' : 'A2 Connection',
        ),
      ])
    })
    vi.mocked(inboxApi.conversations).mockImplementation((companyId) =>
      Promise.resolve({
        items: [conversation(companyId === 'company-b' ? 'B Chat' : currentEpoch === 1 ? 'A1 Chat' : 'A2 Chat')],
        page: 0,
        size: 50,
        totalElements: 1,
        totalPages: 1,
      }),
    )

    const view = renderInbox()
    expect(await screen.findByText('A1 Chat')).toBeInTheDocument()
    await waitFor(() => expect(realtimeClient.subscribe).toHaveBeenCalled())
    const oldListener = realtimeHarness.listeners[0]

    currentCompanyId = 'company-b'
    currentEpoch = 2
    rerenderInbox(view)
    expect(await screen.findByText('B Chat')).toBeInTheDocument()

    currentCompanyId = 'company-a'
    currentEpoch = 3
    rerenderInbox(view)
    expect(await screen.findByText('A2 Chat')).toBeInTheDocument()

    await act(async () => {
      oldListener({
        type: 'inbox.whatsapp.status.changed',
        occurredAt: '2026-10-02T00:00:00Z',
        payload: {
          connectionId: 'connection-1',
          conversationId: 'conversation-1',
          messageId: 'message-a1',
        },
      })
    })

    expect(inboxApi.realtimeSnapshot).not.toHaveBeenCalled()
    expect(screen.getByText('A2 Chat')).toBeInTheDocument()
  })
})
