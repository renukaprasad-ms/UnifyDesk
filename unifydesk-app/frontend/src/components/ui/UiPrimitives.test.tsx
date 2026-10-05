import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import AsyncButton from './AsyncButton'
import ConfirmDialog from './ConfirmDialog'
import StatusBadge from './StatusBadge'
import ProgressBar from './ProgressBar'
import FormField from './FormField'

describe('UI interaction primitives', () => {
  it('requires an explicit confirmation choice', () => {
    const onCancel = vi.fn()
    const onConfirm = vi.fn()

    render(
      <ConfirmDialog
        open
        title="Delete this item?"
        description="This action cannot be undone."
        confirmLabel="Yes, delete"
        cancelLabel="No, keep it"
        tone="danger"
        kind="delete"
        onCancel={onCancel}
        onConfirm={onConfirm}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'No, keep it' }))
    expect(onCancel).toHaveBeenCalledTimes(1)
    expect(onConfirm).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: 'Yes, delete' }))
    expect(onConfirm).toHaveBeenCalledTimes(1)
  })

  it('disables the async action while work is running', () => {
    render(
      <AsyncButton loading loadingLabel="Saving…">
        Save
      </AsyncButton>,
    )

    expect(screen.getByRole('button')).toBeDisabled()
    expect(screen.getByText('Saving…')).toBeInTheDocument()
  })

  it.each([
    ['COMPLETED', 'bg-emerald-50'],
    ['RUNNING', 'bg-blue-50'],
    ['PARTIALLY_FAILED', 'bg-orange-50'],
    ['FAILED', 'bg-rose-50'],
    ['CANCELLED', 'bg-slate-100'],
    ['SENT', 'bg-sky-50'],
    ['READ', 'bg-violet-50'],
    ['UNKNOWN', 'bg-amber-50'],
  ])('uses semantic color for %s', (status, expectedClass) => {
    render(<StatusBadge status={status}>{status}</StatusBadge>)
    expect(screen.getByText(status)).toHaveClass(expectedClass)
  })

  it('exposes progress semantics and clamps the rendered value', () => {
    render(<ProgressBar value={145} label="Upload" showValue />)
    const progress = screen.getByRole('progressbar', { name: 'Upload' })
    expect(progress).toHaveAttribute('aria-valuenow', '100')
    expect(screen.getByText('100%')).toBeInTheDocument()
  })

  it('shows field errors with alert semantics', () => {
    render(
      <FormField label="Company name" error="Company name is required">
        <input aria-label="Company name input" />
      </FormField>,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('Company name is required')
  })
})
