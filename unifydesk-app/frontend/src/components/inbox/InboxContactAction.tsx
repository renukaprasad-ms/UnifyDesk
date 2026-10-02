import { Link } from 'react-router'
import type { InboxParticipant } from '../../features/inbox/types'

export const inboxContactHref = (participant: InboxParticipant) => {
  if (participant.contactId && participant.contactResolution === 'MATCHED') {
    return `/contacts/${participant.contactId}`
  }

  const params = new URLSearchParams()
  if (participant.contactResolution === 'AMBIGUOUS') {
    params.set('q', participant.address)
    return `/contacts?${params.toString()}`
  }

  params.set('new', '1')
  if (participant.participantType === 'EMAIL_ADDRESS') params.set('email', participant.address)
  else params.set('phone', participant.address)
  return `/contacts?${params.toString()}`
}

export default function InboxContactAction({ participant }: { participant?: InboxParticipant | null }) {
  if (!participant) return null

  const matched = Boolean(participant.contactId && participant.contactResolution === 'MATCHED')
  const ambiguous = participant.contactResolution === 'AMBIGUOUS'
  const label = matched ? 'View Contact' : ambiguous ? 'Review Contacts' : 'Create Contact'

  return (
    <Link
      className="secondary-button shrink-0 px-3 py-2 text-xs"
      to={inboxContactHref(participant)}
      title={
        matched
          ? 'Open the canonical Contact linked to this Inbox participant'
          : ambiguous
            ? 'Review matching Contacts before linking this participant'
            : 'Explicitly create a Contact from this Inbox participant'
      }
    >
      {label}
    </Link>
  )
}
