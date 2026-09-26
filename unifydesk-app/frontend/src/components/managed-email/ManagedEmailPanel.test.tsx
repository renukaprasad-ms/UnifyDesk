import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { ManagedEmailApi } from '../../features/managedEmail/managedEmailApi'
import type {
  ManagedEmailDeliverability,
  ManagedEmailDomain,
  ManagedEmailOverview,
  ManagedEmailSender,
  ManagedEmailUsage,
} from '../../features/managedEmail/types'
import ManagedEmailPanel from './ManagedEmailPanel'

const overview: ManagedEmailOverview = {
  enabled: true,
  serviceStatus: 'ACTIVE',
  sendingStatus: 'ENABLED',
  regionLabel: 'Managed by UnifyDesk',
  domainLimit: 5,
  senderLimit: 20,
  globalNotice: null,
}

const activeDomain: ManagedEmailDomain = {
  id: 'domain-1',
  type: 'CUSTOMER_DOMAIN',
  domain: 'example.com',
  status: 'ACTIVE',
  verificationStatus: 'SUCCESS',
  dkimStatus: 'SUCCESS',
  dmarcStatus: 'SUCCESS',
  dmarcPolicy: 'none',
  dnsRecords: [],
  activatedAt: '2026-09-01T00:00:00Z',
  lastCheckedAt: '2026-09-26T00:00:00Z',
}

const managedSender: ManagedEmailSender = {
  id: 'sender-1',
  connectionId: 'connection-1',
  channelConnectionId: 'channel-1',
  provider: 'AMAZON_SES',
  managementMode: 'MESSAGING_MANAGED',
  status: 'ACTIVE',
  fromAddress: 'offers@example.com',
  displayName: 'Example Offers',
  replyToAddress: null,
  connectionName: 'UnifyDesk Managed Email',
  region: 'MANAGED',
  productionAccessEnabled: true,
  sendingEnabled: true,
  managedDomainId: 'domain-1',
  managedDomain: 'example.com',
}

const usage: ManagedEmailUsage = {
  periodStart: '2026-09-01T00:00:00Z',
  periodEndExclusive: '2026-10-01T00:00:00Z',
  accepted: 920,
  reserved: 0,
  adjustments: 0,
  billable: 920,
  limit: 1000,
  remaining: 80,
  limitReached: false,
}

const deliverability: ManagedEmailDeliverability = {
  status: 'WARNING',
  windowStart: '2026-09-19T00:00:00Z',
  windowEnd: '2026-09-26T00:00:00Z',
  accepted: 1000,
  hardBounces: 12,
  complaints: 1,
  hardBounceRate: 0.012,
  complaintRate: 0.001,
  sendingRestricted: false,
  notice: null,
}

const client = (overrides: Partial<ManagedEmailApi> = {}): ManagedEmailApi =>
  ({
    overview: vi.fn().mockResolvedValue(overview),
    domains: vi.fn().mockResolvedValue([activeDomain]),
    domain: vi.fn(),
    createCustomerDomain: vi.fn(),
    createHostedDomain: vi.fn(),
    refreshDomain: vi.fn(),
    retireDomain: vi.fn().mockResolvedValue(null),
    senders: vi.fn().mockResolvedValue([managedSender]),
    createSender: vi.fn(),
    removeSender: vi.fn().mockResolvedValue(undefined),
    usage: vi.fn().mockResolvedValue(usage),
    deliverability: vi.fn().mockResolvedValue(deliverability),
    ...overrides,
  }) as ManagedEmailApi

describe('ManagedEmailPanel', () => {
  it('shows useful usage, near-limit and deliverability states', async () => {
    render(
      <ManagedEmailPanel
        companyId="company-1"
        canModify
        onOpenSetup={vi.fn()}
        client={client()}
      />,
    )

    expect(await screen.findByText('920')).toBeTruthy()
    expect(screen.getByText('80').parentElement?.textContent).toContain('remaining')
    expect(screen.getByText(/Limit: 1,000/i)).toBeTruthy()
    expect(screen.getByText(/close to this period/i)).toBeTruthy()
    expect(screen.getByText('WARNING')).toBeTruthy()
    expect(screen.getByText(/Some sending signals need attention/i)).toBeTruthy()
    expect(screen.getByText('1.2%')).toBeTruthy()
    expect(screen.getByText('0.10%')).toBeTruthy()
  })

  it('presents unlimited usage as Unlimited instead of -1', async () => {
    const api = client({
      usage: vi.fn().mockResolvedValue({ ...usage, limit: -1, remaining: -1 }),
    })
    render(
      <ManagedEmailPanel
        companyId="company-1"
        canModify
        onOpenSetup={vi.fn()}
        client={api}
      />,
    )

    await screen.findByText('920')
    expect(screen.getAllByText('Unlimited').length).toBeGreaterThanOrEqual(2)
    expect(screen.queryByText('-1')).toBeNull()
  })

  it('shows limit reached and suspended deliverability as non-ready', async () => {
    const api = client({
      overview: vi.fn().mockResolvedValue({ ...overview, sendingStatus: 'DISABLED' }),
      usage: vi
        .fn()
        .mockResolvedValue({ ...usage, remaining: 0, limitReached: true }),
      deliverability: vi.fn().mockResolvedValue({
        ...deliverability,
        status: 'SUSPENDED',
        sendingRestricted: true,
        notice: 'Sending is paused while this workspace is reviewed.',
      }),
    })
    render(
      <ManagedEmailPanel
        companyId="company-1"
        canModify
        onOpenSetup={vi.fn()}
        client={api}
      />,
    )

    expect(await screen.findByText(/Sending needs attention/i)).toBeTruthy()
    expect(screen.getByText(/Monthly sending limit reached/i)).toBeTruthy()
    expect(screen.getByText('SUSPENDED')).toBeTruthy()
    expect(
      screen.getByText(/Sending is paused while this workspace is reviewed/i),
    ).toBeTruthy()
  })

  it('labels managed senders as UnifyDesk Managed Email and opens active domains', async () => {
    const openSetup = vi.fn()
    render(
      <ManagedEmailPanel
        companyId="company-1"
        canModify
        onOpenSetup={openSetup}
        client={client()}
      />,
    )

    expect(await screen.findByText('offers@example.com')).toBeTruthy()
    expect(screen.getAllByText(/UnifyDesk Managed Email/i).length).toBeGreaterThanOrEqual(2)

    fireEvent.click(screen.getByRole('button', { name: /Manage/i }))
    expect(openSetup).toHaveBeenCalledWith('domain-1')
  })

  it('supports disabling a sender and retiring a domain without deleting history client-side', async () => {
    const removeSender = vi.fn().mockResolvedValue(undefined)
    const retireDomain = vi
      .fn()
      .mockResolvedValue({ ...activeDomain, status: 'DELETING' })
    const api = client({ removeSender, retireDomain })
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    render(
      <ManagedEmailPanel
        companyId="company-1"
        canModify
        onOpenSetup={vi.fn()}
        client={api}
      />,
    )

    await screen.findByText('offers@example.com')
    fireEvent.click(screen.getByRole('button', { name: /Disable/i }))
    await waitFor(() =>
      expect(removeSender).toHaveBeenCalledWith('company-1', 'sender-1'),
    )

    fireEvent.click(screen.getByRole('button', { name: /Retire/i }))
    await waitFor(() =>
      expect(retireDomain).toHaveBeenCalledWith('company-1', 'domain-1'),
    )
  })
})
