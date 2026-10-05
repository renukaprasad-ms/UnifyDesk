import { useEffect, useRef, useState } from 'react'
import { FiCheck, FiChevronDown, FiPlus } from 'react-icons/fi'
import { useNavigate } from 'react-router'
import type { Company } from '../features/company/types'
import { mediaApi } from '../features/media/mediaApi'
import { workspaceApi } from '../features/workspace/workspaceApi'
import { useAppDispatch, useAppSelector } from '../store/hooks'
import {
  selectCompanies,
  selectCompany,
  selectSelectedCompany,
  selectSelectedCompanyId,
  setCompanies,
} from '../store/workspace/workspaceSlice'
import { Skeleton } from './ui/Skeleton'

const companyInitials = (name: string) =>
  name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('')
    .slice(0, 2) || 'CO'

const mergeCompanies = (memberCompanies: Company[], adminCompanies: Company[]) => {
  const byId = new Map<string, Company>()
  adminCompanies.forEach((company) => byId.set(company.companyId, company))
  memberCompanies.forEach((company) => byId.set(company.companyId, company))
  return [...byId.values()].sort((a, b) => a.name.localeCompare(b.name))
}

export default function WorkspaceSwitcher({ platformAdmin = false }: { platformAdmin?: boolean }) {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const menuRef = useRef<HTMLDivElement | null>(null)
  const attemptedLogoMediaIds = useRef(new Set<string>())
  const companies = useAppSelector(selectCompanies)
  const selectedCompanyId = useAppSelector(selectSelectedCompanyId)
  const selectedCompany = useAppSelector(selectSelectedCompany)
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [logoUrls, setLogoUrls] = useState<Record<string, string>>({})

  useEffect(() => {
    if (!open) return
    const onPointerDown = (event: PointerEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [open])

  useEffect(() => {
    if (!open) return
    let active = true
    setLoading(true)

    Promise.all([
      workspaceApi.list(),
      platformAdmin
        ? workspaceApi.adminVisible().catch(() => [] as Company[])
        : Promise.resolve([] as Company[]),
    ])
      .then(([memberCompanies, adminCompanies]) => {
        if (active) dispatch(setCompanies(mergeCompanies(memberCompanies, adminCompanies)))
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [dispatch, open, platformAdmin])

  useEffect(() => {
    let active = true
    const missingLogos = companies.filter((company) => {
      const mediaId = company.logoMediaId
      return Boolean(mediaId && !attemptedLogoMediaIds.current.has(mediaId))
    })

    if (missingLogos.length === 0) return

    missingLogos.forEach((company) => {
      if (company.logoMediaId) attemptedLogoMediaIds.current.add(company.logoMediaId)
    })

    Promise.all(
      missingLogos.map(async (company) => {
        try {
          const access = await mediaApi.access(company.logoMediaId!)
          return [company.companyId, access.url] as const
        } catch {
          return [company.companyId, ''] as const
        }
      }),
    ).then((entries) => {
      if (!active) return
      const successfulEntries = entries.filter(([, url]) => Boolean(url))
      if (successfulEntries.length === 0) return
      setLogoUrls((current) => ({
        ...current,
        ...Object.fromEntries(successfulEntries),
      }))
    })

    return () => {
      active = false
    }
  }, [companies])

  const label = selectedCompany?.name ?? 'Select workspace'
  const selectedLogoUrl = selectedCompany ? logoUrls[selectedCompany.companyId] : ''

  return (
    <div ref={menuRef} className="relative">
      <button
        type="button"
        className="workspace-switcher"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
      >
        <span className="flex size-8 shrink-0 items-center justify-center overflow-hidden rounded-[8px] bg-indigo-50 text-[11px] font-bold text-indigo-500">
          {selectedLogoUrl ? (
            <img src={selectedLogoUrl} alt="" className="h-full w-full object-cover" />
          ) : selectedCompany ? (
            companyInitials(selectedCompany.name)
          ) : (
            'WS'
          )}
        </span>
        <span className="workspace-switcher-copy min-w-0 flex-1 text-left">
          <span className="block truncate text-[13px] font-semibold text-slate-950">{label}</span>
          <span className="mt-0.5 block text-[10px] font-medium text-slate-500">Workspace</span>
        </span>
        <FiChevronDown
          aria-hidden="true"
          className="workspace-switcher-chevron shrink-0 text-slate-500"
        />
      </button>
      {open && (
        <div className="workspace-menu left-0 right-auto" role="menu">
          {loading && (
            <div className="space-y-2 px-3 py-3" role="status" aria-label="Loading workspaces">
              <Skeleton className="h-9 w-full" />
              <Skeleton className="h-9 w-full" />
            </div>
          )}
          {!loading &&
            companies.map((company) => (
              <button
                key={company.companyId}
                type="button"
                className="workspace-menu-item"
                role="menuitem"
                onClick={() => {
                  dispatch(selectCompany(company.companyId))
                  setOpen(false)
                  navigate('/workspace', { replace: true })
                }}
              >
                <span className="flex size-8 shrink-0 items-center justify-center overflow-hidden rounded-[8px] bg-indigo-50 text-[11px] font-bold text-indigo-500">
                  {logoUrls[company.companyId] ? (
                    <img
                      src={logoUrls[company.companyId]}
                      alt=""
                      className="h-full w-full object-cover"
                    />
                  ) : (
                    companyInitials(company.name)
                  )}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate">{company.name}</span>
                  <span className="mt-0.5 block text-[10px] font-medium text-slate-500">
                    {company.role ?? 'Platform access'}
                  </span>
                </span>
                {selectedCompanyId === company.companyId && (
                  <FiCheck aria-hidden="true" className="shrink-0 text-indigo-500" />
                )}
              </button>
            ))}
          <button
            type="button"
            className="workspace-menu-item border-t border-slate-100 text-indigo-500"
            role="menuitem"
            onClick={() => {
              setOpen(false)
              navigate('/create-workspace')
            }}
          >
            <span className="flex items-center gap-2">
              <FiPlus aria-hidden="true" />
              Create workspace
            </span>
          </button>
        </div>
      )}
    </div>
  )
}
