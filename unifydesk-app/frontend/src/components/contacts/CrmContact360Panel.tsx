import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { crmWorkApi, type Contact360, type CrmTask } from '../../features/contacts/crmWorkApi'
import { crmLeadsApi, type LeadStage } from '../../features/contacts/crmLeadsApi'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'

const stages: LeadStage[] = ['NEW', 'CONTACTED', 'QUALIFIED', 'UNQUALIFIED', 'CONVERTED']
const taskStatuses: CrmTask['status'][] = ['OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED']
const pretty = (value: string) => value.replaceAll('_', ' ').toLowerCase().replace(/^./, (s) => s.toUpperCase())

export default function CrmContact360Panel({ companyId, contactId }: { companyId: string; contactId: string }) {
  const epoch = useAppSelector(selectWorkspaceEpoch)
  const { can } = useIam()
  const [overview, setOverview] = useState<Contact360 | null>(null)
  const [availableAccounts, setAvailableAccounts] = useState<Array<{ id: string; name: string }>>([])
  const [chosenAccount, setChosenAccount] = useState('')
  const [taskTitle, setTaskTitle] = useState('')
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    let active = true
    setOverview(null)
    setAvailableAccounts([])
    setChosenAccount('')
    setError('')
    setLoading(true)
    void crmWorkApi.contact360(companyId, contactId).then((data) => {
      if (active) setOverview(data)
    }).catch((err) => {
      if (active) setError(apiErrorMessage(err, 'Unable to load CRM relationships'))
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [companyId, contactId, epoch, revision])

  const execute = async (action: () => Promise<unknown>) => {
    if (busy) return
    setBusy(true)
    setError('')
    try {
      await action()
      setRevision((value) => value + 1)
    } catch (err) {
      setError(apiErrorMessage(err, 'Unable to update CRM relationships'))
      setRevision((value) => value + 1)
    } finally { setBusy(false) }
  }

  const findAccounts = async () => {
    try {
      const page = await crmWorkApi.accounts(companyId)
      setAvailableAccounts(page.items)
    } catch (err) {
      setError(apiErrorMessage(err, 'Unable to load customer companies'))
    }
  }

  return (
    <section className="panel p-5 lg:col-span-2" aria-label="CRM contact 360">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold text-slate-950">CRM · Customer 360</h2>
          <p className="mt-1 text-sm text-slate-500">Sales relationship information. Marketing permission remains separate.</p>
        </div>
        <Link className="secondary-button" to="/crm/overview">Open CRM</Link>
      </div>
      {error && <p role="alert" className="mt-3 text-sm text-rose-700">{error}</p>}
      {loading ? <p role="status" className="mt-4 text-sm text-slate-500">Loading CRM details…</p> : overview && (
        <div className="mt-5 grid gap-4 lg:grid-cols-2">
          <div className="space-y-3 rounded-xl border border-slate-200 p-4">
            <h3 className="font-bold">Lead lifecycle</h3>
            {overview.lead ? <>
              <p className="text-sm text-slate-500">Current stage: {pretty(overview.lead.stage)}</p>
              {can(PERMISSIONS.CONTACT_MODIFY) && <select aria-label="Lead stage" className="form-input w-full"
                value={overview.lead.stage} disabled={busy}
                onChange={(event) => void execute(() => crmLeadsApi.move(companyId,
                  { contactId, stage: overview.lead!.stage as LeadStage, version: overview.lead!.version,
                    displayName: null, organization: null, ownerUserId: null, createdAt: '', updatedAt: '' },
                  event.target.value as LeadStage))}>
                {stages.map((stage) => <option key={stage} value={stage}>{pretty(stage)}</option>)}
              </select>}
            </> : <>
              <p className="text-sm text-slate-500">Not tracked as a lead.</p>
              {can(PERMISSIONS.CONTACT_MODIFY) && <button type="button" className="secondary-button" disabled={busy}
                onClick={() => void execute(() => crmLeadsApi.enroll(companyId, contactId))}>Track as lead</button>}
            </>}
          </div>
          <div className="space-y-3 rounded-xl border border-slate-200 p-4">
            <h3 className="font-bold">Customer companies</h3>
            {overview.accounts.length ? overview.accounts.map((account) => (
              <div className="flex items-center justify-between gap-2" key={account.id}>
                <span className="text-sm font-medium">{account.name}</span>
                {can(PERMISSIONS.CONTACT_MODIFY) && <button type="button" className="text-xs text-rose-600"
                  disabled={busy} onClick={() => void execute(() => crmWorkApi.unlinkContact(companyId, account.id, contactId))}>Unlink</button>}
              </div>
            )) : <p className="text-sm text-slate-500">No linked customer company.</p>}
            {overview.moreAccounts && <p className="text-xs text-slate-500">More companies exist.</p>}
            {can(PERMISSIONS.CONTACT_MODIFY) && <>
              {!availableAccounts.length && <button className="secondary-button" type="button" disabled={busy}
                onClick={() => void findAccounts()}>Link a company</button>}
              {availableAccounts.length > 0 && <div className="flex flex-wrap gap-2">
                <select className="form-input min-w-32 flex-1" aria-label="Choose customer company" value={chosenAccount}
                  onChange={(event) => setChosenAccount(event.target.value)}>
                  <option value="">Select company</option>
                  {availableAccounts.filter((account) => !overview.accounts.some((item) => item.id === account.id))
                    .map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}
                </select>
                <button className="primary-button" type="button" disabled={busy || !chosenAccount}
                  onClick={() => void execute(() => crmWorkApi.linkContact(companyId, chosenAccount, contactId))}>Link</button>
              </div>}
            </>}
          </div>
          <div className="space-y-3 rounded-xl border border-slate-200 p-4 lg:col-span-2">
            <h3 className="font-bold">Follow-up tasks</h3>
            {overview.tasks.length ? overview.tasks.map((task) => (
              <div key={task.id} className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 py-2">
                <div><p className="text-sm font-semibold">{task.title}</p>
                  <p className="text-xs text-slate-500">{task.dueAt ? 'Due ' + new Date(task.dueAt).toLocaleDateString() : 'No due date'}</p>
                </div>
                {can(PERMISSIONS.CONTACT_MODIFY) ? <select className="form-input" aria-label={'Status for ' + task.title}
                  value={task.status} disabled={busy}
                  onChange={(event) => void execute(() => crmWorkApi.taskStatus(companyId, task, event.target.value as CrmTask['status']))}>
                  {taskStatuses.map((status) => <option value={status} key={status}>{pretty(status)}</option>)}
                </select> : <span className="text-sm">{pretty(task.status)}</span>}
              </div>
            )) : <p className="text-sm text-slate-500">No follow-up tasks.</p>}
            {overview.moreTasks && <Link className="text-sm text-indigo-600" to="/crm/tasks">View more in Tasks</Link>}
            {can(PERMISSIONS.CONTACT_MODIFY) && <form className="flex flex-wrap gap-2"
              onSubmit={(event) => { event.preventDefault(); if (taskTitle.trim()) void execute(async () => {
                await crmWorkApi.addContactTask(companyId, contactId, taskTitle.trim()); setTaskTitle('')
              }) }}>
              <label htmlFor="crm-contact-followup" className="sr-only">Follow-up title</label>
              <input id="crm-contact-followup" className="form-input min-w-40 flex-1" maxLength={240}
                placeholder="New follow-up task" value={taskTitle} onChange={(event) => setTaskTitle(event.target.value)} />
              <button type="submit" className="primary-button" disabled={busy || !taskTitle.trim()}>Add follow-up</button>
            </form>}
          </div>
        </div>
      )}
    </section>
  )
}
