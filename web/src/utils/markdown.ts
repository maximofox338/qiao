import MarkdownIt from 'markdown-it'

/**
 * Safe markdown renderer for legal texts and contract documents.
 * - raw HTML in the source is escaped (html: false)
 * - only http(s), mailto and relative links are kept; markdown-it already blocks javascript:/vbscript:/data:
 * - external links open in a new tab with rel="noopener noreferrer"; nothing is ever fetched (images are not rendered)
 */
const md = new MarkdownIt({ html: false, linkify: false, typographer: false, breaks: false })

md.disable(['image'])

const SAFE_LINK = /^(https?:|mailto:|\/|#|\.\/|\.\.\/)/i
const defaultValidate = md.validateLink.bind(md)
md.validateLink = (url: string) => defaultValidate(url) && (SAFE_LINK.test(url.trim()) || !/^[a-z][a-z0-9+.-]*:/i.test(url.trim()))

const defaultLinkOpen =
  md.renderer.rules.link_open ?? ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))

md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const href = String(token?.attrGet('href') ?? '')
  if (token && /^https?:/i.test(href)) {
    token.attrSet('target', '_blank')
    token.attrSet('rel', 'noopener noreferrer')
  }
  return defaultLinkOpen(tokens, idx, options, env, self)
}

export function renderMarkdown(src: string | null | undefined): string {
  return md.render(src ?? '')
}

/** Escape text for safe insertion with v-html (used to highlight keywords). */
export function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] as string)
}

/** Escapes `text` and wraps case-insensitive occurrences of `keywords` in <mark>. */
export function highlightKeywords(text: string | null | undefined, keywords: string[]): string {
  const safe = escapeHtml(text ?? '')
  const terms = keywords.map((k) => k.trim()).filter(Boolean)
  if (!terms.length) return safe
  const pattern = terms
    .map(escapeHtml)
    .sort((a, b) => b.length - a.length)
    .map((k) => k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
    .join('|')
  return safe.replace(new RegExp(`(${pattern})`, 'gi'), '<mark>$1</mark>')
}
