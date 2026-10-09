import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import BroadcastEmailProviderPickerModal, {
  type BroadcastEmailProviderChoice,
} from '../broadcast/BroadcastEmailProviderPickerModal'
import { SnackbarProvider } from '../feedback/SnackbarProviderComponent'
import { broadcastApi } from '../../features/broadcast/broadcastApi'

vi.mock('../../features/broadcast/broadcastApi', () => ({
  broadcastApi: {
    activeSetupSession: vi.fn(),
    saveSetupSession: vi.fn(),
  },
}))

const renderPicker = (overrides?: {
  onManagedEmail?: () => void
  onSelect?: (provider: BroadcastEmailProviderChoice) => void
}) => {
  const onManagedEmail = vi.fn(overrides?.onManagedEmail)
  const onSelect = vi.fn(overrides?.onSelect)
  render(
    <SnackbarProvider>
      <BroadcastEmailProviderPickerModal
        companyId="company-1"
        onClose={vi.fn()}
        onManagedEmail={onManagedEmail}
        onSelect={onSelect}
      />
    </SnackbarProvider>,
  )
  return { onManagedEmail, onSelect }
}

describe('BroadcastEmailProviderPickerModal Managed Email entry', () => {
  beforeEach(() => {
    vi.mocked(broadcastApi.activeSetupSession).mockResolvedValue(null)
    vi.mocked(broadcastApi.saveSetupSession).mockResolvedValue({
      id: 'setup-1',
      companyId: 'company-1',
      channelType: 'EMAIL',
      provider: 'AMAZON_SES',
      currentStep: 0,
      status: 'IN_PROGRESS',
      createdAt: '2026-09-26T00:00:00Z',
      updatedAt: '2026-09-26T00:00:00Z',
    })
  })

  it('makes UnifyDesk Managed Email the primary path', async () => {
    const { onManagedEmail } = renderPicker()

    const managed = await screen.findByRole('button', {
      name: /Use UnifyDesk Managed Email/i,
    })
    expect(screen.getByText(/No provider credentials are needed/i)).toBeTruthy()

    fireEvent.click(managed)
    expect(onManagedEmail).toHaveBeenCalledTimes(1)
  })

  it('keeps the customer-managed provider path and all existing email providers accessible', async () => {
    renderPicker()
    fireEvent.click(await screen.findByRole('button', { name: /Connect my own provider/i }))

    expect(screen.getByRole('button', { name: /Amazon SES/i })).toBeTruthy()
    expect(screen.getByRole('button', { name: /SendGrid/i })).toBeTruthy()
    expect(screen.getByRole('button', { name: /Azure Email/i })).toBeTruthy()
    expect(screen.getByRole('button', { name: /MSG91 Email/i })).toBeTruthy()
  })

  it('continues the existing provider setup-session flow', async () => {
    renderPicker()
    fireEvent.click(await screen.findByRole('button', { name: /Connect my own provider/i }))
    fireEvent.click(screen.getByRole('button', { name: /Amazon SES/i }))

    await waitFor(() =>
      expect(broadcastApi.saveSetupSession).toHaveBeenCalledWith(
        'company-1',
        expect.objectContaining({
          channelType: 'EMAIL',
          provider: 'AMAZON_SES',
          currentStep: 0,
          status: 'IN_PROGRESS',
        }),
      ),
    )
    expect(screen.getByText(/Amazon SES setup/i)).toBeTruthy()
  })
})
