import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { contactsApi } from '../../features/contacts/contactsApi'
import { useIam } from '../../features/iam/useIam'
import { useAppSelector } from '../../store/hooks'
import SegmentEditorPage from './SegmentEditorPage'
import { asyncJobApi } from '../../features/asyncJob/asyncJobApi'

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
    refreshSegmentCount: vi.fn(),
  },
  isContactVersionConflict: vi.fn(() => false),
}))

vi.mock('../../store/hooks', () => ({
  useAppSelector: vi.fn(),
}))

vi.mock('../../features/iam/useIam', () => ({
  useIam: vi.fn(),
}))

vi.mock('../../features/asyncJob/asyncJobApi', () => ({
  asyncJobApi: {
    get: vi.fn(),
  },
}))

describe('SegmentEditorPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useAppSelector).mockReturnValue('company-1')
    vi.mocked(useIam).mockReturnValue({
      owner: false,
      permissions: [],
      status: 'ready',
      companyId: 'company-1',
      error: null,
      can: () => true,
      canAny: () => true,
      canAll: () => true,
    })
    vi.mocked(contactsApi.customFields).mockResolvedValue([])
    vi.mocked(contactsApi.tags).mockResolvedValue([])
    vi.mocked(contactsApi.lists).mockResolvedValue([])
  })

  it('sends the loaded Segment version on update', async () => {
    vi.mocked(contactsApi.getSegment).mockResolvedValue({
      id: 'segment-1',
      name: 'Existing',
      description: null,
      filterVersion: 1,
      filter: {
        type: 'group',
        op: 'AND',
        children: [
          {
            type: 'predicate',
            field: { kind: 'BUILTIN_CONTACT', key: 'displayName' },
            operator: 'CONTAINS',
            value: 'A',
          },
        ],
      },
      version: 17,
    })
    vi.mocked(contactsApi.validateSegment).mockResolvedValue({
      valid: true,
      normalizedFilter: {
        type: 'group',
        op: 'AND',
        children: [
          {
            type: 'predicate',
            field: { kind: 'BUILTIN_CONTACT', key: 'displayName' },
            operator: 'CONTAINS',
            value: 'A',
          },
        ],
      },
      filterHash: 'hash-1',
      costClass: 'LOW',
    })
    vi.mocked(contactsApi.updateSegment).mockResolvedValue({
      id: 'segment-1',
      name: 'Existing',
      description: null,
      filterVersion: 1,
      filter: {
        type: 'group',
        op: 'AND',
        children: [
          {
            type: 'predicate',
            field: { kind: 'BUILTIN_CONTACT', key: 'displayName' },
            operator: 'CONTAINS',
            value: 'A',
          },
        ],
      },
      version: 18,
    })

    render(
      <MemoryRouter initialEntries={['/contacts/segments/segment-1']}>
        <Routes>
          <Route path="/contacts/segments/:segmentId" element={<SegmentEditorPage />} />
        </Routes>
      </MemoryRouter>,
    )

    await screen.findByDisplayValue('Existing')
    fireEvent.click(screen.getByRole('button', { name: 'Save Segment' }))

    await waitFor(() =>
      expect(contactsApi.updateSegment).toHaveBeenCalledWith(
        'company-1',
        'segment-1',
        expect.objectContaining({
          filterVersion: 1,
          version: 17,
          filter: expect.objectContaining({ type: 'group' }),
        }),
      ),
    )
  })

  it('preserves the user-edited condition after server validation normalizes the preview AST', async () => {
    vi.mocked(contactsApi.validateSegment).mockResolvedValue({
      valid: true,
      normalizedFilter: {
        type: 'group',
        op: 'OR',
        children: [
          {
            type: 'predicate',
            field: { kind: 'BUILTIN_CONTACT', key: 'organization' },
            operator: 'EQ',
            value: 'SERVER_NORMALIZED',
          },
        ],
      },
      filterHash: 'hash-normalized',
      costClass: 'LOW',
    })
    vi.mocked(contactsApi.previewSegment).mockResolvedValue({
      sample: [],
      count: 0,
      countState: 'EXACT',
      countComputedAt: '2026-10-02T00:00:00Z',
    })

    render(
      <MemoryRouter initialEntries={['/contacts/segments/new']}>
        <Routes>
          <Route path="/contacts/segments/new" element={<SegmentEditorPage />} />
        </Routes>
      </MemoryRouter>,
    )

    const value = await screen.findByLabelText('Value')
    fireEvent.change(value, { target: { value: '50' } })
    fireEvent.click(screen.getByRole('button', { name: 'Preview audience' }))

    await waitFor(() => expect(contactsApi.previewSegment).toHaveBeenCalledTimes(1))

    expect(screen.getByLabelText('Value')).toHaveValue('50')
    expect(screen.getByLabelText('Group operator')).toHaveValue('AND')
    expect(screen.getByLabelText('Operator')).toHaveValue('CONTAINS')
    expect(contactsApi.previewSegment).toHaveBeenCalledWith(
      'company-1',
      expect.objectContaining({
        op: 'OR',
        children: [
          expect.objectContaining({
            operator: 'EQ',
            value: 'SERVER_NORMALIZED',
          }),
        ],
      }),
      20,
    )
  })

  it('automatically resolves a pending audience count for a saved Segment', async () => {
    const filter = {
      type: 'group' as const,
      op: 'AND' as const,
      children: [
        {
          type: 'predicate' as const,
          field: { kind: 'IDENTITY' as const, key: 'phone' },
          operator: 'CONTAINS' as const,
          value: '25',
        },
      ],
    }

    vi.mocked(contactsApi.getSegment)
      .mockResolvedValueOnce({
        id: 'segment-1',
        name: 'Phone 25',
        description: null,
        filterVersion: 1,
        filter,
        count: null,
        countState: 'PENDING',
        version: 1,
      })
      .mockResolvedValueOnce({
        id: 'segment-1',
        name: 'Phone 25',
        description: null,
        filterVersion: 1,
        filter,
        count: 1,
        countState: 'CACHED',
        countComputedAt: '2026-10-06T13:00:00Z',
        version: 1,
      })

    vi.mocked(contactsApi.validateSegment).mockResolvedValue({
      valid: true,
      normalizedFilter: filter,
      filterHash: 'hash-1',
      costClass: 'MEDIUM',
    })
    vi.mocked(contactsApi.previewSegment).mockResolvedValue({
      sample: [
        {
          id: 'contact-25',
          displayName: 'Ritika Saxena',
          primaryEmail: 'unifydesk.test25@example.com',
          updatedAt: '2026-10-06T12:00:00Z',
        },
      ],
      count: null,
      countState: 'PENDING',
      countComputedAt: null,
    })
    vi.mocked(contactsApi.refreshSegmentCount).mockResolvedValue({
      id: 'job-1',
      jobId: 'job-1',
      status: 'QUEUED',
    })
    vi.mocked(asyncJobApi.get).mockResolvedValue({
      jobId: 'job-1',
      userId: 'user-1',
      jobType: 'CONTACT_SEGMENT_COUNT' as never,
      status: 'COMPLETED',
      totalUnits: 1,
      completedUnits: 1,
      progressPercent: 100,
      createdAt: '2026-10-06T12:00:00Z',
      updatedAt: '2026-10-06T12:00:01Z',
    })

    render(
      <MemoryRouter initialEntries={['/contacts/segments/segment-1']}>
        <Routes>
          <Route path="/contacts/segments/:segmentId" element={<SegmentEditorPage />} />
        </Routes>
      </MemoryRouter>,
    )

    await screen.findByDisplayValue('Phone 25')
    fireEvent.click(screen.getByRole('button', { name: 'Preview audience' }))

    expect(await screen.findByText('1 matching contact')).toBeInTheDocument()
    expect(contactsApi.refreshSegmentCount).toHaveBeenCalledWith('company-1', 'segment-1')
    expect(asyncJobApi.get).toHaveBeenCalledWith('job-1')
    expect(screen.queryByText(/PENDING:/i)).toBeNull()
  })

  it('renders backend count state and bounded preview sample', async () => {
    vi.mocked(contactsApi.validateSegment).mockResolvedValue({
      valid: true,
      normalizedFilter: {
        type: 'group',
        op: 'AND',
        children: [
          {
            type: 'predicate',
            field: { kind: 'BUILTIN_CONTACT', key: 'displayName' },
            operator: 'CONTAINS',
            value: 'A',
          },
        ],
      },
      filterHash: 'hash-1',
      costClass: 'LOW',
    })
    vi.mocked(contactsApi.previewSegment).mockResolvedValue({
      sample: [
        {
          id: 'contact-1',
          displayName: 'Alice',
          primaryEmail: 'alice@example.com',
          updatedAt: '2026-09-30T00:00:00Z',
        },
      ],
      count: 1234,
      countState: 'CACHED',
      countComputedAt: '2026-09-30T00:00:00Z',
    })

    render(
      <MemoryRouter initialEntries={['/contacts/segments/new']}>
        <Routes>
          <Route path="/contacts/segments/new" element={<SegmentEditorPage />} />
        </Routes>
      </MemoryRouter>,
    )

    fireEvent.change(await screen.findByLabelText('Value'), { target: { value: 'A' } })
    fireEvent.click(screen.getByRole('button', { name: 'Preview audience' }))

    expect(await screen.findByText('1,234 matching contacts')).toBeInTheDocument()
    expect(screen.getByText('Alice')).toBeInTheDocument()
    expect(contactsApi.previewSegment).toHaveBeenCalledWith(
      'company-1',
      expect.objectContaining({ type: 'group' }),
      20,
    )
  })
})
