import { afterEach, describe, expect, it } from 'vitest'
import { applyCompanyTheme, brandContrastColor } from './companyTheme'

afterEach(() => {
  document.documentElement.removeAttribute('data-color-scheme')
  document.documentElement.removeAttribute('style')
  window.localStorage.clear()
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
