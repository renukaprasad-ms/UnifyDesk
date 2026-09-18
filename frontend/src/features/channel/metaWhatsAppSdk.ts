import type { MetaWhatsAppSignupConfig, MetaWhatsAppSignupResult } from './types'

type FacebookLoginResponse = {
  status?: string
  authResponse?: {
    code?: string
  }
}

type FacebookSdk = {
  init: (options: { appId: string; cookie: boolean; xfbml: boolean; version: string }) => void
  login: (
    callback: (response: FacebookLoginResponse) => void,
    options: Record<string, unknown>,
  ) => void
}

declare global {
  interface Window {
    FB?: FacebookSdk
    fbAsyncInit?: () => void
  }
}

let sdkPromise: Promise<void> | null = null

export const loadMetaSdk = (appId: string, graphApiVersion: string) => {
  if (window.FB) {
    window.FB.init({ appId, cookie: true, xfbml: false, version: graphApiVersion })
    return Promise.resolve()
  }
  if (sdkPromise) return sdkPromise

  sdkPromise = new Promise<void>((resolve, reject) => {
    const timeout = window.setTimeout(
      () => reject(new Error('Meta SDK took too long to load')),
      15000,
    )

    window.fbAsyncInit = () => {
      window.clearTimeout(timeout)
      if (!window.FB) {
        reject(new Error('Meta SDK did not initialize'))
        return
      }
      window.FB.init({ appId, cookie: true, xfbml: false, version: graphApiVersion })
      resolve()
    }

    const existing = document.getElementById('facebook-jssdk')
    if (existing) return

    const script = document.createElement('script')
    script.id = 'facebook-jssdk'
    script.async = true
    script.defer = true
    script.crossOrigin = 'anonymous'
    script.src = 'https://connect.facebook.net/en_US/sdk.js'
    script.onerror = () => {
      window.clearTimeout(timeout)
      reject(new Error('Unable to load the Meta SDK'))
    }
    document.head.appendChild(script)
  })

  return sdkPromise
}

export const launchMetaWhatsAppSignup = (
  config: MetaWhatsAppSignupConfig,
): Promise<MetaWhatsAppSignupResult> =>
  new Promise((resolve, reject) => {
    if (!window.FB) {
      reject(new Error('Meta SDK is not ready'))
      return
    }

    let code = ''
    let wabaId = ''
    let phoneNumberId = ''
    let settled = false

    const cleanup = () => {
      window.removeEventListener('message', onMessage)
      window.clearTimeout(timeout)
    }

    const finishIfReady = () => {
      if (settled || !code || !wabaId || !phoneNumberId) return
      settled = true
      cleanup()
      resolve({ code, wabaId, phoneNumberId })
    }

    const fail = (message: string) => {
      if (settled) return
      settled = true
      cleanup()
      reject(new Error(message))
    }

    const onMessage = (event: MessageEvent) => {
      if (
        event.origin !== 'https://www.facebook.com' &&
        event.origin !== 'https://web.facebook.com'
      ) {
        return
      }

      try {
        const data = typeof event.data === 'string' ? JSON.parse(event.data) : event.data
        if (!data || data.type !== 'WA_EMBEDDED_SIGNUP') return

        if (typeof data.event === 'string' && data.event.startsWith('FINISH')) {
          wabaId = String(data.data?.waba_id ?? '')
          phoneNumberId = String(data.data?.phone_number_id ?? '')
          if (!wabaId || !phoneNumberId) {
            fail('Meta did not return the WhatsApp account and phone number IDs')
            return
          }
          finishIfReady()
          return
        }

        if (data.event === 'CANCEL') {
          fail('WhatsApp connection was cancelled')
        } else if (data.event === 'ERROR') {
          fail(data.data?.error_message || 'Meta could not complete WhatsApp signup')
        }
      } catch {
        // Ignore unrelated non-JSON postMessage traffic from the page.
      }
    }

    const timeout = window.setTimeout(
      () => fail('WhatsApp signup timed out. Please try again.'),
      120000,
    )

    window.addEventListener('message', onMessage)

    window.FB.login(
      (response) => {
        const returnedCode = response.authResponse?.code
        if (!returnedCode) {
          fail('Meta login was cancelled or did not return an authorization code')
          return
        }
        code = returnedCode
        finishIfReady()
      },
      {
        config_id: config.configurationId,
        auth_type: 'rerequest',
        response_type: 'code',
        override_default_response_type: true,
        extras: {
          setup: {},
          sessionInfoVersion: '3',
        },
      },
    )
  })
