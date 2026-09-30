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

  it('shows bounded mapping UX and requires explicit consent mapping', async () => {
    vi.mocked(contactsApi.getImport).mockResolvedValue({
      id: 'import-1',
      status: 'PREVIEWED',
    })
    vi.mocked(contactsApi.previewImport).mockResolvedValue({
      headers: ['name', 'email_consent', 'evidence', 'occurred_at'],
      sampleRows: [['Alice', 'OPTED_IN', 'webform-1', '2026-09-01']],
      estimatedRows: 12000,
    })

    renderPage()

    expect(
      await screen.findByText(/Importing a Contact never grants marketing consent/i),
    ).toBeInTheDocument()
    expect(screen.getByText(/bounded server-generated sample/i)).toBeInTheDocument()

    fireEvent.click(
      screen.getByLabelText(/Map EMAIL marketing preference evidence/i),
    )

    expect(screen.getByText('Preference state column')).toBeInTheDocument()
    expect(screen.getByText('Evidence/source reference column')).toBeInTheDocument()
    expect(screen.getByText('Occurred-at column')).toBeInTheDocument()
  })

  it('auto-maps common contact columns and external ID namespace', async () => {
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
        [
          'TEST-00001',
          'Sample00001',
          'Contact',
          'contact00001@example.com',
          '+12025550100',
          'US',
          'CSV_TEST',
        ],
      ],
    })

    renderPage()

    expect(await screen.findByLabelText('Map first_name')).toHaveValue('BUILTIN:firstName')
    expect(screen.getByLabelText('Map email')).toHaveValue('IDENTITY:EMAIL')
    expect(screen.getByLabelText('Map phone')).toHaveValue('IDENTITY:PHONE')
    expect(screen.getByLabelText('Map country_code')).toHaveValue('BUILTIN:countryCode')
    expect(screen.getByLabelText('Map source')).toHaveValue('BUILTIN:source')
    expect(screen.getByLabelText('Map external_id')).toHaveValue('IDENTITY:EXTERNAL')
    expect(screen.getByLabelText('Namespace for external_id')).toHaveValue('source')
  })

  it('announces asynchronous progress without retaining the whole import', async () => {
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

    expect(await screen.findByText(/Processing status: RUNNING/i)).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '42')
    expect(screen.getByText(/Processed 420/i)).toBeInTheDocument()
  })

  it('requests a fresh authorized error link only on demand', async () => {
    vi.mocked(contactsApi.getImport).mockResolvedValue({
      id: 'import-1',
      status: 'COMPLETED',
      errorRows: 3,
    })
    vi.mocked(contactsApi.importErrors).mockResolvedValue({
      count: 3,
      ready: true,
      downloadUrl: 'https://signed.invalid/temporary',
      expiresAt: '2026-09-30T07:00:00Z',
    })
    const open = vi.spyOn(window, 'open').mockImplementation(() => null)

    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: 'Download error report' }))

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
