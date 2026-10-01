export type EmailTemplateCategory =
  | 'PROMOTIONAL'
  | 'NEWSLETTER'
  | 'WELCOME'
  | 'PRODUCT_LAUNCH'
  | 'EVENT'
  | 'ANNOUNCEMENT'
  | 'SEASONAL'
  | 'MINIMAL'

export type EmailTemplateStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
export type EmailTemplatePurpose = 'MARKETING' | 'TRANSACTIONAL'
export type EmailTemplateEditorMode = 'BUILDER' | 'HTML'
export type EmailTemplateProviderBindingStatus =
  'SYNCING' | 'PENDING_REVIEW' | 'SYNCED' | 'READY' | 'REJECTED' | 'FAILED'

export type EmailBlock =
  | { id: string; type: 'HEADER'; brandText?: string; logoUrl?: string }
  | { id: string; type: 'HERO'; heading?: string; text?: string; imageUrl?: string }
  | { id: string; type: 'HEADING'; text: string }
  | { id: string; type: 'TEXT'; text: string }
  | { id: string; type: 'IMAGE'; imageUrl?: string }
  | { id: string; type: 'BUTTON'; text: string; url: string }
  | { id: string; type: 'DIVIDER' }
  | { id: string; type: 'SPACER' }
  | { id: string; type: 'FOOTER'; text: string }

export type EmailDocument = {
  schemaVersion: 1
  settings: {
    width: number
    backgroundColor: string
    contentBackgroundColor: string
    accentColor: string
  }
  blocks: EmailBlock[]
}

export type EmailTemplateSummary = {
  id: string
  name: string
  category: EmailTemplateCategory
  purpose: EmailTemplatePurpose
  publishedPurpose?: EmailTemplatePurpose | null
  status: EmailTemplateStatus
  currentVersion: number
  publishedVersion?: number | null
  hasDraft: boolean
  editorMode: EmailTemplateEditorMode
  subject?: string | null
  variables: string[]
  createdAt: string
  updatedAt: string
}

export type EmailTemplate = EmailTemplateSummary & {
  currentVersionId?: string | null
  publishedVersionId?: string | null
  preheader?: string | null
  documentJson?: string | null
  htmlSource?: string | null
  compiledHtml: string
  compiledText: string
  archivedAt?: string | null
}

export type EmailTemplateCatalogItem = {
  id: string
  slug: string
  name: string
  description: string
  category: EmailTemplateCategory
  purpose: EmailTemplatePurpose
  editorMode: EmailTemplateEditorMode
  subject?: string | null
  preheader?: string | null
  documentJson?: string | null
  htmlSource?: string | null
  previewHtml: string
  variables: string[]
}

export type SaveEmailTemplateInput = {
  name: string
  category: EmailTemplateCategory
  purpose: EmailTemplatePurpose
  editorMode: EmailTemplateEditorMode
  subject?: string | null
  preheader?: string | null
  documentJson?: string | null
  htmlSource?: string | null
  previewValues?: Record<string, string>
}

export type EmailTemplatePreview = {
  subject?: string | null
  preheader?: string | null
  html: string
  text: string
  variables: string[]
  unresolvedVariables: string[]
}

export type Page<T> = {
  content: T[]
  number: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export type EmailTemplateProviderBinding = {
  id: string
  templateId: string
  templateVersionId: string
  templateVersion: number
  connectionId: string
  provider: 'MSG91_EMAIL'
  providerTemplateId?: string | null
  providerTemplateSlug: string
  status: EmailTemplateProviderBindingStatus
  sendable: boolean
  providerStatus?: string | null
  errorCode?: string | null
  errorMessage?: string | null
  syncedAt?: string | null
  lastCheckedAt?: string | null
  updatedAt: string
}
