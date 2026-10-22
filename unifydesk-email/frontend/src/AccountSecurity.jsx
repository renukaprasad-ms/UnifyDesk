import React, { useEffect, useState } from 'react'
import { Button, Field, Notice, Panel } from './components'
import { request, clearSession } from './api'

export default function AccountSecurity() {
  const [status, setStatus] = useState(null)
  const [sessions, setSessions] = useState([])
  const [password, setPassword] = useState('')
  const [replacement, setReplacement] = useState('')
  const [code, setCode] = useState('')
  const [enrollment, setEnrollment] = useState(null)
  const [codes, setCodes] = useState([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const refresh = async () => {
    const [security, active] = await Promise.all([request('/security'), request('/security/sessions')])
    setStatus(security); setSessions(Array.isArray(active) ? active : [])
  }
  useEffect(() => { refresh().catch(failure => setError(failure.message)) }, [])
  const endSession = () => {
    clearSession()
    window.dispatchEvent(new Event('unifydesk-email-session-expired'))
  }
  const run = async operation => {
    setBusy(true); setError(''); setMessage('')
    try { await operation(); setPassword(''); setCode('') }
    catch (failure) { setError(failure.message) }
    finally { setBusy(false) }
  }
  return <Panel>
    <h3>Account security</h3>
    {error && <Notice tone="error">{error}</Notice>}
    {message && <Notice>{message}</Notice>}
    {!status ? <p>Loading security settings…</p> : <>
      <p>Authenticator: {status.mfaEnabled ? 'Enabled' : 'Not enabled'}
        {status.mfaEnabled ? ' · ' + status.recoveryCodesRemaining + ' recovery codes remaining' : ''}</p>
      <Field label="Current password"><input type="password" autoComplete="current-password" maxLength={128}
        value={password} onChange={event => setPassword(event.target.value)}/></Field>
      <Field label="Authenticator or unused recovery code"><input autoComplete="one-time-code" maxLength={80}
        value={code} onChange={event => setCode(event.target.value)}/></Field>
      {!status.mfaEnabled && status.enrollmentEnabled && !enrollment &&
        <Button disabled={busy || !password} onClick={() => run(async () => {
          setEnrollment(await request('/security/mfa/enroll', 'POST', { password }))
        })}>Set up authenticator</Button>}
      {enrollment && <div className="mail-proof">
        <strong>Add this setup key to your authenticator app</strong>
        <code>{enrollment.secret}</code>
        <p>Enter its six-digit code above to finish. Setup expires in ten minutes.</p>
        <Button disabled={busy || !/^[0-9]{6}$/.test(code)} onClick={() => run(async () => {
          setCodes(await request('/security/mfa/confirm', 'POST', { code }))
          setEnrollment(null); await refresh()
        })}>Confirm authenticator</Button>
      </div>}
      {codes.length > 0 && <div className="mail-proof">
        <strong>Save these recovery codes privately. Each works once.</strong>
        <pre>{codes.join('\n')}</pre>
        <Button variant="outline" onClick={() => setCodes([])}>I have saved these codes</Button>
      </div>}
      {status.mfaEnabled && <Button variant="outline" disabled={busy || !password || !code}
        onClick={() => run(async () => {
          await request('/security/mfa/disable', 'POST', { password, code })
          setCodes([]); setMessage('Authenticator disabled. Other sessions were revoked.'); await refresh()
        })}>Disable authenticator</Button>}
      <form onSubmit={event => { event.preventDefault(); run(async () => {
        await request('/security/password', 'POST', { password, newPassword: replacement, code: code || null })
        endSession()
      }) }}>
        <Field label="New password" hint="Changing your password signs out every session.">
          <input required type="password" autoComplete="new-password" minLength={12} maxLength={128}
            value={replacement} onChange={event => setReplacement(event.target.value)}/>
        </Field>
        <Button type="submit" disabled={busy || !password || (status.mfaEnabled && !code)}>Change password</Button>
      </form>
      <h4>Active sessions ({sessions.length})</h4>
      {sessions.map(session => <p key={session.id}>{session.current ? 'This session' : 'Other session'} · Expires {new Date(session.expiresAt).toLocaleString()}</p>)}
      <Button variant="outline" disabled={busy || !password || (status.mfaEnabled && !code)} onClick={() => run(async () => {
        await request('/security/sessions/revoke', 'POST', { password, code: code || null }); endSession()
      })}>Sign out all sessions</Button>
    </>}
  </Panel>
}
