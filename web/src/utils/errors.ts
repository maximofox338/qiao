import { ElMessage } from 'element-plus'
import { ApiError } from '@/api/http'
import i18n from '@/i18n'

const t = (key: string, named?: Record<string, unknown>) => (named ? i18n.global.t(key, named) : i18n.global.t(key))
const te = (key: string) => i18n.global.te(key) || i18n.global.te(key, 'en')

/** Human message for any thrown value: translated by error code, falling back to the server message. */
export function errorText(err: unknown): string {
  if (err instanceof ApiError) {
    if (te(`errors.${err.code}`)) return t(`errors.${err.code}`)
    return err.message || t('errors.UNKNOWN_ERROR')
  }
  if (err instanceof Error && err.message) return err.message
  return t('errors.UNKNOWN_ERROR')
}

/** Translate a field-level validation code (`data.fields[field]`). */
export function fieldErrorText(code: string): string {
  if (te(`fieldErrors.${code}`)) return t(`fieldErrors.${code}`)
  if (te(`errors.${code}`)) return t(`errors.${code}`)
  return code
}

/** Maps a VALIDATION_ERROR into translated per-field messages. */
export function fieldErrorsOf(err: unknown): Record<string, string> {
  if (!(err instanceof ApiError)) return {}
  const out: Record<string, string> = {}
  for (const [field, code] of Object.entries(err.fields)) out[field] = fieldErrorText(code)
  return out
}

export function notifyError(err: unknown) {
  ElMessage({ type: 'error', message: errorText(err), grouping: true, showClose: true, duration: 5000 })
}

export function notifySuccess(message: string) {
  ElMessage({ type: 'success', message, grouping: true, duration: 2500 })
}
