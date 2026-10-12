import React, {useEffect, useRef, useState} from 'react'
import {FilePenLine, Save, Trash2, RotateCcw, Send, RefreshCw} from 'lucide-react'
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
  const sendEnabled = import.meta.env.VITE_MAIL_SEND_UI_ENABLED === 'true'
  const submitKeys = useRef({})
  const [drafts, setDrafts] = useState([])
  const [outgoing, setOutgoing] = useState([])
  const [dirty, setDirty] = useState(false)
  const [form, setForm] = useState(EMPTY)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [refresh, setRefresh] = useState(0)

  useEffect(() => {
    setForm(EMPTY)
    setDrafts([])
    setOutgoing([])
    setDirty(false)
    submitKeys.current = {}
    setError('')
    setNotice('')
  }, [mailboxId])

  useEffect(() => {
    let active = true
    if (!mailboxId) return () => { active = false }
    Promise.all([
      request('/mail/mailboxes/' + mailboxId + '/drafts'),
      request('/mail/mailboxes/' + mailboxId + '/outgoing'),
    ]).then(([items, handoffs]) => {
      if (!active) return
      setDrafts(Array.isArray(items) ? items : [])
      setOutgoing(Array.isArray(handoffs) ? handoffs : [])
    }).catch(failure => { if (active) setError(failure.message) })
    return () => { active = false }
  }, [mailboxId, refresh])

  useEffect(() => {
    if (!replySeed || !mailboxId) return
    const sender = replySeed.from || ''
    const bareSender = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(sender)
      ? sender : ''
    setDirty(true)
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
  const set = (key, value) => {
    setDirty(true)
    setForm(current => ({...current, [key]: value}))
  }
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
      setDirty(false)
      setRefresh(value => value + 1)
    }
  }

  async function openDraft(id) {
    const result = await perform(() => request(base + '/' + id), 'Draft loaded')
    if (result) {
      setForm(fromDraft(result))
      setDirty(false)
    }
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
      setDirty(false)
      setRefresh(value => value + 1)
    }
  }

  async function sendDraft() {
    if (!sendEnabled || !form.id || dirty || held) return
    if (!window.confirm('Submit this saved draft for SMTP processing? This may send real email.')) return
    const id = form.id
    if (!submitKeys.current[id]) submitKeys.current[id] = crypto.randomUUID()
    const result = await perform(() => request(
      base + '/' + id + '/hold', 'POST', undefined, submitKeys.current[id]),
      'Accepted for SMTP processing. Delivery is not confirmed; check the outgoing status.')
    if (result) setRefresh(value => value + 1)
  }

  return <Panel extra="mail-draft-panel">
    <div className="mail-card-head">
      <div><h3><FilePenLine size={19}/> Compose & drafts</h3>
        <p>Compose plain-text mail under {mailboxAddress}. SMTP handoff is tracked separately from recipient delivery.</p>
      </div>
      <Button variant="outline" icon={RotateCcw} disabled={busy} onClick={() => {
        setForm(EMPTY); setDirty(false); setError(''); setNotice('')
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
            <small>{item.state === 'DRAFT' ? 'Unsent draft' : 'Submitted · see outgoing status'}</small>
          </button>)}
      </aside>
      <form className="mail-draft-editor" onSubmit={save}>
        <div className="mail-draft-from"><strong>From</strong> <span>{mailboxAddress}</span></div>
        <Field label="To (comma-separated)"><input value={form.to} disabled={busy || held} maxLength={7000}
          placeholder="recipient@example.com" onChange={event => set('to', event.target.value)}/></Field>
        <Field label="Cc"><input value={form.cc} disabled={busy || held} maxLength={7000} onChange={event => set('cc', event.target.value)}/></Field>
        <Field label="Bcc"><input value={form.bcc} disabled={busy || held} maxLength={7000} onChange={event => set('bcc', event.target.value)}/></Field>
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
          {sendEnabled && form.id && !held &&
            <Button type="button" icon={Send} disabled={busy || dirty}
              onClick={sendDraft}>Submit for sending</Button>}
        </div>
        <p className="mail-compose-warning">{sendEnabled
          ? 'Submission requires an approved mailbox domain and sends via a separate local relay. Accepted by SMTP does not prove inbox delivery. Save edits before sending.'
          : 'Mail sending remains unavailable until the dedicated mailbox relay and security checks pass QA.'}</p>
      </form>
    </div>
    <div className="mail-outgoing">
      <div className="mail-outgoing-heading">
        <strong>Outgoing &amp; handoff status</strong>
        <Button type="button" variant="ghost" icon={RefreshCw}
          disabled={busy} onClick={() => setRefresh(value => value + 1)}>Refresh</Button>
      </div>
      {!outgoing.length ? <p className="mail-muted">No submitted messages for this mailbox user.</p>
        : outgoing.map(item => <div className="mail-outgoing-entry" key={item.intentId}>
          <span>{item.subject || '(No subject)'}</span>
          <small>{item.state.replaceAll('_', ' ').toLowerCase()} ·
            {item.acceptedByPostfix} handed to Postfix ·
            {item.pending} pending · {item.failed} failed ·
            {item.unknown} uncertain · {item.remotelyDelivered} confirmed delivered ·
            {item.bounced} bounced · {item.complained} complaints</small>
        </div>)}
      <p className="mail-compose-warning">Postfix handoff is not recipient inbox delivery.
        UNKNOWN requires investigation; automatic retries are suppressed for uncertain outcomes.</p>
    </div>
  </Panel>
}
