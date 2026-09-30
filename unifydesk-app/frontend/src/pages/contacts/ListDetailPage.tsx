import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import ContactsNav from '../../components/contacts/ContactsNav'
import { contactsApi } from '../../features/contacts/contactsApi'
import type { ContactSummary, ListSummary } from '../../features/contacts/types'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId } from '../../store/workspace/workspaceSlice'

export default function ListDetailPage() {
  const { listId='' }=useParams()
  const companyId=useAppSelector(selectSelectedCompanyId)
  const [list,setList]=useState<ListSummary|null>(null)
  const [members,setMembers]=useState<ContactSummary[]>([])
  const [cursor,setCursor]=useState<string|null>(null)
  const [hasMore,setHasMore]=useState(false)
  const load=async(reset=true)=>{ if(!companyId)return; const [detail,page]=await Promise.all([contactsApi.getList(companyId,listId),contactsApi.listMembers(companyId,listId,reset?null:cursor)]);setList(detail);setMembers((current)=>reset?page.items:[...current,...page.items.filter(n=>!current.some(c=>c.id===n.id))]);setCursor(page.nextCursor);setHasMore(page.hasMore)}
  useEffect(()=>{void load()},[companyId,listId])
  return <section className="px-4 py-6 sm:px-6 lg:px-8"><div className="mx-auto max-w-6xl"><ContactsNav/><h1 className="text-3xl font-bold">{list?.name || 'List'}</h1><p className="mt-2 text-sm text-slate-500">{list?.memberCount.toLocaleString() || 0} members. Membership stays server-side and is loaded by cursor.</p><div className="panel mt-6 overflow-hidden"><table className="mobile-card-table min-w-full text-sm"><thead><tr className="bg-slate-50 text-left"><th className="p-3">Contact</th><th className="p-3">Email</th><th className="p-3">Organization</th></tr></thead><tbody>{members.map(m=><tr key={m.id} className="border-t"><td data-label="Contact" className="p-3"><Link className="font-semibold text-indigo-700" to={'/contacts/'+m.id}>{m.displayName}</Link></td><td data-label="Email" className="p-3">{m.primaryEmail||'—'}</td><td data-label="Organization" className="p-3">{m.organization||'—'}</td></tr>)}</tbody></table></div>{hasMore&&<button className="secondary-button mt-4" onClick={()=>void load(false)}>Load more</button>}</div></section>
}
