import React,{useState} from 'react'
import {Copy,KeyRound,Plus,ShieldCheck,Trash2,X} from 'lucide-react'
import {Badge,Button,Empty,Field,Heading,Notice,Panel,date} from './components'

export function Keys({project,items,create,revoke,busy,notify}) {
  const [add,setAdd]=useState(false),[name,setName]=useState(''),[scopes,setScopes]=useState(['READ','SEND']),[secret,setSecret]=useState(null)
  function toggle(s){setScopes(x=>x.includes(s)?x.filter(y=>y!==s):[...x,s])}
  async function submit(e){e.preventDefault();const d=await create({name:name.trim(),scopes});if(d){setSecret(d.secret);setAdd(false);setName('');setScopes(['READ','SEND'])}}
  const copy=async()=>{try{await navigator.clipboard.writeText(secret);notify('API key copied')}catch{notify('Copy failed')}}
  return <><Heading eyebrow="DEVELOPER ACCESS" title="API keys" description="Give trusted backend services the least privilege they need for your project." action={project&&<Button icon={Plus} onClick={()=>setAdd(!add)}>Create API key</Button>}/>
    <Notice>API key secrets are shown only once. Never place these keys in a public React app, browser bundle, or source control.</Notice>
    {secret&&<div className="dialog-mask"><div className="dialog" role="dialog" aria-modal="true" aria-labelledby="created-key-title"><div className="dialog-top"><span className="item-icon teal"><KeyRound size={23}/></span><button aria-label="Close" className="icon-button" onClick={()=>setSecret(null)}><X size={19}/></button></div><h2 id="created-key-title">Save your API key</h2><p>This secret cannot be recovered after closing this window.</p><pre className="secret-box">{secret}</pre><div className="form-actions"><Button variant="outline" icon={Copy} onClick={copy}>Copy key</Button><Button onClick={()=>setSecret(null)}>I've saved my key</Button></div></div></div>}
    {!project?<Panel><Empty icon={KeyRound} title="Choose a project first" body="API keys are scoped to individual projects."/></Panel>:<>
      {add&&<Panel extra="editor"><form onSubmit={submit}><h3>Create a scoped API key</h3><Field label="Key name"><input required maxLength={100} autoFocus value={name} onChange={e=>setName(e.target.value)} placeholder="e.g. Order notifications"/></Field><div className="field-label">Allowed scopes</div><div className="scope-grid">{['SEND','READ','DOMAINS','VALIDATE','WEBHOOKS'].map(s=><label key={s} className={'scope '+(scopes.includes(s)?'selected':'')}><input type="checkbox" checked={scopes.includes(s)} onChange={()=>toggle(s)}/>{s}</label>)}</div><div className="form-actions"><Button type="submit" disabled={busy||!name.trim()||!scopes.length}>Create key</Button><Button variant="ghost" type="button" onClick={()=>setAdd(false)}>Cancel</Button></div></form></Panel>}
      <Panel>{items.length===0?<Empty icon={KeyRound} title="No API keys" body="Create a scoped key to connect your backend to UnifyDesk Email." action={<Button icon={Plus} onClick={()=>setAdd(true)}>Create key</Button>}/>:<><div className="table-head">{items.length} API keys <span>Secrets are never listed</span></div>{items.map(k=><div className="list-row" key={k.id}><span className="item-icon teal"><KeyRound size={20}/></span><div className="item-main"><b>{k.name}</b><small>Prefix {k.prefix} · {k.scopes} · {date(k.createdAt)}</small></div><Badge value={k.revokedAt?'REVOKED':'ACTIVE'}/>{!k.revokedAt&&<Button variant="danger" icon={Trash2} disabled={busy} onClick={()=>{if(window.confirm('Revoke key '+k.name+'? This cannot be undone.'))revoke(k.id)}}>Revoke</Button>}</div>)}</>}</Panel>
    </>}
  </>
}
