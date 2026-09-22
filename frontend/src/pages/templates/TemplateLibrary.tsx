import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import {
  FiArchive,
  FiCheckCircle,
  FiEdit2,
  FiFileText,
  FiHelpCircle,
  FiMail,
  FiMessageSquare,
  FiPlus,
  FiSearch,
  FiSmartphone,
} from 'react-icons/fi'
import MessageTemplateEditorModal from '../../components/templates/MessageTemplateEditorModal'
import TemplateCreateWizardModal from '../../components/templates/TemplateCreateWizardModal'
import TemplateCreationCompleteModal from '../../components/templates/TemplateCreationCompleteModal'
import TemplateFlowGuideModal from '../../components/templates/TemplateFlowGuideModal'
import { useSnackbar } from '../../components/feedback/SnackbarProvider'
import type { ChannelType } from '../../features/channel/types'
import { PERMISSIONS } from '../../features/iam/permissions'
import { useIam } from '../../features/iam/useIam'
import { templateApi } from '../../features/template/templateApi'
import type {
  MessageTemplate,
  MessageTemplateCategory,
  SaveMessageTemplateInput,
} from '../../features/template/types'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppSelector } from '../../store/hooks'
import { selectSelectedCompanyId } from '../../store/workspace/workspaceSlice'

const tabs: { type: ChannelType; label: string; icon: typeof FiMail }[] = [
  { type: 'EMAIL', label: 'Email', icon: FiMail },
  { type: 'WHATSAPP', label: 'WhatsApp', icon: FiMessageSquare },
  { type: 'SMS', label: 'SMS', icon: FiSmartphone },
]

const statusClass = (status: MessageTemplate['status']) => {
  if (status === 'ACTIVE') return 'bg-emerald-50 text-emerald-700'
  if (status === 'ARCHIVED') return 'bg-slate-100 text-slate-500'
  return 'bg-amber-50 text-amber-700'
}

const providerStatusClass = (status: MessageTemplate['providerStatus']) => {
  if (status === 'APPROVED') return 'bg-emerald-50 text-emerald-700'
  if (status === 'REJECTED' || status === 'DISABLED') return 'bg-rose-50 text-rose-700'
  if (status === 'PENDING_APPROVAL') return 'bg-blue-50 text-blue-700'
  return 'bg-slate-100 text-slate-500'
}

