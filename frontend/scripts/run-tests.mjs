import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const scriptDirectory = dirname(fileURLToPath(import.meta.url))
const vitestEntrypoint = resolve(scriptDirectory, '../node_modules/vitest/vitest.mjs')
const result = spawnSync(process.execPath, [vitestEntrypoint, ...process.argv.slice(2)], {
  env: { ...process.env, NODE_ENV: 'test' },
  stdio: 'inherit',
})

if (result.error) {
  console.error(result.error)
  process.exit(1)
}

process.exit(result.status ?? 1)