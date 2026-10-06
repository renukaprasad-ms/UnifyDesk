export type UserAppearanceMode = 'LIGHT' | 'DARK'

export const DEFAULT_BRAND_COLOR = '#2563EB'

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
  const root = document.documentElement
  root.dataset.colorScheme = appearanceMode.toLowerCase()
  root.style.colorScheme = appearanceMode.toLowerCase()

  root.style.setProperty('--color-indigo-500', primary)
  root.style.setProperty('--theme-primary', primary)
  root.style.setProperty('--theme-primary-hover', adjust(primary, appearanceMode === 'DARK' ? 24 : -24))
  root.style.setProperty(
    '--theme-primary-soft',
    `color-mix(in srgb, ${primary} ${appearanceMode === 'DARK' ? '28%' : '18%'}, ${appearanceMode === 'DARK' ? '#111827' : '#ffffff'})`,
  )
  root.style.setProperty(
    '--theme-primary-softer',
    `color-mix(in srgb, ${primary} ${appearanceMode === 'DARK' ? '18%' : '9%'}, ${appearanceMode === 'DARK' ? '#0f172a' : '#ffffff'})`,
  )
  root.style.setProperty('--theme-primary-ring', adjust(primary, appearanceMode === 'DARK' ? 36 : 42))
  root.style.setProperty('--color-indigo-50', 'var(--theme-primary-softer)')
}
