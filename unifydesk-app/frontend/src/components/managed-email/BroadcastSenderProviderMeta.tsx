import type { BroadcastSender } from '../../features/broadcast/types'
import {
  broadcastSenderConnectionDetails,
  broadcastSenderProviderLabel,
} from '../../features/managedEmail/privacy'

export default function BroadcastSenderProviderMeta({
  sender,
  includeConnectionDetails = false,
}: {
  sender?: BroadcastSender | null
  includeConnectionDetails?: boolean
}) {
  const managedSms =
    sender?.provider === 'MSG91_SMS' && sender.managementMode === 'MESSAGING_MANAGED'
  const details =
    includeConnectionDetails && !managedSms ? broadcastSenderConnectionDetails(sender) : []
  const label = managedSms ? 'UnifyDesk Managed SMS' : broadcastSenderProviderLabel(sender)

  return (
    <>
      {label}
      {details.map((detail) => (
        <span key={detail}> · {detail}</span>
      ))}
    </>
  )
}
