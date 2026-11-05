import { ApiError } from '@/api/client'
import { i18n } from '@/i18n'

/** User-facing, translated message for any error thrown by the API client (translated by `code`). */
export function errorMessage(e: unknown): string {
  const { t, te, locale } = i18n.global
  if (e instanceof ApiError) {
    if (e.code === 'VALIDATION_ERROR') {
      const names = Object.keys(e.fields).map((f) => {
        const base = f.replace(/\[\d+\].*$/, '').replace(/\..*$/, '')
        return te(`fields.${base}`) ? t(`fields.${base}`) : f
      })
      if (names.length) return t('errors.checkFields', { fields: Array.from(new Set(names)).join('、') })
      return t('errors.VALIDATION_ERROR')
    }
    if (te(`errors.${e.code}`)) return t(`errors.${e.code}`)
    // Unknown code: the English fallback message is fine in English; otherwise show a generic text.
    if (locale.value === 'en' && e.message && e.message !== e.code) return e.message
    return t('errors.UNKNOWN', { code: e.code })
  }
  return t('errors.UNKNOWN', { code: 'CLIENT' })
}

export function isCode(e: unknown, code: string): boolean {
  return e instanceof ApiError && e.code === code
}
