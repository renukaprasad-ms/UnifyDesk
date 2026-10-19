import { useLocations } from '../../data/locations'
import LocationSelect, { type LocationSelectProps } from './LocationSelect'

export type CitySelectProps = Omit<LocationSelectProps, 'options' | 'loading' | 'error'> & {
  country?: string | null
  state?: string | null
}
export default function CitySelect({
  country,
  state,
  placeholder = 'Select city',
  ...props
}: CitySelectProps) {
  const { states, loading, error } = useLocations(country)
  const selected = state
    ? states.filter((item) => item.name === state || item.code === state)
    : states
  const cities = [...new Set(selected.flatMap((item) => item.cities))].sort((a, b) =>
    a.localeCompare(b),
  )
  return (
    <LocationSelect
      key={`${country}|${state}`}
      {...props}
      placeholder={placeholder}
      options={cities}
      loading={loading}
      error={error}
    />
  )
}
