import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ManagedEmailApi } from '../../features/managedEmail/managedEmailApi'
import type {
  ManagedEmailDomain,
  ManagedEmailOverview,
  ManagedEmailSender,
} from '../../features/managedEmail/types'
import ManagedEmailModal from './ManagedEmailModal'

const overview: ManagedEmailOverview = {
  enabled: true,
  serviceStatus: 'ACTIVE',
  sendingStatus: 'ENABLED',
  regionLabel: 'Managed by UnifyDesk',
  domainLimit: 5,
  senderLimit: 20,
  globalNotice: null,
}

const domain = (overrides: Partial<ManagedEmailDomain> = {}): ManagedEmailDomain => ({
  id: 'domain-1',
  type: 'CUSTOMER_DOMAIN',
  domain: 'example.com',
  status: 'PENDING_DNS',
  verificationStatus: 'PENDING',
  dkimStatus: 'PENDING',
  dmarcStatus: 'PENDING',
  dmarcPolicy: 'none',
  dnsRecords: [],
  activatedAt: null,
  lastCheckedAt: null,
  ...overrides,
})

const sender = (overrides: Partial<ManagedEmailSender> = {}): ManagedEmailSender => ({
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
  region: 'ap-south-1',
  productionAccessEnabled: true,
  sendingEnabled: true,
  maxSendRate: 1000,
  max24HourSend: 50000,
  sentLast24Hours: 12345,
  managedDomainId: 'domain-1',
  managedDomain: 'example.com',
  ...overrides,
})

const client = (overrides: Partial<ManagedEmailApi> = {}): ManagedEmailApi =>
  ({
    overview: vi.fn().mockResolvedValue(overview),
    domains: vi.fn().mockResolvedValue([]),
    domain: vi.fn().mockResolvedValue(domain()),
    createCustomerDomain: vi.fn().mockResolvedValue(domain()),
    createHostedDomain: vi.fn().mockResolvedValue(
      domain({
        type: 'UNIFYDESK_HOSTED',
        domain: 'abc-k7m4q2.unifydesk.in',
        status: 'VERIFYING',
      }),
    ),
    checkSubdomain: vi.fn().mockResolvedValue({
      available: true,
      subdomain: 'acme',
      domain: 'acme.unifydesk.email',
      message: 'The subdomain is available!',
    }),
    refreshDomain: vi.fn().mockResolvedValue(domain()),
    retireDomain: vi.fn().mockResolvedValue(null),
    senders: vi.fn().mockResolvedValue([]),
    createSender: vi.fn().mockResolvedValue(sender()),
    removeSender: vi.fn().mockResolvedValue(undefined),
    usage: vi.fn(),
    deliverability: vi.fn(),
    ...overrides,
  }) as ManagedEmailApi

