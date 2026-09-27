import { render, screen } from '@testing-library/react'
import { Provider } from 'react-redux'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { store } from '../../store/store'
import { notificationApi } from './notificationApi'
import {
  getBrowserNotificationState,
} from './notificationBrowser'
import { notificationPreferencesFixture } from './notificationFixtures'
import NotificationPreferencesPanel from './NotificationPreferencesPanel'

vi.mock('./notificationApi', () => ({
  notificationApi: {
    preferences: vi.fn(),
    pushSubscriptions: vi.fn(),
    updatePreferences: vi.fn(),
    revokePushSubscription: vi.fn(),
  },
}))

vi.mock('./notificationBrowser', () => ({
  enableBrowserNotifications: vi.fn(),
  getBrowserNotificationState: vi.fn(),
  revokeCurrentBrowserSubscriptionBestEffort: vi.fn(),
  revokeServerSubscription: vi.fn(),
}))

describe('NotificationPreferencesPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(notificationApi.preferences).mockResolvedValue(
      notificationPreferencesFixture,
    )
    vi.mocked(notificationApi.pushSubscriptions).mockResolvedValue([])
    vi.mocked(getBrowserNotificationState).mockResolvedValue({
      supported: true,
      permission: 'default',
      locallySubscribed: false,
    })
  })

  it('renders server-defined mandatory email controls as locked', async () => {
    render(
      <Provider store={store}>
        <NotificationPreferencesPanel />
      </Provider>,
    )

    expect(await screen.findByText('Security alerts')).toBeTruthy()
    const emailSelects = screen.getAllByRole('combobox')
    expect(emailSelects.some((select) => select.hasAttribute('disabled'))).toBe(true)
    expect(screen.getByText('Mandatory email')).toBeTruthy()
  })

  it('educates before prompting and does not enable browser push on mount', async () => {
    render(
      <Provider store={store}>
        <NotificationPreferencesPanel />
      </Provider>,
    )

    expect(await screen.findByText(/only ask for permission after you choose Enable/i)).toBeTruthy()
    expect(getBrowserNotificationState).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: 'Enable browser notifications' })).toBeTruthy()
  })
})
