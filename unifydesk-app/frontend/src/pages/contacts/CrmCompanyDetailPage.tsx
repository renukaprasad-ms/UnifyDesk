import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { companyApi } from '../../features/company/companyApi'
import { crmWorkApi, type CrmAccount, type AccountContact } from '../../features/contacts/crmWorkApi'
import { contactsApi } from '../../features/contacts/contactsApi'
import type { ContactSummary } from '../../features/contacts/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { useWorkspaceEpochScope } from '../../features/workspace/useWorkspaceEpochScope'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId, selectWorkspaceEpoch } from '../../store/workspace/workspaceSlice'

export default function CrmCompanyDetailPage() {
  const { accountId } = useParams<{ accountId: string }>()
  const companyId = useAppSelector(selectSelectedCompanyId)
  const epoch = useAppSelector(selectWorkspaceEpoch)
  const { capture, isCurrent } = useWorkspaceEpochScope(companyId, epoch)
  const { can } = useIam()
  const [account, setAccount] = useState<CrmAccount | null>(null)
  const [contacts, setContacts] = useState<AccountContact[]>([])
  const [name, setName] = useState('')
  const [website, setWebsite] = useState('')
  const [notes, setNotes] = useState('')
  const [owner, setOwner] = useState('')
  const [owners, setOwners] = useState<Array<{ userId: string; name: string }>>([])
  const [query, setQuery] = useState('')
  const [matches, setMatches] = useState<ContactSummary[]>([])
  const [chosen, setChosen] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [refreshToken, setRefreshToken] = useState(0)

  const load = useCallback(async () => {
    if (!companyId || !accountId) return
    const scope = capture()
    setLoading(true)
    try {
      const profile = await crmWorkApi.getAccount(companyId, accountId)
      if (!isCurrent(scope)) return
      setAccount(profile)
      setName(profile.name)
      setWebsite(profile.website ?? '')
      setNotes(profile.notes ?? '')
      setOwner(profile.ownerUserId ?? '')
      if (can(PERMISSIONS.CONTACT_READ)) {
        const related = await crmWorkApi.accountContactSummaries(companyId, accountId)
        if (isCurrent(scope)) setContacts(related)
      }
      setError('')
    } catch (err) {
      if (isCurrent(scope)) setError(apiErrorMessage(err, 'Unable to load company profile'))
    } finally {
      if (isCurrent(scope)) setLoading(false)
    }
  }, [companyId, accountId, capture, isCurrent, can])

  useEffect(() => {
    setAccount(null)
    setContacts([])
    setMatches([])
    setQuery('')
    setChosen('')
    void load()
  }, [load, epoch, refreshToken])

  useEffect(() => {
    if (!companyId || !can(PERMISSIONS.USER_READ)) return
    const scope = capture()
    let active = true
    void companyApi.users(companyId).then((users) => {
      if (active && isCurrent(scope)) {
        setOwners(users.filter((user) => user.status === 'ACTIVE')
          .map((user) => ({ userId: user.userId, name: user.name || user.email })))
      }
    }).catch(() => { /* Profile remains editable without member directory. */ })
    return () => { active = false }
  }, [companyId, epoch, can, capture, isCurrent])

  useEffect(() => {
    if (!companyId || !can(PERMISSIONS.CONTACT_READ)) return
    let active = true
    const scope = capture()
    const timer = setTimeout(() => {
      void contactsApi.list(companyId, { q: query.trim() || undefined, status: 'ACTIVE', size: 30 })
        .then((page) => { if (active && isCurrent(scope)) setMatches(page.items) })
        .catch((err) => {
          if (active && isCurrent(scope)) setError(apiErrorMessage(err, 'Contact search failed'))
        })
    }, 300)
    return () => { active = false; clearTimeout(timer) }
  }, [companyId, epoch, query, can, capture, isCurrent])

  const run = async (action: () => Promise<unknown>) => {
    if (busy) return
    const scope = capture()
    setBusy(true)
    setError('')
    try {
      await action()
      if (isCurrent(scope)) setRefreshToken((value) => value + 1)
    } catch (err) {
      if (isCurrent(scope)) {
        setError(apiErrorMessage(err, 'Company update failed. Refresh and try again.'))
        setRefreshToken((value) => value + 1)
      }
    } finally {
      if (isCurrent(scope)) setBusy(false)
    }
  }

  if (!companyId || !accountId) return <p>Choose a company in CRM.</p>
  return <section className="w-full space-y-5 px-4 py-6 sm:px-6">
    <Link className="text-sm font-semibold text-indigo-600" to="/crm/companies">← All companies</Link>
    <header>
      <p className="eyebrow">CRM / Customer company</p>
      <h1 className="mt-2 text-3xl font-bold">{account?.name ?? 'Company profile'}</h1>
    </header>
    {error && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{error}</p>}
    {loading ? <p role="status">Loading company…</p> : account && <>
      <form className="grid gap-3 rounded-xl border border-slate-200 bg-white p-5 sm:grid-cols-2"
        onSubmit={(event) => {
          event.preventDefault()
          void run(() => crmWorkApi.updateAccount(companyId, account, {
            name: name.trim(), website: website.trim() || null, notes: notes.trim() || null,
            ownerUserId: owner || null,
          }))
        }}>
        <label className="text-sm font-semibold">Company name
          <input className="form-input mt-1 w-full" value={name} maxLength={240}
            disabled={!can(PERMISSIONS.CRM_ACCOUNT_MODIFY)} onChange={(event) => setName(event.target.value)} />
        </label>
        <label className="text-sm font-semibold">Website
          <input className="form-input mt-1 w-full" value={website} maxLength={320}
            disabled={!can(PERMISSIONS.CRM_ACCOUNT_MODIFY)} onChange={(event) => setWebsite(event.target.value)} />
        </label>
        <label className="text-sm font-semibold sm:col-span-2">Notes
          <textarea className="form-input mt-1 w-full" rows={3} value={notes} maxLength={4000}
            disabled={!can(PERMISSIONS.CRM_ACCOUNT_MODIFY)} onChange={(event) => setNotes(event.target.value)} />
        </label>
        {can(PERMISSIONS.USER_READ) && <label className="text-sm font-semibold">Owner
          <select className="form-input mt-1 w-full" value={owner}
            disabled={!can(PERMISSIONS.CRM_ACCOUNT_MODIFY)}
            onChange={(event) => setOwner(event.target.value)}>
            <option value="">Unassigned</option>
            {owner && !owners.some((person) => person.userId === owner) &&
              <option value={owner}>Existing owner</option>}
            {owners.map((person) => <option key={person.userId} value={person.userId}>{person.name}</option>)}
          </select>
        </label>}
        {can(PERMISSIONS.CRM_ACCOUNT_MODIFY) && <div className="sm:col-span-2">
          <button className="primary-button" type="submit" disabled={busy || !name.trim()}>Save company</button>
        </div>}
      </form>
      {can(PERMISSIONS.CONTACT_READ) && <div className="space-y-3 rounded-xl border border-slate-200 bg-white p-5">
        <h2 className="text-lg font-bold">Linked contacts</h2>
        <div className="space-y-2">
          {contacts.map((contact) => <div key={contact.contactId} className="flex items-center justify-between gap-3">
            <Link className="text-sm font-semibold text-indigo-600" to={'/contacts/' + contact.contactId}>
              {contact.displayName || contact.contactId}
            </Link>
            {can(PERMISSIONS.CRM_ACCOUNT_MODIFY) && <button type="button" className="text-sm text-rose-600"
              disabled={busy}
              onClick={() => void run(() => crmWorkApi.unlinkContact(companyId, accountId, contact.contactId))}>Unlink</button>}
          </div>)}
          {!contacts.length && <p className="text-sm text-slate-500">No linked contacts.</p>}
          {contacts.length === 100 && <p className="text-xs text-slate-500">Showing up to 100 contacts.</p>}
        </div>
        {can(PERMISSIONS.CRM_ACCOUNT_MODIFY) && <>
          <label htmlFor="crm-company-contact-query" className="block text-sm font-semibold">Find contact to link</label>
          <input id="crm-company-contact-query" className="form-input w-full" placeholder="Search contacts"
            value={query} onChange={(event) => { setQuery(event.target.value); setChosen(''); setMatches([]) }} />
          <div className="flex flex-wrap gap-2">
            <select className="form-input min-w-48 flex-1" value={chosen} aria-label="Contact to link"
              onChange={(event) => setChosen(event.target.value)}>
              <option value="">Select a contact</option>
              {matches.filter((contact) => !contacts.some((link) => link.contactId === contact.id))
                .map((contact) => <option key={contact.id} value={contact.id}>
                  {contact.displayName || 'Unnamed contact'} {contact.primaryEmail || ''}
                </option>)}
            </select>
            <button className="primary-button" type="button" disabled={!chosen || busy}
              onClick={() => void run(() => crmWorkApi.linkContact(companyId, accountId, chosen))}>Link contact</button>
          </div>
        </>}
      </div>}
    </>}
  </section>
}