describe('ManagedEmailModal', () => {
  const clickEnabled = async (name: RegExp) => {
    const button = screen.getByRole('button', { name }) as HTMLButtonElement
    await waitFor(() => expect(button).not.toBeDisabled())
    fireEvent.click(button)
  }

  beforeEach(() => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('creates a customer-owned domain from a bare domain and shows provisioning', async () => {
    const api = client({
      createCustomerDomain: vi
        .fn()
        .mockResolvedValue(domain({ status: 'PROVISIONING', dnsRecords: [] })),
    })
    render(<ManagedEmailModal companyId="company-1" client={api} onClose={vi.fn()} />)

    await clickEnabled(/I have my own domain/i)
    fireEvent.change(screen.getByLabelText(/Business domain/i), {
      target: { value: 'Example.com' },
    })
    fireEvent.click(screen.getByRole('button', { name: /Add domain/i }))

    await waitFor(() =>
      expect(api.createCustomerDomain).toHaveBeenCalledWith(
        'company-1',
        expect.objectContaining({ domain: 'example.com' }),
      ),
    )
    expect(screen.getByText('Provisioning')).toBeTruthy()
  })

  it('rejects URLs and email addresses as domain input', async () => {
    const api = client()
    render(<ManagedEmailModal companyId="company-1" client={api} onClose={vi.fn()} />)

    await clickEnabled(/I have my own domain/i)
    fireEvent.change(screen.getByLabelText(/Business domain/i), {
      target: { value: 'https://example.com' },
    })
    fireEvent.click(screen.getByRole('button', { name: /Add domain/i }))

    expect(await screen.findByText(/Enter a bare domain such as example.com/i)).toBeTruthy()
    expect(api.createCustomerDomain).not.toHaveBeenCalled()
  })

  it('renders backend DNS records with required and recommended distinctions and copy actions', async () => {
    const pending = domain({
      dnsRecords: [
        {
          id: 'dkim-1',
          purpose: 'DKIM',
          type: 'CNAME',
          name: 'abc._domainkey.example.com',
          value: 'abc.dkim.target.example',
          priority: null,
          required: true,
          status: 'PENDING',
        },
        {
          id: 'dmarc-1',
          purpose: 'DMARC',
          type: 'TXT',
          name: '_dmarc.example.com',
          value: 'v=DMARC1; p=none',
          priority: null,
          required: false,
          status: 'PENDING',
        },
      ],
    })
    const api = client({ createCustomerDomain: vi.fn().mockResolvedValue(pending) })
    render(<ManagedEmailModal companyId="company-1" client={api} onClose={vi.fn()} />)

    await clickEnabled(/I have my own domain/i)
    fireEvent.change(screen.getByLabelText(/Business domain/i), {
      target: { value: 'example.com' },
    })
    fireEvent.click(screen.getByRole('button', { name: /Add domain/i }))

    expect(await screen.findByText('Required for activation')).toBeTruthy()
    expect(screen.getByText('Recommended')).toBeTruthy()
    expect(screen.getByText('abc._domainkey.example.com')).toBeTruthy()
    expect(screen.getByText('v=DMARC1; p=none')).toBeTruthy()

    fireEvent.click(screen.getAllByRole('button', { name: /Copy Value/i })[0])
    await waitFor(() =>
      expect(navigator.clipboard.writeText).toHaveBeenCalledWith('abc.dkim.target.example'),
    )
    expect(screen.getByText('Copied to clipboard')).toBeTruthy()
    expect(screen.queryByText(/Copied.*verified/i)).toBeNull()
  })

  it('uses manual refresh without changing state client-side', async () => {
    const refreshed = domain({ status: 'ACTIVE', activatedAt: '2026-09-26T06:00:00Z' })
    const api = client({
      createCustomerDomain: vi.fn().mockResolvedValue(domain()),
      refreshDomain: vi.fn().mockResolvedValue(refreshed),
    })
    render(<ManagedEmailModal companyId="company-1" client={api} onClose={vi.fn()} />)

    await clickEnabled(/I have my own domain/i)
    fireEvent.change(screen.getByLabelText(/Business domain/i), {
      target: { value: 'example.com' },
    })
    fireEvent.click(screen.getByRole('button', { name: /Add domain/i }))
    await screen.findByText('Waiting for DNS')

    fireEvent.click(screen.getByRole('button', { name: /Recheck/i }))

    await waitFor(() => expect(api.refreshDomain).toHaveBeenCalledWith('company-1', 'domain-1'))
    expect(await screen.findByText(/Domain active/i)).toBeTruthy()
  })

  it('keeps hosted-domain setup automatic and exposes no DNS task', async () => {
    const hosted = domain({
      type: 'UNIFYDESK_HOSTED',
      domain: 'acme.unifydesk.in',
      status: 'ACTIVE',
      dnsRecords: [],
    })
    const api = client({ createHostedDomain: vi.fn().mockResolvedValue(hosted) })
    render(<ManagedEmailModal companyId="company-1" client={api} onClose={vi.fn()} />)

    await clickEnabled(/Use an UnifyDesk domain/i)

    fireEvent.change(screen.getByLabelText(/Choose your company subdomain/i), {
      target: { value: 'acme' },
    })
    fireEvent.click(screen.getByRole('button', { name: /Submit Subdomain Request/i }))

    expect(await screen.findByText('acme.unifydesk.in')).toBeTruthy()
    expect(screen.getByText(/Domain active/i)).toBeTruthy()
    expect(screen.queryByText(/Add these DNS records/i)).toBeNull()
    expect(screen.queryByText('Required for activation')).toBeNull()
  })

  it('locks sender creation to the active domain local part', async () => {
    const active = domain({ status: 'ACTIVE' })
    const createSender = vi.fn().mockResolvedValue(sender())
    const api = client({
      domain: vi.fn().mockResolvedValue(active),
      createSender,
    })
    render(
      <ManagedEmailModal
        companyId="company-1"
        initialDomainId="domain-1"
        client={api}
        onClose={vi.fn()}
      />,
    )

    expect(await screen.findByText(/Domain active/i)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: /Create sender/i }))

    const localPart = screen.getByLabelText(/Email address/i)
    expect(screen.getByText('@ example.com')).toBeTruthy()

    fireEvent.change(localPart, { target: { value: 'ceo@amazon.com' } })
    expect(
      (screen.getByRole('button', { name: /^Create sender$/i }) as HTMLButtonElement).disabled,
    ).toBe(true)
    expect(createSender).not.toHaveBeenCalled()

    fireEvent.change(localPart, { target: { value: 'offers' } })
    fireEvent.change(screen.getByLabelText(/Display name/i), {
      target: { value: 'Offers' },
    })
    fireEvent.click(screen.getByRole('button', { name: /^Create sender$/i }))

    await waitFor(() =>
      expect(createSender).toHaveBeenCalledWith('company-1', {
        domainId: 'domain-1',
        localPart: 'offers',
        displayName: 'Offers',
        replyToAddress: null,
      }),
    )
  })

  it('does not offer sender creation for a non-active or suspended domain', async () => {
    const api = client({
      domain: vi.fn().mockResolvedValue(domain({ status: 'SUSPENDED_POLICY' })),
    })
    render(
      <ManagedEmailModal
        companyId="company-1"
        initialDomainId="domain-1"
        client={api}
        onClose={vi.fn()}
      />,
    )

    expect(await screen.findByText('Sending suspended')).toBeTruthy()
    expect(screen.queryByRole('button', { name: /Create sender/i })).toBeNull()
    expect(screen.getByText(/sending is not currently ready/i)).toBeTruthy()
  })

  it('keeps stale managed provider internals out of sender management UI', async () => {
    const active = domain({ status: 'ACTIVE' })
    const api = client({
      domain: vi.fn().mockResolvedValue(active),
      senders: vi.fn().mockResolvedValue([sender()]),
    })

    render(
      <ManagedEmailModal
        companyId="company-1"
        initialDomainId="domain-1"
        client={api}
        onClose={vi.fn()}
      />,
    )

    expect(await screen.findByText('offers@example.com')).toBeTruthy()
    expect(
      screen.getByText(
        (_, element) => element?.textContent === 'Example Offers · UnifyDesk Managed Email',
      ),
    ).toBeTruthy()
    expect(screen.queryByText(/ap-south-1/i)).toBeNull()
    expect(screen.queryByText(/50,?000/i)).toBeNull()
    expect(screen.queryByText(/12,?345/i)).toBeNull()
    expect(screen.queryByText(/1000\/sec/i)).toBeNull()
  })

  it('cancels pending polling when the setup view unmounts', async () => {
    vi.useFakeTimers()
    const domainRequest = vi.fn().mockResolvedValue(domain())
    const api = client({ domain: domainRequest })
    const view = render(
      <ManagedEmailModal
        companyId="company-1"
        initialDomainId="domain-1"
        client={api}
        onClose={vi.fn()}
      />,
    )

    await act(async () => {
      await Promise.resolve()
      await Promise.resolve()
    })
    expect(domainRequest).toHaveBeenCalledTimes(1)

    view.unmount()
    await act(async () => {
      vi.advanceTimersByTime(30000)
    })
    expect(domainRequest).toHaveBeenCalledTimes(1)
  })

  it('uses the existing responsive modal and sheet patterns', () => {
    const api = client()
    render(<ManagedEmailModal companyId="company-1" client={api} onClose={vi.fn()} />)
    const dialog = screen.getByRole('dialog')
    expect(dialog.className).toContain('mobile-modal-shell')
    expect(dialog.querySelector('.mobile-sheet')).toBeTruthy()
  })
})
