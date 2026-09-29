import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { ChannelConnection } from '../../features/channel/types'
import WhatsAppDisconnectModal from './WhatsAppDisconnectModal'

const connection: ChannelConnection = {
  id: 'connection-1',
  companyId: 'company-1',
  channelType: 'WHATSAPP',
  providerType: 'META_WHATSAPP',
  name: 'Business WhatsApp',
  status: 'ACTIVE',
  createdByUserId: 'user-1',
  createdAt: '2026-09-29T00:00:00Z',
  updatedAt: '2026-09-29T00:00:00Z',
  providerConfigured: true,
  capabilities: ['INBOX'],
}

describe('WhatsAppDisconnectModal', () => {
  it('explains local disconnect without implying Meta asset deletion', () => {
    render(
      <WhatsAppDisconnectModal
        connection={connection}
        working={false}
        onClose={vi.fn()}
        onConfirm={vi.fn()}
      />,
    )

    expect(screen.getByRole('dialog')).toHaveAccessibleName(/disconnect business whatsapp/i)
    expect(screen.getByText(/does not delete your WhatsApp Business Account/i)).toBeInTheDocument()
    expect(screen.getByText(/other Meta business assets/i)).toBeInTheDocument()
  })

  it('supports keyboard escape and explicit local-data policy', () => {
    const onClose = vi.fn()
    const onConfirm = vi.fn()
    render(
      <WhatsAppDisconnectModal
        connection={connection}
        working={false}
        onClose={onClose}
        onConfirm={onConfirm}
      />,
    )

    fireEvent.keyDown(window, { key: 'Escape' })
    expect(onClose).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByRole('button', { name: /delete imported unifydesk data now/i }))
    fireEvent.click(screen.getByRole('button', { name: /disconnect & delete local data/i }))
    expect(onConfirm).toHaveBeenCalledWith('DELETE_NOW')
  })
})
