import { afterEach, describe, expect, it, vi } from 'vitest'
import { applyCompanyTheme, brandContrastColor } from './companyTheme'

afterEach(() => {
  document.documentElement.removeAttribute('data-color-scheme')
  document.documentElement.removeAttribute('style')
  window.localStorage.clear()
  vi.unstubAllGlobals()
})

describe('company brand contrast', () => {
  it('uses dark foreground for very light arbitrary brand colours', () => {
    expect(brandContrastColor('#FDE047')).toBe('#0F172A')
  })

  it('uses white foreground for dark arbitrary brand colours', () => {
    expect(brandContrastColor('#1D4ED8')).toBe('#FFFFFF')
  })

  it('falls back safely for invalid input', () => {
    expect(brandContrastColor('not-a-colour')).toBe('#FFFFFF')
  })
})

describe('company appearance application', () => {
  it('fully reapplies light mode after dark mode', () => {
    applyCompanyTheme('#2563EB', 'DARK')
    expect(document.documentElement.dataset.colorScheme).toBe('dark')
    expect(document.documentElement.style.getPropertyValue('--theme-bg')).toBe('#0f172a')

    applyCompanyTheme('#2563EB', 'LIGHT')

    expect(document.documentElement.dataset.colorScheme).toBe('light')
    expect(document.documentElement.style.colorScheme).toBe('light')
    expect(document.documentElement.style.getPropertyValue('--theme-bg')).toBe('#f8fafd')
    expect(document.documentElement.style.getPropertyValue('--theme-surface')).toBe('#ffffff')
    expect(window.localStorage.getItem('unifydesk.appearanceMode')).toBe('LIGHT')
  })
})

it('defaults to the device theme while storing the preference rather than the resolved color', () => {
  vi.stubGlobal(
    'matchMedia',
    vi.fn(() => ({ matches: true })),
  )
  applyCompanyTheme('#2563EB')
  expect(document.documentElement.dataset.colorScheme).toBe('dark')
  expect(window.localStorage.getItem('unifydesk.appearanceMode')).toBe('SYSTEM')
  vi.stubGlobal(
    'matchMedia',
    vi.fn(() => ({ matches: false })),
  )
  applyCompanyTheme('#2563EB', 'SYSTEM')
  expect(document.documentElement.dataset.colorScheme).toBe('light')
  expect(document.documentElement.style.background).toBe('rgb(248, 250, 253)')
  expect(window.localStorage.getItem('unifydesk.appearanceMode')).toBe('SYSTEM')
})
it('lets explicit light and dark choices override the device', () => {
  vi.stubGlobal(
    'matchMedia',
    vi.fn(() => ({ matches: true })),
  )
  applyCompanyTheme('#2563EB', 'LIGHT')
  expect(document.documentElement.dataset.colorScheme).toBe('light')
  vi.stubGlobal(
    'matchMedia',
    vi.fn(() => ({ matches: false })),
  )
  applyCompanyTheme('#2563EB', 'DARK')
  expect(document.documentElement.dataset.colorScheme).toBe('dark')
})
