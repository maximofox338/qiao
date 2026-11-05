#!/usr/bin/env node
// Qiao — control "cero recursos externos" para builds que deben cargar desde China continental sin VPN.
//
// Uso: node scripts/check-no-external.mjs <dir> [<dir> ...]
//
// Recorre TODOS los ficheros de cada directorio (texto y binario, leídos como latin1 para no
// perder bytes) y busca URLs absolutas http://, https://, ws:// y wss:// (también con barras
// escapadas, p. ej. "https:\/\/cdn..."). En HTML y CSS además busca URLs relativas al protocolo
// ("//cdn.example.com/...") en src=, href=, url(...) e @import, que el navegador también descargaría.
//
// Sale con código 1 y lista fichero + URL si encuentra alguna que no esté permitida.
// Sale con código 2 si falta un directorio o no se pasó ninguno.
//
// Lista de permitidas (identificadores que el navegador nunca descarga):
//  - http://www.w3.org/...  Espacios de nombres XML/SVG/XLink/XHTML/MathML (p. ej.
//    "http://www.w3.org/2000/svg"). Son nombres, no recursos: createElementNS() y los atributos
//    xmlns los comparan como cadenas y jamás hacen una petición de red.
// No se permite nada más: enlaces a documentación en mensajes de error, licencias, CDNs, etc.
// deben eliminarse del build (o justificarse aquí con un comentario antes de añadirlos).

import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative, resolve, extname } from 'node:path'

const ALLOWED_PREFIXES = [
  // Espacios de nombres XML/SVG (solo http, que es como están definidos; https no es un namespace).
  'http://www.w3.org/',
]

const ABSOLUTE_URL = /\b(?:https?|wss?):(?:\/|\\\/){2}[^\s"'`<>()\\{}|^]*/gi
// URLs relativas al protocolo en contextos que el navegador descarga (solo HTML/CSS).
const PROTOCOL_RELATIVE = [
  /\b(?:src|href|srcset|poster|data)\s*=\s*["']?\s*(\/\/[^\s"'<>]+)/gi,
  /url\(\s*["']?\s*(\/\/[^\s"')]+)/gi,
  /@import\s+["'](\/\/[^"']+)/gi,
]
const MARKUP_EXT = new Set(['.html', '.htm', '.css', '.svg', '.xml'])

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    const st = statSync(p)
    if (st.isDirectory()) walk(p, out)
    else if (st.isFile()) out.push(p)
  }
  return out
}

function isAllowed(url) {
  const normalized = url.replace(/\\\//g, '/')
  return ALLOWED_PREFIXES.some((prefix) => normalized.startsWith(prefix))
}

const dirs = process.argv.slice(2)
if (dirs.length === 0) {
  console.error('Uso: node scripts/check-no-external.mjs <dir> [<dir> ...]')
  process.exit(2)
}

const violations = []
let scanned = 0
for (const d of dirs) {
  const root = resolve(d)
  let st
  try {
    st = statSync(root)
  } catch {
    console.error(`ERROR: no existe el directorio "${d}" (¿falta ejecutar el build?)`)
    process.exit(2)
  }
  if (!st.isDirectory()) {
    console.error(`ERROR: "${d}" no es un directorio`)
    process.exit(2)
  }
  for (const file of walk(root)) {
    scanned++
    const text = readFileSync(file, 'latin1')
    const rel = relative(process.cwd(), file)
    for (const m of text.matchAll(ABSOLUTE_URL)) {
      if (!isAllowed(m[0])) violations.push({ file: rel, url: m[0], index: m.index })
    }
    if (MARKUP_EXT.has(extname(file).toLowerCase())) {
      for (const re of PROTOCOL_RELATIVE) {
        for (const m of text.matchAll(re)) violations.push({ file: rel, url: m[1], index: m.index })
      }
    }
  }
}

if (violations.length > 0) {
  console.error(`ERROR: ${violations.length} URL(s) externa(s) encontradas en el build:`)
  for (const v of violations) console.error(`  ${v.file} @${v.index}: ${v.url}`)
  console.error('Los ingenieros acceden desde China continental sin VPN: el build no puede referenciar hosts externos.')
  process.exit(1)
}
console.log(`OK: ${scanned} fichero(s) revisados en ${dirs.join(', ')}; sin URLs externas (solo namespaces XML/SVG permitidos).`)
