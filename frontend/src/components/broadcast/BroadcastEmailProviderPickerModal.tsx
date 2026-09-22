import { FiCheckCircle, FiCloud, FiMail, FiSend, FiX } from 'react-icons/fi'

export type BroadcastEmailProviderChoice = 'AMAZON_SES' | 'SENDGRID'

type Props = {
  onClose: () => void
  onSelect: (provider: BroadcastEmailProviderChoice) => void
}

const providers = [
  {
    id: 'AMAZON_SES' as const,
    name: 'Amazon SES',
    description: 'AWS high-volume email with account quota-aware sending.',
    available: true,
    icon: <FiCloud aria-hidden="true" />,
  },
  {
    id: 'SENDGRID' as const,
    name: 'SendGrid',
    description: 'Twilio SendGrid API for high-volume transactional and broadcast email.',
    available: true,
    icon: <FiSend aria-hidden="true" />,
  },
  {
    id: 'AZURE_EMAIL' as const,
    name: 'Azure Email',
    description: 'Azure Communication Services Email.',
    available: false,
    icon: <FiMail aria-hidden="true" />,
  },
]

export default function BroadcastEmailProviderPickerModal({ onClose, onSelect }: Props) {
  return (
    <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet w-full max-w-xl rounded-2xl bg-white p-5 shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-base font-bold text-slate-950">Connect broadcast email</p>
            <p className="mt-1 text-xs leading-5 text-slate-500">
              Choose the provider this workspace will use for high-volume email broadcasts.
            </p>
          </div>
          <button type="button" className="admin-back-button" onClick={onClose} aria-label="Close">
            <FiX aria-hidden="true" />
          </button>
        </div>

        <div className="mt-5 grid gap-3">
          {providers.map((provider) => (
            <button
              key={provider.id}
              type="button"
              disabled={!provider.available}
              className="flex w-full items-start gap-3 rounded-xl border border-slate-200 p-4 text-left transition hover:border-indigo-200 hover:bg-indigo-50/40 disabled:cursor-not-allowed disabled:bg-slate-50 disabled:opacity-65"
              onClick={() => {
                if (!provider.available || provider.id === 'AZURE_EMAIL') return
                onSelect(provider.id)
              }}
            >
              <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600">
                {provider.icon}
              </span>
              <span className="min-w-0 flex-1">
                <span className="flex flex-wrap items-center gap-2">
                  <span className="text-sm font-bold text-slate-950">{provider.name}</span>
                  {provider.available ? (
                    <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-[10px] font-bold text-emerald-700">
                      <FiCheckCircle aria-hidden="true" />
                      Available
                    </span>
                  ) : (
                    <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-bold text-slate-500">
                      Backend next
                    </span>
                  )}
                </span>
                <span className="mt-1 block text-xs leading-5 text-slate-500">
                  {provider.description}
                </span>
              </span>
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}
