import { createI18n } from 'vue-i18n'
import { Locale as VantLocale } from 'vant'
import vantEnUS from 'vant/es/locale/lang/en-US'
import vantZhCN from 'vant/es/locale/lang/zh-CN'
import zhCN from './zh-CN'
import en from './en'
import { storage } from '@/utils/storage'

export type AppLocale = 'zh-CN' | 'en'
export const LOCALES: AppLocale[] = ['zh-CN', 'en']
const LOCALE_KEY = 'qiao.h5.locale'

function initialLocale(): AppLocale {
  const saved = storage.get(LOCALE_KEY)
  return saved === 'en' || saved === 'zh-CN' ? saved : 'zh-CN'
}

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: initialLocale(),
  fallbackLocale: 'zh-CN',
  messages: { 'zh-CN': zhCN, en },
  missingWarn: false,
  fallbackWarn: false,
})

export function currentLocale(): AppLocale {
  return i18n.global.locale.value as AppLocale
}

export function normalizeLocale(value: string | null | undefined): AppLocale | null {
  if (!value) return null
  const v = value.toLowerCase()
  if (v.startsWith('zh')) return 'zh-CN'
  if (v.startsWith('en')) return 'en'
  return null
}

export function applyLocale(locale: AppLocale) {
  i18n.global.locale.value = locale
  storage.set(LOCALE_KEY, locale)
  document.documentElement.lang = locale
  document.title = i18n.global.t('app.title')
  if (locale === 'en') VantLocale.use('en-US', vantEnUS)
  else VantLocale.use('zh-CN', vantZhCN)
}

applyLocale(currentLocale())
