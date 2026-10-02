import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import WorkspaceEpochBoundary from '../../components/workspace/WorkspaceEpochBoundary'
import { contactsApi } from '../../features/contacts/contactsApi'
import { useIam } from '../../features/iam/useIam'
import { useAppSelector } from '../../store/hooks'
import type { SegmentDefinition, SegmentValidation } from '../../features/contacts/types'
import SegmentEditorPage from './SegmentEditorPage'

vi.mock('../../features/contacts/contactsApi', () => ({
  contactsApi: {
    customFields: vi.fn(),
    tags: vi.fn(),
    lists: vi.fn(),
    getSegment: vi.fn(),
    validateSegment: vi.fn(),
    previewSegment: vi.fn(),
    createSegment: vi.fn(),
    updateSegment: vi.fn(),
    archiveSegment: vi.fn(),
    createExport: vi.fn(),
    getExport: vi.fn(),
    exportDownload: vi.fn(),
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

const filter = {
  type: 'group' as const,
  op: 'AND' as const,
  children: [
    {
      type: 'predicate' as const,
      field: { kind: 'BUILTIN_CONTACT' as const, key: 'displayName' },
      operator: 'CONTAINS' as const,
      value: 'A',
    },
  ],
}

const segment = (name: string): SegmentDefinition => ({
  id: 'segment-1',
  name,
  description: null,
  filterVersion: 1,
  filter,
  normalizedFilter: filter,
  version: 7,
})

const page = () => (
  <MemoryRouter initialEntries={['/contacts/segments/segment-1']}>
    <WorkspaceEpochBoundary>
      <Routes>
        <Route path="/contacts/segments/:segmentId" element={<SegmentEditorPage />} />
      </Routes>
    </WorkspaceEpochBoundary>
  </MemoryRouter>
)

describe('SegmentEditorPage workspace epoch remediation', () => {
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
    vi.mocked(contactsApi.customFields).mockResolvedValue([])
    vi.mocked(contactsApi.tags).mockResolvedValue([])
    vi.mocked(contactsApi.lists).mockResolvedValue([])
    vi.mocked(contactsApi.getSegment).mockImplementation((companyId) =>
      Promise.resolve(segment(companyId === 'company-b' ? 'B Segment' : 'A Segment')),
    )
  })

  it('does not continue stale A1 validation into an update mutation', async () => {
    const oldValidation = deferred<SegmentValidation>()
    vi.mocked(contactsApi.validateSegment).mockImplementationOnce(() => oldValidation.promise)
    vi.mocked(contactsApi.updateSegment).mockResolvedValue(segment('Updated'))

    const view = render(page())
    expect(await screen.findByDisplayValue('A Segment')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Save Segment' }))
    await waitFor(() => expect(contactsApi.validateSegment).toHaveBeenCalledTimes(1))

    currentCompanyId = 'company-b'
    currentEpoch = 2
    view.rerender(page())
    expect(await screen.findByDisplayValue('B Segment')).toBeInTheDocument()

    await act(async () => {
      oldValidation.resolve({
        valid: true,
        normalizedFilter: filter,
        filterHash: 'a1-hash',
        costClass: 'LOW',
      })
      await oldValidation.promise
    })

    expect(contactsApi.updateSegment).not.toHaveBeenCalled()
  })
})
