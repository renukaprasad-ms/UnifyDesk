import type { BroadcastSender } from '../broadcast/types'

export const isMessagingManagedSender = (sender?: Pick<BroadcastSender, 'managementMode'> | null) =>
  sender?.managementMode === 'MESSAGING_MANAGED'

export const managedConnectionDisplayName = (
  sender: Pick<BroadcastSender, 'managementMode'> | null | undefined,
  connectionName: string,
) => (isMessagingManagedSender(sender) ? 'UnifyDesk Managed Email' : connectionName)

export const broadcastSenderProviderLabel = (
  sender?: Pick<BroadcastSender, 'managementMode' | 'provider'> | null,
) => {
  if (isMessagingManagedSender(sender)) return 'UnifyDesk Managed Email'

  const provider = sender?.provider
  if (!provider) return 'Legacy mailbox'
  if (provider === 'AMAZON_SES') return 'Amazon SES'
  if (provider === 'UNIFYDESK_EMAIL') return 'UnifyDesk Email'
  if (provider === 'AZURE_EMAIL') return 'Azure Email'
  if (provider === 'SENDGRID') return 'SendGrid'
  if (provider === 'MSG91_EMAIL') return 'MSG91 Email'
  if (provider === 'MSG91_SMS') return 'MSG91 SMS'
  return String(provider).replaceAll('_', ' ')
}

export const broadcastSenderConnectionDetails = (sender?: BroadcastSender | null): string[] => {
  if (!sender || isMessagingManagedSender(sender)) return []

  const details: string[] = []
  if (sender.provider !== 'MSG91_SMS' && sender.provider !== 'MSG91_EMAIL' && sender.provider !== 'UNIFYDESK_EMAIL' && sender.region) {
    details.push(sender.region)
  }
  if (sender.maxSendRate) {
    details.push(`up to ${sender.maxSendRate}/sec`)
  }
  return details
}
