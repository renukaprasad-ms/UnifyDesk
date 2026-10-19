import { useEffect, useState } from 'react'
import { realtimeClient } from '../realtime/realtimeClient'
import { workspaceApi } from '../workspace/workspaceApi'
import { apiErrorMessage } from '../../lib/api/apiClient'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import {
  iamLoadFailed,
  iamLoadStarted,
  iamLoadSucceeded,
  selectIamState,
} from '../../store/iam/iamSlice'
import {
  selectSelectedCompanyId,
  setCompanies,
} from '../../store/workspace/workspaceSlice'
import { iamApi } from './iamApi'

export default function WorkspaceIamBootstrap({ children }: { children: React.ReactNode }) {
  const dispatch = useAppDispatch()
  const companyId = useAppSelector(selectSelectedCompanyId)
  const iam = useAppSelector(selectIamState)
  const [retryNonce, setRetryNonce] = useState(0)

  useEffect(() => {
    if (!companyId) return
    if (
      retryNonce === 0 &&
      iam.companyId === companyId &&
      (iam.status === 'loading' || iam.status === 'ready')
    ) {
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
        showGlobalSnackbar(apiErrorMessage(error, 'Unable to load workspace permissions'), { variant: 'error' })
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
  }, [companyId, dispatch, retryNonce])

  useEffect(() => {
    if (!companyId) return

    return realtimeClient.subscribe<{
      companyId: string
      reason: string
      membershipStatus?: string | null
    }>('company.access.changed', (event) => {
      if (event.payload.companyId !== companyId) return

      if (
        event.payload.membershipStatus === 'SUSPENDED' ||
        event.payload.membershipStatus === 'LEFT'
      ) {
        void workspaceApi
          .list()
          .then((companies) => dispatch(setCompanies(companies)))
          .catch(() => {
            setRetryNonce((value) => value + 1)
          })
        return
      }

      setRetryNonce((value) => value + 1)
    })
  }, [companyId, dispatch])

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
            onClick={() => setRetryNonce((value) => value + 1)}
          >
            Retry
          </button>
        </div>
      </div>
    )
  }

  return children
}
import { showGlobalSnackbar } from '../../components/feedback/SnackbarProvider'
