import { useSnackbarState } from '../feedback/useSnackbarState'
import { useEffect, useState } from 'react'
import {
  FiPlus,
  FiEdit2,
  FiTrash2,
  FiCode,
  FiCopy,
  FiCheck,
  FiExternalLink,
  FiInbox,
  FiArrowLeft,
  FiEye,
  FiPlay,
  FiPause,
  FiLayers,
} from 'react-icons/fi'
import WorkspaceDialog from '../ui/WorkspaceDialog'
import {
  crmLeadFormsApi,
  type LeadForm,
  type FormFieldConfig,
  type FormSubmission,
} from '../../features/contacts/crmLeadFormsApi'
import {
  crmCustomFieldsApi,
  type CrmFieldDef,
} from '../../features/contacts/crmCustomFieldsApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { Link } from 'react-router'

interface Props {
  companyId: string
  onClose: () => void
}

type ModalView = 'list' | 'editor' | 'share' | 'submissions'

const DEFAULT_PRESET_FIELDS: FormFieldConfig[] = [
  { fieldKey: 'fullName', label: 'Full Name', type: 'text', required: true, placeholder: 'e.g. Jane Doe' },
  { fieldKey: 'email', label: 'Work Email', type: 'email', required: true, placeholder: 'e.g. jane@company.com' },
  { fieldKey: 'phone', label: 'Phone Number', type: 'tel', required: false, placeholder: 'e.g. +1 555-0199' },
  { fieldKey: 'organization', label: 'Company Name', type: 'text', required: false, placeholder: 'e.g. Acme Corp' },
  { fieldKey: 'notes', label: 'Inquiry / Message', type: 'textarea', required: false, placeholder: 'How can we help you?' },
]

function normalizeRedirectUrl(url: string | null | undefined): string | undefined {
  if (!url) return undefined
  const trimmed = url.trim()
  if (!trimmed) return undefined
  if (/^https?:\/\//i.test(trimmed) || trimmed.startsWith('/')) {
    return trimmed
  }
  return `https://${trimmed}`
}

