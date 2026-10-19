import { useLocations } from '../../data/locations'
import LocationSelect, { type LocationSelectProps } from './LocationSelect'

export type StateSelectProps = Omit<LocationSelectProps, 'options' | 'loading' | 'error'> & {
  country?: string | null
}
export default function StateSelect({
  country,
  placeholder = 'Select state',
  ...props
}: StateSelectProps) {
  const { states, loading, error } = useLocations(country)
  return (
    <LocationSelect
      key={country}
      {...props}
      placeholder={placeholder}
      options={states.map((state) => state.name)}
      loading={loading}
      error={error}
    />
  )
}
