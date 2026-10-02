import { createAsyncThunk, createSlice, type PayloadAction } from '@reduxjs/toolkit'
import { broadcastApi } from '../../features/broadcast/broadcastApi'
import type {
  BroadcastGroup,
  BroadcastMessage,
  BroadcastMessageStatus,
  BroadcastMessageSummary,
  BroadcastProgressEvent,
} from '../../features/broadcast/types'
import type { ChannelType } from '../../features/channel/types'
import { sessionCleared } from '../auth/authSlice'

type RequestStatus = 'idle' | 'loading' | 'ready' | 'error'

type BroadcastState = {
  groupsCompanyId: string | null
  groupsEpoch: number | null
  groupsChannelType: ChannelType | null
  groups: BroadcastGroup[]
  groupsPage: number
  groupsTotalPages: number
  groupsStatus: RequestStatus
  loadingMoreGroups: boolean
  historyCompanyId: string | null
  historyEpoch: number | null
  historyGroupId: string | null
  messages: BroadcastMessageSummary[]
  historyPage: number
  historyTotalPages: number
  historyStatus: RequestStatus
  loadingMoreHistory: boolean
}

const initialState: BroadcastState = {
  groupsCompanyId: null,
  groupsEpoch: null,
  groupsChannelType: null,
  groups: [],
  groupsPage: 0,
  groupsTotalPages: 0,
  groupsStatus: 'idle',
  loadingMoreGroups: false,
  historyCompanyId: null,
  historyEpoch: null,
  historyGroupId: null,
  messages: [],
  historyPage: 0,
  historyTotalPages: 0,
  historyStatus: 'idle',
  loadingMoreHistory: false,
}

const messageStatusFromRun = (status: BroadcastProgressEvent['status']): BroadcastMessageStatus =>
  status === 'PREPARING' ? 'QUEUED' : status

const summaryFromMessage = (message: BroadcastMessage): BroadcastMessageSummary => ({
  id: message.id,
  groupId: message.groupId,
  audienceSourceType: message.audienceSourceType,
  audienceSourceId: message.audienceSourceId,
  audienceSnapshotId: message.audienceSnapshotId,
  senderId: message.senderId,
  legacyConnectionId: message.legacyConnectionId,
  runId: message.runId,
  subject: message.subject,
  bodyPreview: message.bodyText?.slice(0, 240) ?? '',
  status: message.status,
  scheduledAt: message.scheduledAt,
  createdAt: message.createdAt,
  updatedAt: message.updatedAt,
  stats: message.stats,
  progressPercent: message.progressPercent,
})

export const fetchBroadcastGroups = createAsyncThunk<
  {
    companyId: string
    epoch: number
    channelType: ChannelType
    append: boolean
    page: Awaited<ReturnType<typeof broadcastApi.groups>>
  },
  {
    companyId: string
    epoch: number
    channelType: ChannelType
    page?: number
    append?: boolean
    force?: boolean
  },
  { state: { broadcast: BroadcastState } }
>(
  'broadcast/fetchGroups',
  async ({ companyId, epoch, channelType, page = 0, append = false }) => ({
    companyId,
    epoch,
    channelType,
    append,
    page: await broadcastApi.groups(companyId, channelType, page),
  }),
  {
    condition: ({ companyId, epoch, channelType, append = false, force }, { getState }) => {
      if (force) return true
      const state = getState().broadcast
      if (
        state.groupsCompanyId !== companyId ||
        state.groupsEpoch !== epoch ||
        state.groupsChannelType !== channelType
      ) {
        return true
      }
      if (append) return !state.loadingMoreGroups
      return state.groupsStatus === 'idle' || state.groupsStatus === 'error'
    },
  },
)

export const fetchBroadcastHistory = createAsyncThunk<
  {
    companyId: string
    epoch: number
    groupId: string
    append: boolean
    page: Awaited<ReturnType<typeof broadcastApi.history>>
  },
  {
    companyId: string
    epoch: number
    groupId: string
    page?: number
    append?: boolean
    force?: boolean
  },
  { state: { broadcast: BroadcastState } }
>(
  'broadcast/fetchHistory',
  async ({ companyId, epoch, groupId, page = 0, append = false }) => ({
    companyId,
    epoch,
    groupId,
    append,
    page: await broadcastApi.history(companyId, groupId, page),
  }),
  {
    condition: ({ companyId, epoch, groupId, append = false, force }, { getState }) => {
      if (force) return true
      const state = getState().broadcast
      const same =
        state.historyCompanyId === companyId &&
        state.historyEpoch === epoch &&
        state.historyGroupId === groupId
      if (!same) return true
      if (append) return !state.loadingMoreHistory
      return state.historyStatus === 'idle' || state.historyStatus === 'error'
    },
  },
)

