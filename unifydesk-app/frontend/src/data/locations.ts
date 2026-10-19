import { useEffect, useState } from 'react'
import { findCountry } from './countriesAndCities'

export type LocationState = { code: string; name: string; cities: string[] }
const cache = new Map<string, Promise<LocationState[]>>()

export function loadLocations(country: string): Promise<LocationState[]> {
  const code = findCountry(country)?.code
  if (!code) return Promise.resolve([])
  let pending = cache.get(code)
  if (!pending) {
    pending = fetch(`${import.meta.env.BASE_URL}locations/${code}.json`)
      .then((response) => {
        if (!response.ok) throw new Error('Unable to load locations')
        return response.json() as Promise<LocationState[]>
      })
      .catch((error) => {
        cache.delete(code)
        throw error
      })
    cache.set(code, pending)
  }
  return pending
}

export function useLocations(country?: string | null) {
  const code = findCountry(country)?.code ?? ''
  const [result, setResult] = useState<{ code: string; states: LocationState[]; error: boolean }>({
    code: '',
    states: [],
    error: false,
  })
  useEffect(() => {
    let active = true
    loadLocations(code).then(
      (states) => {
        if (active) setResult({ code, states, error: false })
      },
      () => {
        if (active) setResult({ code, states: [], error: true })
      },
    )
    return () => {
      active = false
    }
  }, [code])
  return {
    states: result.code === code ? result.states : [],
    loading: !!code && result.code !== code,
    error: result.code === code && result.error,
  }
}
