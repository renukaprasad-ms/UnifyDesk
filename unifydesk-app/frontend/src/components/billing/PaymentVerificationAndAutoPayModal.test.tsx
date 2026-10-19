import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SnackbarProvider } from '../feedback/SnackbarProviderComponent'
import { billingApi } from '../../features/billing/billingApi'
import { launchRazorpayMandateCheckout } from '../../features/billing/razorpayCheckout'
import PaymentVerificationAndAutoPayModal from './PaymentVerificationAndAutoPayModal'

vi.mock('../../features/billing/billingApi', () => ({
  billingApi: {
    verifyPayment: vi.fn().mockResolvedValue({ status: 'SUCCEEDED' }),
    setupAutoPay: vi.fn(),
    verifyAutoPay: vi.fn(),
  },
}))
vi.mock('../../features/realtime/realtimeClient', () => ({
  realtimeClient: { subscribe: () => () => {} },
}))
vi.mock('../../features/billing/razorpayCheckout', () => ({
  launchRazorpayMandateCheckout: vi.fn(),
}))
afterEach(() => {
  vi.useRealTimers()
  vi.clearAllMocks()
})

describe('AutoPay authorization status', () => {
  it('keeps a pending authorization at setup instead of treating it as active', async () => {
    vi.useFakeTimers()
    const pending = {
      status: 'PENDING_AUTH',
      keyId: 'rzp_test_example',
      subscriptionSessionId: 'order_authorization',
      customerId: 'cust_customer',
    }
    vi.mocked(billingApi.setupAutoPay).mockResolvedValue(pending as never)
    vi.mocked(billingApi.verifyAutoPay).mockResolvedValue(pending as never)
    vi.mocked(launchRazorpayMandateCheckout).mockResolvedValue({
      razorpay_order_id: 'order_authorization',
      razorpay_payment_id: 'pay_authorization',
      razorpay_signature: 'signature',
    })
    render(
      <SnackbarProvider>
        <PaymentVerificationAndAutoPayModal
          isOpen
          companyId="10"
          onDone={vi.fn()}
          paymentResult={{
            razorpay_order_id: 'order_payment',
            razorpay_payment_id: 'pay_payment',
            razorpay_signature: 'signature',
          }}
        />
      </SnackbarProvider>,
    )
    await act(async () => {})
    await act(async () => vi.advanceTimersByTime(7000))
    await act(async () => vi.advanceTimersByTime(1200))
    await act(async () => fireEvent.click(screen.getByRole('button', { name: /Setup AutoPay/ })))
    expect(launchRazorpayMandateCheckout).toHaveBeenCalledWith(
      expect.objectContaining({ orderId: 'order_authorization', customerId: 'cust_customer' }),
    )
    expect(screen.getByRole('alert')).toHaveTextContent('AutoPay authorization is pending')
    expect(screen.getByRole('button', { name: /Setup AutoPay/ })).toBeEnabled()
  })
})
