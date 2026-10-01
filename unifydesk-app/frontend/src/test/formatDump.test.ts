import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('frontend formatting extractor', () => {
  it('emits the exact pinned-Prettier output for repository formatting repair', () => {
    const prettierBin = resolve('node_modules/prettier/bin/prettier.cjs')
    execFileSync(process.execPath, [prettierBin, '--write', 'src', '*.ts', '*.json'], {
      cwd: process.cwd(),
      stdio: 'pipe',
    })

    const names = execFileSync(
      'git',
      ['diff', '--name-only', '--', 'unifydesk-app/frontend'],
      { cwd: resolve(process.cwd(), '../..'), encoding: 'utf8' },
    )
      .trim()
      .split('\n')
      .filter(Boolean)

    const prefix = 'unifydesk-app/frontend/'
    const files = Object.fromEntries(
      names
        .filter((name) => name.startsWith(prefix))
        .map((name) => [
          name,
          Buffer.from(
            readFileSync(resolve(process.cwd(), name.slice(prefix.length)), 'utf8'),
          ).toString('base64'),
        ]),
    )

    console.log('UNIFYDESK_FORMAT_DUMP_BEGIN')
    console.log(JSON.stringify(files))
    console.log('UNIFYDESK_FORMAT_DUMP_END')
    expect(Object.keys(files).length).toBeGreaterThan(0)
  }, 30_000)
})
