import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PERMISSIONS } from '../../features/iam/permissions'
import { channelApi } from '../../features/channel/channelApi'
import { metaWhatsAppApi } from '../../features/channel/metaWhatsAppApi'
import { launchMetaWhatsAppSignup, loadMetaSdk } from '../../features/channel/metaWhatsAppSdk'
import CompanyConnections from './CompanyConnections'

const snackbar = {
  success: vi.fn(),
  info: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
}

vi.mock('../../store/hooks', () => ({
  useAppSelector: () => ({ companyId: 'company-1', name: 'Test Company' }),
}))

vi.mock('../../features/iam/useIam', () => ({
  useIam: () => ({
    can: (permission: string) =>
      new Set<string>([
        PERMISSIONS.WHATSAPP_READ,
        PERMISSIONS.WHATSAPP_CONNECT,
        PERMISSIONS.WHATSAPP_MODIFY,
        PERMISSIONS.WHATSAPP_DISCONNECT,
      ]).has(permission),
  }),
}))

vi.mock('../../components/feedback/SnackbarProvider', () => ({
  useSnackbar: () => snackbar,
  showGlobalSnackbar: vi.fn(),
  SnackbarProvider: ({ children }: { children: ReactNode }) => children,
}))

vi.mock('../../features/channel/channelApi', () => ({
  channelApi: {
    list: vi.fn(),
    senders: vi.fn(),
    disconnect: vi.fn(),
  },
}))

vi.mock('../../features/channel/metaWhatsAppApi', () => ({
  metaWhatsAppApi: {
    signupSession: vi.fn(),
    reauthorizeSession: vi.fn(),
    complete: vi.fn(),
    connection: vi.fn(),
    reconcile: vi.fn(),
    disconnect: vi.fn(),
  },
}))

vi.mock('../../features/channel/metaWhatsAppSdk', () => ({
  loadMetaSdk: vi.fn(),
  launchMetaWhatsAppSignup: vi.fn(),
}))

vi.mock('../../features/broadcast/broadcastApi', () => ({
  broadcastApi: {
    senders: vi.fn(),
    activeSetupSession: vi.fn(),
  },
}))

const signupSession = {
  appId: 'app',
  configurationId: 'configuration',
  graphApiVersion: 'v25.0',
  embeddedSignupVersion: '4' as const,
  state: 'fresh-state',
  expiresAt: '2026-09-29T12:00:00Z',
  coexistenceAvailable: false,
}

const activeConnection = {
  connectionId: 'connection-1',
  genericStatus: 'ACTIVE' as const,
  status: 'ACTIVE' as const,
  authState: 'VALID' as const,
  registrationState: 'REGISTERED' as const,
  subscriptionState: 'SUBSCRIBED' as const,
  coexistenceState: 'DISABLED' as const,
  phone: {
    phoneNumberId: 'phone-1',
    displayPhoneNumber: '+15550001111',
    verifiedName: 'Test Business',
    qualityRating: 'GREEN',
  },
  nextAction: 'NONE' as const,
}

const renderConnections = () =>
  render(
    <MemoryRouter initialEntries={['/company/connections?section=whatsapp']}>
      <CompanyConnections />
    </MemoryRouter>,
  )

