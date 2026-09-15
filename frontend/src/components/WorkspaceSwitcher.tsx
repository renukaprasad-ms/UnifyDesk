import { useEffect, useRef, useState } from 'react'
import { FiCheck, FiChevronDown, FiPlus } from 'react-icons/fi'
import { useDispatch, useSelector } from 'react-redux'
import { useNavigate } from 'react-router'
import { companyApi } from '../features/company/companyApi'
import type { AppDispatch, RootState } from '../store/store'
import { selectCompany, selectSelectedCompany, setCompanies } from '../store/user/userSlice'

export default function WorkspaceSwitcher() {
  const dispatch = useDispatch<AppDispatch>()
  const navigate = useNavigate()
  const menuRef = useRef<HTMLDivElement | null>(null)
  const user = useSelector((state: RootState) => state.user.user)
  const companies = useSelector((state: RootState) => state.user.companies)
  const selectedCompanyId = useSelector((state: RootState) => state.user.selectedCompanyId)
  const selectedCompany = useSelector(selectSelectedCompany)
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!open) return

    const onPointerDown = (event: PointerEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }

    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [open])

  useEffect(() => {
    const shouldHydrateCompanies = open || Boolean(selectedCompanyId)
    if (!shouldHydrateCompanies || companies.length > 0 || !user?.hasCompanies) return

    let active = true
    setLoading(true)
    companyApi
      .myCompanies()
      .then((items) => {
        if (active) dispatch(setCompanies(items))
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [companies.length, dispatch, open, selectedCompanyId, user?.hasCompanies])

  const label = selectedCompany?.name ?? 'Select workspace'

  return (
    <div ref={menuRef} className="relative">
      <button
        type="button"
        className="workspace-switcher"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
      >
        <span className="min-w-0">
          <span className="block truncate text-[13px] font-semibold text-slate-950">{label}</span>
          <span className="mt-0.5 block text-[10px] font-medium text-slate-500">Workspace</span>
        </span>
        <FiChevronDown aria-hidden="true" className="shrink-0 text-slate-500" />
      </button>

      {open && (
        <div className="workspace-menu left-0 right-auto" role="menu">
          {loading && <div className="px-3 py-3 text-xs font-semibold text-slate-500">Loading...</div>}

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
                  navigate('/', { replace: true })
                }}
              >
                <span className="min-w-0">
                  <span className="block truncate">{company.name}</span>
                  <span className="mt-0.5 block text-[10px] font-medium text-slate-500">
                    {company.role}
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
