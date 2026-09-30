import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import ContactsNav from './ContactsNav'

describe('ContactsNav', () => {
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
})
