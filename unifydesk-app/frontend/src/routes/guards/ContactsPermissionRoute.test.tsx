import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import PermissionRoute from './PermissionRoute'

vi.mock('../../features/iam/useIam', () => ({
  useIam: vi.fn(),
}))

const renderContactsRoute = () =>
  render(
    <MemoryRouter initialEntries={['/contacts']}>
      <Routes>
        <Route path="/workspace" element={<div>Workspace</div>} />
        <Route element={<PermissionRoute anyOf={[PERMISSIONS.CONTACT_READ]} />}>
          <Route path="/contacts" element={<div>Contacts route</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )

describe('Contacts permission route', () => {
  beforeEach(() => vi.clearAllMocks())

  it('allows CONTACT_READ', () => {
    vi.mocked(useIam).mockReturnValue({
      owner: false,
      permissions: [PERMISSIONS.CONTACT_READ],
      status: 'ready',
      companyId: 'company-1',
      error: null,
      can: (permission) => permission === PERMISSIONS.CONTACT_READ,
      canAny: (permissions) => permissions.includes(PERMISSIONS.CONTACT_READ),
      canAll: (permissions) =>
        permissions.every((permission) => permission === PERMISSIONS.CONTACT_READ),
    })

    renderContactsRoute()
    expect(screen.getByText('Contacts route')).toBeInTheDocument()
  })

  it('redirects a user without CONTACT_READ', () => {
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

    renderContactsRoute()
    expect(screen.getByText('Workspace')).toBeInTheDocument()
    expect(screen.queryByText('Contacts route')).toBeNull()
  })
})
