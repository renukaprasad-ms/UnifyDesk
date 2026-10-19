import PhoneInput from '../../components/ui/PhoneInput'
import { useEffect, useState } from 'react'
import { useParams } from 'react-router'
import {
  FiCalendar,
  FiClock,
  FiVideo,
  FiUser,
  FiMail,
  FiPhone,
  FiCheckCircle,
  FiCopy,
  FiCheck,
  FiArrowLeft,
  FiAlertCircle,
  FiExternalLink,
} from 'react-icons/fi'
import {
  crmBookingsApi,
  type PublicBookingType,
  type BookResult,
} from '../../features/contacts/crmBookingsApi'
import { apiErrorMessage } from '../../lib/api/apiClient'

export default function PublicBookingPage() {
  const { slug } = useParams<{ slug: string }>()
  const [bookingType, setBookingType] = useState<PublicBookingType | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  // Date selection state
  const [selectedDate, setSelectedDate] = useState<string>(() => {
    const today = new Date()
    return today.toISOString().split('T')[0]
  })
  const [availableSlots, setAvailableSlots] = useState<string[]>([])
  const [slotsLoading, setSlotsLoading] = useState(false)
  const [selectedSlot, setSelectedSlot] = useState<string | null>(null)

  // Booking Form state
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [notes, setNotes] = useState('')
  const [bookingBusy, setBookingBusy] = useState(false)
  const [bookResult, setBookResult] = useState<BookResult | null>(null)
  const [copiedLink, setCopiedLink] = useState(false)

  // Load public booking profile
  useEffect(() => {
    if (!slug) return
    setLoading(true)
    setError('')
    crmBookingsApi
      .getPublicType(slug)
      .then((type) => {
        setBookingType(type)
      })
      .catch((err) => {
        setError(apiErrorMessage(err, 'Failed to load booking page or meeting link is inactive.'))
      })
      .finally(() => setLoading(false))
  }, [slug])

  // Fetch slots whenever selectedDate or slug changes
  useEffect(() => {
    if (!slug || !selectedDate) return
    setSlotsLoading(true)
    setSelectedSlot(null)
    crmBookingsApi
      .getAvailableSlots(slug, selectedDate)
      .then((slots) => {
        setAvailableSlots(slots || [])
      })
      .catch(() => {
        setAvailableSlots([])
      })
      .finally(() => setSlotsLoading(false))
  }, [slug, selectedDate])

  async function handleBook(e: React.FormEvent) {
    e.preventDefault()
    if (!slug || !selectedSlot || bookingBusy) return
    setBookingBusy(true)
    setError('')
    try {
      const result = await crmBookingsApi.bookMeeting(slug, {
        slotTime: selectedSlot,
        name: name.trim(),
        email: email.trim(),
        phone: phone.trim() || undefined,
        notes: notes.trim() || undefined,
      })
      setBookResult(result)
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to schedule meeting. This slot may have just been reserved.'))
    } finally {
      setBookingBusy(false)
    }
  }

  function formatTime(isoStr: string) {
    try {
      return new Date(isoStr).toLocaleTimeString([], {
        hour: '2-digit',
        minute: '2-digit',
      })
    } catch {
      return isoStr
    }
  }

  function formatDate(isoDate: string) {
    try {
      const d = new Date(isoDate + 'T00:00:00')
      return d.toLocaleDateString(undefined, {
        weekday: 'short',
        month: 'short',
        day: 'numeric',
      })
    } catch {
      return isoDate
    }
  }

  // Generate next 14 calendar days
  const nextDays = Array.from({ length: 14 }).map((_, i) => {
    const d = new Date()
    d.setDate(d.getDate() + i)
    return d.toISOString().split('T')[0]
  })

  function copyMeetingLink(link: string) {
    navigator.clipboard.writeText(link)
    setCopiedLink(true)
    setTimeout(() => setCopiedLink(false), 2500)
  }

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 dark:bg-slate-950 px-4">
        <div className="text-center space-y-3">
          <div className="inline-block h-8 w-8 animate-spin rounded-full border-4 border-violet-600 border-t-transparent" />
          <p className="text-sm font-medium text-slate-500">Loading meeting schedule...</p>
        </div>
      </div>
    )
  }

  if (error && !bookingType) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 dark:bg-slate-950 px-4">
        <div className="max-w-md w-full rounded-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-8 text-center shadow-lg">
          <FiAlertCircle className="mx-auto h-12 w-12 text-rose-500 mb-3" />
          <h2 className="text-lg font-bold text-slate-900 dark:text-white">Booking Link Unavailable</h2>
          <p className="mt-2 text-xs text-slate-500">{error}</p>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-950 py-12 px-4 sm:px-6 lg:px-8 flex flex-col justify-center">
      <div className="mx-auto w-full max-w-4xl">
        <div className="overflow-hidden rounded-3xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-xl grid md:grid-cols-12 min-h-[580px]">
          
          {/* Left Summary Sidebar */}
          <div className="md:col-span-4 p-8 border-b md:border-b-0 md:border-r border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/40 flex flex-col justify-between">
            <div className="space-y-4">
              <div className="flex items-center gap-3">
                <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-violet-600/10 text-violet-600 dark:bg-violet-400/10 dark:text-violet-400 font-bold text-lg">
                  {bookingType?.hostName ? bookingType.hostName.charAt(0).toUpperCase() : 'U'}
                </div>
                <div>
                  <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Host</p>
                  <p className="font-bold text-sm text-slate-900 dark:text-white">{bookingType?.hostName}</p>
                </div>
              </div>

              <div>
                <h1 className="text-xl font-bold text-slate-900 dark:text-white mt-1">
                  {bookingType?.title}
                </h1>
                {bookingType?.description && (
                  <p className="mt-2 text-xs text-slate-500 dark:text-slate-400 leading-relaxed">
                    {bookingType.description}
                  </p>
                )}
              </div>

              <div className="space-y-2.5 pt-2">
                <div className="flex items-center gap-2 text-xs font-medium text-slate-600 dark:text-slate-300">
                  <FiClock className="h-4 w-4 text-violet-500 shrink-0" />
                  <span>{bookingType?.durationMinutes} minutes duration</span>
                </div>
                <div className="flex items-center gap-2 text-xs font-medium text-slate-600 dark:text-slate-300">
                  <FiVideo className="h-4 w-4 text-violet-500 shrink-0" />
                  <span>
                    {bookingType?.locationType === 'GOOGLE_MEET'
                      ? 'Google Meet video conference'
                      : bookingType?.locationType === 'ZOOM'
                      ? 'Zoom meeting'
                      : bookingType?.locationType === 'PHONE'
                      ? 'Phone consultation'
                      : 'Video meeting'}
                  </span>
                </div>
              </div>
            </div>

            <div className="pt-8 border-t border-slate-200/60 dark:border-slate-800/60 mt-8">
              <p className="text-[11px] text-slate-400">
                Powered by <span className="font-semibold text-slate-600 dark:text-slate-300">UnifyDesk CRM</span>
              </p>
            </div>
          </div>

          {/* Right Main Interaction Area */}
          <div className="md:col-span-8 p-8 flex flex-col justify-center">
            {bookResult ? (
              /* Success Confirmation Card */
              <div className="text-center py-6 space-y-4 max-w-md mx-auto">
                <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-emerald-100 dark:bg-emerald-950/60 text-emerald-600 dark:text-emerald-400">
                  <FiCheckCircle className="h-9 w-9" />
                </div>
                <div>
                  <h3 className="text-xl font-bold text-slate-900 dark:text-white">
                    You're Scheduled!
                  </h3>
                  <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                    A calendar invitation and reminder have been created.
                  </p>
                </div>

                <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-900/60 p-5 text-left space-y-3">
                  <div className="flex items-center gap-3">
                    <FiCalendar className="h-5 w-5 text-violet-600 dark:text-violet-400" />
                    <div>
                      <p className="text-xs text-slate-400">Date & Time</p>
                      <p className="text-sm font-semibold text-slate-900 dark:text-white">
                        {new Date(bookResult.startTime).toLocaleString(undefined, {
                          weekday: 'short',
                          month: 'short',
                          day: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit',
                        })}
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center justify-between border-t border-slate-200 dark:border-slate-800 pt-3">
                    <div className="flex items-center gap-2 text-xs text-slate-600 dark:text-slate-300">
                      <FiVideo className="h-4 w-4 text-violet-500" />
                      <span className="truncate max-w-[200px]">{bookResult.meetingLink}</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => copyMeetingLink(bookResult.meetingLink)}
                        className="rounded-lg p-1.5 text-slate-400 hover:text-slate-600 hover:bg-slate-200 dark:hover:bg-slate-800 transition"
                        title="Copy meeting link"
                      >
                        {copiedLink ? <FiCheck className="text-emerald-500" /> : <FiCopy />}
                      </button>
                      <a
                        href={bookResult.meetingLink}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1 rounded-lg bg-violet-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-violet-500 transition"
                      >
                        Join Call <FiExternalLink className="h-3 w-3" />
                      </a>
                    </div>
                  </div>
                </div>

                <button
                  onClick={() => {
                    setBookResult(null)
                    setSelectedSlot(null)
                  }}
                  className="text-xs text-slate-500 hover:text-slate-700 dark:hover:text-slate-300 transition"
                >
                  ← Book another time slot
                </button>
              </div>
            ) : selectedSlot ? (
              /* Step 2: Guest Details Form */
              <form onSubmit={handleBook} className="space-y-4 max-w-md mx-auto w-full">
                <div className="flex items-center gap-2 text-xs text-slate-500 mb-2">
                  <button
                    type="button"
                    onClick={() => setSelectedSlot(null)}
                    className="flex items-center gap-1 font-medium text-violet-600 hover:underline"
                  >
                    <FiArrowLeft /> Back to slots
                  </button>
                  <span>•</span>
                  <span className="font-semibold text-slate-700 dark:text-slate-300">
                    {new Date(selectedSlot).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}, {formatDate(selectedDate)}
                  </span>
                </div>

                <h3 className="text-base font-bold text-slate-900 dark:text-white">
                  Enter Your Details
                </h3>

                {error && (
                  <div className="rounded-lg bg-rose-50 dark:bg-rose-950/50 p-3 text-xs text-rose-700 dark:text-rose-300 border border-rose-200 dark:border-rose-800 flex items-center gap-2">
                    <FiAlertCircle className="h-4 w-4 shrink-0" />
                    <span>{error}</span>
                  </div>
                )}

                <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
                  <span className="flex items-center gap-1.5">
                    <FiUser className="text-slate-400" />
                    Your Name *
                  </span>
                  <input
                    required
                    className="form-input mt-1.5 w-full text-xs"
                    placeholder="e.g. John Doe"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                  />
                </label>

                <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
                  <span className="flex items-center gap-1.5">
                    <FiMail className="text-slate-400" />
                    Email Address *
                  </span>
                  <input
                    type="email"
                    required
                    className="form-input mt-1.5 w-full text-xs"
                    placeholder="john@example.com"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                  />
                </label>

                <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
                  <span className="flex items-center gap-1.5">
                    <FiPhone className="text-slate-400" />
                    Phone Number (Optional)
                  </span>
                  <PhoneInput
                    className="form-input mt-1.5 w-full text-xs"
                    placeholder="+91 98765 43210"
                    value={phone}
                    onChange={(value) => setPhone(value)}
                  />
                </label>

                <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
                  Notes or Agenda for Meeting (Optional)
                  <textarea
                    rows={2}
                    className="form-input mt-1.5 w-full text-xs"
                    placeholder="What would you like to discuss?"
                    value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                  />
                </label>

                <button
                  type="submit"
                  disabled={bookingBusy}
                  className="w-full rounded-xl bg-violet-600 hover:bg-violet-500 py-3 text-xs font-bold text-white shadow-md transition disabled:opacity-50"
                >
                  {bookingBusy ? 'Scheduling Meeting...' : 'Confirm Booking'}
                </button>
              </form>
            ) : (
              /* Step 1: Date & Slot Picker */
              <div className="space-y-6">
                <div>
                  <h3 className="text-sm font-bold text-slate-900 dark:text-white flex items-center gap-2">
                    <FiCalendar className="text-violet-600 dark:text-violet-400" />
                    Select a Date
                  </h3>
                  
                  {/* Horizontal Date Picker Strip */}
                  <div className="flex gap-2 overflow-x-auto pb-2 pt-3 scrollbar-none">
                    {nextDays.map((dStr) => {
                      const isSelected = dStr === selectedDate
                      const dateObj = new Date(dStr + 'T00:00:00')
                      const dayName = dateObj.toLocaleDateString(undefined, { weekday: 'short' })
                      const dayNum = dateObj.getDate()
                      return (
                        <button
                          key={dStr}
                          type="button"
                          onClick={() => setSelectedDate(dStr)}
                          className={`flex min-w-[62px] flex-col items-center rounded-xl p-2.5 text-center transition border ${
                            isSelected
                              ? 'border-violet-600 bg-violet-600 text-white shadow-md'
                              : 'border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 text-slate-700 dark:text-slate-300 hover:border-violet-300'
                          }`}
                        >
                          <span className={`text-[10px] font-medium uppercase ${isSelected ? 'text-violet-100' : 'text-slate-400'}`}>
                            {dayName}
                          </span>
                          <span className="text-base font-bold mt-0.5">{dayNum}</span>
                        </button>
                      )
                    })}
                  </div>
                </div>

                {/* Available Slots */}
                <div>
                  <h3 className="text-sm font-bold text-slate-900 dark:text-white flex items-center gap-2 mb-3">
                    <FiClock className="text-violet-600 dark:text-violet-400" />
                    Available Times for {formatDate(selectedDate)}
                  </h3>

                  {slotsLoading ? (
                    <div className="py-8 text-center text-xs text-slate-400">Loading open slots...</div>
                  ) : availableSlots.length === 0 ? (
                    <div className="rounded-xl border border-dashed border-slate-200 dark:border-slate-800 p-8 text-center text-xs text-slate-400">
                      No available booking slots on this date. Please try another day.
                    </div>
                  ) : (
                    <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5 max-h-[260px] overflow-y-auto pr-1">
                      {availableSlots.map((iso) => (
                        <button
                          key={iso}
                          type="button"
                          onClick={() => setSelectedSlot(iso)}
                          className="rounded-xl border border-violet-200 dark:border-violet-800 bg-violet-50/50 dark:bg-violet-950/30 py-2.5 px-3 text-xs font-semibold text-violet-700 dark:text-violet-300 hover:bg-violet-600 hover:text-white hover:border-violet-600 transition"
                        >
                          {formatTime(iso)}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>

        </div>
      </div>
    </div>
  )
}
