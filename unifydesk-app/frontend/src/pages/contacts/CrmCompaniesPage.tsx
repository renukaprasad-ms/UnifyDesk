import { useCallback, useEffect, useState } from 'react'
import { crmWorkApi, type CrmAccount } from '../../features/contacts/crmWorkApi'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId, selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'

export default function CrmCompaniesPage() {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const epoch = useAppSelector(selectWorkspaceEpoch)
  const { capture, isCurrent } = useWorkspaceEpochScope(companyId, epoch)
  const { can } = useIam()
  const [items, setItems] = useState<CrmAccount[]>([])
  const [next, setNext] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    if (!companyId) return
    const token = capture()
    setLoading(true)
    try {
      const page = await crmWorkApi.accounts(companyId)
      if (isCurrent(token)) { setItems(page.items); setNext(page.nextCursor); setError('') }
    } catch (err) {
      if (isCurrent(token)) setError(apiErrorMessage(err, 'Unable to load customer companies'))
    } finally { if (isCurrent(token)) setLoading(false) }
  }, [companyId, capture, isCurrent])

  useEffect(() => { setItems([]); setNext(null); void load() }, [load, epoch])

  const add = async () => {
    if (!companyId || !name.trim() || busy) return
    const token = capture()
    setBusy(true)
    try {
      await crmWorkApi.addAccount(companyId, name.trim())
      if (isCurrent(token)) { setName(''); await load() }
    } catch (err) {
      if (isCurrent(token)) setError(apiErrorMessage(err, 'Unable to create company'))
    } finally { if (isCurrent(token)) setBusy(false) }
  }

  const more = async () => {
    if (!companyId || !next || busy) return
    const token = capture()
    setBusy(true)
    try {
      const page = await crmWorkApi.accounts(companyId, next)
      if (isCurrent(token)) { setItems((old) => [...old, ...page.items]); setNext(page.nextCursor) }
    } catch (err) { if (isCurrent(token)) setError(apiErrorMessage(err, 'Unable to load more companies')) }
    finally { if (isCurrent(token)) setBusy(false) }
  }

  return (
    <section className="w-full space-y-5 px-4 py-6 sm:px-6">
      <header><p className="eyebrow">CRM</p><h1 className="mt-2 text-3xl font-bold">Companies</h1>
      <p className="mt-2 text-sm text-slate-500">Customer organizations, separate from your UnifyDesk workspace.</p></header>
      {error && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{error}</p>}
      {can(PERMISSIONS.CRM_ACCOUNT_MODIFY) && (
        <form className="flex flex-wrap gap-2" onSubmit={(event) => { event.preventDefault(); void add() }}>
          <label htmlFor="crm-company-name" className="sr-only">Customer company name</label>
          <input id="crm-company-name" className="form-input min-w-64 flex-1" placeholder="Customer company name"
            maxLength={240} value={name} onChange={(event) => setName(event.target.value)} />
          <button className="primary-button" type="submit" disabled={busy || !name.trim()}>Add company</button>
        </form>
      )}
      {loading ? <p role="status">Loading companies…</p> : items.length === 0 ? (
        <div className="rounded-xl border border-dashed p-8 text-center text-sm text-slate-500">No customer companies yet.</div>
      ) : (
        <div className="overflow-hidden rounded-xl border bg-white">
          {items.map((item) => <div key={item.id} className="border-b border-slate-100 px-4 py-3">
            <p className="font-semibold text-slate-900">{item.name}</p>
            {item.website && <p className="text-xs text-slate-500">{item.website}</p>}
          </div>)}
        </div>
      )}
      {next && <button type="button" className="secondary-button" disabled={busy} onClick={() => void more()}>Load more</button>}
    </section>
  )
}