describe('CompanyConnections WhatsApp onboarding', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(channelApi.list).mockResolvedValue([])
    vi.mocked(channelApi.senders).mockResolvedValue([])
    vi.mocked(metaWhatsAppApi.connection).mockRejectedValue(new Error('not connected'))
    vi.mocked(metaWhatsAppApi.signupSession).mockResolvedValue(signupSession)
    vi.mocked(loadMetaSdk).mockResolvedValue(undefined)
    vi.mocked(launchMetaWhatsAppSignup).mockResolvedValue({
      code: 'code-1',
      wabaId: 'waba-1',
      phoneNumberId: 'phone-1',
    })
    vi.mocked(metaWhatsAppApi.complete).mockResolvedValue(activeConnection)
  })

  it('does not mint signup state on page load and obtains a fresh session for each explicit connect', async () => {
    renderConnections()

    await waitFor(() => expect(channelApi.list).toHaveBeenCalled())
    expect(metaWhatsAppApi.connection).toHaveBeenCalledWith('company-1')
    expect(metaWhatsAppApi.signupSession).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: /connect whatsapp/i }))
    await waitFor(() => expect(metaWhatsAppApi.complete).toHaveBeenCalledTimes(1))

    fireEvent.click(screen.getByRole('button', { name: /connect whatsapp/i }))
    await waitFor(() => expect(metaWhatsAppApi.complete).toHaveBeenCalledTimes(2))

    expect(metaWhatsAppApi.signupSession).toHaveBeenCalledTimes(2)
    expect(metaWhatsAppApi.signupSession).toHaveBeenNthCalledWith(1, 'company-1')
    expect(metaWhatsAppApi.signupSession).toHaveBeenNthCalledWith(2, 'company-1')
  })

  it('uses a fresh reauthorization session when reconnecting a disconnected connection', async () => {
    vi.mocked(channelApi.list).mockResolvedValue([
      {
        id: 'connection-1',
        companyId: 'company-1',
        channelType: 'WHATSAPP',
        providerType: 'META_WHATSAPP',
        name: 'Test Business',
        externalAccountId: 'waba-1',
        status: 'DISCONNECTED',
        createdByUserId: 'user-1',
        createdAt: '2026-09-29T00:00:00Z',
        updatedAt: '2026-09-29T00:00:00Z',
        providerConfigured: true,
        capabilities: ['INBOX'],
      },
    ])
    vi.mocked(metaWhatsAppApi.connection).mockResolvedValue({
      connectionId: 'connection-1',
      genericStatus: 'DISCONNECTED',
      status: 'DISCONNECTED',
      authState: 'UNKNOWN',
      registrationState: 'REGISTERED',
      subscriptionState: 'SUBSCRIBED',
      coexistenceState: 'DISABLED',
      phone: activeConnection.phone,
      nextAction: 'NONE',
    })

    renderConnections()

    vi.mocked(metaWhatsAppApi.reauthorizeSession).mockResolvedValue({
      ...signupSession,
      state: 'reconnect-state',
    })

    fireEvent.click(await screen.findByRole('button', { name: /^reconnect$/i }))

    await waitFor(() =>
      expect(metaWhatsAppApi.reauthorizeSession).toHaveBeenCalledWith('company-1'),
    )
    expect(metaWhatsAppApi.signupSession).not.toHaveBeenCalled()
  })

  it('uses the reauthorization session only when nextAction requires reauthorization', async () => {
    vi.mocked(channelApi.list).mockResolvedValue([
      {
        id: 'connection-1',
        companyId: 'company-1',
        channelType: 'WHATSAPP',
        providerType: 'META_WHATSAPP',
        name: 'Test Business',
        externalAccountId: 'waba-1',
        status: 'ACTIVE',
        createdByUserId: 'user-1',
        createdAt: '2026-09-29T00:00:00Z',
        updatedAt: '2026-09-29T00:00:00Z',
        providerConfigured: true,
        capabilities: ['INBOX'],
      },
    ])
    vi.mocked(metaWhatsAppApi.connection).mockResolvedValue({
      ...activeConnection,
      status: 'REAUTH_REQUIRED',
      authState: 'EXPIRED',
      nextAction: 'REAUTHORIZE',
    })
    vi.mocked(metaWhatsAppApi.reauthorizeSession).mockResolvedValue({
      ...signupSession,
      state: 'reauthorize-state',
    })

    renderConnections()

    const reauthorize = await screen.findByRole('button', { name: /reauthorize/i })
    fireEvent.click(reauthorize)

    await waitFor(() =>
      expect(metaWhatsAppApi.reauthorizeSession).toHaveBeenCalledWith('company-1'),
    )
    expect(metaWhatsAppApi.signupSession).not.toHaveBeenCalled()
  })
})
