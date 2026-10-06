import { useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import ContactsNav from '../../components/contacts/ContactsNav'
import { contactsApi } from '../../features/contacts/contactsApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId } from '../../store/workspace/workspaceSlice'

export default function ImportsPage() {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const navigate = useNavigate()
  const [file, setFile] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const inputRef = useRef<HTMLInputElement | null>(null)
  const [dragging, setDragging] = useState(false)

  const chooseFile = (nextFile: File | null) => {
    if (!nextFile) return
    if (!nextFile.name.toLowerCase().endsWith('.csv')) {
      setFile(null)
      setError('Choose a CSV file.')
      return
    }
    setError('')
    setFile(nextFile)
  }

  const downloadSample = () => {
    const sample = [
      'display_name,first_name,last_name,email,phone,email_marketing_consent,sms_marketing_consent,whatsapp_marketing_consent,consent_source,consent_occurred_at',
      'Anita Rao,Anita,Rao,anita@example.com,+919876543210,true,false,true,website-form,2026-10-01T10:00:00Z',
    ].join('\n')
    const url = URL.createObjectURL(new Blob([sample], { type: 'text/csv;charset=utf-8' }))
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = 'unifydesk-contacts-sample.csv'
    anchor.click()
    URL.revokeObjectURL(url)
  }

  const start = async () => {
    if (!companyId || !file) return
    setBusy(true)
    setError('')
    try {
      const intent = await contactsApi.createImport(companyId, file)
      await contactsApi.uploadImportFile(intent, file)
      navigate('/contacts/imports/' + intent.id)
    } catch (uploadError) {
      setError(apiErrorMessage(uploadError, 'Unable to upload this CSV.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-4xl">
        <ContactsNav />
        <p className="eyebrow">Contacts</p>
        <h1 className="mt-2 text-3xl font-bold text-slate-950">Import contacts</h1>
        <p className="mt-2 max-w-2xl text-sm text-slate-500">
          Upload a CSV and UnifyDesk will help match common columns like name, email and phone
          before anything is imported.
        </p>

        <div className="panel mt-6 p-6">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h2 className="text-lg font-bold text-slate-950">Upload your contacts</h2>
              <p className="mt-1 text-sm text-slate-500">
                We'll detect common columns automatically, then let you review everything before
                importing.
              </p>
            </div>
            <button type="button" className="secondary-button" onClick={downloadSample}>
              Download sample CSV
            </button>
          </div>

          <input
            ref={inputRef}
            className="sr-only"
            type="file"
            accept=".csv,text/csv"
            onChange={(event) => chooseFile(event.target.files?.[0] || null)}
          />

          <button
            type="button"
            className={
              'mt-5 flex min-h-52 w-full flex-col items-center justify-center rounded-2xl border-2 border-dashed px-6 text-center transition ' +
              (dragging
                ? 'border-indigo-400 bg-indigo-50'
                : 'border-slate-200 bg-slate-50 hover:border-indigo-300 hover:bg-indigo-50/60')
            }
            onClick={() => inputRef.current?.click()}
            onDragEnter={(event) => {
              event.preventDefault()
              setDragging(true)
            }}
            onDragOver={(event) => event.preventDefault()}
            onDragLeave={() => setDragging(false)}
            onDrop={(event) => {
              event.preventDefault()
              setDragging(false)
              chooseFile(event.dataTransfer.files?.[0] || null)
            }}
          >
            <span className="text-base font-bold text-slate-950">
              {file ? file.name : 'Drop your CSV here'}
            </span>
            <span className="mt-2 text-sm text-slate-500">
              {file
                ? (file.size / 1024).toLocaleString(undefined, { maximumFractionDigits: 1 }) + ' KB'
                : 'or click to choose a file'}
            </span>
            {file && <span className="mt-3 text-xs font-semibold text-indigo-700">Choose another file</span>}
          </button>

          <div className="mt-4 rounded-xl border border-indigo-100 bg-indigo-50 p-4 text-sm text-slate-700">
            <strong className="text-slate-950">Marketing permissions stay safe.</strong> Importing a
            person never opts them in automatically. When your CSV contains explicit Email, SMS or
            WhatsApp consent columns, UnifyDesk will detect them for review.
          </div>

          {error && (
            <div
              role="alert"
              className="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700"
            >
              {error}
            </div>
          )}

          <button
            className="primary-button mt-5"
            disabled={!file || busy}
            onClick={() => void start()}
          >
            {busy ? 'Uploading…' : 'Continue'}
          </button>
        </div>
      </div>
    </section>
  )
}
