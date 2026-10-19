import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SnackbarProvider } from '../../components/feedback/SnackbarProviderComponent'
import { pendingExternalSnackbars } from '../../components/feedback/SnackbarProvider'
import { authApi } from '../../features/auth/authApi'
import Login from './Login'

vi.mock('../../store/hooks', () => ({ useAppDispatch: () => vi.fn() }))
vi.mock('../../features/auth/authApi', () => ({ authApi: { login: vi.fn(), verifyTwoFactor: vi.fn() } }))
beforeEach(() => { pendingExternalSnackbars.splice(0); vi.clearAllMocks() })

describe('Login snackbar feedback', () => {
  it('announces the password-reset result received after navigation', async () => {
    render(<MemoryRouter initialEntries={[{ pathname: '/login', state: { passwordReset: true } }]}>
      <SnackbarProvider><Login /></SnackbarProvider>
    </MemoryRouter>)
    expect(await screen.findByRole('status')).toHaveTextContent('Password reset successfully')
    expect(screen.getAllByText(/Password reset successfully/)).toHaveLength(1)
  })

  it('shows a failed sign-in through the snackbar', async () => {
    vi.mocked(authApi.login).mockRejectedValueOnce(new Error('Unavailable'))
    render(<MemoryRouter><SnackbarProvider><Login /></SnackbarProvider></MemoryRouter>)
    fireEvent.change(screen.getByPlaceholderText('you@example.com or username'), { target: { value: 'a@example.com' } })
    fireEvent.change(screen.getByPlaceholderText('Enter password'), { target: { value: 'password' } })
    fireEvent.submit(screen.getByRole('button', { name: 'Login' }).closest('form')!)
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to login')
  })
})
