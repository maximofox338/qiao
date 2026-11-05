import { defineStore } from 'pinia'
import { ref } from 'vue'
import { publicApi } from '@/api/endpoints'
import type { Category } from '@/api/types'
import { i18n } from '@/i18n'
import { ALLOWED_CATEGORIES } from '@/data/categories'

/** Cached public data: categories and platform config (fee %). */
export const useMetaStore = defineStore('meta', () => {
  const categories = ref<Category[]>([])
  const feePct = ref<number | null>(null)
  let categoriesPromise: Promise<void> | null = null
  let configPromise: Promise<void> | null = null

  function loadCategories() {
    categoriesPromise ??= publicApi
      .categories()
      .then((list) => {
        categories.value = Array.isArray(list) ? list : []
      })
      .catch(() => {
        categoriesPromise = null
      })
    return categoriesPromise
  }

  function loadConfig() {
    configPromise ??= publicApi
      .config()
      .then((c) => {
        feePct.value = typeof c?.feePct === 'number' ? c.feePct : Number(c?.feePct) || null
      })
      .catch(() => {
        configPromise = null
      })
    return configPromise
  }

  /** Allowed categories for filters: from the API when loaded, else the seed list. */
  function allowedCodes(): string[] {
    const fromApi = categories.value.filter((c) => c.allowed).map((c) => c.code)
    return fromApi.length ? fromApi : [...ALLOWED_CATEGORIES]
  }

  function categoryName(code: string | null | undefined): string {
    if (!code) return ''
    const { t, te, locale } = i18n.global
    const c = categories.value.find((x) => x.code === code)
    if (c?.name) {
      const name = locale.value === 'en' ? c.name.en : c.name.zh
      if (name) return name
    }
    return te(`categories.${code}`) ? t(`categories.${code}`) : code
  }

  return { categories, feePct, loadCategories, loadConfig, allowedCodes, categoryName }
})
