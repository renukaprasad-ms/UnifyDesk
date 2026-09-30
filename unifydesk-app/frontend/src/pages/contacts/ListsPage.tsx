import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import ContactsNav from '../../components/contacts/ContactsNav'
import { contactsApi } from '../../features/contacts/contactsApi'
import type { ListSummary } from '../../features/contacts/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId } from '../../store/workspace/workspaceSlice'

export default function ListsPage() {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const { can } = useIam()
  const [items, setItems] = useState<ListSummary[]>([])
  const [name, setName] = useState('')
  const [error, setError] = useState('')
  const load = async () => { if (!companyId) return; try { setItems(await contactsApi.lists(companyId)) } catch (e) { setError(apiErrorMessage(e, 'Unable to load lists.')) } }
  useEffect(() => { void load() }, [companyId])
  return <section className="px-4 py-6 sm:px-6 lg:px-8"><div className="mx-auto max-w-6xl"><ContactsNav/><div className="flex flex-wrap items-end justify-between gap-4"><div><p className="eyebrow">Audience</p><h1 className="mt-2 text-3xl font-bold">Lists</h1><p className="mt-2 text-sm text-slate-500">Static contact membership with server-side counts.</p></div>{can(PERMISSIONS.CONTACT_MODIFY) && <form className="flex gap-2" onSubmit={async (e) => { e.preventDefault(); if (!companyId || !name.trim()) return; await contactsApi.createList(companyId,{name:name.trim()}); setName(''); await load() }}><label className="sr-only" htmlFor="list-name">List name</label><input id="list-name" className="form-input" value={name} placeholder="New list name" onChange={(e)=>setName(e.target.value)}/><button className="primary-button">Create list</button></form>}</div>{error && <div className="mt-5 rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{error}</div>}<div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{items.map((item)=><Link key={item.id} to={'/contacts/lists/'+item.id} className="panel p-5 hover:border-indigo-200"><h2 className="font-bold">{item.name}</h2><p className="mt-2 text-sm text-slate-500">{item.description || 'Static audience list'}</p><p className="mt-4 text-2xl font-bold">{item.memberCount.toLocaleString()}</p><p className="text-xs text-slate-500">members</p></Link>)}</div></div></section>
}
