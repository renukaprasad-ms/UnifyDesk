import { StrictMode } from 'react'
import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { SnackbarProvider } from './SnackbarProviderComponent'
import { pendingExternalSnackbars, showGlobalSnackbar, useSnackbar } from './SnackbarProvider'

function Controls() {
  const snackbar = useSnackbar()
  return (
    <>
      <button onClick={() => snackbar.success('Saved')}>Success</button>
      <button onClick={() => snackbar.error('Failed')}>Error</button>
      <button onClick={() => snackbar.warning('Check details')}>Warning</button>
      <button onClick={() => snackbar.info('Updated')}>Info</button>
      <button onClick={snackbar.clear}>Clear</button>
    </>
  )
}

beforeEach(() => {
  pendingExternalSnackbars.splice(0)
  vi.useFakeTimers()
})
afterEach(() => {
  vi.useRealTimers()
  pendingExternalSnackbars.splice(0)
})

describe('SnackbarProvider', () => {
  it('renders each severity, deduplicates messages, and stays above modal layers', () => {
    render(
      <SnackbarProvider>
        <Controls />
      </SnackbarProvider>,
    )
    fireEvent.click(screen.getByRole('button', { name: 'Error' }))
    fireEvent.click(screen.getByRole('button', { name: 'Error' }))
    expect(screen.getAllByText('Failed')).toHaveLength(1)
    expect(screen.getByRole('alert')).toHaveTextContent('Failed')
    fireEvent.click(screen.getByRole('button', { name: 'Warning' }))
    fireEvent.click(screen.getByRole('button', { name: 'Success' }))
    expect(screen.getByText('Action required')).toBeInTheDocument()
    expect(screen.getByText('Saved')).toBeInTheDocument()
    const region = screen.getByLabelText('Notifications')
    expect(region.parentElement).toBe(document.body)
    expect(region.className).toContain('z-[200]')
    fireEvent.click(screen.getByRole('button', { name: 'Info' }))
    expect(screen.queryByText('Failed')).not.toBeInTheDocument()
    expect(screen.getByText('Updated')).toBeInTheDocument()
  })

  it('dismisses messages and clears their timers', () => {
    render(
      <SnackbarProvider>
        <Controls />
      </SnackbarProvider>,
    )
    fireEvent.click(screen.getByRole('button', { name: 'Success' }))
    act(() => vi.advanceTimersByTime(4000))
    expect(screen.queryByText('Saved')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Error' }))
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss notification' }))
    expect(screen.queryByText('Failed')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Warning' }))
    fireEvent.click(screen.getByRole('button', { name: 'Clear' }))
    expect(vi.getTimerCount()).toBe(0)
  })

  it('drains queued messages once and retains expiry under StrictMode', () => {
    showGlobalSnackbar('Queued error', { variant: 'error' })
    showGlobalSnackbar('Queued error', { variant: 'error' })
    expect(pendingExternalSnackbars).toHaveLength(1)
    render(
      <StrictMode>
        <SnackbarProvider>
          <Controls />
        </SnackbarProvider>
      </StrictMode>,
    )
    expect(screen.getAllByText('Queued error')).toHaveLength(1)
    act(() => vi.advanceTimersByTime(6500))
    expect(screen.queryByText('Queued error')).not.toBeInTheDocument()
  })
})
