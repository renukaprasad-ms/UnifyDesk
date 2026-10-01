import { describe, expect, it } from 'vitest'
import type { InboxConversation, InboxMessage } from '../../features/inbox/types'
import inboxReducer, {
  applyRealtimeSnapshot,
  fetchInboxConversationDetail,
  fetchInboxConversations,
} from './inboxSlice'

const conversation = (id: string, preview: string, unreadCount: number): InboxConversation => ({
  id,
  connectionId: 'connection-1',
  externalConversationId: `thread-${id}`,
  providerType: 'GMAIL',
  subject: 'Subject',
  lastMessagePreview: preview,
  lastMessageAt: '2026-09-21T10:00:00Z',
  unreadCount,
  status: 'OPEN',
  participants: [
    {
      address: 'sender@example.com',
      participantType: 'EMAIL_ADDRESS',
    },
  ],
})

const message = (id: string, bodyText: string): InboxMessage => ({
  id,
  conversationId: 'conversation-1',
  externalMessageId: `external-${id}`,
  providerFolderKey: 'INBOX',
  direction: 'INBOUND',
  status: 'RECEIVED',
  subject: 'Subject',
  snippet: bodyText,
  bodyText,
  to: ['owner@example.com'],
  cc: [],
  bcc: [],
  sentAt: '2026-09-21T10:00:00Z',
  read: false,
  attachments: [],
})

describe('inboxSlice realtime updates', () => {
  it('patches the changed conversation and appends only the new thread message', () => {
    let state = inboxReducer(undefined, { type: 'init' })

    state = inboxReducer(
      state,
      fetchInboxConversations.pending('request-1', {
        companyId: 'company-1',
        connectionId: 'connection-1',
        folder: 'INBOX',
      }),
    )

    state = inboxReducer(
      state,
      fetchInboxConversations.fulfilled(
        {
          companyId: 'company-1',
          connectionId: 'connection-1',
          folder: 'INBOX',
          append: false,
          page: {
            items: [conversation('conversation-1', 'Old preview', 0)],
            page: 0,
            size: 30,
            totalElements: 1,
            totalPages: 1,
          },
        },
        'request-1',
        {
          companyId: 'company-1',
          connectionId: 'connection-1',
          folder: 'INBOX',
        },
      ),
    )

    state = inboxReducer(
      state,
      fetchInboxConversationDetail.pending('request-2', {
        companyId: 'company-1',
        conversationId: 'conversation-1',
      }),
    )

    state = inboxReducer(
      state,
      fetchInboxConversationDetail.fulfilled(
        {
          companyId: 'company-1',
          conversationId: 'conversation-1',
          detail: {
            conversation: conversation('conversation-1', 'Old preview', 0),
            messages: [message('message-1', 'Old message')],
          },
        },
        'request-2',
        {
          companyId: 'company-1',
          conversationId: 'conversation-1',
        },
      ),
    )

    state = inboxReducer(
      state,
      applyRealtimeSnapshot({
        connectionId: 'connection-1',
        snapshot: {
          conversations: [conversation('conversation-1', 'New preview', 1)],
          messages: [message('message-2', 'New message')],
          missingConversationIds: [],
          missingMessageIds: [],
        },
      }),
    )

    expect(state.conversations).toHaveLength(1)
    expect(state.conversations[0].lastMessagePreview).toBe('New preview')
    expect(state.conversations[0].unreadCount).toBe(1)
    expect(state.messages.map((item) => item.id)).toEqual(['message-1', 'message-2'])
  })

  it('removes only ids reported missing by the realtime snapshot', () => {
    let state = inboxReducer(undefined, { type: 'init' })
    state = {
      ...state,
      scopeCompanyId: 'company-1',
      scopeConnectionId: 'connection-1',
      scopeFolder: 'INBOX',
      conversations: [conversation('conversation-1', 'Preview', 1)],
      conversationTotalElements: 1,
      detailCompanyId: 'company-1',
      detailConversationId: 'conversation-1',
      selectedConversation: conversation('conversation-1', 'Preview', 1),
      messages: [message('message-1', 'Old message'), message('message-2', 'Delete me')],
    }

    state = inboxReducer(
      state,
      applyRealtimeSnapshot({
        connectionId: 'connection-1',
        snapshot: {
          conversations: [],
          messages: [],
          missingConversationIds: [],
          missingMessageIds: ['message-2'],
        },
      }),
    )

    expect(state.messages.map((item) => item.id)).toEqual(['message-1'])
    expect(state.conversations).toHaveLength(1)
  })
})
