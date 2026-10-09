import React from 'react'
import { AlertCircle, CheckCircle2, Inbox } from 'lucide-react'

export function Button({variant='primary',icon:Icon,children,...props}) {
  return <button className={'btn '+variant} {...props}>{Icon&&<Icon size={16}/>} {children}</button>
}
export function Badge({value}) {
  const v=String(value||'Pending');const tone=['ACTIVE','VERIFIED','ENABLED'].includes(v.toUpperCase())?'good':v.toUpperCase()==='SUSPENDED'||v.toUpperCase()==='REVOKED'?'bad':'pending'
  return <span className={'badge '+tone}><span className="badge-dot"/>{v.toLowerCase().replaceAll('_',' ')}</span>
}
export function Panel({children,extra=''}) {return <div className={'panel '+extra}>{children}</div>}
export function Heading({eyebrow,title,description,action}) {
  return <div className="section-heading"><div><span className="eyebrow">{eyebrow}</span><h2>{title}</h2><p>{description}</p></div>{action}</div>
}
export function Empty({title,body,action,icon:Icon=Inbox}) {
  return <div className="empty"><div className="empty-icon"><Icon size={25}/></div><h3>{title}</h3><p>{body}</p>{action}</div>
}
export function Field({label,children,hint}) {
  return <label className="field"><span>{label}</span>{children}{hint&&<small>{hint}</small>}</label>
}
export function Notice({children,tone='info'}) {
  const Icon=tone==='error'?AlertCircle:CheckCircle2
  return <div className={'notice '+tone}><Icon size={18}/><span>{children}</span></div>
}
export const date=v=>v?new Date(v).toLocaleDateString(undefined,{year:'numeric',month:'short',day:'numeric'}):'—'
export const money=v=>'₹'+(Number(v||0)/1000000).toFixed(4)
