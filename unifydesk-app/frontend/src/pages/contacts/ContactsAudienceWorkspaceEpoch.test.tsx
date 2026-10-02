import { act, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import WorkspaceEpochBoundary from '../../components/workspace/WorkspaceEpochBoundary'
import { contactsApi } from '../../features/contacts/contactsApi'
import { useIam } from '../../features/iam/useIam'
import { useAppSelector } from '../../store/hooks'
import type { SegmentDefinition } from '../../features/contacts/types'
import ListDetailPage from './ListDetailPage'
import ListsPage from './ListsPage'
import SegmentsPage from './SegmentsPage'

vi.mock('../../features/contacts/contactsApi', () => ({
  contactsApi: {
    lists: vi.fn(),
    segments: vi.fn(),
    getList: vi.fn(),
    listMembers: vi.fn(),
    updateList: vi.fn(),
    archiveList: vi.fn(),
    addListMembers: vi.fn(),
    removeListMember: vi.fn(),
    createList: vi.fn(),
    createExport: vi.fn(),
    getExport: vi.fn(),
    exportDownload: vi.fn(),
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
  const promise = new Promise<T>((res) => {
    resolve = res
  })
  return { promise, resolve }
}

const list = (name: string) => ({
  id: 'list-1',
  name,
  memberCount: 1,
  status: 'ACTIVE',
})

const filter = {
  type: 'group' as const,
  op: 'AND' as const,
  children: [],
}

const segment = (name: string): SegmentDefinition => ({
  id: 'segment-1',
  name,
  filterVersion: 1,
  filter,
  version: 1,
  status: 'ACTIVE',
  archived: false,
  count: 1,
  countState: 'EXACT',
})

const memberPage = (name: string) => ({
  items: [
    {
      id: name,
      displayName: name,
      updatedAt: '2026-10-02T00:00:00Z',
    },
  ],
  nextCursor: null,
  hasMore: false,
})

const wrap = (child: ReactNode, entry: string, routePath: string) => (
  <MemoryRouter initialEntries={[entry]}>
    <WorkspaceEpochBoundary>
      <Routes>
        <Route path={routePath} element={child} />
      </Routes>
    </WorkspaceEpochBoundary>
  </MemoryRouter>
)

describe('Contacts audience loaders workspace epoch remediation', () => {
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
  })

  it('never lets delayed A1 Lists replace A2 Lists', async () => {
    const oldA = deferred<ReturnType<typeof list>[]>()
    let aCalls = 0
    vi.mocked(contactsApi.lists).mockImplementation((companyId) => {
      if (companyId === 'company-a' && ++aCalls === 1) return oldA.promise
      return Promise.resolve([list(companyId === 'company-b' ? 'B List' : 'A2 List')])
    })

    const view = render(wrap(<ListsPage />, '/contacts/lists', '/contacts/lists'))

    currentCompanyId = 'company-b'
    currentEpoch = 2
    view.rerender(wrap(<ListsPage />, '/contacts/lists', '/contacts/lists'))
    expect(await screen.findByText('B List')).toBeInTheDocument()

    currentCompanyId = 'company-a'
    currentEpoch = 3
    view.rerender(wrap(<ListsPage />, '/contacts/lists', '/contacts/lists'))
    expect(await screen.findByText('A2 List')).toBeInTheDocument()

    await act(async () => {
      oldA.resolve([list('A1 stale List')])
      await oldA.promise
    })

    expect(screen.queryByText('A1 stale List')).not.toBeInTheDocument()
    expect(screen.getByText('A2 List')).toBeInTheDocument()
  })

  it('never lets delayed A1 Segments replace A2 Segments', async () => {
    const oldA = deferred<SegmentDefinition[]>()
    let aCalls = 0
    vi.mocked(contactsApi.segments).mockImplementation((companyId) => {
      if (companyId === 'company-a' && ++aCalls === 1) return oldA.promise
      return Promise.resolve([
        segment(companyId === 'company-b' ? 'B Segment' : 'A2 Segment'),
      ])
    })

    const view = render(wrap(<SegmentsPage />, '/contacts/segments', '/contacts/segments'))

    currentCompanyId = 'company-b'
    currentEpoch = 2
    view.rerender(wrap(<SegmentsPage />, '/contacts/segments', '/contacts/segments'))
    expect(await screen.findByText('B Segment')).toBeInTheDocument()

    currentCompanyId = 'company-a'
    currentEpoch = 3
    view.rerender(wrap(<SegmentsPage />, '/contacts/segments', '/contacts/segments'))
    expect(await screen.findByText('A2 Segment')).toBeInTheDocument()

    await act(async () => {
      oldA.resolve([segment('A1 stale Segment')])
      await oldA.promise
    })

    expect(screen.queryByText('A1 stale Segment')).not.toBeInTheDocument()
    expect(screen.getByText('A2 Segment')).toBeInTheDocument()
  })

  it('never lets delayed A1 List detail or members replace A2', async () => {
    const oldDetail = deferred<ReturnType<typeof list>>()
    const oldMembers = deferred<ReturnType<typeof memberPage>>()
    let aDetailCalls = 0
    let aMemberCalls = 0

    vi.mocked(contactsApi.getList).mockImplementation((companyId) => {
      if (companyId === 'company-a' && ++aDetailCalls === 1) return oldDetail.promise
      return Promise.resolve(list(companyId === 'company-b' ? 'B Detail' : 'A2 Detail'))
    })
    vi.mocked(contactsApi.listMembers).mockImplementation((companyId) => {
      if (companyId === 'company-a' && ++aMemberCalls === 1) return oldMembers.promise
      return Promise.resolve(
        memberPage(companyId === 'company-b' ? 'B Member' : 'A2 Member'),
      )
    })

    const view = render(
      wrap(<ListDetailPage />, '/contacts/lists/list-1', '/contacts/lists/:listId'),
    )

    currentCompanyId = 'company-b'
    currentEpoch = 2
    view.rerender(
      wrap(<ListDetailPage />, '/contacts/lists/list-1', '/contacts/lists/:listId'),
    )
    expect(await screen.findByText('B Detail')).toBeInTheDocument()
    expect(await screen.findByText('B Member')).toBeInTheDocument()

    currentCompanyId = 'company-a'
    currentEpoch = 3
    view.rerender(
      wrap(<ListDetailPage />, '/contacts/lists/list-1', '/contacts/lists/:listId'),
    )
    expect(await screen.findByText('A2 Detail')).toBeInTheDocument()
    expect(await screen.findByText('A2 Member')).toBeInTheDocument()

    await act(async () => {
      oldDetail.resolve(list('A1 stale Detail'))
      oldMembers.resolve(memberPage('A1 stale Member'))
      await Promise.all([oldDetail.promise, oldMembers.promise])
    })

    expect(screen.queryByText('A1 stale Detail')).not.toBeInTheDocument()
    expect(screen.queryByText('A1 stale Member')).not.toBeInTheDocument()
  })
})
