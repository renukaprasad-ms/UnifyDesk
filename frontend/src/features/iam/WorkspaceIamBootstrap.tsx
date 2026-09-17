import { useEffect } from 'react'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import {
  iamLoadFailed,
  iamLoadStarted,
  iamLoadSucceeded,
  selectIamState,
} from '../../store/iam/iamSlice'
import { selectSelectedCompanyId } from '../../store/workspace/workspaceSlice'
import { iamApi } from './iamApi'

export default function WorkspaceIamBootstrap({ children }: { children: React.ReactNode }) {
  const dispatch = useAppDispatch()
  const companyId = useAppSelector(selectSelectedCompanyId)
  const iam = useAppSelector(selectIamState)

  useEffect(() => {
    if (!companyId) return
    if (iam.companyId === companyId && (iam.status === 'loading' || iam.status === 'ready')) {
      return
    }

    let active = true
    dispatch(iamLoadStarted(companyId))
    iamApi
      .myPermissions(companyId)
      .then((response) => {
        if (!active) return
        dispatch(
          iamLoadSucceeded({
            companyId,
            owner: response.owner,
            permissions: response.permissions,
          }),
        )
      })
      .catch((error) => {
        if (!active) return
        dispatch(
          iamLoadFailed({
            companyId,
            error: apiErrorMessage(error, 'Unable to load workspace permissions'),
          }),
        )
      })

    return () => {
      active = false
    }
  }, [companyId, dispatch, iam.companyId, iam.status])

  if (!companyId) return null

  if (iam.companyId !== companyId || iam.status === 'idle' || iam.status === 'loading') {
    return (
      <div className="flex min-h-dvh items-center justify-center bg-white text-sm font-semibold text-slate-500">
        Loading workspace...
      </div>
    )
  }

  if (iam.status === 'error') {
    return (
      <div className="flex min-h-dvh items-center justify-center bg-white px-6">
        <div className="max-w-md text-center">
          <p className="text-sm font-semibold text-red-600">{iam.error}</p>
          <button
            type="button"
            className="secondary-button mt-4"
            onClick={() => dispatch(iamLoadStarted(companyId))}
          >
            Retry
          </button>
        </div>
      </div>
    )
  }

  return children
}
