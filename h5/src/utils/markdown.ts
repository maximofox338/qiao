// Small, strict Markdown renderer for legal texts and contract documents coming from the API.
//
// Why not markdown-it: its linkify code builds "http://" strings that end up in the bundle, and the
// engineer build must contain no external URLs (scripts/check-no-external.mjs). This renderer:
//  - escapes ALL text (raw HTML in the source is shown as text, never interpreted);
//  - supports headings, paragraphs (single newlines become <br>), bold/italic, inline code, fenced code,
//    blockquotes, horizontal rules, ordered/unordered (nested) lists and simple pipe tables;
//  - renders images as their alt text (never fetches anything);
//  - only emits links whose target is relative, #anchor, http(s) or mailto; external links open as a new
//    top-level navigation with rel="noopener noreferrer nofollow".

const ESC: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }
const escapeHtml = (s: string) => s.replace(/[&<>"']/g, (c) => ESC[c])

const CODE_TOKEN = /\u0000(\d+)\u0000/g

/** Returns a safe href (unescaped) or null. Browsers ignore control chars and spaces when parsing URLs. */
export function safeHref(raw: string): string | null {
  const url = raw.replace(/[\u0000-\u0020\u007f]/g, '')
  if (!url) return null
  if (/^(?:https?|mailto):/i.test(url)) return url
  if (/^[^/?#]*:/.test(url)) return null // any other scheme (javascript:, data:, vbscript:, file:…)
  if (url.startsWith('//')) return null // protocol-relative = external host without a scheme
  return url
}

const UNESC: Record<string, string> = { '&amp;': '&', '&lt;': '<', '&gt;': '>', '&quot;': '"', '&#39;': "'" }
const unescapeHtml = (s: string) => s.replace(/&(?:amp|lt|gt|quot|#39);/g, (e) => UNESC[e])

function emphasis(s: string): string {
  return s
    .replace(/(\*\*|__)(?=\S)([\s\S]*?\S)\1/g, '<strong>$2</strong>')
    .replace(/(^|[^\w*])\*(?=\S)([^*]*?\S)\*(?!\*)/g, '$1<em>$2</em>')
    .replace(/(^|[^\w])_(?=\S)([^_]*?\S)_(?!\w)/g, '$1<em>$2</em>')
    .replace(/~~(?=\S)([\s\S]*?\S)~~/g, '<del>$1</del>')
}

function renderInline(src: string): string {
  // Finished HTML fragments (code spans, anchors) are held out of later passes behind \u0000n\u0000 tokens.
  const held: string[] = []
  const hold = (html: string) => {
    held.push(html)
    return `\u0000${held.length - 1}\u0000`
  }
  // 1. code spans first, so their content is not formatted
  let s = src.replace(/(`+)([^`]|[^`][\s\S]*?[^`])\1(?!`)/g, (_m, _t, code: string) =>
    hold(`<code>${escapeHtml(code.trim())}</code>`),
  )
  // 2. everything else is escaped before any markup is added
  s = escapeHtml(s)
  // 3. images → alt text only (no fetch); links → validated anchors
  s = s.replace(/!\[([^\]]*)\]\(([^)\s]*)(?:\s+&quot;[^&]*&quot;)?\)/g, (_m, alt: string) => alt)
  s = s.replace(/\[([^\]]+)\]\(([^)\s]*)(?:\s+&quot;[^&]*&quot;)?\)/g, (_m, text: string, href: string) => {
    const ok = safeHref(unescapeHtml(href))
    if (!ok) return text
    const external = /^(?:https?|mailto):/i.test(ok)
    const attrs = external ? ' target="_blank" rel="noopener noreferrer nofollow"' : ''
    return hold(`<a href="${escapeHtml(ok)}"${attrs}>${emphasis(text)}</a>`)
  })
  // 4. emphasis, 5. restore held fragments (anchors may contain code tokens: loop)
  s = emphasis(s)
  for (let pass = 0; pass < 3 && /\u0000\d+\u0000/.test(s); pass++) {
    s = s.replace(CODE_TOKEN, (_m, i: string) => held[Number(i)] ?? '')
  }
  return s
}

const RE_FENCE = /^\s{0,3}(```|~~~)/
const RE_HEADING = /^\s{0,3}(#{1,6})\s+(.*?)\s*#*\s*$/
const RE_HR = /^\s{0,3}([-*_])(?:\s*\1){2,}\s*$/
const RE_QUOTE = /^\s{0,3}>\s?/
const RE_LIST = /^(\s*)([-*+]|\d{1,9}[.)])\s+(.*)$/
const RE_TABLE_SEP = /^\s*\|?\s*:?-{2,}:?\s*(?:\|\s*:?-{2,}:?\s*)*\|?\s*$/

const indentOf = (line: string) => (/^\s*/.exec(line)?.[0] ?? '').replace(/\t/g, '    ').length
const isBlank = (line: string) => /^\s*$/.test(line)

function startsBlock(line: string) {
  return RE_FENCE.test(line) || RE_HEADING.test(line) || RE_HR.test(line) || RE_QUOTE.test(line) || RE_LIST.test(line)
}

function splitRow(line: string): string[] {
  let l = line.trim()
  if (l.startsWith('|')) l = l.slice(1)
  if (l.endsWith('|')) l = l.slice(0, -1)
  return l.split('|').map((c) => c.trim())
}

function renderBlocks(lines: string[], tight = false): string {
  const out: string[] = []
  let i = 0
  while (i < lines.length) {
    const line = lines[i]
    if (isBlank(line)) {
      i++
      continue
    }

    const fence = RE_FENCE.exec(line)
    if (fence) {
      const code: string[] = []
      i++
      while (i < lines.length && !lines[i].trim().startsWith(fence[1])) code.push(lines[i++])
      i++
      out.push(`<pre><code>${escapeHtml(code.join('\n'))}</code></pre>`)
      continue
    }

    const heading = RE_HEADING.exec(line)
    if (heading) {
      const level = heading[1].length
      out.push(`<h${level}>${renderInline(heading[2])}</h${level}>`)
      i++
      continue
    }

    if (RE_HR.test(line)) {
      out.push('<hr>')
      i++
      continue
    }

    if (RE_QUOTE.test(line)) {
      const quoted: string[] = []
      while (i < lines.length && !isBlank(lines[i]) && (RE_QUOTE.test(lines[i]) || !startsBlock(lines[i]))) {
        quoted.push(lines[i].replace(RE_QUOTE, ''))
        i++
      }
      out.push(`<blockquote>${renderBlocks(quoted)}</blockquote>`)
      continue
    }

    const list = RE_LIST.exec(line)
    if (list) {
      const baseIndent = indentOf(line)
      const ordered = /\d/.test(list[2])
      const start = ordered ? parseInt(list[2], 10) : 1
      const items: string[][] = []
      while (i < lines.length) {
        const l = lines[i]
        const m = RE_LIST.exec(l)
        if (m && indentOf(l) <= baseIndent + 1 && /\d/.test(m[2]) === ordered) {
          items.push([m[3]])
          i++
          continue
        }
        if (isBlank(l)) {
          // a blank line ends the list unless the next line continues it (indented or another item)
          const next = lines[i + 1]
          if (next !== undefined && (indentOf(next) > baseIndent || (RE_LIST.exec(next) && indentOf(next) <= baseIndent + 1))) {
            items[items.length - 1].push('')
            i++
            continue
          }
          break
        }
        if (indentOf(l) > baseIndent || !startsBlock(l)) {
          items[items.length - 1].push(l.replace(new RegExp(`^\\s{0,${baseIndent + 4}}`), ''))
          i++
          continue
        }
        break
      }
      const tag = ordered ? 'ol' : 'ul'
      const startAttr = ordered && start !== 1 ? ` start="${start}"` : ''
      out.push(`<${tag}${startAttr}>${items.map((it) => `<li>${renderBlocks(it, true)}</li>`).join('')}</${tag}>`)
      continue
    }

    if (line.includes('|') && i + 1 < lines.length && RE_TABLE_SEP.test(lines[i + 1])) {
      const head = splitRow(line)
      i += 2
      const rows: string[][] = []
      while (i < lines.length && !isBlank(lines[i]) && lines[i].includes('|')) rows.push(splitRow(lines[i++]))
      const th = head.map((c) => `<th>${renderInline(c)}</th>`).join('')
      const tb = rows.map((r) => `<tr>${head.map((_, k) => `<td>${renderInline(r[k] ?? '')}</td>`).join('')}</tr>`).join('')
      out.push(`<table><thead><tr>${th}</tr></thead><tbody>${tb}</tbody></table>`)
      continue
    }

    // paragraph
    const para: string[] = []
    while (i < lines.length && !isBlank(lines[i]) && (para.length === 0 || !startsBlock(lines[i]))) {
      para.push(lines[i].trim())
      i++
    }
    const html = para.map(renderInline).join('<br>')
    out.push(tight ? html : `<p>${html}</p>`)
  }
  return out.join(tight ? '' : '\n')
}

export function renderMarkdown(src: string | null | undefined): string {
  if (!src) return ''
  const lines = src.replace(/\r\n?/g, '\n').replace(/\u0000/g, '').split('\n')
  return renderBlocks(lines)
}
