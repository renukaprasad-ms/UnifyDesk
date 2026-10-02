import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { unsubscribeApi } from '../../features/unsubscribe/unsubscribeApi'
import EmailUnsubscribePage from './EmailUnsubscribePage'

vi.mock('../../features/unsubscribe/unsubscribeApi', () => ({
  unsubscribeApi: {
    unsubscribe: vi.fn(),
  },
}))

const renderPage = (entry = '/unsubscribe?token=opaque-token-value') =>
  render(
    <MemoryRouter initialEntries={[entry]}>
      <EmailUnsubscribePage />
    </MemoryRouter>,
  )

describe('EmailUnsubscribePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()
    window.sessionStorage.clear()
    vi.mocked(unsubscribeApi.unsubscribe).mockResolvedValue(undefined)
  })

  it('posts only the opaque token and shows the success-equivalent result', async () => {
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: 'Unsubscribe' }))

    await waitFor(() =>
      expect(unsubscribeApi.unsubscribe).toHaveBeenCalledWith('opaque-token-value'),
    )
    expect(
      await screen.findByText('You have been unsubscribed from marketing emails.'),
    ).toBeInTheDocument()
  })

  it('keeps replay/success-equivalent responses successful', async () => {
    vi.mocked(unsubscribeApi.unsubscribe).mockResolvedValueOnce(undefined)
    renderPage('/unsubscribe?token=replayed-opaque-token')

    fireEvent.click(screen.getByRole('button', { name: 'Unsubscribe' }))

    expect(
      await screen.findByText('You have been unsubscribed from marketing emails.'),
    ).toBeInTheDocument()
  })

  it('shows a generic safe error for invalid, expired, or tampered actions', async () => {
    vi.mocked(unsubscribeApi.unsubscribe).mockRejectedValueOnce(
      new Error('companyId=company-secret contactId=contact-secret identityId=identity-secret'),
    )
    renderPage('/unsubscribe?token=tampered-token')

    fireEvent.click(screen.getByRole('button', { name: 'Unsubscribe' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The unsubscribe link may be invalid or expired.',
    )
    expect(screen.queryByText(/company-secret|contact-secret|identity-secret/i)).toBeNull()
  })

  it('does not expose identifiers when the token is missing', () => {
    renderPage('/unsubscribe')

    expect(screen.getByRole('alert')).toHaveTextContent('This unsubscribe link cannot be used')
    expect(unsubscribeApi.unsubscribe).not.toHaveBeenCalled()
  })

  it('never writes the token to local or session storage', async () => {
    renderPage('/unsubscribe?token=storage-sensitive-token')

    fireEvent.click(screen.getByRole('button', { name: 'Unsubscribe' }))
    await screen.findByText('You have been unsubscribed from marketing emails.')

    expect(JSON.stringify({ ...window.localStorage })).not.toContain('storage-sensitive-token')
    expect(JSON.stringify({ ...window.sessionStorage })).not.toContain('storage-sensitive-token')
  })

  it('remains usable at a mobile viewport width', () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 320 })
    renderPage('/unsubscribe?token=mobile-token')

    const button = screen.getByRole('button', { name: 'Unsubscribe' })
    expect(button).toBeVisible()
    expect(button).toBeEnabled()
    expect(button).toHaveClass('w-full')
  })
})
