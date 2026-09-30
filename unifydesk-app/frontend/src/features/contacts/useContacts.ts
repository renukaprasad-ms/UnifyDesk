import { useEffect, useRef, useState } from 'react'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { appendCursorPage, normalizeContactQuery } from './contactModel'
import { contactsApi } from './contactsApi'
import type { ContactQuery, ContactSummary } from './types'

export const useContacts = (companyId: string | null, query: ContactQuery) => {
  const [items, setItems] = useState<ContactSummary[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState('')
  const requestRef = useRef(0)
  const normalized = normalizeContactQuery(query)
  const queryKey = JSON.stringify(normalized)

  useEffect(() => {
    if (!companyId) {
      setItems([])
      setNextCursor(null)
      setHasMore(false)
      return
    }
    const requestId = ++requestRef.current
    setLoading(true)
    setError('')
    contactsApi
      .list(companyId, normalized)
      .then((page) => {
        if (requestRef.current !== requestId) return
        setItems(page.items)
        setNextCursor(page.nextCursor)
        setHasMore(page.hasMore)
      })
      .catch((loadError) => {
        if (requestRef.current === requestId) {
          setError(apiErrorMessage(loadError, 'Unable to load contacts.'))
        }
      })
      .finally(() => {
        if (requestRef.current === requestId) setLoading(false)
      })
  }, [companyId, queryKey])

  const loadMore = async () => {
    if (!companyId || !hasMore || !nextCursor || loadingMore) return
    setLoadingMore(true)
    setError('')
    try {
      const page = await contactsApi.list(companyId, normalized, nextCursor)
      setItems((current) => appendCursorPage(current, page, false))
      setNextCursor(page.nextCursor)
      setHasMore(page.hasMore)
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to load more contacts.'))
    } finally {
      setLoadingMore(false)
    }
  }

  const refresh = async () => {
    if (!companyId) return
    setLoading(true)
    setError('')
    try {
      const page = await contactsApi.list(companyId, normalized)
      setItems(page.items)
      setNextCursor(page.nextCursor)
      setHasMore(page.hasMore)
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Unable to refresh contacts.'))
    } finally {
      setLoading(false)
    }
  }

  return { items, hasMore, loading, loadingMore, error, loadMore, refresh }
}
