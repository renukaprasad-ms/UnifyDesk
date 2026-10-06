import { describe, expect, it } from 'vitest'
import { brandContrastColor } from './companyTheme'

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
