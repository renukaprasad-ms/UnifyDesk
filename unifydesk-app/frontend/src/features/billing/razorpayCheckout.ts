export type RazorpayPaymentSuccess = {
  razorpay_payment_id: string
  razorpay_order_id: string
  razorpay_signature: string
}

export type RazorpayOptions = {
  key: string
  order_id?: string
  amount: number // in paise
  currency?: string
  name?: string
  description?: string
  image?: string
  prefill?: {
    name?: string
    email?: string
    contact?: string
  }
  notes?: Record<string, string>
  theme?: {
    color?: string
    backdrop_color?: string
  }
  modal?: {
    ondismiss?: () => void
    confirm_close?: boolean
    animation?: boolean
  }
  handler?: (response: RazorpayPaymentSuccess) => void
  subscription_id?: string
  customer_id?: string
  recurring?: string
}

export interface RazorpayInstance {
  open: () => void
  on: (event: string, callback: (response: unknown) => void) => void
  close: () => void
}

declare global {
  interface Window {
    Razorpay?: new (options: RazorpayOptions) => RazorpayInstance
  }
}

const SCRIPT_ID = 'razorpay-checkout-js'
const SCRIPT_SRC = 'https://checkout.razorpay.com/v1/checkout.js'

export const loadRazorpayScript = async (): Promise<boolean> => {
  if (window.Razorpay) return true

  return new Promise((resolve, reject) => {
    const existing = document.getElementById(SCRIPT_ID) as HTMLScriptElement | null
    if (existing) {
      existing.addEventListener('load', () => resolve(true), { once: true })
      existing.addEventListener('error', () => reject(new Error('Failed to load Razorpay SDK')), {
        once: true,
      })
      return
    }

    const script = document.createElement('script')
    script.id = SCRIPT_ID
    script.src = SCRIPT_SRC
    script.async = true
    script.onload = () => resolve(true)
    script.onerror = () => reject(new Error('Failed to load Razorpay SDK script'))
    document.body.appendChild(script)
  })
}

export type LaunchRazorpayParams = {
  key: string
  orderId?: string
  amount: number // in Rupees or paise
  amountInPaise?: number
  currency?: string
  name?: string
  description?: string
  customer?: {
    name?: string
    email?: string
    phone?: string
  }
  notes?: Record<string, string>
}

/**
 * Opens the in-page Razorpay SDK popup modal (never redirects).
 * Resolves with the Razorpay response containing razorpay_payment_id, razorpay_order_id, razorpay_signature.
 */
export const launchRazorpayCheckout = async (
  params: LaunchRazorpayParams,
): Promise<RazorpayPaymentSuccess> => {
  await loadRazorpayScript()

  const RazorpayClass = window.Razorpay
  if (!RazorpayClass) {
    throw new Error('Razorpay SDK failed to initialize')
  }

  const calculatedPaise =
    params.amountInPaise && params.amountInPaise > 0
      ? params.amountInPaise
      : Math.round(params.amount * 100)

  return new Promise<RazorpayPaymentSuccess>((resolve, reject) => {
    let resolved = false

    const options: RazorpayOptions = {
      key: params.key || import.meta.env.VITE_RAZORPAY_KEY_ID || 'rzp_test_placeholder',
      amount: calculatedPaise,
      currency: params.currency || 'INR',
      name: params.name || 'UnifyDesk',
      description: params.description || 'Plan Subscription & Balance',
      image: '/favicon.ico',
      prefill: {
        name: params.customer?.name || '',
        email: params.customer?.email || '',
        contact: params.customer?.phone || '',
      },
      notes: params.notes || {},
      theme: {
        color: '#6366f1',
      },
      modal: {
        ondismiss: () => {
          if (!resolved) {
            reject(new Error('Payment was cancelled or popup closed by user.'))
          }
        },
      },
      handler: (response: RazorpayPaymentSuccess) => {
        resolved = true
        resolve(response)
      },
    }

    if (params.orderId && params.orderId.trim().startsWith('order_')) {
      options.order_id = params.orderId.trim()
    }

    try {
      const rzp = new RazorpayClass(options)
      rzp.open()
    } catch (err) {
      reject(err)
    }
  })
}

/**
 * Opens Razorpay recurring mandate authorization popup
 */
export const launchRazorpayMandateCheckout = async (params: {
  key: string
  subscriptionId?: string
  orderId?: string
  customerId?: string
  customer?: { name?: string; email?: string; phone?: string }
}): Promise<RazorpayPaymentSuccess> => {
  const orderId = params.orderId?.trim()
  const customerId = params.customerId?.trim()
  if (!params.key || !params.key.startsWith('rzp_')) {
    throw new Error('Razorpay checkout is not configured')
  }
  if (!orderId?.startsWith('order_') || !customerId?.startsWith('cust_')) {
    throw new Error('Razorpay mandate setup requires a valid authorization order and customer')
  }
  await loadRazorpayScript()

  const RazorpayClass = window.Razorpay
  if (!RazorpayClass) throw new Error('Razorpay SDK failed to initialize')

  return new Promise<RazorpayPaymentSuccess>((resolve, reject) => {
    let completed = false
    const options: RazorpayOptions = {
      key: params.key,
      order_id: orderId,
      customer_id: customerId,
      recurring: '1',
      amount: 100,
      currency: 'INR',
      name: 'UnifyDesk AutoPay',
      description: 'Authorize optional recurring payments. Review the mandate terms before approval.',
      prefill: {
        name: params.customer?.name || '',
        email: params.customer?.email || '',
        contact: params.customer?.phone || '',
      },
      theme: { color: '#6366f1' },
      modal: {
        ondismiss: () => {
          if (!completed) reject(new Error('Mandate setup was cancelled'))
        },
      },
      handler: (response) => {
        if (completed) return
        if (response.razorpay_order_id !== orderId
            || !response.razorpay_payment_id
            || !response.razorpay_signature) {
          reject(new Error('Razorpay mandate verification data is incomplete'))
          return
        }
        completed = true
        resolve(response)
      },
    }
    try {
      new RazorpayClass(options).open()
    } catch (error) {
      reject(error)
    }
  })
}
