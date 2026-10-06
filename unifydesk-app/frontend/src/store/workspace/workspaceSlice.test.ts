import { describe, expect, it } from 'vitest'
import { isWorkspaceEpochCurrent, workspaceEpochToken } from '../../features/workspace/workspaceEpoch'
import workspaceReducer, { selectCompany, setCompanies } from './workspaceSlice'

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

  it('never auto-selects a suspended workspace', () => {
    let state = workspaceReducer(undefined, { type: 'init' })
    state = workspaceReducer(
      state,
      setCompanies([
        {
          companyId: 'company-suspended',
          name: 'Suspended Company',
          slug: 'suspended-company',
          status: 'SUSPENDED',
          role: 'OWNER',
          brandColor: '#2563EB',
        },
      ]),
    )

    expect(state.selectedCompanyId).toBeNull()
  })

  it('auto-selects the only active workspace even when a suspended workspace is visible', () => {
    let state = workspaceReducer(undefined, { type: 'init' })
    state = workspaceReducer(
      state,
      setCompanies([
        {
          companyId: 'company-suspended',
          name: 'Suspended Company',
          slug: 'suspended-company',
          status: 'SUSPENDED',
          role: 'OWNER',
          brandColor: '#2563EB',
        },
        {
          companyId: 'company-active',
          name: 'Active Company',
          slug: 'active-company',
          status: 'ACTIVE',
          role: 'OWNER',
          brandColor: '#0EA5E9',
        },
      ]),
    )

    expect(state.selectedCompanyId).toBe('company-active')
  })

  it('does not invalidate work when the same workspace is selected again', () => {
    let state = workspaceReducer(undefined, { type: 'init' })
    state = workspaceReducer(state, selectCompany('company-a'))

    const epoch = state.epoch
    state = workspaceReducer(state, selectCompany('company-a'))

    expect(state.epoch).toBe(epoch)
  })
})
