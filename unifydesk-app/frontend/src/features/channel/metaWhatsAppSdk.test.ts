import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MetaWhatsAppSignupConfig } from './types'
import {
  __resetMetaWhatsAppSdkForTests,
  launchMetaWhatsAppSignup,
  loadMetaSdk,
  META_WHATSAPP_SIGNUP_TIMEOUT_MS,
} from './metaWhatsAppSdk'

const config: MetaWhatsAppSignupConfig = {
  appId: 'app-id',
  configurationId: 'config-id',
  graphApiVersion: 'v25.0',
  embeddedSignupVersion: '4',
  state: 'opaque-state',
  expiresAt: '2026-09-29T12:00:00Z',
  coexistenceAvailable: false,
}

type LoginCallback = (response: { status?: string; authResponse?: { code?: string } }) => void

const installFacebook = () => {
  let callback: LoginCallback | null = null
  let options: Record<string, unknown> | null = null

  window.FB = {
    init: vi.fn(),
    login: vi.fn((next, nextOptions) => {
      callback = next
      options = nextOptions
    }),
  }

  return {
    callback: () => {
      if (!callback) throw new Error('Facebook login callback was not installed')
      return callback
    },
    options: () => options,
  }
}

const embeddedEvent = (event: string, data: Record<string, unknown> = {}) =>
  new MessageEvent('message', {
    origin: 'https://www.facebook.com',
    data: { type: 'WA_EMBEDDED_SIGNUP', event, data },
  })

