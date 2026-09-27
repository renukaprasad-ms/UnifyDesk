import axios from 'axios'
import { useEffect, useState } from 'react'
import { FiBell, FiLoader } from 'react-icons/fi'
import { useNavigate, useSearchParams } from 'react-router'
import { notificationApi } from '../../features/notifications/notificationApi'
import { isSafeAppRoute, isStringId } from '../../features/notifications/notificationUtils'
import { workspaceApi } from '../../features/workspace/workspaceApi'
import { useAppDispatch } from '../../store/hooks'
import { selectCompany, setCompanies } from '../../store/workspace/workspaceSlice'

export default function NotificationOpenPage() {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [unavailable, setUnavailable] = useState(false)
  const notificationId = searchParams.get('n')
  const companyId = searchParams.get('c')

  useEffect(() => {
    let active = true

    const open = async () => {
      if (!isStringId(notificationId) || (companyId !== null && !isStringId(companyId))) {
        if (active) setUnavailable(true)
        return
      }

      try {
        if (companyId) {
          const companies = await workspaceApi.list()
          if (!active) return
          const allowed = companies.some((company) => company.companyId === companyId)
          if (!allowed) {
            setUnavailable(true)
            return
          }
          dispatch(setCompanies(companies))
          dispatch(selectCompany(companyId))
        }

        const notification = await notificationApi.get(notificationId, companyId)
        if (!active) return

        if (notification.unread) {
          await notificationApi.setReadState(notification.id, companyId, true)
          if (!active) return
        }

        if (!isSafeAppRoute(notification.action?.path)) {
          setUnavailable(true)
          return
        }

        navigate(notification.action.path, { replace: true })
      } catch (error) {
        if (!active) return
        const status = axios.isAxiosError(error) ? error.response?.status : undefined
        if (status === 403 || status === 404 || status === 400) {
          setUnavailable(true)
          return
        }
        setUnavailable(true)
      }
    }

    void open()
    return () => {
      active = false
    }
  }, [companyId, dispatch, navigate, notificationId])

  if (unavailable) {
    return (
      <main className="flex min-h-dvh items-center justify-center bg-slate-50 px-4">
        <div className="panel w-full max-w-lg p-8 text-center">
          <FiBell aria-hidden="true" className="mx-auto text-3xl text-slate-300" />
          <h1 className="mt-4 text-xl font-bold text-slate-950">
            This notification is no longer available
          </h1>
          <p className="mt-2 text-sm leading-6 text-slate-500">
            It may have expired, or your workspace access may have changed.
          </p>
          <button type="button" className="primary-button mt-5" onClick={() => navigate('/workspace', { replace: true })}>
            Go to workspace
          </button>
        </div>
      </main>
    )
  }

  return (
    <main className="flex min-h-dvh items-center justify-center bg-slate-50 px-4" aria-live="polite">
      <div className="flex items-center gap-3 text-sm font-semibold text-slate-500">
        <FiLoader aria-hidden="true" className="animate-spin" />
        Opening notification…
      </div>
    </main>
  )
}
