import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import ContactFormDialog from './ContactFormDialog'

describe('ContactFormDialog', () => {
  it('shows validation and does not submit an invalid contact', () => {
    const onSubmit = vi.fn()
    render(
      <ContactFormDialog
        open
        title="Create contact"
        saving={false}
        onClose={() => undefined}
        onSubmit={onSubmit}
      />,
    )
    fireEvent.click(screen.getByRole('button', { name: 'Save contact' }))
    expect(screen.getByText('Display name is required.')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('does not validate hidden identity fields during profile-only editing', () => {
    const onSubmit = vi.fn()
    render(
      <ContactFormDialog
        open
        title="Edit contact"
        saving={false}
        showIdentityFields={false}
        initial={{
          displayName: 'Alice',
          firstName: '',
          lastName: '',
          organization: '',
          notes: '',
          email: 'legacy-invalid-email',
          phone: '1',
        }}
        onClose={() => undefined}
        onSubmit={onSubmit}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Save contact' }))
    expect(onSubmit).toHaveBeenCalled()
    expect(screen.queryByLabelText('Primary email')).toBeNull()
  })

  it('renders a mobile-safe dialog with explicit labels', () => {
    render(
      <ContactFormDialog
        open
        title="Create contact"
        saving={false}
        onClose={() => undefined}
        onSubmit={() => undefined}
      />,
    )
    expect(screen.getByRole('dialog', { name: 'Create contact' })).toBeInTheDocument()
    expect(screen.getByLabelText('Primary email')).toBeInTheDocument()
    expect(screen.getByLabelText('Primary phone')).toBeInTheDocument()
  })
})
