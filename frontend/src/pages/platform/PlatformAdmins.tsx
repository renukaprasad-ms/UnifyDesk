import { useEffect, useState } from 'react'
import { platformApi } from '../../features/platform/platformApi'
import type { Page, PlatformAdmin } from '../../features/platform/types'

export default function PlatformAdmins() {
  const [data,setData]=useState<Page<PlatformAdmin>|null>(null)
  const [email,setEmail]=useState('')
  const [error,setError]=useState('')
  const load=()=>platformApi.admins('',0).then(setData).catch(e=>setError(e instanceof Error?e.message:'Unable to load admins'))
  useEffect(() => { void load() },[])
  const create=async(e:React.FormEvent)=>{e.preventDefault();setError('');try{await platformApi.createAdmin(email,[]);setEmail('');load()}catch(x){setError(x instanceof Error?x.message:'Unable to create admin')}}
  return <section>
    <h1 className="text-2xl font-bold">Platform admins</h1>
    <p className="mt-1 text-sm text-slate-500">Add registered users as internal admins. Assign least-privilege permissions before giving company access.</p>
    <form onSubmit={create} className="my-5 flex gap-2"><input className="form-input max-w-md" type="email" value={email} onChange={e=>setEmail(e.target.value)} placeholder="Registered user email" required/><button className="primary-button">Add admin</button></form>
    {error&&<p className="mb-3 text-sm text-rose-600">{error}</p>}
    <div className="overflow-x-auto rounded-xl border bg-white"><table className="w-full text-left text-sm"><thead><tr className="border-b"><th className="p-3">Admin</th><th>Role</th><th>Status</th><th>Permissions</th></tr></thead><tbody>
      {data?.content.map(a=><tr key={a.id} className="border-b last:border-0"><td className="p-3"><div className="font-medium">{a.name}</div><div className="text-slate-500">{a.email}</div></td><td>{a.role}</td><td>{a.status}</td><td>{a.role==='SUPER_ADMIN'?'All':a.permissions.length}</td></tr>)}
    </tbody></table></div>
  </section>
}