describe('Meta WhatsApp Embedded Signup v4', () => {
  beforeEach(() => {
    __resetMetaWhatsAppSdkForTests()
    document.getElementById('facebook-jssdk')?.remove()
  })

  afterEach(() => {
    vi.useRealTimers()
    document.getElementById('facebook-jssdk')?.remove()
    delete window.FB
  })

  it('surfaces SDK load failures and allows a clean retry', async () => {
    const firstLoad = loadMetaSdk('app-id', 'v25.0')
    const script = document.getElementById('facebook-jssdk')

    expect(script).toBeInstanceOf(HTMLScriptElement)
    script?.dispatchEvent(new Event('error'))
    await expect(firstLoad).rejects.toThrow('Unable to load the Meta SDK')
  })

  it('reinitializes an already loaded SDK when app/version metadata changes', async () => {
    installFacebook()

    await loadMetaSdk('app-id', 'v25.0')
    await loadMetaSdk('app-id-2', 'v26.0')

    expect(window.FB?.init).toHaveBeenNthCalledWith(1, {
      appId: 'app-id',
      cookie: true,
      xfbml: false,
      version: 'v25.0',
      fedCM: false,
    })
    expect(window.FB?.init).toHaveBeenNthCalledWith(2, {
      appId: 'app-id-2',
      cookie: true,
      xfbml: false,
      version: 'v26.0',
      fedCM: false,
    })
  })

  it('uses v4 configuration with session info schema version 3', async () => {
    const facebook = installFacebook()
    const promise = launchMetaWhatsAppSignup(config)

    expect(facebook.options()).toEqual({
      config_id: 'config-id',
      auth_type: 'rerequest',
      response_type: 'code',
      override_default_response_type: true,
      extras: { setup: {}, sessionInfoVersion: 3 },
    })
    expect(JSON.stringify(facebook.options())).toContain('"sessionInfoVersion":3')

    facebook.callback()({ authResponse: { code: 'auth-code' } })
    window.dispatchEvent(
      embeddedEvent('FINISH', {
        waba_id: 'waba-1',
        phone_number_id: 'phone-1',
        whatsapp_account_id: 'waac-1',
        messaging_account_id: 'ma-1',
      }),
    )

    await expect(promise).resolves.toEqual({
      code: 'auth-code',
      wabaId: 'waba-1',
      phoneNumberId: 'phone-1',
      whatsappAccountId: 'waac-1',
      messagingAccountId: 'ma-1',
    })
  })

  it('accepts Embedded Signup completion from trusted Facebook subdomains', async () => {
    const facebook = installFacebook()
    const promise = launchMetaWhatsAppSignup(config)

    facebook.callback()({ authResponse: { code: 'auth-code' } })
    window.dispatchEvent(
      new MessageEvent('message', {
        origin: 'https://business.facebook.com',
        data: {
          type: 'WA_EMBEDDED_SIGNUP',
          event: 'FINISH',
          data: { waba_id: 'waba-1', phone_number_id: 'phone-1' },
        },
      }),
    )

    await expect(promise).resolves.toMatchObject({
      wabaId: 'waba-1',
      phoneNumberId: 'phone-1',
    })
  })

  it('rejects lookalike and insecure Facebook origins', async () => {
    const facebook = installFacebook()
    const promise = launchMetaWhatsAppSignup(config)

    facebook.callback()({ authResponse: { code: 'auth-code' } })
    for (const origin of [
      'https://facebook.com.attacker.example',
      'https://evilfacebook.com',
      'http://www.facebook.com',
    ]) {
      window.dispatchEvent(
        new MessageEvent('message', {
          origin,
          data: {
            type: 'WA_EMBEDDED_SIGNUP',
            event: 'FINISH',
            data: { waba_id: 'evil', phone_number_id: 'evil' },
          },
        }),
      )
    }

    window.dispatchEvent(
      embeddedEvent('FINISH', { waba_id: 'waba-1', phone_number_id: 'phone-1' }),
    )

    await expect(promise).resolves.toMatchObject({
      wabaId: 'waba-1',
      phoneNumberId: 'phone-1',
    })
  })

  it('ignores untrusted origins and accepts a valid FINISH only once', async () => {
    const facebook = installFacebook()
    const removeSpy = vi.spyOn(window, 'removeEventListener')
    const promise = launchMetaWhatsAppSignup(config)

    facebook.callback()({ authResponse: { code: 'auth-code' } })
    window.dispatchEvent(
      new MessageEvent('message', {
        origin: 'https://attacker.example',
        data: {
          type: 'WA_EMBEDDED_SIGNUP',
          event: 'FINISH',
          data: { waba_id: 'evil', phone_number_id: 'evil' },
        },
      }),
    )
    window.dispatchEvent(
      embeddedEvent('FINISH', { waba_id: 'waba-1', phone_number_id: 'phone-1' }),
    )
    window.dispatchEvent(
      embeddedEvent('FINISH', { waba_id: 'waba-2', phone_number_id: 'phone-2' }),
    )

    await expect(promise).resolves.toMatchObject({
      wabaId: 'waba-1',
      phoneNumberId: 'phone-1',
    })
    expect(
      removeSpy.mock.calls.filter(([type]) => type === 'message'),
    ).toHaveLength(1)
    removeSpy.mockRestore()
  })

  const terminalCases: Array<{
    event: string
    data: Record<string, unknown>
    expected: string
  }> = [
    { event: 'CANCEL', data: {}, expected: 'cancelled' },
    {
      event: 'ERROR',
      data: { error_message: 'Provider rejected signup' },
      expected: 'Provider rejected signup',
    },
  ]

  it.each(terminalCases)('handles $event and removes its listener', async ({ event, data, expected }) => {
    const facebook = installFacebook()
    const removeSpy = vi.spyOn(window, 'removeEventListener')
    const promise = launchMetaWhatsAppSignup(config)

    window.dispatchEvent(embeddedEvent(event, data))

    await expect(promise).rejects.toThrow(expected)
    expect(
      removeSpy.mock.calls.filter(([type]) => type === 'message'),
    ).toHaveLength(1)
    expect(facebook.callback()).toBeTypeOf('function')
    removeSpy.mockRestore()
  })

  it('handles Facebook callback without an authorization code', async () => {
    const facebook = installFacebook()
    const promise = launchMetaWhatsAppSignup(config)

    facebook.callback()({ status: 'not_authorized' })

    await expect(promise).rejects.toThrow('authorization was not granted')
  })

  it('times out and cleans up exactly once', async () => {
    vi.useFakeTimers()
    installFacebook()
    const removeSpy = vi.spyOn(window, 'removeEventListener')
    const promise = launchMetaWhatsAppSignup(config)

    await vi.advanceTimersByTimeAsync(META_WHATSAPP_SIGNUP_TIMEOUT_MS)

    await expect(promise).rejects.toThrow('timed out')
    expect(
      removeSpy.mock.calls.filter(([type]) => type === 'message'),
    ).toHaveLength(1)
    removeSpy.mockRestore()
  })

  it('rejects a non-v4 configuration before opening Facebook Login', async () => {
    const facebook = installFacebook()
    const invalid = { ...config, embeddedSignupVersion: '3' as '4' }

    await expect(launchMetaWhatsAppSignup(invalid)).rejects.toThrow('requires Meta Embedded Signup v4')
    expect(window.FB?.login).not.toHaveBeenCalled()
    expect(facebook.options()).toBeNull()
  })
})
