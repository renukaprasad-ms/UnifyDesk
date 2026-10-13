import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import CrmTasksPage from './CrmTasksPage'
const mocks = vi.hoisted(() => ({
  tasks: vi.fn(),
  columns: vi.fn(),
  move: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  users: vi.fn(),
  createColumn: vi.fn(),
  can: vi.fn((p: string) => p.length > 0),
  capture: () => ({ companyId: '101', epoch: 0 }),
  isCurrent: () => true,
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
vi.mock('../../features/company/companyApi', () => ({ companyApi: { users: mocks.users } }))
vi.mock('../../lib/api/apiClient', () => ({ apiErrorMessage: (err: Error) => err.message }))
vi.mock('../../features/contacts/crmWorkApi', () => ({
  crmWorkApi: {
    tasks: mocks.tasks,
    taskColumns: mocks.columns,
    taskColumn: mocks.move,
    addTask: mocks.create,
    updateTask: mocks.update,
    createTaskColumn: mocks.createColumn,
    accounts: async () => ({ items: [] }),
  },
}))
const columns = [
  { key: 'OPEN', name: 'To do', statusGroup: 'OPEN', position: 0, systemColumn: true, version: 0 },
  {
    key: 'STEP_1',
    name: 'Review',
    statusGroup: 'IN_PROGRESS',
    position: 1,
    systemColumn: false,
    version: 0,
  },
]
const task = {
  id: '10',
  title: 'Call customer',
  details: null,
  status: 'OPEN',
  columnKey: 'OPEN',
  priority: 'NORMAL',
  dueAt: null,
  assigneeUserId: '1',
  contactId: null,
  accountId: null,
  version: 3,
}
beforeEach(() => {
  vi.clearAllMocks()
  mocks.users.mockResolvedValue([
    { userId: '1', name: 'Alex', status: 'ACTIVE' },
    { userId: '2', name: 'Sam', status: 'ACTIVE' },
  ])
  mocks.can.mockImplementation((p) => p.length > 0)
  mocks.tasks.mockResolvedValue({ items: [task], nextCursor: null })
  mocks.columns.mockResolvedValue(columns)
  mocks.create.mockResolvedValue(task)
})
afterEach(cleanup)
const mount = () =>
  render(
    <MemoryRouter>
      <CrmTasksPage />
    </MemoryRouter>,
  )
it('creates tasks directly in a custom step and persists their step key', async () => {
  mount()
  await screen.findByRole('button', { name: 'Create task in Review' })
  fireEvent.click(screen.getByRole('button', { name: 'Create task in Review' }))
  fireEvent.change(screen.getByPlaceholderText('Follow up with customer'), {
    target: { value: 'Check contract' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Create task' }))
  await waitFor(() =>
    expect(mocks.create).toHaveBeenCalledWith(
      '101',
      'Check contract',
      undefined,
      expect.objectContaining({ columnKey: 'STEP_1' }),
    ),
  )
})
it('rolls a failed move back without reloading the board', async () => {
  mocks.move.mockRejectedValue(new Error('Task changed by another teammate'))
  mount()
  const select = await screen.findByRole('combobox', { name: 'Move Call customer to step' })
  fireEvent.change(select, { target: { value: 'STEP_1' } })
  expect(await screen.findByRole('alert')).toHaveTextContent('Task changed by another teammate')
  expect(mocks.move).toHaveBeenCalledWith('101', task, 'STEP_1')
  expect(mocks.tasks).toHaveBeenCalledTimes(1)
})
it('adds a custom step with its selected status group', async () => {
  mocks.createColumn.mockResolvedValue([
    ...columns,
    {
      key: 'STEP_2',
      name: 'QA',
      statusGroup: 'IN_PROGRESS',
      position: 2,
      systemColumn: false,
      version: 0,
    },
  ])
  mount()
  await screen.findByRole('button', { name: 'Create task in Review' })
  fireEvent.click(screen.getByRole('button', { name: 'Manage steps' }))
  fireEvent.change(screen.getByPlaceholderText('Review'), { target: { value: 'QA' } })
  fireEvent.click(screen.getByRole('button', { name: 'Add step' }))
  await waitFor(() => expect(mocks.createColumn).toHaveBeenCalledWith('101', 'QA', 'IN_PROGRESS'))
  expect(await screen.findByRole('region', { name: 'QA column' })).toBeInTheDocument()
})
it('hides configuration from people without workflow permission', async () => {
  mocks.can.mockImplementation((p) => p !== 'crm.task.workflow.modify')
  mount()
  await screen.findByRole('button', { name: 'Create task in Review' })
  expect(screen.queryByRole('button', { name: 'Manage steps' })).toBeNull()
})

it('moves instantly on a slow connection and reconciles the saved version without refetching', async () => {
  let resolve!: (value: typeof task) => void
  mocks.move.mockImplementation(
    () =>
      new Promise((r) => {
        resolve = r
      }),
  )
  mount()
  const select = await screen.findByRole('combobox', { name: 'Move Call customer to step' })
  fireEvent.change(select, { target: { value: 'STEP_1' } })
  const review = screen.getByRole('region', { name: 'Review column' })
  expect(within(review).getByRole('article', { name: task.title })).toHaveAttribute(
    'aria-busy',
    'true',
  )
  expect(within(review).getByRole('status')).toHaveTextContent('Saving')
  expect(screen.queryByText('Loading tasks...')).toBeNull()
  expect(screen.getByRole('button', { name: 'Refresh' })).toBeDisabled()
  expect(mocks.tasks).toHaveBeenCalledTimes(1)
  await act(async () =>
    resolve({ ...task, columnKey: 'STEP_1', status: 'IN_PROGRESS', version: 4 }),
  )
  expect(within(review).getByRole('article', { name: task.title })).toHaveAttribute(
    'aria-busy',
    'false',
  )
  expect(screen.queryByText('Saving...')).toBeNull()
  mocks.move.mockResolvedValue({ ...task, version: 5 })
  fireEvent.change(screen.getByRole('combobox', { name: 'Move Call customer to step' }), {
    target: { value: 'OPEN' },
  })
  await waitFor(() =>
    expect(mocks.move).toHaveBeenLastCalledWith(
      '101',
      expect.objectContaining({ version: 4, columnKey: 'STEP_1' }),
      'OPEN',
    ),
  )
  expect(mocks.tasks).toHaveBeenCalledTimes(1)
})
it('rolls back only the failed task while another card saves successfully', async () => {
  const other = { ...task, id: '11', title: 'Send proposal' }
  mocks.tasks.mockResolvedValue({ items: [task, other], nextCursor: null })
  let reject!: (error: Error) => void, resolve!: (value: typeof task) => void
  mocks.move.mockImplementation((_company, entry) =>
    entry.id === '10'
      ? new Promise((_r, j) => {
          reject = j
        })
      : new Promise((r) => {
          resolve = r
        }),
  )
  mount()
  await screen.findByRole('combobox', { name: 'Move Call customer to step' })
  fireEvent.change(screen.getByRole('combobox', { name: 'Move Call customer to step' }), {
    target: { value: 'STEP_1' },
  })
  fireEvent.change(screen.getByRole('combobox', { name: 'Move Send proposal to step' }), {
    target: { value: 'STEP_1' },
  })
  await act(async () =>
    resolve({ ...other, columnKey: 'STEP_1', status: 'IN_PROGRESS', version: 4 }),
  )
  await act(async () => reject(new Error('Connection lost')))
  expect(
    within(screen.getByRole('region', { name: 'To do column' })).getByRole('article', {
      name: task.title,
    }),
  ).toBeInTheDocument()
  expect(
    within(screen.getByRole('region', { name: 'Review column' })).getByRole('article', {
      name: other.title,
    }),
  ).toBeInTheDocument()
  expect(screen.getByRole('alert')).toHaveTextContent('Connection lost')
  expect(mocks.tasks).toHaveBeenCalledTimes(1)
})
it('keeps creation in a dialog and hides filters until the filter button opens them', async () => {
  mount()
  await screen.findByRole('button', { name: 'New task' })
  expect(screen.queryByRole('combobox', { name: 'Filter task status' })).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Filters' }))
  expect(screen.getByRole('combobox', { name: 'Filter task status' })).toBeInTheDocument()
  fireEvent.keyDown(document, { key: 'Escape' })
  expect(screen.queryByRole('region', { name: 'Filter options' })).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'New task' }))
  expect(screen.getByRole('dialog', { name: 'New task' })).toContainElement(
    screen.getByPlaceholderText('Follow up with customer'),
  )
  fireEvent.click(screen.getByRole('button', { name: 'Close New task' }))
  expect(screen.queryByRole('dialog')).toBeNull()
})

it('removes an acknowledged move from results when it leaves the active status filter', async () => {
  let resolve!: (value: typeof task) => void
  mocks.move.mockImplementation(
    () =>
      new Promise((r) => {
        resolve = r
      }),
  )
  mount()
  await screen.findByRole('combobox', { name: 'Move Call customer to step' })
  fireEvent.click(screen.getByRole('button', { name: 'Filters' }))
  fireEvent.change(screen.getByRole('combobox', { name: 'Filter task status' }), {
    target: { value: 'OPEN' },
  })
  fireEvent.keyDown(document, { key: 'Escape' })
  await waitFor(() => expect(mocks.tasks).toHaveBeenCalledTimes(2))
  const select = await screen.findByRole('combobox', { name: 'Move Call customer to step' })
  fireEvent.change(select, { target: { value: 'STEP_1' } })
  expect(
    within(screen.getByRole('region', { name: 'Review column' })).getByRole('article', {
      name: task.title,
    }),
  ).toBeInTheDocument()
  await act(async () =>
    resolve({ ...task, columnKey: 'STEP_1', status: 'IN_PROGRESS', version: 4 }),
  )
  expect(screen.queryByRole('article', { name: task.title })).toBeNull()
  expect(mocks.tasks).toHaveBeenCalledTimes(2)
})

it('assigns a task from its board card without requiring the list editor', async () => {
  mocks.update.mockResolvedValue({ ...task, assigneeUserId: '2', version: 4 })
  mount()
  fireEvent.click(await screen.findByRole('button', { name: 'Assign Call customer' }))
  expect(screen.getByRole('dialog', { name: 'Assign task' })).toBeInTheDocument()
  fireEvent.change(screen.getByRole('combobox', { name: 'Assign to' }), { target: { value: '2' } })
  fireEvent.click(screen.getByRole('button', { name: 'Save assignment' }))
  await waitFor(() =>
    expect(mocks.update).toHaveBeenCalledWith(
      '101',
      expect.objectContaining({ version: 3 }),
      expect.objectContaining({ assigneeUserId: '2', title: task.title }),
    ),
  )
  await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Assign task' })).toBeNull())
  expect(screen.getByRole('button', { name: 'Board' })).toHaveAttribute('aria-pressed', 'true')
  expect(screen.getByRole('button', { name: 'View history for Call customer' })).toBeInTheDocument()
})
