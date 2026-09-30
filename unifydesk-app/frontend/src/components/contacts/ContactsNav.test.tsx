import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useIam } from '../../features/iam/useIam'
import ContactsNav from './ContactsNav'

vi.mock('../../features/iam/useIam', () => ({
  useIam: vi.fn(),
}))

describe('ContactsNav', () => {
  beforeEach(() => {
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
  })

  it('keeps all first-class Contacts destinations reachable in a mobile-scrollable nav', () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/contacts']}>
        <ContactsNav />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'Contacts' })).toHaveAttribute('href', '/contacts')
    expect(screen.getByRole('link', { name: 'Lists' })).toHaveAttribute('href', '/contacts/lists')
    expect(screen.getByRole('link', { name: 'Segments' })).toHaveAttribute('href', '/contacts/segments')
    expect(screen.getByRole('link', { name: 'Imports' })).toHaveAttribute('href', '/contacts/imports')
    expect(container.querySelector('nav')).toHaveClass('overflow-x-auto')
  })

  it('hides Imports when CONTACT_IMPORT is unavailable', () => {
    vi.mocked(useIam).mockReturnValue({
      owner: false,
      permissions: [],
      status: 'ready',
      companyId: 'company-1',
      error: null,
      can: () => false,
      canAny: () => false,
      canAll: () => false,
    })

    render(
      <MemoryRouter initialEntries={['/contacts']}>
        <ContactsNav />
      </MemoryRouter>,
    )

    expect(screen.queryByRole('link', { name: 'Imports' })).toBeNull()
  })
})
