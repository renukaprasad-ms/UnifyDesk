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
  type?: unknown
  event?: unknown
  data?: unknown
}

type EmbeddedSignupData = {
  waba_id?: unknown
  phone_number_id?: unknown
  whatsapp_account_id?: unknown
  messaging_account_id?: unknown
  error_message?: unknown
}

declare global {
  interface Window {
    FB?: FacebookSdk
    fbAsyncInit?: () => void
  }
}

const SDK_SCRIPT_ID = 'facebook-jssdk'
const SDK_LOAD_TIMEOUT_MS = 15_000
export const META_WHATSAPP_SIGNUP_TIMEOUT_MS = 5 * 60_000
export const META_WHATSAPP_SESSION_INFO_GRACE_MS = 3_000
const isAllowedFacebookOrigin = (origin: string) => {
  try {
    const url = new URL(origin)
    const hostname = url.hostname.toLowerCase()
    return (
      url.protocol === 'https:' &&
      (hostname === 'facebook.com' || hostname.endsWith('.facebook.com'))
    )
  } catch {
    return false
  }
}

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

  if (window.FB) {
    initializeSdk(normalizedAppId, normalizedVersion)
    return Promise.resolve()
  }
  if (sdkPromise) return sdkPromise

  sdkPromise = new Promise<void>((resolve, reject) => {
    let settled = false

    const cleanup = () => {
      window.clearTimeout(timeout)
      if (window.fbAsyncInit === finish) window.fbAsyncInit = undefined
    }

    const fail = (message: string) => {
      if (settled) return
      settled = true
      cleanup()
      sdkPromise = null
      reject(new Error(message))
    }

    const finish = () => {
      if (settled) return
      try {
        if (!initializeSdk(normalizedAppId, normalizedVersion)) return
        settled = true
        cleanup()
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
      }, 0)
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
  if (value && typeof value === 'object') return value as EmbeddedSignupMessage
  if (typeof value !== 'string') return null

  const trimmed = value.trim()
  if (!trimmed.startsWith('{')) return null

  try {
    const parsed = JSON.parse(trimmed) as unknown
    return parsed && typeof parsed === 'object' ? (parsed as EmbeddedSignupMessage) : null
  } catch {
    return null
  }
}

const stringValue = (value: unknown) =>
  typeof value === 'string' && value.trim() ? value.trim() : undefined

export const launchMetaWhatsAppSignup = (
  config: MetaWhatsAppSignupConfig,
): Promise<MetaWhatsAppSignupResult> =>
  new Promise((resolve, reject) => {
    if (config.embeddedSignupVersion !== '4') {
      reject(new Error('This WhatsApp connection requires Meta Embedded Signup v4.'))
      return
    }

    if (!window.isSecureContext && window.location.hostname !== 'localhost') {
      reject(new Error('Meta WhatsApp signup requires HTTPS'))
      return
    }

    if (!window.FB) {
      reject(new Error('Meta SDK is not ready. Please try again.'))
      return
    }

    let code: string | undefined
    let finishData: EmbeddedSignupData | undefined
    let settled = false
    let sessionInfoGraceTimeout: number | undefined

    const cleanup = () => {
      window.removeEventListener('message', onMessage)
      window.clearTimeout(timeout)
      if (sessionInfoGraceTimeout !== undefined) {
        window.clearTimeout(sessionInfoGraceTimeout)
      }
    }

    const settleError = (message: string) => {
      if (settled) return
      settled = true
      cleanup()
      reject(new Error(message))
    }

    const settleSuccess = (allowCodeOnly = false) => {
      if (settled || !code) return
      if (!finishData && !allowCodeOnly) return

      const wabaId = stringValue(finishData?.waba_id)
      const phoneNumberId = stringValue(finishData?.phone_number_id)
      if (finishData && !wabaId && !phoneNumberId) {
        settleError('Meta did not return a WhatsApp account or phone number reference.')
        return
      }

      settled = true
      cleanup()
      resolve({
        code,
        wabaId,
        phoneNumberId,
        whatsappAccountId: stringValue(finishData?.whatsapp_account_id),
        messagingAccountId: stringValue(finishData?.messaging_account_id),
      })
    }

    const scheduleCodeOnlyFallback = () => {
      if (settled || !code || finishData || sessionInfoGraceTimeout !== undefined) return
      sessionInfoGraceTimeout = window.setTimeout(
        () => settleSuccess(true),
        META_WHATSAPP_SESSION_INFO_GRACE_MS,
      )
    }

    const onMessage = (event: MessageEvent) => {
      if (!isAllowedFacebookOrigin(event.origin)) return

      const message = parseEmbeddedSignupMessage(event.data)
      if (!message || message.type !== 'WA_EMBEDDED_SIGNUP' || typeof message.event !== 'string') {
        return
      }

      const data =
        message.data && typeof message.data === 'object'
          ? (message.data as EmbeddedSignupData)
          : undefined

      if (message.event === 'FINISH' || message.event.startsWith('FINISH_')) {
        if (!data) {
          settleError('Meta completed signup without account details.')
          return
        }
        finishData = data
        settleSuccess()
        return
      }

      if (message.event === 'CANCEL') {
        settleError('WhatsApp connection was cancelled.')
        return
      }

      if (message.event === 'ERROR') {
        settleError(stringValue(data?.error_message) ?? 'Meta could not complete WhatsApp signup.')
      }
    }

    const timeout = window.setTimeout(
      () => settleError('WhatsApp connection timed out. Please try again.'),
      META_WHATSAPP_SIGNUP_TIMEOUT_MS,
    )

    window.addEventListener('message', onMessage)

    try {
      window.FB.login(
        (response) => {
          const returnedCode = stringValue(response.authResponse?.code)
          if (!returnedCode) {
            settleError(
              response.status === 'not_authorized'
                ? 'Meta authorization was not granted.'
                : 'WhatsApp connection was cancelled or the popup closed before authorization.',
            )
            return
          }

          code = returnedCode
          settleSuccess()
          scheduleCodeOnlyFallback()
        },
        {
          config_id: config.configurationId.trim(),
          auth_type: 'rerequest',
          response_type: 'code',
          override_default_response_type: true,
          extras: {
            setup: {},
            sessionInfoVersion: 3,
          },
        },
      )
    } catch {
      settleError('Unable to open Meta login. Please try again.')
    }
  })

export const __resetMetaWhatsAppSdkForTests = () => {
  sdkPromise = null
  sdkInitializedKey = null
}
