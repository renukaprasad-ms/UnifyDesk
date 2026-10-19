import { useEffect, useRef, useState } from 'react'
import {
  FiAlertCircle,
  FiArrowRight,
  FiCheck,
  FiCheckCircle,
  FiClock,
  FiCreditCard,
  FiLoader,
  FiShield,
  FiZap,
} from 'react-icons/fi'
import { billingApi } from '../../features/billing/billingApi'
import {
  launchRazorpayMandateCheckout,
  type RazorpayPaymentSuccess,
} from '../../features/billing/razorpayCheckout'
import type { AutoPayDetails } from '../../features/billing/types'
import { realtimeClient } from '../../features/realtime/realtimeClient'
import { apiErrorMessage } from '../../lib/api/apiClient'

interface PaymentVerificationAndAutoPayModalProps {
  isOpen: boolean
  companyId: string
  companyName?: string
  paymentResult?: RazorpayPaymentSuccess | null
  customerEmail?: string
  customerPhone?: string
  onDone: () => void
}

type VerificationStep =
  | 'VERIFYING_SIGNATURE'
  | 'WAITING_GATEWAY_WS'
  | 'VERIFIED'
  | 'AUTOPAY_PROMPT'
  | 'AUTOPAY_PROCESSING'
  | 'AUTOTOPUP_SETUP'
  | 'COMPLETED'

