export type WorkspaceEpochToken = {
  companyId: string
  epoch: number
}

export const workspaceEpochToken = (
  companyId: string | null | undefined,
  epoch: number,
): WorkspaceEpochToken | null => (companyId ? { companyId, epoch } : null)

export const isWorkspaceEpochCurrent = (
  token: WorkspaceEpochToken | null | undefined,
  companyId: string | null | undefined,
  epoch: number,
) => Boolean(token && companyId && token.companyId === companyId && token.epoch === epoch)
