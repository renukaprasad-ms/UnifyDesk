import React,{useEffect,useState,useCallback} from 'react'
import { Activity, ArrowRight, ChevronDown, FolderKanban, Globe2, KeyRound, LayoutDashboard, LogOut, Mail, Menu, RefreshCw, Settings2, ShieldCheck, Webhook, X } from 'lucide-react'
import {clearSession,readSession,request} from './api'
import Login from './Login'
import {Button} from './components'
import {Overview,Projects,Domains,Keys,Webhooks,Usage,Account} from './screens'

const NAV=[
  {id:'overview',text:'Overview',icon:LayoutDashboard},{id:'projects',text:'Projects',icon:FolderKanban},
  {id:'domains',text:'Sending domains',icon:Globe2},{id:'keys',text:'API keys',icon:KeyRound},
  {id:'webhooks',text:'Webhooks',icon:Webhook},{id:'usage',text:'Usage & billing',icon:Activity},
  {id:'account',text:'Account',icon:Settings2}
]
const EMPTY={domains:[],keys:[],webhooks:[],usage:null}

function Dashboard({session,onLogout}) {
  const [tab,setTab]=useState('overview'),[menu,setMenu]=useState(false)
  const [projects,setProjects]=useState([]),[projectId,setProjectId]=useState('')
  const [details,setDetails]=useState(EMPTY),[loading,setLoading]=useState(true),[refreshTick,setRefreshTick]=useState(0)
  const [fetching,setFetching]=useState(false),[busy,setBusy]=useState(false),[message,setMessage]=useState('')
  const notify=useCallback(text=>setMessage(text),[])
  const selected=projects.find(p=>p.id===projectId)||null
  const path=selected?'/projects/'+selected.id:''
  useEffect(()=>{if(!message)return;const id=setTimeout(()=>setMessage(''),5500);return()=>clearTimeout(id)},[message])
  const refreshProjects=useCallback(async()=>{
    const data=await request('/projects'),list=Array.isArray(data)?data:[]
    setProjects(list);setProjectId(current=>list.some(p=>p.id===current)?current:(list[0]?.id||''))
    return list
  },[])
  useEffect(()=>{
    let active=true
    refreshProjects().catch(e=>{if(active)notify(e.message)}).finally(()=>{if(active)setLoading(false)})
    return()=>{active=false}
  },[refreshProjects,notify])
  useEffect(()=>{
    if(!projectId){setDetails(EMPTY);return}
    let active=true
    setFetching(true);setDetails(EMPTY)
    Promise.allSettled([
      request('/projects/'+projectId+'/domains'),request('/projects/'+projectId+'/keys'),
      request('/projects/'+projectId+'/webhooks'),request('/projects/'+projectId+'/usage')
    ]).then(results=>{
      if(!active)return
      const data=results.map(r=>r.status==='fulfilled'?r.value:null)
      setDetails({domains:Array.isArray(data[0])?data[0]:[],keys:Array.isArray(data[1])?data[1]:[],webhooks:Array.isArray(data[2])?data[2]:[],usage:data[3]})
      const rejected=results.find(r=>r.status==='rejected')
      if(rejected)notify('Some project data is unavailable: '+rejected.reason.message)
    }).finally(()=>{if(active)setFetching(false)})
    return()=>{active=false}
  },[projectId,refreshTick,notify])
  const refresh=()=>setRefreshTick(n=>n+1)
  async function mutate(url,method,body,message){
    setBusy(true)
    try {const result=await request(url,method,body);notify(message);refresh();return result??true}
    catch(e){notify(e.message);return null}
    finally{setBusy(false)}
  }
  async function createProject(body){
    const result=await mutate('/projects','POST',body,'Project created')
    if(result)await refreshProjects().catch(e=>notify(e.message))
    return !!result
  }
  async function logout(){
    await request('/sessions','DELETE').catch(()=>{})
    clearSession();onLogout()
  }
  const go=id=>{setTab(id);setMenu(false)}
  const activeLabel=NAV.find(x=>x.id===tab)?.text||'Overview'
  return <div className="app-shell">
    <aside className={'sidebar '+(menu?'open':'')}><div className="brand"><span className="brand-mark"><Mail size={21}/></span>unifydesk <strong>email</strong><button className="mobile-close" aria-label="Close menu" onClick={()=>setMenu(false)}><X size={19}/></button></div>
      <div className="workspace-pill"><span className="workspace-logo"><Mail size={19}/></span><span><strong>Email control center</strong><small>Developer workspace</small></span><ChevronDown size={16}/></div>
      <div className="nav-label">WORKSPACE</div>
      <nav aria-label="Product navigation">{NAV.map(n=>{const Icon=n.icon;return <button key={n.id} className={'nav-item '+(tab===n.id?'active':'')} onClick={()=>go(n.id)} aria-current={tab===n.id?'page':undefined}><Icon size={19}/>{n.text}</button>})}</nav>
      <div className="sidebar-spacer"/>
      <div className="sidebar-tip"><ShieldCheck size={21}/><strong>Send with confidence</strong><p>Verify your domain and get operator approval before sending.</p><button onClick={()=>go('domains')}>Review DNS setup <ArrowRight size={14}/></button></div>
      <button className="sidebar-account" onClick={()=>go('account')}><span className="avatar">{session.email?.[0]?.toUpperCase()||'U'}</span><span><strong>{session.email}</strong><small>{session.role||'Member'}</small></span><Settings2 size={16}/></button>
    </aside>
    {menu&&<button aria-label="Close navigation" className="mobile-overlay" onClick={()=>setMenu(false)}/>}
    <div className="main-column">
      <header className="topbar"><div className="breadcrumbs"><button className="menu-trigger" onClick={()=>setMenu(true)} aria-label="Open navigation"><Menu size={22}/></button><span>Workspace</span><span>/</span><strong>{activeLabel}</strong></div><div className="top-actions"><span className="connection"><i/> Console connected</span><button className="icon-button" onClick={()=>{refreshProjects().catch(e=>notify(e.message));refresh()}} aria-label="Refresh" title="Refresh data"><RefreshCw size={18}/></button><button className="avatar header-avatar" aria-label="Account" onClick={()=>go('account')}>{session.email?.[0]?.toUpperCase()||'U'}</button></div></header>
      <main className="content">
        <div className="page-top"><div><span className="eyebrow">UNIFYDESK EMAIL</span><h1>{tab==='overview'?'Good to see you.':activeLabel}</h1><p>{tab==='overview'?"Here's what's happening across your email workspace.":'Your tools to manage and monitor independent email delivery.'}</p></div><div className="project-picker"><label htmlFor="active-project">ACTIVE PROJECT</label><div><FolderKanban size={18}/><select id="active-project" value={projectId} onChange={e=>setProjectId(e.target.value)}>{projects.length===0?<option value="">No projects yet</option>:projects.map(p=><option value={p.id} key={p.id}>{p.name}</option>)}</select><ChevronDown size={15}/></div></div></div>
        {loading?<div className="loading"><RefreshCw className="spin" size={24}/> Loading your workspace…</div>:<>
          {tab==='overview'&&<Overview projects={projects} project={selected} data={details} go={go}/>}
          {tab==='projects'&&<Projects projects={projects} create={createProject} busy={busy}/>}
          {tab==='domains'&&<Domains key={projectId} project={selected} items={details.domains} busy={busy} notify={notify} create={body=>mutate(path+'/domains','POST',body,'Domain added')} verify={id=>mutate(path+'/domains/'+id+'/verify','POST',undefined,'DNS verification completed')}/>}
          {tab==='keys'&&<Keys key={projectId} project={selected} items={details.keys} busy={busy} notify={notify} create={body=>mutate(path+'/keys','POST',body,'API key created')} revoke={id=>mutate(path+'/keys/'+id,'DELETE',undefined,'API key revoked')}/>}
          {tab==='webhooks'&&<Webhooks key={projectId} project={selected} items={details.webhooks} busy={busy} create={body=>mutate(path+'/webhooks','POST',body,'Webhook created')} disable={id=>mutate(path+'/webhooks/'+id,'DELETE',undefined,'Webhook disabled')}/>}
          {tab==='usage'&&<Usage project={selected} data={details.usage}/>}
          {tab==='account'&&<Account session={session} logout={logout}/>}
        </>}
        {fetching&&!loading&&<span className="sync-hint"><RefreshCw size={12} className="spin"/> Updating project data</span>}
        <footer className="footer"><span>© UnifyDesk Email • Independent email infrastructure</span><span>Designed for secure sending <ShieldCheck size={15}/></span></footer>
      </main>
    </div>
    {message&&<div className="toast" role="status"><i/>{message}<button aria-label="Dismiss notification" onClick={()=>setMessage('')}><X size={16}/></button></div>}
  </div>
}
export default function App(){
  const [session,setSession]=useState(readSession)
  useEffect(()=>{
    const end=()=>setSession(null)
    window.addEventListener('unifydesk-email-session-expired',end)
    return()=>window.removeEventListener('unifydesk-email-session-expired',end)
  },[])
  return session?<Dashboard session={session} onLogout={()=>setSession(null)}/>:<Login onLogin={setSession}/>
}
