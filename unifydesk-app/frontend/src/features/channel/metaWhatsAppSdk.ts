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

type EmbeddedSignupMessage = {
  type?: string
  event?: string
  data?: {
    waba_id?: string
    phone_number_id?: string
    error_message?: string
  }
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

let sdkPromise: Promise<void> | null = null
let sdkInitializedKey: string | null = null

const initializeSdk = (appId: string, graphApiVersion: string) => {
  const normalizedAppId = appId.trim()
  const normalizedVersion = graphApiVersion.trim()
  const initKey = `${normalizedAppId}:${normalizedVersion}`

  if (!window.FB) return false
  if (sdkInitializedKey === initKey) return true

  window.FB.init({
    appId: normalizedAppId,
    cookie: true,
    xfbml: false,
    version: normalizedVersion,
    fedCM: false,
  })
  sdkInitializedKey = initKey
  return true
}

export const loadMetaSdk = (appId: string, graphApiVersion: string) => {
  const normalizedAppId = appId.trim()
  const normalizedVersion = graphApiVersion.trim()
  const initKey = `${normalizedAppId}:${normalizedVersion}`

  if (sdkInitializedKey === initKey && window.FB) return Promise.resolve()
  if (sdkPromise) return sdkPromise

  sdkPromise = new Promise<void>((resolve, reject) => {
    let settled = false

    const fail = (message: string) => {
      if (settled) return
      settled = true
      window.clearTimeout(timeout)
      sdkPromise = null
      reject(new Error(message))
    }

    const finish = () => {
      if (settled) return
      try {
        if (!initializeSdk(normalizedAppId, normalizedVersion)) return
        settled = true
        window.clearTimeout(timeout)
        resolve()
      } catch (error) {
        fail(
          error instanceof Error
            ? `Meta SDK initialization failed: ${error.message}`
            : 'Meta SDK initialization failed',
        )
      }
    }

    const timeout = window.setTimeout(
      () => fail('Meta SDK took too long to initialize'),
      SDK_LOAD_TIMEOUT_MS,
    )

    window.fbAsyncInit = finish

    const existing = document.getElementById(SDK_SCRIPT_ID) as HTMLScriptElement | null
    if (existing) {
      existing.addEventListener('error', () => fail('Unable to load the Meta SDK'), { once: true })
      window.setTimeout(() => {
        if (!settled && window.FB) finish()
      }, 1_000)
      return
    }

    const script = document.createElement('script')
    script.id = SDK_SCRIPT_ID
    script.async = true
    script.defer = true
    script.crossOrigin = 'anonymous'
    script.src = 'https://connect.facebook.net/en_US/sdk.js'
    script.addEventListener('error', () => fail('Unable to load the Meta SDK'), { once: true })
    document.head.appendChild(script)
  })

  return sdkPromise
}

const parseEmbeddedSignupMessage = (value: unknown): EmbeddedSignupMessage | null => {
  if (value && typeof value === 'object') {
    return value as EmbeddedSignupMessage
  }

  if (typeof value !== 'string') return null

  const trimmed = value.trim()
  if (!trimmed.startsWith('{')) {
    // The Facebook SDK also posts internal protocol messages such as "cb=...".
    // They are not WhatsApp Embedded Signup events and must be ignored.
    return null
  }

  try {
    return JSON.parse(trimmed) as EmbeddedSignupMessage
  } catch {
    return null
  }
}

export const launchMetaWhatsAppSignup = (
  config: MetaWhatsAppSignupConfig,
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

      const data = parseEmbeddedSignupMessage(event.data)
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
    }

    const timeout = window.setTimeout(
      () => fail('WhatsApp connection timed out. Please try again.'),
      SIGNUP_TIMEOUT_MS,
    )

    window.addEventListener('message', onMessage)

    try {
      window.FB.login(
        (response) => {
          const returnedCode = response.authResponse?.code
          if (!returnedCode) {
            fail('WhatsApp connection was cancelled or could not be completed.')
            return
          }

          code = returnedCode
          finishIfReady()
        },
        {
          config_id: config.configurationId.trim(),
          auth_type: 'rerequest',
          response_type: 'code',
          override_default_response_type: true,
          extras: {
            setup: {},
            sessionInfoVersion: '3',
          },
        },
      )
    } catch {
      fail('Unable to open Meta login. Please try again.')
    }
  })
