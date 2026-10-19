import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import ContactsNav from '../../components/contacts/ContactsNav'
import { contactsApi } from '../../features/contacts/contactsApi'
import type { SegmentDefinition } from '../../features/contacts/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId, selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { ListSkeleton } from '../../components/ui/Skeleton'

export default function SegmentsPage() {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const workspaceEpoch = useAppSelector(selectWorkspaceEpoch)
  const { capture: captureScope, isCurrent: scopeIsCurrent } = useWorkspaceEpochScope(
    companyId,
    workspaceEpoch,
  )
  const { can } = useIam()
  const [items, setItems] = useState<SegmentDefinition[]>([])
  const [loading, setLoading] = useState(true)
  const [, setError] = useSnackbarState('', 'error')

  useEffect(() => {
    if (!companyId) {
      setItems([])
      setLoading(false)
      return
    }

    const scope = captureScope()
    setLoading(true)
    setError('')
    contactsApi
      .segments(companyId)
      .then((segments) => {
        if (!scopeIsCurrent(scope)) return
        setItems(segments.filter((segment) => !segment.archived && segment.status !== 'ARCHIVED'))
      })
      .catch((loadError) => {
        if (scopeIsCurrent(scope)) {
          setError(apiErrorMessage(loadError, 'Unable to load Segments.'))
        }
      })
      .finally(() => {
        if (scopeIsCurrent(scope)) setLoading(false)
      })
  }, [captureScope, companyId, scopeIsCurrent, workspaceEpoch])

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="w-full">
        <ContactsNav />
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Audience</p>
            <h1 className="mt-2 text-3xl font-bold text-slate-950">Segments</h1>
            <p className="mt-2 text-sm text-slate-500">
              Build reusable audiences that stay up to date as your Contacts change.
            </p>
          </div>
          {can(PERMISSIONS.CONTACT_MODIFY) && (
            <Link className="primary-button" to="/contacts/segments/new">
              New Segment
            </Link>
          )}
        </header>



        {loading ? (
          <div className="mt-6">
            <ListSkeleton rows={6} />
          </div>
        ) : items.length === 0 ? (
          <div className="panel mt-6 p-10 text-center">
            <h2 className="font-bold text-slate-950">No Segments yet</h2>
            <p className="mt-2 text-sm text-slate-500">
              Build a dynamic audience from contact, identity, preference and suppression fields.
            </p>
          </div>
        ) : (
          <div className="mt-6 grid gap-4 md:grid-cols-2">
            {items.map((item) => (
              <Link
                key={item.id}
                to={'/contacts/segments/' + item.id}
                className="panel p-5 hover:border-indigo-200"
              >
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <h2 className="font-bold text-slate-950">{item.name}</h2>
                    <p className="mt-1 text-sm text-slate-500">
                      {item.description || 'Dynamic Contact Segment'}
                    </p>
                  </div>
                  <span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-bold text-slate-600">
                    {item.count == null ? 'Count not ready' : 'Ready'}
                  </span>
                </div>
                <p className="mt-4 text-2xl font-bold text-slate-950">
                  {item.count == null ? '—' : item.count.toLocaleString()}
                </p>
                <p className="text-xs text-slate-500">
                  {item.count == null
                    ? 'Open this Segment to calculate the audience size'
                    : `${item.count === 1 ? 'matching Contact' : 'matching Contacts'}`}
                </p>
              </Link>
            ))}
          </div>
        )}
      </div>
    </section>
  )
}
