import {
  FiArrowLeft,
  FiChevronRight,
  FiMail,
  FiMessageSquare,
  FiSmartphone,
  FiX,
} from 'react-icons/fi'
import type { ChannelType } from '../../features/channel/types'
import type { MessageTemplateCategory } from '../../features/template/types'

type Props = {
  channelType: ChannelType
  category: MessageTemplateCategory | null
  onChannelChange: (channelType: ChannelType) => void
  onCategoryChange: (category: MessageTemplateCategory) => void
  onBack: () => void
  onClose: () => void
  onContinue: () => void
}

const channels: {
  type: ChannelType
  name: string
  description: string
  icon: typeof FiMail
}[] = [
  {
    type: 'EMAIL',
    name: 'Email',
    description: 'Reusable subjects and email content for Broadcasts and automations.',
    icon: FiMail,
  },
  {
    type: 'WHATSAPP',
    name: 'WhatsApp',
    description: 'Meta-compatible template content with header, body, footer and buttons.',
    icon: FiMessageSquare,
  },
  {
    type: 'SMS',
    name: 'SMS',
    description: 'Reusable SMS copy for broadcasts and provider-backed message flows.',
    icon: FiSmartphone,
  },
]

const categoryOptions = (
  channelType: ChannelType,
): {
  value: MessageTemplateCategory
  title: string
  description: string
}[] => {
  if (channelType === 'WHATSAPP') {
    return [
      {
        value: 'MARKETING',
        title: 'Marketing',
        description: 'Offers, promotions, product announcements and re-engagement.',
      },
      {
        value: 'UTILITY',
        title: 'Utility',
        description: 'Order, account, delivery and other user-requested updates.',
      },
      {
        value: 'AUTHENTICATION',
        title: 'Authentication',
        description: 'Login and verification messages that require provider approval.',
      },
    ]
  }

  return [
    {
      value: 'MARKETING',
      title: 'Marketing',
      description: 'Campaigns, promotions, offers and product announcements.',
    },
    {
      value: 'TRANSACTIONAL',
      title: 'Transactional',
      description: 'Receipts, confirmations, alerts and account messages.',
    },
    {
      value: 'UTILITY',
      title: 'Utility',
      description: 'Reminders and operational updates for existing users.',
    },
  ]
}

export default function TemplateCreateWizardModal({
  channelType,
  category,
  onChannelChange,
  onCategoryChange,
  onBack,
  onClose,
  onContinue,
}: Props) {
  const step = category ? 2 : 1

  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet max-h-[92vh] w-full max-w-3xl overflow-y-auto rounded-2xl bg-white p-5 shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-start gap-3">
            {step === 2 && (
              <button
                type="button"
                className="admin-back-button"
                onClick={onBack}
                aria-label="Back"
              >
                <FiArrowLeft aria-hidden="true" />
              </button>
            )}
            <div>
              <p className="text-base font-bold text-slate-950">Create template</p>
              <p className="mt-1 text-xs leading-5 text-slate-500">
                Step {step} of 4 · {step === 1 ? 'Choose a channel' : 'Choose the template purpose'}
              </p>
            </div>
          </div>
          <button type="button" className="admin-back-button" onClick={onClose} aria-label="Close">
            <FiX aria-hidden="true" />
          </button>
        </div>

        <div className="mt-5 h-1.5 overflow-hidden rounded-full bg-slate-100">
          <div
            className="h-full rounded-full bg-indigo-500 transition-all"
            style={{ width: step === 1 ? '25%' : '50%' }}
          />
        </div>

        {step === 1 ? (
          <div className="mt-6">
            <h2 className="text-sm font-bold text-slate-950">
              Where will this template be used?
            </h2>
            <p className="mt-1 text-xs leading-5 text-slate-500">
              The editor and provider rules change based on the selected channel.
            </p>

            <div className="mt-4 grid gap-3 sm:grid-cols-3">
              {channels.map((channel) => {
                const Icon = channel.icon
                const selected = channelType === channel.type
                return (
                  <button
                    key={channel.type}
                    type="button"
                    className={`rounded-2xl border p-4 text-left transition ${
                      selected
                        ? 'border-indigo-300 bg-indigo-50'
                        : 'border-slate-200 hover:border-indigo-200 hover:bg-slate-50'
                    }`}
                    onClick={() => onChannelChange(channel.type)}
                  >
                    <span
                      className={`flex size-10 items-center justify-center rounded-xl ${
                        selected
                          ? 'bg-white text-indigo-600 shadow-sm'
                          : 'bg-slate-100 text-slate-500'
                      }`}
                    >
                      <Icon aria-hidden="true" />
                    </span>
                    <span className="mt-4 block text-sm font-bold text-slate-950">
                      {channel.name}
                    </span>
                    <span className="mt-1 block text-xs leading-5 text-slate-500">
                      {channel.description}
                    </span>
                  </button>
                )
              })}
            </div>

            <div className="mt-6 flex justify-end">
              <button
                type="button"
                className="primary-button inline-flex items-center gap-2"
                onClick={() =>
                  onCategoryChange(
                    channelType === 'WHATSAPP' ? 'MARKETING' : 'MARKETING',
                  )
                }
              >
                Continue
                <FiChevronRight aria-hidden="true" />
              </button>
            </div>
          </div>
        ) : (
          <div className="mt-6">
            <h2 className="text-sm font-bold text-slate-950">
              What is this template for?
            </h2>
            <p className="mt-1 text-xs leading-5 text-slate-500">
              This determines the validation rules and, for WhatsApp, the category submitted to Meta.
            </p>

            <div className="mt-4 grid gap-3">
              {categoryOptions(channelType).map((option) => {
                const selected = category === option.value
                return (
                  <button
                    key={option.value}
                    type="button"
                    className={`flex items-start justify-between gap-4 rounded-2xl border p-4 text-left transition ${
                      selected
                        ? 'border-indigo-300 bg-indigo-50'
                        : 'border-slate-200 hover:border-indigo-200 hover:bg-slate-50'
                    }`}
                    onClick={() => onCategoryChange(option.value)}
                  >
                    <span>
                      <span className="block text-sm font-bold text-slate-950">
                        {option.title}
                      </span>
                      <span className="mt-1 block text-xs leading-5 text-slate-500">
                        {option.description}
                      </span>
                    </span>
                    <span
                      className={`mt-1 size-4 shrink-0 rounded-full border-2 ${
                        selected
                          ? 'border-indigo-500 bg-indigo-500 ring-4 ring-indigo-100'
                          : 'border-slate-300'
                      }`}
                    />
                  </button>
                )
              })}
            </div>

            {channelType === 'WHATSAPP' && (
              <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-3 text-xs leading-5 text-amber-800">
                WhatsApp templates are saved locally first. They become usable only after the Meta
                provider submission and approval step.
              </div>
            )}

            <div className="mt-6 flex items-center justify-between gap-3">
              <button type="button" className="secondary-button" onClick={onBack}>
                Back
              </button>
              <button
                type="button"
                className="primary-button inline-flex items-center gap-2"
                onClick={onContinue}
                disabled={!category}
              >
                Build template
                <FiChevronRight aria-hidden="true" />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
