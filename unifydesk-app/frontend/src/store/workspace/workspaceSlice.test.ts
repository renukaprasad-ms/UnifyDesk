import { describe, expect, it } from 'vitest'
import { isWorkspaceEpochCurrent, workspaceEpochToken } from '../../features/workspace/workspaceEpoch'
import workspaceReducer, { selectCompany } from './workspaceSlice'

describe('workspace epoch', () => {
  it('changes generation for A -> B -> A so old A work stays stale', () => {
    let state = workspaceReducer(undefined, { type: 'init' })

    state = workspaceReducer(state, selectCompany('company-a'))
    const oldA = workspaceEpochToken(state.selectedCompanyId, state.epoch)
    expect(state.epoch).toBe(1)

    state = workspaceReducer(state, selectCompany('company-b'))
    expect(state.epoch).toBe(2)

    state = workspaceReducer(state, selectCompany('company-a'))
    expect(state.epoch).toBe(3)
    expect(isWorkspaceEpochCurrent(oldA, state.selectedCompanyId, state.epoch)).toBe(false)
  })

  it('does not invalidate work when the same workspace is selected again', () => {
    let state = workspaceReducer(undefined, { type: 'init' })
    state = workspaceReducer(state, selectCompany('company-a'))

    const epoch = state.epoch
    state = workspaceReducer(state, selectCompany('company-a'))

    expect(state.epoch).toBe(epoch)
  })
})
