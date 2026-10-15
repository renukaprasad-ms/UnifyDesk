import { ListSkeleton } from '../ui/Skeleton'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { crmWorkApi, type Contact360, type CrmAccount } from '../../features/contacts/crmWorkApi'
import {
  crmLeadsApi,
  type LeadHistoryEvent,
  type LeadStage,
} from '../../features/contacts/crmLeadsApi'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId, selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'

export default function ContactCrmPanel({ contactId }: { contactId: string }) {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const epoch = useAppSelector(selectWorkspaceEpoch)
  const { capture, isCurrent } = useWorkspaceEpochScope(companyId, epoch)
  const { can, canAll } = useIam()
  const canRead360 = canAll([
    PERMISSIONS.CRM_LEAD_READ,
    PERMISSIONS.CRM_ACCOUNT_READ,
    PERMISSIONS.CRM_TASK_READ,
    PERMISSIONS.CONTACT_READ,
  ])
  const [details, setDetails] = useState<Contact360 | null>(null)
  const [options, setOptions] = useState<CrmAccount[]>([])
  const [history, setHistory] = useState<LeadHistoryEvent[]>([])
  const [accountId, setAccountId] = useState('')
  const [taskTitle, setTaskTitle] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [generation, setGeneration] = useState(0)
  const requestId = useRef(0)

  const reload = useCallback(async () => {
    if (!companyId || !contactId || !canRead360) {
      setLoading(false)
      return
    }
    const scope = capture()
    const requestGeneration = ++requestId.current
    setLoading(true)
    try {
      const overview = await crmWorkApi.contact360(companyId, contactId)
      if (!isCurrent(scope) || requestGeneration !== requestId.current) return
      setDetails(overview)
      setError('')
    } catch (err) {
      if (isCurrent(scope) && requestGeneration === requestId.current)
        setError(apiErrorMessage(err, 'Unable to load CRM information'))
    } finally {
      if (isCurrent(scope) && requestGeneration === requestId.current) setLoading(false)
    }
  }, [companyId, contactId, capture, isCurrent, generation, canRead360])

  useEffect(() => {
    setDetails(null)
    setOptions([])
    setHistory([])
    setAccountId('')
    setTaskTitle('')
    setGeneration((value) => value + 1)
  }, [companyId, contactId, epoch])

  useEffect(() => {
    void reload()
  }, [reload])

  useEffect(() => {
    if (!companyId || !contactId || !details?.lead || !can(PERMISSIONS.CRM_LEAD_READ)) {
      return
    }
    const scope = capture()
    let active = true
    void crmLeadsApi
      .history(companyId, contactId)
      .then((events) => {
        if (active && isCurrent(scope)) setHistory(events)
      })
      .catch(() => {
        if (active && isCurrent(scope)) setHistory([])
      })
    return () => {
      active = false
    }
  }, [
    companyId,
    contactId,
    details?.lead?.stage,
    details?.lead?.version,
    epoch,
    can,
    capture,
    isCurrent,
  ])

  const act = async (action: () => Promise<unknown>) => {
    if (!companyId || busy) return
    const scope = capture()
    setBusy(true)
    setError('')
    try {
      await action()
      if (isCurrent(scope)) await reload()
    } catch (err) {
      if (isCurrent(scope)) setError(apiErrorMessage(err, 'CRM update failed'))
    } finally {
      if (isCurrent(scope)) setBusy(false)
    }
  }

  const loadAccounts = async () => {
    if (!companyId) return
    const scope = capture()
    try {
      const page = await crmWorkApi.accounts(companyId)
      if (isCurrent(scope))
        setOptions(
          page.items.filter(
            (account) => !details?.accounts.some((linked) => linked.id === account.id),
          ),
        )
    } catch (err) {
      if (isCurrent(scope)) setError(apiErrorMessage(err, 'Unable to load companies'))
    }
  }

  if (!canRead360) return null

  return (
    <section className="panel p-5 lg:col-span-2" aria-label="CRM relationship summary">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 className="text-lg font-bold text-slate-950">CRM · Customer 360</h2>
          <p className="mt-1 text-sm text-slate-500">
            Sales tracking is separate from marketing consent and delivery restrictions.
          </p>
        </div>
        <Link className="secondary-button" to="/crm/overview">
          Open CRM
        </Link>
      </div>
      {error && (
        <p role="alert" className="mt-3 text-sm text-rose-700">
          {error}
        </p>
      )}
      {loading && <ListSkeleton rows={3} />}
      {details && !loading && (
        <div className="mt-5 grid gap-5 lg:grid-cols-3">
          {can(PERMISSIONS.CRM_LEAD_READ) && (
            <div className="rounded-xl border border-slate-200 p-4">
              <h3 className="font-semibold">Lead stage</h3>
              {details.lead ? (
                can(PERMISSIONS.CRM_LEAD_MODIFY) ? (
                  <select
                    className="form-input mt-2 w-full"
                    aria-label="Lead stage"
                    disabled={busy}
                    value={details.lead.stage}
                    onChange={(event) =>
                      void act(() =>
                        crmLeadsApi.moveStage(
                          companyId!,
                          contactId,
                          details.lead!.version,
                          event.target.value as LeadStage,
                        ),
                      )
                    }
                  >
                    {(
                      ['NEW', 'CONTACTED', 'QUALIFIED', 'UNQUALIFIED', 'CONVERTED'] as LeadStage[]
                    ).map((stage) => (
                      <option key={stage} value={stage}>
                        {stage.replaceAll('_', ' ')}
                      </option>
                    ))}
                  </select>
                ) : (
                  <p className="mt-2 text-sm">{details.lead.stage.replaceAll('_', ' ')}</p>
                )
              ) : (
                <p className="mt-2 text-sm">Not a lead</p>
              )}
              {!details.lead && can(PERMISSIONS.CRM_LEAD_MODIFY) && (
                <button
                  type="button"
                  className="secondary-button mt-3"
                  disabled={busy}
                  onClick={() => void act(() => crmLeadsApi.enroll(companyId!, contactId))}
                >
                  Mark as lead
                </button>
              )}
              {history.length > 0 && (
                <div className="mt-4 border-t border-slate-200 pt-3">
                  <h4 className="text-sm font-semibold">Stage history</h4>
                  <ol className="mt-2 space-y-2">
                    {history.slice(0, 5).map((event) => (
                      <li key={event.id} className="text-xs text-slate-600">
                        <span className="font-medium">{event.newStage.replaceAll('_', ' ')}</span>
                        <span className="ml-2">{new Date(event.occurredAt).toLocaleString()}</span>
                      </li>
                    ))}
                  </ol>
                </div>
              )}
              <Link className="mt-3 block text-sm font-semibold text-indigo-600" to="/crm/leads">
                View leads
              </Link>
            </div>
          )}
          {can(PERMISSIONS.CRM_ACCOUNT_READ) && (
            <div className="rounded-xl border border-slate-200 p-4">
              <h3 className="font-semibold">Customer companies</h3>
              <div className="mt-2 space-y-2">
                {details.accounts.map((account) => (
                  <div key={account.id} className="flex items-center justify-between gap-2 text-sm">
                    <span>{account.name}</span>
                    {can(PERMISSIONS.CRM_ACCOUNT_MODIFY) && (
                      <button
                        type="button"
                        className="text-rose-600"
                        disabled={busy}
                        onClick={() =>
                          void act(() =>
                            crmWorkApi.unlinkContact(companyId!, account.id, contactId),
                          )
                        }
                      >
                        Unlink
                      </button>
                    )}
                  </div>
                ))}
              </div>
              {details.accounts.length === 0 && (
                <p className="mt-2 text-sm text-slate-500">No companies linked.</p>
              )}
              {details.moreAccounts && (
                <p className="mt-2 text-xs text-slate-500">Showing the newest 20 links.</p>
              )}
              {can(PERMISSIONS.CRM_ACCOUNT_MODIFY) && (
                <div className="mt-3 flex flex-wrap gap-2">
                  <button
                    type="button"
                    className="secondary-button"
                    onClick={() => void loadAccounts()}
                    disabled={busy}
                  >
                    Choose company
                  </button>
                  {options.length > 0 && (
                    <>
                      <select
                        aria-label="Company to link"
                        className="form-input min-w-0 flex-1"
                        value={accountId}
                        onChange={(event) => setAccountId(event.target.value)}
                      >
                        <option value="">Select company</option>
                        {options.map((account) => (
                          <option key={account.id} value={account.id}>
                            {account.name}
                          </option>
                        ))}
                      </select>
                      <button
                        type="button"
                        className="primary-button"
                        disabled={!accountId || busy}
                        onClick={() =>
                          void act(async () => {
                            await crmWorkApi.linkContact(companyId!, accountId, contactId)
                            setAccountId('')
                            setOptions([])
                          })
                        }
                      >
                        Link
                      </button>
                    </>
                  )}
                </div>
              )}
            </div>
          )}
          {can(PERMISSIONS.CRM_TASK_READ) && (
            <div className="rounded-xl border border-slate-200 p-4">
              <h3 className="font-semibold">Follow-up tasks</h3>
              <ul className="mt-2 space-y-2">
                {details.tasks.map((task) => (
                  <li key={task.id} className="text-sm">
                    <span className="font-medium">{task.title}</span>
                    <span className="ml-2 text-xs text-slate-500">
                      {task.status.replaceAll('_', ' ')}
                    </span>
                  </li>
                ))}
              </ul>
              {details.tasks.length === 0 && (
                <p className="mt-2 text-sm text-slate-500">No linked tasks.</p>
              )}
              {details.moreTasks && (
                <p className="mt-2 text-xs text-slate-500">Showing the latest 20 tasks.</p>
              )}
              {can(PERMISSIONS.CRM_TASK_MODIFY) && (
                <form
                  className="mt-3 flex gap-2"
                  onSubmit={(event) => {
                    event.preventDefault()
                    const title = taskTitle.trim()
                    if (!title) return
                    void act(async () => {
                      await crmWorkApi.addContactTask(companyId!, contactId, title)
                      setTaskTitle('')
                    })
                  }}
                >
                  <label htmlFor="crm-contact-task" className="sr-only">
                    Follow-up title
                  </label>
                  <input
                    id="crm-contact-task"
                    className="form-input min-w-0 flex-1"
                    placeholder="Add follow-up"
                    maxLength={240}
                    value={taskTitle}
                    onChange={(event) => setTaskTitle(event.target.value)}
                  />
                  <button
                    className="primary-button"
                    type="submit"
                    disabled={busy || !taskTitle.trim()}
                  >
                    Add
                  </button>
                </form>
              )}
              <Link className="mt-3 block text-sm font-semibold text-indigo-600" to="/crm/tasks">
                View tasks
              </Link>
            </div>
          )}
        </div>
      )}
    </section>
  )
}
