import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { contactsApi } from './contactsApi'
import { useContacts } from './useContacts'

vi.mock('./contactsApi', () => ({
  contactsApi: {
    list: vi.fn(),
  },
}))

const deferred = <T,>() => {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((res) => {
    resolve = res
  })
  return { promise, resolve }
}

const page = (name: string, hasMore = false, nextCursor: string | null = null) => ({
  items: [
    {
      id: name,
      displayName: name,
      updatedAt: '2026-10-02T00:00:00Z',
    },
  ],
  hasMore,
  nextCursor,
})

function Harness({
  companyId,
  epoch,
}: {
  companyId: string
  epoch: number
}) {
  const contacts = useContacts(companyId, epoch, { sort: 'UPDATED_DESC', size: 50 })
  return (
    <div>
      {contacts.items.map((item) => (
        <span key={item.id}>{item.displayName}</span>
      ))}
      <button type="button" onClick={() => void contacts.refresh()}>
        Refresh
      </button>
      <button type="button" onClick={() => void contacts.loadMore()}>
        Load more
      </button>
    </div>
  )
}

describe('useContacts workspace epoch ownership', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('does not let a delayed A1 load repopulate B or A2', async () => {
    const oldA = deferred<ReturnType<typeof page>>()
    let aCalls = 0
    vi.mocked(contactsApi.list).mockImplementation((companyId) => {
      if (companyId === 'company-a' && ++aCalls === 1) return oldA.promise
      return Promise.resolve(page(companyId === 'company-b' ? 'B Contact' : 'A2 Contact'))
    })

    const view = render(<Harness companyId="company-a" epoch={1} />)
    view.rerender(<Harness companyId="company-b" epoch={2} />)
    expect(await screen.findByText('B Contact')).toBeInTheDocument()

    view.rerender(<Harness companyId="company-a" epoch={3} />)
    expect(await screen.findByText('A2 Contact')).toBeInTheDocument()

    await act(async () => {
      oldA.resolve(page('A1 stale Contact'))
      await oldA.promise
    })

    expect(screen.queryByText('A1 stale Contact')).not.toBeInTheDocument()
    expect(screen.getByText('A2 Contact')).toBeInTheDocument()
  })

  it('guards refresh results with the same workspace epoch', async () => {
    const staleRefresh = deferred<ReturnType<typeof page>>()
    let calls = 0
    vi.mocked(contactsApi.list).mockImplementation((companyId) => {
      calls += 1
      if (calls === 1) return Promise.resolve(page('A1 current Contact'))
      if (calls === 2) return staleRefresh.promise
      return Promise.resolve(page(companyId === 'company-b' ? 'B Contact' : 'A2 Contact'))
    })

    const view = render(<Harness companyId="company-a" epoch={1} />)
    expect(await screen.findByText('A1 current Contact')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    await waitFor(() => expect(contactsApi.list).toHaveBeenCalledTimes(2))

    view.rerender(<Harness companyId="company-b" epoch={2} />)
    expect(await screen.findByText('B Contact')).toBeInTheDocument()

    await act(async () => {
      staleRefresh.resolve(page('A1 stale Refresh'))
      await staleRefresh.promise
    })

    expect(screen.queryByText('A1 stale Refresh')).not.toBeInTheDocument()
    expect(screen.getByText('B Contact')).toBeInTheDocument()
  })

  it('guards load-more results with the same workspace epoch', async () => {
    const staleMore = deferred<ReturnType<typeof page>>()
    let calls = 0
    vi.mocked(contactsApi.list).mockImplementation((companyId, _query, cursor) => {
      calls += 1
      if (companyId === 'company-a' && !cursor && calls === 1) {
        return Promise.resolve(page('A1 current Contact', true, 'cursor-a1'))
      }
      if (companyId === 'company-a' && cursor === 'cursor-a1') return staleMore.promise
      return Promise.resolve(page(companyId === 'company-b' ? 'B Contact' : 'A2 Contact'))
    })

    const view = render(<Harness companyId="company-a" epoch={1} />)
    expect(await screen.findByText('A1 current Contact')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Load more' }))
    await waitFor(() => expect(contactsApi.list).toHaveBeenCalledTimes(2))

    view.rerender(<Harness companyId="company-a" epoch={3} />)
    expect(await screen.findByText('A2 Contact')).toBeInTheDocument()

    await act(async () => {
      staleMore.resolve(page('A1 stale More'))
      await staleMore.promise
    })

    expect(screen.queryByText('A1 stale More')).not.toBeInTheDocument()
    expect(screen.getByText('A2 Contact')).toBeInTheDocument()
  })
})
