import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import IdentityManager from './IdentityManager'

describe('IdentityManager', () => {
  it('supports primary and remove actions with explicit labels', async () => {
    const onPrimary = vi.fn().mockResolvedValue(undefined)
    const onRemove = vi.fn().mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    render(
      <IdentityManager
        identities={[
          { id: 'email-1', type: 'EMAIL', value: 'a@example.com', primary: true },
          { id: 'email-2', type: 'EMAIL', value: 'b@example.com', primary: false },
        ]}
        canModify
        busy={false}
        onAdd={vi.fn()}
        onPrimary={onPrimary}
        onRemove={onRemove}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Make primary' }))
    await waitFor(() => expect(onPrimary).toHaveBeenCalledWith('email-2'))

    const removeButtons = screen.getAllByRole('button', { name: 'Remove' })
    fireEvent.click(removeButtons[0])
    fireEvent.click(screen.getByRole('button', { name: 'Yes, remove identity' }))
    await waitFor(() => expect(onRemove).toHaveBeenCalledWith('email-1'))
  })

  it('requires a namespace for external identities', async () => {
    const onAdd = vi.fn().mockResolvedValue(undefined)
    render(
      <IdentityManager
        identities={[]}
        canModify
        busy={false}
        onAdd={onAdd}
        onPrimary={vi.fn()}
        onRemove={vi.fn()}
      />,
    )

    fireEvent.change(screen.getByLabelText('Identity type'), {
      target: { value: 'EXTERNAL' },
    })
    fireEvent.change(screen.getByLabelText('Identity value'), {
      target: { value: 'crm-123' },
    })

    expect(screen.getByRole('button', { name: 'Add identity' })).toBeDisabled()

    fireEvent.change(screen.getByLabelText('External identity namespace'), {
      target: { value: 'crm' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Add identity' }))

    await waitFor(() => expect(onAdd).toHaveBeenCalledWith('EXTERNAL', 'crm-123', 'crm'))
  })

  it('hides mutation controls without modify permission', () => {
    render(
      <IdentityManager
        identities={[{ id: 'phone-1', type: 'PHONE', value: '+919999999999', primary: false }]}
        canModify={false}
        busy={false}
        onAdd={vi.fn()}
        onPrimary={vi.fn()}
        onRemove={vi.fn()}
      />,
    )

    expect(screen.queryByRole('button', { name: 'Make primary' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Remove' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Add identity' })).toBeNull()
  })
})
