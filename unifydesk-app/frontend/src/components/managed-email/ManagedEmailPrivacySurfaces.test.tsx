import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import type { BroadcastSender } from '../../features/broadcast/types'
import BroadcastSenderProviderMeta from './BroadcastSenderProviderMeta'

const staleManagedSender: BroadcastSender = {
  id: 'managed-privacy',
  connectionId: 'managed-connection',
  channelConnectionId: 'managed-channel',
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
  ...staleManagedSender,
  id: 'customer-managed',
  managementMode: 'CUSTOMER_MANAGED',
  connectionName: 'Customer SES',
  region: 'us-east-1',
  maxSendRate: 25,
  max24HourSend: 50000,
  sentLast24Hours: 12345,
}

const assertManagedSafe = (surface: string, includeConnectionDetails = false) => {
  render(
    <div data-testid={surface}>
      <BroadcastSenderProviderMeta
        sender={staleManagedSender}
        includeConnectionDetails={includeConnectionDetails}
      />
    </div>,
  )

  const text = screen.getByTestId(surface).textContent ?? ''
  expect(text).toContain('UnifyDesk Managed Email')
  expect(text).not.toContain('Amazon SES')
  expect(text).not.toContain('ap-south-1')
  expect(text).not.toContain('1000')
  expect(text).not.toContain('50000')
  expect(text).not.toContain('12345')
}

describe('ME-SEC-008 managed sender surfaces', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('keeps Company Connections safe with stale sender data', () => {
    assertManagedSafe('company-connections')
  })

  it('keeps the Broadcast composer safe when connection details are requested', () => {
    assertManagedSafe('composer', true)
  })

  it('keeps Broadcast history safe', () => {
    assertManagedSafe('history')
  })

  it('keeps Broadcast detail safe', () => {
    assertManagedSafe('detail')
  })

  it.each([
    ['desktop', 1440],
    ['tablet', 900],
    ['mobile', 390],
  ])('keeps managed metadata private at %s width', (_name, width) => {
    Object.defineProperty(window, 'innerWidth', {
      configurable: true,
      writable: true,
      value: width,
    })
    window.dispatchEvent(new Event('resize'))
    assertManagedSafe(`viewport-${width}`, true)
  })

  it('preserves customer-managed SES branding and legitimate provider details', () => {
    render(
      <div data-testid="customer-managed">
        <BroadcastSenderProviderMeta
          sender={customerManagedSes}
          includeConnectionDetails
        />
      </div>,
    )

    const text = screen.getByTestId('customer-managed').textContent ?? ''
    expect(text).toContain('Amazon SES')
    expect(text).toContain('us-east-1')
    expect(text).toContain('up to 25/sec')
    expect(text).not.toContain('UnifyDesk Managed Email')
  })
})
