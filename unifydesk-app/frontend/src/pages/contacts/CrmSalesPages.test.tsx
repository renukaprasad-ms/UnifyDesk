import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CrmDealsPage from './CrmDealsPage'
import CrmActivitiesPage from './CrmActivitiesPage'

const mocks = vi.hoisted(() => ({
  deals: vi.fn(),
  deal: vi.fn(),
  createDeal: vi.fn(),
  moveDeal: vi.fn(),
  editDeal: vi.fn(),
  users: vi.fn(),
  activities: vi.fn(),
  history: vi.fn(),
  capture: () => ({ companyId: '101', epoch: 0 }),
  isCurrent: () => true,
  can: vi.fn((permission: string) => permission.length > 0),
}))
vi.mock('../../store/hooks', () => ({ useAppSelector: (selector: () => unknown) => selector() }))
vi.mock('../../store/workspace/workspaceSlice', () => ({
  selectSelectedCompanyId: () => '101',
  selectWorkspaceEpoch: () => 0,
}))
vi.mock('../../features/workspace/useWorkspaceEpochScope', () => ({
  useWorkspaceEpochScope: () => ({ capture: mocks.capture, isCurrent: mocks.isCurrent }),
}))
vi.mock('../../features/iam/useIam', () => ({ useIam: () => ({ can: mocks.can }) }))
vi.mock('../../lib/api/apiClient', () => ({
  default: {},
  apiErrorMessage: (error: Error) => error.message,
}))
vi.mock('../../features/company/companyApi', () => ({ companyApi: { users: mocks.users } }))
vi.mock('../../features/contacts/contactsApi', () => ({
  contactsApi: { list: async () => ({ items: [] }) },
}))
vi.mock('../../features/contacts/crmWorkApi', () => ({
  crmWorkApi: { accounts: async () => ({ items: [] }) },
}))
vi.mock('../../features/contacts/crmSalesApi', async (original) => ({
  ...(await original<typeof import('../../features/contacts/crmSalesApi')>()),
  crmSalesApi: {
    deals: mocks.deals,
    deal: mocks.deal,
    createDeal: mocks.createDeal,
    moveDeal: mocks.moveDeal,
    editDeal: mocks.editDeal,
    activities: mocks.activities,
    history: mocks.history,
  },
}))
const deal = {
  id: '501',
  title: 'Renewal',
  description: null,
  amount: '10.05',
  currency: 'INR',
  stage: 'NEW',
  expectedCloseDate: null,
  ownerUserId: '1',
  ownerName: 'Alex',
  contactId: null,
  contactName: null,
  accountId: null,
  accountName: null,
  version: 3,
  createdAt: '2026-10-13T08:00:00Z',
  updatedAt: '2026-10-13T08:00:00Z',
}
beforeEach(() => {
  vi.clearAllMocks()
  mocks.users.mockResolvedValue([
    { userId: '1', name: 'Alex', status: 'ACTIVE' },
    { userId: '2', name: 'Sam', status: 'ACTIVE' },
  ])
  mocks.can.mockReturnValue(true)
  mocks.deals.mockResolvedValue({ items: [deal], nextCursor: null, hasMore: false })
  mocks.deal.mockResolvedValue(deal)
  mocks.history.mockResolvedValue([])
})
afterEach(cleanup)
describe('CRM sales interactions', () => {
  it('does not offer creation or stage mutation to read-only teammates', async () => {
    mocks.can.mockImplementation((permission) => permission !== 'crm.deal.modify')
    render(
      <MemoryRouter>
        <CrmDealsPage />
      </MemoryRouter>,
    )
    await screen.findByRole('button', { name: 'Renewal' })
    expect(screen.queryByRole('button', { name: 'New deal' })).toBeNull()
    expect(screen.queryByLabelText('Stage for Renewal')).toBeNull()
    expect(screen.getByRole('button', { name: 'Renewal' }).closest('article')).toHaveAttribute(
      'draggable',
      'false',
    )
  })
  it('preserves an exact decimal value when creating an opportunity', async () => {
    mocks.createDeal.mockImplementation(async (_company, fields) => {
      const created = { ...deal, ...fields, id: '502' }
      mocks.deal.mockResolvedValue(created)
      return created
    })
    render(
      <MemoryRouter>
        <CrmDealsPage />
      </MemoryRouter>,
    )
    await screen.findByRole('button', { name: 'Renewal' })
    fireEvent.click(screen.getByRole('button', { name: 'New deal' }))
    fireEvent.change(screen.getByLabelText('Deal name'), { target: { value: 'Large renewal' } })
    fireEvent.change(screen.getByLabelText('Value'), { target: { value: '123456789012345.67' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save deal' }))
    await waitFor(() =>
      expect(mocks.createDeal).toHaveBeenCalledWith(
        '101',
        expect.objectContaining({
          title: 'Large renewal',
          amount: '123456789012345.67',
          currency: 'INR',
        }),
      ),
    )
  })
  it('keeps a stage conflict visible after refreshing the latest deal data', async () => {
    mocks.moveDeal.mockRejectedValue(new Error('The deal was updated by another teammate.'))
    render(
      <MemoryRouter>
        <CrmDealsPage />
      </MemoryRouter>,
    )
    await screen.findByRole('button', { name: 'Renewal' })
    fireEvent.change(screen.getByLabelText('Stage for Renewal'), { target: { value: 'WON' } })
    expect(await screen.findByRole('alert')).toHaveTextContent('updated by another teammate')
    expect(mocks.moveDeal).toHaveBeenCalledWith(
      '101',
      expect.objectContaining({ version: 3 }),
      'WON',
    )
    expect(screen.getByLabelText('Stage for Renewal')).toHaveValue('NEW')
  })
  it('paginates activity without merging different sources that share an event ID', async () => {
    const common = {
      id: '900',
      detail: 'Created',
      actorName: 'Alex',
      actorUsername: 'alex',
      contactId: null,
      occurredAt: '2026-10-13T08:00:00Z',
    }
    mocks.activities
      .mockResolvedValueOnce({
        items: [{ ...common, source: 'TASK', title: 'Follow up', href: '/crm/tasks?taskId=401' }],
        nextCursor: 'next-page',
        hasMore: true,
      })
      .mockResolvedValueOnce({
        items: [{ ...common, source: 'DEAL', title: 'Renewal', href: '/crm/deals?dealId=501' }],
        nextCursor: null,
        hasMore: false,
      })
    render(
      <MemoryRouter>
        <CrmActivitiesPage />
      </MemoryRouter>,
    )
    await screen.findByText('Follow up')
    fireEvent.click(screen.getByRole('button', { name: 'Load more activity' }))
    await screen.findByText('Renewal')
    expect(screen.getByText('Follow up')).toBeInTheDocument()
    expect(mocks.activities).toHaveBeenLastCalledWith(
      '101',
      expect.objectContaining({ cursor: 'next-page' }),
    )
    expect(
      screen.getAllByRole('link', { name: 'Open source' }).map((link) => link.getAttribute('href')),
    ).toEqual(['/crm/tasks?taskId=401', '/crm/deals?dealId=501'])
  })
})

it('moves deals immediately and saves their stage without replacing the board with loading', async () => {
  let resolve!: (value: typeof deal) => void
  mocks.moveDeal.mockImplementation(
    () =>
      new Promise((r) => {
        resolve = r
      }),
  )
  render(
    <MemoryRouter>
      <CrmDealsPage />
    </MemoryRouter>,
  )
  await screen.findByRole('button', { name: 'Renewal' })
  fireEvent.change(screen.getByLabelText('Stage for Renewal'), { target: { value: 'WON' } })
  expect(
    within(screen.getByRole('region', { name: 'Won stage' })).getByRole('button', {
      name: 'Renewal',
    }),
  ).toBeInTheDocument()
  expect(screen.getByLabelText('Stage for Renewal')).toBeDisabled()
  expect(mocks.deals).toHaveBeenCalledTimes(1)
  await act(async () => resolve({ ...deal, stage: 'WON', version: 4 }))
  expect(screen.getByLabelText('Stage for Renewal')).toBeEnabled()
  expect(screen.getByLabelText('Stage for Renewal')).toHaveValue('WON')
  expect(mocks.deals).toHaveBeenCalledTimes(1)
})

it('renders deal cards with title, amount, and linked customer', async () => {
  render(
    <MemoryRouter>
      <CrmDealsPage />
    </MemoryRouter>,
  )
  const card = await screen.findByRole('button', { name: 'Renewal' })
  expect(card).toBeInTheDocument()
  expect(screen.getByText('No linked customer')).toBeInTheDocument()
})
it('shows empty drop columns instead of replacing an empty pipeline with a splash panel', async () => {
  mocks.deals.mockResolvedValue({ items: [], nextCursor: null, hasMore: false })
  render(
    <MemoryRouter>
      <CrmDealsPage />
    </MemoryRouter>,
  )
  expect(await screen.findByRole('region', { name: 'New stage' })).toBeInTheDocument()
  expect(screen.getAllByRole('region', { name: /stage$/ })).toHaveLength(6)
})
