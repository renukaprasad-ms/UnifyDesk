import { useEffect, useState } from 'react'
import { platformApi } from '../../features/platform/platformApi'
import type { Page, PlatformCompany } from '../../features/platform/types'

export default function PlatformCompanies() {
  const [data,setData]=useState<Page<PlatformCompany>|null>(null)
  const [search,setSearch]=useState('')
  const [page,setPage]=useState(0)
  const [error,setError]=useState('')
  const load=()=>platformApi.companies(search,page).then(setData).catch(e=>setError(e instanceof Error?e.message:'Unable to load companies'))
  useEffect(load,[page])
  return <section>
    <h1 className="text-2xl font-bold">Companies</h1>
    <form className="my-5 flex gap-2" onSubmit={e=>{e.preventDefault();setPage(0);load()}}>
      <input className="form-input max-w-md" value={search} onChange={e=>setSearch(e.target.value)} placeholder="Search name or business email" />
      <button className="primary-button">Search</button>
    </form>
    {error&&<p className="text-sm text-rose-600">{error}</p>}
    <div className="overflow-x-auto rounded-xl border bg-white"><table className="w-full text-left text-sm"><thead><tr className="border-b"><th className="p-3">Company</th><th>Email</th><th>Country</th><th>Status</th><th>Action</th></tr></thead><tbody>
      {data?.content.map(c=><tr key={c.id} className="border-b last:border-0"><td className="p-3 font-medium">{c.name}</td><td>{c.businessEmail||'—'}</td><td>{c.country||'—'}</td><td>{c.status}</td><td><button className="text-indigo-600" onClick={async()=>{await platformApi.updateCompanyStatus(c.id,c.status==='ACTIVE'?'SUSPENDED':'ACTIVE');load()}}>{c.status==='ACTIVE'?'Suspend':'Activate'}</button></td></tr>)}
    </tbody></table></div>
    <div className="mt-4 flex gap-3"><button disabled={!data||data.first} onClick={()=>setPage(p=>p-1)}>Previous</button><span>Page {(data?.number??0)+1} of {data?.totalPages??1}</span><button disabled={!data||data.last} onClick={()=>setPage(p=>p+1)}>Next</button></div>
  </section>
}
