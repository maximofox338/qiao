import { currentLocale } from '@/i18n'

function intlLocale() {
  return currentLocale() === 'en' ? 'en-US' : 'zh-CN'
}

export function toNumber(v: number | string | null | undefined): number {
  if (v === null || v === undefined || v === '') return 0
  const n = typeof v === 'number' ? v : Number(v)
  return Number.isFinite(n) ? n : 0
}

/** USD amount, e.g. "US$1,250.00" (zh) / "$1,250.00" (en). */
export function formatUsd(v: number | string | null | undefined, opts: { compact?: boolean } = {}): string {
  const n = toNumber(v)
  try {
    return new Intl.NumberFormat(intlLocale(), {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: opts.compact && Number.isInteger(n) ? 0 : 2,
      maximumFractionDigits: 2,
    }).format(n)
  } catch {
    return `$${n.toFixed(2)}`
  }
}

const pad = (n: number) => String(n).padStart(2, '0')

/**
 * Compact `YYYY-MM-DD` in every locale (short enough for phone cards). API dates (`YYYY-MM-DD`) are
 * calendar dates and are shown as-is; ISO timestamps are converted to the viewer's local date.
 */
export function formatDate(v: string | null | undefined): string {
  if (!v) return ''
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(v)
  if (!m) return v
  if (v.length > 10) {
    const d = new Date(v)
    if (!Number.isNaN(d.getTime())) return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  }
  return `${m[1]}-${m[2]}-${m[3]}`
}

export function formatDateTime(v: string | null | undefined): string {
  if (!v) return ''
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return v
  return d.toLocaleString(intlLocale(), {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

const regionNames: Record<string, Intl.DisplayNames | null> = {}
/** ISO-3166 alpha-2 → localized country name, using the browser's own data (no network). */
export function countryName(code: string | null | undefined): string {
  if (!code) return ''
  const loc = intlLocale()
  if (!(loc in regionNames)) {
    try {
      regionNames[loc] = new Intl.DisplayNames([loc], { type: 'region' })
    } catch {
      regionNames[loc] = null
    }
  }
  try {
    return regionNames[loc]?.of(code.toUpperCase()) ?? code
  } catch {
    return code
  }
}

export function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

export function sameId(a: unknown, b: unknown): boolean {
  return a !== null && a !== undefined && b !== null && b !== undefined && String(a) === String(b)
}