const broadcastSlice = createSlice({
  name: 'broadcast',
  initialState,
  reducers: {
    upsertBroadcastGroup(state, action: PayloadAction<BroadcastGroup>) {
      const index = state.groups.findIndex((group) => group.id === action.payload.id)
      if (index >= 0) state.groups[index] = action.payload
      else state.groups.unshift(action.payload)
    },
    removeBroadcastGroupFromState(state, action: PayloadAction<string>) {
      state.groups = state.groups.filter((group) => group.id !== action.payload)
    },
    applyBroadcastMutation(state, action: PayloadAction<BroadcastMessage>) {
      const summary = summaryFromMessage(action.payload)
      if (state.historyGroupId !== summary.groupId) return
      const index = state.messages.findIndex((message) => message.id === summary.id)
      if (index >= 0) state.messages[index] = summary
      else state.messages.unshift(summary)
    },
    applyBroadcastProgress(state, action: PayloadAction<BroadcastProgressEvent>) {
      if (state.historyGroupId !== action.payload.groupId) return
      const message = state.messages.find((item) => item.id === action.payload.broadcastId)
      if (!message) return
      message.runId = action.payload.runId
      message.status = messageStatusFromRun(action.payload.status)
      message.stats = action.payload.stats
      message.progressPercent = action.payload.progressPercent
    },
    clearBroadcastHistory(state) {
      state.historyCompanyId = null
      state.historyEpoch = null
      state.historyGroupId = null
      state.messages = []
      state.historyPage = 0
      state.historyTotalPages = 0
      state.historyStatus = 'idle'
      state.loadingMoreHistory = false
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchBroadcastGroups.pending, (state, action) => {
        const { companyId, epoch, channelType, append = false } = action.meta.arg
        if (
          state.groupsCompanyId !== companyId ||
          state.groupsEpoch !== epoch ||
          state.groupsChannelType !== channelType
        ) {
          state.groups = []
        }
        state.groupsCompanyId = companyId
        state.groupsEpoch = epoch
        state.groupsChannelType = channelType
        if (append) state.loadingMoreGroups = true
        else state.groupsStatus = 'loading'
      })
      .addCase(fetchBroadcastGroups.fulfilled, (state, action) => {
        if (
          state.groupsCompanyId !== action.payload.companyId ||
          state.groupsEpoch !== action.payload.epoch ||
          state.groupsChannelType !== action.payload.channelType
        ) {
          return
        }
        if (action.payload.append) {
          const byId = new Map(state.groups.map((group) => [group.id, group]))
          action.payload.page.content.forEach((group) => byId.set(group.id, group))
          state.groups = Array.from(byId.values())
        } else {
          state.groups = action.payload.page.content
        }
        state.groupsPage = action.payload.page.number
        state.groupsTotalPages = action.payload.page.totalPages
        state.groupsStatus = 'ready'
        state.loadingMoreGroups = false
      })
      .addCase(fetchBroadcastGroups.rejected, (state, action) => {
        if (action.meta.condition) return
        if (
          state.groupsCompanyId !== action.meta.arg.companyId ||
          state.groupsEpoch !== action.meta.arg.epoch ||
          state.groupsChannelType !== action.meta.arg.channelType
        ) return
        if (action.meta.arg.append) state.loadingMoreGroups = false
        else state.groupsStatus = 'error'
      })
      .addCase(fetchBroadcastHistory.pending, (state, action) => {
        const { companyId, epoch, groupId, append = false } = action.meta.arg
        const same =
          state.historyCompanyId === companyId &&
          state.historyEpoch === epoch &&
          state.historyGroupId === groupId
        if (!same) {
          state.messages = []
          state.historyPage = 0
          state.historyTotalPages = 0
        }
        state.historyCompanyId = companyId
        state.historyEpoch = epoch
        state.historyGroupId = groupId
        if (append) state.loadingMoreHistory = true
        else state.historyStatus = 'loading'
      })
      .addCase(fetchBroadcastHistory.fulfilled, (state, action) => {
        if (
          state.historyCompanyId !== action.payload.companyId ||
          state.historyEpoch !== action.payload.epoch ||
          state.historyGroupId !== action.payload.groupId
        ) {
          return
        }
        if (action.payload.append) {
          const byId = new Map(state.messages.map((message) => [message.id, message]))
          action.payload.page.content.forEach((message) => byId.set(message.id, message))
          state.messages = Array.from(byId.values())
        } else {
          state.messages = action.payload.page.content
        }
        state.historyPage = action.payload.page.number
        state.historyTotalPages = action.payload.page.totalPages
        state.historyStatus = 'ready'
        state.loadingMoreHistory = false
      })
      .addCase(fetchBroadcastHistory.rejected, (state, action) => {
        if (action.meta.condition) return
        if (
          state.historyCompanyId !== action.meta.arg.companyId ||
          state.historyEpoch !== action.meta.arg.epoch ||
          state.historyGroupId !== action.meta.arg.groupId
        ) return
        state.historyStatus = 'error'
        state.loadingMoreHistory = false
      })
      .addCase(sessionCleared, () => initialState)
  },
})

export const {
  applyBroadcastMutation,
  applyBroadcastProgress,
  clearBroadcastHistory,
  removeBroadcastGroupFromState,
  upsertBroadcastGroup,
} = broadcastSlice.actions

export default broadcastSlice.reducer
