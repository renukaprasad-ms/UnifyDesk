import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { contactsApi } from '../../features/contacts/contactsApi'
import { useIam } from '../../features/iam/useIam'
import { useAppSelector } from '../../store/hooks'
import ListDetailPage from './ListDetailPage'

vi.mock('../../features/contacts/contactsApi', () => ({
  contactsApi: {
    getList: vi.fn(),
    list: vi.fn(),
    listMembers: vi.fn(),
    updateList: vi.fn(),
    archiveList: vi.fn(),
    addListMembers: vi.fn(),
    removeListMember: vi.fn(),
  },
}))

vi.mock('../../features/iam/useIam', () => ({ useIam: vi.fn() }))
vi.mock('../../store/hooks', () => ({ useAppSelector: vi.fn() }))

const member = {
  id: 'contact-1',
  displayName: 'Alice',
  primaryEmail: 'alice@example.com',
  organization: 'Acme',
  updatedAt: '2026-09-30T00:00:00Z',
}

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/contacts/lists/list-1']}>
      <Routes>
        <Route path="/contacts/lists/:listId" element={<ListDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )

describe('ListDetailPage', () => {
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
    vi.mocked(contactsApi.getList).mockResolvedValue({
      id: 'list-1',
      name: 'Customers',
      memberCount: 1,
    })
    vi.mocked(contactsApi.listMembers).mockResolvedValue({
      items: [member],
      nextCursor: null,
      hasMore: false,
    })
  })

  it('adds only a bounded small set through the synchronous membership endpoint', async () => {
    vi.mocked(contactsApi.addListMembers).mockResolvedValue({ added: 2 })
    renderPage()

    vi.mocked(contactsApi.list).mockResolvedValue({
      items: [
        { ...member, id: 'contact-2', displayName: 'Bob' },
        { ...member, id: 'contact-3', displayName: 'Carol' },
      ],
      nextCursor: null,
      hasMore: false,
    })
    const input = await screen.findByRole('searchbox')
    fireEvent.change(input, { target: { value: 'contact' } })
    fireEvent.click(await screen.findByRole('checkbox', { name: /Bob/ }))
    fireEvent.click(screen.getByRole('checkbox', { name: /Carol/ }))
    fireEvent.click(screen.getByRole('button', { name: /Add selected/ }))

    await waitFor(() =>
      expect(contactsApi.addListMembers).toHaveBeenCalledWith('company-1', 'list-1', [
        'contact-2',
        'contact-3',
      ]),
    )
  })

  it('removes one loaded member without fetching the entire List', async () => {
    vi.mocked(contactsApi.removeListMember).mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: 'Remove' }))
    fireEvent.click(screen.getByRole('button', { name: 'Yes, remove' }))

    await waitFor(() =>
      expect(contactsApi.removeListMember).toHaveBeenCalledWith('company-1', 'list-1', 'contact-1'),
    )
  })
})
