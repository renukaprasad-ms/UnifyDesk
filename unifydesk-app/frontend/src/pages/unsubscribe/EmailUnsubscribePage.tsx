import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { unsubscribeApi } from '../../features/unsubscribe/unsubscribeApi'

type SubmissionState = 'idle' | 'submitting' | 'success' | 'error'

const GENERIC_ERROR_MESSAGE =
  'We could not complete this request. The unsubscribe link may be invalid or expired.'

const EmailUnsubscribePage = () => {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')?.trim() ?? ''
  const [submissionState, setSubmissionState] = useState<SubmissionState>('idle')

  const submit = async () => {
    if (!token || submissionState === 'submitting') return

    setSubmissionState('submitting')
    try {
      await unsubscribeApi.unsubscribe(token)
      setSubmissionState('success')
    } catch {
      setSubmissionState('error')
    }
  }

  const invalidLink = !token || submissionState === 'error'

  return (
    <main className="flex min-h-dvh items-center justify-center bg-slate-50 px-4 py-8 sm:px-6">
      <section
        className="w-full max-w-lg rounded-3xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8"
        aria-labelledby="unsubscribe-title"
      >
        <div className="mb-6 flex items-center gap-3">
          <div
            className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-600 text-lg font-bold text-white"
            aria-hidden="true"
          >
            U
          </div>
          <span className="text-xl font-bold tracking-tight text-slate-950">UnifyDesk</span>
        </div>

        {submissionState === 'success' ? (
          <div role="status" aria-live="polite">
            <p className="text-sm font-semibold text-emerald-700">Preference updated</p>
            <h1
              id="unsubscribe-title"
              className="mt-2 text-2xl font-bold tracking-tight text-slate-950"
            >
              You’re unsubscribed
            </h1>
            <p className="mt-3 text-sm leading-6 text-slate-600">
              You have been unsubscribed from marketing emails.
            </p>
          </div>
        ) : invalidLink ? (
          <div role="alert">
            <p className="text-sm font-semibold text-slate-500">Unable to update preference</p>
            <h1
              id="unsubscribe-title"
              className="mt-2 text-2xl font-bold tracking-tight text-slate-950"
            >
              This unsubscribe link cannot be used
            </h1>
            <p className="mt-3 text-sm leading-6 text-slate-600">{GENERIC_ERROR_MESSAGE}</p>
          </div>
        ) : (
          <>
            <p className="text-sm font-semibold text-indigo-600">Email preferences</p>
            <h1
              id="unsubscribe-title"
              className="mt-2 text-2xl font-bold tracking-tight text-slate-950"
            >
              Unsubscribe from marketing emails
            </h1>
            <p className="mt-3 text-sm leading-6 text-slate-600">
              Confirm below to stop receiving marketing emails sent through UnifyDesk.
            </p>
            <button
              type="button"
              className="mt-7 flex min-h-11 w-full items-center justify-center rounded-xl bg-indigo-600 px-4 py-3 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-60"
              onClick={() => void submit()}
              disabled={submissionState === 'submitting'}
            >
              {submissionState === 'submitting' ? 'Unsubscribing…' : 'Unsubscribe'}
            </button>
          </>
        )}
      </section>
    </main>
  )
}

export default EmailUnsubscribePage
