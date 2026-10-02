import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import WorkspaceEpochBoundary from '../workspace/WorkspaceEpochBoundary'
import { broadcastApi } from '../../features/broadcast/broadcastApi'
import { contactsApi } from '../../features/contacts/contactsApi'
import { useIam } from '../../features/iam/useIam'
import { useAppSelector } from '../../store/hooks'
import type {
  AudienceSourceDescriptor,
  BroadcastMessage,
  BroadcastSender,
  ChannelReadiness,
  RecipientResolutionPreview,
} from '../../features/broadcast/types'
import CanonicalBroadcastJourney from './CanonicalBroadcastJourney'

vi.mock('../../features/contacts/contactsApi', () => ({
  contactsApi: {
    lists: vi.fn(),
    segments: vi.fn(),
    list: vi.fn(),
  },
}))

vi.mock('../../features/broadcast/broadcastApi', () => ({
  broadcastApi: {
    channelReadiness: vi.fn(),
    senders: vi.fn(),
    previewAudience: vi.fn(),
    createAudienceMessage: vi.fn(),
    send: vi.fn(),
    schedule: vi.fn(),
  },
  isBroadcastAudiencePreviewStale: (error: unknown) => {
    const candidate = error as {
      isAxiosError?: boolean
      response?: { status?: number; data?: { error_message?: string } }
    }
    return (
      candidate.isAxiosError === true &&
      candidate.response?.status === 409 &&
      candidate.response.data?.error_message === 'BROADCAST_AUDIENCE_PREVIEW_STALE'
    )
  },
})))

vi.mock('../../features/whatsapp/whatsappApi', () => ({
  whatsappApi: { templates: vi.fn() },
}))

vi.mock('../../features/managedSms/managedSmsApi', () => ({
  managedSmsApi: {
    headers: vi.fn(),
    templates: vi.fn(),
    previewTemplate: vi.fn(),
  },
}))

vi.mock('../../features/iam/useIam', () => ({ useIam: vi.fn() }))
vi.mock('../../store/hooks', () => ({ useAppSelector: vi.fn() }))

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
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

const list = (name: string) => ({
  id: 'list-1',
  name,
  memberCount: 3,
  status: 'ACTIVE',
})

const contactPage = (name: string) => ({
  items: [
    {
      id: 'contact-1',
      displayName: name,
      updatedAt: '2026-10-02T00:00:00Z',
      primaryEmail: 'contact@example.com',
    },
  ],
  nextCursor: null,
  hasMore: false,
})

const readiness = (label: string): ChannelReadiness => ({
  channel: 'EMAIL',
  availability: 'AVAILABLE',
  configuration: 'READY',
  senders: [
    {
      senderId: 'sender-1',
      label,
      state: 'READY',
      capabilities: {
        providerAcceptance: true,
        deliveryEvents: false,
        readEvents: false,
        inboundReplies: false,
        templateRequired: false,
        freeFormSessionSupported: false,
      },
    },
  ],
})

const sender: BroadcastSender = {
  id: 'sender-1',
  connectionId: 'connection-1',
  channelConnectionId: 'channel-connection-1',
  provider: 'SMTP',
  managementMode: 'CUSTOMER_MANAGED',
  status: 'ACTIVE',
  fromAddress: 'sender@example.com',
  connectionName: 'SMTP',
  region: 'global',
  productionAccessEnabled: true,
  sendingEnabled: true,
}

const descriptor: AudienceSourceDescriptor = {
  sourceType: 'LIST',
  sourceId: 'list-1',
  sourceVersion: '17',
  filterVersion: null,
  filterHash: null,
  contactSelectionHash: null,
  resolvedAt: '2026-10-02T06:00:00Z',
}

const preview = (sourceDescriptor = descriptor): RecipientResolutionPreview => ({
  channel: 'EMAIL',
  purpose: 'MARKETING',
  selectedCount: 3,
  eligibleCount: 3,
  excludedCount: 0,
  sourceDescriptor,
  exclusions: {},
  sampleEligible: [],
  sampleExcluded: [],
  computedAt: '2026-10-02T06:00:00Z',
})

const message = (status: BroadcastMessage['status'] = 'DRAFT'): BroadcastMessage => ({
  id: 'broadcast-1',
  senderId: 'sender-1',
  status,
  createdAt: '2026-10-02T06:00:00Z',
  updatedAt: '2026-10-02T06:00:00Z',
  stats: {
    total: 0,
    pending: 0,
    processing: 0,
    accepted: 0,
    failed: 0,
    bounced: 0,
    unknown: 0,
    skipped: 0,
  },
})

const setupCurrentCatalog = () => {
  vi.mocked(contactsApi.lists).mockImplementation((companyId) =>
    Promise.resolve([list(companyId === 'company-b' ? 'B List' : 'A2 List')]),
  )
  vi.mocked(contactsApi.segments).mockResolvedValue([])
  vi.mocked(contactsApi.list).mockImplementation((companyId) =>
    Promise.resolve(contactPage(companyId === 'company-b' ? 'B Contact' : 'A2 Contact')),
  )
  vi.mocked(broadcastApi.channelReadiness).mockImplementation((companyId) =>
    Promise.resolve(readiness(companyId === 'company-b' ? 'B Sender' : 'A2 Sender')),
  )
  vi.mocked(broadcastApi.senders).mockResolvedValue([sender])
}

