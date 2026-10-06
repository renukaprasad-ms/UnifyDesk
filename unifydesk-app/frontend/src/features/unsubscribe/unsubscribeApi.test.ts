import { beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from '../../lib/api/apiClient'
import { EMAIL_UNSUBSCRIBE_ENDPOINT, unsubscribeApi } from './unsubscribeApi'

vi.mock('../../lib/api/apiClient', () => ({
  default: {
    post: vi.fn(),
  },
}))

describe('unsubscribeApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(apiClient.post).mockResolvedValue({} as never)
  })

  it('posts to the final public unsubscribe endpoint with no request body', async () => {
    await unsubscribeApi.unsubscribe('opaque-token')

    expect(apiClient.post).toHaveBeenCalledOnce()
    expect(apiClient.post).toHaveBeenCalledWith(
      `${EMAIL_UNSUBSCRIBE_ENDPOINT}/opaque-token`,
      undefined,
      {
        _skipAuthRefresh: true,
        _skipErrorSnackbar: true,
      },
    )
  })

  it('URL-encodes the opaque token without parsing or decoding it', async () => {
    const token = 'opaque/token?part=a+b%25'

    await unsubscribeApi.unsubscribe(token)

    expect(apiClient.post).toHaveBeenCalledWith(
      `${EMAIL_UNSUBSCRIBE_ENDPOINT}/${encodeURIComponent(token)}`,
      undefined,
      expect.any(Object),
    )
  })

  it('does not send internal recipient identifiers in a request body', async () => {
    await unsubscribeApi.unsubscribe('opaque-token')

    const [, body] = vi.mocked(apiClient.post).mock.calls[0] ?? []
    expect(body).toBeUndefined()
    expect(JSON.stringify(body)).not.toMatch(/companyId|contactId|identityId|channel|purpose/i)
  })
})
