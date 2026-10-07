export type AsyncJobStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'
export type AsyncJobType =
  'GMAIL_SYNC' | 'MICROSOFT_SYNC' | 'ZOHO_MAIL_SYNC' | 'IMAP_SMTP_SYNC' | 'INBOX_READ_SYNC' | 'ENTITY_CLEANUP'

export type AsyncJob = {
  jobId: string
  companyId?: string | null
  userId: string
  jobType: AsyncJobType
  status: AsyncJobStatus
  totalUnits: number
  completedUnits: number
  progressPercent: number
  statusMessage?: string | null
  errorMessage?: string | null
  createdAt: string
  startedAt?: string | null
  completedAt?: string | null
  updatedAt: string
}
