import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const source = path.join(root, 'node_modules/@countrystatecity/countries-browser/dist/data')
const output = path.join(root, 'public/locations')
fs.mkdirSync(output, { recursive: true })
const countries = JSON.parse(fs.readFileSync(path.join(source, 'countries.json'), 'utf8'))
let stateCount = 0
let cityCount = 0
for (const country of countries) {
  const stateFile = path.join(source, 'states', `${country.iso2}.json`)
  const states = fs.existsSync(stateFile) ? JSON.parse(fs.readFileSync(stateFile, 'utf8')) : []
  const rows = states.map((state) => {
    const cityFile = path.join(source, 'cities', `${country.iso2}-${state.iso2}.json`)
    const cities = fs.existsSync(cityFile) ? JSON.parse(fs.readFileSync(cityFile, 'utf8')) : []
    const names = [...new Set(cities.map((city) => city.name))].sort((a, b) => a.localeCompare(b))
    stateCount++
    cityCount += names.length
    return { code: state.iso2, name: state.name, cities: names }
  })
  fs.writeFileSync(path.join(output, `${country.iso2}.json`), JSON.stringify(rows))
}
fs.writeFileSync(
  path.join(root, 'src/data/locationCountries.json'),
  JSON.stringify(
    countries.map((c) => ({
      code: c.iso2,
      name: c.name,
      dialCode: `+${c.phonecode}`,
      flag: c.emoji,
      cities: [],
    })),
  ),
)
console.log(`${countries.length} countries, ${stateCount} states, ${cityCount} city names`)
