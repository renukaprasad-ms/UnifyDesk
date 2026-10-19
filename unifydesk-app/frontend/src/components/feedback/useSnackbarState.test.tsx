import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import {
  externalSnackbarListeners,
  pendingExternalSnackbars,
  type ExternalSnackbarRequest,
} from './SnackbarProvider'
import { useSnackbarState } from './useSnackbarState'

let requests: ExternalSnackbarRequest[]
const listener = (request: ExternalSnackbarRequest) => requests.push(request)
beforeEach(() => {
  requests = []
  pendingExternalSnackbars.splice(0)
  externalSnackbarListeners.add(listener)
})
afterEach(() => externalSnackbarListeners.delete(listener))

describe('useSnackbarState', () => {
  it('publishes action feedback immediately and preserves local state', () => {
    const { result, unmount } = renderHook(() => useSnackbarState('', 'success'))
    act(() => result.current[1]('Changes saved'))
    expect(result.current[0]).toBe('Changes saved')
    expect(requests).toEqual([{ message: 'Changes saved', options: { variant: 'success' } }])
    unmount()
    expect(requests).toHaveLength(1)
  })

  it('does not announce resets or empty messages', () => {
    const { result } = renderHook(() => useSnackbarState<string | null>(null, 'error'))
    act(() => {
      result.current[1]('')
      result.current[1]('   ')
      result.current[1](null)
    })
    expect(requests).toEqual([])
  })

  it('supports functional updates and ignores stale callbacks after unmount', () => {
    const { result, unmount } = renderHook(() => useSnackbarState('', 'warning'))
    const update = result.current[1]
    act(() => {
      update('Check')
      update((previous) => `${previous} the selected account`)
    })
    expect(result.current[0]).toBe('Check the selected account')
    unmount()
    act(() => update('Stale request failed'))
    expect(requests).toHaveLength(2)
  })
})
