import { useEffect, useRef, useState } from 'react'
import { FiAlertTriangle, FiCheckCircle, FiClock, FiRefreshCw } from 'react-icons/fi'
import { Link } from 'react-router'
import { billingApi } from '../../features/billing/billingApi'
import { paymentReturnConfirmed } from '../../features/billing/billingModel'
import { clearPendingBillingCheckout, readPendingBillingCheckout, type PendingBillingCheckout } from '../../features/billing/pendingCheckout'
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
  const runRef = useRef(0)

  const verify = async () => {
    if (!companyId) { setState('ERROR'); setError('Select the workspace used for this payment to verify Billing status.'); return }
    const run = ++runRef.current
    const scope = captureScope()
    setState('CHECKING'); setError('')
    for (let attempt = 0; attempt < MAX_POLLS; attempt += 1) {
      if (runRef.current !== run || !scopeIsCurrent(scope)) return
      try {
        const current = await billingApi.companyPlan(companyId)
        if (runRef.current !== run || !scopeIsCurrent(scope)) return
        setDetails(current)
        const correlated = context?.companyId === companyId ? context : null
        if (paymentReturnConfirmed(current, correlated)) {
          setState('CONFIRMED'); clearPendingBillingCheckout(); return
        }
      } catch (cause) {
        if (attempt === MAX_POLLS - 1 && runRef.current === run && scopeIsCurrent(scope)) {
          setState('ERROR'); setError(apiErrorMessage(cause, 'Unable to verify Billing status.')); return
        }
      }
      if (attempt < MAX_POLLS - 1) await new Promise((resolve) => window.setTimeout(resolve, POLL_INTERVAL_MS))
    }
    if (runRef.current === run && scopeIsCurrent(scope)) setState('PENDING')
  }

  useEffect(() => { void verify(); return () => { runRef.current += 1 } }, [companyId, epoch])
  const rawActionUrl = context?.companyId === companyId ? context.recurringAuthorizationActionUrl : null
  const actionUrl = (() => { if (!rawActionUrl) return null; try { const url = new URL(rawActionUrl); return url.protocol === 'https:' ? url.toString() : null } catch { return null } })()

  return <section className="px-4 py-10 sm:px-6 lg:px-8"><div className="mx-auto max-w-2xl"><div className="panel p-6 sm:p-8">
    {state === 'CHECKING' && <div className="text-center"><FiRefreshCw className="mx-auto size-8 animate-spin text-indigo-500" /><h1 className="mt-4 text-2xl font-bold">Checking payment status…</h1><p className="mt-2 text-sm text-slate-500">The return URL is not payment confirmation. UnifyDesk is checking authoritative backend subscription state.</p></div>}
    {state === 'CONFIRMED' && <div className="text-center"><FiCheckCircle className="mx-auto size-9 text-emerald-500" /><h1 className="mt-4 text-2xl font-bold">Payment confirmed</h1><p className="mt-2 text-sm text-slate-500">Backend Billing state changed after checkout and now shows the selected plan as active.</p>{details?.subscription && <p className="mt-4 text-sm font-bold">{details.subscription.planName} · {details.subscription.status}</p>}</div>}
    {state === 'PENDING' && <div className="text-center"><FiClock className="mx-auto size-9 text-amber-500" /><h1 className="mt-4 text-2xl font-bold">Payment is being verified</h1><p className="mt-2 text-sm text-slate-500">The bounded backend re-read did not establish authoritative payment confirmation. A pre-existing target plan is not treated as payment proof.</p></div>}
    {state === 'ERROR' && <div className="text-center"><FiAlertTriangle className="mx-auto size-9 text-rose-500" /><h1 className="mt-4 text-2xl font-bold">Unable to verify payment</h1><p className="mt-2 text-sm text-slate-500">{error}</p></div>}
    {state !== 'CHECKING' && actionUrl && state !== 'ERROR' && <div className="mt-6 rounded-xl border border-indigo-100 bg-indigo-50 p-4"><p className="text-sm font-bold">Automatic renewal authorization</p><p className="mt-1 text-xs">This backend-provided continuation is not proof that authorization succeeded.</p><a className="secondary-button mt-3 inline-flex" href={actionUrl} rel="noreferrer">Continue renewal authorization</a></div>}
    <div className="mt-7 flex flex-col gap-2 sm:flex-row sm:justify-center">{state !== 'CHECKING' && state !== 'CONFIRMED' && <button type="button" className="secondary-button" onClick={() => void verify()}>Check again</button>}<Link className="primary-button" to="/company/plan">Back to Billing</Link></div>
  </div></div></section>
}
