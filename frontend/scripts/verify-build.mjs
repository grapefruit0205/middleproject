import { existsSync } from 'node:fs'

const required = ['dist/manifest.webmanifest', 'dist/sw.js', 'dist/index.html']

const missing = required.filter((file) => !existsSync(file))

if (missing.length > 0) {
  console.error(`Missing build outputs: ${missing.join(', ')}`)
  process.exit(1)
}

console.log(`PWA build outputs verified: ${required.join(', ')}`)
