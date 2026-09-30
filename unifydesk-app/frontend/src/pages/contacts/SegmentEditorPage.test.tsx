import { fireEvent, render, screen } from '@testing-library/react'
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
  },
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
