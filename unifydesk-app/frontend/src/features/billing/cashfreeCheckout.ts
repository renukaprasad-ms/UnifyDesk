type CashfreeMode = 'sandbox' | 'production'

/** Result resolved by Cashfree SDK when modal checkout completes or is dismissed. */
export type CashfreeCheckoutResult = {
  paymentDetails?: {
    paymentMessage: string
    paymentStatus: 'SUCCESS' | 'FAILED' | 'PENDING' | 'USER_DROPPED' | string
    cf_payment_id?: string | number
    [key: string]: unknown
  }
  subscriptionDetails?: {
    paymentStatus: string
    [key: string]: unknown
  }
  [key: string]: unknown
}

type CashfreeSdk = {
  checkout: (options: {
    paymentSessionId?: string
    redirectTarget?: '_self' | '_blank' | '_modal'
  }) => Promise<CashfreeCheckoutResult>
  /** Separate method for mandate/subscription authorization flows */
  subscriptionsCheckout: (options: {
    subsSessionId: string
    redirectTarget?: '_self' | '_blank' | '_modal'
  }) => Promise<CashfreeCheckoutResult>
}

declare global {
  interface Window {
    Cashfree?: (config: { mode: CashfreeMode }) => CashfreeSdk
  }
}

const SCRIPT_ID = 'cashfree-js-v3'
const SCRIPT_SOURCE = 'https://sdk.cashfree.com/js/v3/cashfree.js'

const configuredMode = (): CashfreeMode => {
  const value = import.meta.env.VITE_CASHFREE_MODE?.trim().toLowerCase()
  if (!value) return 'sandbox'
  if (value === 'sandbox' || value === 'production') return value
  throw new Error('Cashfree checkout mode must be sandbox or production')
}

const loadCashfree = async () => {
  if (window.Cashfree) return window.Cashfree

  await new Promise<void>((resolve, reject) => {
    const existing = document.getElementById(SCRIPT_ID) as HTMLScriptElement | null
    if (existing) {
      existing.addEventListener('load', () => resolve(), { once: true })
      existing.addEventListener('error', () => reject(new Error('Unable to load Cashfree checkout')), {
        once: true,
      })
      return
    }

    const script = document.createElement('script')
    script.id = SCRIPT_ID
    script.src = SCRIPT_SOURCE
    script.async = true
    script.addEventListener('load', () => resolve(), { once: true })
    script.addEventListener('error', () => reject(new Error('Unable to load Cashfree checkout')), {
      once: true,
    })
    document.head.appendChild(script)
  })

  if (!window.Cashfree) throw new Error('Cashfree checkout did not initialize')
  return window.Cashfree
}

/**
 * Opens the Cashfree one-time payment checkout as an in-page modal popup (like Razorpay).
 * Resolves with the SDK result when the user completes or dismisses checkout — no page redirect.
 */
export const launchCashfreeCheckout = async (paymentSessionId: string): Promise<CashfreeCheckoutResult> => {
  if (!paymentSessionId) throw new Error('Payment checkout session is unavailable')
  const factory = await loadCashfree()
  const cashfree = factory({ mode: configuredMode() })
  return cashfree.checkout({ paymentSessionId, redirectTarget: '_modal' })
}

/**
 * Opens the Cashfree mandate/autopay authorization page (same-tab redirect).
 *
 * Implementation notes:
 * - Uses `cashfree.subscriptionsCheckout()` — the dedicated mandate method on the SDK.
 *   The generic `checkout()` only accepts `paymentSessionId` and will error for mandate flows.
 * - The session ID must be passed as `subsSessionId` (Cashfree's internal short name).
 * - Uses `_self` to avoid browser popup blocking that occurs when `_blank` is called
 *   after an async gap (e.g. after the setupAutoPay API call).
 * - The returnUrl configured during setup routes the user back after authorization.
 */
export const launchCashfreeSubscriptionCheckout = async (
  subscriptionSessionId: string,
): Promise<CashfreeCheckoutResult> => {
  if (!subscriptionSessionId) throw new Error('Subscription checkout session is unavailable')
  const factory = await loadCashfree()
  const cashfree = factory({ mode: configuredMode() })
  return cashfree.subscriptionsCheckout({
    subsSessionId: subscriptionSessionId,
    redirectTarget: '_self',
  })
}
