import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { FiCode, FiCopy, FiEdit2, FiFileText, FiPlus, FiSearch, FiServer } from 'react-icons/fi'
import EmailTemplateEditorModal from '../../components/email-templates/EmailTemplateEditorModal'
import EmailTemplatePreview from '../../components/email-templates/EmailTemplatePreview'
import EmailTemplateProviderModal from '../../components/email-templates/EmailTemplateProviderModal'
import { useSnackbar } from '../../components/feedback/SnackbarProvider'
import { emailTemplateApi } from '../../features/emailTemplate/emailTemplateApi'
import type {
  EmailTemplate,
  EmailTemplateCatalogItem,
  EmailTemplatePurpose,
  EmailTemplateSummary,
} from '../../features/emailTemplate/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId } from '../../store/workspace/workspaceSlice'
import { CardSkeleton } from '../../components/ui/Skeleton'

export default function TemplateLibrary() {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const snackbar = useSnackbar()
  const { can } = useIam()
  const [searchParams, setSearchParams] = useSearchParams()
  const [tab, setTab] = useState<'gallery' | 'mine'>('gallery')
  const [catalog, setCatalog] = useState<EmailTemplateCatalogItem[]>([])
  const [templates, setTemplates] = useState<EmailTemplateSummary[]>([])
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(true)
  const [editor, setEditor] = useState<{
    template?: EmailTemplate | null
    mode?: 'BUILDER' | 'HTML'
    purpose?: EmailTemplatePurpose
  } | null>(null)
  const [preview, setPreview] = useState<EmailTemplateCatalogItem | null>(null)
  const [providerTemplate, setProviderTemplate] = useState<EmailTemplateSummary | null>(null)

  const load = useCallback(async () => {
    if (!companyId) return
    setLoading(true)
    try {
      const [galleryResult, mineResult] = await Promise.allSettled([
        emailTemplateApi.catalog(),
        emailTemplateApi.list(companyId, { q: search.trim() || undefined, size: 48 }),
      ])

      if (galleryResult.status === 'fulfilled') {
        setCatalog(galleryResult.value)
      } else {
        setCatalog([])
        snackbar.error(
          apiErrorMessage(galleryResult.reason, 'Unable to load the template gallery'),
        )
      }

      if (mineResult.status === 'fulfilled') {
        setTemplates(mineResult.value.content)
      } else {
        setTemplates([])
        snackbar.error(
          apiErrorMessage(mineResult.reason, 'Unable to load your email templates'),
        )
      }
    } finally {
      setLoading(false)
    }
  }, [companyId, search, snackbar])

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 180)
    return () => window.clearTimeout(timer)
  }, [load])

  useEffect(() => {
    const create = searchParams.get('create')
    if (
      (create !== 'transactional' && create !== 'marketing') ||
      !can(PERMISSIONS.TEMPLATE_CREATE)
    ) {
      return
    }

    setEditor({
      mode: 'BUILDER',
      purpose: create === 'transactional' ? 'TRANSACTIONAL' : 'MARKETING',
    })
    setTab('mine')

    const next = new URLSearchParams(searchParams)
    next.delete('create')
    setSearchParams(next, { replace: true })
  }, [can, searchParams, setSearchParams])

  const edit = async (templateId: string) => {
    if (!companyId) return
    try {
      setEditor({ template: await emailTemplateApi.get(companyId, templateId) })
    } catch (error) {
      snackbar.error(apiErrorMessage(error, 'Unable to open email template'))
    }
  }

  const cloneCatalogTemplate = async (catalogId: string) => {
    if (!companyId) return
    try {
      const created = await emailTemplateApi.cloneCatalog(companyId, catalogId)
      snackbar.success('Template copied to My Templates.')
      setEditor({ template: created })
      setTab('mine')
      await load()
    } catch (error) {
      snackbar.error(apiErrorMessage(error, 'Unable to use this template'))
    }
  }

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Content</p>
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
              Email templates
            </h1>
            <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
              Start from a professionally designed marketing email, build your own, or paste production HTML.
            </p>
          </div>
          {can(PERMISSIONS.TEMPLATE_CREATE) && (
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                className="secondary-button inline-flex items-center gap-2"
                onClick={() => setEditor({ mode: 'HTML' })}
              >
                <FiCode /> Paste HTML
              </button>
              <button
                type="button"
                className="primary-button inline-flex items-center gap-2"
                onClick={() => setEditor({ mode: 'BUILDER' })}
              >
                <FiPlus /> Create blank
              </button>
            </div>
          )}
        </header>

        <div className="mt-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="inline-flex w-fit rounded-xl bg-slate-100 p-1">
            <button
              type="button"
              className={`rounded-lg px-4 py-2 text-xs font-bold ${
                tab === 'gallery' ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-500'
              }`}
              onClick={() => setTab('gallery')}
            >
              Template gallery
            </button>
            <button
              type="button"
              className={`rounded-lg px-4 py-2 text-xs font-bold ${
                tab === 'mine' ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-500'
              }`}
              onClick={() => setTab('mine')}
            >
              My templates
            </button>
          </div>
          {tab === 'mine' && (
            <label className="relative">
              <FiSearch className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                className="form-input min-w-[260px] pl-9"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search my templates"
              />
            </label>
          )}
        </div>

        {loading ? (
          <div className="mt-5 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
            {Array.from({ length: 6 }).map((_, index) => <CardSkeleton key={index} rows={4} />)}
          </div>
        ) : tab === 'gallery' ? (
          <div className="mt-5 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
            {catalog.map((item) => (
              <article
                key={item.id}
                className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm"
              >
                <EmailTemplatePreview html={item.previewHtml} compact title={item.name} />
                <div className="p-4">
                  <h2 className="text-sm font-bold text-slate-950">{item.name}</h2>
                  <div className="mt-1 flex flex-wrap gap-2 text-[10px] font-bold uppercase tracking-[0.08em]">
                    <span className="text-indigo-500">{item.category.replaceAll('_', ' ')}</span>
                    <span className={item.purpose === 'TRANSACTIONAL' ? 'text-emerald-600' : 'text-fuchsia-600'}>
                      {item.purpose}
                    </span>
                  </div>
                  <p className="mt-3 min-h-10 text-xs leading-5 text-slate-500">
                    {item.description}
                  </p>
                  <div className="mt-4 flex gap-2">
                    <button
                      type="button"
                      className="secondary-button flex-1"
                      onClick={() => setPreview(item)}
                    >
                      Preview
                    </button>
                    {can(PERMISSIONS.TEMPLATE_CREATE) && (
                      <button
                        type="button"
                        className="primary-button flex-1 inline-flex items-center justify-center gap-2"
                        onClick={() => void cloneCatalogTemplate(item.id)}
                      >
                        <FiCopy /> Use template
                      </button>
                    )}
                  </div>
                </div>
              </article>
            ))}
          </div>
        ) : templates.length === 0 ? (
          <div className="mt-5 rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-16 text-center">
            <FiFileText className="mx-auto text-3xl text-slate-300" />
            <p className="mt-4 text-sm font-bold text-slate-900">No email templates yet</p>
            <p className="mt-1 text-xs text-slate-500">
              Use a gallery template, start blank, or paste HTML.
            </p>
          </div>
        ) : (
          <div className="mt-5 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {templates.map((template) => (
              <article key={template.id} className="rounded-2xl border border-slate-200 bg-white p-5">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <h2 className="text-sm font-bold text-slate-950">{template.name}</h2>
                    <div className="mt-1 flex flex-wrap gap-2 text-[10px] font-bold uppercase tracking-[0.08em] text-slate-400">
                      <span>{template.category.replaceAll('_', ' ')}</span>
                      <span className={template.purpose === 'TRANSACTIONAL' ? 'text-emerald-600' : 'text-fuchsia-600'}>
                        {template.purpose}
                      </span>
                    </div>
                  </div>
                  <span
                    className={`rounded-full px-2 py-1 text-[9px] font-bold ${
                      template.status === 'PUBLISHED'
                        ? 'bg-emerald-50 text-emerald-700'
                        : 'bg-amber-50 text-amber-700'
                    }`}
                  >
                    {template.status}
                  </span>
                </div>
                <p className="mt-4 truncate text-xs font-semibold text-slate-700">
                  {template.subject || 'No subject yet'}
                </p>
                <div className="mt-3 flex flex-wrap gap-1.5">
                  {template.variables.slice(0, 5).map((variable) => (
                    <span
                      key={variable}
                      className="rounded-full bg-indigo-50 px-2 py-1 text-[9px] font-semibold text-indigo-700"
                    >
                      {'{{'}{variable}{'}}'}
                    </span>
                  ))}
                  {template.hasDraft && template.status === 'PUBLISHED' && (
                    <span className="rounded-full bg-blue-50 px-2 py-1 text-[9px] font-bold text-blue-700">
                      Unpublished changes
                    </span>
                  )}
                </div>
                <div className="mt-5 flex flex-wrap gap-2">
                  {can(PERMISSIONS.TEMPLATE_MODIFY) && (
                    <button
                      type="button"
                      className="secondary-button inline-flex items-center gap-2"
                      onClick={() => void edit(template.id)}
                    >
                      <FiEdit2 /> Edit
                    </button>
                  )}
                  {template.status === 'PUBLISHED' && (
                    <button
                      type="button"
                      className="secondary-button inline-flex items-center gap-2"
                      onClick={() => setProviderTemplate(template)}
                    >
                      <FiServer /> Providers
                    </button>
                  )}
                </div>
              </article>
            ))}
          </div>
        )}
      </div>

      {editor && companyId && (
        <EmailTemplateEditorModal
          companyId={companyId}
          template={editor.template}
          initialMode={editor.mode}
          initialPurpose={editor.purpose}
          onClose={() => setEditor(null)}
          onSaved={() => {
            setEditor(null)
            setTab('mine')
            void load()
          }}
        />
      )}

      {providerTemplate && companyId && (
        <EmailTemplateProviderModal
          companyId={companyId}
          template={providerTemplate}
          canModify={can(PERMISSIONS.TEMPLATE_MODIFY)}
          onClose={() => setProviderTemplate(null)}
        />
      )}

      {preview && (
        <div className="mobile-modal-shell fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 p-4">
          <div className="mobile-sheet max-h-[94vh] w-full max-w-3xl overflow-y-auto rounded-2xl bg-white p-4 shadow-xl">
            <div className="mb-4 flex items-center justify-between gap-4">
              <div>
                <p className="text-sm font-bold text-slate-950">{preview.name}</p>
                <p className="mt-1 text-xs text-slate-500">{preview.description}</p>
              </div>
              <button type="button" className="admin-back-button" onClick={() => setPreview(null)}>
                ×
              </button>
            </div>
            <EmailTemplatePreview html={preview.previewHtml} title={preview.name} />
          </div>
        </div>
      )}
    </section>
  )
}
