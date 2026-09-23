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

const SDK_SCRIPT_ID = 'facebook-jssdk'
const SDK_LOAD_TIMEOUT_MS = 15_000
const SIGNUP_TIMEOUT_MS = 5 * 60_000
const POPUP_HINT_DELAY_MS = 8_000

let sdkPromise: Promise<void> | null = null

const initializeSdk = (appId: string, graphApiVersion: string) => {
  if (!window.FB) return false
  window.FB.init({ appId, cookie: true, xfbml: false, version: graphApiVersion })
  return true
}

export const loadMetaSdk = (appId: string, graphApiVersion: string) => {
  if (initializeSdk(appId, graphApiVersion)) {
    return Promise.resolve()
  }
  if (sdkPromise) return sdkPromise

  sdkPromise = new Promise<void>((resolve, reject) => {
    let settled = false

    const finish = () => {
      if (settled || !initializeSdk(appId, graphApiVersion)) return
      settled = true
      window.clearTimeout(timeout)
      resolve()
    }

    const fail = (message: string) => {
      if (settled) return
      settled = true
      window.clearTimeout(timeout)
      sdkPromise = null
      reject(new Error(message))
    }

    const timeout = window.setTimeout(
      () => fail('Meta SDK took too long to load'),
      SDK_LOAD_TIMEOUT_MS,
    )

    window.fbAsyncInit = finish

    const existing = document.getElementById(SDK_SCRIPT_ID) as HTMLScriptElement | null
    if (existing) {
      existing.addEventListener('load', finish, { once: true })
      existing.addEventListener(
        'error',
        () => fail('Unable to load the Meta SDK'),
        { once: true },
      )

      // If a previous SDK load left a stale script element but never produced window.FB,
      // replace it so a later retry can recover without requiring a full browser restart.
      window.setTimeout(() => {
        if (!settled && !window.FB && existing.isConnected) {
          existing.remove()
          sdkPromise = null
          fail('Meta SDK did not initialize. Refresh the page and try again.')
        }
      }, 2_000)
      return
    }

    const script = document.createElement('script')
    script.id = SDK_SCRIPT_ID
    script.async = true
    script.defer = true
    script.crossOrigin = 'anonymous'
    script.src = 'https://connect.facebook.net/en_US/sdk.js'
    script.addEventListener('load', finish, { once: true })
    script.addEventListener(
      'error',
      () => fail('Unable to load the Meta SDK'),
      { once: true },
    )
    document.head.appendChild(script)
  })

  return sdkPromise
}

type MetaSignupLaunchOptions = {
  onPopupNotObserved?: () => void
}

export const launchMetaWhatsAppSignup = (
  config: MetaWhatsAppSignupConfig,
  options: MetaSignupLaunchOptions = {},
): Promise<MetaWhatsAppSignupResult> =>
  new Promise((resolve, reject) => {
    if (!window.isSecureContext && window.location.hostname !== 'localhost') {
      reject(new Error('Meta WhatsApp signup requires HTTPS'))
      return
    }
    if (!window.FB) {
      reject(new Error('Meta SDK is not ready. Refresh the page and try again.'))
      return
    }

    let code = ''
    let wabaId = ''
    let phoneNumberId = ''
    let settled = false
    let popupObserved = false

    const handleWindowBlur = () => {
      popupObserved = true
    }

    const cleanup = () => {
      window.removeEventListener('message', onMessage)
      window.removeEventListener('blur', handleWindowBlur)
      window.clearTimeout(timeout)
      window.clearTimeout(popupHintTimeout)
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

        popupObserved = true

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
      () =>
        fail(
          'Meta WhatsApp signup timed out. If no Meta window opened, allow pop-ups for this site and try again.',
        ),
      SIGNUP_TIMEOUT_MS,
    )

    const popupHintTimeout = window.setTimeout(() => {
      if (!settled && !popupObserved) {
        options.onPopupNotObserved?.()
      }
    }, POPUP_HINT_DELAY_MS)

    window.addEventListener('message', onMessage)
    window.addEventListener('blur', handleWindowBlur)

    try {
      // Keep FB.login in the same synchronous click task. That preserves browser user
      // activation and makes popup blocking much less likely.
      window.FB.login(
        (response) => {
          popupObserved = true
          const returnedCode = response.authResponse?.code
          if (!returnedCode) {
            const status = response.status ? ` (status: ${response.status})` : ''
            fail(
              `Meta login was cancelled or did not return an authorization code${status}. Check the Facebook Login for Business configuration and permissions.`,
            )
            return
          }
          code = returnedCode
          finishIfReady()
        },
        {
          config_id: config.configurationId,
          response_type: 'code',
          override_default_response_type: true,
          extras: {
            setup: {},
            featureType: '',
            sessionInfoVersion: '3',
          },
        },
      )
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Meta login could not be opened'
      fail(`Unable to open Meta login: ${message}`)
    }
  })
