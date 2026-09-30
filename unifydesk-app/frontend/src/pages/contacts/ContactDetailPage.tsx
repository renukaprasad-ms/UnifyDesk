import { useEffect, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router'
import ContactsNav from '../../components/contacts/ContactsNav'
import { contactsApi } from '../../features/contacts/contactsApi'
import type { CommunicationPreference, ContactActivity, ContactDetail, ContactIdentity, Suppression } from '../../features/contacts/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId } from '../../store/workspace/workspaceSlice'

const channels: CommunicationPreference['channel'][] = ['EMAIL', 'SMS', 'WHATSAPP']

export default function ContactDetailPage() {
  const { contactId = '' } = useParams()
  const companyId = useAppSelector(selectSelectedCompanyId)
  const { can } = useIam()
  const [contact, setContact] = useState<ContactDetail | null>(null)
  const [preferences, setPreferences] = useState<CommunicationPreference[]>([])
  const [suppressions, setSuppressions] = useState<Suppression[]>([])
  const [activity, setActivity] = useState<ContactActivity[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editingPreference, setEditingPreference] = useState<CommunicationPreference['channel'] | null>(null)

  const load = async () => {
    if (!companyId || !contactId) return
    setLoading(true)
    setError('')
    try {
      const [detail, pref, suppressed, timeline] = await Promise.all([
        contactsApi.get(companyId, contactId),
        can(PERMISSIONS.CONTACT_CONSENT_READ) ? contactsApi.preferences(companyId, contactId) : Promise.resolve([]),
        can(PERMISSIONS.CONTACT_CONSENT_READ) ? contactsApi.suppressions(companyId, contactId) : Promise.resolve([]),
        contactsApi.activity(companyId, contactId).then((page) => page.items).catch(() => []),
      ])
      setContact(detail)
      setPreferences(pref)
      setSuppressions(suppressed)
      setActivity(timeline)
    } catch (loadError) {
      setError(apiErrorMessage(loadError, 'Contact not found.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load() }, [companyId, contactId])

  const changePreference = async (state: CommunicationPreference['state']) => {
    const channel = editingPreference
    if (!companyId || !channel) return
    if (!window.confirm('Record ' + state.replaceAll('_', ' ') + ' for ' + channel + ' marketing?')) return
    await contactsApi.updatePreference(companyId, contactId, channel, state, 'Changed by workspace user')
    setEditingPreference(null)
    await load()
  }

  if (contact?.status === 'MERGED' && contact.mergedIntoContactId) return <Navigate to={'/contacts/' + contact.mergedIntoContactId} replace />

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-6xl">
        <ContactsNav />
        {loading ? <div className="panel p-10 text-center text-sm text-slate-500">Loading contact…</div> : error || !contact ? (
          <div className="panel p-8 text-center"><h1 className="text-xl font-bold">Contact unavailable</h1><p className="mt-2 text-sm text-slate-500">{error || 'This contact could not be loaded.'}</p><Link to="/contacts" className="primary-button mt-5 inline-flex">Back to contacts</Link></div>
        ) : (
          <>
            <header><p className="eyebrow">Contact</p><h1 className="mt-2 text-3xl font-bold text-slate-950">{contact.displayName}</h1><p className="mt-2 text-sm text-slate-500">{contact.organization || 'No organization'}</p></header>
            <div className="mt-6 grid gap-5 lg:grid-cols-2">
              <section className="panel p-5"><h2 className="text-lg font-bold">Profile & identities</h2><dl className="mt-4 grid gap-3 text-sm"><div><dt className="text-slate-500">Email</dt><dd className="font-medium">{contact.primaryEmail || '—'}</dd></div><div><dt className="text-slate-500">Phone</dt><dd className="font-medium">{contact.primaryPhone || '—'}</dd></div></dl><div className="mt-5 space-y-2">{contact.identities.map((identity: ContactIdentity) => <div key={identity.id} className="flex items-center justify-between gap-3 rounded-xl bg-slate-50 p-3 text-sm"><span><strong>{identity.type}</strong> · {identity.value}</span>{identity.primary && <span className="rounded-full bg-indigo-50 px-2 py-1 text-xs font-bold text-indigo-700">Primary</span>}</div>)}</div></section>
              <section className="panel p-5"><h2 className="text-lg font-bold">Lists & tags</h2><div className="mt-4 flex flex-wrap gap-2">{contact.tags?.map((tag) => <span key={tag.id} className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold">{tag.name}</span>) || null}</div><div className="mt-4 space-y-2">{contact.lists?.map((list) => <Link key={list.id} to={'/contacts/lists/' + list.id} className="block rounded-xl border border-slate-200 p-3 text-sm font-semibold hover:bg-slate-50">{list.name} · {list.memberCount.toLocaleString()} members</Link>)}</div></section>
              {can(PERMISSIONS.CONTACT_CONSENT_READ) && <section className="panel p-5 lg:col-span-2"><h2 className="text-lg font-bold">Marketing preferences</h2><p className="mt-1 text-sm text-slate-500">Contact existence does not imply marketing consent. Each channel is managed separately.</p><div className="mt-4 grid gap-3 md:grid-cols-3">{channels.map((channel) => {
                const pref = preferences.find((item) => item.channel === channel)
                return <div key={channel} className="rounded-xl border border-slate-200 p-4"><div className="flex items-center justify-between"><strong>{channel === 'WHATSAPP' ? 'WhatsApp' : channel}</strong><span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-bold">{pref?.state ?? 'UNKNOWN'}</span></div><p className="mt-2 text-xs text-slate-500">Source: {pref?.source || 'Not recorded'}{pref?.occurredAt ? ' · ' + new Date(pref.occurredAt).toLocaleString() : ''}</p>{can(PERMISSIONS.CONTACT_CONSENT_MODIFY) && <button className="secondary-button mt-3" onClick={() => setEditingPreference(channel)}>Change</button>}</div>
              })}</div></section>}
              {can(PERMISSIONS.CONTACT_CONSENT_READ) && <section className="panel p-5 lg:col-span-2"><h2 className="text-lg font-bold">Suppressions</h2><p className="mt-1 text-sm text-slate-500">Suppression is a delivery block and is separate from consent.</p><div className="mt-4 space-y-2">{suppressions.length === 0 ? <p className="text-sm text-slate-500">No suppressions.</p> : suppressions.map((item) => <div key={item.id} className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-rose-100 bg-rose-50 p-3 text-sm"><div><strong>{item.reason.replaceAll('_', ' ')}</strong><p className="text-xs text-rose-700">Source: {item.source || 'Unknown'}{item.createdAt ? ' · ' + new Date(item.createdAt).toLocaleString() : ''}</p></div>{item.liftable && can(PERMISSIONS.CONTACT_CONSENT_MODIFY) && <button className="secondary-button" onClick={async () => { if (!window.confirm('Lift this suppression?')) return; if (companyId) { await contactsApi.liftSuppression(companyId, contactId, item.id); await load() } }}>Lift suppression</button>}</div>)}</div></section>}
              <section className="panel p-5 lg:col-span-2"><h2 className="text-lg font-bold">Activity</h2><div className="mt-4 space-y-3">{activity.length === 0 ? <p className="text-sm text-slate-500">No recent activity.</p> : activity.map((item) => <div key={item.id} className="border-l-2 border-indigo-200 pl-4"><strong className="text-sm">{item.title}</strong><p className="text-xs text-slate-500">{item.summary}</p><time className="text-xs text-slate-400">{new Date(item.occurredAt).toLocaleString()}</time></div>)}</div></section>
            </div>
          </>
        )}
        {editingPreference && <div className="mobile-modal-shell fixed inset-0 z-50 flex items-end justify-center bg-slate-950/35 p-0 sm:items-center sm:p-4"><div role="dialog" aria-modal="true" className="mobile-sheet w-full max-w-lg rounded-t-2xl bg-white p-5 sm:rounded-2xl"><h2 className="text-lg font-bold">Change {editingPreference} marketing preference</h2><p className="mt-2 text-sm text-slate-500">This records an explicit preference event. It does not remove unrelated suppressions.</p><div className="mt-5 grid gap-2">{(['OPTED_IN','OPTED_OUT','UNKNOWN'] as const).map((state) => <button key={state} className="secondary-button justify-start" onClick={() => void changePreference(state)}>{state.replaceAll('_',' ')}</button>)}</div><button className="secondary-button mt-4" onClick={() => setEditingPreference(null)}>Cancel</button></div></div>}
      </div>
    </section>
  )
}
