import AccountSecurity from './AccountSecurity'
import OrganizationRoles from './OrganizationRoles'
import React,{useState} from 'react'
import {Activity,ArrowRight,ArrowUpRight,Check,CheckCircle2,Copy,FolderKanban,Globe2,KeyRound,Plus,Send,ShieldCheck,Webhook,LogOut,LockKeyhole} from 'lucide-react'
import {Badge,Button,Empty,Field,Heading,Notice,Panel,money,date} from './components'

export function Overview({projects,project,data,go}) {
  const verified=data.domains.filter(x=>x.status==='VERIFIED').length
  const activeKeys=data.keys.filter(x=>!x.revokedAt).length
  const setup=[
    {title:'Create a project',detail:'Isolate environments and applications',ready:projects.length>0,target:'projects'},
    {title:'Verify a sending domain',detail:'Configure DNS and DKIM authentication',ready:verified>0,target:'domains'},
    {title:'Create a scoped API key',detail:'Connect your backend securely',ready:activeKeys>0,target:'keys'},
    {title:'Configure event webhooks',detail:'Receive signed lifecycle notifications',ready:data.webhooks.some(x=>x.enabled),target:'webhooks'}
  ]
  const stats=[
    {title:'Total projects',value:projects.length,sub:'Your organization',icon:FolderKanban,color:'blue'},
    {title:'Verified domains',value:verified,sub:data.domains.length+' configured',icon:Globe2,color:'purple'},
    {title:'Active API keys',value:activeKeys,sub:'Scoped credentials',icon:KeyRound,color:'teal'},
    {title:'SMTP handoffs',value:data.usage?.handedOff??'—',sub:'Reporting period',icon:Send,color:'orange'},
  ]
  return <>
    <div className="hero"><div className="hero-copy"><span className="hero-eyebrow"><CheckCircle2 size={16}/> YOUR EMAIL WORKSPACE</span><h2>Everything email,<br/><em>one clear view.</em></h2><p>Manage sender identity, connect your apps, and keep your delivery infrastructure organized.</p><Button variant="white" icon={ArrowRight} onClick={()=>go('domains')}>Manage sending domains</Button></div><div className="hero-visual"><div className="hero-ring a"/><div className="hero-ring b"/><div className="hero-plane"><Send size={48}/></div><div className="hero-float"><CheckCircle2 size={21}/><span><b>Console connected</b><small>Secure control plane</small></span></div></div></div>
    <div className="metrics">{stats.map(s=>{const Icon=s.icon;return <div className="metric" key={s.title}><div className="metric-top"><span className={'metric-icon '+s.color}><Icon size={22}/></span><span className="metric-arrow"><ArrowUpRight size={17}/></span></div><p>{s.title}</p><div className="metric-number">{s.value}</div><small>{s.sub}</small></div>})}</div>
    <div className="two-columns">
      <Panel><div className="panel-heading"><div><h3>Getting started</h3><p>Everything you need to launch confidently.</p></div><span className="mini-label">CHECKLIST</span></div>{setup.map((x,i)=><button key={x.title} className="step" onClick={()=>go(x.target)}><span className={'step-index '+(x.ready?'complete':'')}>{x.ready?<Check size={17}/>:i+1}</span><span><b>{x.title}</b><small>{x.detail}</small></span><ArrowUpRight size={18}/></button>)}</Panel>
      <Panel><div className="panel-heading"><div><h3>Workspace status</h3><p>Configuration for your current project.</p></div><ShieldCheck size={22} color="#487ad7"/></div>
        <div className="status-line"><span><CheckCircle2 size={20} color="#17a889"/></span><div><b>Console API</b><small>Authenticated connection</small></div><Badge value="Healthy"/></div>
        <div className="status-line"><span><FolderKanban size={20}/></span><div><b>Selected project</b><small>{project?.name||'No project yet'}</small></div><Badge value={project?.status||'Pending'}/></div>
        <div className="status-line"><span><Send size={20}/></span><div><b>Project sending</b><small>Operator approval required</small></div><Badge value={project?.sendingEnabled?'ACTIVE':'Disabled'}/></div>
        <Notice>Projects start with sending disabled until your operator approves access, DNS and safety checks.</Notice>
      </Panel>
    </div>
  </>
}
export function Projects({projects,create,busy}) {
  const [editing,setEditing]=useState(false),[name,setName]=useState('')
  const submit=async e=>{e.preventDefault();if(await create({name:name.trim()})){setEditing(false);setName('')}}
  return <><Heading eyebrow="ORGANIZATION" title="Projects" description="Each workload gets isolated domains, credentials, and usage." action={<Button icon={Plus} onClick={()=>setEditing(!editing)}>New project</Button>}/>
    {editing&&<Panel extra="editor"><form onSubmit={submit}><h3>Create project</h3><div className="inline-form"><Field label="Project name"><input required autoFocus maxLength={120} placeholder="e.g. Transactional notifications" value={name} onChange={e=>setName(e.target.value)}/></Field><Button type="submit" disabled={busy||!name.trim()}>Create project</Button><Button type="button" variant="ghost" onClick={()=>setEditing(false)}>Cancel</Button></div></form></Panel>}
    <Panel>{projects.length===0?<Empty icon={FolderKanban} title="No projects yet" body="Create your first project to manage email sending domains and API credentials." action={<Button icon={Plus} onClick={()=>setEditing(true)}>Create project</Button>}/>:<><div className="table-head">{projects.length} {projects.length===1?'project':'projects'} <span>Sending is operator-controlled</span></div>{projects.map(p=><div className="list-row" key={p.id}><span className="item-icon blue"><FolderKanban size={20}/></span><div className="item-main"><strong>{p.name}</strong><small>Project ID · {p.id}</small></div><Badge value={p.status}/><Badge value={p.sendingEnabled?'ACTIVE':'Disabled'}/></div>)}</>}</Panel>
  </>
}
export function Usage({project,data}) {
  return <><Heading eyebrow="ANALYTICS" title="Usage & billing" description="Review project usage and estimated accrued cost, based on accepted handoffs."/>
    {!project?<Panel><Empty title="Create a project first" body="Usage is tracked per project." icon={Activity}/></Panel>:<>
      <div className="usage-cards"><Panel><span className="metric-icon blue"><Send size={21}/></span><p>Accepted SMTP handoffs</p><strong>{data?.handedOff??'—'}</strong><small>Current reporting period</small></Panel><Panel><span className="metric-icon teal"><Activity size={21}/></span><p>Estimated accrued cost</p><strong>{data?money(data.chargedMicroInr):'—'}</strong><small>INR, indicative only</small></Panel></div>
      <Panel><h3>Reporting period</h3><p className="muted">{date(data?.from)} — {date(data?.to)} (UTC)</p><Notice>Payment collection and tax invoices are not active. Accepted SMTP handoffs are not a guarantee of inbox delivery.</Notice></Panel>
    </>}
  </>
}
export function Account({session,logout}) {
  return <><Heading eyebrow="IDENTITY" title="Account settings" description="Review your organization and authenticated session."/><Panel><div className="account-card"><span className="account-big-avatar">{session.email?.[0]?.toUpperCase()||'U'}</span><div><h3>{session.email}</h3><p>Independent Email Console account</p><Badge value={session.role}/></div></div><div className="settings-row"><span><b>Organization ID</b><small>{session.organizationId}</small></span><ShieldCheck size={22}/></div><div className="settings-row"><span><b>Session expires</b><small>{date(session.expiresAt)}</small></span><LockKeyhole size={22}/></div><div className="settings-row"><span><b>Sign out of this browser tab</b><small>Revokes your current console session.</small></span><Button variant="outline" icon={LogOut} onClick={logout}>Sign out</Button></div></Panel><AccountSecurity/><OrganizationRoles session={session}/></>
}
