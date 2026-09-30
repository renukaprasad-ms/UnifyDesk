import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import ContactsNav from '../../components/contacts/ContactsNav'
import SegmentBuilder from '../../components/contacts/SegmentBuilder'
import { contactsApi } from '../../features/contacts/contactsApi'
import { emptySegment, segmentFields, validateSegmentClient } from '../../features/contacts/segmentModel'
import type { CustomFieldDefinition, ListSummary, SegmentGroup, SegmentPreview, TagSummary } from '../../features/contacts/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId } from '../../store/workspace/workspaceSlice'

export default function SegmentEditorPage(){
  const {segmentId}=useParams()
  const creating=!segmentId||segmentId==='new'
  const companyId=useAppSelector(selectSelectedCompanyId)
  const navigate=useNavigate()
  const [name,setName]=useState('')
  const [description,setDescription]=useState('')
  const [filter,setFilter]=useState<SegmentGroup>(emptySegment())
  const [customFields,setCustomFields]=useState<CustomFieldDefinition[]>([])
  const [tags,setTags]=useState<TagSummary[]>([])
  const [lists,setLists]=useState<ListSummary[]>([])
  const [preview,setPreview]=useState<SegmentPreview|null>(null)
  const [errors,setErrors]=useState<string[]>([])
  const [serverErrors,setServerErrors]=useState<string[]>([])
  const [saving,setSaving]=useState(false)

  useEffect(()=>{ if(!companyId)return; Promise.all([contactsApi.customFields(companyId),contactsApi.tags(companyId),contactsApi.lists(companyId)]).then(([c,t,l])=>{setCustomFields(c);setTags(t);setLists(l)}); if(!creating&&segmentId){contactsApi.getSegment(companyId,segmentId).then(s=>{setName(s.name);setDescription(s.description||'');setFilter(s.normalizedFilter||s.filter)})}},[companyId,segmentId,creating])
  const fields=segmentFields(customFields,tags.map(t=>({value:t.id,label:t.name})),lists.map(l=>({value:l.id,label:l.name})))
  const validate=()=>{const local=validateSegmentClient(filter,fields);setErrors(local);return local.length===0}
  const runPreview=async()=>{ if(!companyId||!validate())return;setServerErrors([]);try{const validation=await contactsApi.validateSegment(companyId,filter);if(!validation.valid){setServerErrors(validation.errors?.map(e=>e.message)||['Segment is invalid.']);return} const normalized=validation.normalizedFilter||filter;setFilter(normalized);setPreview(await contactsApi.previewSegment(companyId,normalized,20))}catch(e){setServerErrors([apiErrorMessage(e,'Unable to preview segment.')])}}
  const save=async()=>{if(!companyId||!name.trim()||!validate())return;setSaving(true);setServerErrors([]);try{const validation=await contactsApi.validateSegment(companyId,filter);if(!validation.valid){setServerErrors(validation.errors?.map(e=>e.message)||['Segment is invalid.']);return} const normalized=validation.normalizedFilter||filter;const saved=creating?await contactsApi.createSegment(companyId,{name:name.trim(),description:description.trim()||null,filterVersion:1,filter:normalized}):await contactsApi.updateSegment(companyId,segmentId!,{name:name.trim(),description:description.trim()||null,filterVersion:1,filter:normalized});navigate('/contacts/segments/'+saved.id,{replace:true})}catch(e){setServerErrors([apiErrorMessage(e,'Unable to save segment.')])}finally{setSaving(false)}}
  return <section className="px-4 py-6 sm:px-6 lg:px-8"><div className="mx-auto max-w-6xl"><ContactsNav/><h1 className="text-3xl font-bold">{creating?'New segment':'Edit segment'}</h1><div className="panel mt-6 grid gap-4 p-5 md:grid-cols-2"><label className="text-xs font-semibold">Name<input className="form-input mt-2" value={name} onChange={e=>setName(e.target.value)}/></label><label className="text-xs font-semibold">Description<input className="form-input mt-2" value={description} onChange={e=>setDescription(e.target.value)}/></label></div><div className="mt-5"><SegmentBuilder value={filter} fields={fields} onChange={value=>{setFilter(value);setPreview(null);setErrors([]);setServerErrors([])}}/></div>{[...errors,...serverErrors].length>0&&<div role="alert" className="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700"><ul className="list-disc pl-5">{[...errors,...serverErrors].map((e,i)=><li key={i}>{e}</li>)}</ul></div>}<div className="mt-5 flex flex-wrap gap-2"><button className="secondary-button" onClick={()=>void runPreview()}>Validate & preview</button><button className="primary-button" disabled={saving||!name.trim()} onClick={()=>void save()}>{saving?'Saving…':'Save segment'}</button></div>{preview&&<section className="panel mt-5 p-5" aria-live="polite"><div className="flex flex-wrap items-center justify-between gap-3"><h2 className="font-bold">Preview</h2><span className="rounded-full bg-indigo-50 px-3 py-1 text-xs font-bold text-indigo-700">{preview.countState}: {preview.count==null?'pending':preview.count.toLocaleString()}</span></div><div className="mt-4 grid gap-2">{preview.sample.map(c=><div key={c.id} className="rounded-lg bg-slate-50 p-3 text-sm"><strong>{c.displayName}</strong><span className="ml-2 text-slate-500">{c.primaryEmail||c.primaryPhone||''}</span></div>)}</div></section>}</div></section>
}
