import React, { useEffect, useState } from 'react'
import { request } from './api'
import { Button, Field, Notice, Panel } from './components'

const PERMISSIONS = {
  MANAGE_PROJECTS: 'Manage projects', MANAGE_DOMAINS: 'Manage sending domains',
  MANAGE_API_KEYS: 'Manage API keys', MANAGE_WEBHOOKS: 'Manage webhooks', MANAGE_MAILBOXES: 'Provision mailboxes',
}
export default function OrganizationRoles({ session }) {
  const [roles, setRoles] = useState([]), [users, setUsers] = useState([])
  const [name, setName] = useState(''), [editing, setEditing] = useState(null)
  const [permissions, setPermissions] = useState([]), [assignments, setAssignments] = useState({})
  const [password, setPassword] = useState(''), [code, setCode] = useState('')
  const [error, setError] = useState(''), [message, setMessage] = useState(''), [busy, setBusy] = useState(false)
  const reload = async () => {
    const [available, members] = await Promise.all([request('/iam/roles'), request('/iam/users')])
    setRoles(available); setUsers(members)
    setAssignments(Object.fromEntries(members.map(user => [user.id, user.customRoleId || ''])))
  }
  useEffect(() => { if (session.role === 'OWNER') reload().catch(failure => setError(failure.message)) }, [session.role])
  if (session.role !== 'OWNER') return null
  const run = async operation => {
    setBusy(true); setError(''); setMessage('')
    try {
      await operation(); await reload(); setPassword(''); setCode('')
      setMessage('Role saved. Affected users must sign in again. Mailbox content still requires an explicit grant.')
    } catch (failure) { setError(failure.message) }
    finally { setBusy(false) }
  }
  const proof = { password, code: code || null }
  return <Panel>
    <h3>Organization roles</h3>
    <p>Custom roles control organization management. Mailbox reading, sending and delegation use separate grants.</p>
    {error && <Notice tone="error">{error}</Notice>}{message && <Notice>{message}</Notice>}
    <Field label="Owner password"><input type="password" maxLength={128} autoComplete="current-password" value={password} onChange={event => setPassword(event.target.value)}/></Field>
    <Field label="Authenticator or unused recovery code"><input autoComplete="one-time-code" maxLength={80} value={code} onChange={event => setCode(event.target.value)}/></Field>
    <form onSubmit={event => { event.preventDefault(); run(async () => {
      await request('/iam/roles' + (editing ? '/' + editing : ''), editing ? 'PUT' : 'POST', { ...(editing ? {} : { name }), permissions, ...proof })
      setEditing(null); setName(''); setPermissions([])
    }) }}>
      <Field label="Role name"><input required maxLength={80} disabled={!!editing} value={name} onChange={event => setName(event.target.value)}/></Field>
      {Object.entries(PERMISSIONS).map(([permission, label]) => <label key={permission}>
        <input type="checkbox" checked={permissions.includes(permission)} onChange={event => setPermissions(previous => event.target.checked ? [...previous, permission] : previous.filter(item => item !== permission))}/>{label} </label>)}
      <Button disabled={busy || !password || !name.trim()} type="submit">{editing ? 'Update role' : 'Create role'}</Button>
      {editing && <Button type="button" variant="ghost" onClick={() => { setEditing(null); setName(''); setPermissions([]) }}>Cancel edit</Button>}
    </form>
    {roles.map(role => <div className="settings-row" key={role.id}><span><b>{role.name}</b><small>{role.permissions.map(permission => PERMISSIONS[permission]).join(' · ') || 'Read-only organization access'}</small></span>
      <Button variant="outline" onClick={() => { setEditing(role.id); setName(role.name); setPermissions(role.permissions) }}>Edit permissions</Button></div>)}
    <h4>Organization members</h4>
    {users.map(user => <div className="settings-row" key={user.id}><span><b>{user.email}</b><small>User ID: {user.id} · {user.status} · {user.customRoleId ? 'Custom role' : user.role}</small></span>
      {user.role === 'OWNER' ? <span>Owner</span> : <>
        <select aria-label={'Role for ' + user.email} value={assignments[user.id] || ''} onChange={event => setAssignments(previous => ({ ...previous, [user.id]: event.target.value }))}>
          <option value="">Viewer</option>{roles.map(role => <option key={role.id} value={role.id}>{role.name}</option>)}
        </select>
        <Button disabled={busy || !password || user.status !== 'ACTIVE'} onClick={() => run(() => request('/iam/users/' + user.id + '/role', 'PUT', { roleId: assignments[user.id] || null, ...proof }))}>Assign role</Button>
      </>}
    </div>)}
  </Panel>
}
