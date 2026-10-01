import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { managedSmsApi } from '../../features/managedSms/managedSmsApi'
import type {
  ManagedSmsDeliveryHealth,
  ManagedSmsDltProfile,
  ManagedSmsHeader,
  ManagedSmsStatus,
  ManagedSmsTemplate,
  ManagedSmsUsage,
} from '../../features/managedSms/types'
import ManagedSmsPanel from './ManagedSmsPanel'
import ManagedSmsSetupModal from './ManagedSmsSetupModal'

const snackbar = vi.hoisted(() => ({
  error: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
  info: vi.fn(),
  show: vi.fn(),
  dismiss: vi.fn(),
  clear: vi.fn(),
}))

vi.mock('../feedback/SnackbarProvider', () => ({
  useSnackbar: () => snackbar,
}))

type Deferred<T> = {
  promise: Promise<T>
  resolve: (value: T) => void
  reject: (reason?: unknown) => void
}

const deferred = <T,>(): Deferred<T> => {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((nextResolve, nextReject) => {
    resolve = nextResolve
    reject = nextReject
  })
  return { promise, resolve, reject }
}

const status = (company: string): ManagedSmsStatus => ({
  enabled: true,
  state: 'READY',
  nextAction: `${company} status`,
  dltProfile: null,
  readyHeaderCount: company === 'company-a' ? 11 : 22,
  readyTemplateCount: company === 'company-a' ? 33 : 44,
  sendingEnabled: true,
  usage: { acceptedSegments: company === 'company-a' ? 111 : 222, limit: 1000 },
})

const usage = (acceptedSegments: number): ManagedSmsUsage => ({
  acceptedSegments,
  reservedSegments: 0,
  unknownSegments: 0,
  limit: 1000,
  remaining: 1000 - acceptedSegments,
  sendingEnabled: true,
})

const health = (delivered: number): ManagedSmsDeliveryHealth => ({
  status: 'HEALTHY',
  delivered,
  failed: 0,
  rejected: 0,
  unknown: 0,
  actionRequired: `health-${delivered}`,
})

const profile = (company: string): ManagedSmsDltProfile => ({
  id: `${company}-profile`,
  legalName: `${company} legal name`,
  peIdMasked: `${company}-pe`,
  chainState: 'READY',
})

const header = (company: string): ManagedSmsHeader => ({
  id: `${company}-header-id`,
  header: company === 'company-a' ? 'HEADERA' : 'HEADERB',
  messageCategory: 'SERVICE',
  state: 'READY',
})

const template = (company: string): ManagedSmsTemplate => ({
  id: `${company}-template-id`,
  name: `${company} template`,
  messageCategory: 'SERVICE',
  state: 'READY',
  activeVersion: {
    id: `${company}-version`,
    version: 1,
    dltTemplateId: `${company}-dlt`,
    bodyText: `${company} template body`,
    variables: [],
    allowedHeaderIds: [`${company}-header-id`],
  },
})

