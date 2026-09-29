import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { channelApi } from '../../features/channel/channelApi'
import { PERMISSIONS } from '../../features/iam/permissions'
import { inboxApi } from '../../features/inbox/inboxApi'
import { whatsappApi } from '../../features/whatsapp/whatsappApi'
import WhatsAppInbox from './WhatsAppInbox'

type StatusChangedEvent = {
  type: string
  occurredAt: string
  payload: {
    connectionId: string
    conversationId: string
    messageId: string
  }
}

const realtimeHarness = vi.hoisted(() => ({
  connected: false,
  statusListener: null as null | ((event: StatusChangedEvent) => void),
  connectionListener: null as null | ((connected: boolean) => void),
}))

const snackbar = {
  success: vi.fn(),
  info: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
}

vi.mock('../../store/hooks', () => ({
  useAppSelector: () => 'company-1',
}))

vi.mock('../../features/iam/useIam', () => ({
  useIam: () => ({
    can: (permission: string) =>
      new Set<string>([
        PERMISSIONS.WHATSAPP_READ,
        PERMISSIONS.WHATSAPP_SEND,
        PERMISSIONS.WHATSAPP_TEMPLATE_READ,
        PERMISSIONS.WHATSAPP_TEMPLATE_MODIFY,
      ]).has(permission),
  }),
}))

vi.mock('../feedback/SnackbarProvider', () => ({
  useSnackbar: () => snackbar,
  showGlobalSnackbar: vi.fn(),
  SnackbarProvider: ({ children }: { children: ReactNode }) => children,
}))

