import { useEffect, useMemo, useRef, useState } from 'react'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useWorkspaceEpochScope } from '../workspace/useWorkspaceEpochScope'
import { appendCursorPage, normalizeContactQuery } from './contactModel'
import { contactsApi } from './contactsApi'
import type { ContactQuery, ContactSummary } from './types'

export const useContacts = (companyId: string | null, epoch: number, query: ContactQuery) => {
  const { capture: captureScope, isCurrent: scopeIsCurrent } = useWorkspaceEpochScope(
    companyId,
    epoch,
  )
  const [items, setItems] = useState<ContactSummary[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState('')
  const requestRef = useRef(0)
  const normalized = useMemo(
    () => normalizeContactQuery(query),
    [
      query.q,
      query.status,
      query.tagId,
      query.listId,
      query.createdAfter,
      query.createdBefore,
      query.sort,
      query.size,
    ],
  )
  const queryKey = JSON.stringify(normalized)

  useEffect(() => {
    if (!companyId) {
      setItems([])
      setNextCursor(null)
      setHasMore(false)
      return
    }
    const requestId = ++requestRef.current
    const scope = captureScope()
    setItems([])
    setNextCursor(null)
    setHasMore(false)
    setLoading(true)
    setError('')
    contactsApi
      .list(companyId, normalized)
      .then((page) => {
        if (requestRef.current !== requestId || !scopeIsCurrent(scope)) return
        setItems(page.items)
        setNextCursor(page.nextCursor)
        setHasMore(page.hasMore)
      })
      .catch((loadError) => {
        if (requestRef.current === requestId && scopeIsCurrent(scope)) {
          setError(apiErrorMessage(loadError, 'Unable to load contacts.'))
        }
      })
      .finally(() => {
        if (requestRef.current === requestId && scopeIsCurrent(scope)) setLoading(false)
      })
  }, [captureScope, companyId, epoch, queryKey, normalized, scopeIsCurrent])

  const loadMore = async () => {
    if (!companyId || !hasMore || !nextCursor || loadingMore) return
    const scope = captureScope()
    if (!scopeIsCurrent(scope)) return
    setLoadingMore(true)
    setError('')
    try {
      const page = await contactsApi.list(companyId, normalized, nextCursor)
      if (!scopeIsCurrent(scope)) return
      setItems((current) => appendCursorPage(current, page, false))
      setNextCursor(page.nextCursor)
      setHasMore(page.hasMore)
    } catch (loadError) {
      if (scopeIsCurrent(scope)) {
        setError(apiErrorMessage(loadError, 'Unable to load more contacts.'))
      }
    } finally {
      if (scopeIsCurrent(scope)) setLoadingMore(false)
    }
  }

  const refresh = async () => {
    if (!companyId) return
    const scope = captureScope()
    if (!scopeIsCurrent(scope)) return
    setLoading(true)
    setError('')
    try {
      const page = await contactsApi.list(companyId, normalized)
      if (!scopeIsCurrent(scope)) return
      setItems(page.items)
      setNextCursor(page.nextCursor)
      setHasMore(page.hasMore)
    } catch (loadError) {
      if (scopeIsCurrent(scope)) {
        setError(apiErrorMessage(loadError, 'Unable to refresh contacts.'))
      }
    } finally {
      if (scopeIsCurrent(scope)) setLoading(false)
    }
  }

  return { items, hasMore, loading, loadingMore, error, loadMore, refresh }
}
