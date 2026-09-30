import { useState } from 'react'
import { useNavigate } from 'react-router'
import ContactsNav from '../../components/contacts/ContactsNav'
import { contactsApi } from '../../features/contacts/contactsApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId } from '../../store/workspace/workspaceSlice'

export default function ImportsPage(){
 const companyId=useAppSelector(selectSelectedCompanyId)
 const navigate=useNavigate()
 const [file,setFile]=useState<File|null>(null)
 const [busy,setBusy]=useState(false)
 const [error,setError]=useState('')
 const start=async()=>{if(!companyId||!file)return;setBusy(true);setError('');try{const intent=await contactsApi.createImport(companyId,file);await contactsApi.uploadImportFile(intent,file);navigate('/contacts/imports/'+intent.id)}catch(e){setError(apiErrorMessage(e,'Unable to start CSV import.'))}finally{setBusy(false)}}
 return <section className="px-4 py-6 sm:px-6 lg:px-8"><div className="mx-auto max-w-4xl"><ContactsNav/><p className="eyebrow">Audience</p><h1 className="mt-2 text-3xl font-bold">Import contacts</h1><p className="mt-2 text-sm text-slate-500">CSV is uploaded directly to the backend/storage workflow. The browser keeps only the selected File handle and bounded preview rows.</p><div className="panel mt-6 p-6"><label className="block text-sm font-semibold">CSV file<input className="form-input mt-2" type="file" accept=".csv,text/csv" onChange={e=>setFile(e.target.files?.[0]||null)}/></label><div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800"><strong>Consent reminder:</strong> importing a contact does not grant marketing permission. Consent is mapped separately only when you have explicit evidence.</div>{error&&<p className="mt-4 text-sm text-rose-700">{error}</p>}<button className="primary-button mt-5" disabled={!file||busy} onClick={()=>void start()}>{busy?'Uploading…':'Upload and preview'}</button></div></div></section>
}
