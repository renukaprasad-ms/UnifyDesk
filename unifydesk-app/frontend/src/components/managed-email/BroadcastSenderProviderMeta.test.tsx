import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { BroadcastSender } from '../../features/broadcast/types'
import { managedConnectionDisplayName } from '../../features/managedEmail/privacy'
import BroadcastSenderProviderMeta from './BroadcastSenderProviderMeta'

const managedStaleSender: BroadcastSender = {
  id: 'managed-1',
  connectionId: 'connection-managed',
  channelConnectionId: 'channel-managed',
  provider: 'AMAZON_SES',
  managementMode: 'MESSAGING_MANAGED',
  status: 'ACTIVE',
  fromAddress: 'offers@example.com',
  displayName: 'Example Offers',
  replyToAddress: null,
  connectionName: 'UnifyDesk Managed Email',
  region: 'ap-south-1',
  productionAccessEnabled: true,
  sendingEnabled: true,
  maxSendRate: 1000,
  max24HourSend: 50000,
  sentLast24Hours: 12345,
}

const customerManagedSes: BroadcastSender = {
  ...managedStaleSender,
  id: 'customer-ses-1',
  connectionId: 'connection-customer',
  channelConnectionId: 'channel-customer',
  managementMode: 'CUSTOMER_MANAGED',
  connectionName: 'My Amazon SES',
  region: 'ap-south-1',
  maxSendRate: 25,
  max24HourSend: 50000,
  sentLast24Hours: 12345,
}

describe('BroadcastSenderProviderMeta privacy', () => {
  it('hides stale AWS topology and shared account capacity for Managed Email', () => {
    render(
      <div data-testid="meta">
        <BroadcastSenderProviderMeta
          sender={managedStaleSender}
          includeConnectionDetails
        />
      </div>,
    )

    const text = screen.getByTestId('meta').textContent ?? ''
    expect(text).toContain('UnifyDesk Managed Email')
    expect(text).not.toContain('Amazon SES')
    expect(text).not.toContain('ap-south-1')
    expect(text).not.toContain('1000')
    expect(text).not.toContain('50000')
    expect(text).not.toContain('12345')
    expect(text).not.toContain('/sec')
  })

  it('normalizes a stale managed connection name without changing customer-managed SES', () => {
    expect(managedConnectionDisplayName(managedStaleSender, 'Amazon SES ap-south-1')).toBe(
      'UnifyDesk Managed Email',
    )
    expect(managedConnectionDisplayName(customerManagedSes, 'My Amazon SES')).toBe(
      'My Amazon SES',
    )
  })

  it('keeps legitimate customer-managed SES provider details visible', () => {
    render(
      <div data-testid="meta">
        <BroadcastSenderProviderMeta
          sender={customerManagedSes}
          includeConnectionDetails
        />
      </div>,
    )

    const text = screen.getByTestId('meta').textContent ?? ''
    expect(text).toContain('Amazon SES')
    expect(text).toContain('ap-south-1')
    expect(text).toContain('up to 25/sec')
    expect(text).not.toContain('UnifyDesk Managed Email')
  })
})
