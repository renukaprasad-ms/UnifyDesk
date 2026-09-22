import {
  FiCheckCircle,
  FiCloud,
  FiDatabase,
  FiImage,
  FiSend,
  FiUsers,
  FiX,
} from 'react-icons/fi'

type Props = {
  onClose: () => void
}

const steps = [
  {
    icon: FiImage,
    title: '1. Design once',
    text: 'Build the Email header, logo, content blocks, buttons and footer. Images are referenced from company media; templates do not store image bytes.',
  },
  {
    icon: FiCloud,
    title: '2. Media goes to storage',
    text: 'Logo and campaign images upload through the Media service to the configured storage provider, including S3. The template stores media IDs only.',
  },
  {
    icon: FiDatabase,
    title: '3. Add personalization',
    text: 'Insert variables such as {{first_name}}, {{discount}} or {{offer_url}}. CSV/custom fields and built-in recipient fields supply the real values later.',
  },
  {
    icon: FiUsers,
    title: '4. Choose an audience',
    text: 'In Broadcast, select the recipient group and an active template. Recipient metadata is snapshotted when the Broadcast starts.',
  },
  {
    icon: FiCheckCircle,
    title: '5. Render every recipient',
    text: 'Messaging resolves variables separately for every recipient. Missing required values fail that recipient instead of sending raw {{...}} tokens.',
  },
  {
    icon: FiSend,
    title: '6. Deliver through the provider',
    text: 'The rendered content is sent through the selected Email/SMS provider. WhatsApp and provider-managed templates additionally require provider approval or mapping.',
  },
]

const example = [
  'CSV row',
  'email = renuka@example.com',
  'name = Renuka Prasad',
  'discount = 20',
  '',
  'Template',
  'Hi {{first_name}},',
  'Get {{discount}}% off.',
  '',
  'Rendered for this recipient',
  'Hi Renuka,',
  'Get 20% off.',
].join('\\n')

export default function TemplateFlowGuideModal({ onClose }: Props) {
  return (
    <div className="mobile-modal-shell fixed inset-0 z-[80] flex items-center justify-center bg-slate-950/40 p-4">
      <div className="mobile-sheet max-h-[90vh] w-full max-w-3xl overflow-y-auto rounded-2xl bg-white p-5 shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-base font-bold text-slate-950">How Templates work</p>
            <p className="mt-1 text-xs leading-5 text-slate-500">
              Templates separate reusable design/content from recipient data and provider infrastructure.
            </p>
          </div>
          <button type="button" className="admin-back-button" onClick={onClose} aria-label="Close">
            <FiX aria-hidden="true" />
          </button>
        </div>

        <div className="mt-6 grid gap-3 sm:grid-cols-2">
          {steps.map(({ icon: Icon, title, text }) => (
            <div key={title} className="rounded-2xl border border-slate-200 p-4">
              <span className="flex size-9 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600">
                <Icon aria-hidden="true" />
              </span>
              <p className="mt-3 text-xs font-bold text-slate-900">{title}</p>
              <p className="mt-1 text-[11px] leading-5 text-slate-500">{text}</p>
            </div>
          ))}
        </div>

        <div className="mt-5 rounded-2xl border border-indigo-100 bg-indigo-50/60 p-4">
          <p className="text-xs font-bold text-indigo-900">Example</p>
          <pre className="mt-2 whitespace-pre-wrap font-sans text-[11px] leading-5 text-indigo-800">
            {example}
          </pre>
        </div>

        <div className="mt-5 rounded-2xl border border-amber-100 bg-amber-50 p-4 text-[11px] leading-5 text-amber-800">
          WhatsApp templates are different: Messaging can design/store them locally, but Meta must approve them before they become sendable. MSG91 provider templates/Flows are mapped separately when required.
        </div>

        <div className="mt-6 flex justify-end">
          <button type="button" className="primary-button" onClick={onClose}>
            Got it
          </button>
        </div>
      </div>
    </div>
  )
}
