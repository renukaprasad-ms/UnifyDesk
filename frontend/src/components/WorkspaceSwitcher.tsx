import { useEffect, useRef, useState } from 'react'
import { FiCheck, FiChevronDown, FiPlus } from 'react-icons/fi'
import { useNavigate } from 'react-router'
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

const companyInitials = (name: string) =>
  name.split(' ').filter(Boolean).slice(0, 2).map((part) => part[0]?.toUpperCase()).join('').slice(0, 2) || 'CO'

export default function WorkspaceSwitcher() {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const menuRef = useRef<HTMLDivElement | null>(null)
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
    if (!open || companies.length > 0) return
    let active = true
    setLoading(true)
    workspaceApi.list().then((items) => {
      if (active) dispatch(setCompanies(items))
    }).finally(() => {
      if (active) setLoading(false)
    })
    return () => {
      active = false
    }
  }, [companies.length, dispatch, open])

  useEffect(() => {
    let active = true
    const missingLogos = companies.filter(
      (company) => company.logoMediaId && !logoUrls[company.companyId],
    )
    if (missingLogos.length === 0) return
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
      setLogoUrls((current) => ({
        ...current,
        ...Object.fromEntries(entries.filter(([, url]) => url)),
      }))
    })
    return () => {
      active = false
    }
  }, [companies, logoUrls])

  const label = selectedCompany?.name ?? 'Select workspace'
  const selectedLogoUrl = selectedCompany ? logoUrls[selectedCompany.companyId] : ''

  return (
    <div ref={menuRef} className="relative">
      <button type="button" className="workspace-switcher" aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen((current) => !current)}>
        <span className="flex size-8 shrink-0 items-center justify-center overflow-hidden rounded-[8px] bg-indigo-50 text-[11px] font-bold text-indigo-500">
          {selectedLogoUrl ? <img src={selectedLogoUrl} alt="" className="h-full w-full object-cover" /> : selectedCompany ? companyInitials(selectedCompany.name) : 'WS'}
        </span>
        <span className="min-w-0"><span className="block truncate text-[13px] font-semibold text-slate-950">{label}</span><span className="mt-0.5 block text-[10px] font-medium text-slate-500">Workspace</span></span>
        <FiChevronDown aria-hidden="true" className="shrink-0 text-slate-500" />
      </button>
      {open && (
        <div className="workspace-menu left-0 right-auto" role="menu">
          {loading && <div className="px-3 py-3 text-xs font-semibold text-slate-500">Loading...</div>}
          {!loading && companies.map((company) => (
            <button key={company.companyId} type="button" className="workspace-menu-item" role="menuitem" onClick={() => { dispatch(selectCompany(company.companyId)); setOpen(false); navigate('/workspace', { replace: true }) }}>
              <span className="flex size-8 shrink-0 items-center justify-center overflow-hidden rounded-[8px] bg-indigo-50 text-[11px] font-bold text-indigo-500">{logoUrls[company.companyId] ? <img src={logoUrls[company.companyId]} alt="" className="h-full w-full object-cover" /> : companyInitials(company.name)}</span>
              <span className="min-w-0"><span className="block truncate">{company.name}</span><span className="mt-0.5 block text-[10px] font-medium text-slate-500">{company.role}</span></span>
              {selectedCompanyId === company.companyId && <FiCheck aria-hidden="true" className="shrink-0 text-indigo-500" />}
            </button>
          ))}
          <button type="button" className="workspace-menu-item border-t border-slate-100 text-indigo-500" role="menuitem" onClick={() => { setOpen(false); navigate('/create-workspace') }}>
            <span className="flex items-center gap-2"><FiPlus aria-hidden="true" />Create workspace</span>
          </button>
        </div>
      )}
    </div>
  )
}
