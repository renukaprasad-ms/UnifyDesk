import { useEffect, useMemo, useState, type ReactNode } from 'react'
import {
  FiArrowLeft,
  FiCheck,
  FiCheckCircle,
  FiChevronRight,
  FiCloud,
  FiExternalLink,
  FiHelpCircle,
  FiMail,
  FiSend,
  FiX,
} from 'react-icons/fi'
import { broadcastApi } from '../../features/broadcast/broadcastApi'
import { useSnackbar } from '../feedback/SnackbarProvider'
import type { BroadcastSenderProvider, BroadcastSetupStatus } from '../../features/broadcast/types'
import { CardSkeleton } from '../ui/Skeleton'

export type BroadcastEmailProviderChoice = 'AMAZON_SES' | 'SENDGRID' | 'AZURE_EMAIL' | 'MSG91_EMAIL'

type Props = {
  companyId: string
  onClose: () => void
  onManagedEmail: () => void
  onSelect: (provider: BroadcastEmailProviderChoice) => void
}

type Stage = 'landing' | 'questions' | 'providers' | 'guide'
type Ecosystem = 'AWS' | 'AZURE' | 'SENDGRID' | 'MSG91' | 'NONE'
type Goal = 'MARKETING' | 'TRANSACTIONAL' | 'BOTH'

type SetupStep = {
  title: string
  description: string
  explainer?: string
}

type ProviderInfo = {
  id: BroadcastEmailProviderChoice
  name: string
  description: string
  icon: ReactNode
  consoleUrl: string
  goodFor: string[]
  needs: string[]
  steps: SetupStep[]
}

const providers: ProviderInfo[] = [
  {
    id: 'AMAZON_SES',
    name: 'Amazon SES',
    description: 'AWS high-volume email with quota-aware sending.',
    icon: <FiCloud aria-hidden="true" />,
    consoleUrl: 'https://console.aws.amazon.com/ses/',
    goodFor: ['You already use AWS', 'High-volume sending', 'AWS-managed infrastructure'],
    needs: [
      'AWS account',
      'Verified email or domain',
      'SES sending access',
      'IAM role or access keys',
    ],
    steps: [
      {
        title: 'Open Amazon SES and choose a region',
        description:
          'Choose the AWS region you want Messaging to send from and keep that region selected.',
        explainer:
          'An AWS region is the data-center location for your SES account, such as ap-south-1.',
      },
      {
        title: 'Verify your sending identity',
        description:
          'In SES, verify the email address or domain you want to use as the From address.',
        explainer: 'A verified identity proves that you control the sender address or domain.',
      },
      {
        title: 'Check production access',
        description:
          'If the SES account is still in sandbox, request production access before sending real campaigns.',
        explainer:
          'SES sandbox accounts can only send to verified recipients and have lower limits.',
      },
      {
        title: 'Create secure SES access',
        description:
          'Create an IAM role or access key with permission to send through SES. Do not paste your AWS password.',
        explainer:
          'IAM credentials let Messaging call SES without knowing your AWS account password.',
      },
      {
        title: 'Connect SES to Messaging',
        description:
          'You are ready to enter the region, credentials and verified From address securely.',
      },
    ],
  },
  {
    id: 'SENDGRID',
    name: 'SendGrid',
    description: 'API-focused email delivery through Twilio SendGrid.',
    icon: <FiSend aria-hidden="true" />,
    consoleUrl: 'https://app.sendgrid.com/',
    goodFor: [
      'You already use SendGrid',
      'API-first email delivery',
      'Transactional and broadcast email',
    ],
    needs: ['SendGrid account', 'Verified sender or domain', 'API key'],
    steps: [
      {
        title: 'Open or create your SendGrid account',
        description: 'Sign in to SendGrid and make sure the account can use Email API.',
      },
      {
        title: 'Authenticate your sender',
        description:
          'Use Sender Authentication to verify either the exact From address or the sending domain.',
        explainer:
          'Domain authentication is better when you plan to send from several addresses on the same domain.',
      },
      {
        title: 'Create an API key',
        description:
          'Create an API key with Mail Send permission and read access to Sender Authentication.',
        explainer:
          'Messaging encrypts this API key before storing it and never needs your SendGrid password.',
      },
      {
        title: 'Connect SendGrid to Messaging',
        description: 'You are ready to enter the API key and the verified From address.',
      },
    ],
  },
  {
    id: 'AZURE_EMAIL',
    name: 'Azure Email',
    description: 'Azure Communication Services Email.',
    icon: <FiMail aria-hidden="true" />,
    consoleUrl: 'https://portal.azure.com/',
    goodFor: [
      'Your company already uses Azure',
      'Microsoft cloud infrastructure',
      'ACS-based applications',
    ],
    needs: [
      'Azure subscription',
      'Communication Services resource',
      'Email domain',
      'Endpoint and access key',
    ],
    steps: [
      {
        title: 'Create or open Communication Services',
        description:
          'In Azure Portal, create or open the Azure Communication Services resource you want Messaging to use.',
      },
      {
        title: 'Set up Email Communication Services',
        description:
          'Create the email resource, configure a domain, and link that domain to the Communication Services resource.',
        explainer:
          'The linked email domain determines which MailFrom addresses Azure allows you to send from.',
      },
      {
        title: 'Copy the ACS endpoint and access key',
        description:
          'Open Keys in the Communication Services resource and copy the endpoint and one access key.',
        explainer: 'The access key is a secret. Messaging encrypts it before storage.',
      },
      {
        title: 'Choose your MailFrom address',
        description:
          'Use a MailFrom address that belongs to the domain connected to Azure Communication Services.',
      },
      {
        title: 'Connect Azure Email to Messaging',
        description: 'You are ready to enter the endpoint, access key and MailFrom address.',
      },
    ],
  },
  {
    id: 'MSG91_EMAIL',
    name: 'MSG91 Email',
    description: 'Template-based email using a verified MSG91 domain.',
    icon: <FiMail aria-hidden="true" />,
    consoleUrl: 'https://control.msg91.com/',
    goodFor: ['You already use MSG91', 'Template-based delivery', 'One provider for email and SMS'],
    needs: ['MSG91 account', 'Verified sending domain', 'Email template', 'Auth key'],
    steps: [
      {
        title: 'Open MSG91 Email',
        description:
          'Sign in to MSG91 and open the Email product for the account you want Messaging to use.',
      },
      {
        title: 'Verify your sending domain',
        description: 'Add the sending domain in MSG91 and complete the DNS verification steps.',
        explainer: 'The From address you enter in Messaging must belong to this verified domain.',
      },
      {
        title: 'Create an email template',
        description:
          'Create the template Messaging should use and keep its Template ID. Include a variable for the broadcast body.',
        explainer:
          'Messaging sends the broadcast content into your configured template variable instead of replacing the provider template.',
      },
      {
        title: 'Copy your auth key',
        description:
          'Get the MSG91 auth key for this account. Messaging encrypts it before storage.',
      },
      {
        title: 'Connect MSG91 Email to Messaging',
        description: 'You are ready to enter the domain, template ID, auth key and From address.',
      },
    ],
  },
]

