import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { contactsApi } from '../../features/contacts/contactsApi'
import { useContacts } from '../../features/contacts/useContacts'
import { useIam } from '../../features/iam/useIam'
import { useAppSelector } from '../../store/hooks'
import ContactsPage from './ContactsPage'

vi.mock('../../features/contacts/contactsApi', () => ({
  contactsApi: {
    tags: vi.fn(),
    lists: vi.fn(),
    create: vi.fn(),
    createExport: vi.fn(),
    getExport: vi.fn(),
    exportDownload: vi.fn(),
  },
}))

vi.mock('../../features/contacts/useContacts', () => ({ useContacts: vi.fn() }))
vi.mock('../../features/iam/useIam', () => ({ useIam: vi.fn() }))
vi.mock('../../store/hooks', () => ({ useAppSelector: vi.fn() }))

const iam = {
  owner: false,
  permissions: [],
  status: 'ready' as const,
  companyId: 'company-1',
  error: null,
  can: () => true,
  canAny: () => true,
  canAll: () => true,
}

const contactsState = {
  items: [],
  hasMore: false,
  loading: false,
  loadingMore: false,
  error: '',
  loadMore: vi.fn(),
  refresh: vi.fn(),
}

function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{location.pathname + location.search}</output>
}

const renderPage = (entry = '/contacts') =>
  render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route
          path="/contacts"
          element={
            <>
              <ContactsPage />
              <LocationProbe />
            </>
          }
        />
      </Routes>
    </MemoryRouter>,
  )

describe('ContactsPage Round-2 remediation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useAppSelector).mockReturnValue('company-1')
    vi.mocked(useIam).mockReturnValue(iam)
    vi.mocked(useContacts).mockReturnValue(contactsState)
    vi.mocked(contactsApi.tags).mockResolvedValue([])
    vi.mocked(contactsApi.lists).mockResolvedValue([])
    vi.mocked(contactsApi.createExport).mockResolvedValue({
      id: 'export-1',
      status: 'COMPLETED',
      rowCount: 0,
    })
  })

  it('creates the normal Contacts export with only backend-supported fields', async () => {
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: 'Export contacts' }))

    await waitFor(() => expect(contactsApi.createExport).toHaveBeenCalledTimes(1))
    const [companyId, request] = vi.mocked(contactsApi.createExport).mock.calls[0]

    expect(companyId).toBe('company-1')
    expect(request).toEqual({
      sourceType: 'CONTACTS',
      sourceId: null,
      fields: ['displayName', 'organization'],
      idempotencyKey: expect.any(String),
    })
    expect(request.fields).not.toContain('primaryEmail')
    expect(request.fields).not.toContain('primaryPhone')
  })

  it('does not offer DELETED while preserving supported Contact statuses', () => {
    renderPage()

    const status = screen.getByLabelText('Status')
    expect(within(status).getByRole('option', { name: 'Active/default' })).toBeInTheDocument()
    expect(within(status).getByRole('option', { name: 'Active' })).toBeInTheDocument()
    expect(within(status).getByRole('option', { name: 'Merged' })).toBeInTheDocument()
    expect(within(status).queryByRole('option', { name: 'Deleted' })).toBeNull()
  })

  it('removes an unsupported stale status and cursor before issuing Contact queries', async () => {
    renderPage('/contacts?status=DELETED&cursor=stale')

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/contacts'))

    for (const [, , query] of vi.mocked(useContacts).mock.calls) {
      expect(query.status).not.toBe('DELETED')
    }
    expect(vi.mocked(useContacts).mock.calls.at(-1)?.[2].status).toBeUndefined()
  })

  it('preserves the supported MERGED status from URL state', () => {
    renderPage('/contacts?status=MERGED')

    expect(vi.mocked(useContacts)).toHaveBeenCalledWith(
      'company-1',
      'company-1',
      expect.objectContaining({ status: 'MERGED' }),
    )
  })
})
