import { afterEach, describe, expect, it, vi } from 'vitest'
import { launchRazorpayMandateCheckout, type RazorpayOptions } from './razorpayCheckout'

afterEach(() => {
  delete window.Razorpay
})

describe('Razorpay recurring checkout', () => {
  it('requires the provider order and customer before opening the SDK', async () => {
    await expect(
      launchRazorpayMandateCheckout({ key: 'rzp_test_example', orderId: 'order_example' }),
    ).rejects.toThrow('valid authorization order and customer')
  })

  it('passes the saved customer and authorization order to recurring checkout', async () => {
    let captured: RazorpayOptions | undefined
    const open = vi.fn()
    window.Razorpay = class {
      constructor(options: RazorpayOptions) {
        captured = options
      }
      open = open
      on = vi.fn()
      close = vi.fn()
    }
    const pending = launchRazorpayMandateCheckout({
      key: 'rzp_test_example',
      orderId: 'order_authorization',
      customerId: 'cust_customer',
    })
    await vi.waitFor(() => expect(open).toHaveBeenCalledOnce())
    expect(captured).toMatchObject({
      order_id: 'order_authorization',
      customer_id: 'cust_customer',
      recurring: '1',
    })
    const response = {
      razorpay_order_id: 'order_authorization',
      razorpay_payment_id: 'pay_authorization',
      razorpay_signature: 'signature',
    }
    captured!.handler!(response)
    await expect(pending).resolves.toEqual(response)
  })
})
