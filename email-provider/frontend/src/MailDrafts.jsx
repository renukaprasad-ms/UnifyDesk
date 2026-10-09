import React, {useEffect, useState} from 'react'
import {FilePenLine, Save, Trash2, RotateCcw} from 'lucide-react'
import {request} from './api'
import {Button, Empty, Field, Notice, Panel} from './components'

const EMPTY = {id: null, revision: null, to: '', cc: '', bcc: '',
  subject: '', bodyText: '', replyToMessageId: null}

function addresses(text) {
  return text.trim() ? text.split(',').map(value => value.trim()).filter(Boolean) : []
}
function fromDraft(draft) {
  return {id: draft.id, revision: draft.revision,
    to: (draft.to || []).join(', '),
    cc: (draft.cc || []).join(', '),
    bcc: (draft.bcc || []).join(', '),
    subject: draft.subject || '',
    bodyText: draft.bodyText || '',
    replyToMessageId: draft.replyToMessageId || null}
}

export default function MailDrafts({mailboxId, mailboxAddress, replySeed}) {
  const [drafts, setDrafts] = useState([])
  const [form, setForm] = useState(EMPTY)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [refresh, setRefresh] = useState(0)

  useEffect(() => {
    setForm(EMPTY)
    setDrafts([])
    setError('')
    setNotice('')
  }, [mailboxId])

  useEffect(() => {
    let active = true
    if (!mailboxId) return () => { active = false }
    request('/mail/mailboxes/' + mailboxId + '/drafts')
      .then(items => { if (active) setDrafts(Array.isArray(items) ? items : []) })
      .catch(failure => { if (active) setError(failure.message) })
    return () => { active = false }
  }, [mailboxId, refresh])

  useEffect(() => {
    if (!replySeed || !mailboxId) return
    const sender = replySeed.from || ''
    const bareSender = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(sender)
      ? sender : ''
    setForm({...EMPTY,
      to: bareSender,
      subject: /^re:/i.test(replySeed.subject || '') ? replySeed.subject :
        'Re: ' + (replySeed.subject || '(No subject)'),
      replyToMessageId: replySeed.id})
    setError('')
    setNotice(bareSender
      ? 'Reply draft prepared. Verify the recipient before saving.'
      : 'Reply draft prepared. Enter the recipient address before saving.')
  }, [replySeed, mailboxId])

  const held = drafts.some(item => item.id === form.id && item.state !== 'DRAFT')
  const set = (key, value) => setForm(current => ({...current, [key]: value}))
  const base = '/mail/mailboxes/' + mailboxId + '/drafts'

  function payload() {
    return {to: addresses(form.to), cc: addresses(form.cc), bcc: addresses(form.bcc),
      subject: form.subject, bodyText: form.bodyText,
      replyToMessageId: form.replyToMessageId}
  }

  async function perform(action, success) {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      const result = await action()
      setNotice(success)
      return result
    } catch (failure) {
      setError(failure.message)
      return null
    } finally {
      setBusy(false)
    }
  }

  async function save(event) {
    event.preventDefault()
    const body = payload()
    const result = await perform(
      () => form.id
        ? request(base + '/' + form.id, 'PUT', {revision: form.revision, draft: body})
        : request(base, 'POST', body),
      'Draft saved privately. No email has been sent.')
    if (result) {
      setForm(fromDraft(result))
      setRefresh(value => value + 1)
    }
  }

  async function openDraft(id) {
    const result = await perform(() => request(base + '/' + id), 'Draft loaded')
    if (result) setForm(fromDraft(result))
  }

  async function deleteDraft() {
    if (!form.id || !window.confirm('Permanently delete this unsent draft?')) return
    const id = form.id
    const result = await perform(async () => {
      await request(base + '/' + id, 'DELETE')
      return true
    }, 'Unsent draft deleted')
    if (result) {
      setForm(EMPTY)
      setRefresh(value => value + 1)
    }
  }

  return <Panel extra="mail-draft-panel">
    <div className="mail-card-head">
      <div><h3><FilePenLine size={19}/> Compose & drafts</h3>
        <p>Save plain-text drafts under {mailboxAddress}. Outgoing delivery is not enabled yet.</p>
      </div>
      <Button variant="outline" icon={RotateCcw} disabled={busy} onClick={() => {
        setForm(EMPTY); setError(''); setNotice('')
      }}>New draft</Button>
    </div>

    <div className="mail-draft-layout">
      <aside className="mail-draft-list">
        <strong>Saved drafts</strong>
        {!drafts.length ? <Empty title="No saved drafts" body="Create your first draft to keep it here."/> :
          drafts.map(item => <button type="button" key={item.id}
            className={'mail-draft-item ' + (item.id === form.id ? 'active' : '')}
            disabled={busy} onClick={() => openDraft(item.id)}>
            <span>{item.subject || '(No subject)'}</span>
            <small>{item.state === 'DRAFT' ? 'Unsent draft' : 'Held · not sent'}</small>
          </button>)}
      </aside>
      <form className="mail-draft-editor" onSubmit={save}>
        <div className="mail-draft-from"><strong>From</strong> <span>{mailboxAddress}</span></div>
        <Field label="To (comma-separated)"><input value={form.to} disabled={busy || held} maxLength={7000}
          placeholder="recipient@example.com" onChange={event => set('to', event.target.value)}/></Field>
        <Field label="Cc"><input value={form.cc} disabled={busy || held} maxLength={7000} onChange={event => set('cc', event.target.value)}/></Field>
        <Field label="Bcc"><input value={form.bcc} disabled={busy} maxLength={7000} onChange={event => set('bcc', event.target.value)}/></Field>
        <Field label="Subject"><input value={form.subject} disabled={busy || held} maxLength={998} onChange={event => set('subject', event.target.value)}/></Field>
        <Field label="Plain-text message"><textarea value={form.bodyText} disabled={busy || held}
          maxLength={65536} rows={9} placeholder="Write your message…"
          onChange={event => set('bodyText', event.target.value)}/></Field>
        {form.replyToMessageId && <p className="mail-muted">Replying to received message {form.replyToMessageId}</p>}
        {error && <Notice tone="error">{error}</Notice>}
        {notice && <Notice>{notice}</Notice>}
        <div className="mail-actions">
          <Button type="submit" disabled={busy || held} icon={Save}>
            {busy ? 'Saving…' : form.id ? 'Save changes' : 'Save draft'}
          </Button>
          {form.id && !held && <Button type="button" variant="danger" disabled={busy}
            icon={Trash2} onClick={deleteDraft}>Delete</Button>}
        </div>
        <p className="mail-compose-warning">These drafts are saved privately. No Send button is available until
          authenticated mailbox transport, delivery receipts and abuse protections pass QA.</p>
      </form>
    </div>
  </Panel>
}
