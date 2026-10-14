import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from '../../lib/api/apiClient'
import { dashboardApi, loadDashboardSections, type DashboardResponse } from './dashboardApi'

vi.mock('../../lib/api/apiClient', () => ({ default: { get: vi.fn() } }))

describe('dashboard response contract', () => {
  beforeEach(() => vi.clearAllMocks())
  afterEach(() => vi.restoreAllMocks())

  const emptyDashboard: DashboardResponse = {
    contacts: { summary: null, recent: [], lists: [], segments: [] },
    broadcasts: { total: 0, recent: [] },
    inbox: null,
    connections: [],
    managedEmail: { overview: null, usage: null, deliverability: null },
    managedSms: { status: null, usage: null, health: null },
    ads: { connections: [], accounts: [], campaigns: [], campaignTotal: 0 },
    unavailableSections: [],
  }

  it('delivers core and ads before a slow channel response arrives', async () => {
    let resolveChannels!: (data: DashboardResponse) => void
    const channels = new Promise<DashboardResponse>((resolve) => {
      resolveChannels = resolve
    })
    vi.spyOn(dashboardApi, 'get').mockImplementation(async (_companyId, section) =>
      section === 'channels' ? channels : emptyDashboard,
    )
    const onData = vi.fn()
    const onError = vi.fn()
    const onSettled = vi.fn()
    let finished = false
    const loading = loadDashboardSections('123', onData, onError, onSettled).then(() => {
      finished = true
    })
    await vi.waitFor(() => expect(onData).toHaveBeenCalledTimes(2))
    expect(onData).toHaveBeenCalledWith('core', emptyDashboard)
    expect(onData).toHaveBeenCalledWith('ads', emptyDashboard)
    expect(onSettled).not.toHaveBeenCalledWith('channels')
    expect(finished).toBe(false)
    resolveChannels(emptyDashboard)
    await loading
    expect(onData).toHaveBeenCalledWith('channels', emptyDashboard)
    expect(onError).not.toHaveBeenCalled()
  })

  it('keeps successful groups visible when another group fails', async () => {
    vi.spyOn(dashboardApi, 'get').mockImplementation(async (_companyId, section) => {
      if (section === 'channels') throw new Error('Unavailable')
      return emptyDashboard
    })
    const onData = vi.fn()
    const onError = vi.fn()
    const onSettled = vi.fn()
    await loadDashboardSections('123', onData, onError, onSettled)
    expect(onData).toHaveBeenCalledTimes(2)
    expect(onError).toHaveBeenCalledExactlyOnceWith('channels')
    expect(onSettled).toHaveBeenCalledTimes(3)
  })

  it('requests the selected section endpoint', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { status: true, data: emptyDashboard } })
    await dashboardApi.get('123', 'core')
    expect(apiClient.get).toHaveBeenCalledExactlyOnceWith('/api/v1/companies/123/dashboard/core')
  })

  it('uses one workspace request and normalizes long counts while preserving partial failures', async () => {
    const data = {
      contacts: { summary: null, recent: [], lists: [], segments: [] },
      broadcasts: { total: '15', recent: [] },
      inbox: null,
      connections: [],
      managedEmail: { overview: null, usage: null, deliverability: null },
      managedSms: { status: null, usage: null, health: null },
      ads: { connections: [], accounts: [], campaigns: [], campaignTotal: '3' },
      unavailableSections: ['contacts.summary'],
    }
    vi.mocked(apiClient.get).mockResolvedValue({ data: { status: true, data } })
    const result = await dashboardApi.get('123')
    expect(apiClient.get).toHaveBeenCalledTimes(1)
    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/companies/123/dashboard')
    expect(result.broadcasts.total).toBe(15)
    expect(result.ads.campaignTotal).toBe(3)
    expect(result.unavailableSections).toEqual(['contacts.summary'])
    expect(result.contacts).toEqual(data.contacts)
  })

  it('rejects unsuccessful responses so the dashboard can display a retry message', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: { status: false, error_message: 'Company access denied' },
    })
    await expect(dashboardApi.get('123')).rejects.toThrow('Company access denied')
  })
})
