type CashfreeMode = 'sandbox' | 'production'

type CashfreeSdk = {
  checkout: (options: { paymentSessionId: string; redirectTarget: '_self' }) => Promise<unknown>
}

declare global {
  interface Window {
    Cashfree?: (config: { mode: CashfreeMode }) => CashfreeSdk
  }
}

const SCRIPT_ID = 'cashfree-js-v3'
const SCRIPT_SOURCE = 'https://sdk.cashfree.com/js/v3/cashfree.js'

const configuredMode = (): CashfreeMode => {
  const value = import.meta.env.VITE_CASHFREE_MODE
  if (value === 'sandbox' || value === 'production') return value
  throw new Error('Cashfree checkout mode is not configured')
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

export const launchCashfreeCheckout = async (paymentSessionId: string) => {
  if (!paymentSessionId) throw new Error('Payment checkout session is unavailable')
  const factory = await loadCashfree()
  const cashfree = factory({ mode: configuredMode() })
  await cashfree.checkout({ paymentSessionId, redirectTarget: '_self' })
}
