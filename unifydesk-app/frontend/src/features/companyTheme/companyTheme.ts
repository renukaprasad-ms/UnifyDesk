import { useCallback, useMemo, useState } from 'react'

export type CompanyThemeMode = 'light' | 'dark'
export type CompanyThemeColorId = 'blue' | 'orange' | 'emerald' | 'rose' | 'violet' | 'slate'

export type CompanyThemeSettings = {
  colorId: CompanyThemeColorId
  mode: CompanyThemeMode
}

type CompanyThemePalette = {
  primary: string
  primaryHover: string
  soft: string
  softer: string
  ring: string
}

type CompanyThemePaletteOption = {
  id: CompanyThemeColorId
  name: string
  light: CompanyThemePalette
  dark: CompanyThemePalette
}

const STORAGE_PREFIX = 'unifydesk.companyTheme'

export const DEFAULT_COMPANY_THEME: CompanyThemeSettings = {
  colorId: 'blue',
  mode: 'light',
}

export const COMPANY_THEME_PALETTES: CompanyThemePaletteOption[] = [
  {
    id: 'blue',
    name: 'Unify Blue',
    light: {
      primary: '#2563eb',
      primaryHover: '#1d4ed8',
      soft: '#dbeafe',
      softer: '#eff6ff',
      ring: '#93c5fd',
    },
    dark: {
      primary: '#60a5fa',
      primaryHover: '#93c5fd',
      soft: '#1e3a5f',
      softer: '#10243c',
      ring: '#3b82f6',
    },
  },
  {
    id: 'orange',
    name: 'Growth Orange',
    light: {
      primary: '#ea580c',
      primaryHover: '#c2410c',
      soft: '#ffedd5',
      softer: '#fff7ed',
      ring: '#fdba74',
    },
    dark: {
      primary: '#fb923c',
      primaryHover: '#fdba74',
      soft: '#5b2a12',
      softer: '#2f1b10',
      ring: '#f97316',
    },
  },
  {
    id: 'emerald',
    name: 'Trust Green',
    light: {
      primary: '#059669',
      primaryHover: '#047857',
      soft: '#d1fae5',
      softer: '#ecfdf5',
      ring: '#6ee7b7',
    },
    dark: {
      primary: '#34d399',
      primaryHover: '#6ee7b7',
      soft: '#164e3b',
      softer: '#102920',
      ring: '#10b981',
    },
  },
  {
    id: 'rose',
    name: 'Signal Rose',
    light: {
      primary: '#e11d48',
      primaryHover: '#be123c',
      soft: '#ffe4e6',
      softer: '#fff1f2',
      ring: '#fda4af',
    },
    dark: {
      primary: '#fb7185',
      primaryHover: '#fda4af',
      soft: '#5a1f2d',
      softer: '#34131d',
      ring: '#f43f5e',
    },
  },
  {
    id: 'violet',
    name: 'Product Violet',
    light: {
      primary: '#7c3aed',
      primaryHover: '#6d28d9',
      soft: '#ede9fe',
      softer: '#f5f3ff',
      ring: '#c4b5fd',
    },
    dark: {
      primary: '#a78bfa',
      primaryHover: '#c4b5fd',
      soft: '#3b2467',
      softer: '#221735',
      ring: '#8b5cf6',
    },
  },
  {
    id: 'slate',
    name: 'Executive Slate',
    light: {
      primary: '#334155',
      primaryHover: '#1e293b',
      soft: '#e2e8f0',
      softer: '#f8fafc',
      ring: '#94a3b8',
    },
    dark: {
      primary: '#cbd5e1',
      primaryHover: '#f1f5f9',
      soft: '#334155',
      softer: '#1f2937',
      ring: '#64748b',
    },
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

export const themePaletteForMode = (
  option: CompanyThemePaletteOption,
  mode: CompanyThemeMode,
): CompanyThemePalette => option[mode]

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
  if (companyId) {
    window.localStorage.setItem(storageKeyFor(null), JSON.stringify(settings))
  }
}

export const applyCompanyTheme = (settings: CompanyThemeSettings) => {
  if (typeof document === 'undefined') return

  const option =
    companyThemePaletteById.get(settings.colorId) ?? companyThemePaletteById.get('blue')!
  const palette = themePaletteForMode(option, settings.mode)
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
