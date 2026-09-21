import { createAsyncThunk, createSlice, type PayloadAction } from '@reduxjs/toolkit'
import { channelApi } from '../../features/channel/channelApi'
import type { ChannelConnection } from '../../features/channel/types'
import { inboxApi } from '../../features/inbox/inboxApi'
import type {
  InboxConversation,
  InboxConversationDetail,
  InboxMessage,
  InboxRealtimeSnapshot,
} from '../../features/inbox/types'

type RequestStatus = 'idle' | 'loading' | 'ready' | 'error'

type InboxState = {
  connections: ChannelConnection[]
  connectionsCompanyId: string | null
  connectionsStatus: RequestStatus
  scopeCompanyId: string | null
  scopeConnectionId: string | null
  scopeFolder: 'INBOX' | 'SENT'
  conversations: InboxConversation[]
  conversationPage: number
  conversationTotalPages: number
  conversationTotalElements: number
  conversationsStatus: RequestStatus
  loadingMore: boolean
  detailCompanyId: string | null
  detailConversationId: string | null
  selectedConversation: InboxConversation | null
  messages: InboxMessage[]
  detailStatus: RequestStatus
}

const initialState: InboxState = {
  connections: [],
  connectionsCompanyId: null,
  connectionsStatus: 'idle',
  scopeCompanyId: null,
  scopeConnectionId: null,
  scopeFolder: 'INBOX',
  conversations: [],
  conversationPage: 0,
  conversationTotalPages: 0,
  conversationTotalElements: 0,
  conversationsStatus: 'idle',
  loadingMore: false,
  detailCompanyId: null,
  detailConversationId: null,
  selectedConversation: null,
  messages: [],
  detailStatus: 'idle',
}

const scopeKey = (companyId: string, connectionId: string, folder: 'INBOX' | 'SENT') =>
  `${companyId}:${connectionId}:${folder}`

const currentScopeKey = (state: InboxState) =>
  state.scopeCompanyId && state.scopeConnectionId
    ? scopeKey(state.scopeCompanyId, state.scopeConnectionId, state.scopeFolder)
    : null

const sortConversations = (items: InboxConversation[]) =>
  items.sort((left, right) => {
    const leftAt = left.lastMessageAt ? Date.parse(left.lastMessageAt) : 0
    const rightAt = right.lastMessageAt ? Date.parse(right.lastMessageAt) : 0
    return rightAt - leftAt
  })

const sortMessages = (items: InboxMessage[]) =>
  items.sort((left, right) => {
    const leftAt = left.sentAt ? Date.parse(left.sentAt) : 0
    const rightAt = right.sentAt ? Date.parse(right.sentAt) : 0
    return leftAt - rightAt
  })

export const fetchEmailConnections = createAsyncThunk<
  { companyId: string; connections: ChannelConnection[] },
  { companyId: string; force?: boolean },
  { state: { inbox: InboxState } }
>(
  'inbox/fetchEmailConnections',
  async ({ companyId }) => ({
    companyId,
    connections: await channelApi.list(companyId, 'EMAIL'),
  }),
  {
    condition: ({ companyId, force }, { getState }) => {
      const state = getState().inbox
      if (force) return true
      if (state.connectionsCompanyId !== companyId) return true
      return state.connectionsStatus === 'idle' || state.connectionsStatus === 'error'
    },
  },
)

export const fetchInboxConversations = createAsyncThunk<
  {
    companyId: string
    connectionId: string
    folder: 'INBOX' | 'SENT'
    append: boolean
    page: Awaited<ReturnType<typeof inboxApi.conversations>>
  },
  {
    companyId: string
    connectionId: string
    folder: 'INBOX' | 'SENT'
    page?: number
    append?: boolean
    force?: boolean
  },
  { state: { inbox: InboxState } }
>(
  'inbox/fetchConversations',
  async ({ companyId, connectionId, folder, page = 0, append = false }) => ({
    companyId,
    connectionId,
    folder,
    append,
    page: await inboxApi.conversations(companyId, page, 30, connectionId, folder),
  }),
  {
    condition: ({ companyId, connectionId, folder, append = false, force }, { getState }) => {
      const state = getState().inbox
      const sameScope = currentScopeKey(state) === scopeKey(companyId, connectionId, folder)
      if (force) return true
      if (!sameScope) return true
      if (append) return !state.loadingMore
      return state.conversationsStatus === 'idle' || state.conversationsStatus === 'error'
    },
  },
)

export const fetchInboxConversationDetail = createAsyncThunk<
  { companyId: string; conversationId: string; detail: InboxConversationDetail },
  { companyId: string; conversationId: string; force?: boolean },
  { state: { inbox: InboxState } }
>(
  'inbox/fetchConversationDetail',
  async ({ companyId, conversationId }) => ({
    companyId,
    conversationId,
    detail: await inboxApi.conversationDetail(companyId, conversationId),
  }),
  {
    condition: ({ companyId, conversationId, force }, { getState }) => {
      const state = getState().inbox
      if (force) return true
      if (
        state.detailCompanyId !== companyId ||
        state.detailConversationId !== conversationId
      ) {
        return true
      }
      return state.detailStatus === 'idle' || state.detailStatus === 'error'
    },
  },
)

