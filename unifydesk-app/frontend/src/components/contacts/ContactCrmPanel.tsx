import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { crmWorkApi, type Contact360, type CrmAccount } from '../../features/contacts/crmWorkApi'
import { crmLeadsApi } from '../../features/contacts/crmLeadsApi'
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
  const { can } = useIam()
  const [details, setDetails] = useState<Contact360 | null>(null)
  const [options, setOptions] = useState<CrmAccount[]>([])
  const [accountId, setAccountId] = useState('')
  const [taskTitle, setTaskTitle] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [generation, setGeneration] = useState(0)

  const reload = useCallback(async () => {
    if (!companyId || !contactId) return
    const scope = capture()
    const requestGeneration = generation
    setLoading(true)
    try {
      const overview = await crmWorkApi.contact360(companyId, contactId)
      if (!isCurrent(scope) || requestGeneration !== generation) return
      setDetails(overview)
      setError('')
    } catch (err) {
      if (isCurrent(scope)) setError(apiErrorMessage(err, 'Unable to load CRM information'))
    } finally {
      if (isCurrent(scope)) setLoading(false)
    }
  }, [companyId, contactId, capture, isCurrent, generation])

  useEffect(() => {
    setDetails(null)
    setOptions([])
    setAccountId('')
    setTaskTitle('')
    setGeneration((value) => value + 1)
  }, [companyId, contactId, epoch])

  useEffect(() => { void reload() }, [reload])

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
      if (isCurrent(scope)) setOptions(page.items.filter(
        (account) => !details?.accounts.some((linked) => linked.id === account.id),
      ))
    } catch (err) {
      if (isCurrent(scope)) setError(apiErrorMessage(err, 'Unable to load companies'))
    }
  }

  return <section className="panel p-5 lg:col-span-2" aria-label="CRM relationship summary">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <div><h2 className="text-lg font-bold text-slate-950">CRM · Customer 360</h2>
        <p className="mt-1 text-sm text-slate-500">Sales tracking is separate from marketing consent and delivery restrictions.</p>
      </div>
      <Link className="secondary-button" to="/crm/overview">Open CRM</Link>
    </div>
    {error && <p role="alert" className="mt-3 text-sm text-rose-700">{error}</p>}
    {loading && <p role="status" className="mt-4 text-sm text-slate-500">Loading CRM relationships…</p>}
    {details && !loading && <div className="mt-5 grid gap-5 lg:grid-cols-3">
      <div className="rounded-xl border border-slate-200 p-4">
        <h3 className="font-semibold">Lead stage</h3>
        <p className="mt-2 text-sm">{details.lead ? details.lead.stage.replaceAll('_', ' ') : 'Not a lead'}</p>
        {!details.lead && can(PERMISSIONS.CONTACT_MODIFY) && (
          <button type="button" className="secondary-button mt-3" disabled={busy}
            onClick={() => void act(() => crmLeadsApi.enroll(companyId!, contactId))}>Mark as lead</button>
        )}
        <Link className="mt-3 block text-sm font-semibold text-indigo-600" to="/crm/leads">View leads</Link>
      </div>
      <div className="rounded-xl border border-slate-200 p-4">
        <h3 className="font-semibold">Customer companies</h3>
        <div className="mt-2 space-y-2">{details.accounts.map((account) => <div
          key={account.id} className="flex items-center justify-between gap-2 text-sm">
          <span>{account.name}</span>
          {can(PERMISSIONS.CONTACT_MODIFY) && <button type="button" className="text-rose-600"
            disabled={busy} onClick={() => void act(() => crmWorkApi.unlinkContact(companyId!, account.id, contactId))}>Unlink</button>}
        </div>)}</div>
        {details.accounts.length === 0 && <p className="mt-2 text-sm text-slate-500">No companies linked.</p>}
        {details.moreAccounts && <p className="mt-2 text-xs text-slate-500">Showing the newest 20 links.</p>}
        {can(PERMISSIONS.CONTACT_MODIFY) && <div className="mt-3 flex flex-wrap gap-2">
          <button type="button" className="secondary-button" onClick={() => void loadAccounts()} disabled={busy}>Choose company</button>
          {options.length > 0 && <><select aria-label="Company to link" className="form-input min-w-0 flex-1"
            value={accountId} onChange={(event) => setAccountId(event.target.value)}>
            <option value="">Select company</option>
            {options.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}
          </select><button type="button" className="primary-button" disabled={!accountId || busy}
            onClick={() => void act(async () => { await crmWorkApi.linkContact(companyId!, accountId, contactId); setAccountId(''); setOptions([]) })}>Link</button></>}
        </div>}
      </div>
      <div className="rounded-xl border border-slate-200 p-4">
        <h3 className="font-semibold">Follow-up tasks</h3>
        <ul className="mt-2 space-y-2">{details.tasks.map((task) =>
          <li key={task.id} className="text-sm"><span className="font-medium">{task.title}</span>
            <span className="ml-2 text-xs text-slate-500">{task.status.replaceAll('_', ' ')}</span></li>)}</ul>
        {details.tasks.length === 0 && <p className="mt-2 text-sm text-slate-500">No linked tasks.</p>}
        {details.moreTasks && <p className="mt-2 text-xs text-slate-500">Showing the latest 20 tasks.</p>}
        {can(PERMISSIONS.CONTACT_MODIFY) && <form className="mt-3 flex gap-2" onSubmit={(event) => {
          event.preventDefault()
          const title = taskTitle.trim()
          if (!title) return
          void act(async () => { await crmWorkApi.addContactTask(companyId!, contactId, title); setTaskTitle('') })
        }}>
          <label htmlFor="crm-contact-task" className="sr-only">Follow-up title</label>
          <input id="crm-contact-task" className="form-input min-w-0 flex-1" placeholder="Add follow-up"
            maxLength={240} value={taskTitle} onChange={(event) => setTaskTitle(event.target.value)} />
          <button className="primary-button" type="submit" disabled={busy || !taskTitle.trim()}>Add</button>
        </form>}
        <Link className="mt-3 block text-sm font-semibold text-indigo-600" to="/crm/tasks">View tasks</Link>
      </div>
    </div>}
  </section>
}
