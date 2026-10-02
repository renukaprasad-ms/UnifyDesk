import { render, screen, waitFor } from '@testing-library/react'
import { Provider } from 'react-redux'
import { MemoryRouter, useLocation } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { authApi } from './features/auth/authApi'
import { unsubscribeApi } from './features/unsubscribe/unsubscribeApi'
import { workspaceApi } from './features/workspace/workspaceApi'
import { sessionCleared, setAuthUser } from './store/auth/authSlice'
import { store } from './store/store'

vi.mock('./features/unsubscribe/unsubscribeApi', () => ({
  unsubscribeApi: {
    unsubscribe: vi.fn(),
  },
}))

vi.mock('./features/auth/authApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./features/auth/authApi')>()
  return {
    ...actual,
    authApi: {
      ...actual.authApi,
      refresh: vi.fn(),
    },
  }
})

vi.mock('./features/workspace/workspaceApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./features/workspace/workspaceApi')>()
  return {
    ...actual,
    workspaceApi: {
      ...actual.workspaceApi,
      list: vi.fn(),
    },
  }
})

function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{location.pathname}</output>
}

const renderApp = (entry: string) =>
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={[entry]}>
        <App />
        <LocationProbe />
      </MemoryRouter>
    </Provider>,
  )

describe('public unsubscribe routing', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    store.dispatch(sessionCleared())
    vi.mocked(unsubscribeApi.unsubscribe).mockResolvedValue(undefined)
    vi.mocked(authApi.refresh).mockRejectedValue(new Error('no session'))
    vi.mocked(workspaceApi.list).mockResolvedValue([])
  })

  it('renders /unsubscribe logged out without redirecting to login or bootstrapping auth', () => {
    renderApp('/unsubscribe?token=public-token')

    expect(screen.getByRole('heading', { name: 'Unsubscribe from marketing emails' })).toBeVisible()
    expect(screen.getByTestId('location')).toHaveTextContent('/unsubscribe')
    expect(authApi.refresh).not.toHaveBeenCalled()
    expect(workspaceApi.list).not.toHaveBeenCalled()
  })

  it('keeps /unsubscribe public while the visitor is already authenticated', () => {
    store.dispatch(
      setAuthUser({
        userId: 'user-1',
        name: 'Authenticated User',
        username: 'authenticated',
        email: 'authenticated@example.com',
        verified: true,
        hasCompanies: true,
        companyCount: 1,
      }),
    )

    renderApp('/unsubscribe?token=authenticated-token')

    expect(screen.getByRole('heading', { name: 'Unsubscribe from marketing emails' })).toBeVisible()
    expect(screen.getByTestId('location')).toHaveTextContent('/unsubscribe')
    expect(authApi.refresh).not.toHaveBeenCalled()
  })

  it('preserves authentication protection on existing protected routes', async () => {
    store.dispatch(sessionCleared())

    renderApp('/workspace')

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login'))
    expect(screen.getByRole('heading', { name: 'Login' })).toBeVisible()
  })
})
