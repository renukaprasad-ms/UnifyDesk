import React,{useState} from 'react'
import {Copy,Plus,Webhook,X} from 'lucide-react'
import {Badge,Button,Empty,Field,Heading,Notice,Panel} from './components'

export function Webhooks({project,items,create,disable,busy}) {
  const [editing,setEditing]=useState(false),[url,setUrl]=useState(''),[secret,setSecret]=useState(null)
  async function submit(e){e.preventDefault();const d=await create({url:url.trim()});if(d){setSecret(d.signingSecret);setUrl('');setEditing(false)}}
  return <><Heading eyebrow="INTEGRATIONS" title="Event webhooks" description="Get signed delivery and status events delivered to your HTTPS backend." action={project&&<Button icon={Plus} onClick={()=>setEditing(!editing)}>Add endpoint</Button>}/>
    <Notice>Allow only trusted HTTPS destinations, and verify webhook signatures inside your backend. Webhooks may need operator approval.</Notice>
    {secret&&<Panel extra="secret-reveal"><div className="panel-heading"><h3>Signing secret — save it now</h3><button className="icon-button" aria-label="Close" onClick={()=>setSecret(null)}><X size={19}/></button></div><pre className="secret-box">{secret}</pre><Button icon={Copy} variant="outline" onClick={()=>navigator.clipboard.writeText(secret)}>Copy signing secret</Button></Panel>}
    {!project?<Panel><Empty icon={Webhook} title="Choose a project" body="Webhooks belong to a single email project."/></Panel>:<>
      {editing&&<Panel extra="editor"><form onSubmit={submit}><h3>New webhook endpoint</h3><div className="inline-form"><Field label="HTTPS destination"><input autoFocus required type="url" pattern="https://.*" maxLength={1024} value={url} placeholder="https://example.com/email/events" onChange={e=>setUrl(e.target.value)}/></Field><Button type="submit" disabled={busy||!url.trim()}>Create webhook</Button><Button variant="ghost" type="button" onClick={()=>setEditing(false)}>Cancel</Button></div></form></Panel>}
      <Panel>{!items.length?<Empty icon={Webhook} title="No webhooks configured" body="Add an HTTPS endpoint to receive signed delivery events." action={<Button icon={Plus} onClick={()=>setEditing(true)}>Add webhook</Button>}/>:<><div className="table-head">{items.length} endpoints</div>{items.map(h=><div key={h.id} className="list-row"><span className="item-icon purple"><Webhook size={20}/></span><div className="item-main"><b className="break">{h.url}</b><small>Endpoint ID · {h.id}</small></div><Badge value={h.enabled?'ACTIVE':'Disabled'}/>{h.enabled&&<Button variant="danger" disabled={busy} onClick={()=>{if(window.confirm('Disable webhook '+h.url+'?'))disable(h.id)}}>Disable</Button>}</div>)}</>}</Panel>
    </>}
  </>
}
