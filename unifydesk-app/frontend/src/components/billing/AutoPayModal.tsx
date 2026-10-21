import { useSnackbarState } from '../feedback/useSnackbarState'
import PhoneInput from '../ui/PhoneInput'
import { useState, type FormEvent } from 'react'
import { FiCheckCircle, FiExternalLink, FiLock, FiMail, FiShield, FiUser, FiX, FiZap } from 'react-icons/fi'
import { billingApi } from '../../features/billing/billingApi'
import { launchRazorpayMandateCheckout } from '../../features/billing/razorpayCheckout'
import type { AutoPayDetails, AutoPaySetupInput } from '../../features/billing/types'
import { autoPayStatusLabel } from '../../features/billing/types'
import { apiErrorMessage } from '../../lib/api/apiClient'

interface AutoPayModalProps {
  isOpen: boolean
  companyId: string
  companyName?: string
  initialEmail?: string
  initialPhone?: string
  onClose: () => void
  onSuccess: (details: AutoPayDetails) => void
}

export default function AutoPayModal({
  isOpen,
  companyId,
  companyName = '',
  initialEmail = '',
  initialPhone = '',
  onClose,
  onSuccess,
}: AutoPayModalProps) {
  const [name, setName] = useState(companyName)
  const [email, setEmail] = useState(initialEmail)
  const [phone, setPhone] = useState(initialPhone)
  const [submitting, setSubmitting] = useState(false)
  const [, setError] = useSnackbarState('', 'error')
  const [mandateResult, setMandateResult] = useState<AutoPayDetails | null>(null)

  if (!isOpen) return null

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!email.trim() || !phone.trim()) {
      setError('Billing email and phone number are required.')
      return
    }

    setSubmitting(true)
    setError('')
    try {
      const input: AutoPaySetupInput = {
        name: name.trim() || companyName,
        email: email.trim(),
        phone: phone.trim(),
        returnUrl: `${window.location.origin}/billing/autopay`,
        maxAmount: 15000,
      }

      const result = await billingApi.setupAutoPay(companyId, input)
      if (result.status === 'ACTIVE') {
        onSuccess(result)
        onClose()
        return
      }
      if (!result.keyId?.startsWith('rzp_')
          || !result.subscriptionSessionId?.startsWith('order_')
          || !result.customerId?.startsWith('cust_')) {
        throw new Error('Razorpay has not returned a valid authorization order and customer')
      }
      setMandateResult(result)
      if (result.providerSubscriptionId?.startsWith('token_')) {
        await handleVerifyInner()
        return
      }

      try {
          const mandateRes = await launchRazorpayMandateCheckout({
            key: result.keyId || '',
            subscriptionId: result.providerSubscriptionId || undefined,
            orderId: result.subscriptionSessionId || undefined,
            customerId: result.customerId || undefined,
            customer: {
              name: name.trim() || companyName,
              email: email.trim(),
              phone: phone.trim(),
            },
          })
          await handleVerifyInner({
            paymentId: mandateRes.razorpay_payment_id,
            orderId: mandateRes.razorpay_order_id,
            signature: mandateRes.razorpay_signature,
          })
        } catch (checkoutErr) {
          setError(checkoutErr instanceof Error ? checkoutErr.message : 'Razorpay mandate checkout was not completed')
        }
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to prepare AutoPay mandate setup.'))
    } finally {
      setSubmitting(false)
    }
  }

  const handleVerifyInner = async (verificationPayload?: { paymentId?: string; orderId?: string; signature?: string }) => {
    const result = await billingApi.verifyAutoPay(companyId, verificationPayload)
    setMandateResult(result)
    if (result.status === 'ACTIVE') {
      onSuccess(result)
      onClose()
    } else {
      setError(`AutoPay status is "${autoPayStatusLabel[result.status] ?? result.status}". If you just approved the mandate, verify again after your bank confirms it.`)
    }
  }

  const handleVerify = async () => {
    setSubmitting(true)
    setError('')
    try {
      await handleVerifyInner()
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to verify AutoPay mandate status.'))
    } finally {
      setSubmitting(false)
    }
  }

  const openMandatePopup = async () => {
    if (!mandateResult) return
    setSubmitting(true)
    setError('')
    try {
      if (mandateResult.providerSubscriptionId?.startsWith('token_')) {
        await handleVerifyInner()
        return
      }
      const mandateRes = await launchRazorpayMandateCheckout({
        key: mandateResult.keyId || '',
        subscriptionId: mandateResult.providerSubscriptionId || undefined,
        orderId: mandateResult.subscriptionSessionId || undefined,
        customerId: mandateResult.customerId || undefined,
        customer: {
          name: name.trim() || companyName,
          email: email.trim(),
          phone: phone.trim(),
        },
      })
      await handleVerifyInner({
        paymentId: mandateRes.razorpay_payment_id,
        orderId: mandateRes.razorpay_order_id,
        signature: mandateRes.razorpay_signature,
      })
    } catch (checkoutErr) {
      setError(checkoutErr instanceof Error ? checkoutErr.message : 'Mandate popup dismissed.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm animate-fadeIn">
      <div className="relative w-full max-w-lg rounded-2xl bg-slate-900 border border-slate-800 p-6 sm:p-8 shadow-2xl text-slate-100">
        {/* Close Button */}
        <button
          type="button"
          onClick={onClose}
          disabled={submitting}
          className="absolute top-4 right-4 p-2 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition cursor-pointer"
          aria-label="Close modal"
        >
          <FiX className="size-5" />
        </button>

        {/* Modal Header */}
        <div className="flex items-center gap-3 mb-5">
          <div className="flex items-center justify-center size-12 rounded-xl bg-gradient-to-tr from-indigo-600 to-purple-500 text-white shadow-lg shadow-indigo-500/25">
            <FiZap className="size-6" />
          </div>
          <div>
            <h2 className="text-xl font-bold tracking-tight text-white">Set Up AutoPay</h2>
            <p className="text-xs text-slate-400">Zero-downtime plan renewals & smart wallet top-ups</p>
          </div>
        </div>

        {/* Benefit Highlights */}
        <div className="grid grid-cols-2 gap-2 mb-6">
          <div className="p-3 rounded-xl bg-slate-800/60 border border-slate-750 flex items-start gap-2.5">
            <FiCheckCircle className="size-4 text-emerald-400 mt-0.5 shrink-0" />
            <div className="text-xs">
              <span className="font-semibold text-slate-200 block">Auto-Renewals</span>
              <span className="text-slate-400">Never miss a renewal or lose account features</span>
            </div>
          </div>
          <div className="p-3 rounded-xl bg-slate-800/60 border border-slate-750 flex items-start gap-2.5">
            <FiZap className="size-4 text-amber-400 mt-0.5 shrink-0" />
            <div className="text-xs">
              <span className="font-semibold text-slate-200 block">Wallet Top-Up</span>
              <span className="text-slate-400">Keeps message campaigns running seamlessly</span>
            </div>
          </div>
        </div>

        {/* RBI Compliance Notice */}
        <div className="mb-6 p-3.5 rounded-xl bg-indigo-950/40 border border-indigo-800/50 flex items-start gap-3">
          <FiShield className="size-5 text-indigo-400 shrink-0 mt-0.5" />
          <div className="text-xs leading-relaxed text-indigo-200">
            <strong className="text-white block mb-0.5">Razorpay Mandate Authorization</strong>
            Razorpay handles payment authorization. The requested mandate validity is up to 3 years with a ₹15,000 maximum per debit, subject to bank and provider approval. A ₹1 authorization payment may be collected.
          </div>
        </div>



        {mandateResult ? (
          /* Step 2: Mandate Authorization Ready */
          <div className="space-y-4">
            <div className="p-4 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-center">
              <FiCheckCircle className="size-8 text-emerald-400 mx-auto mb-2" />
              <h3 className="font-bold text-white text-sm">Mandate Checkout Ready</h3>
              <p className="text-xs text-slate-400 mt-1">
                Complete your optional recurring payment mandate using the Razorpay checkout. Your bank may require additional confirmation.
              </p>
            </div>

            <button
              type="button"
              onClick={() => void openMandatePopup()}
              disabled={submitting}
              className="w-full inline-flex items-center justify-center gap-2 py-3 px-4 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-semibold text-sm transition shadow-lg shadow-indigo-600/30 cursor-pointer"
            >
              <span>Authorize Mandate (Razorpay Popup)</span>
              <FiExternalLink className="size-4" />
            </button>

            <button
              type="button"
              onClick={() => void handleVerify()}
              disabled={submitting}
              className="w-full py-2.5 px-4 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 font-medium text-xs transition border border-slate-700 cursor-pointer"
            >
              {submitting ? 'Verifying status…' : 'I have approved, check mandate status'}
            </button>
          </div>
        ) : (
          /* Step 1: Billing Contact Information Form */
          <form onSubmit={(e) => void handleSubmit(e)} className="space-y-4">
            <div>
              <label htmlFor="autopay-name" className="block text-xs font-semibold text-slate-300 mb-1.5">
                Billing Entity Name
              </label>
              <div className="relative">
                <FiUser className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500 size-4" />
                <input
                  id="autopay-name"
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="Company Legal Name or Your Name"
                  className="w-full pl-10 pr-3.5 py-2.5 rounded-xl bg-slate-800/80 border border-slate-700 text-white text-xs placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  required
                />
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label htmlFor="autopay-email" className="block text-xs font-semibold text-slate-300 mb-1.5">
                  Billing Email
                </label>
                <div className="relative">
                  <FiMail className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500 size-4" />
                  <input
                    id="autopay-email"
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="billing@company.com"
                    className="w-full pl-10 pr-3.5 py-2.5 rounded-xl bg-slate-800/80 border border-slate-700 text-white text-xs placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                    required
                  />
                </div>
              </div>

              <div>
                <label htmlFor="autopay-phone" className="block text-xs font-semibold text-slate-300 mb-1.5">
                  Billing Phone
                </label>
                <div className="relative">
                  <PhoneInput
                    id="autopay-phone"
                    value={phone}
                    onChange={(value) => setPhone(value)}
                    placeholder="9876543210"
                    className="w-full pl-10 pr-3.5 py-2.5 rounded-xl bg-slate-800/80 border border-slate-700 text-white text-xs placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                    required
                  />
                </div>
              </div>
            </div>

            {/* Mandate Terms Summary */}
            <div className="p-3 rounded-xl bg-slate-800/40 border border-slate-800 text-[11px] text-slate-400 space-y-1">
              <div className="flex justify-between">
                <span>Maximum per debit:</span>
                <span className="font-semibold text-slate-200">₹15,000.00</span>
              </div>
              <div className="flex justify-between">
                <span>Mandate Validity:</span>
                <span className="font-semibold text-slate-200">Up to 3 years (subject to approval)</span>
              </div>
              <div className="flex justify-between">
                <span>Supported Methods:</span>
                <span className="font-semibold text-slate-200">Choose an available method on Razorpay</span>
              </div>
            </div>

            {/* Action Buttons */}
            <div className="pt-2 flex flex-col sm:flex-row gap-2">
              <button
                type="button"
                onClick={onClose}
                disabled={submitting}
                className="w-full sm:w-1/3 py-2.5 px-4 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 font-medium text-xs transition border border-slate-700"
              >
                Maybe Later
              </button>
              <button
                type="submit"
                disabled={submitting}
                className="w-full sm:w-2/3 py-2.5 px-4 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-semibold text-xs transition shadow-lg shadow-indigo-600/30 flex items-center justify-center gap-2"
              >
                <FiLock className="size-3.5" />
                <span>{submitting ? 'Setting up…' : 'Proceed to Authorize AutoPay'}</span>
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}
