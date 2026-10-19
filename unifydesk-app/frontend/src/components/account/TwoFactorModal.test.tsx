import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import TwoFactorModal from './TwoFactorModal'
import { authApi } from '../../features/auth/authApi'

vi.mock('../../features/auth/authApi', () => ({
  authApi: {
    beginTwoFactorSettingsChange: vi.fn(),
    completeTwoFactorSettingsChange: vi.fn(),
  },
}))

describe('TwoFactorModal', () => {
  it('renders password step initially and sends verification code', async () => {
    vi.mocked(authApi.beginTwoFactorSettingsChange).mockResolvedValueOnce({
      challengeToken: 'test-token',
      expiresInSeconds: 300,
    })

    const handleSuccess = vi.fn()
    const handleClose = vi.fn()

    render(
      <TwoFactorModal
        open={true}
        targetEnabled={true}
        onClose={handleClose}
        onSuccess={handleSuccess}
      />,
    )

    expect(screen.getByText('Enable 2FA')).toBeDefined()
    expect(screen.getByPlaceholderText('Enter current password')).toBeDefined()

    const passwordInput = screen.getByPlaceholderText('Enter current password')
    fireEvent.change(passwordInput, { target: { value: 'password123' } })

    const submitBtn = screen.getByRole('button', { name: /send verification code/i })
    fireEvent.click(submitBtn)

    await waitFor(() => {
      expect(authApi.beginTwoFactorSettingsChange).toHaveBeenCalledWith('password123', true)
    })

    // Now in OTP step with 6-box inputs
    await waitFor(() => {
      expect(screen.getByText(/enter 6-digit code/i)).toBeDefined()
    })
    const otpBoxes = screen.getAllByRole('textbox')
    expect(otpBoxes).toHaveLength(6)
  })
})
