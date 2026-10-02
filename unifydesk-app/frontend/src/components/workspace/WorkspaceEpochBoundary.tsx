import { Fragment, type ReactNode } from 'react'
import { useAppSelector } from '../../store/hooks'
import {
  selectSelectedCompanyId,
  selectWorkspaceEpoch,
} from '../../store/workspace/workspaceSlice'

export default function WorkspaceEpochBoundary({ children }: { children: ReactNode }) {
  const companyId = useAppSelector(selectSelectedCompanyId)
  const epoch = useAppSelector(selectWorkspaceEpoch)

  return <Fragment key={`${companyId ?? 'no-workspace'}:${epoch}`}>{children}</Fragment>
}
