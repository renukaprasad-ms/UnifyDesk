import PhoneInput from '../../components/ui/PhoneInput'
import CountrySelect from '../../components/ui/CountrySelect'
import StateSelect from '../../components/ui/StateSelect'
import CitySelect from '../../components/ui/CitySelect'
import { useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { FiCheckCircle, FiAlertCircle, FiSend } from 'react-icons/fi'
import {
  crmLeadFormsApi,
  type PublicForm,
  type FormFieldConfig,
} from '../../features/contacts/crmLeadFormsApi'
import { apiErrorMessage } from '../../lib/api/apiClient'

export default function PublicLeadFormPage() {
  const { formKey } = useParams<{ formKey: string }>()
  const [form, setForm] = useState<PublicForm | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [successMsg, setSuccessMsg] = useState('')
  const [formData, setFormData] = useState<Record<string, string>>({})

  useEffect(() => {
    if (!formKey) {
      setError('Form not found')
      setLoading(false)
      return
    }

    crmLeadFormsApi
      .getPublic(formKey)
      .then((res) => {
        setForm(res)
        const initialData: Record<string, string> = {}
        res.fields?.forEach((f) => {
          initialData[f.fieldKey] = ''
        })
        setFormData(initialData)
      })
      .catch((err) => {
        setError(apiErrorMessage(err, 'Unable to load form or this form is no longer active.'))
      })
      .finally(() => setLoading(false))
  }, [formKey])

  function handleFieldChange(key: string, value: string) {
    setFormData((prev) => ({ ...prev, [key]: value }))
  }

  function formatFormErrorMessage(err: unknown, defaultMsg: string): string {
    const raw = apiErrorMessage(err, defaultMsg)
    if (raw.startsWith('FIELD_REQUIRED:')) {
      const keyOrLabel = raw.substring('FIELD_REQUIRED:'.length)
      const matched = form?.fields?.find((f) => f.fieldKey === keyOrLabel || f.label === keyOrLabel)
      return `Please fill in the required field: ${matched?.label || keyOrLabel}`
    }
    if (raw.startsWith('CRM_CUSTOM_FIELD_REQUIRED:')) {
      const keyOrLabel = raw.substring('CRM_CUSTOM_FIELD_REQUIRED:'.length)
      const matched = form?.fields?.find((f) => f.fieldKey === keyOrLabel || f.label === keyOrLabel)
      return `Please fill in the required field: ${matched?.label || keyOrLabel}`
    }
    if (raw.startsWith('CRM_CUSTOM_FIELD_INVALID_')) {
      return 'One of the form fields has an invalid value. Please review your entries.'
    }
    if (raw === 'FORM_NOT_ACTIVE') {
      return 'This form is currently closed and no longer accepting new submissions.'
    }
    if (raw === 'FORM_NOT_FOUND') {
      return 'This form could not be found or has been removed.'
    }
    return raw
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!formKey || !form) return

    setSubmitting(true)
    setError('')

    try {
      const result = await crmLeadFormsApi.submitPublic(formKey, formData)
      setSubmitted(true)
      setSuccessMsg(result.message || form.successMessage || 'Thank you! Your submission has been received.')

      if (result.redirectUrl) {
        let target = result.redirectUrl.trim()
        if (!/^https?:\/\//i.test(target) && !target.startsWith('/')) {
          target = `https://${target}`
        }
        setTimeout(() => {
          window.location.href = target
        }, 1500)
      }
    } catch (err) {
      setError(formatFormErrorMessage(err, 'Failed to submit form. Please check your inputs and try again.'))
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 dark:bg-slate-950 p-4">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-indigo-600 border-t-transparent" />
      </div>
    )
  }

  if (error && !form) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 dark:bg-slate-950 p-4">
        <div className="w-full max-w-md rounded-2xl bg-white dark:bg-slate-900 p-8 text-center shadow-lg border border-slate-200 dark:border-slate-800">
          <FiAlertCircle className="mx-auto size-12 text-rose-500 mb-3" />
          <h2 className="text-xl font-bold text-slate-900 dark:text-slate-100 mb-2">Form Unavailable</h2>
          <p className="text-sm text-slate-600 dark:text-slate-400">{error}</p>
        </div>
      </div>
    )
  }

  if (submitted) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 dark:bg-slate-950 p-4">
        <div className="w-full max-w-md rounded-2xl bg-white dark:bg-slate-900 p-8 text-center shadow-xl border border-slate-200 dark:border-slate-800 animate-in fade-in zoom-in-95 duration-200">
          <div className="mx-auto flex size-14 items-center justify-center rounded-full bg-emerald-50 dark:bg-emerald-950/50 text-emerald-600 dark:text-emerald-400 mb-4">
            <FiCheckCircle className="size-8" />
          </div>
          <h2 className="text-2xl font-bold text-slate-900 dark:text-slate-100 mb-2">Success!</h2>
          <p className="text-sm text-slate-600 dark:text-slate-400 mb-6">{successMsg}</p>
          {form?.redirectUrl && (
            <p className="text-xs text-slate-400">Redirecting you shortly...</p>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-950 py-12 px-4 sm:px-6 lg:px-8 flex flex-col justify-between">
      <div className="mx-auto w-full max-w-xl">
        <div className="rounded-2xl bg-white dark:bg-slate-900 p-6 sm:p-10 shadow-xl border border-slate-200 dark:border-slate-800">
          <div className="mb-8">
            <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 dark:text-slate-100 tracking-tight">
              {form?.title}
            </h1>
            {form?.description && (
              <p className="mt-2 text-sm text-slate-600 dark:text-slate-400 leading-relaxed">
                {form.description}
              </p>
            )}
          </div>

          {error && (
            <div className="mb-6 flex items-center gap-2 rounded-xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900 p-3.5 text-sm text-rose-700 dark:text-rose-300">
              <FiAlertCircle className="size-5 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-5">
            {form?.fields?.map((field: FormFieldConfig) => (
              <div key={field.fieldKey}>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300 mb-1.5">
                  {field.label}
                  {field.required && <span className="ml-1 text-rose-500">*</span>}
                </label>

                {field.fieldKey === 'country' ? (
                  <CountrySelect value={formData.country || ''} valueType="name" required={field.required}
                    onChange={country => setFormData(current => ({ ...current, country, state: '', city: '' }))} />
                ) : field.fieldKey === 'state' ? (
                  <StateSelect country={formData.country} value={formData.state || ''} required={field.required}
                    onChange={state => setFormData(current => ({ ...current, state, city: '' }))} />
                ) : field.fieldKey === 'city' ? (
                  <CitySelect country={formData.country} state={formData.state} value={formData.city || ''} required={field.required}
                    onChange={city => handleFieldChange('city', city)} />
                ) : field.type === 'textarea' ? (
                  <textarea
                    rows={4}
                    required={field.required}
                    placeholder={field.placeholder || ''}
                    value={formData[field.fieldKey] || ''}
                    onChange={(e) => handleFieldChange(field.fieldKey, e.target.value)}
                    className="w-full rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 px-3.5 py-2.5 text-sm text-slate-900 dark:text-slate-100 placeholder-slate-400 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 transition"
                  />
                ) : field.type === 'select' ? (
                  <select
                    required={field.required}
                    value={formData[field.fieldKey] || ''}
                    onChange={(e) => handleFieldChange(field.fieldKey, e.target.value)}
                    className="w-full rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 px-3.5 py-2.5 text-sm text-slate-900 dark:text-slate-100 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 transition"
                  >
                    <option value="">{field.placeholder || 'Select an option'}</option>
                    {field.options?.map((opt) => (
                      <option key={opt} value={opt}>
                        {opt}
                      </option>
                    ))}
                  </select>
                ) : field.type === 'tel' || field.fieldKey === 'phone' ? (
                  <PhoneInput required={field.required} value={formData[field.fieldKey] || ''}
                    onChange={value => handleFieldChange(field.fieldKey, value)} />
                ) : (
                  <input
                    type={field.type || 'text'}
                    required={field.required}
                    placeholder={field.placeholder || ''}
                    value={formData[field.fieldKey] || ''}
                    onChange={(e) => handleFieldChange(field.fieldKey, e.target.value)}
                    className="w-full rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 px-3.5 py-2.5 text-sm text-slate-900 dark:text-slate-100 placeholder-slate-400 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 transition"
                  />
                )}
              </div>
            ))}

            <button
              type="submit"
              disabled={submitting}
              className="mt-6 flex w-full items-center justify-center gap-2 rounded-xl bg-indigo-600 px-5 py-3 text-sm font-semibold text-white shadow-md hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 disabled:opacity-50 transition active:scale-[0.99]"
            >
              {submitting ? (
                <>
                  <div className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                  <span>Submitting...</span>
                </>
              ) : (
                <>
                  <FiSend className="size-4" />
                  <span>Submit</span>
                </>
              )}
            </button>
          </form>
        </div>
      </div>

      <div className="mt-8 text-center text-xs text-slate-400 dark:text-slate-600">
        <span>Powered by </span>
        <span className="font-semibold text-slate-500 dark:text-slate-400">UnifyDesk CRM</span>
      </div>
    </div>
  )
}
