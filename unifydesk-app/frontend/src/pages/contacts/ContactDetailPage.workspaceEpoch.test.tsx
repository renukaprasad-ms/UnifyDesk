import { act, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import WorkspaceEpochBoundary from '../../components/workspace/WorkspaceEpochBoundary'
import { contactsApi } from '../../features/contacts/contactsApi'
import { useIam } from '../../features/iam/useIam'
import { useAppSelector } from '../../store/hooks'
import type {
  CommunicationPreference,
  ContactDetail,
  Suppression,
} from '../../features/contacts/types'
import ContactDetailPage from './ContactDetailPage'

vi.mock('../../features/contacts/contactsApi', () => ({
  contactsApi: {
    get: vi.fn(),
    tags: vi.fn(),
    customFields: vi.fn(),
    activity: vi.fn(),
    preferences: vi.fn(),
    suppressions: vi.fn(),
    update: vi.fn(),
    addIdentity: vi.fn(),
    setPrimaryIdentity: vi.fn(),
    removeIdentity: vi.fn(),
    addTag: vi.fn(),
    removeTag: vi.fn(),
    updatePreference: vi.fn(),
    createSuppression: vi.fn(),
    liftSuppression: vi.fn(),
  },
  isContactVersionConflict: vi.fn(() => false),
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
  const promise = new Promise<T>((res) => {
    resolve = res
  })
  return { promise, resolve }
}

const detail = (name: string): ContactDetail => ({
  id: 'contact-1',
  displayName: name,
  updatedAt: '2026-10-02T00:00:00Z',
  version: 1,
  identities: [],
  tags: [],
  lists: [],
  customFields: {},
  status: 'ACTIVE',
})

const activity = (title: string) => ({
  items: [
    {
      id: title,
      type: 'TEST',
      title,
      occurredAt: '2026-10-02T00:00:00Z',
    },
  ],
  nextCursor: null,
  hasMore: false,
})

const preference = (source: string): CommunicationPreference => ({
  channel: 'EMAIL',
  purpose: 'MARKETING',
  state: 'OPTED_IN',
  source,
})

const suppression = (source: string): Suppression => ({
  id: source,
  channel: 'EMAIL',
  reasonCode: 'MANUAL',
  source,
  state: 'ACTIVE',
  liftable: false,
  active: true,
})

const page = () => (
  <MemoryRouter initialEntries={['/contacts/contact-1']}>
    <Routes>
      <Route
        path="/contacts/:contactId"
        element={
          <WorkspaceEpochBoundary>
            <ContactDetailPage />
          </WorkspaceEpochBoundary>
        }
      />
    </Routes>
  </MemoryRouter>
)

describe('ContactDetailPage workspace epoch remediation', () => {
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
    vi.mocked(contactsApi.tags).mockResolvedValue([])
    vi.mocked(contactsApi.customFields).mockResolvedValue([])
    vi.mocked(contactsApi.preferences).mockResolvedValue([preference('current')])
    vi.mocked(contactsApi.suppressions).mockResolvedValue([])
  })

  it('synchronously removes A1 Contact state on B and only renders freshly resolved A2 state', async () => {
    const bDetail = deferred<ContactDetail>()
    const oldAActivity = deferred<ReturnType<typeof activity>>()
    const oldAPreferences = deferred<CommunicationPreference[]>()
    const oldASuppressions = deferred<Suppression[]>()

    let aDetailCalls = 0
    let aActivityCalls = 0
    let aPreferenceCalls = 0
    let aSuppressionCalls = 0

    vi.mocked(contactsApi.get).mockImplementation((companyId) => {
      if (companyId === 'company-a') {
        aDetailCalls += 1
        return Promise.resolve(detail(aDetailCalls === 1 ? 'A1 Contact' : 'A2 Contact'))
      }
      return bDetail.promise
    })

    vi.mocked(contactsApi.activity).mockImplementation((companyId) => {
      if (companyId === 'company-a' && ++aActivityCalls === 1) return oldAActivity.promise
      return Promise.resolve(activity(companyId === 'company-b' ? 'B Activity' : 'A2 Activity'))
    })

    vi.mocked(contactsApi.preferences).mockImplementation((companyId) => {
      if (companyId === 'company-a' && ++aPreferenceCalls === 1) return oldAPreferences.promise
      return Promise.resolve([preference(companyId === 'company-b' ? 'B consent' : 'A2 consent')])
    })

    vi.mocked(contactsApi.suppressions).mockImplementation((companyId) => {
      if (companyId === 'company-a' && ++aSuppressionCalls === 1) return oldASuppressions.promise
      return Promise.resolve([suppression(companyId === 'company-b' ? 'B suppression' : 'A2 suppression')])
    })

    const view = render(page())
    expect(await screen.findByText('A1 Contact')).toBeInTheDocument()

    currentCompanyId = 'company-b'
    currentEpoch = 2
    view.rerender(page())

    expect(screen.queryByText('A1 Contact')).not.toBeInTheDocument()
    expect(screen.getByText('Loading Contact…')).toBeInTheDocument()

    await act(async () => {
      oldAActivity.resolve(activity('A1 stale Activity'))
      oldAPreferences.resolve([preference('A1 stale consent')])
      oldASuppressions.resolve([suppression('A1 stale suppression')])
      await Promise.all([
        oldAActivity.promise,
        oldAPreferences.promise,
        oldASuppressions.promise,
      ])
    })

    expect(screen.queryByText('A1 Contact')).not.toBeInTheDocument()
    expect(screen.queryByText('A1 stale Activity')).not.toBeInTheDocument()
    expect(screen.queryByText(/A1 stale consent/)).not.toBeInTheDocument()
    expect(screen.queryByText(/A1 stale suppression/)).not.toBeInTheDocument()

    currentCompanyId = 'company-a'
    currentEpoch = 3
    view.rerender(page())

    expect(screen.queryByText('A1 Contact')).not.toBeInTheDocument()
    expect(await screen.findByText('A2 Contact')).toBeInTheDocument()
    expect(await screen.findByText('A2 Activity')).toBeInTheDocument()
    expect(await screen.findByText(/Source: A2 consent/)).toBeInTheDocument()
    expect(await screen.findByText(/Source: A2 suppression/)).toBeInTheDocument()

    await act(async () => {
      bDetail.resolve(detail('B late Contact'))
      await bDetail.promise
    })

    expect(screen.queryByText('B late Contact')).not.toBeInTheDocument()
    expect(screen.getByText('A2 Contact')).toBeInTheDocument()
  })



  it('keeps delayed A1 detail and activity responses out of A2', async () => {
    const oldDetail = deferred<ContactDetail>()
    const oldActivity = deferred<ReturnType<typeof activity>>()
    let aGetCalls = 0
    let aActivityCalls = 0

    vi.mocked(contactsApi.get).mockImplementation((companyId) => {
      if (companyId === 'company-a' && ++aGetCalls === 1) return oldDetail.promise
      return Promise.resolve(detail(companyId === 'company-b' ? 'B Contact' : 'A2 Contact'))
    })
    vi.mocked(contactsApi.activity).mockImplementation((companyId) => {
      if (companyId === 'company-a' && ++aActivityCalls === 1) return oldActivity.promise
      return Promise.resolve(activity(companyId === 'company-b' ? 'B Activity' : 'A2 Activity'))
    })

    const view = render(page())

    currentCompanyId = 'company-b'
    currentEpoch = 2
    view.rerender(page())
    expect(await screen.findByText('B Contact')).toBeInTheDocument()

    currentCompanyId = 'company-a'
    currentEpoch = 3
    view.rerender(page())
    expect(await screen.findByText('A2 Contact')).toBeInTheDocument()
    expect(await screen.findByText('A2 Activity')).toBeInTheDocument()

    await act(async () => {
      oldDetail.resolve(detail('A1 stale Contact'))
      oldActivity.resolve(activity('A1 stale Activity'))
      await Promise.all([oldDetail.promise, oldActivity.promise])
    })

    expect(screen.queryByText('A1 stale Contact')).not.toBeInTheDocument()
    expect(screen.queryByText('A1 stale Activity')).not.toBeInTheDocument()
    expect(screen.getByText('A2 Contact')).toBeInTheDocument()
  })

  it('keeps delayed A1 consent and suppression responses out of A2', async () => {
    const oldPreferences = deferred<CommunicationPreference[]>()
    const oldSuppressions = deferred<Suppression[]>()
    let aPreferenceCalls = 0
    let aSuppressionCalls = 0

    vi.mocked(contactsApi.get).mockImplementation((companyId) =>
      Promise.resolve(detail(companyId === 'company-b' ? 'B Contact' : currentEpoch === 1 ? 'A1 Contact' : 'A2 Contact')),
    )
    vi.mocked(contactsApi.activity).mockResolvedValue(activity('Activity'))
    vi.mocked(contactsApi.preferences).mockImplementation((companyId) => {
      if (companyId === 'company-a' && ++aPreferenceCalls === 1) return oldPreferences.promise
      return Promise.resolve([preference(companyId === 'company-b' ? 'B consent' : 'A2 consent')])
    })
    vi.mocked(contactsApi.suppressions).mockImplementation((companyId) => {
      if (companyId === 'company-a' && ++aSuppressionCalls === 1) return oldSuppressions.promise
      return Promise.resolve([suppression(companyId === 'company-b' ? 'B suppression' : 'A2 suppression')])
    })

    const view = render(page())
    await waitFor(() => expect(contactsApi.preferences).toHaveBeenCalledTimes(1))

    currentCompanyId = 'company-b'
    currentEpoch = 2
    view.rerender(page())
    expect(await screen.findByText('B Contact')).toBeInTheDocument()

    currentCompanyId = 'company-a'
    currentEpoch = 3
    view.rerender(page())
    expect(await screen.findByText('A2 Contact')).toBeInTheDocument()
    expect(await screen.findByText(/Source: A2 consent/)).toBeInTheDocument()
    expect(await screen.findByText(/Source: A2 suppression/)).toBeInTheDocument()

    await act(async () => {
      oldPreferences.resolve([preference('A1 stale consent')])
      oldSuppressions.resolve([suppression('A1 stale suppression')])
      await Promise.all([oldPreferences.promise, oldSuppressions.promise])
    })

    expect(screen.queryByText(/A1 stale consent/)).not.toBeInTheDocument()
    expect(screen.queryByText(/A1 stale suppression/)).not.toBeInTheDocument()
    expect(screen.getByText(/Source: A2 consent/)).toBeInTheDocument()
  })
})
