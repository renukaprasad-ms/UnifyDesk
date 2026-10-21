import React,{useState} from 'react'
import { ArrowRight, ArrowUpRight, Globe2, LockKeyhole, Mail, ShieldCheck, Zap } from 'lucide-react'
import {Button,Field,Notice} from './components'
import {request,saveSession} from './api'

export default function Login({onLogin}) {
  const [activate,setActivate]=useState(false)
  const [fields,setFields]=useState({org:'',email:'',password:'',invite:'',newPassword:''})
  const [error,setError]=useState(''),[busy,setBusy]=useState(false)
  const set=(key,value)=>setFields(x=>({...x,[key]:value}))
  async function submit(e) {
    e.preventDefault();setBusy(true);setError('')
    try {
      if(activate) {
        await request('/activate','POST',{invitationToken:fields.invite.trim(),password:fields.newPassword})
        setActivate(false);setError('Invitation activated. Please sign in.')
      } else {
        // Snowflake organization IDs are 64-bit; avoid lossy JS Number coercion.
        if(!/^[1-9][0-9]*$/.test(fields.org.trim()))throw new Error('Enter a valid organization ID')
        const session=await request('/sessions','POST',{organizationId:fields.org.trim(),email:fields.email.trim(),password:fields.password})
        if(!session?.sessionToken)throw new Error('The server did not return a session token')
        saveSession(session);onLogin(session)
      }
    }catch(err){setError(err.message)}finally{setBusy(false)}
  }
  return <div className="auth-layout">
    <section className="auth-aside"><div className="brand light"><span className="brand-mark"><Mail size={22}/></span>unifydesk <strong>email</strong></div>
      <div className="auth-message"><span className="eyebrow inverse">EMAIL INFRASTRUCTURE, SIMPLIFIED</span><h1>Deliver with clarity.<br/><em>Grow with confidence.</em></h1><p>All your sender identity, domains, API credentials and delivery analytics in one beautiful workspace.</p><div className="benefits"><span><ShieldCheck size={19}/> Sender identity you control</span><span><Zap size={19}/> Built for API-first teams</span><span><Globe2 size={19}/> Infrastructure visibility</span></div></div>
      <div className="auth-aside-footer">UNIFYDESK EMAIL <span>Independent sending platform</span></div><div className="orb orb-one"/><div className="orb orb-two"/>
    </section>
    <section className="auth-main"><div className="auth-card"><div className="auth-mobile-brand"><Mail size={24}/> UnifyDesk Email</div><span className="eyebrow">CONTROL CENTER</span><h2>{activate?'Activate your account':'Welcome back'}</h2><p>{activate?'Use the invitation issued by your organization operator.':'Sign in to manage your independent email workspace.'}</p>
      <form onSubmit={submit}>
        {activate?<><Field label="Invitation token"><input required value={fields.invite} onChange={e=>set('invite',e.target.value)} placeholder="ude_inv_..." autoComplete="off"/></Field><Field label="Set password" hint="At least 12 characters"><input required minLength={12} maxLength={128} type="password" value={fields.newPassword} onChange={e=>set('newPassword',e.target.value)} placeholder="Create a strong password" autoComplete="new-password"/></Field></>:
        <><Field label="Organization ID"><input required inputMode="numeric" pattern="[1-9][0-9]*" value={fields.org} onChange={e=>set('org',e.target.value)} placeholder="Your organization ID"/></Field><Field label="Work email"><input required type="email" value={fields.email} onChange={e=>set('email',e.target.value)} placeholder="you@company.com" autoComplete="username"/></Field><Field label="Password"><input required type="password" value={fields.password} onChange={e=>set('password',e.target.value)} placeholder="Enter your password" autoComplete="current-password"/></Field></>}
        {error&&<Notice tone={error.startsWith('Invitation activated')?'info':'error'}>{error}</Notice>}
        <Button type="submit" disabled={busy} icon={ArrowRight}>{busy?'Please wait…':activate?'Activate account':'Sign in to console'}</Button>
      </form>
      <div className="auth-switch">{activate?'Have an active account?':'Have an invitation?'} <button type="button" onClick={()=>{setActivate(!activate);setError('')}}>{activate?'Sign in':'Activate your account'} <ArrowUpRight size={14}/></button></div>
      <p className="security-foot"><LockKeyhole size={15}/> Protected session · Scoped credentials</p>
    </div><div className="auth-disclaimer">Access is created by your organization operator. Public registration is not available yet.</div></section>
  </div>
}