export default function PaymentVerificationAndAutoPayModal({
  isOpen,
  companyId,
  companyName = '',
  paymentResult,
  customerEmail = '',
  customerPhone = '',
  onDone,
}: PaymentVerificationAndAutoPayModalProps) {
  const [step, setStep] = useState<VerificationStep>('VERIFYING_SIGNATURE')
  const [error, setError] = useState<string>('')
  const [countdown, setCountdown] = useState<number>(7)
  const [autoPayDetails, setAutoPayDetails] = useState<AutoPayDetails | null>(null)
  const [isSettingUpAutoPay, setIsSettingUpAutoPay] = useState(false)
  const [billingEmail, setBillingEmail] = useState(customerEmail)
  const [billingPhone, setBillingPhone] = useState(customerPhone)
  const [threshold, setThreshold] = useState<number>(500)
  const [rechargeAmount, setRechargeAmount] = useState<number>(2000)
  const [autoRechargeEnabled, setAutoRechargeEnabled] = useState<boolean>(true)
  const [savingTopupRule, setSavingTopupRule] = useState<boolean>(false)
  const wsReceivedRef = useRef(false)

  useEffect(() => {
    if (isOpen) {
      setBillingEmail(customerEmail)
      setBillingPhone(customerPhone)
    }
  }, [isOpen, customerEmail, customerPhone])

  useEffect(() => {
    if (!isOpen || !paymentResult) return

    let isMounted = true
    wsReceivedRef.current = false
    setStep('VERIFYING_SIGNATURE')
    setError('')
    setCountdown(7)

    // Listen for WebSocket confirmation from backend
    const unsubscribeWs = realtimeClient.subscribe<{
      orderId?: string
      paymentId?: string
      status?: string
    }>('billing.payment.updated', (event) => {
      const payload = event?.payload
      if (
        payload?.orderId === paymentResult.razorpay_order_id ||
        payload?.paymentId === paymentResult.razorpay_payment_id ||
        payload?.status === 'SUCCEEDED'
      ) {
        wsReceivedRef.current = true
        if (isMounted) {
          setStep('VERIFIED')
          setTimeout(() => {
            if (isMounted) setStep('AUTOPAY_PROMPT')
          }, 1200)
        }
      }
    })

    // Step 1: Call Backend Verification API
    const runVerification = async () => {
      try {
        await billingApi.verifyPayment(companyId, {
          paymentId: paymentResult.razorpay_order_id,
          razorpayOrderId: paymentResult.razorpay_order_id,
          razorpayPaymentId: paymentResult.razorpay_payment_id,
          razorpaySignature: paymentResult.razorpay_signature,
        })

        if (!isMounted) return

        // Step 2: Show "waiting for confirmation from bank or gateway..." for 5-10 seconds
        setStep('WAITING_GATEWAY_WS')

        let remaining = 7
        const timer = setInterval(() => {
          remaining -= 1
          if (isMounted) setCountdown(remaining)
          if (remaining <= 0 || wsReceivedRef.current) {
            clearInterval(timer)
            if (isMounted && step !== 'AUTOPAY_PROMPT' && step !== 'COMPLETED') {
              setStep('VERIFIED')
              setTimeout(() => {
                if (isMounted) setStep('AUTOPAY_PROMPT')
              }, 1200)
            }
          }
        }, 1000)

        return () => clearInterval(timer)
      } catch (err) {
        if (isMounted) {
          setError(apiErrorMessage(err, 'Failed to verify payment with gateway.'))
        }
      }
    }

    void runVerification()

    return () => {
      isMounted = false
      unsubscribeWs()
    }
  }, [isOpen, paymentResult, companyId])

  if (!isOpen) return null

  const handleAuthorizeAutoPay = async () => {
    const email = billingEmail.trim()
    const phone = billingPhone.replace(/\\D/g, '').replace(/^91(?=\\d{10}$)/, '')
    if (!email.includes('@') || !/^[6-9]\\d{9}$/.test(phone)) {
      setError('Enter your billing email and a valid 10-digit Indian mobile number to authorize AutoPay.')
      return
    }

    setIsSettingUpAutoPay(true)
    setError('')
    try {
      const setup = await billingApi.setupAutoPay(companyId, {
        name: companyName || 'Company Admin',
        email,
        phone,
        maxAmount: 15000,
      })
      // A local mandate reference cannot be used for Razorpay Checkout.
      if (!setup.keyId?.startsWith('rzp_')
        || !setup.subscriptionSessionId?.startsWith('order_')
        || !setup.customerId?.startsWith('cust_')) {
        throw new Error('Razorpay has not provided a valid authorization order and customer. Please retry setup.')
      }
      setAutoPayDetails(setup)
      const mandateRes = await launchRazorpayMandateCheckout({
        key: setup.keyId,
        orderId: setup.subscriptionSessionId,
        customerId: setup.customerId,
        customer: { name: companyName, email, phone },
      })
      const verified = await billingApi.verifyAutoPay(companyId, {
        paymentId: mandateRes.razorpay_payment_id,
        orderId: mandateRes.razorpay_order_id,
        signature: mandateRes.razorpay_signature,
      })
      setAutoPayDetails(verified)
      if (verified.status !== 'ACTIVE') {
        setError('Razorpay mandate is still awaiting confirmation. Check its status in Billing before enabling automatic top-ups.')
        return
      }
      setStep('AUTOTOPUP_SETUP')
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not initialize AutoPay setup.'))
    } finally {
      setIsSettingUpAutoPay(false)
    }
  }

  const handleSaveTopupRule = async () => {
    setSavingTopupRule(true)
    setError('')
    try {
      if (autoRechargeEnabled) {
        await billingApi.configureAutoRecharge(companyId, {
          enabled: true,
          threshold: threshold,
          amount: rechargeAmount,
        })
      }
      try {
        localStorage.setItem('unifydesk_autopay_banner_dismissed', 'true')
      } catch {
        // ignore
      }
      onDone()
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to save auto-topup rule.'))
    } finally {
      setSavingTopupRule(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm animate-fade-in">
      <div className="relative w-full max-w-lg rounded-2xl bg-slate-900 border border-slate-800 p-6 sm:p-8 shadow-2xl text-slate-100">
        {/* Glow header highlight */}
        <div className="absolute -top-12 left-1/2 -translate-x-1/2 w-48 h-24 bg-indigo-500/20 blur-3xl rounded-full pointer-events-none" />

        {/* State 1: Verifying signature */}
        {step === 'VERIFYING_SIGNATURE' && (
          <div className="flex flex-col items-center text-center py-6">
            <div className="relative mb-5 flex h-16 w-16 items-center justify-center rounded-2xl bg-indigo-500/10 border border-indigo-500/20 text-indigo-400">
              <FiLoader className="h-8 w-8 animate-spin" />
            </div>
            <h3 className="text-xl font-bold text-white">Verifying Transaction</h3>
            <p className="mt-2 text-sm text-slate-400 max-w-sm">
              Checking cryptographic signature with Razorpay backend...
            </p>
          </div>
        )}

        {/* State 2: Waiting for bank / websocket confirmation */}
        {step === 'WAITING_GATEWAY_WS' && (
          <div className="flex flex-col items-center text-center py-6">
            <div className="relative mb-5 flex h-16 w-16 items-center justify-center rounded-2xl bg-amber-500/10 border border-amber-500/20 text-amber-400">
              <FiClock className="h-8 w-8 animate-pulse" />
              <span className="absolute -top-1 -right-1 flex h-4 w-4">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-amber-400 opacity-75"></span>
                <span className="relative inline-flex rounded-full h-4 w-4 bg-amber-500 text-[9px] font-bold text-slate-950 items-center justify-center">
                  {countdown}
                </span>
              </span>
            </div>
            <h3 className="text-xl font-bold text-white">Confirming with Bank & Gateway</h3>
            <p className="mt-2 text-sm text-slate-400 max-w-sm">
              Waiting for confirmation from bank or gateway... Two-way verification in progress.
            </p>
            <div className="mt-6 flex items-center gap-2 rounded-full bg-slate-800/80 px-4 py-1.5 text-xs text-slate-300 border border-slate-700/60">
              <span className="h-2 w-2 rounded-full bg-amber-400 animate-ping" />
              Real-time payment sync active ({countdown}s)
            </div>
          </div>
        )}

        {/* State 3: Payment Verified checkmark */}
        {step === 'VERIFIED' && (
          <div className="flex flex-col items-center text-center py-6 animate-scale-in">
            <div className="mb-5 flex h-16 w-16 items-center justify-center rounded-2xl bg-emerald-500/15 border border-emerald-500/30 text-emerald-400">
              <FiCheckCircle className="h-10 w-10 text-emerald-400 animate-bounce" />
            </div>
            <h3 className="text-xl font-bold text-white">Payment Confirmed!</h3>
            <p className="mt-2 text-sm text-slate-400 max-w-sm">
              Your payment has been successfully recorded and verified by the gateway.
            </p>
          </div>
        )}

        {/* State 4: Optional AutoPay Mandate Setup Prompt */}
        {step === 'AUTOPAY_PROMPT' && (
          <div className="flex flex-col text-left py-2">
            <div className="flex items-center gap-3 mb-4">
              <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-500 to-purple-600 text-white shadow-lg shadow-indigo-500/25">
                <FiZap className="h-6 w-6" />
              </div>
              <div>
                <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-emerald-400 uppercase tracking-wider">
                  <FiCheck className="h-3 w-3" /> Payment Complete
                </span>
                <h3 className="text-xl font-bold text-white">Setup AutoPay for Hands-Free Renewals</h3>
              </div>
            </div>

            <p className="text-sm text-slate-300 leading-relaxed mb-4">
              Never let your messaging channels disconnect. Set up an automated recurring payment mandate for future plan renewals and automatic wallet top-ups.
            </p>

            <div className="space-y-3 rounded-xl bg-slate-800/50 border border-slate-700/70 p-4 text-xs text-slate-300 mb-6">
              <div className="flex items-start gap-2.5">
                <FiShield className="h-4 w-4 text-indigo-400 shrink-0 mt-0.5" />
                <div>
                  <span className="font-semibold text-slate-100">₹1 authorization payment:</span> Razorpay uses a small authorization payment for mandate registration. Future debits require an active, approved mandate.
                </div>
              </div>
              <div className="flex items-start gap-2.5">
                <FiCreditCard className="h-4 w-4 text-indigo-400 shrink-0 mt-0.5" />
                <div>
                  <span className="font-semibold text-slate-100">Mandate terms:</span> Requested validity is up to 3 years, with a <strong className="text-indigo-300">₹15,000</strong> maximum per debit, subject to your bank and Razorpay.
                </div>
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-4">
              <label className="text-xs text-slate-300">
                Billing email
                <input type="email" required value={billingEmail}
                  onChange={(event) => setBillingEmail(event.target.value)}
                  placeholder="billing@company.com"
                  className="mt-1 w-full rounded-lg bg-slate-800 border border-slate-700 px-3 py-2 text-white"
                />
              </label>
              <label className="text-xs text-slate-300">
                Billing mobile number
                <input type="tel" required value={billingPhone}
                  onChange={(event) => setBillingPhone(event.target.value)}
                  placeholder="10-digit mobile number"
                  className="mt-1 w-full rounded-lg bg-slate-800 border border-slate-700 px-3 py-2 text-white"
                />
              </label>
            </div>

            {error && (
              <div className="mb-4 flex items-center gap-2 rounded-lg bg-red-500/10 border border-red-500/20 p-3 text-xs text-red-400">
                <FiAlertCircle className="h-4 w-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <div className="flex flex-col sm:flex-row items-center gap-3">
              <button
                type="button"
                onClick={handleAuthorizeAutoPay}
                disabled={isSettingUpAutoPay}
                className="w-full sm:flex-1 inline-flex items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-indigo-500 to-purple-600 px-5 py-3 text-sm font-semibold text-white shadow-lg shadow-indigo-500/25 hover:from-indigo-600 hover:to-purple-700 focus:outline-none focus:ring-2 focus:ring-indigo-400 disabled:opacity-50 transition-all cursor-pointer"
              >
                {isSettingUpAutoPay ? (
                  <>
                    <FiLoader className="h-4 w-4 animate-spin" />
                    Opening Razorpay...
                  </>
                ) : (
                  <>
                    <FiZap className="h-4 w-4" />
                    Setup AutoPay (SDK Popup)
                  </>
                )}
              </button>

              <button
                type="button"
                onClick={onDone}
                disabled={isSettingUpAutoPay}
                className="w-full sm:w-auto inline-flex items-center justify-center gap-1.5 rounded-xl bg-slate-800 px-5 py-3 text-sm font-semibold text-slate-300 hover:bg-slate-700 hover:text-white transition-all cursor-pointer"
              >
                Skip & Enter Dashboard
                <FiArrowRight className="h-4 w-4" />
              </button>
            </div>
          </div>
        )}

        {/* State 5: Auto-Topup Rule Setup with the Active Mandate */}
        {step === 'AUTOTOPUP_SETUP' && (
          <div className="flex flex-col text-left py-2 animate-scale-in">
            <div className="flex items-center gap-3 mb-4">
              <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-emerald-500 to-teal-600 text-white shadow-lg shadow-emerald-500/25">
                <FiCheckCircle className="h-6 w-6" />
              </div>
              <div>
                <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-emerald-400 uppercase tracking-wider">
                  Mandate Active (₹15,000 / mo)
                </span>
                <h3 className="text-xl font-bold text-white">Enable Wallet Auto-Topup</h3>
              </div>
            </div>

            <p className="text-sm text-slate-300 leading-relaxed mb-4">
              Your recurring mandate is active! Use the same mandate to automatically refill your Communications Wallet so WhatsApp, SMS, and Email campaigns never pause.
            </p>

            <div className="rounded-xl bg-slate-800/60 border border-slate-700/70 p-4 space-y-4 mb-6">
              <div className="flex items-center justify-between">
                <div>
                  <div className="text-sm font-semibold text-white">Communications Auto-Recharge</div>
                  <div className="text-xs text-slate-400">Charged automatically via your active mandate</div>
                </div>
                <label className="relative inline-flex items-center cursor-pointer">
                  <input
                    type="checkbox"
                    checked={autoRechargeEnabled}
                    onChange={(e) => setAutoRechargeEnabled(e.target.checked)}
                    className="sr-only peer"
                  />
                  <div className="w-11 h-6 bg-slate-700 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-emerald-500" />
                </label>
              </div>

              {autoRechargeEnabled && (
                <div className="grid grid-cols-2 gap-3 pt-2 border-t border-slate-700/50">
                  <div>
                    <label className="block text-xs font-medium text-slate-400 mb-1">When balance falls below:</label>
                    <div className="relative">
                      <span className="absolute left-3 top-2.5 text-xs text-slate-500 font-semibold">₹</span>
                      <input
                        type="number"
                        value={threshold}
                        onChange={(e) => setThreshold(Number(e.target.value) || 0)}
                        min={100}
                        step={100}
                        className="w-full rounded-lg bg-slate-900 border border-slate-700 pl-7 pr-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
                      />
                    </div>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-slate-400 mb-1">Automatically add:</label>
                    <div className="relative">
                      <span className="absolute left-3 top-2.5 text-xs text-slate-500 font-semibold">₹</span>
                      <input
                        type="number"
                        value={rechargeAmount}
                        onChange={(e) => setRechargeAmount(Number(e.target.value) || 0)}
                        min={500}
                        step={500}
                        className="w-full rounded-lg bg-slate-900 border border-slate-700 pl-7 pr-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
                      />
                    </div>
                  </div>
                </div>
              )}
            </div>

            <div className="flex flex-col sm:flex-row items-center gap-3">
              <button
                type="button"
                onClick={handleSaveTopupRule}
                disabled={savingTopupRule}
                className="w-full sm:flex-1 inline-flex items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-emerald-500 to-teal-600 px-5 py-3 text-sm font-semibold text-white shadow-lg shadow-emerald-500/25 hover:from-emerald-600 hover:to-teal-700 focus:outline-none disabled:opacity-50 transition-all cursor-pointer"
              >
                {savingTopupRule ? (
                  <>
                    <FiLoader className="h-4 w-4 animate-spin" />
                    Saving Rule...
                  </>
                ) : (
                  <>
                    <FiCheck className="h-4 w-4" />
                    Save Rule & Enter Dashboard
                  </>
                )}
              </button>
              <button
                type="button"
                onClick={onDone}
                disabled={savingTopupRule}
                className="w-full sm:w-auto inline-flex items-center justify-center gap-1.5 rounded-xl bg-slate-800 px-5 py-3 text-sm font-semibold text-slate-300 hover:bg-slate-700 hover:text-white transition-all cursor-pointer"
              >
                Skip for Now
                <FiArrowRight className="h-4 w-4" />
              </button>
            </div>
          </div>
        )}

        {/* State 6: AutoPay Completed */}
        {step === 'COMPLETED' && (
          <div className="flex flex-col items-center text-center py-6 animate-scale-in">
            <div className="mb-5 flex h-16 w-16 items-center justify-center rounded-2xl bg-emerald-500/15 border border-emerald-500/30 text-emerald-400">
              <FiCheckCircle className="h-10 w-10 text-emerald-400" />
            </div>
            <h3 className="text-xl font-bold text-white">AutoPay Successfully Configured!</h3>
            <p className="mt-2 text-sm text-slate-400 max-w-sm">
              Your 20-year recurring mandate (up to ₹15,000) is now active. Future renewals and automated wallet recharges will process effortlessly.
            </p>
            {autoPayDetails?.maxAmount && (
              <p className="mt-1 text-xs text-indigo-400 font-medium">
                Mandate Limit: ₹{autoPayDetails.maxAmount.toLocaleString()}
              </p>
            )}
            <button
              type="button"
              onClick={onDone}
              className="mt-6 inline-flex items-center justify-center gap-2 rounded-xl bg-indigo-600 px-6 py-2.5 text-sm font-semibold text-white hover:bg-indigo-500 transition-all cursor-pointer shadow-lg shadow-indigo-600/30"
            >
              Enter Dashboard
              <FiArrowRight className="h-4 w-4" />
            </button>
          </div>
        )}

        {error && step !== 'AUTOPAY_PROMPT' && (
          <div className="mt-4 flex flex-col items-center text-center">
            <div className="flex items-center gap-2 rounded-lg bg-red-500/10 border border-red-500/20 p-3 text-xs text-red-400 w-full mb-4">
              <FiAlertCircle className="h-4 w-4 shrink-0" />
              <span className="text-left">{error}</span>
            </div>
            <button
              type="button"
              onClick={onDone}
              className="rounded-xl bg-slate-800 px-5 py-2 text-xs font-semibold text-slate-300 hover:bg-slate-700 hover:text-white transition-all"
            >
              Continue to Dashboard
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
