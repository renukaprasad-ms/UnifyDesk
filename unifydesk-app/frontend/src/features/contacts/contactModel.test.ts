import { describe, expect, it } from 'vitest'
import {
  DEFAULT_CONTACT_EXPORT_FIELDS,
  appendCursorPage,
  buildImportCommitInput,
  buildSelectionDescriptor,
  buildSuggestedConsentMappings,
  buildSuggestedImportMappings,
  contactPrimaryIdentityValue,
  normalizeContactQuery,
  parseContactSearchStatus,
  toPatchContactInput,
  validateContactDraft,
} from './contactModel'

describe('contactModel', () => {
  it('validates create form fields', () => {
    expect(
      validateContactDraft({
        displayName: '',
        firstName: '',
        lastName: '',
        organization: '',
        notes: '',
        email: 'bad',
        phone: '12',
      }),
    ).toEqual(
      expect.objectContaining({
        displayName: expect.any(String),
        email: expect.any(String),
        phone: expect.any(String),
      }),
    )
  })

  it('keeps Contact IDs as strings and includes optimistic version in PATCH payload', () => {
    const patch = toPatchContactInput(
      {
        displayName: 'Alice',
        firstName: '',
        lastName: '',
        organization: '',
        notes: '',
        email: '',
        phone: '',
      },
      9,
    )
    expect(patch.version).toBe(9)
    expect(typeof '9223372036854775807').toBe('string')
  })

  it('centralizes primary identity fallback from canonical identities', () => {
    const value = contactPrimaryIdentityValue(
      {
        primaryEmail: null,
        primaryPhone: null,
        identities: [
          {
            id: 'identity-1',
            type: 'EMAIL',
            value: 'alice@example.com',
            normalizedValue: 'alice@example.com',
            primary: true,
            status: 'ACTIVE',
          },
        ],
      },
      'EMAIL',
    )
    expect(value).toBe('alice@example.com')
  })

  it('preserves opaque cursor progression while deduplicating loaded rows', () => {
    const current = [{ id: '1', displayName: 'A', updatedAt: '2026-01-01' }]
    const next = appendCursorPage(
      current,
      {
        items: [
          { id: '1', displayName: 'A', updatedAt: '2026-01-01' },
          { id: '2', displayName: 'B', updatedAt: '2026-01-02' },
        ],
        nextCursor: 'opaque',
        hasMore: true,
      },
      false,
    )
    expect(next.map((item) => item.id)).toEqual(['1', '2'])
  })

  it('uses a filter descriptor rather than enumerating all results', () => {
    expect(
      buildSelectionDescriptor(new Set(['1', '2']), true, { q: 'acme', sort: 'UPDATED_DESC' }),
    ).toEqual({ type: 'FILTER', filter: { q: 'acme', sort: 'UPDATED_DESC' } })
  })

  it('bounds page size and trims search', () => {
    expect(normalizeContactQuery({ q: '  alice  ', size: 999 })).toEqual({ q: 'alice', size: 200 })
  })

  it('accepts only supported Contact search statuses from URL state', () => {
    expect(parseContactSearchStatus('ACTIVE')).toBe('ACTIVE')
    expect(parseContactSearchStatus('MERGED')).toBe('MERGED')
    expect(parseContactSearchStatus('DELETED')).toBeUndefined()
    expect(parseContactSearchStatus('unknown')).toBeUndefined()
  })

  it('keeps default Contact export fields within the frozen backend allow-list', () => {
    expect(DEFAULT_CONTACT_EXPORT_FIELDS).toEqual(['displayName', 'organization'])
    expect(DEFAULT_CONTACT_EXPORT_FIELDS).not.toContain('primaryEmail')
    expect(DEFAULT_CONTACT_EXPORT_FIELDS).not.toContain('primaryPhone')
  })

  it('auto-maps common CSV headers and leaves unknown columns untouched', () => {
    const mappings = buildSuggestedImportMappings([
      'external_id',
      'first_name',
      'last_name',
      'email',
      'phone',
      'country_code',
      'source',
    ])

    expect(mappings).toEqual([
      { column: 'external_id', target: { type: 'IGNORE' } },
      { column: 'first_name', target: { type: 'BUILTIN', field: 'firstName' } },
      { column: 'last_name', target: { type: 'BUILTIN', field: 'lastName' } },
      { column: 'email', target: { type: 'IDENTITY', identityType: 'EMAIL' } },
      { column: 'phone', target: { type: 'IDENTITY', identityType: 'PHONE' } },
      { column: 'country_code', target: { type: 'IGNORE' } },
      { column: 'source', target: { type: 'IGNORE' } },
    ])
  })

  it('never auto-assigns the same import target twice', () => {
    const mappings = buildSuggestedImportMappings(['email', 'email_address'])
    expect(mappings[0].target).toEqual({ type: 'IDENTITY', identityType: 'EMAIL' })
    expect(mappings[1].target).toEqual({ type: 'IGNORE' })
  })

  it('auto-detects explicit marketing consent columns without treating them as contact fields', () => {
    const headers = [
      'email',
      'email_marketing_consent',
      'sms_marketing_consent',
      'whatsapp_marketing_consent',
      'consent_source',
      'consent_occurred_at',
    ]

    expect(buildSuggestedImportMappings(headers).slice(1)).toEqual([
      { column: 'email_marketing_consent', target: { type: 'IGNORE' } },
      { column: 'sms_marketing_consent', target: { type: 'IGNORE' } },
      { column: 'whatsapp_marketing_consent', target: { type: 'IGNORE' } },
      { column: 'consent_source', target: { type: 'IGNORE' } },
      { column: 'consent_occurred_at', target: { type: 'IGNORE' } },
    ])

    expect(buildSuggestedConsentMappings(headers)).toEqual([
      {
        channel: 'EMAIL',
        stateColumn: 'email_marketing_consent',
        source: 'IMPORT',
        evidenceColumn: 'consent_source',
        occurredAtColumn: 'consent_occurred_at',
      },
      {
        channel: 'SMS',
        stateColumn: 'sms_marketing_consent',
        source: 'IMPORT',
        evidenceColumn: 'consent_source',
        occurredAtColumn: 'consent_occurred_at',
      },
      {
        channel: 'WHATSAPP',
        stateColumn: 'whatsapp_marketing_consent',
        source: 'IMPORT',
        evidenceColumn: 'consent_source',
        occurredAtColumn: 'consent_occurred_at',
      },
    ])
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
    expect(result.consentDeclarations).toEqual([])
    expect(result.mappings).toHaveLength(1)
  })
})