const providerById = Object.fromEntries(
  providers.map((provider) => [provider.id, provider]),
) as Record<BroadcastEmailProviderChoice, ProviderInfo>

const isEmailProvider = (
  provider: BroadcastSenderProvider,
): provider is BroadcastEmailProviderChoice =>
  provider === 'AMAZON_SES' ||
  provider === 'SENDGRID' ||
  provider === 'AZURE_EMAIL' ||
  provider === 'MSG91_EMAIL'

export default function BroadcastEmailProviderPickerModal({
  companyId,
  onClose,
  onManagedEmail,
  onSelect,
}: Props) {
  const snackbar = useSnackbar()
  const [stage, setStage] = useState<Stage>('landing')
  const [selectedProvider, setSelectedProvider] = useState<BroadcastEmailProviderChoice | null>(
    null,
  )
  const [step, setStep] = useState(0)
  const [ecosystem, setEcosystem] = useState<Ecosystem>('NONE')
  const [goal, setGoal] = useState<Goal>('BOTH')
  const [guidedChoice, setGuidedChoice] = useState(false)
  const [restoring, setRestoring] = useState(true)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    let cancelled = false

    broadcastApi
      .activeSetupSession(companyId, 'EMAIL')
      .then((session) => {
        if (cancelled || !session || !isEmailProvider(session.provider)) return
        setSelectedProvider(session.provider)
        const info = providerById[session.provider]
        setStep(Math.min(Math.max(0, session.currentStep), info.steps.length - 1))
        if (session.setupDataJson) {
          try {
            const data = JSON.parse(session.setupDataJson) as {
              onboardingMode?: 'guided' | 'provider'
              ecosystem?: Ecosystem
              goal?: Goal
            }
            if (data.onboardingMode) setGuidedChoice(data.onboardingMode === 'guided')
            if (data.ecosystem) setEcosystem(data.ecosystem)
            if (data.goal) setGoal(data.goal)
          } catch {
            // Setup metadata is optional. A malformed legacy value must not block onboarding.
          }
        }
        setStage('guide')
      })
      .catch(() => {
        if (!cancelled) snackbar.warning('Unable to restore the previous setup progress.')
      })
      .finally(() => {
        if (!cancelled) setRestoring(false)
      })

    return () => {
      cancelled = true
    }
  }, [companyId, snackbar])

  const suggestedProvider = useMemo<BroadcastEmailProviderChoice>(() => {
    if (ecosystem === 'AWS') return 'AMAZON_SES'
    if (ecosystem === 'AZURE') return 'AZURE_EMAIL'
    if (ecosystem === 'SENDGRID') return 'SENDGRID'
    if (ecosystem === 'MSG91') return 'MSG91_EMAIL'
    return 'SENDGRID'
  }, [ecosystem])

  const setupDataJson = () =>
    JSON.stringify({
      onboardingMode: guidedChoice ? 'guided' : 'provider',
      ecosystem,
      goal,
    })

  const saveProgress = async (
    provider: BroadcastEmailProviderChoice,
    currentStep: number,
    status: BroadcastSetupStatus,
  ) => {
    setSaving(true)
    try {
      await broadcastApi.saveSetupSession(companyId, {
        channelType: 'EMAIL',
        provider,
        currentStep,
        status,
        setupDataJson: setupDataJson(),
      })
    } catch {
      snackbar.warning(
        'Unable to save setup progress. You can still continue, but resume may not work.',
      )
    } finally {
      setSaving(false)
    }
  }

  const chooseProvider = async (provider: BroadcastEmailProviderChoice) => {
    setSelectedProvider(provider)
    setStep(0)
    setStage('guide')
    await saveProgress(provider, 0, 'IN_PROGRESS')
  }

  const nextStep = async () => {
    if (!selectedProvider) return
    const info = providerById[selectedProvider]
    if (step >= info.steps.length - 1) {
      await saveProgress(selectedProvider, step, 'READY_TO_CONNECT')
      onSelect(selectedProvider)
      return
    }

    const next = step + 1
    setStep(next)
    await saveProgress(selectedProvider, next, 'WAITING_EXTERNAL_ACTION')
  }

  const previousStep = async () => {
    if (!selectedProvider || step <= 0) {
      setStage('providers')
      return
    }
    const previous = step - 1
    setStep(previous)
    await saveProgress(selectedProvider, previous, 'IN_PROGRESS')
  }

  const startOver = async () => {
    if (selectedProvider) {
      await saveProgress(selectedProvider, step, 'CANCELLED')
    }
    setSelectedProvider(null)
    setStep(0)
    setEcosystem('NONE')
    setGoal('BOTH')
    setGuidedChoice(false)
    setStage('landing')
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

  const info = selectedProvider ? providerById[selectedProvider] : null
  const currentStep = info ? info.steps[step] : null

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet max-h-[92vh] w-full max-w-3xl overflow-y-auto rounded-2xl bg-white p-5 shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              {stage !== 'landing' && (
                <button
                  type="button"
                  className="admin-back-button"
                  onClick={() => {
                    if (stage === 'guide') void previousStep()
                    else if (stage === 'providers') setStage('landing')
                    else setStage('landing')
                  }}
                  aria-label="Back"
                >
                  <FiArrowLeft aria-hidden="true" />
                </button>
              )}
              <div>
                <p className="text-base font-bold text-slate-950">Connect broadcast email</p>
                <p className="mt-1 text-xs leading-5 text-slate-500">
                  {stage === 'landing'
                    ? 'We can guide you even if you have never configured an email provider before.'
                    : stage === 'questions'
                      ? 'A couple of simple questions will narrow down the setup path.'
                      : stage === 'providers'
                        ? 'Choose a provider, then we will walk through the exact setup.'
                        : info
                          ? `${info.name} setup`
                          : 'Provider setup'}
                </p>
              </div>
            </div>
          </div>

          <button type="button" className="admin-back-button" onClick={onClose} aria-label="Close">
            <FiX aria-hidden="true" />
          </button>
        </div>

        {stage === 'landing' && (
          <div className="mt-6 grid gap-4 sm:grid-cols-2">
            <button
              type="button"
              className="rounded-2xl border border-indigo-200 bg-indigo-50/60 p-5 text-left transition hover:border-indigo-300 hover:bg-indigo-50"
              onClick={onManagedEmail}
            >
              <span className="flex size-11 items-center justify-center rounded-xl bg-white text-indigo-600 shadow-sm">
                <FiMail aria-hidden="true" />
              </span>
              <span className="mt-4 block text-sm font-bold text-slate-950">
                Use UnifyDesk Managed Email
              </span>
              <span className="mt-1 block text-xs leading-5 text-slate-600">
                UnifyDesk handles the email-sending infrastructure for you. No provider credentials
                are needed.
              </span>
              <span className="mt-4 inline-flex items-center gap-1 text-xs font-bold text-indigo-600">
                Set up Managed Email <FiChevronRight aria-hidden="true" />
              </span>
            </button>

            <button
              type="button"
              className="rounded-2xl border border-slate-200 p-5 text-left transition hover:border-indigo-200 hover:bg-slate-50"
              onClick={() => {
                setGuidedChoice(false)
                setStage('providers')
              }}
            >
              <span className="flex size-11 items-center justify-center rounded-xl bg-slate-100 text-slate-600">
                <FiCloud aria-hidden="true" />
              </span>
              <span className="mt-4 block text-sm font-bold text-slate-950">
                Connect my own provider
              </span>
              <span className="mt-1 block text-xs leading-5 text-slate-600">
                Advanced: connect Amazon SES, SendGrid, Azure Email or MSG91 Email that you already
                manage.
              </span>
              <span className="mt-4 inline-flex items-center gap-1 text-xs font-bold text-slate-600">
                Choose provider <FiChevronRight aria-hidden="true" />
              </span>
            </button>
          </div>
        )}

        {stage === 'questions' && (
          <div className="mt-6">
            <p className="text-sm font-bold text-slate-950">
              Where do you already have an account?
            </p>
            <div className="mt-3 grid gap-2 sm:grid-cols-3">
              {[
                ['AWS', 'AWS'],
                ['AZURE', 'Microsoft Azure'],
                ['SENDGRID', 'SendGrid'],
                ['MSG91', 'MSG91'],
                ['NONE', 'None / not sure'],
              ].map(([value, label]) => (
                <button
                  key={value}
                  type="button"
                  className={`rounded-xl border px-3 py-3 text-left text-xs font-semibold transition ${
                    ecosystem === value
                      ? 'border-indigo-300 bg-indigo-50 text-indigo-700'
                      : 'border-slate-200 text-slate-700 hover:border-indigo-200'
                  }`}
                  onClick={() => setEcosystem(value as Ecosystem)}
                >
                  {label}
                </button>
              ))}
            </div>

            <p className="mt-6 text-sm font-bold text-slate-950">What are you mainly sending?</p>
            <div className="mt-3 grid gap-2 sm:grid-cols-3">
              {[
                ['MARKETING', 'Marketing campaigns'],
                ['TRANSACTIONAL', 'Transactional emails'],
                ['BOTH', 'Both'],
              ].map(([value, label]) => (
                <button
                  key={value}
                  type="button"
                  className={`rounded-xl border px-3 py-3 text-left text-xs font-semibold transition ${
                    goal === value
                      ? 'border-indigo-300 bg-indigo-50 text-indigo-700'
                      : 'border-slate-200 text-slate-700 hover:border-indigo-200'
                  }`}
                  onClick={() => setGoal(value as Goal)}
                >
                  {label}
                </button>
              ))}
            </div>

            <div className="mt-6 flex justify-end">
              <button
                type="button"
                className="primary-button"
                onClick={() => setStage('providers')}
              >
                Show setup options
              </button>
            </div>
          </div>
        )}

        {stage === 'providers' && (
          <div className="mt-6 grid gap-3">
            {providers.map((provider) => {
              const suggested = guidedChoice && provider.id === suggestedProvider
              return (
                <button
                  key={provider.id}
                  type="button"
                  className={`w-full rounded-2xl border p-4 text-left transition hover:border-indigo-300 hover:bg-indigo-50/40 ${
                    suggested ? 'border-indigo-200 bg-indigo-50/40' : 'border-slate-200'
                  }`}
                  onClick={() => void chooseProvider(provider.id)}
                >
                  <span className="flex items-start gap-3">
                    <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600">
                      {provider.icon}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="flex flex-wrap items-center gap-2">
                        <span className="text-sm font-bold text-slate-950">{provider.name}</span>
                        {suggested && (
                          <span className="rounded-full bg-indigo-100 px-2 py-0.5 text-[10px] font-bold text-indigo-700">
                            Suggested for you
                          </span>
                        )}
                      </span>
                      <span className="mt-1 block text-xs leading-5 text-slate-500">
                        {provider.description}
                      </span>
                      <span className="mt-3 grid gap-1 sm:grid-cols-2">
                        <span className="text-[11px] text-slate-600">
                          <strong>Good if:</strong> {provider.goodFor[0]}
                        </span>
                        <span className="text-[11px] text-slate-600">
                          <strong>You need:</strong> {provider.needs.slice(0, 2).join(', ')}
                        </span>
                      </span>
                    </span>
                    <FiChevronRight className="mt-2 shrink-0 text-slate-400" aria-hidden="true" />
                  </span>
                </button>
              )
            })}
          </div>
        )}

        {stage === 'guide' && info && currentStep && (
          <div className="mt-6">
            <div className="rounded-2xl border border-slate-200 bg-slate-50/70 p-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <span className="flex size-10 items-center justify-center rounded-xl bg-white text-indigo-600 shadow-sm">
                    {info.icon}
                  </span>
                  <div>
                    <p className="text-sm font-bold text-slate-950">{info.name}</p>
                    <p className="text-[11px] text-slate-500">
                      Step {step + 1} of {info.steps.length}
                    </p>
                  </div>
                </div>
                <button
                  type="button"
                  className="text-xs font-semibold text-slate-500 hover:text-slate-800"
                  onClick={() => void startOver()}
                  disabled={saving}
                >
                  Start over
                </button>
              </div>

              <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-slate-200">
                <div
                  className="h-full rounded-full bg-indigo-500 transition-all"
                  style={{ width: `${((step + 1) / info.steps.length) * 100}%` }}
                />
              </div>
            </div>

            <div className="mt-5 grid gap-5 lg:grid-cols-[1fr_240px]">
              <div className="rounded-2xl border border-slate-200 p-5">
                <span className="inline-flex size-7 items-center justify-center rounded-full bg-indigo-50 text-xs font-bold text-indigo-700">
                  {step + 1}
                </span>
                <h3 className="mt-3 text-base font-bold text-slate-950">{currentStep.title}</h3>
                <p className="mt-2 text-sm leading-6 text-slate-600">{currentStep.description}</p>

                {currentStep.explainer && (
                  <div className="mt-4 rounded-xl border border-sky-100 bg-sky-50/70 p-3">
                    <p className="flex items-center gap-2 text-xs font-bold text-sky-800">
                      <FiHelpCircle aria-hidden="true" />
                      What does this mean?
                    </p>
                    <p className="mt-1 text-xs leading-5 text-sky-700">{currentStep.explainer}</p>
                  </div>
                )}

                {step < info.steps.length - 1 && (
                  <a
                    className="secondary-button mt-5 inline-flex items-center gap-2"
                    href={info.consoleUrl}
                    target="_blank"
                    rel="noreferrer"
                  >
                    Open {info.name}
                    <FiExternalLink aria-hidden="true" />
                  </a>
                )}
              </div>

              <aside className="rounded-2xl border border-slate-200 p-4">
                <p className="text-xs font-bold uppercase tracking-[0.08em] text-slate-400">
                  You will need
                </p>
                <div className="mt-3 grid gap-2">
                  {info.needs.map((need) => (
                    <div key={need} className="flex gap-2 text-xs leading-5 text-slate-600">
                      <FiCheckCircle
                        className="mt-0.5 shrink-0 text-emerald-500"
                        aria-hidden="true"
                      />
                      <span>{need}</span>
                    </div>
                  ))}
                </div>

                <div className="mt-5 rounded-xl bg-slate-50 p-3 text-[11px] leading-5 text-slate-500">
                  Provider credentials are entered only in the secure connection form. Setup
                  progress never stores API keys, passwords or access keys.
                </div>
              </aside>
            </div>

            <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
              <button
                type="button"
                className="secondary-button inline-flex items-center gap-2"
                onClick={() => void previousStep()}
                disabled={saving}
              >
                <FiArrowLeft aria-hidden="true" />
                Back
              </button>

              <button
                type="button"
                className="primary-button inline-flex items-center gap-2"
                onClick={() => void nextStep()}
                disabled={saving}
              >
                {step === info.steps.length - 1 ? (
                  <>
                    Continue to secure connection
                    <FiCheck aria-hidden="true" />
                  </>
                ) : (
                  <>
                    I’ve done this
                    <FiChevronRight aria-hidden="true" />
                  </>
                )}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
