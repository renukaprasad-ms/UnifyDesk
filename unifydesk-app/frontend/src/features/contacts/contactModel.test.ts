import { describe, expect, it } from 'vitest'
import {
  appendCursorPage,
  buildImportCommitInput,
  buildSelectionDescriptor,
  normalizeContactQuery,
  validateContactDraft,
} from './contactModel'

describe('contactModel', () => {
  it('validates create form fields', () => {
    expect(validateContactDraft({
      displayName: '',
      firstName: '',
      lastName: '',
      organization: '',
      notes: '',
      email: 'bad',
      phone: '12',
    })).toEqual(expect.objectContaining({
      displayName: expect.any(String),
      email: expect.any(String),
      phone: expect.any(String),
    }))
  })

  it('preserves opaque cursor progression while deduplicating loaded rows', () => {
    const current = [{ id: '1', displayName: 'A', updatedAt: '2026-01-01' }]
    const next = appendCursorPage(current, {
      items: [
        { id: '1', displayName: 'A', updatedAt: '2026-01-01' },
        { id: '2', displayName: 'B', updatedAt: '2026-01-02' },
      ],
      nextCursor: 'opaque',
      hasMore: true,
    }, false)
    expect(next.map((item) => item.id)).toEqual(['1', '2'])
  })

  it('uses a filter descriptor rather than enumerating all results', () => {
    expect(buildSelectionDescriptor(new Set(['1', '2']), true, { q: 'acme', sort: 'UPDATED_DESC' }))
      .toEqual({ type: 'FILTER', filter: { q: 'acme', sort: 'UPDATED_DESC' } })
  })

  it('bounds page size and trims search', () => {
    expect(normalizeContactQuery({ q: '  alice  ', size: 999 })).toEqual({ q: 'alice', size: 200 })
  })

  it('does not create consent from an ordinary import mapping', () => {
    const result = buildImportCommitInput({
      mappings: [
        { column: 'name', target: { type: 'BUILTIN', field: 'displayName' } },
        { column: 'unused', target: { type: 'IGNORE' } },
      ],
      duplicateStrategy: 'SKIP',
      consentMappings: [],
      idempotencyKey: 'fixed',
    })
    expect(result.consentMappings).toEqual([])
    expect(result.mappings).toHaveLength(1)
  })
})
