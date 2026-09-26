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
  const details = includeConnectionDetails
    ? broadcastSenderConnectionDetails(sender)
    : []

  return (
    <>
      {broadcastSenderProviderLabel(sender)}
      {details.map((detail) => (
        <span key={detail}> · {detail}</span>
      ))}
    </>
  )
}
