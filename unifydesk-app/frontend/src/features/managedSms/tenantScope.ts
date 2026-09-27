export const isManagedSmsTenantCurrent = (
  requestCompanyId: string,
  currentCompanyId: string | null | undefined,
) => Boolean(currentCompanyId) && requestCompanyId === currentCompanyId
