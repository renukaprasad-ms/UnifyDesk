import React, { useCallback, useEffect, useState } from 'react'
import { CheckCircle2, ClipboardCopy, Download, Inbox, MailPlus, RefreshCw, ShieldAlert, ShieldCheck } from 'lucide-react'
import { request, downloadMail } from './api'
import { Badge, Button, Empty, Field, Notice, Panel } from './components'
import './mail.css'

/**
 * Operator-controlled mail UI. Every request uses an authenticated console
 * session; there are no project keys, raw MIME HTML previews, or fake sends.
 */
export default function MailConsole({ session }) {
  const canManage = ['OWNER', 'ADMIN'].includes(session.role)
  const [domains, setDomains] = useState([])
  const [domainId, setDomainId] = useState('')
  const [mailboxes, setMailboxes] = useState([])
  const [mailboxId, setMailboxId] = useState('')
  const [folder, setFolder] = useState('INBOX')
  const [messages, setMessages] = useState([])
  const [cursor, setCursor] = useState(null)
  const [pages, setPages] = useState([])
  const [proof, setProof] = useState(null)
  const [domainName, setDomainName] = useState('')
  const [localPart, setLocalPart] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [status, setStatus] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [messageLoading, setMessageLoading] = useState(false)
  const [selectedMessage, setSelectedMessage] = useState(null)
  const [revision, setRevision] = useState(0)

  const currentDomain = domains.find(domain => domain.id === domainId)
  const currentMailbox = mailboxes.find(mailbox => mailbox.id === mailboxId)

  const reloadDomains = useCallback(async () => {
    const list = await request('/mail/domains')
    setDomains(Array.isArray(list) ? list : [])
    setDomainId(previous => list?.some(item => item.id === previous)
      ? previous : (list?.[0]?.id ?? ''))
  }, [])

  useEffect(() => {
    let active = true
    setLoading(true)
    reloadDomains().catch(failure => {
      if (active) setError(failure.message)
    }).finally(() => {
      if (active) setLoading(false)
    })
    return () => { active = false }
  }, [reloadDomains, revision])

  useEffect(() => {
    let active = true
    setMailboxId('')
    setMailboxes([])
    setMessages([])
    setCursor(null)
    setPages([])
    setSelectedMessage(null)
    if (!domainId) return () => { active = false }
    request('/mail/domains/' + domainId + '/mailboxes')
      .then(list => {
        if (!active) return
        const items = Array.isArray(list) ? list : []
        setMailboxes(items)
        setMailboxId(previous => items.some(mailbox => mailbox.id === previous)
          ? previous : (items[0]?.id ?? ''))
      })
      .catch(failure => { if (active) setError(failure.message) })
    return () => { active = false }
  }, [domainId, revision])

  useEffect(() => {
    let active = true
    setMessages([])
    setSelectedMessage(null)
    if (!mailboxId || currentMailbox?.status !== 'ACTIVE') return () => { active = false }
    setMessageLoading(true)
    const search = new URLSearchParams({ folder, limit: '30' })
    if (cursor) search.set('beforeId', cursor)
    request('/mail/mailboxes/' + mailboxId + '/messages?' + search)
      .then(list => { if (active) setMessages(Array.isArray(list) ? list : []) })
      .catch(failure => { if (active) setError(failure.message) })
      .finally(() => { if (active) setMessageLoading(false) })
    return () => { active = false }
  }, [mailboxId, currentMailbox?.status, folder, cursor, revision])

  const run = async (operation, successMessage) => {
    setBusy(true)
    setError('')
    setStatus('')
    try {
      const result = await operation()
      setStatus(successMessage)
      setRevision(value => value + 1)
      return result
    } catch (failure) {
      setError(failure.message)
      return null
    } finally {
      setBusy(false)
    }
  }

  async function createDomain(event) {
    event.preventDefault()
    const result = await run(() => request('/mail/domains', 'POST', {
      domain: domainName.trim()
    }), 'Domain staged. Publish the verification TXT record below.')
    if (result) {
      setProof(result)
      setDomainId(result.domain.id)
      setDomainName('')
    }
  }

  async function rotateChallenge() {
    const result = await run(() => request('/mail/domains/' + domainId + '/challenge', 'POST'),
      'New DNS proof issued. The previous TXT value is no longer valid.')
    if (result) setProof(result)
  }

  async function createMailbox(event) {
    event.preventDefault()
    const result = await run(() => request('/mail/domains/' + domainId + '/mailboxes', 'POST', {
      localPart: localPart.trim(), displayName: displayName.trim() || null
    }), 'Mailbox staged. It cannot receive mail until activated.')
    if (result) {
      setMailboxId(result.id)
      setLocalPart('')
      setDisplayName('')
    }
  }

  async function activateMailbox() {
    await run(() => request('/mail/mailboxes/' + mailboxId + '/activate', 'POST', {
      userId: String(session.userId)
    }), 'Mailbox activated for your account. Incoming SMTP remains separately gated.')
  }

  async function saveRaw(message) {
    setError('')
    setBusy(true)
    try {
      await downloadMail(mailboxId, message.id)
    } catch (failure) {
      setError(failure.message)
    } finally {
      setBusy(false)
    }
  }

  function changeFolder(next) {
    setFolder(next)
    setCursor(null)
    setPages([])
  }

  function nextPage() {
    if (messages.length < 30) return
    setPages(items => [...items, cursor])
    setCursor(messages[messages.length - 1].id)
  }

  function previousPage() {
    if (!pages.length) return
    setCursor(pages[pages.length - 1])
    setPages(items => items.slice(0, -1))
  }

  const copy = async value => {
    try {
      await navigator.clipboard.writeText(value)
      setStatus('DNS record copied')
    } catch {
      setError('Clipboard unavailable; select and copy the value manually.')
    }
  }

  return <section className="mail-workspace">
    <div className="mail-page-heading">
      <div>
        <span className="eyebrow">PRIVATE HOSTED MAIL · EARLY DEVELOPMENT</span>
        <h2>Business mailboxes</h2>
        <p>Set up domain ownership, prepare mailboxes and inspect verified messages. Live receiving stays disabled until security QA.</p>
      </div>
      <Button variant="outline" icon={RefreshCw} disabled={busy} onClick={() => setRevision(value => value + 1)}>Refresh</Button>
    </div>

    <div className="mail-alert" role="note">
      <ShieldAlert size={19}/>
      <div><strong>Development preview</strong><span>Domain verification does not configure MX records. Mail remains unavailable until inbound storage and scanner gates are approved.</span></div>
    </div>

    {error && <Notice tone="error">{error}</Notice>}
    {status && <Notice>{status}</Notice>}
    {loading ? <p className="mail-muted">Loading mail domains…</p> : <>
      <div className="mail-setup-grid">
        <Panel>
          <div className="mail-card-head"><div><h3>Hosted domains</h3><p>Organization-owned mail receiving domains</p></div><ShieldCheck size={19}/></div>
          <label className="field"><span>Current domain</span>
            <select value={domainId} onChange={event => { setDomainId(event.target.value); setProof(null) }} disabled={!domains.length}>
              {domains.length ? domains.map(d => <option key={d.id} value={d.id}>{d.name} · {d.status}</option>) : <option value="">No mail domain</option>}
            </select>
          </label>
          {currentDomain && <div className="mail-status"><span>{currentDomain.name}</span><Badge value={currentDomain.status}/></div>}
          {canManage && <form className="mail-mini-form" onSubmit={createDomain}>
            <Field label="Add domain"><input type="text" required maxLength={253} placeholder="example.com" value={domainName} onChange={event => setDomainName(event.target.value)}/></Field>
            <Button disabled={busy || !domainName.trim()} type="submit">Add domain</Button>
          </form>}
          {currentDomain?.status === 'PENDING' && canManage && <div className="mail-actions">
            <Button variant="outline" disabled={busy} onClick={() => run(() => request('/mail/domains/' + domainId + '/verify', 'POST'), 'DNS verification checked')}>Verify DNS</Button>
            <Button variant="ghost" disabled={busy} onClick={rotateChallenge}>New TXT proof</Button>
          </div>}
          {proof?.domain.id === domainId && <div className="mail-proof" aria-label="DNS TXT ownership proof">
            <strong>One-time TXT ownership record</strong>
            <small>Publish this record in your DNS provider. Store the value now; it is not returned again.</small>
            <label>Name <code>{proof.txtName}</code></label>
            <label>TXT value <code>{proof.txtValue}</code></label>
            <Button variant="outline" icon={ClipboardCopy} onClick={() => copy(proof.txtValue)}>Copy TXT value</Button>
          </div>}
        </Panel>
        <Panel>
          <div className="mail-card-head"><div><h3>Mailboxes</h3><p>Created disabled until an authorized owner activates them</p></div><MailPlus size={19}/></div>
          <label className="field"><span>Current mailbox</span>
            <select value={mailboxId} onChange={event => setMailboxId(event.target.value)} disabled={!mailboxes.length}>
              {mailboxes.length ? mailboxes.map(item => <option key={item.id} value={item.id}>{item.address} · {item.status}</option>) : <option value="">No mailbox</option>}
            </select>
          </label>
          {currentMailbox && <div className="mail-status"><span>{currentMailbox.address}</span><Badge value={currentMailbox.status}/></div>}
          {canManage && currentDomain && <form className="mail-mini-form" onSubmit={createMailbox}>
            <Field label="Mailbox name"><input required maxLength={64} placeholder="support" value={localPart} onChange={event => setLocalPart(event.target.value)}/></Field>
            <Field label="Display name"><input maxLength={160} placeholder="Support team" value={displayName} onChange={event => setDisplayName(event.target.value)}/></Field>
            <Button type="submit" disabled={busy || !localPart.trim()}>Create disabled mailbox</Button>
          </form>}
          {canManage && currentMailbox?.status === 'DISABLED' && currentDomain?.status === 'VERIFIED' && session.userId &&
            <div className="mail-actions"><Button disabled={busy} onClick={activateMailbox}>Assign to me and activate</Button></div>}
          {!mailboxes.length && <p className="mail-muted">Create a domain and then add its first mailbox.</p>}
        </Panel>
      </div>

      <Panel extra="mail-inbox-panel">
        <div className="mail-inbox-heading"><div><h3><Inbox size={19}/> Message center</h3><p>Messages are available only after scanning and per-user authorization.</p></div><span className="mail-muted">Max 30 per page</span></div>
        {!currentMailbox || currentMailbox.status !== 'ACTIVE' ?
          <Empty icon={Inbox} title="No active mailbox selected" body="Verify your domain and activate a mailbox to view its Inbox."/> :
          <div className="mail-reader">
            <div className="mail-folders" aria-label="Mail folders">
              {['INBOX','SPAM'].map(name => <button key={name} type="button" className={folder === name ? 'selected' : ''} onClick={() => changeFolder(name)}>{name === 'INBOX' ? 'Inbox' : 'Spam'}</button>)}
            </div>
            <div className="mail-message-list">
              {messageLoading ? <div className="mail-reader-empty">Loading messages…</div> :
                messages.length === 0 ? <div className="mail-reader-empty">No messages in this folder.</div> :
                messages.map(item => <button type="button" key={item.id} className={'mail-message-row ' + (selectedMessage?.id === item.id ? 'selected' : '')} onClick={() => setSelectedMessage(item)}>
                  <strong>{item.subject || '(No subject)'}</strong><span>{item.from || 'Unknown sender'}</span>
                  <small>{item.receivedAt ? new Date(item.receivedAt).toLocaleString() : '—'}</small>
                </button>)}
              <div className="mail-pager"><Button variant="ghost" disabled={!pages.length || messageLoading} onClick={previousPage}>Previous</Button><Button variant="ghost" disabled={messages.length < 30 || messageLoading} onClick={nextPage}>Next</Button></div>
            </div>
            <div className="mail-preview">
              {selectedMessage ? <>
                <span className="eyebrow">SAFE MESSAGE METADATA</span>
                <h3>{selectedMessage.subject || '(No subject)'}</h3>
                <p><strong>From:</strong> {selectedMessage.from || 'Unknown sender'}</p>
                <p><strong>Received:</strong> {selectedMessage.receivedAt ? new Date(selectedMessage.receivedAt).toLocaleString() : '—'}</p>
                <div className="mail-preview-placeholder"><ShieldCheck size={21}/><span>Untrusted email HTML is not displayed in this version. Download the original message to inspect it using a trusted email client.</span></div>
                <Button variant="outline" icon={Download} disabled={busy} onClick={() => saveRaw(selectedMessage)}>Download original (.eml)</Button>
              </> : <div className="mail-reader-empty">Select an email to view its details.</div>}
            </div>
          </div>}
      </Panel>
    </>}
  </section>
}
