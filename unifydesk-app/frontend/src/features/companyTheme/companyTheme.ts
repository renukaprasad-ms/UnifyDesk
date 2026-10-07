export type UserAppearanceMode = 'LIGHT' | 'DARK'

export const DEFAULT_BRAND_COLOR = '#2563EB'
export const APPEARANCE_STORAGE_KEY = 'unifydesk.appearanceMode'

export const normalizeAppearanceMode = (
  value: string | null | undefined,
): UserAppearanceMode => (value === 'DARK' ? 'DARK' : 'LIGHT')

export const readStoredAppearanceMode = () => {
  if (typeof window === 'undefined') return null
  const stored = window.localStorage.getItem(APPEARANCE_STORAGE_KEY)
  return stored ? normalizeAppearanceMode(stored) : null
}

const persistAppearanceMode = (appearanceMode: UserAppearanceMode) => {
  if (typeof window === 'undefined') return
  window.localStorage.setItem(APPEARANCE_STORAGE_KEY, appearanceMode)
}

const appearanceTokens: Record<
  UserAppearanceMode,
  Record<
    | '--theme-bg'
    | '--theme-surface'
    | '--theme-surface-subtle'
    | '--theme-border'
    | '--theme-border-soft'
    | '--theme-text'
    | '--theme-muted',
    string
  >
> = {
  LIGHT: {
    '--theme-bg': '#f8fafd',
    '--theme-surface': '#ffffff',
    '--theme-surface-subtle': '#fbfcff',
    '--theme-border': '#e0e5f0',
    '--theme-border-soft': '#e9edf5',
    '--theme-text': '#0e172b',
    '--theme-muted': '#63738c',
  },
  DARK: {
    '--theme-bg': '#0f172a',
    '--theme-surface': '#111827',
    '--theme-surface-subtle': '#172033',
    '--theme-border': '#263449',
    '--theme-border-soft': '#1f2a3d',
    '--theme-text': '#e5edf8',
    '--theme-muted': '#94a3b8',
  },
}

export const brandContrastColor = (hex: string | null | undefined) => {
  const value = normalizeHex(hex).slice(1)
  const red = Number.parseInt(value.slice(0, 2), 16)
  const green = Number.parseInt(value.slice(2, 4), 16)
  const blue = Number.parseInt(value.slice(4, 6), 16)
  const luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255
  return luminance > 0.62 ? '#0F172A' : '#FFFFFF'
}

const normalizeHex = (value: string | null | undefined) => {
  const candidate = value?.trim().toUpperCase() ?? ''
  return /^#[0-9A-F]{6}$/.test(candidate) ? candidate : DEFAULT_BRAND_COLOR
}

const channel = (value: string, offset: number) =>
  Math.max(0, Math.min(255, Number.parseInt(value, 16) + offset))
    .toString(16)
    .padStart(2, '0')

const adjust = (hex: string, offset: number) => {
  const value = normalizeHex(hex).slice(1)
  return `#${channel(value.slice(0, 2), offset)}${channel(value.slice(2, 4), offset)}${channel(value.slice(4, 6), offset)}`
}

export const applyCompanyTheme = (
  brandColor: string | null | undefined,
  appearanceMode: UserAppearanceMode = 'LIGHT',
) => {
  if (typeof document === 'undefined') return

  const primary = normalizeHex(brandColor)
  const mode = normalizeAppearanceMode(appearanceMode)
  const root = document.documentElement
  root.dataset.colorScheme = mode.toLowerCase()
  root.style.colorScheme = mode.toLowerCase()
  persistAppearanceMode(mode)

  Object.entries(appearanceTokens[mode]).forEach(([property, value]) => {
    root.style.setProperty(property, value)
  })

  root.style.setProperty('--color-indigo-500', primary)
  root.style.setProperty('--theme-primary', primary)
  root.style.setProperty('--theme-primary-contrast', brandContrastColor(primary))
  root.style.setProperty('--theme-primary-hover', adjust(primary, mode === 'DARK' ? 24 : -24))
  root.style.setProperty(
    '--theme-primary-soft',
    `color-mix(in srgb, ${primary} ${mode === 'DARK' ? '28%' : '18%'}, ${mode === 'DARK' ? '#111827' : '#ffffff'})`,
  )
  root.style.setProperty(
    '--theme-primary-softer',
    `color-mix(in srgb, ${primary} ${mode === 'DARK' ? '18%' : '9%'}, ${mode === 'DARK' ? '#0f172a' : '#ffffff'})`,
  )
  root.style.setProperty('--theme-primary-ring', adjust(primary, mode === 'DARK' ? 36 : 42))
  root.style.setProperty('--color-indigo-50', 'var(--theme-primary-softer)')

  document
    .querySelector('meta[name="theme-color"]')
    ?.setAttribute('content', mode === 'DARK' ? '#0f172a' : primary)
}
