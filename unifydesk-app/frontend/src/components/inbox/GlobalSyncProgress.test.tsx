import { act, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AsyncJob } from '../../features/asyncJob/types'
import { realtimeClient } from '../../features/realtime/realtimeClient'
import GlobalSyncProgress from './GlobalSyncProgress'

vi.mock('../../features/realtime/realtimeClient', () => ({
  realtimeClient: { subscribe: vi.fn(() => () => {}) },
}))

const job: AsyncJob = {
  jobId: 'zoho-sync-1',
  companyId: 'company-1',
  userId: 'user-1',
  jobType: 'ZOHO_MAIL_SYNC',
  status: 'QUEUED',
  totalUnits: 0,
  completedUnits: 0,
  progressPercent: 0,
  statusMessage: 'Zoho Mail sync queued',
  createdAt: '2026-10-08T04:00:00Z',
  updatedAt: '2026-10-08T04:00:00Z',
}

const publishProgress = (payload: AsyncJob) => {
  const listener = vi.mocked(realtimeClient.subscribe).mock.calls.find(
    ([eventType]) => eventType === 'async.job.progress',
  )?.[1]
  expect(listener).toBeDefined()
  act(() => {
    listener?.({ type: 'async.job.progress', occurredAt: payload.updatedAt, payload })
  })
}

describe('GlobalSyncProgress', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('displays Zoho Mail jobs from queued through completion', () => {
    render(<GlobalSyncProgress />)
    publishProgress(job)
    expect(screen.getByText('Zoho Mail sync')).toBeInTheDocument()
    expect(screen.getByText('Sync in progress')).toBeInTheDocument()
    expect(screen.getByText('Zoho Mail sync queued')).toBeInTheDocument()

    publishProgress({
      ...job,
      status: 'RUNNING',
      totalUnits: 10,
      completedUnits: 5,
      progressPercent: 50,
      statusMessage: 'Importing Zoho Mail history',
    })
    expect(screen.getByText('Importing Zoho Mail history')).toBeInTheDocument()

    publishProgress({
      ...job,
      status: 'COMPLETED',
      totalUnits: 10,
      completedUnits: 10,
      progressPercent: 100,
      statusMessage: 'Zoho Mail sync complete',
    })
    expect(screen.getByText('Sync finished')).toBeInTheDocument()
    expect(screen.getByText('Zoho Mail sync complete')).toBeInTheDocument()
  })

  it('ignores other async job types', () => {
    render(<GlobalSyncProgress />)
    publishProgress({ ...job, jobType: 'INBOX_READ_SYNC' })
    expect(screen.queryByText('Zoho Mail sync')).not.toBeInTheDocument()
  })
})
