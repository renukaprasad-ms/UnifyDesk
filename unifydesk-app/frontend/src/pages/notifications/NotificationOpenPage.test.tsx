import { render, screen } from '@testing-library/react'
import { Provider } from 'react-redux'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { companyNotification } from '../../features/notifications/notificationFixtures'
import { notificationApi } from '../../features/notifications/notificationApi'
import { workspaceApi } from '../../features/workspace/workspaceApi'
import { store } from '../../store/store'
import NotificationOpenPage from './NotificationOpenPage'

vi.mock('../../features/notifications/notificationApi', () => ({
  notificationApi: {
    get: vi.fn(),
    setReadState: vi.fn(),
  },
}))

vi.mock('../../features/workspace/workspaceApi', () => ({
  workspaceApi: {
    list: vi.fn(),
  },
}))

const company = {
  companyId: '922337203685470100',
  name: 'Acme',
  slug: 'acme',
  status: 'ACTIVE' as const,
  role: 'OWNER' as const,
  brandColor: '#2563EB',
}

describe('NotificationOpenPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(workspaceApi.list).mockResolvedValue([company])
    vi.mocked(notificationApi.get).mockResolvedValue(companyNotification)
    vi.mocked(notificationApi.setReadState).mockResolvedValue({
      notification: { ...companyNotification, unread: false, readAt: '2026-09-27T07:00:00Z' },
      unreadCount: { account: 0, company: 0, total: 0 },
    })
  })

  it('verifies workspace access, marks read, then follows the server route', async () => {
    render(
      <Provider store={store}>
        <MemoryRouter
          initialEntries={['/notification-open?n=922337203685470001&c=922337203685470100']}
        >
          <Routes>
            <Route path="/notification-open" element={<NotificationOpenPage />} />
            <Route path="/inbox/email" element={<div>Inbox target</div>} />
          </Routes>
        </MemoryRouter>
      </Provider>,
    )

    expect(await screen.findByText('Inbox target')).toBeTruthy()
    expect(notificationApi.get).toHaveBeenCalledWith('922337203685470001', '922337203685470100')
    expect(notificationApi.setReadState).toHaveBeenCalledWith(
      companyNotification.id,
      '922337203685470100',
      true,
    )
  })

  it('fails closed when the requested company is no longer accessible', async () => {
    vi.mocked(workspaceApi.list).mockResolvedValue([])

    render(
      <Provider store={store}>
        <MemoryRouter
          initialEntries={['/notification-open?n=922337203685470001&c=922337203685470100']}
        >
          <Routes>
            <Route path="/notification-open" element={<NotificationOpenPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>,
    )

    expect(await screen.findByText('This notification is no longer available')).toBeTruthy()
    expect(notificationApi.get).not.toHaveBeenCalled()
  })
})
