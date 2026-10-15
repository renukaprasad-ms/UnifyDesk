import { useEffect, useState } from 'react'
import {
  FiX,
  FiCalendar,
  FiPlus,
  FiTrash2,
  FiEdit2,
  FiRefreshCw,
  FiToggleLeft,
  FiToggleRight,
  FiClock,
  FiVideo,
  FiCopy,
  FiCheck,
  FiExternalLink,
  FiAlertCircle,
  FiCheckCircle,
} from 'react-icons/fi'
import {
  crmBookingsApi,
  type BookingType,
  type MeetingBooking,
  type SaveBookingTypeInput,
} from '../../features/contacts/crmBookingsApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import WorkspaceDialog from '../ui/WorkspaceDialog'

interface BookingManagerModalProps {
  companyId: string
  onClose: () => void
}

export default function BookingManagerModal({
  companyId,
  onClose,
}: BookingManagerModalProps) {
  const [activeTab, setActiveTab] = useState<'types' | 'bookings'>('types')
  const [types, setTypes] = useState<BookingType[]>([])
  const [bookings, setBookings] = useState<MeetingBooking[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [feedback, setFeedback] = useState('')
  const [copiedSlug, setCopiedSlug] = useState<string | null>(null)

  // Add / Edit Type dialog
  const [editingType, setEditingType] = useState<BookingType | null>(null)
  const [showAddType, setShowAddType] = useState(false)

  // Form states
  const [title, setTitle] = useState('')
  const [slug, setSlug] = useState('')
  const [desc, setDesc] = useState('')
  const [durationMinutes, setDurationMinutes] = useState<number>(30)
  const [locationType, setLocationType] = useState('GOOGLE_MEET')
  const [locationDetails, setLocationDetails] = useState('')
  const [workingHoursStart, setWorkingHoursStart] = useState('09:00')
  const [workingHoursEnd, setWorkingHoursEnd] = useState('18:00')
  const [bufferAfterMinutes, setBufferAfterMinutes] = useState<number>(10)

  useEffect(() => {
    loadAll()
  }, [companyId])

  async function loadAll() {
    if (!companyId) return
    setLoading(true)
    setError('')
    try {
      const [t, b] = await Promise.all([
        crmBookingsApi.listTypes(companyId),
        crmBookingsApi.listBookings(companyId, undefined, undefined, 50),
      ])
      setTypes(t || [])
      setBookings(b || [])
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to load meeting scheduling details'))
    } finally {
      setLoading(false)
    }
  }

  function openNewType() {
    setEditingType(null)
    setTitle('')
    setSlug('')
    setDesc('')
    setDurationMinutes(30)
    setLocationType('GOOGLE_MEET')
    setLocationDetails('')
    setWorkingHoursStart('09:00')
    setWorkingHoursEnd('18:00')
    setBufferAfterMinutes(10)
    setShowAddType(true)
  }

  function openEditType(t: BookingType) {
    setEditingType(t)
    setTitle(t.title)
    setSlug(t.slug)
    setDesc(t.description || '')
    setDurationMinutes(t.durationMinutes)
    setLocationType(t.locationType)
    setLocationDetails(t.locationDetails || '')
    setWorkingHoursStart(t.workingHoursStart)
    setWorkingHoursEnd(t.workingHoursEnd)
    setBufferAfterMinutes(t.bufferAfterMinutes)
    setShowAddType(true)
  }

  async function handleToggle(t: BookingType) {
    if (!companyId || busy) return
    setBusy(true)
    try {
      const updated = await crmBookingsApi.toggleType(companyId, t.id, !t.isActive)
      setTypes((prev) => prev.map((item) => (item.id === t.id ? updated : item)))
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to toggle booking link status'))
    } finally {
      setBusy(false)
    }
  }

  async function handleDelete(id: string) {
    if (!companyId || busy) return
    if (!window.confirm('Delete this booking type? Existing scheduled meetings will be retained.')) return
    setBusy(true)
    try {
      await crmBookingsApi.deleteType(companyId, id)
      setTypes((prev) => prev.filter((item) => item.id !== id))
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to delete booking type'))
    } finally {
      setBusy(false)
    }
  }

  async function handleCancelBooking(id: string) {
    if (!companyId || busy) return
    if (!window.confirm('Cancel this scheduled meeting?')) return
    setBusy(true)
    try {
      await crmBookingsApi.cancelBooking(companyId, id)
      setBookings((prev) =>
        prev.map((b) => (b.id === id ? { ...b, status: 'CANCELLED' } : b))
      )
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to cancel meeting'))
    } finally {
      setBusy(false)
    }
  }

  async function handleSave(e: React.FormEvent) {
    e.preventDefault()
    if (!companyId || busy) return
    setBusy(true)
    setError('')

    const payload: SaveBookingTypeInput = {
      title: title.trim(),
      slug: slug.trim() || undefined,
      description: desc.trim() || null,
      durationMinutes: Number(durationMinutes),
      locationType,
      locationDetails: locationDetails.trim() || null,
      workingHoursStart,
      workingHoursEnd,
      bufferAfterMinutes: Number(bufferAfterMinutes),
    }

    try {
      if (editingType) {
        await crmBookingsApi.updateType(companyId, editingType.id, payload)
      } else {
        await crmBookingsApi.createType(companyId, payload)
      }
      setShowAddType(false)
      await loadAll()
      setFeedback('Booking type saved successfully')
      setTimeout(() => setFeedback(''), 3000)
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to save booking type'))
    } finally {
      setBusy(false)
    }
  }

  function copyPublicLink(s: string) {
    const url = `${window.location.origin}/book/${s}`
    navigator.clipboard.writeText(url)
    setCopiedSlug(s)
    setTimeout(() => setCopiedSlug(null), 2500)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 overflow-y-auto">
      <div className="relative w-full max-w-4xl rounded-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-200 dark:border-slate-800 px-6 py-4 bg-slate-50/50 dark:bg-slate-900/50">
          <div className="flex items-center gap-2.5">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-violet-600/10 text-violet-600 dark:bg-violet-400/10 dark:text-violet-400">
              <FiCalendar className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900 dark:text-white flex items-center gap-2">
                Meeting Scheduler & Booking Links
                <span className="rounded-full bg-violet-100 dark:bg-violet-900/40 text-violet-700 dark:text-violet-300 text-xs px-2.5 py-0.5 font-medium">
                  Calendar Sync
                </span>
              </h2>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Share Calendly-like scheduling links with leads to automatically book calls and log CRM tasks
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="rounded-lg p-2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition"
          >
            <FiX className="h-5 w-5" />
          </button>
        </div>

        {/* Tab Navigation */}
        <div className="flex items-center justify-between border-b border-slate-200 dark:border-slate-800 px-6 py-2.5 bg-white dark:bg-slate-900">
          <div className="flex items-center gap-2">
            <button
              onClick={() => setActiveTab('types')}
              className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg transition ${
                activeTab === 'types'
                  ? 'bg-violet-50 dark:bg-violet-950/50 text-violet-600 dark:text-violet-400 border border-violet-200 dark:border-violet-800'
                  : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800'
              }`}
            >
              <FiCalendar className="h-3.5 w-3.5" />
              Booking Links ({types.length})
            </button>
            <button
              onClick={() => setActiveTab('bookings')}
              className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg transition ${
                activeTab === 'bookings'
                  ? 'bg-violet-50 dark:bg-violet-950/50 text-violet-600 dark:text-violet-400 border border-violet-200 dark:border-violet-800'
                  : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800'
              }`}
            >
              <FiClock className="h-3.5 w-3.5" />
              Scheduled Meetings ({bookings.length})
            </button>
          </div>

          <div className="flex items-center gap-2">
            {activeTab === 'types' && (
              <button
                onClick={openNewType}
                className="flex items-center gap-1.5 rounded-lg bg-violet-600 text-white px-3 py-1.5 text-xs font-medium hover:bg-violet-500 shadow-sm transition"
              >
                <FiPlus className="h-3.5 w-3.5" />
                New Booking Link
              </button>
            )}
            <button
              onClick={loadAll}
              disabled={loading || busy}
              className="p-1.5 rounded-lg text-slate-500 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition"
              title="Refresh"
            >
              <FiRefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
            </button>
          </div>
        </div>

        {/* Feedback & Error banners */}
        {feedback && (
          <div className="bg-emerald-50 dark:bg-emerald-950/50 text-emerald-700 dark:text-emerald-300 px-6 py-2 text-xs flex items-center gap-2 border-b border-emerald-200 dark:border-emerald-800">
            <FiCheckCircle className="h-4 w-4" />
            <span>{feedback}</span>
          </div>
        )}
        {error && (
          <div className="bg-rose-50 dark:bg-rose-950/50 text-rose-700 dark:text-rose-300 px-6 py-2 text-xs flex items-center gap-2 border-b border-rose-200 dark:border-rose-800">
            <FiAlertCircle className="h-4 w-4" />
            <span>{error}</span>
          </div>
        )}

        {/* Content Body */}
        <div className="flex-1 overflow-y-auto p-6 space-y-4">
          {loading && types.length === 0 ? (
            <div className="py-16 text-center text-sm text-slate-400">Loading schedules...</div>
          ) : activeTab === 'types' ? (
            types.length === 0 ? (
              <div className="rounded-xl border border-dashed border-slate-300 dark:border-slate-700 p-12 text-center">
                <FiCalendar className="mx-auto h-8 w-8 text-slate-400 mb-2" />
                <h4 className="text-sm font-semibold text-slate-700 dark:text-slate-300">
                  No Booking Links Created
                </h4>
                <p className="text-xs text-slate-400 mt-1 max-w-sm mx-auto">
                  Create shareable meeting links for sales discovery calls, demos, and consultations.
                </p>
                <button
                  onClick={openNewType}
                  className="mt-4 inline-flex items-center gap-1.5 rounded-lg bg-violet-600 text-white px-3.5 py-1.5 text-xs font-medium hover:bg-violet-500 shadow-sm"
                >
                  <FiPlus className="h-3.5 w-3.5" />
                  Create First Booking Link
                </button>
              </div>
            ) : (
              <div className="space-y-3">
                {types.map((t) => (
                  <div
                    key={t.id}
                    className={`rounded-xl border transition p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-4 ${
                      t.isActive
                        ? 'border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900/60 shadow-sm'
                        : 'border-slate-200 dark:border-slate-800 bg-slate-50/60 dark:bg-slate-900/30 opacity-60'
                    }`}
                  >
                    <div className="space-y-1.5 flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="font-semibold text-sm text-slate-900 dark:text-white">
                          {t.title}
                        </span>
                        <span className="rounded-full bg-violet-50 dark:bg-violet-950/60 text-violet-700 dark:text-violet-400 text-[10px] font-medium px-2 py-0.5 border border-violet-200 dark:border-violet-800">
                          ⏱️ {t.durationMinutes}m
                        </span>
                        <span className="rounded-full bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 text-[10px] font-medium px-2 py-0.5">
                          {t.locationType === 'GOOGLE_MEET' ? '🎥 Google Meet' : t.locationType}
                        </span>
                      </div>

                      {t.description && (
                        <p className="text-xs text-slate-500 dark:text-slate-400 truncate">
                          {t.description}
                        </p>
                      )}

                      <div className="flex items-center gap-3 text-[11px] text-slate-400 pt-1">
                        <span>Host: <strong className="text-slate-600 dark:text-slate-300 font-medium">{t.hostName}</strong></span>
                        <span>•</span>
                        <span>Hours: {t.workingHoursStart} – {t.workingHoursEnd}</span>
                      </div>
                    </div>

                    <div className="flex items-center gap-2 shrink-0 self-end sm:self-center border-t sm:border-t-0 pt-2 sm:pt-0 border-slate-100 dark:border-slate-800">
                      <button
                        type="button"
                        onClick={() => copyPublicLink(t.slug)}
                        className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 px-2.5 py-1.5 text-xs font-medium text-slate-700 dark:text-slate-300 hover:bg-slate-100 transition"
                        title="Copy shareable link"
                      >
                        {copiedSlug === t.slug ? <FiCheck className="text-emerald-500" /> : <FiCopy />}
                        <span>{copiedSlug === t.slug ? 'Copied!' : 'Copy Link'}</span>
                      </button>

                      <a
                        href={`/book/${t.slug}`}
                        target="_blank"
                        rel="noreferrer"
                        className="rounded-lg p-2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition"
                        title="Open booking page"
                      >
                        <FiExternalLink className="h-4 w-4" />
                      </a>

                      <button
                        onClick={() => handleToggle(t)}
                        disabled={busy}
                        className={`text-2xl transition ${
                          t.isActive
                            ? 'text-violet-600 hover:text-violet-500'
                            : 'text-slate-300 dark:text-slate-600 hover:text-slate-400'
                        }`}
                        title={t.isActive ? 'Pause Booking Link' : 'Activate Booking Link'}
                      >
                        {t.isActive ? <FiToggleRight /> : <FiToggleLeft />}
                      </button>

                      <button
                        onClick={() => openEditType(t)}
                        className="rounded-lg p-2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition"
                        title="Edit Booking Type"
                      >
                        <FiEdit2 className="h-4 w-4" />
                      </button>

                      <button
                        onClick={() => handleDelete(t.id)}
                        className="rounded-lg p-2 text-slate-400 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-950/50 transition"
                        title="Delete Booking Type"
                      >
                        <FiTrash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )
          ) : (
            /* Scheduled Meetings Tab */
            bookings.length === 0 ? (
              <div className="rounded-xl border border-dashed border-slate-300 dark:border-slate-700 p-12 text-center text-xs text-slate-400">
                <FiClock className="mx-auto h-8 w-8 text-slate-300 mb-2" />
                No meetings have been booked yet. Share your booking links with prospects to schedule calls.
              </div>
            ) : (
              <div className="overflow-x-auto rounded-xl border border-slate-200 dark:border-slate-800">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-50 dark:bg-slate-800/60 text-slate-500 border-b border-slate-200 dark:border-slate-800">
                    <tr>
                      <th className="py-2.5 px-3 font-semibold">Date & Time</th>
                      <th className="py-2.5 px-3 font-semibold">Meeting</th>
                      <th className="py-2.5 px-3 font-semibold">Guest</th>
                      <th className="py-2.5 px-3 font-semibold">Host</th>
                      <th className="py-2.5 px-3 font-semibold">Status</th>
                      <th className="py-2.5 px-3 font-semibold text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                    {bookings.map((b) => (
                      <tr key={b.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30">
                        <td className="py-2.5 px-3 text-slate-900 dark:text-white font-medium whitespace-nowrap">
                          {new Date(b.startTime).toLocaleString(undefined, {
                            month: 'short',
                            day: 'numeric',
                            hour: '2-digit',
                            minute: '2-digit',
                          })}
                        </td>
                        <td className="py-2.5 px-3 font-medium text-slate-800 dark:text-slate-200 whitespace-nowrap">
                          {b.bookingTypeTitle}
                        </td>
                        <td className="py-2.5 px-3 whitespace-nowrap">
                          <p className="font-semibold text-slate-800 dark:text-slate-200">{b.guestName}</p>
                          <p className="text-[11px] text-slate-400">{b.guestEmail}</p>
                        </td>
                        <td className="py-2.5 px-3 text-slate-600 dark:text-slate-400 whitespace-nowrap">
                          {b.hostName}
                        </td>
                        <td className="py-2.5 px-3 whitespace-nowrap">
                          {b.status === 'CONFIRMED' ? (
                            <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-400 px-2 py-0.5 text-[10px] font-medium border border-emerald-200 dark:border-emerald-800">
                              Confirmed
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 dark:bg-slate-800 text-slate-500 px-2 py-0.5 text-[10px] font-medium">
                              {b.status}
                            </span>
                          )}
                        </td>
                        <td className="py-2.5 px-3 text-right whitespace-nowrap space-x-2">
                          {b.meetingLink && (
                            <a
                              href={b.meetingLink}
                              target="_blank"
                              rel="noreferrer"
                              className="inline-flex items-center gap-1 rounded-lg bg-violet-50 dark:bg-violet-950/50 text-violet-700 dark:text-violet-300 px-2 py-1 text-[11px] font-semibold hover:bg-violet-100 transition"
                            >
                              <FiVideo className="h-3 w-3" /> Join
                            </a>
                          )}
                          {b.status === 'CONFIRMED' && (
                            <button
                              onClick={() => handleCancelBooking(b.id)}
                              className="text-slate-400 hover:text-rose-600 text-[11px] transition"
                            >
                              Cancel
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
          )}
        </div>

        {/* Add / Edit Booking Type Submodal */}
        {showAddType && (
          <WorkspaceDialog
            title={editingType ? 'Edit Booking Type' : 'New Shareable Booking Link'}
            wide
            busy={busy}
            error={error}
            onClose={() => setShowAddType(false)}
          >
            <form onSubmit={handleSave} className="space-y-4">
              <div className="grid gap-4 sm:grid-cols-2">
                <label className="text-xs font-semibold text-slate-700 dark:text-slate-300 sm:col-span-2">
                  Meeting Title *
                  <input
                    required
                    maxLength={140}
                    className="form-input mt-1.5 w-full text-xs"
                    placeholder="e.g. 30 Min Discovery Call, Product Demo"
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                  />
                </label>

                <label className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                  Custom URL Slug (Optional)
                  <input
                    maxLength={60}
                    className="form-input mt-1.5 w-full text-xs"
                    placeholder="e.g. discovery-call"
                    value={slug}
                    onChange={(e) => setSlug(e.target.value)}
                  />
                  <span className="text-[11px] font-normal text-slate-400">
                    Will be available at /book/{slug || 'title'}
                  </span>
                </label>

                <label className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                  Duration (Minutes) *
                  <select
                    className="form-input mt-1.5 w-full text-xs"
                    value={durationMinutes}
                    onChange={(e) => setDurationMinutes(Number(e.target.value))}
                  >
                    <option value={15}>15 Minutes</option>
                    <option value={30}>30 Minutes</option>
                    <option value={45}>45 Minutes</option>
                    <option value={60}>60 Minutes (1 Hour)</option>
                    <option value={90}>90 Minutes</option>
                  </select>
                </label>

                <label className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                  Location Type
                  <select
                    className="form-input mt-1.5 w-full text-xs"
                    value={locationType}
                    onChange={(e) => setLocationType(e.target.value)}
                  >
                    <option value="GOOGLE_MEET">🎥 Google Meet (Auto-generated link)</option>
                    <option value="ZOOM">📹 Zoom</option>
                    <option value="PHONE">📞 Phone Call</option>
                    <option value="IN_PERSON">🏢 In-Person Meeting</option>
                  </select>
                </label>

                <label className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                  Buffer After Meeting (Minutes)
                  <select
                    className="form-input mt-1.5 w-full text-xs"
                    value={bufferAfterMinutes}
                    onChange={(e) => setBufferAfterMinutes(Number(e.target.value))}
                  >
                    <option value={0}>0 Minutes</option>
                    <option value={5}>5 Minutes</option>
                    <option value={10}>10 Minutes</option>
                    <option value={15}>15 Minutes</option>
                  </select>
                </label>

                <div className="grid grid-cols-2 gap-2 sm:col-span-2">
                  <label className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                    Working Hours Start
                    <input
                      type="time"
                      required
                      className="form-input mt-1.5 w-full text-xs"
                      value={workingHoursStart}
                      onChange={(e) => setWorkingHoursStart(e.target.value)}
                    />
                  </label>
                  <label className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                    Working Hours End
                    <input
                      type="time"
                      required
                      className="form-input mt-1.5 w-full text-xs"
                      value={workingHoursEnd}
                      onChange={(e) => setWorkingHoursEnd(e.target.value)}
                    />
                  </label>
                </div>

                <label className="text-xs font-semibold text-slate-700 dark:text-slate-300 sm:col-span-2">
                  Description / Welcome Notes (Optional)
                  <textarea
                    rows={2}
                    className="form-input mt-1.5 w-full text-xs"
                    placeholder="Briefly describe what this meeting is about..."
                    value={desc}
                    onChange={(e) => setDesc(e.target.value)}
                  />
                </label>
              </div>

              <div className="flex items-center justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowAddType(false)}
                  className="rounded-lg border border-slate-200 dark:border-slate-700 px-4 py-2 text-xs font-medium text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800"
                >
                  Cancel
                </button>
                <button type="submit" disabled={busy} className="primary-button text-xs py-2 px-4">
                  {busy ? 'Saving...' : editingType ? 'Update Link' : 'Create Booking Link'}
                </button>
              </div>
            </form>
          </WorkspaceDialog>
        )}
      </div>
    </div>
  )
}