const inboxSlice = createSlice({
  name: 'inbox',
  initialState,
  reducers: {
    clearInboxState: () => initialState,
    clearConversationDetail(state) {
      state.detailCompanyId = null
      state.detailConversationId = null
      state.selectedConversation = null
      state.messages = []
      state.detailStatus = 'idle'
    },
    applyRealtimeSnapshot(
      state,
      action: PayloadAction<{
        connectionId: string
        snapshot: InboxRealtimeSnapshot
      }>,
    ) {
      const { connectionId, snapshot } = action.payload

      if (state.scopeConnectionId === connectionId) {
        const byId = new Map(state.conversations.map((item) => [item.id, item]))
        let total = state.conversationTotalElements
        const allLoaded = total <= state.conversations.length

        for (const missingId of snapshot.missingConversationIds) {
          if (byId.delete(missingId)) total = Math.max(0, total - 1)
        }
        for (const conversation of snapshot.conversations) {
          const wasLoaded = byId.has(conversation.id)
          byId.set(conversation.id, conversation)
          if (!wasLoaded && allLoaded) total += 1
        }

        state.conversations = sortConversations(Array.from(byId.values()))
        state.conversationTotalElements = Math.max(total, state.conversations.length)
      }

      if (state.detailConversationId) {
        const updatedConversation = snapshot.conversations.find(
          (item) => item.id === state.detailConversationId,
        )
        if (updatedConversation) state.selectedConversation = updatedConversation

        const byId = new Map(state.messages.map((item) => [item.id, item]))
        snapshot.missingMessageIds.forEach((id) => byId.delete(id))
        snapshot.messages
          .filter((message) => message.conversationId === state.detailConversationId)
          .forEach((message) => byId.set(message.id, message))
        state.messages = sortMessages(Array.from(byId.values()))
      }
    },
    applyConversationReadState(
      state,
      action: PayloadAction<{ conversationId: string; read: boolean }>,
    ) {
      const { conversationId, read } = action.payload
      if (!read) return

      state.conversations = state.conversations.map((conversation) =>
        conversation.id === conversationId ? { ...conversation, unreadCount: 0 } : conversation,
      )
      if (state.selectedConversation?.id === conversationId) {
        state.selectedConversation = { ...state.selectedConversation, unreadCount: 0 }
        state.messages = state.messages.map((message) =>
          message.direction === 'INBOUND' ? { ...message, read: true } : message,
        )
      }
    },
    applyMessageReadState(
      state,
      action: PayloadAction<{ messageId: string; read: boolean }>,
    ) {
      const { messageId, read } = action.payload
      state.messages = state.messages.map((message) =>
        message.id === messageId ? { ...message, read } : message,
      )
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchEmailConnections.pending, (state, action) => {
        if (state.connectionsCompanyId !== action.meta.arg.companyId) {
          state.connections = []
        }
        state.connectionsCompanyId = action.meta.arg.companyId
        state.connectionsStatus = 'loading'
      })
      .addCase(fetchEmailConnections.fulfilled, (state, action) => {
        if (state.connectionsCompanyId !== action.payload.companyId) return
        state.connections = action.payload.connections
        state.connectionsStatus = 'ready'
      })
      .addCase(fetchEmailConnections.rejected, (state, action) => {
        if (action.meta.condition) return
        state.connectionsStatus = 'error'
      })
      .addCase(fetchInboxConversations.pending, (state, action) => {
        const { companyId, connectionId, folder, append = false } = action.meta.arg
        const nextKey = scopeKey(companyId, connectionId, folder)
        if (currentScopeKey(state) !== nextKey) {
          state.scopeCompanyId = companyId
          state.scopeConnectionId = connectionId
          state.scopeFolder = folder
          state.conversations = []
          state.conversationPage = 0
          state.conversationTotalPages = 0
          state.conversationTotalElements = 0
        }
        if (append) state.loadingMore = true
        else state.conversationsStatus = 'loading'
      })
      .addCase(fetchInboxConversations.fulfilled, (state, action) => {
        const { companyId, connectionId, folder, append, page } = action.payload
        if (currentScopeKey(state) !== scopeKey(companyId, connectionId, folder)) return

        if (append) {
          const byId = new Map(state.conversations.map((item) => [item.id, item]))
          page.items.forEach((item) => byId.set(item.id, item))
          state.conversations = sortConversations(Array.from(byId.values()))
        } else {
          state.conversations = page.items
        }

        state.conversationPage = page.page
        state.conversationTotalPages = page.totalPages
        state.conversationTotalElements = page.totalElements
        state.conversationsStatus = 'ready'
        state.loadingMore = false
      })
      .addCase(fetchInboxConversations.rejected, (state, action) => {
        if (action.meta.condition) return
        state.conversationsStatus = 'error'
        state.loadingMore = false
      })
      .addCase(fetchInboxConversationDetail.pending, (state, action) => {
        const { companyId, conversationId } = action.meta.arg
        if (
          state.detailCompanyId !== companyId ||
          state.detailConversationId !== conversationId
        ) {
          state.selectedConversation = null
          state.messages = []
        }
        state.detailCompanyId = companyId
        state.detailConversationId = conversationId
        state.detailStatus = 'loading'
      })
      .addCase(fetchInboxConversationDetail.fulfilled, (state, action) => {
        if (
          state.detailCompanyId !== action.payload.companyId ||
          state.detailConversationId !== action.payload.conversationId
        ) {
          return
        }
        state.selectedConversation = action.payload.detail.conversation
        state.messages = action.payload.detail.messages
        state.detailStatus = 'ready'
      })
      .addCase(fetchInboxConversationDetail.rejected, (state, action) => {
        if (action.meta.condition) return
        state.detailStatus = 'error'
      })
  },
})

export const {
  applyConversationReadState,
  applyMessageReadState,
  applyRealtimeSnapshot,
  clearConversationDetail,
  clearInboxState,
} = inboxSlice.actions

export default inboxSlice.reducer
