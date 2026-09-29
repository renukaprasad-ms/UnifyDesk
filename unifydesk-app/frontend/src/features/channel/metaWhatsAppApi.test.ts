import { beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from '../../lib/api/apiClient'
import { metaWhatsAppApi } from './metaWhatsAppApi'

vi.mock('../../lib/api/apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

const post = vi.mocked(apiClient.post)
const get = vi.mocked(apiClient.get)

describe('metaWhatsAppApi frozen Round-1 endpoints', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('mints a signup session only when signupSession is explicitly called and each retry is fresh', async () => {
    post
      .mockResolvedValueOnce({
        data: {
          data: {
            appId: 'app',
            configurationId: 'configuration',
            graphApiVersion: 'v25.0',
            embeddedSignupVersion: '4',
            state: 'state-1',
            expiresAt: '2026-09-29T12:00:00Z',
            coexistenceAvailable: false,
          },
        },
      } as never)
      .mockResolvedValueOnce({
        data: {
          data: {
            appId: 'app',
            configurationId: 'configuration',
            graphApiVersion: 'v25.0',
            embeddedSignupVersion: '4',
            state: 'state-2',
            expiresAt: '2026-09-29T12:01:00Z',
            coexistenceAvailable: false,
          },
        },
      } as never)

    expect(post).not.toHaveBeenCalled()
    const first = await metaWhatsAppApi.signupSession('company-1')
    const second = await metaWhatsAppApi.signupSession('company-1')

    expect(first.state).toBe('state-1')
    expect(second.state).toBe('state-2')
    expect(post).toHaveBeenNthCalledWith(
      1,
      '/api/v1/companies/company-1/channels/whatsapp/meta/signup-session',
    )
    expect(post).toHaveBeenNthCalledWith(
      2,
      '/api/v1/companies/company-1/channels/whatsapp/meta/signup-session',
    )
  })

  it('uses the reauthorize, connection and reconcile frozen paths', async () => {
    post.mockResolvedValue({ data: { data: { state: 'x' } } } as never)
    get.mockResolvedValue({
      data: { data: { connectionId: '1', status: 'ACTIVE', nextAction: 'NONE' } },
    } as never)

    await metaWhatsAppApi.reauthorizeSession('company-1')
    await metaWhatsAppApi.connection('company-1')
    await metaWhatsAppApi.reconcile('company-1')

    expect(post).toHaveBeenCalledWith(
      '/api/v1/companies/company-1/channels/whatsapp/meta/reauthorize-session',
    )
    expect(get).toHaveBeenCalledWith(
      '/api/v1/companies/company-1/channels/whatsapp/meta/connection',
    )
    expect(post).toHaveBeenCalledWith(
      '/api/v1/companies/company-1/channels/whatsapp/meta/reconcile',
    )
  })

  it('passes provider identifiers only through the frozen completion body', async () => {
    post.mockResolvedValue({
      data: { data: { connectionId: '1', status: 'VALIDATING', nextAction: 'WAIT' } },
    } as never)

    await metaWhatsAppApi.complete('company-1', 'opaque-state', {
      code: 'authorization-code',
      wabaId: 'waba',
      phoneNumberId: 'phone',
      whatsappAccountId: 'waac',
      messagingAccountId: 'ma',
    })

    expect(post).toHaveBeenCalledWith(
      '/api/v1/companies/company-1/channels/whatsapp/meta/complete',
      {
        state: 'opaque-state',
        code: 'authorization-code',
        wabaId: 'waba',
        phoneNumberId: 'phone',
        whatsappAccountId: 'waac',
        messagingAccountId: 'ma',
      },
    )
  })
})
