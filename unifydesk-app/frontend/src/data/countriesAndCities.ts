import countries from './locationCountries.json'

export interface CountryData {
  code: string
  name: string
  dialCode: string
  flag: string
}

export const COUNTRIES: CountryData[] = countries

export function findCountry(query?: string | null): CountryData | undefined {
  if (!query) return undefined
  const normalized = query.trim().toUpperCase()
  return COUNTRIES.find(
    (c) => c.code.toUpperCase() === normalized || c.name.toUpperCase() === normalized,
  )
}
