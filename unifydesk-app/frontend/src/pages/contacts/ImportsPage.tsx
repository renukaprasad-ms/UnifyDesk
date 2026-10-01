import { useState } from 'react'
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
          <h2 className="text-lg font-bold text-slate-950">Choose a CSV file</h2>
          <p className="mt-1 text-sm text-slate-500">
            You'll review the column mapping and import options on the next screen.
          </p>

          <label className="mt-5 block text-sm font-semibold text-slate-700">
            CSV file
            <input
              className="form-input mt-2"
              type="file"
              accept=".csv,text/csv"
              onChange={(event) => setFile(event.target.files?.[0] || null)}
            />
          </label>

          {file && (
            <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm">
              <p className="font-semibold text-slate-900">{file.name}</p>
              <p className="mt-1 text-slate-500">
                {(file.size / 1024).toLocaleString(undefined, { maximumFractionDigits: 1 })} KB
              </p>
            </div>
          )}

          <div className="mt-4 rounded-xl border border-indigo-100 bg-indigo-50 p-4 text-sm text-slate-700">
            <strong className="text-slate-950">Marketing consent is separate.</strong> Adding
            someone to Contacts does not automatically opt them into Email, SMS or WhatsApp
            marketing. If your CSV contains verified consent data, you can map it under Advanced
            settings.
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
