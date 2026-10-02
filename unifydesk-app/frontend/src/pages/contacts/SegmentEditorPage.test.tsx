import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { contactsApi } from '../../features/contacts/contactsApi'
import { useIam } from '../../features/iam/useIam'
import { useAppSelector } from '../../store/hooks'
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
  },
  isContactVersionConflict: vi.fn(() => false),
}))

vi.mock('../../store/hooks', () => ({
  useAppSelector: vi.fn(),
}))

vi.mock('../../features/iam/useIam', () => ({
  useIam: vi.fn(),
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
    fireEvent.click(screen.getByRole('button', { name: 'Validate & preview' }))

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
    fireEvent.click(screen.getByRole('button', { name: 'Validate & preview' }))

    expect(await screen.findByText('CACHED: 1,234')).toBeInTheDocument()
    expect(screen.getByText('Alice')).toBeInTheDocument()
    expect(contactsApi.previewSegment).toHaveBeenCalledWith(
      'company-1',
      expect.objectContaining({ type: 'group' }),
      20,
    )
  })
})
