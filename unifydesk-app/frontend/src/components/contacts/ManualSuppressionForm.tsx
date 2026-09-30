import { useState, type FormEvent } from 'react'
import type { ContactChannel } from '../../features/contacts/types'

type Props = {
  busy: boolean
  onCreate: (payload: {
    channel?: ContactChannel | null
    reason: string
    note?: string | null
  }) => Promise<void>
}

const reasons = ['MANUAL', 'DND', 'INVALID_DESTINATION', 'ABUSE_POLICY', 'LEGAL_POLICY'] as const

export default function ManualSuppressionForm({ busy, onCreate }: Props) {
  const [channel, setChannel] = useState<ContactChannel | ''>('')
  const [reason, setReason] = useState<(typeof reasons)[number]>('MANUAL')
  const [note, setNote] = useState('')

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!window.confirm('Create this manual suppression? It will block eligible delivery according to policy.')) {
      return
    }
    await onCreate({
      channel: channel || null,
      reason,
      note: note.trim() || null,
    })
    setNote('')
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
          onChange={(event) => setChannel(event.target.value as ContactChannel | '')}
        >
          <option value="">All applicable</option>
          <option value="EMAIL">Email</option>
          <option value="SMS">SMS</option>
          <option value="WHATSAPP">WhatsApp</option>
        </select>
      </label>
      <label className="text-xs font-semibold text-rose-900">
        Reason
        <select
          className="form-input mt-1"
          value={reason}
          onChange={(event) => setReason(event.target.value as (typeof reasons)[number])}
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
  )
}
