import { createI18n } from 'vue-i18n'
import en from './locales/en'
import es from './locales/es'

export type AppLocale = 'en' | 'es'
export type LocaleArea = 'client' | 'admin'

/** Client portal offers en + es (default from browser, fallback en). Admin offers es + en (default es). */
export const AREA_LOCALES: Record<LocaleArea, AppLocale[]> = {
  client: ['en', 'es'],
  admin: ['es', 'en'],
}

const STORAGE_KEYS: Record<LocaleArea, string> = {
  client: 'qiao.locale',
  admin: 'qiao.admin.locale',
}

function isLocale(v: unknown): v is AppLocale {
  return v === 'en' || v === 'es'
}

function stored(area: LocaleArea): AppLocale | null {
  try {
    const v = window.localStorage.getItem(STORAGE_KEYS[area])
    return isLocale(v) ? v : null
  } catch {
    return null
  }
}

export function browserLocale(): AppLocale {
  const langs = typeof navigator !== 'undefined' ? (navigator.languages?.length ? navigator.languages : [navigator.language]) : []
  for (const l of langs) {
    const base = (l || '').toLowerCase().split('-')[0]
    if (isLocale(base)) return base
  }
  return 'en'
}

export function preferredLocale(area: LocaleArea): AppLocale {
  return stored(area) ?? (area === 'admin' ? 'es' : browserLocale())
}

export const i18n = createI18n({
  legacy: false,
  locale: preferredLocale('client'),
  fallbackLocale: 'en',
  messages: { en, es },
  missingWarn: false,
  fallbackWarn: false,
})

let currentArea: LocaleArea = 'client'

function apply(locale: AppLocale) {
  i18n.global.locale.value = locale
  if (typeof document !== 'undefined') document.documentElement.lang = locale
}

export function currentLocale(): AppLocale {
  return i18n.global.locale.value as AppLocale
}

/** Called by the router when entering an area: each area remembers its own locale. */
export function useAreaLocale(area: LocaleArea) {
  currentArea = area
  apply(preferredLocale(area))
}

export function setLocale(locale: AppLocale, area: LocaleArea = currentArea) {
  try {
    window.localStorage.setItem(STORAGE_KEYS[area], locale)
  } catch {
    /* ignore */
  }
  if (area === currentArea) apply(locale)
}

export default i18n