const renderJourney = (onCreated = vi.fn()) =>
  render(
    <MemoryRouter initialEntries={['/broadcast/email']}>
      <WorkspaceEpochBoundary>
        <CanonicalBroadcastJourney channel="EMAIL" onCreated={onCreated} />
      </WorkspaceEpochBoundary>
    </MemoryRouter>,
  )

const chooseReadyEmail = async () => {
  await screen.findByRole('option', { name: /A2 List/ })
  fireEvent.change(screen.getByLabelText('Audience'), { target: { value: 'list-1' } })
  fireEvent.change(screen.getByLabelText('Subject'), { target: { value: 'Subject' } })
  fireEvent.change(screen.getByLabelText('Message'), { target: { value: 'Body' } })
  fireEvent.click(screen.getByRole('button', { name: 'Preview eligible recipients' }))
  await screen.findByLabelText('Recipient eligibility preview')
}

describe('CanonicalBroadcastJourney workspace epoch remediation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
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
    setupCurrentCatalog()
    vi.mocked(broadcastApi.previewAudience).mockResolvedValue(preview())
    vi.mocked(broadcastApi.createAudienceMessage).mockResolvedValue(message())
    vi.mocked(broadcastApi.send).mockResolvedValue(message('QUEUED'))
    vi.mocked(broadcastApi.schedule).mockResolvedValue(message('SCHEDULED'))
    vi.spyOn(window, 'confirm').mockReturnValue(true)
  })

  it('never renders a delayed A1 catalog after B then A2 becomes current', async () => {
    const gate = deferred<void>()
    let aCalls = 0
    vi.mocked(contactsApi.lists).mockImplementation((companyId) => {
      if (companyId === 'company-a' && ++aCalls === 1) {
        return gate.promise.then(() => [list('A1 stale List')])
      }
      return Promise.resolve([list(companyId === 'company-b' ? 'B List' : 'A2 List')])
    })
    vi.mocked(contactsApi.segments).mockImplementation((companyId) =>
      companyId === 'company-a' && aCalls === 1 ? gate.promise.then(() => []) : Promise.resolve([]),
    )
    vi.mocked(contactsApi.list).mockImplementation((companyId) =>
      companyId === 'company-a' && aCalls === 1
        ? gate.promise.then(() => contactPage('A1 stale Contact'))
        : Promise.resolve(contactPage(companyId === 'company-b' ? 'B Contact' : 'A2 Contact')),
    )
    vi.mocked(broadcastApi.channelReadiness).mockImplementation((companyId) =>
      companyId === 'company-a' && aCalls === 1
        ? gate.promise.then(() => readiness('A1 stale Sender'))
        : Promise.resolve(readiness(companyId === 'company-b' ? 'B Sender' : 'A2 Sender')),
    )
    vi.mocked(broadcastApi.senders).mockImplementation((companyId) =>
      companyId === 'company-a' && aCalls === 1 ? gate.promise.then(() => [sender]) : Promise.resolve([sender]),
    )

    const view = renderJourney()

    currentCompanyId = 'company-b'
    currentEpoch = 2
    view.rerender(
      <MemoryRouter initialEntries={['/broadcast/email']}>
        <WorkspaceEpochBoundary>
          <CanonicalBroadcastJourney channel="EMAIL" onCreated={vi.fn()} />
        </WorkspaceEpochBoundary>
      </MemoryRouter>,
    )

    currentCompanyId = 'company-a'
    currentEpoch = 3
    view.rerender(
      <MemoryRouter initialEntries={['/broadcast/email']}>
        <WorkspaceEpochBoundary>
          <CanonicalBroadcastJourney channel="EMAIL" onCreated={vi.fn()} />
        </WorkspaceEpochBoundary>
      </MemoryRouter>,
    )

    expect(await screen.findByRole('option', { name: /A2 List/ })).toBeInTheDocument()

    await act(async () => {
      gate.resolve(undefined)
      await gate.promise
    })

    expect(screen.queryByRole('option', { name: /A1 stale List/ })).not.toBeInTheDocument()
    expect(screen.queryByText(/A1 stale Sender/)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Send Broadcast' })).toBeDisabled()
  })

  it('ignores a delayed A1 preview after B then A2 and keeps launch disabled', async () => {
    const stalePreview = deferred<RecipientResolutionPreview>()
    vi.mocked(broadcastApi.previewAudience)
      .mockImplementationOnce(() => stalePreview.promise)
      .mockResolvedValue(preview())

    const view = renderJourney()
    await screen.findByRole('option', { name: /A2 List/ })
    fireEvent.change(screen.getByLabelText('Audience'), { target: { value: 'list-1' } })
    fireEvent.change(screen.getByLabelText('Subject'), { target: { value: 'Subject' } })
    fireEvent.change(screen.getByLabelText('Message'), { target: { value: 'Body' } })
    fireEvent.click(screen.getByRole('button', { name: 'Preview eligible recipients' }))
    await waitFor(() => expect(broadcastApi.previewAudience).toHaveBeenCalledTimes(1))

    currentCompanyId = 'company-b'
    currentEpoch = 2
    view.rerender(
      <MemoryRouter initialEntries={['/broadcast/email']}>
        <WorkspaceEpochBoundary>
          <CanonicalBroadcastJourney channel="EMAIL" onCreated={vi.fn()} />
        </WorkspaceEpochBoundary>
      </MemoryRouter>,
    )
    currentCompanyId = 'company-a'
    currentEpoch = 3
    view.rerender(
      <MemoryRouter initialEntries={['/broadcast/email']}>
        <WorkspaceEpochBoundary>
          <CanonicalBroadcastJourney channel="EMAIL" onCreated={vi.fn()} />
        </WorkspaceEpochBoundary>
      </MemoryRouter>,
    )

    await screen.findByRole('option', { name: /A2 List/ })
    await act(async () => {
      stalePreview.resolve(preview({ ...descriptor, sourceVersion: 'A1-stale' }))
      await stalePreview.promise
    })

    expect(screen.queryByLabelText('Recipient eligibility preview')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Send Broadcast' })).toBeDisabled()
  })

  it('stops a stale A1 draft completion before the subsequent Send call', async () => {
    const draft = deferred<BroadcastMessage>()
    vi.mocked(broadcastApi.createAudienceMessage).mockImplementationOnce(() => draft.promise)

    const view = renderJourney()
    await chooseReadyEmail()
    fireEvent.click(screen.getByRole('button', { name: 'Send Broadcast' }))
    await waitFor(() => expect(broadcastApi.createAudienceMessage).toHaveBeenCalledTimes(1))

    currentCompanyId = 'company-b'
    currentEpoch = 2
    view.rerender(
      <MemoryRouter initialEntries={['/broadcast/email']}>
        <WorkspaceEpochBoundary>
          <CanonicalBroadcastJourney channel="EMAIL" onCreated={vi.fn()} />
        </WorkspaceEpochBoundary>
      </MemoryRouter>,
    )

    await act(async () => {
      draft.resolve(message())
      await draft.promise
    })

    expect(broadcastApi.send).not.toHaveBeenCalled()
    expect(broadcastApi.schedule).not.toHaveBeenCalled()
  })

  it('passes the exact preview descriptor object to canonical Send', async () => {
    const sourceDescriptor = { ...descriptor }
    vi.mocked(broadcastApi.previewAudience).mockResolvedValue(preview(sourceDescriptor))

    renderJourney()
    await chooseReadyEmail()
    fireEvent.click(screen.getByRole('button', { name: 'Send Broadcast' }))

    await waitFor(() => expect(broadcastApi.send).toHaveBeenCalledTimes(1))
    expect(vi.mocked(broadcastApi.send).mock.calls[0]).toEqual([
      'company-a',
      'broadcast-1',
      sourceDescriptor,
    ])
    expect(vi.mocked(broadcastApi.send).mock.calls[0][2]).toBe(sourceDescriptor)
  })

  it('passes the exact preview descriptor object to canonical Schedule', async () => {
    const sourceDescriptor = { ...descriptor, sourceVersion: '18' }
    vi.mocked(broadcastApi.previewAudience).mockResolvedValue(preview(sourceDescriptor))

    renderJourney()
    await chooseReadyEmail()
    fireEvent.change(screen.getByLabelText(/Schedule/), {
      target: { value: '2099-01-01T10:00' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Schedule Broadcast' }))

    await waitFor(() => expect(broadcastApi.schedule).toHaveBeenCalledTimes(1))
    expect(vi.mocked(broadcastApi.schedule).mock.calls[0][0]).toBe('company-a')
    expect(vi.mocked(broadcastApi.schedule).mock.calls[0][1]).toBe('broadcast-1')
    expect(vi.mocked(broadcastApi.schedule).mock.calls[0][3]).toBe(sourceDescriptor)
  })

  it('clears a stale preview conflict and requires an explicit re-preview', async () => {
    vi.mocked(broadcastApi.send).mockRejectedValueOnce({
      isAxiosError: true,
      response: {
        status: 409,
        data: {
          status: false,
          status_code: 409,
          error_message: 'BROADCAST_AUDIENCE_PREVIEW_STALE',
        },
      },
    })

    renderJourney()
    await chooseReadyEmail()
    fireEvent.click(screen.getByRole('button', { name: 'Send Broadcast' }))

    expect(
      await screen.findByText(/audience changed since this preview/i),
    ).toBeInTheDocument()
    expect(screen.queryByLabelText('Recipient eligibility preview')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Send Broadcast' })).toBeDisabled()
    expect(broadcastApi.previewAudience).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByRole('button', { name: 'Preview eligible recipients' }))
    await waitFor(() => expect(broadcastApi.previewAudience).toHaveBeenCalledTimes(2))
    expect(await screen.findByLabelText('Recipient eligibility preview')).toBeInTheDocument()
  })
})