describe('Managed SMS tenant request invalidation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('keeps delayed company-A panel results out of the company-B UI', async () => {
    const aStatus = deferred<ManagedSmsStatus>()
    const bStatus = deferred<ManagedSmsStatus>()
    const statusSpy = vi.spyOn(managedSmsApi, 'status').mockImplementation((companyId) => {
      return companyId === 'company-a' ? aStatus.promise : bStatus.promise
    })
    const usageSpy = vi
      .spyOn(managedSmsApi, 'usage')
      .mockImplementation((companyId) =>
        Promise.resolve(usage(companyId === 'company-a' ? 111 : 222)),
      )
    const healthSpy = vi
      .spyOn(managedSmsApi, 'deliveryHealth')
      .mockImplementation((companyId) =>
        Promise.resolve(health(companyId === 'company-a' ? 111 : 222)),
      )

    const view = render(
      <ManagedSmsPanel
        companyId="company-a"
        canModify
        onOpenSetup={vi.fn()}
      />,
    )

    await waitFor(() => expect(statusSpy).toHaveBeenCalledWith('company-a'))

    view.rerender(
      <ManagedSmsPanel
        companyId="company-b"
        canModify
        onOpenSetup={vi.fn()}
      />,
    )
    await waitFor(() => expect(statusSpy).toHaveBeenCalledWith('company-b'))

    await act(async () => {
      aStatus.resolve(status('company-a'))
      await aStatus.promise
    })

    expect(screen.queryByText('company-a status')).not.toBeInTheDocument()
    expect(screen.queryByText('111')).not.toBeInTheDocument()
    expect(usageSpy).not.toHaveBeenCalledWith('company-a')
    expect(healthSpy).not.toHaveBeenCalledWith('company-a')

    await act(async () => {
      bStatus.resolve(status('company-b'))
      await bStatus.promise
    })

    expect(await screen.findByText('company-b status')).toBeInTheDocument()
    expect(await screen.findByText('222')).toBeInTheDocument()
    expect(await screen.findByText('health-222')).toBeInTheDocument()
  })

  it('does not surface a stale company-A panel rejection after switching to B', async () => {
    snackbar.error.mockClear()
    const aStatus = deferred<ManagedSmsStatus>()
    vi.spyOn(managedSmsApi, 'status').mockImplementation((companyId) =>
      companyId === 'company-a' ? aStatus.promise : Promise.resolve(status('company-b')),
    )
    vi.spyOn(managedSmsApi, 'usage').mockResolvedValue(usage(222))
    vi.spyOn(managedSmsApi, 'deliveryHealth').mockResolvedValue(health(222))

    const view = render(
      <ManagedSmsPanel companyId="company-a" canModify onOpenSetup={vi.fn()} />,
    )
    view.rerender(
      <ManagedSmsPanel companyId="company-b" canModify onOpenSetup={vi.fn()} />,
    )

    await screen.findByText('company-b status')

    await act(async () => {
      aStatus.reject(new Error('company-a panel failure'))
      try {
        await aStatus.promise
      } catch {
        // Expected rejection: the stale request must be ignored by company B.
      }
    })

    expect(snackbar.error).not.toHaveBeenCalled()
    expect(screen.getByText('company-b status')).toBeInTheDocument()
  })

  it('keeps delayed company-A setup data out of the company-B modal', async () => {
    const aStatus = deferred<ManagedSmsStatus>()
    const aProfile = deferred<ManagedSmsDltProfile | null>()
    const aHeaders = deferred<ManagedSmsHeader[]>()
    const aTemplates = deferred<ManagedSmsTemplate[]>()
    const bStatus = deferred<ManagedSmsStatus>()
    const bProfile = deferred<ManagedSmsDltProfile | null>()
    const bHeaders = deferred<ManagedSmsHeader[]>()
    const bTemplates = deferred<ManagedSmsTemplate[]>()

    vi.spyOn(managedSmsApi, 'status').mockImplementation((companyId) =>
      companyId === 'company-a' ? aStatus.promise : bStatus.promise,
    )
    vi.spyOn(managedSmsApi, 'dltProfile').mockImplementation((companyId) =>
      companyId === 'company-a' ? aProfile.promise : bProfile.promise,
    )
    vi.spyOn(managedSmsApi, 'headers').mockImplementation((companyId) =>
      companyId === 'company-a' ? aHeaders.promise : bHeaders.promise,
    )
    vi.spyOn(managedSmsApi, 'templates').mockImplementation((companyId) =>
      companyId === 'company-a' ? aTemplates.promise : bTemplates.promise,
    )

    const view = render(
      <ManagedSmsSetupModal
        companyId="company-a"
        canModify
        onClose={vi.fn()}
        onChanged={vi.fn()}
      />,
    )

    await waitFor(() => expect(managedSmsApi.status).toHaveBeenCalledWith('company-a'))

    view.rerender(
      <ManagedSmsSetupModal
        companyId="company-b"
        canModify
        onClose={vi.fn()}
        onChanged={vi.fn()}
      />,
    )
    await waitFor(() => expect(managedSmsApi.status).toHaveBeenCalledWith('company-b'))

    await act(async () => {
      aStatus.resolve(status('company-a'))
      aProfile.resolve(profile('company-a'))
      aHeaders.resolve([header('company-a')])
      aTemplates.resolve([template('company-a')])
      await Promise.all([aStatus.promise, aProfile.promise, aHeaders.promise, aTemplates.promise])
    })

    expect(screen.queryByText('company-a status')).not.toBeInTheDocument()
    expect(screen.queryByText('company-a legal name')).not.toBeInTheDocument()
    expect(screen.queryByText('HEADERA')).not.toBeInTheDocument()
    expect(screen.queryByText('company-a template')).not.toBeInTheDocument()
    expect(screen.queryByText('company-a template body')).not.toBeInTheDocument()

    await act(async () => {
      bStatus.resolve(status('company-b'))
      bProfile.resolve(profile('company-b'))
      bHeaders.resolve([header('company-b')])
      bTemplates.resolve([template('company-b')])
      await Promise.all([bStatus.promise, bProfile.promise, bHeaders.promise, bTemplates.promise])
    })

    expect(await screen.findByText('company-b status')).toBeInTheDocument()
    expect(screen.getByText('company-b legal name')).toBeInTheDocument()
    expect(screen.getByText('HEADERB')).toBeInTheDocument()
    expect(screen.getByText('company-b template')).toBeInTheDocument()
    expect(screen.getByText('company-b template body')).toBeInTheDocument()
  })

  it('does not surface stale company-A setup failures after switching to B', async () => {
    snackbar.error.mockClear()
    const aStatus = deferred<ManagedSmsStatus>()
    const aProfile = deferred<ManagedSmsDltProfile | null>()
    const aHeaders = deferred<ManagedSmsHeader[]>()
    const aTemplates = deferred<ManagedSmsTemplate[]>()

    vi.spyOn(managedSmsApi, 'status').mockImplementation((companyId) =>
      companyId === 'company-a' ? aStatus.promise : Promise.resolve(status('company-b')),
    )
    vi.spyOn(managedSmsApi, 'dltProfile').mockImplementation((companyId) =>
      companyId === 'company-a' ? aProfile.promise : Promise.resolve(profile('company-b')),
    )
    vi.spyOn(managedSmsApi, 'headers').mockImplementation((companyId) =>
      companyId === 'company-a' ? aHeaders.promise : Promise.resolve([header('company-b')]),
    )
    vi.spyOn(managedSmsApi, 'templates').mockImplementation((companyId) =>
      companyId === 'company-a' ? aTemplates.promise : Promise.resolve([template('company-b')]),
    )

    const view = render(
      <ManagedSmsSetupModal
        companyId="company-a"
        canModify
        onClose={vi.fn()}
        onChanged={vi.fn()}
      />,
    )
    view.rerender(
      <ManagedSmsSetupModal
        companyId="company-b"
        canModify
        onClose={vi.fn()}
        onChanged={vi.fn()}
      />,
    )

    await screen.findByText('company-b status')

    await act(async () => {
      aStatus.reject(new Error('company-a status failure'))
      aProfile.reject(new Error('company-a profile failure'))
      aHeaders.reject(new Error('company-a headers failure'))
      aTemplates.reject(new Error('company-a templates failure'))
      await Promise.allSettled([
        aStatus.promise,
        aProfile.promise,
        aHeaders.promise,
        aTemplates.promise,
      ])
    })

    expect(snackbar.error).not.toHaveBeenCalled()
    expect(screen.getByText('company-b status')).toBeInTheDocument()
    expect(screen.getByText('company-b legal name')).toBeInTheDocument()
    expect(screen.getByText('HEADERB')).toBeInTheDocument()
    expect(screen.getByText('company-b template')).toBeInTheDocument()
  })
  it('does not continue a stale company-A setup mutation after switching to B', async () => {
    snackbar.success.mockClear()
    snackbar.error.mockClear()
    const onChanged = vi.fn()
    const aCreate = deferred<ManagedSmsDltProfile>()

    vi.spyOn(managedSmsApi, 'status').mockImplementation((companyId) =>
      Promise.resolve(status(companyId)),
    )
    vi.spyOn(managedSmsApi, 'dltProfile').mockResolvedValue(null)
    vi.spyOn(managedSmsApi, 'headers').mockResolvedValue([])
    vi.spyOn(managedSmsApi, 'templates').mockResolvedValue([])
    vi.spyOn(managedSmsApi, 'createDltProfile').mockImplementation((companyId) =>
      companyId === 'company-a' ? aCreate.promise : Promise.resolve(profile(companyId)),
    )
    const submitSpy = vi
      .spyOn(managedSmsApi, 'submitDltProfile')
      .mockResolvedValue(profile('company-a'))

    const view = render(
      <ManagedSmsSetupModal
        companyId="company-a"
        canModify
        onClose={vi.fn()}
        onChanged={onChanged}
      />,
    )

    await screen.findByText('company-a status')
    fireEvent.change(screen.getByLabelText('Registered legal business name'), {
      target: { value: 'Company A Legal' },
    })
    fireEvent.change(screen.getByLabelText('PE / Entity ID'), {
      target: { value: 'PE-A' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Submit DLT profile' }))

    await waitFor(() =>
      expect(managedSmsApi.createDltProfile).toHaveBeenCalledWith(
        'company-a',
        expect.objectContaining({ legalName: 'Company A Legal', peId: 'PE-A' }),
      ),
    )

    view.rerender(
      <ManagedSmsSetupModal
        companyId="company-b"
        canModify
        onClose={vi.fn()}
        onChanged={onChanged}
      />,
    )
    await screen.findByText('company-b status')

    await act(async () => {
      aCreate.resolve(profile('company-a'))
      await aCreate.promise
    })

    expect(submitSpy).not.toHaveBeenCalled()
    expect(onChanged).not.toHaveBeenCalled()
    expect(snackbar.success).not.toHaveBeenCalled()
    expect(snackbar.error).not.toHaveBeenCalled()
    expect(screen.getByText('company-b status')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Submit DLT profile' })).not.toBeDisabled()
  })

  it('does not surface a stale company-A mutation failure after switching to B', async () => {
    snackbar.error.mockClear()
    const onChanged = vi.fn()
    const aCreate = deferred<ManagedSmsDltProfile>()

    vi.spyOn(managedSmsApi, 'status').mockImplementation((companyId) =>
      Promise.resolve(status(companyId)),
    )
    vi.spyOn(managedSmsApi, 'dltProfile').mockResolvedValue(null)
    vi.spyOn(managedSmsApi, 'headers').mockResolvedValue([])
    vi.spyOn(managedSmsApi, 'templates').mockResolvedValue([])
    vi.spyOn(managedSmsApi, 'createDltProfile').mockImplementation((companyId) =>
      companyId === 'company-a' ? aCreate.promise : Promise.resolve(profile(companyId)),
    )
    vi.spyOn(managedSmsApi, 'submitDltProfile').mockResolvedValue(profile('company-a'))

    const view = render(
      <ManagedSmsSetupModal
        companyId="company-a"
        canModify
        onClose={vi.fn()}
        onChanged={onChanged}
      />,
    )

    await screen.findByText('company-a status')
    fireEvent.change(screen.getByLabelText('Registered legal business name'), {
      target: { value: 'Company A Legal' },
    })
    fireEvent.change(screen.getByLabelText('PE / Entity ID'), {
      target: { value: 'PE-A' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Submit DLT profile' }))

    await waitFor(() =>
      expect(managedSmsApi.createDltProfile).toHaveBeenCalledWith(
        'company-a',
        expect.any(Object),
      ),
    )

    view.rerender(
      <ManagedSmsSetupModal
        companyId="company-b"
        canModify
        onClose={vi.fn()}
        onChanged={onChanged}
      />,
    )
    await screen.findByText('company-b status')

    await act(async () => {
      aCreate.reject(new Error('company-a mutation failure'))
      try {
        await aCreate.promise
      } catch {
        // Expected rejection: stale company-A failures must be ignored by company B.
      }
    })

    expect(onChanged).not.toHaveBeenCalled()
    expect(snackbar.error).not.toHaveBeenCalled()
    expect(screen.getByText('company-b status')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Submit DLT profile' })).not.toBeDisabled()
  })

})
