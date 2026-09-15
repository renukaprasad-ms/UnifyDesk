import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { companyApi } from '../../features/company/companyApi'

export default function CreateWorkspace() {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [businessEmail, setBusinessEmail] = useState('')
  const [country, setCountry] = useState('India')
  const [timezone, setTimezone] = useState('Asia/Kolkata')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      const company = await companyApi.create({
        name,
        businessEmail,
        country,
        timezone,
      })
      navigate(`/select-plan/${company.companyId}`, { replace: true })
    } catch (submitError) {
      setError(apiErrorMessage(submitError, 'Unable to create workspace'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="min-h-dvh bg-white px-6 py-12 sm:py-20">
      <main className="mx-auto w-full max-w-[358px]">
        <h1 className="text-[26px] font-bold leading-tight text-slate-950">Create your workspace</h1>
        <p className="mt-2 text-xs text-slate-500">
          Company and workspace are the same concept in Phase 1
        </p>

        <form className="mt-5 space-y-4" onSubmit={submit}>
          <label className="block">
            <span className="mb-2 block text-[11px] font-medium text-slate-600">Company name</span>
            <input
              className="h-10 w-full rounded-[8px] border border-slate-200 px-3 text-sm text-slate-950 placeholder:text-slate-400"
              placeholder="Acme Pvt Ltd"
              value={name}
              onChange={(event) => setName(event.target.value)}
              maxLength={160}
              required
            />
          </label>

          <label className="block">
            <span className="mb-2 block text-[11px] font-medium text-slate-600">
              Business email
            </span>
            <input
              className="h-10 w-full rounded-[8px] border border-slate-200 px-3 text-sm text-slate-950 placeholder:text-slate-400"
              type="email"
              placeholder="hello@acme.in"
              value={businessEmail}
              onChange={(event) => setBusinessEmail(event.target.value)}
            />
          </label>

          <label className="block">
            <span className="mb-2 block text-[11px] font-medium text-slate-600">Country</span>
            <input
              className="h-10 w-full rounded-[8px] border border-slate-200 px-3 text-sm text-slate-950 placeholder:text-slate-400"
              value={country}
              onChange={(event) => setCountry(event.target.value)}
            />
          </label>

          <label className="block">
            <span className="mb-2 block text-[11px] font-medium text-slate-600">Timezone</span>
            <input
              className="h-10 w-full rounded-[8px] border border-slate-200 px-3 text-sm text-slate-950 placeholder:text-slate-400"
              value={timezone}
              onChange={(event) => setTimezone(event.target.value)}
            />
          </label>

          {error && <p className="text-xs font-semibold text-red-600">{error}</p>}

          <button
            type="submit"
            className="mt-5 flex h-10 w-full items-center justify-center rounded-[8px] bg-indigo-500 text-xs font-semibold text-white"
            disabled={submitting}
          >
            {submitting ? 'Creating...' : 'Create workspace'}
          </button>
        </form>

        <p className="mt-5 text-[11px] text-slate-500">You become the workspace OWNER</p>
      </main>
    </div>
  )
}
