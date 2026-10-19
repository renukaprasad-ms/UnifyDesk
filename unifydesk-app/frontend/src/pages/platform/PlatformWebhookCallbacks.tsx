import { useSnackbarState } from '../../components/feedback/useSnackbarState'
import { useEffect, useMemo, useState } from 'react'
import { FiChevronLeft, FiChevronRight, FiEye, FiRefreshCw, FiSearch, FiX } from 'react-icons/fi'
import { platformApi } from '../../features/platform/platformApi'
import type {
  Page,
  PlatformWebhookCallback,
  WebhookCallbackStatus,
} from '../../features/platform/types'
import StatusBadge from '../../components/ui/StatusBadge'
import { TableSkeleton } from '../../components/ui/Skeleton'

const formatDate = (value?: string | null) =>
  value
    ? new Intl.DateTimeFormat(undefined, {
        dateStyle: 'medium',
        timeStyle: 'medium',
      }).format(new Date(value))
    : '—'

const pretty = (value?: string | null) => {
  if (!value) return '—'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function DetailBlock({ label, value }: { label: string; value?: string | null }) {
  return (
    <div>
      <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-500">{label}</p>
      <pre className="mt-2 max-h-72 overflow-auto whitespace-pre-wrap break-words rounded-xl border border-slate-200 bg-slate-950 p-4 text-xs leading-5 text-slate-100">
        {pretty(value)}
      </pre>
    </div>
  )
}

export default function PlatformWebhookCallbacks() {
  const [data, setData] = useState<Page<PlatformWebhookCallback> | null>(null)
  const [selected, setSelected] = useState<PlatformWebhookCallback | null>(null)
  const [search, setSearch] = useState('')
  const [provider, setProvider] = useState('')
  const [status, setStatus] = useState<WebhookCallbackStatus | ''>('')
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [, setError] = useSnackbarState('', 'error')

  const load = async (targetPage = page) => {
    setLoading(true)
    setError('')
    try {
      setData(
        await platformApi.webhookCallbacks({
          search: search.trim(),
          provider,
          status,
          page: targetPage,
        }),
      )
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Unable to load webhook callbacks')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load(page)
  }, [page, provider, status])

  const callbacks = data?.content ?? []
  const totalPages = Math.max(data?.totalPages ?? 1, 1)
  const resultLabel = useMemo(() => {
    if (!data) return '—'
    return `${data.totalElements} ${data.totalElements === 1 ? 'callback' : 'callbacks'}`
  }, [data])

  const submitSearch = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (page === 0) void load(0)
    else setPage(0)
  }

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-[1500px]">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="eyebrow">Channel analytics</p>
            <h1 className="mt-2 text-2xl font-bold text-slate-950">Webhook callbacks</h1>
            <p className="mt-1 max-w-3xl text-sm text-slate-500">
              Inspect inbound provider callbacks, raw and decoded payloads, processing outcomes,
              errors and response status. Sensitive authentication values are redacted before
              storage.
            </p>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-sm font-medium text-slate-500">{resultLabel}</span>
            <button
              type="button"
              onClick={() => void load(page)}
              disabled={loading}
              className="secondary-button inline-flex items-center gap-2"
            >
              <FiRefreshCw aria-hidden="true" className={loading ? 'animate-spin' : ''} />
              Refresh
            </button>
          </div>
        </div>

        <div className="mt-6 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm shadow-slate-200/40">
          <div className="border-b border-slate-200 p-4 sm:p-5">
            <form
              className="grid gap-3 lg:grid-cols-[1fr_180px_180px_auto]"
              onSubmit={submitSearch}
            >
              <label className="relative block">
                <FiSearch
                  aria-hidden="true"
                  className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400"
                />
                <input
                  className="form-input w-full pl-10"
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder="Search event ID, callback type, endpoint or error"
                />
              </label>

              <select
                className="form-input"
                value={provider}
                onChange={(event) => {
                  setProvider(event.target.value)
                  setPage(0)
                }}
              >
                <option value="">All providers</option>
                <option value="GMAIL">Gmail</option>
                <option value="MICROSOFT">Microsoft</option>
                <option value="META_WHATSAPP">Meta WhatsApp</option>
                <option value="MSG91">MSG91</option>
              </select>

              <select
                className="form-input"
                value={status}
                onChange={(event) => {
                  setStatus(event.target.value as WebhookCallbackStatus | '')
                  setPage(0)
                }}
              >
                <option value="">All statuses</option>
                <option value="RECEIVED">Received</option>
                <option value="PROCESSED">Processed</option>
                <option value="IGNORED">Ignored</option>
                <option value="FAILED">Failed</option>
                <option value="REJECTED">Rejected</option>
              </select>

              <button className="primary-button min-w-24" type="submit" disabled={loading}>
                Search
              </button>
            </form>
          </div>



          <div className="overflow-x-auto">
            <table className="mobile-card-table min-w-full text-left">
              <thead className="bg-slate-50/80">
                <tr className="border-b border-slate-200 text-[11px] font-semibold uppercase tracking-wider text-slate-500">
                  <th className="px-5 py-3.5">Received</th>
                  <th className="px-5 py-3.5">Provider</th>
                  <th className="px-5 py-3.5">Callback</th>
                  <th className="px-5 py-3.5">Status</th>
                  <th className="px-5 py-3.5">HTTP</th>
                  <th className="px-5 py-3.5">Duration</th>
                  <th className="px-5 py-3.5 text-right">Details</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {loading && callbacks.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="p-4"><TableSkeleton rows={7} columns={7} /></td>
                  </tr>
                ) : callbacks.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="px-5 py-16 text-center">
                      <p className="text-sm font-semibold text-slate-800">No callbacks found</p>
                      <p className="mt-1 text-sm text-slate-500">
                        New provider callbacks will appear here after they reach the backend.
                      </p>
                    </td>
                  </tr>
                ) : (
                  callbacks.map((callback) => (
                    <tr key={callback.id} className="transition-colors hover:bg-slate-50/60">
                      <td className="px-5 py-4 text-sm text-slate-700" data-label="Received">
                        <div className="font-medium text-slate-900">
                          {formatDate(callback.receivedAt)}
                        </div>
                        <div className="mt-0.5 text-xs text-slate-500">ID {callback.id}</div>
                      </td>
                      <td className="px-5 py-4" data-label="Provider">
                        <span className="rounded-full bg-indigo-50 px-2.5 py-1 text-xs font-semibold text-indigo-700 ring-1 ring-inset ring-indigo-600/20">
                          {callback.providerType}
                        </span>
                      </td>
                      <td className="px-5 py-4" data-label="Callback">
                        <p className="text-sm font-semibold text-slate-900">
                          {callback.callbackType}
                        </p>
                        <p className="mt-0.5 max-w-[320px] truncate text-xs text-slate-500">
                          {callback.eventId ? `Event ${callback.eventId}` : callback.endpoint}
                        </p>
                      </td>
                      <td className="px-5 py-4" data-label="Status">
                        <StatusBadge status={callback.status}>{callback.status}</StatusBadge>
                      </td>
                      <td
                        className="px-5 py-4 text-sm font-medium text-slate-700"
                        data-label="HTTP"
                      >
                        {callback.httpStatus ?? '—'}
                      </td>
                      <td className="px-5 py-4 text-sm text-slate-600" data-label="Duration">
                        {callback.durationMs == null ? '—' : `${callback.durationMs} ms`}
                      </td>
                      <td className="px-5 py-4 text-right" data-label="Details">
                        <button
                          type="button"
                          onClick={() => setSelected(callback)}
                          className="secondary-button inline-flex items-center gap-2"
                        >
                          <FiEye aria-hidden="true" />
                          View
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          <div className="flex flex-col gap-3 border-t border-slate-200 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-sm text-slate-500">
              Page <span className="font-semibold text-slate-800">{(data?.number ?? 0) + 1}</span>{' '}
              of <span className="font-semibold text-slate-800">{totalPages}</span>
            </p>
            <div className="flex items-center gap-2">
              <button
                type="button"
                disabled={!data || data.first || loading}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
                className="secondary-button inline-flex items-center gap-2 disabled:cursor-not-allowed disabled:opacity-40"
              >
                <FiChevronLeft aria-hidden="true" />
                Previous
              </button>
              <button
                type="button"
                disabled={!data || data.last || loading}
                onClick={() => setPage((current) => current + 1)}
                className="secondary-button inline-flex items-center gap-2 disabled:cursor-not-allowed disabled:opacity-40"
              >
                Next
                <FiChevronRight aria-hidden="true" />
              </button>
            </div>
          </div>
        </div>
      </div>

      {selected && (
        <div className="mobile-modal-shell fixed inset-0 z-50 flex items-end justify-center bg-slate-950/45 p-0 sm:items-center sm:p-5">
          <button
            type="button"
            className="absolute inset-0 cursor-default"
            aria-label="Close callback details"
            onClick={() => setSelected(null)}
          />
          <div className="mobile-sheet relative z-10 max-h-[94vh] w-full max-w-5xl overflow-auto rounded-t-3xl bg-white shadow-2xl sm:rounded-3xl">
            <div className="sticky top-0 z-10 flex items-start justify-between gap-4 border-b border-slate-200 bg-white/95 px-5 py-4 backdrop-blur sm:px-6">
              <div>
                <p className="eyebrow">Webhook callback</p>
                <h2 className="mt-1 text-xl font-bold text-slate-950">{selected.callbackType}</h2>
                <p className="mt-1 text-sm text-slate-500">
                  {selected.providerType} · {formatDate(selected.receivedAt)}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setSelected(null)}
                className="secondary-button flex size-10 items-center justify-center p-0"
                aria-label="Close"
              >
                <FiX aria-hidden="true" />
              </button>
            </div>

            <div className="space-y-6 p-5 sm:p-6">
              <div className="grid gap-4 rounded-2xl border border-slate-200 bg-slate-50/70 p-4 sm:grid-cols-2 lg:grid-cols-4">
                <div>
                  <p className="text-xs font-semibold text-slate-500">Status</p>
                  <StatusBadge status={selected.status} className="mt-2">{selected.status}</StatusBadge>
                </div>
                <div>
                  <p className="text-xs font-semibold text-slate-500">HTTP response</p>
                  <p className="mt-2 text-sm font-bold text-slate-900">
                    {selected.httpStatus ?? '—'}
                  </p>
                </div>
                <div>
                  <p className="text-xs font-semibold text-slate-500">Duration</p>
                  <p className="mt-2 text-sm font-bold text-slate-900">
                    {selected.durationMs == null ? '—' : `${selected.durationMs} ms`}
                  </p>
                </div>
                <div>
                  <p className="text-xs font-semibold text-slate-500">Event ID</p>
                  <p className="mt-2 break-all text-sm font-bold text-slate-900">
                    {selected.eventId ?? '—'}
                  </p>
                </div>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <p className="text-xs font-semibold text-slate-500">Endpoint</p>
                  <p className="mt-1 break-all text-sm text-slate-900">
                    {selected.httpMethod} {selected.endpoint}
                  </p>
                </div>
                <div>
                  <p className="text-xs font-semibold text-slate-500">Remote address</p>
                  <p className="mt-1 text-sm text-slate-900">{selected.remoteAddress ?? '—'}</p>
                </div>
                <div>
                  <p className="text-xs font-semibold text-slate-500">Content type</p>
                  <p className="mt-1 break-all text-sm text-slate-900">
                    {selected.contentType ?? '—'}
                  </p>
                </div>
                <div>
                  <p className="text-xs font-semibold text-slate-500">Completed</p>
                  <p className="mt-1 text-sm text-slate-900">{formatDate(selected.completedAt)}</p>
                </div>
                <div className="sm:col-span-2">
                  <p className="text-xs font-semibold text-slate-500">Query string</p>
                  <p className="mt-1 break-all font-mono text-xs text-slate-700">
                    {selected.queryString ?? '—'}
                  </p>
                </div>
              </div>

              {selected.errorMessage && (
                <div className="rounded-xl border border-rose-200 bg-rose-50 p-4">
                  <p className="text-xs font-semibold uppercase tracking-wider text-rose-600">
                    Error
                  </p>
                  <p className="mt-2 whitespace-pre-wrap break-words text-sm text-rose-800">
                    {selected.errorMessage}
                  </p>
                </div>
              )}

              <DetailBlock label="Request headers" value={selected.requestHeaders} />
              <DetailBlock label="Raw payload" value={selected.payload} />
              <DetailBlock label="Decoded payload" value={selected.decodedPayload} />
              <DetailBlock label="Processing result" value={selected.processingResult} />
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
