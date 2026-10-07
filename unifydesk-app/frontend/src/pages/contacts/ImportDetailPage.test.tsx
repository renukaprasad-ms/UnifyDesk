import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { contactsApi } from '../../features/contacts/contactsApi'
import { useAppSelector } from '../../store/hooks'
import ImportDetailPage from './ImportDetailPage'

vi.mock('../../features/contacts/contactsApi', () => ({
  contactsApi: {
    getImport: vi.fn(),
    lists: vi.fn(),
    customFields: vi.fn(),
    previewImport: vi.fn(),
    commitImport: vi.fn(),
    importErrors: vi.fn(),
  },
}))

vi.mock('../../store/hooks', () => ({
  useAppSelector: vi.fn(),
}))

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/contacts/imports/import-1']}>
      <Routes>
        <Route path="/contacts/imports/:importId" element={<ImportDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )

describe('ImportDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useAppSelector).mockReturnValue('company-1')
    vi.mocked(contactsApi.lists).mockResolvedValue([])
    vi.mocked(contactsApi.customFields).mockResolvedValue([])
  })

  it('auto-maps common columns and shows marketing permission controls', async () => {
    vi.mocked(contactsApi.getImport).mockResolvedValue({
      id: 'import-1',
      status: 'PREVIEWED',
    })
    vi.mocked(contactsApi.previewImport).mockResolvedValue({
      headers: [
        'external_id',
        'first_name',
        'last_name',
        'email',
        'phone',
        'country_code',
        'source',
      ],
      sampleRows: [
        ['TEST-00001', 'Alice', 'Contact', 'alice@example.com', '+12025550100', 'US', 'CSV_TEST'],
      ],
    })

    renderPage()

    expect(
      await screen.findByText(/We matched common column names automatically/i),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Import first_name as')).toHaveValue('BUILTIN:firstName')
    expect(screen.getByLabelText('Import last_name as')).toHaveValue('BUILTIN:lastName')
    expect(screen.getByLabelText('Import email as')).toHaveValue('IDENTITY:EMAIL')
    expect(screen.getByLabelText('Import phone as')).toHaveValue('IDENTITY:PHONE')
    expect(screen.getByLabelText('Import external_id as')).toHaveValue('IGNORE')
    expect(screen.getByText(/Marketing permissions/i)).toBeInTheDocument()
  })

  it('allows explicit consent evidence mapping when marketing permissions are enabled', async () => {
    vi.mocked(contactsApi.getImport).mockResolvedValue({
      id: 'import-1',
      status: 'PREVIEWED',
    })
    vi.mocked(contactsApi.previewImport).mockResolvedValue({
      headers: ['name', 'email_consent', 'evidence', 'occurred_at'],
      sampleRows: [['Alice', 'OPTED_IN', 'webform-1', '2026-09-01']],
    })

    renderPage()

    await screen.findByText(/Marketing permissions/i)

    expect(screen.getByLabelText(/Import EMAIL marketing preference/i)).toBeChecked()
    expect(screen.getByText('Preference column')).toBeInTheDocument()
    expect(screen.getByText('Evidence column')).toBeInTheDocument()
    expect(screen.getByText('Consent date column')).toBeInTheDocument()
  })

  it('auto-detects explicit consent columns and includes them in the import summary', async () => {
    vi.mocked(contactsApi.getImport).mockResolvedValue({
      id: 'import-1',
      status: 'PREVIEWED',
    })
    vi.mocked(contactsApi.previewImport).mockResolvedValue({
      headers: [
        'display_name',
        'email',
        'email_marketing_consent',
        'sms_marketing_consent',
        'whatsapp_marketing_consent',
        'consent_source',
        'consent_occurred_at',
      ],
      sampleRows: [
        [
          'Alice',
          'alice@example.com',
          'TRUE',
          'FALSE',
          'TRUE',
          'website-form',
          '2026-10-01T10:00:00Z',
        ],
      ],
    })

    renderPage()

    expect(await screen.findByText(/Consent columns detected/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/Import EMAIL marketing preference/i)).toBeChecked()
    expect(screen.getByLabelText(/Import SMS marketing preference/i)).toBeChecked()
    expect(screen.getByLabelText(/Import WhatsApp marketing preference/i)).toBeChecked()
    expect(screen.getByText(/3 channels mapped/i)).toBeInTheDocument()
  })

  it('supports one attested marketing permission choice for the whole import', async () => {
    vi.mocked(contactsApi.getImport)
      .mockResolvedValueOnce({
        id: 'import-1',
        status: 'PREVIEWED',
      })
      .mockResolvedValueOnce({
        id: 'import-1',
        status: 'QUEUED',
      })
    vi.mocked(contactsApi.previewImport).mockResolvedValue({
      headers: ['name', 'email'],
      sampleRows: [['Alice', 'alice@example.com']],
    })
    vi.mocked(contactsApi.commitImport).mockResolvedValue({
      id: 'job-1',
      status: 'QUEUED',
    })

    renderPage()

    fireEvent.click(await screen.findByLabelText(/Same permission for all contacts/i))
    fireEvent.click(screen.getByLabelText(/^EMAIL$/i))
    fireEvent.click(
      screen.getByLabelText(
        /I confirm these contacts gave my business permission to receive marketing/i,
      ),
    )
    fireEvent.click(screen.getByRole('button', { name: 'Import contacts' }))

    await waitFor(() =>
      expect(contactsApi.commitImport).toHaveBeenCalledWith(
        'company-1',
        'import-1',
        expect.objectContaining({
          consentMappings: [],
          consentDeclarations: [
            expect.objectContaining({
              channel: 'EMAIL',
              state: 'OPTED_IN',
              source: 'IMPORT',
              evidence: 'Website form',
              occurredAt: null,
              confirmed: true,
            }),
          ],
        }),
      ),
    )
  })

  it('announces asynchronous progress in business-friendly language', async () => {
    vi.mocked(contactsApi.getImport).mockResolvedValue({
      id: 'import-1',
      status: 'RUNNING',
      job: {
        id: 'job-1',
        status: 'RUNNING',
        progressPercent: 42,
        processedCount: 420,
        successCount: 400,
        errorCount: 20,
      },
      totalRows: 1000,
      errorRows: 20,
    })

    renderPage()

    expect(await screen.findByText(/Importing contacts/i)).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '42')
    expect(screen.getByText('420')).toBeInTheDocument()
    expect(screen.getByText('400')).toBeInTheDocument()
    expect(screen.getByText('20')).toBeInTheDocument()
  })

  it('shows a clear all-failed outcome and requests the error report only on demand', async () => {
    vi.mocked(contactsApi.getImport).mockResolvedValue({
      id: 'import-1',
      status: 'COMPLETED',
      totalRows: 5000,
      successRows: 0,
      errorRows: 5000,
    })
    vi.mocked(contactsApi.importErrors).mockResolvedValue({
      count: 5000,
      ready: true,
      downloadUrl: 'https://signed.invalid/temporary',
      expiresAt: '2026-09-30T07:00:00Z',
    })
    const open = vi.spyOn(window, 'open').mockImplementation(() => null)

    renderPage()

    expect(await screen.findByText(/We couldn't add any contacts/i)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Download error report' }))

    await waitFor(() =>
      expect(contactsApi.importErrors).toHaveBeenCalledWith('company-1', 'import-1'),
    )
    expect(open).toHaveBeenCalledWith(
      'https://signed.invalid/temporary',
      '_blank',
      'noopener,noreferrer',
    )
  })
})
