import type { InboxConversation } from '../../features/inbox/types'

export const conversationParticipant = (
  conversation: InboxConversation,
  ownAddress?: string,
) => {
  const participant = conversation.participants.find(
    (item) => item.address.toLowerCase() !== ownAddress?.toLowerCase(),
  )
  return participant?.displayName || participant?.address || ownAddress || 'Email'
}
