import { describe, expect, it } from 'vitest'
import type { BroadcastMessageSummary, BroadcastStats } from '../../features/broadcast/types'
import broadcastReducer, { applyBroadcastProgress, fetchBroadcastHistory } from './broadcastSlice'

const stats = (accepted = 0): BroadcastStats => ({
  total: 10,
  pending: 10 - accepted,
  processing: 0,
  accepted,
  failed: 0,
  bounced: 0,
  unknown: 0,
  skipped: 0,
})

const message = (id: string, groupId: string): BroadcastMessageSummary => ({
  id,
  groupId,
  senderId: 'sender-1',
  runId: null,
  subject: 'Subject',
  bodyPreview: 'Preview',
  status: 'QUEUED',
  createdAt: '2026-09-21T10:00:00Z',
  updatedAt: '2026-09-21T10:00:00Z',
  stats: stats(),
})

describe('broadcastSlice realtime progress', () => {
  it('patches only the affected broadcast in the active group', () => {
    let state = broadcastReducer(undefined, { type: 'init' })

    state = broadcastReducer(
      state,
      fetchBroadcastHistory.pending('request-1', {
        companyId: 'company-1',
        epoch: 1,
        groupId: 'group-1',
      }),
    )

    state = broadcastReducer(
      state,
      fetchBroadcastHistory.fulfilled(
        {
          companyId: 'company-1',
          epoch: 1,
          groupId: 'group-1',
          append: false,
          page: {
            content: [message('broadcast-1', 'group-1'), message('broadcast-2', 'group-1')],
            number: 0,
            size: 30,
            totalElements: 2,
            totalPages: 1,
            first: true,
            last: true,
          },
        },
        'request-1',
        { companyId: 'company-1', epoch: 1, groupId: 'group-1' },
      ),
    )

    state = broadcastReducer(
      state,
      applyBroadcastProgress({
        runId: 'run-1',
        broadcastId: 'broadcast-2',
        groupId: 'group-1',
        status: 'RUNNING',
        stats: stats(4),
        progressPercent: 40,
      }),
    )

    expect(state.messages[0].status).toBe('QUEUED')
    expect(state.messages[1].status).toBe('RUNNING')
    expect(state.messages[1].stats.accepted).toBe(4)
    expect(state.messages[1].progressPercent).toBe(40)
  })

  it('ignores an A1 history response after A -> B -> A reaches a new epoch', () => {
    let state = broadcastReducer(undefined, { type: 'init' })

    state = broadcastReducer(
      state,
      fetchBroadcastHistory.pending('request-a1', {
        companyId: 'company-a',
        epoch: 1,
        groupId: 'group-a',
      }),
    )
    state = broadcastReducer(
      state,
      fetchBroadcastHistory.pending('request-b', {
        companyId: 'company-b',
        epoch: 2,
        groupId: 'group-b',
      }),
    )
    state = broadcastReducer(
      state,
      fetchBroadcastHistory.pending('request-a2', {
        companyId: 'company-a',
        epoch: 3,
        groupId: 'group-a',
      }),
    )

    state = broadcastReducer(
      state,
      fetchBroadcastHistory.fulfilled(
        {
          companyId: 'company-a',
          epoch: 1,
          groupId: 'group-a',
          append: false,
          page: {
            content: [message('stale-a1', 'group-a')],
            number: 0,
            size: 30,
            totalElements: 1,
            totalPages: 1,
            first: true,
            last: true,
          },
        },
        'request-a1',
        { companyId: 'company-a', epoch: 1, groupId: 'group-a' },
      ),
    )

    expect(state.historyCompanyId).toBe('company-a')
    expect(state.historyEpoch).toBe(3)
    expect(state.messages).toEqual([])
  })

  it('ignores progress for a group that is not currently loaded', () => {
    let state = broadcastReducer(undefined, { type: 'init' })
    state = {
      ...state,
      historyCompanyId: 'company-1',
      historyGroupId: 'group-1',
      messages: [message('broadcast-1', 'group-1')],
    }

    state = broadcastReducer(
      state,
      applyBroadcastProgress({
        runId: 'run-2',
        broadcastId: 'broadcast-1',
        groupId: 'group-2',
        status: 'RUNNING',
        stats: stats(5),
        progressPercent: 50,
      }),
    )

    expect(state.messages[0].status).toBe('QUEUED')
    expect(state.messages[0].stats.accepted).toBe(0)
  })
})
