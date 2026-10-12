import axios from 'axios'
import apiClient from '../../lib/api/apiClient'
import type { ApiResponse } from '../../lib/api/types'
import {
  createIdempotencyKey,
  type ContactMarketingPreferences,
} from './contactModel'
import type {
  AsyncJob,
  BulkOperation,
  CommunicationPreference,
  ContactActivity,
  ContactDashboardSummary,
  ContactDetail,
  ContactExport,
  ContactIdentity,
  ContactImport,
  ContactPatchInput,
  ContactQuery,
  ContactSelection,
  CreateSuppressionInput,
  ContactSummary,
  ContactWriteInput,
  CursorPage,
  CustomFieldDefinition,
  ExportDownload,
  ExportRequest,
  ImportCommitInput,
  ImportErrors,
  ImportPreview,
  LiftSuppressionInput,
  ListSummary,
  SegmentDefinition,
  SegmentGroup,
  SegmentPreview,
  SegmentValidation,
  Suppression,
  TagSummary,
} from './types'

const unwrap = <T>(response: ApiResponse<T>) => {
  if (response.data === undefined) throw new Error(response.error_message ?? 'Empty API response')
  return response.data
}

const base = (companyId: string) => '/api/v1/companies/' + companyId + '/contacts'

export const contactsApi = {
  async list(companyId: string, query: ContactQuery, cursor?: string | null) {
    const response = await apiClient.get<ApiResponse<CursorPage<ContactSummary>>>(base(companyId), {
      params: { ...query, cursor: cursor || undefined },
    })
    return unwrap(response.data)
  },

  async dashboardSummary(companyId: string) {
    const response = await apiClient.get<ApiResponse<ContactDashboardSummary>>(
      base(companyId) + '/dashboard-summary',
    )
    return unwrap(response.data)
  },

  async get(companyId: string, contactId: string) {
    const response = await apiClient.get<ApiResponse<ContactDetail>>(
      base(companyId) + '/' + contactId,
    )
    return unwrap(response.data)
  },

  async create(companyId: string, payload: ContactWriteInput) {
    const response = await apiClient.post<ApiResponse<ContactDetail>>(base(companyId), payload)
    return unwrap(response.data)
  },

  async update(companyId: string, contactId: string, payload: ContactPatchInput) {
    const response = await apiClient.patch<ApiResponse<ContactDetail>>(
      base(companyId) + '/' + contactId,
      payload,
    )
    return unwrap(response.data)
  },

  async delete(companyId: string, contactId: string) {
    await apiClient.delete(base(companyId) + '/' + contactId)
  },

  async addIdentity(
    companyId: string,
    contactId: string,
    payload: {
      type: ContactIdentity['type']
      value: string
      namespace?: string | null
      primary?: boolean
    },
  ) {
    const response = await apiClient.post<ApiResponse<ContactIdentity>>(
      base(companyId) + '/' + contactId + '/identities',
      payload,
    )
    return unwrap(response.data)
  },

  async setPrimaryIdentity(companyId: string, contactId: string, identityId: string) {
    const response = await apiClient.post<ApiResponse<ContactIdentity>>(
      base(companyId) + '/' + contactId + '/identities/' + identityId + '/primary',
    )
    return unwrap(response.data)
  },

  async removeIdentity(companyId: string, contactId: string, identityId: string) {
    await apiClient.delete(base(companyId) + '/' + contactId + '/identities/' + identityId)
  },

  async tags(companyId: string) {
    const response = await apiClient.get<ApiResponse<TagSummary[]>>(base(companyId) + '/tags')
    return unwrap(response.data)
  },

  async createTag(companyId: string, payload: { name: string; color?: string | null }) {
    const response = await apiClient.post<ApiResponse<TagSummary>>(
      base(companyId) + '/tags',
      payload,
    )
    return unwrap(response.data)
  },

  async updateTag(companyId: string, tagId: string, payload: { name: string; color?: string | null }) {
    const response = await apiClient.patch<ApiResponse<TagSummary>>(
      base(companyId) + '/tags/' + tagId,
      payload,
    )
    return unwrap(response.data)
  },

  async archiveTag(companyId: string, tagId: string) {
    await apiClient.delete(base(companyId) + '/tags/' + tagId)
  },

  async addTag(companyId: string, contactId: string, tagId: string) {
    await apiClient.post(base(companyId) + '/' + contactId + '/tags/' + tagId)
  },

  async removeTag(companyId: string, contactId: string, tagId: string) {
    await apiClient.delete(base(companyId) + '/' + contactId + '/tags/' + tagId)
  },

  async customFields(companyId: string) {
    const response = await apiClient.get<ApiResponse<CustomFieldDefinition[]>>(
      base(companyId) + '/custom-fields',
    )
    return unwrap(response.data)
  },

  async createCustomField(
    companyId: string,
    payload: {
      fieldKey: string
      label: string
      type: CustomFieldDefinition['type']
      options?: string[] | null
      position?: number
    },
  ) {
    const response = await apiClient.post<ApiResponse<CustomFieldDefinition>>(
      base(companyId) + '/custom-fields',
      payload,
    )
    return unwrap(response.data)
  },

  async updateCustomField(
    companyId: string,
    fieldId: string,
    payload: { label: string; options?: string[] | null; position?: number },
  ) {
    const response = await apiClient.patch<ApiResponse<CustomFieldDefinition>>(
      base(companyId) + '/custom-fields/' + fieldId,
      payload,
    )
    return unwrap(response.data)
  },

  async archiveCustomField(companyId: string, fieldId: string) {
    await apiClient.delete(base(companyId) + '/custom-fields/' + fieldId)
  },

  async lists(companyId: string) {
    const response = await apiClient.get<ApiResponse<ListSummary[]>>(base(companyId) + '/lists')
    return unwrap(response.data)
  },

  async createList(companyId: string, payload: { name: string; description?: string | null }) {
    const response = await apiClient.post<ApiResponse<ListSummary>>(
      base(companyId) + '/lists',
      payload,
    )
    return unwrap(response.data)
  },

  async getList(companyId: string, listId: string) {
    const response = await apiClient.get<ApiResponse<ListSummary>>(
      base(companyId) + '/lists/' + listId,
    )
    return unwrap(response.data)
  },

  async updateList(
    companyId: string,
    listId: string,
    payload: { name?: string; description?: string | null },
  ) {
    const response = await apiClient.patch<ApiResponse<ListSummary>>(
      base(companyId) + '/lists/' + listId,
      payload,
    )
    return unwrap(response.data)
  },

  async archiveList(companyId: string, listId: string) {
    await apiClient.delete(base(companyId) + '/lists/' + listId)
  },

  async listMembers(companyId: string, listId: string, cursor?: string | null, size = 50) {
    const response = await apiClient.get<ApiResponse<CursorPage<ContactSummary>>>(
      base(companyId) + '/lists/' + listId + '/members',
      { params: { cursor: cursor || undefined, size } },
    )
    return unwrap(response.data)
  },

  async addListMembers(companyId: string, listId: string, contactIds: string[]) {
    const response = await apiClient.post<ApiResponse<{ added: number }>>(
      base(companyId) + '/lists/' + listId + '/members',
      { contactIds },
    )
    return unwrap(response.data)
  },

  async removeListMember(companyId: string, listId: string, contactId: string) {
    await apiClient.delete(base(companyId) + '/lists/' + listId + '/members/' + contactId)
  },

  async segments(companyId: string) {
    const response = await apiClient.get<ApiResponse<SegmentDefinition[]>>(
      base(companyId) + '/segments',
    )
    return unwrap(response.data)
  },

  async createSegment(
    companyId: string,
    payload: { name: string; description?: string | null; filterVersion: 1; filter: SegmentGroup },
  ) {
    const response = await apiClient.post<ApiResponse<SegmentDefinition>>(
      base(companyId) + '/segments',
      payload,
    )
    return unwrap(response.data)
  },

  async getSegment(companyId: string, segmentId: string) {
    const response = await apiClient.get<ApiResponse<SegmentDefinition>>(
      base(companyId) + '/segments/' + segmentId,
    )
    return unwrap(response.data)
  },

  async updateSegment(
    companyId: string,
    segmentId: string,
    payload: {
      name: string
      description?: string | null
      filterVersion: 1
      filter: SegmentGroup
      version: number
    },
  ) {
    const response = await apiClient.put<ApiResponse<SegmentDefinition>>(
      base(companyId) + '/segments/' + segmentId,
      payload,
    )
    return unwrap(response.data)
  },

  async archiveSegment(companyId: string, segmentId: string) {
    await apiClient.delete(base(companyId) + '/segments/' + segmentId)
  },

  async validateSegment(companyId: string, filter: SegmentGroup) {
    const response = await apiClient.post<ApiResponse<SegmentValidation>>(
      base(companyId) + '/segments/validate',
      { filterVersion: 1, filter },
    )
    return unwrap(response.data)
  },

  async previewSegment(companyId: string, filter: SegmentGroup, sampleSize = 20) {
    const response = await apiClient.post<ApiResponse<SegmentPreview>>(
      base(companyId) + '/segments/preview',
      { filterVersion: 1, filter, sampleSize: Math.min(sampleSize, 20) },
    )
    return unwrap(response.data)
  },

  async refreshSegmentCount(companyId: string, segmentId: string) {
    const response = await apiClient.post<ApiResponse<AsyncJob>>(
      base(companyId) + '/segments/' + segmentId + '/refresh-count',
    )
    return unwrap(response.data)
  },

  async preferences(companyId: string, contactId: string) {
    const response = await apiClient.get<ApiResponse<CommunicationPreference[]>>(
      base(companyId) + '/' + contactId + '/preferences',
    )
    return unwrap(response.data)
  },

  async applyMarketingPreferences(
    companyId: string,
    contactId: string,
    preferences: ContactMarketingPreferences,
    reason: string,
  ) {
    await Promise.all(
      (Object.entries(preferences) as Array<
        [CommunicationPreference['channel'], CommunicationPreference['state']]
      >)
        .filter(([, state]) => state !== 'UNKNOWN')
        .map(([channel, state]) =>
          contactsApi.updatePreference(companyId, contactId, channel, state, reason),
        ),
    )
  },

  async updatePreference(
    companyId: string,
    contactId: string,
    channel: CommunicationPreference['channel'],
    state: CommunicationPreference['state'],
    reason: string,
    identityId?: string | null,
  ) {
    const response = await apiClient.put<ApiResponse<CommunicationPreference>>(
      base(companyId) + '/' + contactId + '/preferences/' + channel + '/MARKETING',
      {
        state,
        identityId: identityId || null,
        source: 'USER_UI',
        sourceRef: null,
        occurredAt: new Date().toISOString(),
        reason,
        idempotencyKey: createIdempotencyKey(),
      },
    )
    return unwrap(response.data)
  },

  async suppressions(companyId: string, contactId: string) {
    const response = await apiClient.get<ApiResponse<Suppression[]>>(
      base(companyId) + '/' + contactId + '/suppressions',
    )
    return unwrap(response.data)
  },

  async createSuppression(companyId: string, contactId: string, payload: CreateSuppressionInput) {
    const response = await apiClient.post<ApiResponse<Suppression>>(
      base(companyId) + '/' + contactId + '/suppressions',
      payload,
    )
    return unwrap(response.data)
  },

  async liftSuppression(
    companyId: string,
    contactId: string,
    suppressionId: string,
    payload: LiftSuppressionInput,
  ) {
    const response = await apiClient.post<ApiResponse<Suppression>>(
      base(companyId) + '/' + contactId + '/suppressions/' + suppressionId + '/lift',
      payload,
    )
    return unwrap(response.data)
  },

  async activity(companyId: string, contactId: string, cursor?: string | null, size = 30) {
    const response = await apiClient.get<ApiResponse<CursorPage<ContactActivity>>>(
      base(companyId) + '/' + contactId + '/activity',
      { params: { cursor: cursor || undefined, size } },
    )
    return unwrap(response.data)
  },

  async bulk(
    companyId: string,
    selection: ContactSelection,
    operation: BulkOperation,
    idempotencyKey: string,
  ) {
    const response = await apiClient.post<ApiResponse<AsyncJob | { updated: number }>>(
      base(companyId) + '/bulk-operations',
      { selection, operation, idempotencyKey },
    )
    return unwrap(response.data)
  },

  async createImport(companyId: string, file: File) {
    const response = await apiClient.post<ApiResponse<ContactImport>>(
      base(companyId) + '/imports',
      {
        fileName: file.name,
        contentType: file.type || 'text/csv',
        sizeBytes: file.size,
      },
    )
    return unwrap(response.data)
  },

  async uploadImportFile(intent: ContactImport, file: File) {
    if (intent.upload?.url) {
      const result = await fetch(intent.upload.url, {
        method: intent.upload.method ?? 'PUT',
        headers: intent.upload.headers,
        body: file,
      })
      if (!result.ok) throw new Error('Unable to upload CSV file.')
      return
    }
    if (intent.serverUploadUrl) {
      const form = new FormData()
      form.append('file', file)
      await apiClient.post(intent.serverUploadUrl, form)
      return
    }
    throw new Error('Import upload contract is unavailable.')
  },

  async previewImport(companyId: string, importId: string) {
    const response = await apiClient.post<ApiResponse<ImportPreview>>(
      base(companyId) + '/imports/' + importId + '/preview',
    )
    return unwrap(response.data)
  },

  async commitImport(companyId: string, importId: string, payload: ImportCommitInput) {
    const response = await apiClient.post<ApiResponse<AsyncJob>>(
      base(companyId) + '/imports/' + importId + '/commit',
      payload,
    )
    return unwrap(response.data)
  },

  async getImport(companyId: string, importId: string) {
    const response = await apiClient.get<ApiResponse<ContactImport>>(
      base(companyId) + '/imports/' + importId,
    )
    return unwrap(response.data)
  },

  async importErrors(companyId: string, importId: string) {
    const response = await apiClient.get<ApiResponse<ImportErrors>>(
      base(companyId) + '/imports/' + importId + '/errors',
    )
    return unwrap(response.data)
  },

  async createExport(companyId: string, payload: ExportRequest) {
    const response = await apiClient.post<ApiResponse<ContactExport>>(
      base(companyId) + '/exports',
      payload,
    )
    return unwrap(response.data)
  },

  async getExport(companyId: string, exportId: string) {
    const response = await apiClient.get<ApiResponse<ContactExport>>(
      base(companyId) + '/exports/' + exportId,
    )
    return unwrap(response.data)
  },

  async exportDownload(companyId: string, exportId: string) {
    const response = await apiClient.get<ApiResponse<ExportDownload>>(
      base(companyId) + '/exports/' + exportId + '/download',
    )
    return unwrap(response.data)
  },
}

export const isContactsErrorCode = (error: unknown, code: string) => {
  if (!axios.isAxiosError<ApiResponse<never>>(error)) return false
  const response = error.response
  if (!response) return false
  const values = [response.data?.error_message, response.data?.message].filter(
    (value): value is string => Boolean(value),
  )
  return values.some((value) => value === code || value.includes(code))
}

export const isContactVersionConflict = (error: unknown) =>
  isContactsErrorCode(error, 'CONTACT_VERSION_CONFLICT')
