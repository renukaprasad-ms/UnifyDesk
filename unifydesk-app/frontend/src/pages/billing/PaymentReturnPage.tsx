import { useEffect, useRef, useState } from 'react'
import { FiAlertTriangle, FiCheckCircle, FiClock, FiRefreshCw, FiZap } from 'react-icons/fi'
import { Link } from 'react-router'
import AutoPayModal from '../../components/billing/AutoPayModal'
import { billingApi } from '../../features/billing/billingApi'
import { paymentReturnConfirmed } from '../../features/billing/billingModel'
import {
  clearPendingBillingCheckout,
  readPendingBillingCheckout,
  type PendingBillingCheckout,
} from '../../features/billing/pendingCheckout'
import type { CompanyPlanDetails } from '../../features/billing/types'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId, selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'

const MAX_POLLS = 6
const POLL_INTERVAL_MS = 2000

type VerificationState = 'CHECKING' | 'CONFIRMED' | 'PENDING' | 'ERROR'

export default function PaymentReturnPage() {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const epoch = useAppSelector(selectWorkspaceEpoch)
  const { capture: captureScope, isCurrent: scopeIsCurrent } = useWorkspaceEpochScope(companyId, epoch)
  const [context] = useState<PendingBillingCheckout | null>(() => readPendingBillingCheckout())
  const [details, setDetails] = useState<CompanyPlanDetails | null>(null)
  const [state, setState] = useState<VerificationState>('CHECKING')
  const [error, setError] = useState('')
  const [autoPayModalOpen, setAutoPayModalOpen] = useState(false)
  const [autoPayActive, setAutoPayActive] = useState(false)
  const runRef = useRef(0)

  const verify = async () => {
    if (!companyId) {
      setState('ERROR')
      setError('Select the workspace used for this Billing flow to verify subscription status.')
      return
    }

    const run = ++runRef.current
    const scope = captureScope()
    setState('CHECKING')
    setError('')

    for (let attempt = 0; attempt < MAX_POLLS; attempt += 1) {
      if (runRef.current !== run || !scopeIsCurrent(scope)) return
      try {
        const current = await billingApi.companyPlan(companyId)
        if (runRef.current !== run || !scopeIsCurrent(scope)) return
        setDetails(current)
        if (paymentReturnConfirmed(current, context, companyId)) {
          setState('CONFIRMED')
          clearPendingBillingCheckout()
          try {
            const ap = await billingApi.getAutoPayDetails(companyId)
            if (ap && ap.status === 'ACTIVE') setAutoPayActive(true)
          } catch (ignored) {}
          return
        }
      } catch (cause) {
        if (attempt === MAX_POLLS - 1 && runRef.current === run && scopeIsCurrent(scope)) {
          setState('ERROR')
          setError(apiErrorMessage(cause, 'Unable to verify Billing activation.'))
          return
        }
      }
      if (attempt < MAX_POLLS - 1) {
        await new Promise((resolve) => window.setTimeout(resolve, POLL_INTERVAL_MS))
      }
    }

    if (runRef.current === run && scopeIsCurrent(scope)) setState('PENDING')
  }

  useEffect(() => {
    void verify()
    return () => {
      runRef.current += 1
    }
  }, [companyId, epoch])

  const rawActionUrl = context?.companyId === companyId ? context.recurringAuthorizationActionUrl : null
  const actionUrl = (() => {
    if (!rawActionUrl) return null
    try {
      const url = new URL(rawActionUrl)
      return url.protocol === 'https:' ? url.toString() : null
    } catch {
      return null
    }
  })()

  return (
    <section className="px-4 py-10 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-2xl">
        <div className="panel p-6 sm:p-8">
          {state === 'CHECKING' && (
            <div className="text-center">
              <FiRefreshCw className="mx-auto size-8 animate-spin text-indigo-500" aria-hidden="true" />
              <h1 className="mt-4 text-2xl font-bold text-slate-950">Checking Billing activation…</h1>
              <p className="mt-2 text-sm leading-6 text-slate-500">
                The return URL is not payment confirmation. UnifyDesk is checking authoritative backend subscription state.
              </p>
            </div>
          )}
          {state === 'CONFIRMED' && (
            <div className="text-center">
              <FiCheckCircle className="mx-auto size-9 text-emerald-500" aria-hidden="true" />
              <h1 className="mt-4 text-2xl font-bold text-slate-950">Subscription activated</h1>
              <p className="mt-2 text-sm leading-6 text-slate-500">
                Authoritative backend Billing state now shows the selected subscription as active.
              </p>
              {details?.subscription && (
                <div className="mt-5 rounded-xl bg-slate-50 p-4 text-left text-sm">
                  <p className="font-bold text-slate-950">{details.subscription.planName}</p>
                  <p className="mt-1 text-xs text-slate-500">
                    Subscription status: {details.subscription.status.replaceAll('_', ' ')}
                  </p>
                </div>
              )}

              {!autoPayActive ? (
                <div className="mt-6 rounded-2xl border border-indigo-200 bg-gradient-to-br from-indigo-50/70 to-blue-50/50 p-5 text-left shadow-sm">
                  <div className="flex items-start gap-3">
                    <div className="flex items-center justify-center size-10 rounded-xl bg-indigo-600 text-white shadow-md shadow-indigo-600/20 shrink-0">
                      <FiZap className="size-5" />
                    </div>
                    <div className="flex-1">
                      <h3 className="font-bold text-indigo-950 text-sm">Set up AutoPay (Optional)</h3>
                      <p className="mt-1 text-xs text-indigo-800 leading-relaxed">
                        Never worry about expiring plans or depleted wallet balances. Authorize once via UPI AutoPay or Netbanking to enable zero-downtime renewals and automatic top-ups.
                      </p>
                      <div className="mt-3.5 flex items-center gap-2">
                        <button
                          type="button"
                          onClick={() => setAutoPayModalOpen(true)}
                          className="px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-semibold text-xs transition shadow-sm inline-flex items-center gap-1.5"
                        >
                          <FiZap className="size-3.5" />
                          <span>Set Up AutoPay (Razorpay)</span>
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              ) : (
                <div className="mt-6 rounded-xl border border-emerald-200 bg-emerald-50/60 p-4 text-left flex items-center gap-3">
                  <FiCheckCircle className="size-5 text-emerald-600 shrink-0" />
                  <div className="text-xs text-emerald-900 font-medium">
                    AutoPay is active on your account. Future renewals will be processed automatically.
                  </div>
                </div>
              )}
            </div>
          )}
          {state === 'PENDING' && (
            <div className="text-center">
              <FiClock className="mx-auto size-9 text-amber-500" aria-hidden="true" />
              <h1 className="mt-4 text-2xl font-bold text-slate-950">Billing activation is being verified</h1>
              <p className="mt-2 text-sm leading-6 text-slate-500">
                Conclusive activation is not available from authoritative backend subscription state yet. Automatic checks stopped after a bounded verification window.
              </p>
            </div>
          )}
          {state === 'ERROR' && (
            <div className="text-center">
              <FiAlertTriangle className="mx-auto size-9 text-rose-500" aria-hidden="true" />
              <h1 className="mt-4 text-2xl font-bold text-slate-950">Unable to verify Billing activation</h1>
              <p className="mt-2 text-sm leading-6 text-slate-500">{error}</p>
            </div>
          )}
          {state !== 'CHECKING' && actionUrl && state !== 'ERROR' && (
            <div className="mt-6 rounded-xl border border-indigo-100 bg-indigo-50 p-4">
              <p className="text-sm font-bold text-indigo-950">Automatic renewal authorization</p>
              <p className="mt-1 text-xs leading-5 text-indigo-800">
                The backend checkout response created a separate renewal authorization continuation. This link itself is not proof that authorization succeeded.
              </p>
              <a className="secondary-button mt-3 inline-flex" href={actionUrl} rel="noreferrer">
                Continue renewal authorization
              </a>
            </div>
          )}
          <div className="mt-7 flex flex-col gap-2 sm:flex-row sm:justify-center">
            {state !== 'CHECKING' && state !== 'CONFIRMED' && (
              <button type="button" className="secondary-button justify-center" onClick={() => void verify()}>
                Check again
              </button>
            )}
            <Link className="primary-button justify-center" to="/company/plan">Back to Billing</Link>
          </div>
        </div>
      </div>

      {companyId && (
        <AutoPayModal
          isOpen={autoPayModalOpen}
          companyId={companyId}
          onClose={() => setAutoPayModalOpen(false)}
          onSuccess={(details) => {
            if (details.status === 'ACTIVE') {
              setAutoPayActive(true)
            }
          }}
        />
      )}
    </section>
  )
}
