import React, { useEffect, useState } from 'react'
import { Button, Field, Notice, Panel } from './components'
import { request } from './api'

export default function MailboxAccess({ mailboxId, currentUserId, onCapability }) {
  const [grants, setGrants] = useState(null)
  const [target, setTarget] = useState('')
  const [permissions, setPermissions] = useState({ canRead: true, canSend: false, canDelegate: false })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [confirm, setConfirm] = useState(null)
  const reload = async () => {
    const items = await request('/mail/mailboxes/' + mailboxId + '/access')
    setGrants(items); onCapability(true)
  }
  useEffect(() => {
    let active = true
    setGrants(null); onCapability(false)
    request('/mail/mailboxes/' + mailboxId + '/access')
      .then(items => { if (active) { setGrants(items); onCapability(true) } })
      .catch(() => { if (active) onCapability(false) })
    return () => { active = false }
  }, [mailboxId, onCapability])
  const run = async operation => {
    setBusy(true); setError('')
    try { await operation(); await reload() }
    catch (failure) { setError(failure.message) }
    finally { setBusy(false) }
  }
  if (!grants) return null
  return <Panel>
    <h3>Mailbox access</h3>
    <p>Grant each person only the access they need. Delegates can share and organize this mailbox.</p>
    {error && <Notice tone="error">{error}</Notice>}
    {grants.map(grant => <div key={grant.userId} className="settings-row">
      <span><b>User {grant.userId}</b><small>{[
        grant.canRead && 'Read', grant.canSend && 'Send', grant.canDelegate && 'Delegate'
      ].filter(Boolean).join(' · ')}</small></span>
      {grant.userId !== String(currentUserId) && (confirm === grant.userId ?
        <><Button disabled={busy} onClick={() => run(async () => {
          await request('/mail/mailboxes/' + mailboxId + '/access/' + grant.userId, 'DELETE'); setConfirm(null)
        })}>Confirm revoke</Button><Button variant="ghost" onClick={() => setConfirm(null)}>Cancel</Button></> :
        <Button variant="outline" disabled={busy} onClick={() => setConfirm(grant.userId)}>Revoke</Button>)}
    </div>)}
    <form onSubmit={event => { event.preventDefault(); run(async () => {
      await request('/mail/mailboxes/' + mailboxId + '/access', 'PUT', { userId: target.trim(), ...permissions }); setTarget('')
    }) }}>
      <Field label="Organization user ID"><input required pattern="[1-9][0-9]{0,18}" maxLength={19} value={target}
        onChange={event => setTarget(event.target.value)}/></Field>
      {Object.entries({ canRead: 'Read messages', canSend: 'Compose and send', canDelegate: 'Manage mailbox access and folders' }).map(([permission, label]) =>
        <label key={permission}><input type="checkbox" checked={permissions[permission]}
          onChange={event => setPermissions(previous => ({ ...previous, [permission]: event.target.checked }))}/>{label} </label>)}
      <Button disabled={busy || !target.trim() || !Object.values(permissions).some(Boolean)} type="submit">Save access</Button>
    </form>
  </Panel>
}
