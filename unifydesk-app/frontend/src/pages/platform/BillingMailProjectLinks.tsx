import { useEffect, useMemo, useState } from 'react'
import { adminBillingApi } from '../../features/platformBilling/adminBillingApi'
import type {
  MailBillingProjectLink, MailBillingProjectLinkInput,
  MailBillingUsageTotals,
} from '../../features/platformBilling/types'

type Props = { companies: { companyId: string; name: string }[] }
const int = (value: number) => new Intl.NumberFormat('en-IN').format(value)
const inr = (micro: number) => new Intl.NumberFormat('en-IN', {
  style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 6,
}).format(micro / 1_000_000)
const datetime = (value: string | null) =>
  value ? new Date(value).toLocaleString('en-IN') : 'Not yet synchronized'

export default function BillingMailProjectLinks({ companies }: Props) {
  const [links, setLinks] = useState<MailBillingProjectLink[]>([])
  const [usage, setUsage] = useState<MailBillingUsageTotals[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [form, setForm] = useState<MailBillingProjectLinkInput>({
    companyId: '', mailProjectId: '', ownershipProof: '', ownershipVerified: false,
  })

  const reload = async () => {
    const to = new Date()
    const from = new Date(to.getTime() - 30 * 86400000)
    const [nextLinks, nextUsage] = await Promise.all([
      adminBillingApi.mailProjectLinks(),
      adminBillingApi.mailProjectUsage(from.toISOString(), to.toISOString()),
    ])
    setLinks(nextLinks)
    setUsage(nextUsage)
  }

  useEffect(() => {
    let active = true
    const to = new Date()
    const from = new Date(to.getTime() - 30 * 86400000)
    Promise.all([
      adminBillingApi.mailProjectLinks(),
      adminBillingApi.mailProjectUsage(from.toISOString(), to.toISOString()),
    ]).then(([nextLinks, nextUsage]) => {
      if (active) { setLinks(nextLinks); setUsage(nextUsage) }
    }).catch((cause: unknown) => {
      if (active) setError(cause instanceof Error ? cause.message : 'Unable to load Mail bridge')
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])

  const summary = useMemo(() => ({
    handoffs: usage.reduce((sum, item) => sum + item.handoffs, 0),
    externalPriceMicroInr: usage.reduce((sum, item) => sum + item.productPriceMicroInr, 0),
  }), [usage])

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError('')
    setNotice('')
    if (!form.ownershipVerified) {
      setError('Verify Mail project ownership before linking')
      return
    }
    setBusy('create')
    try {
      const saved = await adminBillingApi.linkMailProject(form)
      setNotice(`Mail project ${saved.mailProjectId} is linked for report-only usage. Existing Mail handoffs are not retrospectively imported.`)
      setForm({ companyId: '', mailProjectId: '', ownershipProof: '', ownershipVerified: false })
      await reload()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to link Mail project')
    } finally { setBusy('') }
  }

  const sync = async (projectId: string) => {
    setBusy(projectId)
    setError('')
    setNotice('')
    try {
      const result = await adminBillingApi.syncMailProject(projectId)
      await reload()
      setNotice(`Project ${projectId}: ${int(result.imported)} new handoffs imported.${result.hasMore ? ' More pages remain: synchronize again or let the scheduler continue.' : ''} No charges were created.`)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to sync Mail project')
    } finally { setBusy('') }
  }

  const toggle = async (link: MailBillingProjectLink) => {
    setBusy(link.mailProjectId)
    setError('')
    try {
      await adminBillingApi.changeMailProjectState(
        link.mailProjectId, link.state === 'ACTIVE' ? 'PAUSED' : 'ACTIVE',
      )
      await reload()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to update Mail project sync state')
    } finally { setBusy('') }
  }

  return <section className="mt-6 space-y-5" aria-label="UnifyDesk Mail billing bridge">
    <div className="panel p-5">
      <h2 className="font-bold">UnifyDesk Mail project integration</h2>
      <p className="mt-2 text-sm text-slate-600">
        UnifyDesk Mail has independent organization and project identities. A Super Admin must
        verify the owner and map each Mail project to the correct company.
        Only accepted recipient handoffs after linking are imported.
      </p>
      <p className="mt-2 text-xs font-semibold text-amber-700">
        Reporting only: this bridge never creates billing charges or invoices. Mail product
        amounts remain separate from managed email revenue.
      </p>
      {error && <p className="mt-4 text-sm text-red-700" role="alert">{error}</p>}
      {notice && <p className="mt-4 text-sm text-emerald-700" role="status">{notice}</p>}
      <form className="mt-5 grid gap-4 lg:grid-cols-3" onSubmit={(event) => { void submit(event) }}>
        <label className="text-xs font-semibold">UnifyDesk company
          <select required className="form-input mt-1" value={form.companyId}
            onChange={(event) => setForm({ ...form, companyId: event.target.value })}>
            <option value="">Choose company</option>
            {companies.map((company) => <option value={company.companyId} key={company.companyId}>{company.name}</option>)}
          </select>
        </label>
        <label className="text-xs font-semibold">Mail project ID
          <input required className="form-input mt-1" inputMode="numeric" pattern="[0-9]+"
            placeholder="Exact Mail product project ID" value={form.mailProjectId}
            onChange={(event) => setForm({ ...form, mailProjectId: event.target.value })} />
        </label>
        <label className="text-xs font-semibold">Ownership verification reference
          <input required minLength={12} maxLength={500} className="form-input mt-1"
            placeholder="Ticket, approved onboarding record or contract" value={form.ownershipProof}
            onChange={(event) => setForm({ ...form, ownershipProof: event.target.value })} />
        </label>
        <label className="col-span-full flex items-start gap-2 text-xs">
          <input type="checkbox" checked={form.ownershipVerified}
            onChange={(event) => setForm({ ...form, ownershipVerified: event.target.checked })} />
          I independently verified that this Mail project belongs to the selected UnifyDesk company.
          I understand an incorrect mapping would misattribute usage.
        </label>
        <div className="col-span-full flex justify-end">
          <button className="primary-button" disabled={busy !== '' || !form.ownershipVerified || !companies.length}>
            {busy === 'create' ? 'Verifying Mail project...' : 'Verify and link for reporting'}
          </button>
        </div>
      </form>
    </div>
    <div className="panel p-5">
      <h3 className="font-semibold">Mail traffic (last 30 days)</h3>
      <p className="mt-2 text-xs text-slate-500">
        Remote Mail product price is a source-ledger reference, not a UnifyDesk invoice or a received payment.
      </p>
      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        <div className="rounded-xl border p-4">
          <p className="text-xs text-slate-500">Imported accepted recipient handoffs</p>
          <p className="mt-2 text-2xl font-bold tabular-nums">{int(summary.handoffs)}</p>
        </div>
        <div className="rounded-xl border p-4">
          <p className="text-xs text-slate-500">Standalone Mail ledger price (informational)</p>
          <p className="mt-2 text-2xl font-bold tabular-nums">{inr(summary.externalPriceMicroInr)}</p>
        </div>
      </div>
      {loading ? <p className="mt-4 text-sm">Loading Mail project links...</p>
        : <div className="mt-5 overflow-x-auto">
          <table className="w-full min-w-[860px] text-left text-sm">
            <thead><tr className="border-b text-xs text-slate-500">
              <th className="py-3 pr-3">Company</th><th className="px-3">Mail project ID</th>
              <th className="px-3">Mode / State</th><th className="px-3">Last sync</th>
              <th className="px-3 text-right">30-day handoffs</th><th className="px-3 text-right">Actions</th>
            </tr></thead>
            <tbody>{links.map((link) => {
              const matched = usage.find((row) => row.mailProjectId === link.mailProjectId)
              return <tr className="border-b" key={link.mailProjectId}>
                <td className="py-3 pr-3">{companies.find((c) => c.companyId === link.companyId)?.name ?? `Company ${link.companyId}`}</td>
                <td className="px-3 font-mono text-xs">{link.mailProjectId}</td>
                <td className="px-3 text-xs">{link.mode} · {link.state}</td>
                <td className="px-3 text-xs">{datetime(link.lastSyncedAt)}</td>
                <td className="px-3 text-right tabular-nums">{int(matched?.handoffs ?? 0)}</td>
                <td className="px-3 text-right">
                  <div className="flex justify-end gap-2">
                    <button className="secondary-button" type="button"
                      disabled={busy !== '' || link.state !== 'ACTIVE'}
                      onClick={() => { void sync(link.mailProjectId) }}>
                      {busy === link.mailProjectId ? 'Working...' : 'Sync'}
                    </button>
                    <button className="secondary-button" type="button" disabled={busy !== ''}
                      onClick={() => { void toggle(link) }}>
                      {link.state === 'ACTIVE' ? 'Pause' : 'Resume'}
                    </button>
                  </div>
                </td>
              </tr>
            })}</tbody>
          </table>
          {!links.length && <p className="py-5 text-sm text-slate-500">
            No Mail projects linked. Link an independently verified project to start counting future handoffs.
          </p>}
        </div>}
    </div>
  </section>
}
