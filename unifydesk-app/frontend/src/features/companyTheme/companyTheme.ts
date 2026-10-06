import { useCallback, useMemo, useState } from 'react'

export type CompanyThemeMode = 'light' | 'dark'
export type CompanyThemeColorId = 'blue' | 'orange' | 'emerald' | 'rose' | 'violet' | 'slate'

export type CompanyThemeSettings = {
  colorId: CompanyThemeColorId
  mode: CompanyThemeMode
}

type CompanyThemePalette = {
  id: CompanyThemeColorId
  name: string
  primary: string
  primaryHover: string
  soft: string
  softer: string
  ring: string
}

const STORAGE_PREFIX = 'unifydesk.companyTheme'

export const DEFAULT_COMPANY_THEME: CompanyThemeSettings = {
  colorId: 'blue',
  mode: 'light',
}

export const COMPANY_THEME_PALETTES: CompanyThemePalette[] = [
  {
    id: 'blue',
    name: 'Unify Blue',
    primary: '#2563eb',
    primaryHover: '#1d4ed8',
    soft: '#dbeafe',
    softer: '#eff6ff',
    ring: '#93c5fd',
  },
  {
    id: 'orange',
    name: 'Growth Orange',
    primary: '#ea580c',
    primaryHover: '#c2410c',
    soft: '#ffedd5',
    softer: '#fff7ed',
    ring: '#fdba74',
  },
  {
    id: 'emerald',
    name: 'Trust Green',
    primary: '#059669',
    primaryHover: '#047857',
    soft: '#d1fae5',
    softer: '#ecfdf5',
    ring: '#6ee7b7',
  },
  {
    id: 'rose',
    name: 'Signal Rose',
    primary: '#e11d48',
    primaryHover: '#be123c',
    soft: '#ffe4e6',
    softer: '#fff1f2',
    ring: '#fda4af',
  },
  {
    id: 'violet',
    name: 'Product Violet',
    primary: '#7c3aed',
    primaryHover: '#6d28d9',
    soft: '#ede9fe',
    softer: '#f5f3ff',
    ring: '#c4b5fd',
  },
  {
    id: 'slate',
    name: 'Executive Slate',
    primary: '#334155',
    primaryHover: '#1e293b',
    soft: '#e2e8f0',
    softer: '#f8fafc',
    ring: '#94a3b8',
  },
]

export const companyThemePaletteById = new Map(
  COMPANY_THEME_PALETTES.map((palette) => [palette.id, palette]),
)

const storageKeyFor = (companyId: string | null | undefined) =>
  companyId ? `${STORAGE_PREFIX}.${companyId}` : `${STORAGE_PREFIX}.global`

const isCompanyThemeMode = (value: unknown): value is CompanyThemeMode =>
  value === 'light' || value === 'dark'

const isCompanyThemeColorId = (value: unknown): value is CompanyThemeColorId =>
  typeof value === 'string' && companyThemePaletteById.has(value as CompanyThemeColorId)

export const readCompanyTheme = (companyId?: string | null): CompanyThemeSettings => {
  if (typeof window === 'undefined') return DEFAULT_COMPANY_THEME

  try {
    const raw = window.localStorage.getItem(storageKeyFor(companyId))
    if (!raw) return DEFAULT_COMPANY_THEME
    const parsed = JSON.parse(raw) as Partial<CompanyThemeSettings>

    return {
      colorId: isCompanyThemeColorId(parsed.colorId)
        ? parsed.colorId
        : DEFAULT_COMPANY_THEME.colorId,
      mode: isCompanyThemeMode(parsed.mode) ? parsed.mode : DEFAULT_COMPANY_THEME.mode,
    }
  } catch {
    return DEFAULT_COMPANY_THEME
  }
}

export const writeCompanyTheme = (
  companyId: string | null | undefined,
  settings: CompanyThemeSettings,
) => {
  if (typeof window === 'undefined') return
  window.localStorage.setItem(storageKeyFor(companyId), JSON.stringify(settings))
}

export const applyCompanyTheme = (settings: CompanyThemeSettings) => {
  if (typeof document === 'undefined') return

  const palette =
    companyThemePaletteById.get(settings.colorId) ?? companyThemePaletteById.get('blue')!
  const root = document.documentElement

  root.dataset.colorScheme = settings.mode
  root.style.colorScheme = settings.mode
  root.style.setProperty('--color-indigo-500', palette.primary)
  root.style.setProperty('--color-indigo-50', palette.softer)
  root.style.setProperty('--theme-primary', palette.primary)
  root.style.setProperty('--theme-primary-hover', palette.primaryHover)
  root.style.setProperty('--theme-primary-soft', palette.soft)
  root.style.setProperty('--theme-primary-softer', palette.softer)
  root.style.setProperty('--theme-primary-ring', palette.ring)
}

export const useCompanyTheme = (companyId?: string | null) => {
  const storageKey = storageKeyFor(companyId)
  const [stored, setStored] = useState<{ storageKey: string; settings: CompanyThemeSettings }>(
    () => ({
      storageKey,
      settings: readCompanyTheme(companyId),
    }),
  )
  const settings = stored.storageKey === storageKey ? stored.settings : readCompanyTheme(companyId)

  const updateSettings = useCallback(
    (next: CompanyThemeSettings) => {
      writeCompanyTheme(companyId, next)
      applyCompanyTheme(next)
      setStored({ storageKey, settings: next })
    },
    [companyId, storageKey],
  )

  return useMemo(
    () => ({
      settings,
      setColorId: (colorId: CompanyThemeColorId) => updateSettings({ ...settings, colorId }),
      setMode: (mode: CompanyThemeMode) => updateSettings({ ...settings, mode }),
    }),
    [settings, updateSettings],
  )
}
