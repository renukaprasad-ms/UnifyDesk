import { describe, expect, it } from 'vitest'
import { utcWeekStart } from './analyticsBucketing'

describe('UTC reporting week boundaries', () => {
  it('groups Monday through Sunday under the same start date', () => {
    expect(utcWeekStart('2026-10-05')).toBe('2026-10-05')
    expect(utcWeekStart('2026-10-09')).toBe('2026-10-05')
    expect(utcWeekStart('2026-10-11')).toBe('2026-10-05')
  })
  it('starts a fresh week on the following Monday', () => {
    expect(utcWeekStart('2026-10-12')).toBe('2026-10-12')
  })
  it('handles year-boundary weeks', () => {
    expect(utcWeekStart('2027-01-01')).toBe('2026-12-28')
  })
  it('rejects invalid date strings', () => {
    expect(() => utcWeekStart('not-a-date')).toThrow('Invalid UTC reporting day')
  })
})
