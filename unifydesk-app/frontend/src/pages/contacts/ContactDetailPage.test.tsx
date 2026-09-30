import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { contactsApi } from '../../features/contacts/contactsApi'
import { useIam } from '../../features/iam/useIam'
import { useAppSelector } from '../../store/hooks'
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
}))

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

const renderPage = (entry = '/contacts/contact-1') =>
  render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route path="/contacts/target" element={<div>Target contact</div>} />
        <Route path="/contacts/:contactId" element={<ContactDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )

describe('ContactDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useAppSelector).mockReturnValue('company-1')
    vi.mocked(useIam).mockReturnValue(iam)
    vi.mocked(contactsApi.tags).mockResolvedValue([])
    vi.mocked(contactsApi.customFields).mockResolvedValue([])
    vi.mocked(contactsApi.activity).mockResolvedValue({
      items: [],
      nextCursor: null,
      hasMore: false,
    })
    vi.mocked(contactsApi.preferences).mockResolvedValue([])
    vi.mocked(contactsApi.suppressions).mockResolvedValue([])
  })

  it('shows a safe not-found state without exposing foreign-resource details', async () => {
    vi.mocked(contactsApi.get).mockRejectedValue(new Error('foreign tenant id exists'))

    renderPage()

    expect(await screen.findByText('Contact unavailable')).toBeInTheDocument()
    expect(screen.getByText('Contact not found.')).toBeInTheDocument()
    expect(screen.queryByText(/foreign tenant/i)).toBeNull()
  })

  it('redirects merged contacts to the safe target', async () => {
    vi.mocked(contactsApi.get).mockResolvedValue({
      id: 'source',
      displayName: 'Merged source',
      updatedAt: '2026-09-30T00:00:00Z',
      version: 1,
      identities: [],
      status: 'MERGED',
      mergedIntoContactId: 'target',
    })

    renderPage('/contacts/source')

    expect(await screen.findByText('Target contact')).toBeInTheDocument()
  })
})
