import { cleanup, render } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import CompanyThemeProvider from './CompanyThemeProvider'
const state = vi.hoisted(() => ({ mode: 'SYSTEM' as 'SYSTEM' | 'LIGHT' | 'DARK' }))
vi.mock('../../store/hooks', () => ({ useAppSelector: (selector: () => unknown) => selector() }))
vi.mock('../../store/auth/authSlice', () => ({
  selectAuthUser: () => ({ appearanceMode: state.mode }),
}))
vi.mock('../../store/workspace/workspaceSlice', () => ({
  selectSelectedCompany: () => ({ brandColor: '#2563EB' }),
}))
afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  window.localStorage.clear()
  document.documentElement.removeAttribute('style')
  document.documentElement.removeAttribute('data-color-scheme')
})
it('tracks device changes live and unsubscribes when the user chooses an override', () => {
  const listeners = new Set<() => void>()
  const media = {
    matches: false,
    addEventListener: vi.fn((_name: string, fn: () => void) => listeners.add(fn)),
    removeEventListener: vi.fn((_name: string, fn: () => void) => listeners.delete(fn)),
  }
  vi.stubGlobal('matchMedia', () => media)
  state.mode = 'SYSTEM'
  const { rerender } = render(<CompanyThemeProvider />)
  expect(document.documentElement.dataset.colorScheme).toBe('light')
  media.matches = true
  listeners.forEach((fn) => fn())
  expect(document.documentElement.dataset.colorScheme).toBe('dark')
  expect(window.localStorage.getItem('unifydesk.appearanceMode')).toBe('SYSTEM')
  state.mode = 'LIGHT'
  rerender(<CompanyThemeProvider />)
  expect(listeners.size).toBe(0)
  media.matches = true
  listeners.forEach((fn) => fn())
  expect(document.documentElement.dataset.colorScheme).toBe('light')
})
