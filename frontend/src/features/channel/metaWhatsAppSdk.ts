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

const META_SDK_SCRIPT_ID = 'facebook-jssdk'
const META_SDK_LOAD_TIMEOUT_MS = 15_000
const META_SIGNUP_TIMEOUT_MS = 5 * 60_000
const META_POPUP_HINT_DELAY_MS = 8_000

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
    const existing = document.getElementById(META_SDK_SCRIPT_ID) as HTMLScriptElement | null

    const cleanup = () => {
      window.clearTimeout(timeout)
      existing?.removeEventListener('load', handleScriptLoad)
      existing?.removeEventListener('error', handleScriptError)
    }

    const finish = () => {
      if (settled) return
      if (!initializeSdk(appId, graphApiVersion)) return
      settled = true
      cleanup()
      resolve()
    }

    const fail = (message: string) => {
      if (settled) return
      settled = true
      cleanup()
      sdkPromise = null
      reject(new Error(message))
    }

    const handleScriptLoad = () => {
      if (!initializeSdk(appId, graphApiVersion)) {
        fail('Meta SDK loaded but did not initialize')
        return
      }
      finish()
    }

    const handleScriptError = () => fail('Unable to load the Meta SDK')

    const timeout = window.setTimeout(
      () => fail('Meta SDK took too long to load'),
      META_SDK_LOAD_TIMEOUT_MS,
    )

    window.fbAsyncInit = finish

    if (existing) {
      existing.addEventListener('load', handleScriptLoad, { once: true })
      existing.addEventListener('error', handleScriptError, { once: true })

      // The script can already be loaded while FB has not yet been observed by this module.
      // Give the SDK a microtask to publish window.FB before waiting for fbAsyncInit/load.
      queueMicrotask(finish)
      return
    }

    const script = document.createElement('script')
    script.id = META_SDK_SCRIPT_ID
    script.async = true
    script.defer = true
    script.crossOrigin = 'anonymous'
    script.src = 'https://connect.facebook.net/en_US/sdk.js'
    script.addEventListener('load', handleScriptLoad, { once: true })
    script.addEventListener('error', handleScriptError, { once: true })
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
      META_SIGNUP_TIMEOUT_MS,
    )

    const popupHintTimeout = window.setTimeout(() => {
      if (!settled && !popupObserved) {
        options.onPopupNotObserved?.()
      }
    }, META_POPUP_HINT_DELAY_MS)

    window.addEventListener('message', onMessage)
    window.addEventListener('blur', handleWindowBlur)

    try {
      // Keep FB.login synchronous with the user's click. Browsers are much more likely to allow
      // the Meta window when the SDK invocation remains in the original user-activation task.
      window.FB.login(
        (response) => {
          popupObserved = true
          const returnedCode = response.authResponse?.code
          if (!returnedCode) {
            const status = response.status ? ` (status: ${response.status})` : ''
            fail(
              `Meta login was cancelled or did not return an authorization code${status}. Check the Facebook Login for Business configuration and try again.`,
            )
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
          // Embedded Signup v4 derives products/assets/permissions from the Login for Business
          // configuration. Do not send the legacy sessionInfoVersion v3 option here.
          extras: {
            setup: {},
          },
        },
      )
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Meta login could not be opened'
      fail(`Unable to open Meta login: ${message}`)
    }
  })
