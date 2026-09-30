import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import ContactsNav from '../../components/contacts/ContactsNav'
import { contactsApi } from '../../features/contacts/contactsApi'
import type { SegmentDefinition } from '../../features/contacts/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId } from '../../store/workspace/workspaceSlice'

export default function SegmentsPage() {
  const companyId=useAppSelector(selectSelectedCompanyId)
  const {can}=useIam()
  const [items,setItems]=useState<SegmentDefinition[]>([])
  const [error,setError]=useState('')
  useEffect(()=>{ if(!companyId)return; contactsApi.segments(companyId).then(setItems).catch(e=>setError(apiErrorMessage(e,'Unable to load segments.'))) },[companyId])
  return <section className="px-4 py-6 sm:px-6 lg:px-8"><div className="mx-auto max-w-6xl"><ContactsNav/><div className="flex flex-wrap items-end justify-between gap-4"><div><p className="eyebrow">Audience</p><h1 className="mt-2 text-3xl font-bold">Segments</h1><p className="mt-2 text-sm text-slate-500">Dynamic audiences evaluated by the backend from a bounded filter AST.</p></div>{can(PERMISSIONS.CONTACT_MODIFY)&&<Link className="primary-button" to="/contacts/segments/new">New segment</Link>}</div>{error&&<div className="mt-5 rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{error}</div>}<div className="mt-6 grid gap-4 md:grid-cols-2">{items.map(item=><Link key={item.id} to={'/contacts/segments/'+item.id} className="panel p-5 hover:border-indigo-200"><div className="flex items-start justify-between gap-4"><div><h2 className="font-bold">{item.name}</h2><p className="mt-1 text-sm text-slate-500">{item.description||'Dynamic contact segment'}</p></div><span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-bold">{item.countState||'PENDING'}</span></div><p className="mt-4 text-2xl font-bold">{item.count==null?'—':item.count.toLocaleString()}</p><p className="text-xs text-slate-500">matching contacts</p></Link>)}</div></div></section>
}
