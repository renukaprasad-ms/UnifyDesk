import type { MetaWhatsAppSignupConfig, MetaWhatsAppSignupResult } from './types'

type FacebookLoginResponse = {
  status?: string
  authResponse?: {
    code?: string
  }
}

type FacebookSdk = {
  init: (options: {
    appId: string
    cookie: boolean
    xfbml: boolean
    version: string
    fedCM?: boolean
  }) => void
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
const POPUP_HINT_DELAY_MS = 6_000
const LOG_PREFIX = '[MetaWhatsApp]'

let sdkPromise: Promise<void> | null = null
let sdkInitializedKey: string | null = null

const metaLog = (stage: string, details?: Record<string, unknown>) => {
  if (details) {
    console.info(LOG_PREFIX, stage, details)
  } else {
    console.info(LOG_PREFIX, stage)
  }
}

const metaWarn = (stage: string, details?: Record<string, unknown>) => {
  if (details) {
    console.warn(LOG_PREFIX, stage, details)
  } else {
    console.warn(LOG_PREFIX, stage)
  }
}

const metaError = (stage: string, details?: Record<string, unknown>) => {
  if (details) {
    console.error(LOG_PREFIX, stage, details)
  } else {
    console.error(LOG_PREFIX, stage)
  }
}

const initializeSdk = (appId: string, graphApiVersion: string) => {
  const normalizedAppId = appId.trim()
  const normalizedVersion = graphApiVersion.trim()
  const initKey = `${normalizedAppId}:${normalizedVersion}`

  if (!window.FB) {
    metaWarn('SDK_INIT_SKIPPED', { reason: 'window.FB is not available' })
    return false
  }

  if (sdkInitializedKey === initKey) {
    metaLog('SDK_ALREADY_INITIALIZED', {
      appId: normalizedAppId,
      graphApiVersion: normalizedVersion,
    })
    return true
  }

  metaLog('SDK_INIT_START', {
    appId: normalizedAppId,
    graphApiVersion: normalizedVersion,
    secureContext: window.isSecureContext,
    hostname: window.location.hostname,
  })

  window.FB.init({
    appId: normalizedAppId,
    cookie: true,
    xfbml: false,
    version: normalizedVersion,
    // Login for Business / WhatsApp Embedded Signup uses config_id.
    // Keep Facebook's newer one-tap FedCM path disabled because it can
    // interfere with config_id-based business login flows.
    fedCM: false,
  })
  sdkInitializedKey = initKey

  metaLog('SDK_READY', {
    appId: normalizedAppId,
    graphApiVersion: normalizedVersion,
    fedCM: false,
  })
  return true
}

export const loadMetaSdk = (appId: string, graphApiVersion: string) => {
  const normalizedAppId = appId.trim()
  const normalizedVersion = graphApiVersion.trim()
  const initKey = `${normalizedAppId}:${normalizedVersion}`

  metaLog('SDK_LOAD_REQUESTED', {
    appId: normalizedAppId,
    graphApiVersion: normalizedVersion,
    appIdTrimmed: normalizedAppId !== appId,
    graphApiVersionTrimmed: normalizedVersion !== graphApiVersion,
    fbAlreadyAvailable: Boolean(window.FB),
    initializedForRequestedApp: sdkInitializedKey === initKey,
  })

  if (sdkInitializedKey === initKey && window.FB) {
    metaLog('SDK_REUSED')
    return Promise.resolve()
  }

  if (sdkPromise) {
    metaLog('SDK_LOAD_REUSED_PENDING_PROMISE')
    return sdkPromise
  }

  sdkPromise = new Promise<void>((resolve, reject) => {
    let settled = false

    const finish = () => {
      if (settled) return

      try {
        if (!initializeSdk(normalizedAppId, normalizedVersion)) {
          metaWarn('FB_ASYNC_INIT_WITHOUT_FB')
          return
        }

        settled = true
        window.clearTimeout(timeout)
        metaLog('SDK_LOAD_COMPLETE')
        resolve()
      } catch (error) {
        fail(
          error instanceof Error
            ? `Meta SDK initialization failed: ${error.message}`
            : 'Meta SDK initialization failed',
        )
      }
    }

    const fail = (message: string) => {
      if (settled) return
      settled = true
      window.clearTimeout(timeout)
      sdkPromise = null
      metaError('SDK_LOAD_FAILED', { message })
      reject(new Error(message))
    }

    const timeout = window.setTimeout(
      () => fail('Meta SDK took too long to initialize'),
      SDK_LOAD_TIMEOUT_MS,
    )

    // Meta's SDK is only guaranteed to be ready for FB.init from fbAsyncInit.
    // The script element's load event can fire before the SDK's internal bootstrap
    // has reached that point, so never call FB.init from the load event.
    window.fbAsyncInit = () => {
      metaLog('FB_ASYNC_INIT_CALLBACK')
      finish()
    }

    const existing = document.getElementById(SDK_SCRIPT_ID) as HTMLScriptElement | null
    if (existing) {
      metaLog('SDK_SCRIPT_ALREADY_PRESENT', {
        src: existing.src,
        connected: existing.isConnected,
        fbAvailable: Boolean(window.FB),
      })

      existing.addEventListener(
        'load',
        () => {
          metaLog('SDK_EXISTING_SCRIPT_LOAD_EVENT', {
            fbAvailable: Boolean(window.FB),
            note: 'Waiting for fbAsyncInit before calling FB.init.',
          })
        },
        { once: true },
      )
      existing.addEventListener(
        'error',
        () => fail('Unable to load the Meta SDK'),
        { once: true },
      )

      // If the script came from an earlier fully completed bootstrap in this SPA,
      // our module-local marker may have been lost. Give fbAsyncInit a chance first,
      // then initialize as a recovery path only when FB is already present.
      window.setTimeout(() => {
        if (!settled && window.FB) {
          metaWarn('SDK_EXISTING_SCRIPT_RECOVERY_INIT')
          finish()
        }
      }, 1_000)
      return
    }

    const script = document.createElement('script')
    script.id = SDK_SCRIPT_ID
    script.async = true
    script.defer = true
    script.crossOrigin = 'anonymous'
    script.src = 'https://connect.facebook.net/en_US/sdk.js'
    script.addEventListener(
      'load',
      () => {
        metaLog('SDK_SCRIPT_LOAD_EVENT', {
          fbAvailable: Boolean(window.FB),
          note: 'Waiting for fbAsyncInit before calling FB.init.',
        })
      },
      { once: true },
    )
    script.addEventListener(
      'error',
      () => fail('Unable to load the Meta SDK'),
      { once: true },
    )

    metaLog('SDK_SCRIPT_APPENDED', { src: script.src })
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
    const startedAt = performance.now()
    const elapsedMs = () => Math.round(performance.now() - startedAt)

    const normalizedAppId = config.appId.trim()
    const normalizedConfigurationId = config.configurationId.trim()
    const normalizedGraphApiVersion = config.graphApiVersion.trim()

    metaLog('SIGNUP_START', {
      appId: normalizedAppId,
      configurationId: normalizedConfigurationId,
      graphApiVersion: normalizedGraphApiVersion,
      appIdTrimmed: normalizedAppId !== config.appId,
      configurationIdTrimmed: normalizedConfigurationId !== config.configurationId,
      secureContext: window.isSecureContext,
      hostname: window.location.hostname,
      fbAvailable: Boolean(window.FB),
    })

    if (!window.isSecureContext && window.location.hostname !== 'localhost') {
      metaError('SIGNUP_REJECTED_INSECURE_CONTEXT')
      reject(new Error('Meta WhatsApp signup requires HTTPS'))
      return
    }

    if (!window.FB) {
      metaError('SIGNUP_REJECTED_SDK_NOT_READY')
      reject(new Error('Meta SDK is not ready. Refresh the page and try again.'))
      return
    }

    let code = ''
    let wabaId = ''
    let phoneNumberId = ''
    let settled = false
    let metaResponseObserved = false

    const cleanup = () => {
      window.removeEventListener('message', onMessage)
      window.clearTimeout(timeout)
      window.clearTimeout(popupHintTimeout)
      metaLog('SIGNUP_CLEANUP', { elapsedMs: elapsedMs() })
    }

    const finishIfReady = () => {
      if (settled || !code || !wabaId || !phoneNumberId) {
        if (!settled) {
          metaLog('SIGNUP_WAITING_FOR_DATA', {
            elapsedMs: elapsedMs(),
            hasCode: Boolean(code),
            hasWabaId: Boolean(wabaId),
            hasPhoneNumberId: Boolean(phoneNumberId),
          })
        }
        return
      }

      settled = true
      metaLog('SIGNUP_RESULT_READY', {
        elapsedMs: elapsedMs(),
        hasCode: true,
        wabaId,
        phoneNumberId,
      })
      cleanup()
      resolve({ code, wabaId, phoneNumberId })
    }

    const fail = (message: string, stage = 'SIGNUP_FAILED') => {
      if (settled) return
      settled = true
      metaError(stage, {
        elapsedMs: elapsedMs(),
        message,
        hasCode: Boolean(code),
        hasWabaId: Boolean(wabaId),
        hasPhoneNumberId: Boolean(phoneNumberId),
      })
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

      metaLog('FACEBOOK_POST_MESSAGE_RECEIVED', {
        elapsedMs: elapsedMs(),
        origin: event.origin,
        dataType: typeof event.data,
      })

      try {
        const data = typeof event.data === 'string' ? JSON.parse(event.data) : event.data
        if (!data || data.type !== 'WA_EMBEDDED_SIGNUP') {
          metaLog('FACEBOOK_POST_MESSAGE_IGNORED', {
            elapsedMs: elapsedMs(),
            messageType: data?.type ?? null,
            event: data?.event ?? null,
          })
          return
        }

        metaResponseObserved = true
        metaLog('EMBEDDED_SIGNUP_EVENT', {
          elapsedMs: elapsedMs(),
          event: data.event ?? null,
        })

        if (typeof data.event === 'string' && data.event.startsWith('FINISH')) {
          wabaId = String(data.data?.waba_id ?? '')
          phoneNumberId = String(data.data?.phone_number_id ?? '')

          metaLog('EMBEDDED_SIGNUP_FINISH', {
            elapsedMs: elapsedMs(),
            wabaId,
            phoneNumberId,
          })

          if (!wabaId || !phoneNumberId) {
            fail(
              'Meta did not return the WhatsApp account and phone number IDs',
              'EMBEDDED_SIGNUP_MISSING_IDS',
            )
            return
          }

          finishIfReady()
          return
        }

        if (data.event === 'CANCEL') {
          fail('WhatsApp connection was cancelled', 'EMBEDDED_SIGNUP_CANCELLED')
        } else if (data.event === 'ERROR') {
          fail(
            data.data?.error_message || 'Meta could not complete WhatsApp signup',
            'EMBEDDED_SIGNUP_ERROR',
          )
        }
      } catch (error) {
        metaWarn('FACEBOOK_POST_MESSAGE_PARSE_FAILED', {
          elapsedMs: elapsedMs(),
          message: error instanceof Error ? error.message : 'Unknown parse error',
        })
      }
    }

    const timeout = window.setTimeout(
      () =>
        fail(
          'Meta WhatsApp signup timed out. Check the browser console for [MetaWhatsApp] logs and verify the Facebook Login for Business configuration.',
          'SIGNUP_TIMEOUT',
        ),
      SIGNUP_TIMEOUT_MS,
    )

    // There is no reliable browser API for observing the popup created internally by FB.login().
    // In particular, window blur is NOT proof that the popup opened. Only a Facebook callback
    // or WhatsApp Embedded Signup postMessage is treated as a real Meta response.
    const popupHintTimeout = window.setTimeout(() => {
      if (!settled && !metaResponseObserved) {
        metaWarn('NO_META_RESPONSE_YET', {
          elapsedMs: elapsedMs(),
          message:
            'FB.login was invoked but no Facebook callback or WhatsApp Embedded Signup message has been observed yet.',
        })
        options.onPopupNotObserved?.()
      }
    }, POPUP_HINT_DELAY_MS)

    window.addEventListener('message', onMessage)
    metaLog('POST_MESSAGE_LISTENER_ATTACHED', { elapsedMs: elapsedMs() })

    try {
      // Keep FB.login in this synchronous user-click task. Awaiting a network call before
      // FB.login can lose browser user activation and cause popup blocking.
      metaLog('FB_LOGIN_CALLING', {
        elapsedMs: elapsedMs(),
        configurationId: normalizedConfigurationId,
      })

      window.FB.login(
        (response) => {
          metaResponseObserved = true

          const returnedCode = response.authResponse?.code
          metaLog('FB_LOGIN_CALLBACK', {
            elapsedMs: elapsedMs(),
            status: response.status ?? null,
            hasAuthResponse: Boolean(response.authResponse),
            hasCode: Boolean(returnedCode),
          })

          if (!returnedCode) {
            const status = response.status ? ` (status: ${response.status})` : ''
            fail(
              `Meta login was cancelled or did not return an authorization code${status}. Check the Facebook Login for Business configuration and permissions.`,
              'FB_LOGIN_NO_CODE',
            )
            return
          }

          code = returnedCode
          metaLog('FB_LOGIN_CODE_RECEIVED', {
            elapsedMs: elapsedMs(),
            codeLength: returnedCode.length,
          })
          finishIfReady()
        },
        {
          config_id: normalizedConfigurationId,
          auth_type: 'rerequest',
          response_type: 'code',
          override_default_response_type: true,
          // Embedded Signup v4 gets products, assets and permissions from the
          // Facebook Login for Business configuration. Keep the launch payload minimal.
          extras: {
            setup: {},
          },
        },
      )

      metaLog('FB_LOGIN_CALL_RETURNED', {
        elapsedMs: elapsedMs(),
        note: 'This only means the JavaScript SDK call returned; it does not prove a popup opened.',
      })
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Meta login could not be opened'
      fail(`Unable to open Meta login: ${message}`, 'FB_LOGIN_THROWN')
    }
  })