vi.mock('../../features/channel/channelApi', () => ({
  channelApi: {
    list: vi.fn(),
  },
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

vi.mock('../../features/realtime/realtimeClient', () => ({
  realtimeClient: {
    isConnected: vi.fn(() => realtimeHarness.connected),
    subscribe: vi.fn(
      (
        type: string,
        listener: (event: StatusChangedEvent) => void,
      ) => {
        if (type === 'inbox.whatsapp.status.changed') {
          realtimeHarness.statusListener = listener
        }
        return () => {
          if (realtimeHarness.statusListener === listener) {
            realtimeHarness.statusListener = null
          }
        }
      },
    ),
    subscribeConnection: vi.fn((listener: (connected: boolean) => void) => {
      realtimeHarness.connectionListener = listener
      listener(realtimeHarness.connected)
      return () => {
        if (realtimeHarness.connectionListener === listener) {
          realtimeHarness.connectionListener = null
        }
      }
    }),
  },
}))

const connection = {
  id: 'connection-1',
  companyId: 'company-1',
  channelType: 'WHATSAPP' as const,
  providerType: 'META_WHATSAPP' as const,
  name: 'Test WhatsApp',
  externalAccountId: 'waba-1',
  status: 'ACTIVE' as const,
  createdByUserId: 'user-1',
  createdAt: '2026-09-29T00:00:00Z',
  updatedAt: '2026-09-29T00:00:00Z',
  providerConfigured: true,
  capabilities: ['INBOX' as const],
}

const conversation = {
  id: 'conversation-1',
  connectionId: 'connection-1',
  externalConversationId: 'customer-1',
  providerType: 'META_WHATSAPP' as const,
  lastMessagePreview: 'Reply',
  lastMessageAt: '2026-09-29T05:01:00Z',
  unreadCount: 0,
  status: 'OPEN' as const,
  participants: [
    {
      address: '+15550002222',
      displayName: 'Customer',
      participantType: 'PHONE_NUMBER' as const,
    },
  ],
}

const outboundMessage = {
  id: 'message-2',
  conversationId: 'conversation-1',
  externalMessageId: 'provider-message-2',
  direction: 'OUTBOUND' as const,
  status: 'SENT' as const,
  bodyText: 'Reply',
  to: [],
  cc: [],
  bcc: [],
  sentAt: '2026-09-29T05:01:00Z',
  read: true,
  attachments: [],
}

const detailWithStatus = (
  whatsappStatus: 'PENDING' | 'ACCEPTED' | 'SENT' | 'DELIVERED' | 'READ' | 'FAILED' | 'UNKNOWN',
) => ({
  conversation,
  messages: [{ ...outboundMessage, whatsappStatus }],
})

const snapshotWithStatus = (
  whatsappStatus: 'PENDING' | 'ACCEPTED' | 'SENT' | 'DELIVERED' | 'READ' | 'FAILED' | 'UNKNOWN',
) => ({
  conversations: [conversation],
  messages: [{ ...outboundMessage, whatsappStatus }],
  missingConversationIds: [],
  missingMessageIds: [],
})

const statusEvent = (
  overrides: Partial<StatusChangedEvent['payload']> = {},
): StatusChangedEvent => ({
  type: 'inbox.whatsapp.status.changed',
  occurredAt: '2026-09-29T06:00:00Z',
  payload: {
    connectionId: 'connection-1',
    conversationId: 'conversation-1',
    messageId: 'message-2',
    ...overrides,
  },
})

const renderInbox = () =>
  render(
    <MemoryRouter
      initialEntries={[
        '/inbox/social-media?connectionId=connection-1&conversationId=conversation-1',
      ]}
    >
      <WhatsAppInbox />
    </MemoryRouter>,
  )

describe('WhatsApp Inbox Round-2 delivery state and template hardening', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    realtimeHarness.connected = false
    realtimeHarness.statusListener = null
    realtimeHarness.connectionListener = null

    vi.mocked(channelApi.list).mockResolvedValue([connection])
    vi.mocked(inboxApi.conversations).mockResolvedValue({
      items: [conversation],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    })
    vi.mocked(inboxApi.conversationDetail).mockResolvedValue(detailWithStatus('SENT'))
    vi.mocked(inboxApi.realtimeSnapshot).mockResolvedValue(snapshotWithStatus('SENT'))
    vi.mocked(whatsappApi.composer).mockResolvedValue({
      freeFormAllowed: true,
      customerServiceExpiresAt: '2026-09-30T05:00:00Z',
      templateRequired: false,
      supportedMedia: ['IMAGE', 'DOCUMENT'],
      connectionStatus: 'ACTIVE',
    })
    vi.mocked(whatsappApi.templates).mockResolvedValue([])
  })

  it('recovers durable READ from the HTTP detail response with no local send memory', async () => {
    vi.mocked(inboxApi.conversationDetail).mockResolvedValue(detailWithStatus('READ'))

    renderInbox()

    expect(await screen.findByLabelText('Read')).toBeInTheDocument()
    expect(screen.queryByLabelText('Sent')).not.toBeInTheDocument()
  })

  it('merges DELIVERED then READ from scoped realtime status events', async () => {
    vi.mocked(inboxApi.realtimeSnapshot)
      .mockResolvedValueOnce(snapshotWithStatus('DELIVERED'))
      .mockResolvedValueOnce(snapshotWithStatus('READ'))

    renderInbox()
    expect(await screen.findByLabelText('Sent')).toBeInTheDocument()

    await act(async () => {
      realtimeHarness.statusListener?.(statusEvent())
    })
    expect(await screen.findByLabelText('Delivered')).toBeInTheDocument()

    await act(async () => {
      realtimeHarness.statusListener?.(statusEvent())
    })
    expect(await screen.findByLabelText('Read')).toBeInTheDocument()

    expect(inboxApi.realtimeSnapshot).toHaveBeenNthCalledWith(1, 'company-1', {
      connectionId: 'connection-1',
      folder: 'INBOX',
      detailConversationId: 'conversation-1',
      conversationIds: ['conversation-1'],
      messageIds: ['message-2'],
    })
  })

  it('ignores status events for another connection or conversation', async () => {
    renderInbox()
    expect(await screen.findByLabelText('Sent')).toBeInTheDocument()

    await act(async () => {
      realtimeHarness.statusListener?.(
        statusEvent({ connectionId: 'connection-other' }),
      )
      realtimeHarness.statusListener?.(
        statusEvent({ conversationId: 'conversation-other' }),
      )
    })

    expect(inboxApi.realtimeSnapshot).not.toHaveBeenCalled()
  })

  it('recovers durable status on browser focus and realtime reconnect', async () => {
    vi.mocked(inboxApi.conversationDetail)
      .mockResolvedValueOnce(detailWithStatus('SENT'))
      .mockResolvedValueOnce(detailWithStatus('DELIVERED'))
      .mockResolvedValueOnce(detailWithStatus('READ'))

    renderInbox()
    expect(await screen.findByLabelText('Sent')).toBeInTheDocument()

    await act(async () => {
      window.dispatchEvent(new Event('focus'))
    })
    expect(await screen.findByLabelText('Delivered')).toBeInTheDocument()

    await act(async () => {
      realtimeHarness.connectionListener?.(false)
      realtimeHarness.connectionListener?.(true)
    })
    expect(await screen.findByLabelText('Read')).toBeInTheDocument()
  })

  it('keeps durable UNKNOWN warning semantics without offering a blind retry', async () => {
    vi.mocked(inboxApi.conversationDetail).mockResolvedValue(detailWithStatus('UNKNOWN'))

    renderInbox()

    expect(await screen.findByLabelText('Delivery uncertain')).toBeInTheDocument()
    expect(
      screen.getByText(
        /delivery is uncertain\. refresh for provider status before deciding whether to send another message/i,
      ),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /retry/i })).not.toBeInTheDocument()
  })

  it('submits BODY and variable URL-button parameters in provider button order', async () => {
    vi.mocked(whatsappApi.composer).mockResolvedValue({
      freeFormAllowed: false,
      customerServiceExpiresAt: null,
      templateRequired: true,
      supportedMedia: ['IMAGE', 'DOCUMENT'],
      connectionStatus: 'ACTIVE',
    })
    vi.mocked(whatsappApi.templates).mockResolvedValue([
      {
        id: 'template-url-buttons',
        providerTemplateId: 'provider-template-url-buttons',
        name: 'order_links',
        language: 'en_US',
        category: 'UTILITY',
        status: 'APPROVED',
        syncedAt: '2026-09-29T00:00:00Z',
        components: [
          {
            type: 'BODY',
            text: 'Order {{1}} is ready.',
          },
          {
            type: 'BUTTONS',
            buttons: [
              {
                type: 'URL',
                text: 'Track',
                url: 'https://example.test/orders/{{1}}',
              },
              {
                type: 'URL',
                text: 'Help',
                url: 'https://example.test/help',
              },
              {
                type: 'QUICK_REPLY',
                text: 'Done',
              },
              {
                type: 'URL',
                text: 'Invoice',
                url: 'https://example.test/invoices/{{1}}',
              },
            ],
          },
        ],
      },
    ])
    vi.mocked(whatsappApi.send).mockResolvedValue({
      messageId: 'message-3',
      clientRequestId: 'request-template',
      status: 'ACCEPTED',
    })

    renderInbox()

    expect(await screen.findByText('Approved template')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Template'), {
      target: { value: 'template-url-buttons' },
    })
    fireEvent.change(screen.getByLabelText('Body variable 1'), {
      target: { value: 'ORDER-42' },
    })
    fireEvent.change(screen.getByLabelText('Button variable 1'), {
      target: { value: 'TRACK-42' },
    })
    fireEvent.change(screen.getByLabelText('Button variable 2'), {
      target: { value: 'INVOICE-42' },
    })

    expect(screen.queryByLabelText('Button variable 3')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /send template/i }))

    await waitFor(() =>
      expect(whatsappApi.send).toHaveBeenCalledWith(
        'company-1',
        'conversation-1',
        expect.objectContaining({
          type: 'TEMPLATE',
          templateId: 'template-url-buttons',
          parameters: {
            header: [],
            body: ['ORDER-42'],
            buttons: ['TRACK-42', 'INVOICE-42'],
          },
        }),
      ),
    )
  })
})
