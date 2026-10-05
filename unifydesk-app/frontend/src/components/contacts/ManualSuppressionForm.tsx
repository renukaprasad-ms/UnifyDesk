import { useState, type FormEvent } from 'react'
import { createIdempotencyKey } from '../../features/contacts/contactModel'
import type {
  ContactChannel,
  CreateSuppressionInput,
  SuppressionReasonCode,
} from '../../features/contacts/types'
import ConfirmDialog from '../ui/ConfirmDialog'

type Props = {
  busy: boolean
  onCreate: (payload: CreateSuppressionInput) => Promise<void>
}

const reasons: SuppressionReasonCode[] = [
  'MANUAL',
  'DND',
  'INVALID_DESTINATION',
  'ABUSE_POLICY',
  'LEGAL_POLICY',
]

export default function ManualSuppressionForm({ busy, onCreate }: Props) {
  const [channel, setChannel] = useState<ContactChannel>('EMAIL')
  const [reasonCode, setReasonCode] = useState<SuppressionReasonCode>('MANUAL')
  const [note, setNote] = useState('')
  const [confirmOpen, setConfirmOpen] = useState(false)

  const createSuppression = async () => {
    await onCreate({
      channel,
      identityId: null,
      reasonCode,
      sourceRef: null,
      occurredAt: new Date().toISOString(),
      note: note.trim() || null,
      idempotencyKey: createIdempotencyKey(),
    })
    setNote('')
    setConfirmOpen(false)
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    setConfirmOpen(true)
  }

  return (
    <form
      className="mt-4 grid gap-2 rounded-xl border border-rose-100 bg-rose-50 p-4 md:grid-cols-[160px_200px_minmax(0,1fr)_auto]"
      onSubmit={submit}
    >
      <label className="text-xs font-semibold text-rose-900">
        Channel
        <select
          className="form-input mt-1"
          value={channel}
          onChange={(event) => setChannel(event.target.value as ContactChannel)}
        >
          <option value="EMAIL">Email</option>
          <option value="SMS">SMS</option>
          <option value="WHATSAPP">WhatsApp</option>
        </select>
      </label>

      <label className="text-xs font-semibold text-rose-900">
        Reason
        <select
          className="form-input mt-1"
          value={reasonCode}
          onChange={(event) => setReasonCode(event.target.value as SuppressionReasonCode)}
        >
          {reasons.map((item) => (
            <option key={item} value={item}>
              {item.replaceAll('_', ' ')}
            </option>
          ))}
        </select>
      </label>

      <label className="text-xs font-semibold text-rose-900">
        Note
        <input
          className="form-input mt-1"
          value={note}
          maxLength={500}
          placeholder="Why is this Contact being suppressed?"
          onChange={(event) => setNote(event.target.value)}
        />
      </label>

      <button className="secondary-button self-end text-rose-700" disabled={busy}>
        Add suppression
      </button>
    </form>
      <ConfirmDialog
        open={confirmOpen}
        title="Create this manual suppression?"
        description="This can make the contact ineligible for delivery on the selected channel and will be recorded for audit."
        confirmLabel="Yes, add suppression"
        cancelLabel="No, cancel"
        tone="danger"
        working={busy}
        onCancel={() => setConfirmOpen(false)}
        onConfirm={() => void createSuppression()}
      />
  )
}