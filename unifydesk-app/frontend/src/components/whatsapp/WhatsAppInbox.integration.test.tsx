import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { channelApi } from '../../features/channel/channelApi'
import { PERMISSIONS } from '../../features/iam/permissions'
import { inboxApi } from '../../features/inbox/inboxApi'
import { mediaApi } from '../../features/media/mediaApi'
import { whatsappApi } from '../../features/whatsapp/whatsappApi'
import WhatsAppInbox from './WhatsAppInbox'

let allowedPermissions = new Set<string>()
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
    can: (permission: string) => allowedPermissions.has(permission),
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
  lastMessagePreview: 'Hello',
  lastMessageAt: '2026-09-29T05:00:00Z',
  unreadCount: 1,
  status: 'OPEN' as const,
  participants: [
    {
      address: '+15550002222',
      displayName: 'Customer',
      participantType: 'PHONE_NUMBER' as const,
    },
  ],
}

const inboundMessage = {
  id: 'message-1',
  conversationId: 'conversation-1',
  externalMessageId: 'provider-message-1',
  direction: 'INBOUND' as const,
  status: 'RECEIVED' as const,
  bodyText: 'Hello',
  to: [],
  cc: [],
  bcc: [],
  sentAt: '2026-09-29T05:00:00Z',
  read: false,
  attachments: [],
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

describe('WhatsAppInbox frozen UX', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    allowedPermissions = new Set<string>([
      PERMISSIONS.WHATSAPP_READ,
      PERMISSIONS.WHATSAPP_SEND,
      PERMISSIONS.WHATSAPP_TEMPLATE_READ,
      PERMISSIONS.WHATSAPP_TEMPLATE_MODIFY,
      PERMISSIONS.MEDIA_UPLOAD,
    ])

    vi.mocked(channelApi.list).mockResolvedValue([connection])
    vi.mocked(inboxApi.conversations).mockResolvedValue({
      items: [conversation],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    })
    vi.mocked(inboxApi.conversationDetail).mockResolvedValue({
      conversation,
      messages: [inboundMessage],
    })
    vi.mocked(whatsappApi.composer).mockResolvedValue({
      freeFormAllowed: true,
      customerServiceExpiresAt: '2026-09-30T05:00:00Z',
      templateRequired: false,
      supportedMedia: ['IMAGE', 'DOCUMENT'],
      connectionStatus: 'ACTIVE',
    })
    vi.mocked(whatsappApi.templates).mockResolvedValue([])
  })

  it('renders a chat composer without email recipient or subject fields', async () => {
    renderInbox()

    expect(await screen.findByLabelText('WhatsApp message')).toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: /^to$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: /^cc$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: /^bcc$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: /subject/i })).not.toBeInTheDocument()
  })

  it('switches to the approved-template flow when the composer requires it', async () => {
    vi.mocked(whatsappApi.composer).mockResolvedValue({
      freeFormAllowed: false,
      customerServiceExpiresAt: null,
      templateRequired: true,
      supportedMedia: ['IMAGE', 'DOCUMENT'],
      connectionStatus: 'ACTIVE',
    })
    vi.mocked(whatsappApi.templates).mockResolvedValue([
      {
        id: 'template-1',
        providerTemplateId: 'provider-template-1',
        name: 'order_update',
        language: 'en_US',
        category: 'UTILITY',
        status: 'APPROVED',
        syncedAt: '2026-09-29T00:00:00Z',
        components: [{ type: 'BODY', text: 'Hello {{1}}, order {{2}} is ready.' }],
      },
    ])
    vi.mocked(whatsappApi.send).mockResolvedValue({
      messageId: 'message-2',
      clientRequestId: 'request-1',
      status: 'ACCEPTED',
    })

    renderInbox()

    expect(await screen.findByText('Approved template')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Template'), {
      target: { value: 'template-1' },
    })
    fireEvent.change(screen.getByLabelText('Body variable 1'), {
      target: { value: 'Customer' },
    })
    fireEvent.change(screen.getByLabelText('Body variable 2'), {
      target: { value: '123' },
    })
    fireEvent.click(screen.getByRole('button', { name: /send template/i }))

    await waitFor(() =>
      expect(whatsappApi.send).toHaveBeenCalledWith(
        'company-1',
        'conversation-1',
        expect.objectContaining({
          type: 'TEMPLATE',
          templateId: 'template-1',
          parameters: {
            header: [],
            body: ['Customer', '123'],
            buttons: [],
          },
        }),
      ),
    )
  })

  it('uploads supported media through the existing Media flow before sending', async () => {
    vi.mocked(mediaApi.initiateUpload).mockResolvedValue({
      mediaId: 'media-1',
      uploadUrl: 'https://storage.example/upload',
      expiresAt: '2026-09-29T12:00:00Z',
      requiredHeaders: {},
    })
    vi.mocked(mediaApi.uploadFileToStorage).mockResolvedValue(undefined)
    vi.mocked(mediaApi.completeUpload).mockResolvedValue({
      mediaId: 'media-1',
      status: 'ACTIVE',
    })
    vi.mocked(whatsappApi.send).mockResolvedValue({
      messageId: 'message-2',
      clientRequestId: 'request-1',
      status: 'ACCEPTED',
    })

    const { container } = renderInbox()
    await screen.findByLabelText('WhatsApp message')

    const input = container.querySelector('input[type="file"]')
    expect(input).not.toBeNull()
    const file = new File(['image-bytes'], 'photo.png', { type: 'image/png' })
    fireEvent.change(input as HTMLInputElement, { target: { files: [file] } })

    await waitFor(() =>
      expect(mediaApi.initiateUpload).toHaveBeenCalledWith(file, 'WHATSAPP_MEDIA', 'company-1'),
    )
    expect(mediaApi.uploadFileToStorage).toHaveBeenCalled()
    expect(mediaApi.completeUpload).toHaveBeenCalledWith('media-1')
    expect(whatsappApi.send).toHaveBeenCalledWith(
      'company-1',
      'conversation-1',
      expect.objectContaining({ type: 'MEDIA', mediaId: 'media-1' }),
    )
  })

  it('renders UNKNOWN as delivery uncertainty and does not offer a blind retry', async () => {
    vi.mocked(inboxApi.conversationDetail)
      .mockResolvedValueOnce({
        conversation,
        messages: [inboundMessage],
      })
      .mockResolvedValue({
        conversation,
        messages: [inboundMessage, outboundMessage],
      })
    vi.mocked(whatsappApi.send).mockResolvedValue({
      messageId: 'message-2',
      clientRequestId: 'request-unknown',
      status: 'UNKNOWN',
      safeErrorCode: 'WHATSAPP_SEND_AMBIGUOUS',
    })

    renderInbox()
    const composer = await screen.findByLabelText('WhatsApp message')
    fireEvent.change(composer, { target: { value: 'Reply' } })
    fireEvent.click(screen.getByRole('button', { name: /^send$/i }))

    expect(
      await screen.findByText(
        /delivery is uncertain\. refresh for provider status before deciding whether to send another message/i,
      ),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /retry/i })).not.toBeInTheDocument()
    expect(snackbar.warning).toHaveBeenCalledWith(
      expect.stringMatching(/do not resend immediately/i),
    )
  })

  it('does not expose send, template or media controls without their permissions', async () => {
    allowedPermissions = new Set<string>([PERMISSIONS.WHATSAPP_READ])

    renderInbox()
    await screen.findByText('Hello')

    expect(whatsappApi.composer).not.toHaveBeenCalled()
    expect(screen.queryByLabelText('WhatsApp message')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^send$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /image \/ pdf/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^template$/i })).not.toBeInTheDocument()
  })
})
