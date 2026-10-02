import { useCallback, useLayoutEffect, useRef } from 'react'
import {
  isWorkspaceEpochCurrent,
  workspaceEpochToken,
  type WorkspaceEpochToken,
} from './workspaceEpoch'

export const useWorkspaceEpochScope = (
  companyId: string | null | undefined,
  epoch: number,
) => {
  const currentRef = useRef({ companyId, epoch })
  currentRef.current = { companyId, epoch }
  const activeRef = useRef(true)

  useLayoutEffect(() => {
    activeRef.current = true
    return () => {
      activeRef.current = false
    }
  }, [])

  const capture = useCallback(
    (): WorkspaceEpochToken | null =>
      workspaceEpochToken(currentRef.current.companyId, currentRef.current.epoch),
    [],
  )

  const isCurrent = useCallback(
    (token: WorkspaceEpochToken | null | undefined) =>
      activeRef.current &&
      isWorkspaceEpochCurrent(
        token,
        currentRef.current.companyId,
        currentRef.current.epoch,
      ),
    [],
  )

  return { capture, isCurrent }
}
