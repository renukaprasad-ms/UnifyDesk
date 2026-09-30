import { beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from '../../lib/api/apiClient'
import { contactsApi } from './contactsApi'

vi.mock('../../lib/api/apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

const ok = <T,>(data: T) => ({
  data: {
    status: true,
    status_code: 200,
    data,
  },
})

describe('contactsApi', () => {
  beforeEach(() => vi.clearAllMocks())

  it('forwards search, filters, supported sort and opaque cursor to the backend', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      ok({ items: [], nextCursor: 'opaque.next', hasMore: true }) as never,
    )

    await contactsApi.list(
      'company-1',
      {
        q: 'alice',
        tagId: 'tag-1',
        listId: 'list-1',
        createdAfter: '2026-01-01',
        sort: 'NAME_ASC',
        size: 50,
      },
      'opaque.current',
    )

    expect(apiClient.get).toHaveBeenCalledWith(
      '/api/v1/companies/company-1/contacts',
      {
        params: {
          q: 'alice',
          tagId: 'tag-1',
          listId: 'list-1',
          createdAfter: '2026-01-01',
          sort: 'NAME_ASC',
          size: 50,
          cursor: 'opaque.current',
        },
      },
    )
  })

  it('uses a selection descriptor for bulk work instead of a flattened contact list', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(
      ok({ id: 'job-1', status: 'QUEUED' }) as never,
    )

    await contactsApi.bulk(
      'company-1',
      { type: 'FILTER', filter: { q: 'acme', tagId: 'tag-1' } },
      { type: 'ADD_TO_LIST', listId: 'list-2' },
      'idem-1',
    )

    expect(apiClient.post).toHaveBeenCalledWith(
      '/api/v1/companies/company-1/contacts/bulk-operations',
      {
        selection: { type: 'FILTER', filter: { q: 'acme', tagId: 'tag-1' } },
        operation: { type: 'ADD_TO_LIST', listId: 'list-2' },
        idempotencyKey: 'idem-1',
      },
    )
  })

  it('keeps export on the frozen source-based export contract', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(
      ok({ id: 'export-1', status: 'QUEUED' }) as never,
    )

    await contactsApi.createExport('company-1', {
      sourceType: 'SEGMENT',
      sourceId: 'segment-1',
      fields: ['displayName', 'primaryEmail'],
      idempotencyKey: 'idem-export',
    })

    expect(apiClient.post).toHaveBeenCalledWith(
      '/api/v1/companies/company-1/contacts/exports',
      {
        sourceType: 'SEGMENT',
        sourceId: 'segment-1',
        fields: ['displayName', 'primaryEmail'],
        idempotencyKey: 'idem-export',
      },
    )
  })

  it('records channel-specific preference updates without a global subscribed flag', async () => {
    vi.mocked(apiClient.put).mockResolvedValue(
      ok({
        channel: 'EMAIL',
        purpose: 'MARKETING',
        state: 'OPTED_OUT',
      }) as never,
    )

    await contactsApi.updatePreference(
      'company-1',
      'contact-1',
      'EMAIL',
      'OPTED_OUT',
      'Customer request',
    )

    expect(apiClient.put).toHaveBeenCalledWith(
      '/api/v1/companies/company-1/contacts/contact-1/preferences/EMAIL/MARKETING',
      expect.objectContaining({
        state: 'OPTED_OUT',
        source: 'USER_UI',
        reason: 'Customer request',
      }),
    )
  })
})
