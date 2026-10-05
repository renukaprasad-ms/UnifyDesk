import { useEffect, useState } from 'react'
import {
  FiArrowLeft,
  FiCheck,
  FiCheckCircle,
  FiExternalLink,
  FiHelpCircle,
  FiSmartphone,
  FiX,
} from 'react-icons/fi'
import { broadcastApi } from '../../features/broadcast/broadcastApi'
import { useSnackbar } from '../feedback/SnackbarProvider'
import { CardSkeleton } from '../ui/Skeleton'

type Props = {
  companyId: string
  onClose: () => void
  onContinue: () => void
}

const steps = [
  {
    title: 'Open or create your MSG91 account',
    description:
      'Sign in to MSG91 and make sure SMS is enabled for the account you want this workspace to use.',
    explainer:
      'Messaging connects to your MSG91 account with an auth key. It never needs your MSG91 password.',
  },
  {
    title: 'Prepare your sender and template',
    description:
      'Create or select the SMS Sender ID/Header and the template required for the messages you plan to send.',
    explainer:
      'For Indian commercial SMS, your MSG91 setup generally needs the appropriate DLT-approved header and template before sending.',
  },
  {
    title: 'Create an MSG91 Flow',
    description:
      'Create a Flow that uses your approved template and keep its Flow ID. Include a variable for the broadcast message body.',
    explainer:
      'Messaging sends each Broadcast body into the Flow variable you configure, VAR1 by default.',
  },
  {
    title: 'Copy your auth key',
    description:
      'Get the MSG91 auth key for the account. Treat it like a password; Messaging encrypts it before storage.',
  },
  {
    title: 'Connect MSG91 SMS to Messaging',
    description:
      'You are ready to enter the auth key, Flow ID, Sender ID/Header and message variable securely.',
  },
]

export default function Msg91SmsSetupGuideModal({ companyId, onClose, onContinue }: Props) {
  const snackbar = useSnackbar()
  const [step, setStep] = useState(0)
  const [restoring, setRestoring] = useState(true)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    let cancelled = false
    broadcastApi
      .activeSetupSession(companyId, 'SMS')
      .then((session) => {
        if (cancelled || !session || session.provider !== 'MSG91_SMS') return
        setStep(Math.min(Math.max(0, session.currentStep), steps.length - 1))
      })
      .catch(() => {
        if (!cancelled) snackbar.warning('Unable to restore the previous SMS setup progress.')
      })
      .finally(() => {
        if (!cancelled) setRestoring(false)
      })

    return () => {
      cancelled = true
    }
  }, [companyId, snackbar])

  const save = async (
    currentStep: number,
    status: 'IN_PROGRESS' | 'WAITING_EXTERNAL_ACTION' | 'READY_TO_CONNECT' | 'CANCELLED',
  ) => {
    setSaving(true)
    try {
      await broadcastApi.saveSetupSession(companyId, {
        channelType: 'SMS',
        provider: 'MSG91_SMS',
        currentStep,
        status,
        setupDataJson: JSON.stringify({ onboardingMode: 'guided' }),
      })
    } catch {
      snackbar.warning(
        'Unable to save setup progress. You can still continue, but resume may not work.',
      )
    } finally {
      setSaving(false)
    }
  }

  const next = async () => {
    if (step >= steps.length - 1) {
      await save(step, 'READY_TO_CONNECT')
      onContinue()
      return
    }
    const nextStep = step + 1
    setStep(nextStep)
    await save(nextStep, 'WAITING_EXTERNAL_ACTION')
  }

  const previous = async () => {
    if (step <= 0) {
      onClose()
      return
    }
    const previousStep = step - 1
    setStep(previousStep)
    await save(previousStep, 'IN_PROGRESS')
  }

  const startOver = async () => {
    await save(step, 'CANCELLED')
    setStep(0)
    await save(0, 'IN_PROGRESS')
  }

  if (restoring) {
    return (
      <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
        <div className="mobile-sheet w-full max-w-xl rounded-2xl bg-white p-6 text-center shadow-xl">
          <CardSkeleton rows={4} />
        </div>
      </div>
    )
  }

  const current = steps[step]

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white p-5 shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-start gap-3">
            <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-violet-50 text-violet-600">
              <FiSmartphone aria-hidden="true" />
            </span>
            <div>
              <p className="text-base font-bold text-slate-950">Set up SMS Broadcast</p>
              <p className="mt-1 text-xs leading-5 text-slate-500">
                MSG91 setup · Step {step + 1} of {steps.length}
              </p>
            </div>
          </div>
          <button type="button" className="admin-back-button" onClick={onClose} aria-label="Close">
            <FiX aria-hidden="true" />
          </button>
        </div>

        <div className="mt-5 h-1.5 overflow-hidden rounded-full bg-slate-200">
          <div
            className="h-full rounded-full bg-violet-500 transition-all"
            style={{ width: `${((step + 1) / steps.length) * 100}%` }}
          />
        </div>

        <div className="mt-5 rounded-2xl border border-slate-200 p-5">
          <span className="inline-flex size-7 items-center justify-center rounded-full bg-violet-50 text-xs font-bold text-violet-700">
            {step + 1}
          </span>
          <h3 className="mt-3 text-base font-bold text-slate-950">{current.title}</h3>
          <p className="mt-2 text-sm leading-6 text-slate-600">{current.description}</p>

          {current.explainer && (
            <div className="mt-4 rounded-xl border border-sky-100 bg-sky-50/70 p-3">
              <p className="flex items-center gap-2 text-xs font-bold text-sky-800">
                <FiHelpCircle aria-hidden="true" />
                What does this mean?
              </p>
              <p className="mt-1 text-xs leading-5 text-sky-700">{current.explainer}</p>
            </div>
          )}

          {step < steps.length - 1 && (
            <a
              className="secondary-button mt-5 inline-flex items-center gap-2"
              href="https://control.msg91.com/"
              target="_blank"
              rel="noreferrer"
            >
              Open MSG91
              <FiExternalLink aria-hidden="true" />
            </a>
          )}
        </div>

        <div className="mt-4 rounded-xl bg-slate-50 p-3 text-[11px] leading-5 text-slate-500">
          <div className="flex gap-2">
            <FiCheckCircle className="mt-0.5 shrink-0 text-emerald-500" aria-hidden="true" />
            <span>
              Setup progress stores only the current step. Your MSG91 auth key is entered only in
              the secure connection form and is encrypted before storage.
            </span>
          </div>
        </div>

        <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="secondary-button inline-flex items-center gap-2"
              onClick={() => void previous()}
              disabled={saving}
            >
              <FiArrowLeft aria-hidden="true" />
              Back
            </button>
            <button
              type="button"
              className="text-xs font-semibold text-slate-500 hover:text-slate-800"
              onClick={() => void startOver()}
              disabled={saving}
            >
              Start over
            </button>
          </div>

          <button
            type="button"
            className="primary-button inline-flex items-center gap-2"
            onClick={() => void next()}
            disabled={saving}
          >
            {step === steps.length - 1 ? (
              <>
                Continue to secure connection
                <FiCheck aria-hidden="true" />
              </>
            ) : (
              'I’ve done this'
            )}
          </button>
        </div>
      </div>
    </div>
  )
}