export default function TemplateLibrary() {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const navigate = useNavigate()
  const { can } = useIam()
  const snackbar = useSnackbar()
  const [channelType, setChannelType] = useState<ChannelType>('EMAIL')
  const [templates, setTemplates] = useState<MessageTemplate[]>([])
  const [search, setSearch] = useState('')
  const [includeArchived, setIncludeArchived] = useState(false)
  const [loading, setLoading] = useState(false)
  const [workingId, setWorkingId] = useState<string | null>(null)
  const [editorOpen, setEditorOpen] = useState(false)
  const [editing, setEditing] = useState<MessageTemplate | null>(null)
  const [createWizardOpen, setCreateWizardOpen] = useState(false)
  const [createChannelType, setCreateChannelType] = useState<ChannelType>('EMAIL')
  const [createCategory, setCreateCategory] = useState<MessageTemplateCategory | null>(null)
  const [createdTemplate, setCreatedTemplate] = useState<MessageTemplate | null>(null)
  const [flowGuideOpen, setFlowGuideOpen] = useState(false)

  const load = async () => {
    if (!companyId) return
    setLoading(true)
    try {
      setTemplates(await templateApi.list(companyId, channelType, includeArchived))
    } catch (requestError) {
      snackbar.error(apiErrorMessage(requestError, 'Unable to load templates'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [companyId, channelType, includeArchived])

  const visibleTemplates = useMemo(() => {
    const query = search.trim().toLowerCase()
    if (!query) return templates
    return templates.filter(
      (template) =>
        template.name.toLowerCase().includes(query) ||
        template.bodyText.toLowerCase().includes(query) ||
        template.subject?.toLowerCase().includes(query),
    )
  }, [search, templates])

  const openCreate = () => {
    setEditing(null)
    setCreateChannelType(channelType)
    setCreateCategory(null)
    setCreateWizardOpen(true)
    setEditorOpen(false)
  }

  const continueCreateFlow = () => {
    if (!createCategory) return
    setCreateWizardOpen(false)
    setEditing(null)
    setEditorOpen(true)
  }

  const save = async (input: SaveMessageTemplateInput, activateAfterSave: boolean) => {
    if (!companyId) return
    const creating = !editing
    setWorkingId(editing?.id ?? 'new')
    try {
      let saved = editing
        ? await templateApi.update(companyId, editing.id, input)
        : await templateApi.create(companyId, {
            ...input,
            channelType,
          })

      if (
        activateAfterSave &&
        saved.channelType !== 'WHATSAPP' &&
        saved.status !== 'ACTIVE'
      ) {
        saved = await templateApi.activate(companyId, saved.id)
      }

      snackbar.success(editing ? 'Template updated.' : 'Template created.')
      setEditorOpen(false)
      setEditing(null)
      if (creating) {
        setCreatedTemplate(saved)
        setChannelType(saved.channelType)
      }
      await load()
    } catch (requestError) {
      snackbar.error(apiErrorMessage(requestError, 'Unable to save template'))
    } finally {
      setWorkingId(null)
    }
  }

  const activate = async (template: MessageTemplate) => {
    if (!companyId) return
    setWorkingId(template.id)
    try {
      await templateApi.activate(companyId, template.id)
      snackbar.success('Template activated.')
      await load()
    } catch (requestError) {
      snackbar.error(apiErrorMessage(requestError, 'Unable to activate template'))
    } finally {
      setWorkingId(null)
    }
  }

  const moveToDraft = async (template: MessageTemplate) => {
    if (!companyId) return
    setWorkingId(template.id)
    try {
      await templateApi.moveToDraft(companyId, template.id)
      snackbar.success('Template moved to draft.')
      await load()
    } catch (requestError) {
      snackbar.error(apiErrorMessage(requestError, 'Unable to update template'))
    } finally {
      setWorkingId(null)
    }
  }

  const archive = async (template: MessageTemplate) => {
    if (!companyId || !window.confirm(`Archive "${template.name}"?`)) return
    setWorkingId(template.id)
    try {
      await templateApi.archive(companyId, template.id)
      snackbar.success('Template archived.')
      await load()
    } catch (requestError) {
      snackbar.error(apiErrorMessage(requestError, 'Unable to archive template'))
    } finally {
      setWorkingId(null)
    }
  }

  return (
    <section className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-6xl">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Content</p>
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
              Templates
            </h1>
            <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
              Reusable content for Email, WhatsApp and SMS. Provider approval remains separate from your local template library.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              className="secondary-button inline-flex items-center gap-2"
              onClick={() => setFlowGuideOpen(true)}
            >
              <FiHelpCircle aria-hidden="true" />
              How it works
            </button>
            {can(PERMISSIONS.TEMPLATE_CREATE) && (
              <button type="button" className="primary-button inline-flex items-center gap-2" onClick={openCreate}>
                <FiPlus aria-hidden="true" />
                New template
              </button>
            )}
          </div>
        </header>

        <div className="mt-6 grid gap-3 md:grid-cols-4">
          {[
            ['1', 'Create', 'Choose Email, WhatsApp or SMS and the template purpose.'],
            ['2', 'Personalize', 'Add reusable content and variables such as {{first_name}}.'],
            ['3', 'Approve when needed', 'WhatsApp and provider-backed templates keep approval state separate.'],
            ['4', 'Use', 'Select active Email/SMS templates directly from the Broadcast composer.'],
          ].map(([step, title, description]) => (
            <div key={step} className="rounded-2xl border border-slate-200 bg-white p-4">
              <span className="inline-flex size-7 items-center justify-center rounded-full bg-indigo-50 text-[11px] font-bold text-indigo-700">
                {step}
              </span>
              <p className="mt-3 text-xs font-bold text-slate-900">{title}</p>
              <p className="mt-1 text-[11px] leading-5 text-slate-500">{description}</p>
            </div>
          ))}
        </div>

        <div className="panel mt-6 overflow-hidden">
        <div className="flex flex-col gap-4 border-b border-slate-100 p-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex flex-wrap gap-2">
            {tabs.map((tab) => {
              const Icon = tab.icon
              const active = channelType === tab.type
              return (
                <button
                  key={tab.type}
                  type="button"
                  className={`inline-flex items-center gap-2 rounded-xl px-3 py-2 text-xs font-bold transition ${
                    active
                      ? 'bg-indigo-50 text-indigo-700'
                      : 'text-slate-500 hover:bg-slate-50 hover:text-slate-800'
                  }`}
                  onClick={() => setChannelType(tab.type)}
                >
                  <Icon aria-hidden="true" />
                  {tab.label}
                </button>
              )
            })}
          </div>

          <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
            <label className="relative">
              <FiSearch
                className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                aria-hidden="true"
              />
              <input
                className="form-input min-w-[240px] pl-9"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search templates"
              />
            </label>
            <label className="flex items-center gap-2 text-xs font-semibold text-slate-500">
              <input
                type="checkbox"
                checked={includeArchived}
                onChange={(event) => setIncludeArchived(event.target.checked)}
              />
              Show archived
            </label>
          </div>
        </div>

        {loading ? (
          <div className="px-5 py-14 text-center text-sm text-slate-500">Loading templates…</div>
        ) : visibleTemplates.length === 0 ? (
          <div className="px-5 py-16 text-center">
            <FiFileText className="mx-auto text-3xl text-slate-300" aria-hidden="true" />
            <p className="mt-4 text-sm font-bold text-slate-800">
              No {channelType.toLowerCase()} templates yet
            </p>
            <p className="mt-1 text-xs text-slate-500">
              Create reusable content once and select it when composing a Broadcast.
            </p>
            {can(PERMISSIONS.TEMPLATE_CREATE) && (
              <button type="button" className="primary-button mt-5 inline-flex items-center gap-2" onClick={openCreate}>
                <FiPlus aria-hidden="true" />
                Create template
              </button>
            )}
          </div>
        ) : (
          <div className="grid gap-4 p-4 xl:grid-cols-2">
            {visibleTemplates.map((template) => (
              <article key={template.id} className="rounded-2xl border border-slate-200 bg-white p-4">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <h2 className="truncate text-sm font-bold text-slate-950">{template.name}</h2>
                      <span className={`rounded-full px-2 py-1 text-[9px] font-bold ${statusClass(template.status)}`}>
                        {template.status}
                      </span>
                      {template.channelType === 'WHATSAPP' && (
                        <span className={`rounded-full px-2 py-1 text-[9px] font-bold ${providerStatusClass(template.providerStatus)}`}>
                          {template.providerStatus.replaceAll('_', ' ')}
                        </span>
                      )}
                    </div>
                    <p className="mt-1 text-[10px] font-semibold uppercase tracking-[0.08em] text-slate-400">
                      {template.category.replaceAll('_', ' ')} · {template.language || 'default'}
                    </p>
                  </div>
                </div>

                {template.subject && (
                  <p className="mt-4 truncate text-xs font-bold text-slate-800">{template.subject}</p>
                )}
                {template.headerText && (
                  <p className="mt-4 text-xs font-bold text-slate-800">{template.headerText}</p>
                )}
                <p className="mt-2 line-clamp-4 whitespace-pre-wrap text-xs leading-5 text-slate-600">
                  {template.bodyText}
                </p>
                {template.footerText && (
                  <p className="mt-2 text-[10px] text-slate-400">{template.footerText}</p>
                )}

                <div className="mt-4 flex flex-wrap gap-1.5">
                  {template.variables.map((variable) => (
                    <span key={variable} className="rounded-full bg-indigo-50 px-2 py-1 text-[9px] font-semibold text-indigo-700">
                      {'{{'}{variable}{'}}'}
                    </span>
                  ))}
                  {template.variables.length === 0 && (
                    <span className="text-[10px] text-slate-400">No variables</span>
                  )}
                </div>

                {template.status !== 'ARCHIVED' && (
                  <div className="mt-5 flex flex-wrap gap-2 border-t border-slate-100 pt-4">
                    {can(PERMISSIONS.TEMPLATE_MODIFY) && (
                      <button
                        type="button"
                        className="secondary-button inline-flex items-center gap-2"
                        onClick={() => {
                          setEditing(template)
                          setEditorOpen(true)
                        }}
                      >
                        <FiEdit2 aria-hidden="true" />
                        Edit
                      </button>
                    )}

                    {can(PERMISSIONS.TEMPLATE_MODIFY) &&
                      template.channelType !== 'WHATSAPP' &&
                      template.status === 'DRAFT' && (
                        <button
                          type="button"
                          className="secondary-button inline-flex items-center gap-2"
                          disabled={workingId === template.id}
                          onClick={() => void activate(template)}
                        >
                          <FiCheckCircle aria-hidden="true" />
                          Activate
                        </button>
                      )}

                    {can(PERMISSIONS.TEMPLATE_MODIFY) && template.status === 'ACTIVE' && (
                      <button
                        type="button"
                        className="secondary-button"
                        disabled={workingId === template.id}
                        onClick={() => void moveToDraft(template)}
                      >
                        Move to draft
                      </button>
                    )}

                    {can(PERMISSIONS.TEMPLATE_DELETE) && (
                      <button
                        type="button"
                        className="secondary-button inline-flex items-center gap-2 text-rose-600"
                        disabled={workingId === template.id}
                        onClick={() => void archive(template)}
                      >
                        <FiArchive aria-hidden="true" />
                        Archive
                      </button>
                    )}
                  </div>
                )}

                {template.channelType === 'WHATSAPP' && template.providerStatus === 'LOCAL_ONLY' && (
                  <div className="mt-4 rounded-xl border border-amber-100 bg-amber-50 px-3 py-2 text-[10px] leading-4 text-amber-700">
                    Local draft only. Meta submission and approval sync will be added in the WhatsApp provider phase.
                  </div>
                )}
              </article>
            ))}
          </div>
        )}
        </div>
      </div>

      {createWizardOpen && (
        <TemplateCreateWizardModal
          channelType={createChannelType}
          category={createCategory}
          onChannelChange={(nextChannel) => {
            setCreateChannelType(nextChannel)
            setCreateCategory(null)
          }}
          onCategoryChange={setCreateCategory}
          onBack={() => setCreateCategory(null)}
          onClose={() => {
            setCreateWizardOpen(false)
            setCreateCategory(null)
          }}
          onContinue={continueCreateFlow}
        />
      )}

      {editorOpen && companyId && (
        <MessageTemplateEditorModal
          companyId={companyId}
          channelType={editing?.channelType ?? createChannelType}
          template={editing}
          initialCategory={editing ? undefined : createCategory ?? undefined}
          creationFlow={!editing}
          busy={workingId !== null}
          onBack={
            editing
              ? undefined
              : () => {
                  setEditorOpen(false)
                  setCreateWizardOpen(true)
                }
          }
          onClose={() => {
            setEditorOpen(false)
            setEditing(null)
            setCreateCategory(null)
          }}
          onSave={(input, activateAfterSave) => void save(input, activateAfterSave)}
        />
      )}

      {flowGuideOpen && (
        <TemplateFlowGuideModal onClose={() => setFlowGuideOpen(false)} />
      )}

      {createdTemplate && (
        <TemplateCreationCompleteModal
          template={createdTemplate}
          onClose={() => {
            setCreatedTemplate(null)
            setCreateCategory(null)
          }}
          onCreateAnother={() => {
            const nextChannel = createdTemplate.channelType
            setCreatedTemplate(null)
            setCreateChannelType(nextChannel)
            setCreateCategory(null)
            setCreateWizardOpen(true)
          }}
          onUseInBroadcast={() => {
            const path =
              createdTemplate.channelType === 'EMAIL' ? '/broadcast/email' : '/broadcast/sms'
            setCreatedTemplate(null)
            navigate(path)
          }}
        />
      )}
    </section>
  )
}
