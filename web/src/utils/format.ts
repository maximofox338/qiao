import { currentLocale } from '@/i18n'

const localeTag = () => (currentLocale() === 'es' ? 'es-ES' : 'en-US')

export function money(v: number | string | null | undefined): string {
  if (v === null || v === undefined || v === '') return '—'
  const n = typeof v === 'number' ? v : Number(v)
  if (!Number.isFinite(n)) return '—'
  return new Intl.NumberFormat(localeTag(), { style: 'currency', currency: 'USD', minimumFractionDigits: 2 }).format(n)
}

export function num(v: number | null | undefined): string {
  if (v === null || v === undefined || !Number.isFinite(v)) return '—'
  return new Intl.NumberFormat(localeTag()).format(v)
}

/** `YYYY-MM-DD` → localised date without timezone shifts. */
export function date(v: string | null | undefined): string {
  if (!v) return '—'
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(v)
  if (!m) return v
  const d = new Date(Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3])))
  return new Intl.DateTimeFormat(localeTag(), { year: 'numeric', month: 'short', day: 'numeric', timeZone: 'UTC' }).format(d)
}

export function dateTime(v: string | null | undefined): string {
  if (!v) return '—'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return v
  return new Intl.DateTimeFormat(localeTag(), { dateStyle: 'medium', timeStyle: 'short' }).format(d)
}

export function bytes(n: number | null | undefined): string {
  if (n === null || n === undefined || !Number.isFinite(n)) return '—'
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(1)} MB`
}

/** Round to cents, avoiding float drift when summing milestone amounts. */
export function cents(n: number): number {
  return Math.round((n + Number.EPSILON) * 100) / 100
}

export function sumAmounts(values: Array<number | null | undefined>): number {
  return values.reduce<number>((acc, v) => acc + Math.round((Number(v) || 0) * 100), 0) / 100
}

/** Number of whole months between two YYYY-MM-DD dates, used for the 12-month duration hint. */
export function exceedsTwelveMonths(start: string, end: string): boolean {
  if (!start || !end) return false
  const s = new Date(`${start}T00:00:00Z`)
  const limit = new Date(s)
  limit.setUTCMonth(limit.getUTCMonth() + 12)
  return new Date(`${end}T00:00:00Z`).getTime() > limit.getTime()
}
