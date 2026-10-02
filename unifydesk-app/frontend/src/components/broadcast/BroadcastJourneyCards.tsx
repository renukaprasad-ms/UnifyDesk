import type {
  ChannelReadiness,
  RecipientExclusionReason,
  RecipientResolutionPreview,
  SenderReadiness,
} from '../../features/broadcast/types'

const exclusionLabels: Record<RecipientExclusionReason, string> = {
  DUPLICATE: 'Duplicates',
  MISSING_IDENTITY: 'Missing channel identity',
  INVALID_IDENTITY: 'Invalid identity',
  OPTED_OUT: 'Opted out',
  CONSENT_REQUIRED: 'Consent required',
  SUPPRESSED: 'Suppressed',
  CHANNEL_INELIGIBLE: 'Channel unavailable',
}

export const deliveryCapabilityCopy = (sender: SenderReadiness) => {
  if (sender.capabilities.readEvents) return 'Accepted, delivered and read status available'
  if (sender.capabilities.deliveryEvents) return 'Accepted and delivery status available'
  return 'Provider acceptance only; final delivery is not reported'
}

export function AudienceResolutionPreviewCard({
  preview,
  loading,
}: {
  preview: RecipientResolutionPreview | null
  loading: boolean
}) {
  if (loading) {
    return (
      <section className="rounded-2xl border border-slate-200 bg-white p-4" aria-live="polite">
        <p className="text-sm font-bold text-slate-900">Checking audience eligibility…</p>
        <p className="mt-1 text-xs text-slate-500">
          Contacts stay server-side while UnifyDesk evaluates channel eligibility.
        </p>
      </section>
    )
  }

  if (!preview) return null

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-4" aria-label="Audience eligibility">
      <div className="grid grid-cols-3 gap-2 text-center">
        <div className="rounded-xl bg-slate-50 p-3">
          <p className="text-lg font-bold text-slate-950">{preview.selectedCount.toLocaleString()}</p>
          <p className="text-[10px] font-semibold uppercase tracking-wide text-slate-500">Selected</p>
        </div>
        <div className="rounded-xl bg-emerald-50 p-3">
          <p className="text-lg font-bold text-emerald-700">{preview.eligibleCount.toLocaleString()}</p>
          <p className="text-[10px] font-semibold uppercase tracking-wide text-emerald-700">Eligible</p>
        </div>
        <div className="rounded-xl bg-amber-50 p-3">
          <p className="text-lg font-bold text-amber-700">{preview.excludedCount.toLocaleString()}</p>
          <p className="text-[10px] font-semibold uppercase tracking-wide text-amber-700">Excluded</p>
        </div>
      </div>

      {preview.excludedCount > 0 && (
        <div className="mt-4 grid gap-2 sm:grid-cols-2">
          {Object.entries(preview.exclusions)
            .filter((entry): entry is [RecipientExclusionReason, number] => Boolean(entry[1]))
            .map(([reason, count]) => (
              <div key={reason} className="flex items-center justify-between rounded-lg border border-slate-100 px-3 py-2 text-xs">
                <span className="text-slate-600">{exclusionLabels[reason]}</span>
                <strong className="text-slate-900">{count.toLocaleString()}</strong>
              </div>
            ))}
        </div>
      )}

      {preview.eligibleCount === 0 && (
        <p className="mt-4 rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-xs font-semibold text-rose-700">
          No eligible recipients. Sending is blocked until the audience has at least one eligible Contact.
        </p>
      )}
    </section>
  )
}

export function ChannelReadinessPanel({
  readiness,
  selectedSenderId,
  onSenderChange,
}: {
  readiness: ChannelReadiness | null
  selectedSenderId: string
  onSenderChange: (senderId: string) => void
}) {
  if (!readiness) {
    return (
      <section className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-xs text-amber-800">
        Sender readiness is unavailable. Sending stays disabled until configuration can be verified.
      </section>
    )
  }

  const readySenders = readiness.senders.filter((sender) => sender.state === 'READY')

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p className="text-sm font-bold text-slate-900">Channel readiness</p>
          <p className="mt-1 text-xs text-slate-500">
            {readiness.configuration.replaceAll('_', ' ').toLowerCase()}
          </p>
        </div>
        {readiness.nextAction && (
          <span className="rounded-full bg-amber-50 px-2 py-1 text-[10px] font-bold text-amber-700">
            {readiness.nextAction}
          </span>
        )}
      </div>

      {readySenders.length > 0 ? (
        <label className="mt-4 block text-xs font-semibold text-slate-700">
          Sender
          <select
            className="form-input mt-2"
            value={selectedSenderId}
            onChange={(event) => onSenderChange(event.target.value)}
          >
            <option value="">Choose sender</option>
            {readySenders.map((sender) => (
              <option key={sender.senderId} value={sender.senderId}>
                {sender.label}{sender.addressOrNumber ? ` · ${sender.addressOrNumber}` : ''}
              </option>
            ))}
          </select>
        </label>
      ) : (
        <p className="mt-4 text-xs font-semibold text-amber-700">
          {readiness.nextAction || 'Set up a sender before creating this Broadcast.'}
        </p>
      )}

      {selectedSenderId &&
        readiness.senders.find((sender) => sender.senderId === selectedSenderId) && (
          <p className="mt-3 text-[11px] leading-5 text-slate-500">
            {deliveryCapabilityCopy(
              readiness.senders.find((sender) => sender.senderId === selectedSenderId)!,
            )}
          </p>
        )}
    </section>
  )
}