export default function LeadFormsModal({ companyId, onClose }: Props) {
  const [view, setView] = useState<ModalView>('list')
  const [forms, setForms] = useState<LeadForm[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useSnackbarState('', 'error')
  const [copiedKey, setCopiedKey] = useState<string | null>(null)

  // Custom Lead Field Definitions
  const [customDefs, setCustomDefs] = useState<CrmFieldDef[]>([])

  // Active / Selected Form
  const [selectedForm, setSelectedForm] = useState<LeadForm | null>(null)

  // Editor State
  const [isEditing, setIsEditing] = useState(false)
  const [editorTitle, setEditorTitle] = useState('')
  const [editorDescription, setEditorDescription] = useState('')
  const [editorStage, setEditorStage] = useState('NEW')
  const [editorSuccessMsg, setEditorSuccessMsg] = useState('Thank you! We will get in touch with you shortly.')
  const [editorRedirectUrl, setEditorRedirectUrl] = useState('')
  const [editorFields, setEditorFields] = useState<FormFieldConfig[]>(DEFAULT_PRESET_FIELDS)
  const [previewTab, setPreviewTab] = useState<'build' | 'preview'>('build')

  // Submissions State
  const [submissions, setSubmissions] = useState<FormSubmission[]>([])
  const [loadingSubmissions, setLoadingSubmissions] = useState(false)

  // Load forms and custom field definitions
  useEffect(() => {
    loadForms()
    crmCustomFieldsApi
      .list(companyId, 'LEAD')
      .then(setCustomDefs)
      .catch(() => {})
  }, [companyId])

  async function loadForms() {
    setLoading(true)
    setError('')
    try {
      const data = await crmLeadFormsApi.list(companyId)
      setForms(data)
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to load web forms'))
    } finally {
      setLoading(false)
    }
  }

  function startCreate() {
    setSelectedForm(null)
    setIsEditing(false)
    setEditorTitle('')
    setEditorDescription('')
    setEditorStage('NEW')
    setEditorSuccessMsg('Thank you! We will get in touch with you shortly.')
    setEditorRedirectUrl('')
    setEditorFields([...DEFAULT_PRESET_FIELDS])
    setPreviewTab('build')
    setView('editor')
  }

  function startEdit(form: LeadForm) {
    setSelectedForm(form)
    setIsEditing(true)
    setEditorTitle(form.title)
    setEditorDescription(form.description || '')
    setEditorStage(form.defaultLeadStage || 'NEW')
    setEditorSuccessMsg(form.successMessage || 'Thank you! We will get in touch with you shortly.')
    setEditorRedirectUrl(form.redirectUrl || '')
    setEditorFields(form.fields && form.fields.length > 0 ? form.fields : [...DEFAULT_PRESET_FIELDS])
    setPreviewTab('build')
    setView('editor')
  }

  async function handleToggleStatus(form: LeadForm) {
    const nextStatus = form.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE'
    setBusy(true)
    try {
      const updated = await crmLeadFormsApi.update(companyId, form.id, { status: nextStatus })
      setForms((prev) => prev.map((f) => (f.id === updated.id ? updated : f)))
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to update form status'))
    } finally {
      setBusy(false)
    }
  }

  async function handleDelete(form: LeadForm) {
    if (!window.confirm(`Are you sure you want to delete "${form.title}"? Submissions will remain recorded.`)) {
      return
    }
    setBusy(true)
    try {
      await crmLeadFormsApi.delete(companyId, form.id)
      setForms((prev) => prev.filter((f) => f.id !== form.id))
      if (selectedForm?.id === form.id) {
        setView('list')
        setSelectedForm(null)
      }
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to delete form'))
    } finally {
      setBusy(false)
    }
  }

  async function handleSaveForm() {
    if (!editorTitle.trim()) {
      setError('Form title is required')
      return
    }
    if (editorFields.length === 0) {
      setError('At least one form field is required')
      return
    }

    setBusy(true)
    setError('')

    try {
      if (isEditing && selectedForm) {
        const updated = await crmLeadFormsApi.update(companyId, selectedForm.id, {
          title: editorTitle.trim(),
          description: editorDescription.trim() || undefined,
          defaultLeadStage: editorStage,
          successMessage: editorSuccessMsg.trim() || undefined,
          redirectUrl: normalizeRedirectUrl(editorRedirectUrl),
          fields: editorFields,
        })
        setForms((prev) => prev.map((f) => (f.id === updated.id ? updated : f)))
        setSelectedForm(updated)
      } else {
        const created = await crmLeadFormsApi.create(companyId, {
          title: editorTitle.trim(),
          description: editorDescription.trim() || undefined,
          defaultLeadStage: editorStage,
          successMessage: editorSuccessMsg.trim() || undefined,
          redirectUrl: normalizeRedirectUrl(editorRedirectUrl),
          fields: editorFields,
        })
        setForms((prev) => [created, ...prev])
        setSelectedForm(created)
      }
      setView('list')
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to save form'))
    } finally {
      setBusy(false)
    }
  }

  async function openSubmissions(form: LeadForm) {
    setSelectedForm(form)
    setView('submissions')
    setLoadingSubmissions(true)
    try {
      const data = await crmLeadFormsApi.submissions(companyId, form.id)
      setSubmissions(data)
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to load submissions'))
    } finally {
      setLoadingSubmissions(false)
    }
  }

  function openShare(form: LeadForm) {
    setSelectedForm(form)
    setView('share')
  }

  function copyToClipboard(text: string, key: string) {
    navigator.clipboard.writeText(text)
    setCopiedKey(key)
    setTimeout(() => setCopiedKey(null), 2000)
  }

  // Field manipulation helpers
  function removeField(index: number) {
    setEditorFields((prev) => prev.filter((_, i) => i !== index))
  }

  function toggleFieldRequired(index: number) {
    setEditorFields((prev) =>
      prev.map((f, i) => (i === index ? { ...f, required: !f.required } : f)),
    )
  }

  function updateFieldLabel(index: number, label: string) {
    setEditorFields((prev) =>
      prev.map((f, i) => (i === index ? { ...f, label } : f)),
    )
  }

  function updateFieldPlaceholder(index: number, placeholder: string) {
    setEditorFields((prev) =>
      prev.map((f, i) => (i === index ? { ...f, placeholder } : f)),
    )
  }

  function moveField(index: number, direction: 'up' | 'down') {
    const target = direction === 'up' ? index - 1 : index + 1
    if (target < 0 || target >= editorFields.length) return
    const updated = [...editorFields]
    const temp = updated[index]
    updated[index] = updated[target]
    updated[target] = temp
    setEditorFields(updated)
  }

  function addPresetField(preset: FormFieldConfig) {
    if (editorFields.some((f) => f.fieldKey === preset.fieldKey)) return
    setEditorFields((prev) => [...prev, { ...preset }])
  }

  function addCustomField(def: CrmFieldDef) {
    if (editorFields.some((f) => f.fieldKey === def.fieldKey)) return
    const inputType =
      def.fieldType === 'DROPDOWN' || def.fieldType === 'MULTI_SELECT'
        ? 'select'
        : def.fieldType === 'NUMBER'
          ? 'number'
          : def.fieldType === 'DATE'
            ? 'date'
            : 'text'

    setEditorFields((prev) => [
      ...prev,
      {
        fieldKey: def.fieldKey,
        label: def.label,
        type: inputType,
        required: def.required,
        placeholder: `Enter ${def.label.toLowerCase()}`,
        options: def.options || undefined,
      },
    ])
  }

  // Generate snippets for share view
  const publicUrl = selectedForm
    ? `${window.location.origin}/forms/${selectedForm.formKey}`
    : ''

  const iframeSnippet = selectedForm
    ? `<iframe src="${publicUrl}" width="100%" height="650" frameborder="0" style="border:none;border-radius:12px;box-shadow:0 10px 25px -5px rgba(0,0,0,0.1);"></iframe>`
    : ''

  const htmlFormSnippet = selectedForm
    ? `<!-- UnifyDesk Web-to-Lead Form: ${selectedForm.title} -->
<form id="unifydesk-lead-form-${selectedForm.formKey}" style="max-width:480px;font-family:system-ui,-apple-system,sans-serif;display:flex;flex-direction:column;gap:14px;">
${(selectedForm.fields || [])
  .map(
    (f) => `  <div>
    <label style="display:block;font-size:12px;font-weight:600;margin-bottom:4px;text-transform:uppercase;">${f.label}${f.required ? ' *' : ''}</label>
    ${
      f.type === 'textarea'
        ? `<textarea name="${f.fieldKey}" ${f.required ? 'required' : ''} placeholder="${f.placeholder || ''}" rows="3" style="width:100%;padding:10px;border:1px solid #cbd5e1;border-radius:8px;box-sizing:border-box;"></textarea>`
        : f.type === 'select'
          ? `<select name="${f.fieldKey}" ${f.required ? 'required' : ''} style="width:100%;padding:10px;border:1px solid #cbd5e1;border-radius:8px;box-sizing:border-box;">
      <option value="">${f.placeholder || 'Select...'}</option>
      ${(f.options || []).map((opt) => `      <option value="${opt}">${opt}</option>`).join('\n')}
    </select>`
          : `<input type="${f.type}" name="${f.fieldKey}" ${f.required ? 'required' : ''} placeholder="${f.placeholder || ''}" style="width:100%;padding:10px;border:1px solid #cbd5e1;border-radius:8px;box-sizing:border-box;" />`
    }
  </div>`,
  )
  .join('\n')}
  <button type="submit" style="background:#4f46e5;color:#fff;border:none;padding:12px 20px;border-radius:8px;font-weight:600;cursor:pointer;margin-top:6px;">Submit</button>
  <div id="unifydesk-form-status-${selectedForm.formKey}" style="display:none;padding:10px;border-radius:8px;font-size:14px;margin-top:8px;"></div>
</form>

<script>
(function() {
  var form = document.getElementById("unifydesk-lead-form-${selectedForm.formKey}");
  var status = document.getElementById("unifydesk-form-status-${selectedForm.formKey}");
  if (!form) return;
  form.addEventListener("submit", function(e) {
    e.preventDefault();
    var formData = new FormData(form);
    var payload = {};
    formData.forEach(function(value, key) { payload[key] = value; });
    var btn = form.querySelector("button[type=submit]");
    if (btn) { btn.disabled = true; btn.innerText = "Submitting..."; }
    fetch("${window.location.origin}/api/v1/public/crm/forms/${selectedForm.formKey}/submit", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    })
    .then(function(res) { return res.json(); })
    .then(function(res) {
      if (btn) { btn.disabled = false; btn.innerText = "Submit"; }
      if (res.data && res.data.success) {
        status.style.display = "block";
        status.style.background = "#ecfdf5";
        status.style.color = "#047857";
        status.innerText = res.data.message || "${selectedForm.successMessage || 'Thank you! We will get in touch soon.'}";
        form.reset();
        ${selectedForm.redirectUrl ? `setTimeout(function(){ window.location.href = "${normalizeRedirectUrl(selectedForm.redirectUrl)}"; }, 1200);` : ''}
      } else {
        throw new Error(res.error_message || "Submission failed");
      }
    })
    .catch(function(err) {
      if (btn) { btn.disabled = false; btn.innerText = "Submit"; }
      status.style.display = "block";
      status.style.background = "#fff1f2";
      status.style.color = "#be123c";
      status.innerText = err.message || "An error occurred. Please try again.";
    });
  });
})();
</script>`
    : ''

  return (
    <WorkspaceDialog
      title={
        view === 'list'
          ? 'Web-to-Lead Forms'
          : view === 'editor'
            ? isEditing
              ? `Edit Form: ${editorTitle || selectedForm?.title}`
              : 'Create Web-to-Lead Form'
            : view === 'share'
              ? `Share & Embed: ${selectedForm?.title}`
              : `Submissions: ${selectedForm?.title}`
      }
      wide
      busy={busy}
      error={error}
      onClose={onClose}
    >
      <div className="space-y-4">
        {/* Navigation Breadcrumb / Back button when in sub-views */}
        {view !== 'list' && (
          <div className="flex items-center justify-between border-b border-slate-200 dark:border-slate-800 pb-3">
            <button
              type="button"
              onClick={() => {
                setError('')
                setView('list')
              }}
              className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-200 transition"
            >
              <FiArrowLeft className="size-3.5" />
              <span>Back to all forms</span>
            </button>
            {view === 'editor' && (
              <div className="flex rounded-lg bg-slate-100 dark:bg-slate-800 p-0.5 text-xs font-medium">
                <button
                  type="button"
                  onClick={() => setPreviewTab('build')}
                  className={`rounded-md px-3 py-1 transition ${
                    previewTab === 'build'
                      ? 'bg-white dark:bg-slate-700 text-slate-900 dark:text-slate-100 shadow-sm'
                      : 'text-slate-600 dark:text-slate-400 hover:text-slate-900'
                  }`}
                >
                  <span className="flex items-center gap-1.5">
                    <FiLayers className="size-3.5" />
                    Builder
                  </span>
                </button>
                <button
                  type="button"
                  onClick={() => setPreviewTab('preview')}
                  className={`rounded-md px-3 py-1 transition ${
                    previewTab === 'preview'
                      ? 'bg-white dark:bg-slate-700 text-slate-900 dark:text-slate-100 shadow-sm'
                      : 'text-slate-600 dark:text-slate-400 hover:text-slate-900'
                  }`}
                >
                  <span className="flex items-center gap-1.5">
                    <FiEye className="size-3.5" />
                    Live Preview
                  </span>
                </button>
              </div>
            )}
          </div>
        )}

        {/* -------------------- VIEW 1: FORMS LIST -------------------- */}
        {view === 'list' && (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  Embed lead capture forms on your website, blogs, or landing pages. Inbound inquiries automatically create contacts, enroll leads, and log notes.
                </p>
              </div>
              <button
                type="button"
                onClick={startCreate}
                className="inline-flex items-center gap-1.5 rounded-lg bg-indigo-600 px-3.5 py-2 text-xs font-semibold text-white shadow-sm hover:bg-indigo-700 active:scale-[0.98] transition"
              >
                <FiPlus className="size-3.5" />
                <span>Create Form</span>
              </button>
            </div>

            {loading ? (
              <div className="flex items-center justify-center py-12">
                <div className="h-6 w-6 animate-spin rounded-full border-2 border-indigo-600 border-t-transparent" />
              </div>
            ) : forms.length === 0 ? (
              <div className="rounded-xl border border-dashed border-slate-300 dark:border-slate-700 p-8 text-center">
                <FiInbox className="mx-auto size-10 text-slate-400 mb-2" />
                <h3 className="text-sm font-semibold text-slate-800 dark:text-slate-200">No Web Forms yet</h3>
                <p className="mt-1 text-xs text-slate-500 dark:text-slate-400 max-w-sm mx-auto">
                  Create your first lead capture form to automatically capture visitors as qualified leads in your UnifyDesk CRM.
                </p>
                <button
                  type="button"
                  onClick={startCreate}
                  className="mt-4 inline-flex items-center gap-1.5 rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-indigo-700 transition"
                >
                  <FiPlus className="size-3.5" />
                  <span>Create First Form</span>
                </button>
              </div>
            ) : (
              <div className="divide-y divide-slate-100 dark:divide-slate-800 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 overflow-hidden">
                {forms.map((form) => (
                  <div
                    key={form.id}
                    className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 p-4 hover:bg-slate-50/75 dark:hover:bg-slate-800/40 transition"
                  >
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2 flex-wrap">
                        <h4 className="font-semibold text-sm text-slate-900 dark:text-slate-100">
                          {form.title}
                        </h4>
                        <span
                          className={`inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-medium ${
                            form.status === 'ACTIVE'
                              ? 'bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800'
                              : 'bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-400 border border-amber-200 dark:border-amber-800'
                          }`}
                        >
                          {form.status}
                        </span>
                        <span className="inline-flex items-center rounded bg-slate-100 dark:bg-slate-800 px-2 py-0.5 text-[11px] font-medium text-slate-600 dark:text-slate-300">
                          Stage: {form.defaultLeadStage || 'NEW'}
                        </span>
                      </div>
                      {form.description && (
                        <p className="mt-1 text-xs text-slate-500 dark:text-slate-400 line-clamp-1">
                          {form.description}
                        </p>
                      )}
                      <div className="mt-2 flex items-center gap-4 text-[11px] text-slate-400">
                        <span>{form.fields?.length || 0} fields configured</span>
                        <span>•</span>
                        <span className="font-semibold text-slate-600 dark:text-slate-300">
                          {form.submissionsCount} submissions
                        </span>
                        <span>•</span>
                        <span>Created {new Date(form.createdAt).toLocaleDateString()}</span>
                      </div>
                    </div>

                    <div className="flex items-center gap-1.5 shrink-0">
                      <button
                        type="button"
                        onClick={() => openShare(form)}
                        title="Embed & Share Code"
                        className="inline-flex items-center gap-1 rounded-md border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2.5 py-1.5 text-xs font-medium text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-700 shadow-sm transition"
                      >
                        <FiCode className="size-3.5 text-indigo-500" />
                        <span>Embed & Share</span>
                      </button>

                      <button
                        type="button"
                        onClick={() => openSubmissions(form)}
                        title="View Submissions"
                        className="inline-flex items-center gap-1 rounded-md border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2.5 py-1.5 text-xs font-medium text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-700 shadow-sm transition"
                      >
                        <FiInbox className="size-3.5 text-sky-500" />
                        <span>Submissions ({form.submissionsCount})</span>
                      </button>

                      <button
                        type="button"
                        onClick={() => handleToggleStatus(form)}
                        title={form.status === 'ACTIVE' ? 'Pause Form' : 'Activate Form'}
                        className="p-1.5 rounded-md text-slate-500 hover:text-slate-700 dark:hover:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 transition"
                      >
                        {form.status === 'ACTIVE' ? <FiPause className="size-4" /> : <FiPlay className="size-4" />}
                      </button>

                      <button
                        type="button"
                        onClick={() => startEdit(form)}
                        title="Edit Form"
                        className="p-1.5 rounded-md text-slate-500 hover:text-slate-700 dark:hover:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 transition"
                      >
                        <FiEdit2 className="size-4" />
                      </button>

                      <button
                        type="button"
                        onClick={() => handleDelete(form)}
                        title="Delete Form"
                        className="p-1.5 rounded-md text-rose-500 hover:text-rose-700 hover:bg-rose-50 dark:hover:bg-rose-950/50 transition"
                      >
                        <FiTrash2 className="size-4" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* -------------------- VIEW 2: FORM EDITOR -------------------- */}
        {view === 'editor' && (
          <div className="space-y-5">
            {previewTab === 'preview' ? (
              /* LIVE PREVIEW */
              <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 p-6">
                <div className="mx-auto max-w-md rounded-2xl bg-white dark:bg-slate-900 p-6 shadow-md border border-slate-200 dark:border-slate-800">
                  <h3 className="text-xl font-bold text-slate-900 dark:text-slate-100">
                    {editorTitle || 'Your Form Title'}
                  </h3>
                  {editorDescription && (
                    <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                      {editorDescription}
                    </p>
                  )}

                  <div className="mt-5 space-y-3.5">
                    {editorFields.map((field) => (
                      <div key={field.fieldKey}>
                        <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                          {field.label}
                          {field.required && <span className="ml-1 text-rose-500">*</span>}
                        </label>
                        {field.type === 'textarea' ? (
                          <textarea
                            disabled
                            rows={3}
                            placeholder={field.placeholder || ''}
                            className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 px-3 py-2 text-xs text-slate-400 cursor-not-allowed"
                          />
                        ) : field.type === 'select' ? (
                          <select
                            disabled
                            className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 px-3 py-2 text-xs text-slate-400 cursor-not-allowed"
                          >
                            <option>{field.placeholder || 'Select an option'}</option>
                          </select>
                        ) : (
                          <input
                            disabled
                            type="text"
                            placeholder={field.placeholder || ''}
                            className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 px-3 py-2 text-xs text-slate-400 cursor-not-allowed"
                          />
                        )}
                      </div>
                    ))}
                    <button
                      type="button"
                      disabled
                      className="mt-4 w-full rounded-lg bg-indigo-600 px-4 py-2 text-xs font-semibold text-white opacity-80 cursor-not-allowed"
                    >
                      Submit
                    </button>
                  </div>
                </div>
              </div>
            ) : (
              /* BUILDER CONTROLS */
              <div className="grid grid-cols-1 md:grid-cols-12 gap-5">
                {/* Form General Settings */}
                <div className="md:col-span-5 space-y-3.5 border-r border-slate-200 dark:border-slate-800 pr-0 md:pr-4">
                  <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">
                    General Settings
                  </h4>

                  <div>
                    <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                      Form Title *
                    </label>
                    <input
                      type="text"
                      value={editorTitle}
                      onChange={(e) => setEditorTitle(e.target.value)}
                      placeholder="e.g. Request a Demo / Contact Us"
                      className="w-full rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-1.5 text-xs text-slate-900 dark:text-slate-100 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                      Description / Subtitle
                    </label>
                    <textarea
                      rows={2}
                      value={editorDescription}
                      onChange={(e) => setEditorDescription(e.target.value)}
                      placeholder="Tell visitors what will happen once they submit"
                      className="w-full rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-1.5 text-xs text-slate-900 dark:text-slate-100 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                      Default Lead Stage
                    </label>
                    <select
                      value={editorStage}
                      onChange={(e) => setEditorStage(e.target.value)}
                      className="w-full rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-1.5 text-xs text-slate-900 dark:text-slate-100 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                    >
                      <option value="NEW">New</option>
                      <option value="CONTACTED">Contacted</option>
                      <option value="QUALIFIED">Qualified</option>
                    </select>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                      Success Message
                    </label>
                    <input
                      type="text"
                      value={editorSuccessMsg}
                      onChange={(e) => setEditorSuccessMsg(e.target.value)}
                      placeholder="Thank you! We will get in touch with you shortly."
                      className="w-full rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-1.5 text-xs text-slate-900 dark:text-slate-100 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                      Redirect URL (Optional)
                    </label>
                    <input
                      type="url"
                      value={editorRedirectUrl}
                      onChange={(e) => setEditorRedirectUrl(e.target.value)}
                      placeholder="https://example.com/thank-you"
                      className="w-full rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-1.5 text-xs text-slate-900 dark:text-slate-100 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                    />
                  </div>
                </div>

                {/* Form Fields Configurator */}
                <div className="md:col-span-7 space-y-3.5">
                  <div className="flex items-center justify-between">
                    <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">
                      Form Fields ({editorFields.length})
                    </h4>
                  </div>

                  {/* Active Fields List */}
                  <div className="space-y-2 max-h-[300px] overflow-y-auto pr-1">
                    {editorFields.map((field, idx) => (
                      <div
                        key={field.fieldKey}
                        className="flex items-center gap-2 rounded-lg border border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-800/40 p-2 text-xs"
                      >
                        <div className="flex flex-col gap-0.5 text-slate-400">
                          <button
                            type="button"
                            disabled={idx === 0}
                            onClick={() => moveField(idx, 'up')}
                            className="hover:text-slate-700 disabled:opacity-20"
                          >
                            ▲
                          </button>
                          <button
                            type="button"
                            disabled={idx === editorFields.length - 1}
                            onClick={() => moveField(idx, 'down')}
                            className="hover:text-slate-700 disabled:opacity-20"
                          >
                            ▼
                          </button>
                        </div>

                        <div className="min-w-0 flex-1 grid grid-cols-2 gap-2">
                          <input
                            type="text"
                            value={field.label}
                            onChange={(e) => updateFieldLabel(idx, e.target.value)}
                            placeholder="Label"
                            className="rounded border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 px-2 py-1 text-xs"
                          />
                          <input
                            type="text"
                            value={field.placeholder || ''}
                            onChange={(e) => updateFieldPlaceholder(idx, e.target.value)}
                            placeholder="Placeholder"
                            className="rounded border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 px-2 py-1 text-xs"
                          />
                        </div>

                        <button
                          type="button"
                          onClick={() => toggleFieldRequired(idx)}
                          className={`shrink-0 rounded px-2 py-1 text-[11px] font-semibold transition ${
                            field.required
                              ? 'bg-rose-50 text-rose-700 border border-rose-200 dark:bg-rose-950/50 dark:text-rose-400 dark:border-rose-800'
                              : 'bg-slate-200 text-slate-600 dark:bg-slate-700 dark:text-slate-400'
                          }`}
                        >
                          {field.required ? 'Required' : 'Optional'}
                        </button>

                        <button
                          type="button"
                          onClick={() => removeField(idx)}
                          className="shrink-0 p-1 text-slate-400 hover:text-rose-600"
                        >
                          <FiTrash2 className="size-3.5" />
                        </button>
                      </div>
                    ))}
                  </div>

                  {/* Add Field Presets & Custom Fields */}
                  <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-800/20 p-3 space-y-2">
                    <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wide">
                      Add Common Fields
                    </p>
                    <div className="flex flex-wrap gap-1.5">
                      {DEFAULT_PRESET_FIELDS.map((preset) => {
                        const isAdded = editorFields.some((f) => f.fieldKey === preset.fieldKey)
                        return (
                          <button
                            key={preset.fieldKey}
                            type="button"
                            disabled={isAdded}
                            onClick={() => addPresetField(preset)}
                            className="inline-flex items-center gap-1 rounded-md bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 px-2 py-1 text-xs text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-700 disabled:opacity-40 shadow-xs transition"
                          >
                            <FiPlus className="size-3" />
                            <span>{preset.label}</span>
                          </button>
                        )
                      })}
                    </div>

                    {customDefs.length > 0 && (
                      <div className="pt-2">
                        <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wide mb-1">
                          CRM Custom Fields
                        </p>
                        <div className="flex flex-wrap gap-1.5">
                          {customDefs.map((def) => {
                            const isAdded = editorFields.some((f) => f.fieldKey === def.fieldKey)
                            return (
                              <button
                                key={def.fieldKey}
                                type="button"
                                disabled={isAdded}
                                onClick={() => addCustomField(def)}
                                className="inline-flex items-center gap-1 rounded-md bg-indigo-50 dark:bg-indigo-950/40 border border-indigo-200 dark:border-indigo-800 px-2 py-1 text-xs text-indigo-700 dark:text-indigo-300 hover:bg-indigo-100 disabled:opacity-40 transition"
                              >
                                <FiPlus className="size-3" />
                                <span>{def.label}</span>
                              </button>
                            )
                          })}
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            )}

            {/* Editor Action Buttons */}
            <div className="flex items-center justify-end gap-2 border-t border-slate-200 dark:border-slate-800 pt-4">
              <button
                type="button"
                onClick={() => setView('list')}
                className="rounded-lg border border-slate-200 dark:border-slate-700 px-3.5 py-1.5 text-xs font-semibold text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleSaveForm}
                disabled={busy}
                className="inline-flex items-center gap-1.5 rounded-lg bg-indigo-600 px-4 py-1.5 text-xs font-semibold text-white hover:bg-indigo-700 shadow-sm transition"
              >
                {busy ? 'Saving...' : isEditing ? 'Save Changes' : 'Create Form'}
              </button>
            </div>
          </div>
        )}

        {/* -------------------- VIEW 3: EMBED & SHARE -------------------- */}
        {view === 'share' && selectedForm && (
          <div className="space-y-5">
            {/* Shareable URL */}
            <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-4 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold uppercase tracking-wider text-slate-600 dark:text-slate-400">
                  Direct Shareable Link
                </span>
                <a
                  href={publicUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1 text-xs text-indigo-600 hover:underline"
                >
                  <FiExternalLink className="size-3" />
                  <span>Open Form</span>
                </a>
              </div>
              <div className="flex items-center gap-2">
                <input
                  type="text"
                  readOnly
                  value={publicUrl}
                  className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 px-3 py-1.5 text-xs text-slate-700 dark:text-slate-200 font-mono"
                />
                <button
                  type="button"
                  onClick={() => copyToClipboard(publicUrl, 'link')}
                  className="inline-flex items-center gap-1.5 shrink-0 rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-indigo-700 shadow-xs"
                >
                  {copiedKey === 'link' ? <FiCheck className="size-3.5" /> : <FiCopy className="size-3.5" />}
                  <span>{copiedKey === 'link' ? 'Copied!' : 'Copy Link'}</span>
                </button>
              </div>
            </div>

            {/* HTML Form Snippet */}
            <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-4 space-y-2">
              <div className="flex items-center justify-between">
                <div>
                  <span className="text-xs font-bold uppercase tracking-wider text-slate-600 dark:text-slate-400">
                    HTML Embed Snippet
                  </span>
                  <p className="text-[11px] text-slate-400">
                    Paste directly into WordPress, Webflow, Shopify, or any static HTML site.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => copyToClipboard(htmlFormSnippet, 'html')}
                  className="inline-flex items-center gap-1.5 shrink-0 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-1.5 text-xs font-semibold text-slate-700 dark:text-slate-200 hover:bg-slate-50 shadow-xs"
                >
                  {copiedKey === 'html' ? <FiCheck className="size-3.5 text-emerald-600" /> : <FiCopy className="size-3.5" />}
                  <span>{copiedKey === 'html' ? 'Copied HTML!' : 'Copy HTML'}</span>
                </button>
              </div>
              <textarea
                readOnly
                rows={6}
                value={htmlFormSnippet}
                className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-900 text-slate-200 px-3 py-2 text-[11px] font-mono leading-relaxed"
              />
            </div>

            {/* iFrame Embed Snippet */}
            <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-4 space-y-2">
              <div className="flex items-center justify-between">
                <div>
                  <span className="text-xs font-bold uppercase tracking-wider text-slate-600 dark:text-slate-400">
                    iFrame Snippet
                  </span>
                  <p className="text-[11px] text-slate-400">
                    One-line responsive iframe for zero-code integration.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => copyToClipboard(iframeSnippet, 'iframe')}
                  className="inline-flex items-center gap-1.5 shrink-0 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-1.5 text-xs font-semibold text-slate-700 dark:text-slate-200 hover:bg-slate-50 shadow-xs"
                >
                  {copiedKey === 'iframe' ? <FiCheck className="size-3.5 text-emerald-600" /> : <FiCopy className="size-3.5" />}
                  <span>{copiedKey === 'iframe' ? 'Copied iFrame!' : 'Copy iFrame'}</span>
                </button>
              </div>
              <input
                type="text"
                readOnly
                value={iframeSnippet}
                className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 px-3 py-1.5 text-xs text-slate-700 dark:text-slate-200 font-mono"
              />
            </div>
          </div>
        )}

        {/* -------------------- VIEW 4: SUBMISSIONS -------------------- */}
        {view === 'submissions' && (
          <div className="space-y-4">
            {loadingSubmissions ? (
              <div className="flex items-center justify-center py-12">
                <div className="h-6 w-6 animate-spin rounded-full border-2 border-indigo-600 border-t-transparent" />
              </div>
            ) : submissions.length === 0 ? (
              <div className="rounded-xl border border-dashed border-slate-300 dark:border-slate-700 p-8 text-center">
                <FiInbox className="mx-auto size-10 text-slate-400 mb-2" />
                <h3 className="text-sm font-semibold text-slate-800 dark:text-slate-200">No submissions yet</h3>
                <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                  Submissions captured through your public link or embed will show up here.
                </p>
              </div>
            ) : (
              <div className="divide-y divide-slate-100 dark:divide-slate-800 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 overflow-hidden max-h-[420px] overflow-y-auto">
                {submissions.map((sub) => (
                  <div key={sub.id} className="p-4 hover:bg-slate-50/50 dark:hover:bg-slate-800/30 transition">
                    <div className="flex items-center justify-between gap-3 mb-2">
                      <div className="flex items-center gap-2">
                        <span className="flex size-7 items-center justify-center rounded-full bg-indigo-50 text-[11px] font-bold text-indigo-600">
                          {(sub.contactName || 'Visitor').slice(0, 2).toUpperCase()}
                        </span>
                        <div>
                          {sub.contactId ? (
                            <Link
                              to={`/contacts/${sub.contactId}`}
                              className="font-semibold text-xs text-indigo-600 hover:underline"
                            >
                              {sub.contactName || 'Unnamed Contact'}
                            </Link>
                          ) : (
                            <span className="font-semibold text-xs text-slate-800 dark:text-slate-200">
                              {sub.contactName || 'Anonymous Visitor'}
                            </span>
                          )}
                        </div>
                      </div>
                      <div className="text-[11px] text-slate-400 flex items-center gap-2">
                        {sub.ipAddress && <span>IP: {sub.ipAddress}</span>}
                        <span>•</span>
                        <span>{new Date(sub.submittedAt).toLocaleString()}</span>
                      </div>
                    </div>

                    {/* Payload badges */}
                    <div className="flex flex-wrap gap-1.5 mt-2">
                      {Object.entries(sub.payload || {}).map(([key, value]) => (
                        <div
                          key={key}
                          className="inline-flex items-center gap-1 rounded bg-slate-100 dark:bg-slate-800 px-2 py-0.5 text-[11px] text-slate-700 dark:text-slate-300 border border-slate-200/50 dark:border-slate-700/50"
                        >
                          <span className="font-semibold text-slate-500 dark:text-slate-400 capitalize">
                            {key}:
                          </span>
                          <span>{String(value)}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </WorkspaceDialog>
  )
}
