import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import TaskBoard from './TaskBoard'
import type { CrmTask, CrmTaskColumn } from '../../features/contacts/crmWorkApi'
const columns: CrmTaskColumn[] = [
  { key: 'OPEN', name: 'To do', statusGroup: 'OPEN', position: 0, systemColumn: true, version: 0 },
  {
    key: 'STEP_1',
    name: 'Review',
    statusGroup: 'IN_PROGRESS',
    position: 1,
    systemColumn: false,
    version: 2,
  },
]
const task: CrmTask = {
  id: '10',
  title: 'Call customer',
  details: 'Renewal',
  status: 'OPEN',
  columnKey: 'OPEN',
  priority: 'HIGH',
  dueAt: null,
  assigneeUserId: '1',
  contactId: null,
  accountId: null,
  version: 3,
}
afterEach(cleanup)
describe('Task board interactions', () => {
  it('moves a dragged task to a custom step, ignoring drops in its current step', () => {
    const onMove = vi.fn()
    render(
      <TaskBoard
        tasks={[task]}
        columns={columns}
        busy={false}
        canMove
        onMove={onMove}
        onOpen={vi.fn()}
        onEdit={vi.fn()}
        onNew={vi.fn()}
        assigneeName={() => 'Alex'}
      />,
    )
    const transfer = { setData: vi.fn(), getData: () => task.id, effectAllowed: '', dropEffect: '' }
    fireEvent.dragStart(screen.getByRole('article', { name: task.title }), {
      dataTransfer: transfer,
    })
    fireEvent.drop(screen.getByRole('region', { name: 'To do column' }), { dataTransfer: transfer })
    expect(onMove).not.toHaveBeenCalled()
    fireEvent.dragStart(screen.getByRole('article', { name: task.title }), {
      dataTransfer: transfer,
    })
    fireEvent.dragOver(screen.getByRole('region', { name: 'Review column' }), {
      dataTransfer: transfer,
    })
    fireEvent.drop(screen.getByRole('region', { name: 'Review column' }), {
      dataTransfer: transfer,
    })
    expect(onMove).toHaveBeenCalledWith(task, 'STEP_1')
  })
  it('supports a keyboard accessible step selector and creation within a column', () => {
    const onMove = vi.fn(),
      onNew = vi.fn()
    render(
      <TaskBoard
        tasks={[task]}
        columns={columns}
        busy={false}
        canMove
        onMove={onMove}
        onOpen={vi.fn()}
        onEdit={vi.fn()}
        onNew={onNew}
        assigneeName={() => 'Alex'}
      />,
    )
    fireEvent.change(screen.getByRole('combobox', { name: 'Move Call customer to step' }), {
      target: { value: 'STEP_1' },
    })
    expect(onMove).toHaveBeenCalledWith(task, 'STEP_1')
    fireEvent.click(screen.getByRole('button', { name: 'Create task in Review' }))
    expect(onNew).toHaveBeenCalledWith('STEP_1')
  })
  it('prevents changes in read only mode and while saving', () => {
    const onMove = vi.fn()
    const props = {
      tasks: [task],
      columns,
      busy: false,
      canMove: false,
      onMove,
      onOpen: vi.fn(),
      onEdit: vi.fn(),
      onNew: vi.fn(),
      assigneeName: () => 'Alex',
    }
    const { rerender } = render(<TaskBoard {...props} />)
    expect(screen.queryByRole('combobox')).toBeNull()
    expect(screen.getByRole('article', { name: task.title })).toHaveAttribute('draggable', 'false')
    fireEvent.drop(screen.getByRole('region', { name: 'Review column' }), {
      dataTransfer: { getData: () => task.id },
    })
    expect(onMove).not.toHaveBeenCalled()
    rerender(<TaskBoard {...props} canMove busy />)
    expect(screen.getByRole('combobox')).toBeDisabled()
    fireEvent.drop(screen.getByRole('region', { name: 'Review column' }), {
      dataTransfer: { getData: () => task.id },
    })
    expect(onMove).not.toHaveBeenCalled()
  })
})
