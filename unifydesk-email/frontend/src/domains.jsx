import React,{useState} from 'react'
import {CheckCircle2,Copy,Globe2,Plus,RefreshCw} from 'lucide-react'
import {Badge,Button,Empty,Field,Heading,Panel,date} from './components'

export function Domains({project,items,create,verify,busy,notify}) {
  const [add,setAdd]=useState(false),[domain,setDomain]=useState('')
  async function submit(e){e.preventDefault();if(await create({domain:domain.trim()})){setAdd(false);setDomain('')}}
  const copy=async value=>{try{await navigator.clipboard.writeText(value);notify('DNS value copied')}catch{notify('Copy failed — select the value manually')}}
  return <><Heading eyebrow="SENDER IDENTITY" title="Sending domains" description="Verify domain ownership and DKIM identity to prepare for trusted email delivery." action={project&&<Button icon={Plus} onClick={()=>setAdd(!add)}>Add domain</Button>}/>
    {!project?<Panel><Empty icon={Globe2} title="No project selected" body="Create a project to add its sender domain."/></Panel>:<>
      {add&&<Panel extra="editor"><form onSubmit={submit}><h3>Add a sending domain</h3><p className="muted">You'll need DNS access to verify this domain.</p><div className="inline-form"><Field label="Domain"><input autoFocus required maxLength={253} value={domain} onChange={e=>setDomain(e.target.value)} placeholder="mail.example.com"/></Field><Button type="submit" disabled={!domain.trim()||busy}>Add domain</Button><Button variant="ghost" type="button" onClick={()=>setAdd(false)}>Cancel</Button></div></form></Panel>}
      {items.length===0?<Panel><Empty icon={Globe2} title="No sending domains" body="Add a domain and publish its DNS records to verify sender identity." action={<Button icon={Plus} onClick={()=>setAdd(true)}>Add domain</Button>}/></Panel>:
        items.map(d=><Panel key={d.id} extra="domain-panel"><div className="domain-head"><span className="item-icon purple"><Globe2 size={23}/></span><div className="item-main"><h3>{d.domain}</h3><small>Sender domain ID · {d.id}</small></div><Badge value={d.status}/><Button variant="outline" icon={RefreshCw} disabled={busy} onClick={()=>verify(d.id)}>Verify DNS</Button></div>
          <div className="dns-meta"><span><CheckCircle2 className={d.ownershipVerified?'verified':'muted'} size={17}/> Ownership {d.ownershipVerified?'verified':'pending'}</span><span><CheckCircle2 className={d.dkimVerified?'verified':'muted'} size={17}/> DKIM {d.dkimVerified?'verified':'pending'}</span><span>Last checked: {date(d.lastCheckedAt)}</span></div>
          <h4>Required DNS records <span className="muted">{(d.dnsRecords||[]).length} records</span></h4>
          {!(d.dnsRecords||[]).length?<p className="muted">No DNS records are available yet.</p>:<div className="data-table-scroll"><table className="data-table"><thead><tr><th>Type</th><th>Name</th><th>Value</th><th>Status</th><th></th></tr></thead><tbody>{d.dnsRecords.map((r,i)=><tr key={i}><td><code className="record-type">{r.type}</code></td><td><code>{r.name}</code></td><td className="long-record"><code title={r.value}>{r.value}</code></td><td><Badge value={r.verified?'VERIFIED':'Pending'}/></td><td><button className="icon-button" title="Copy DNS value" aria-label={'Copy record value for '+r.name} onClick={()=>copy(r.value)}><Copy size={17}/></button></td></tr>)}</tbody></table></div>}
        </Panel>)}
    </>}
  </>
}
