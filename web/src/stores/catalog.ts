import { defineStore } from 'pinia'
import { ref } from 'vue'
import { publicApi } from '@/api/user'
import type { Category } from '@/api/types'
import { currentLocale } from '@/i18n'

export const useCatalogStore = defineStore('catalog', () => {
  const categories = ref<Category[]>([])
  let pending: Promise<Category[]> | null = null

  async function loadCategories(force = false): Promise<Category[]> {
    if (categories.value.length && !force) return categories.value
    if (!pending) {
      pending = publicApi
        .categories()
        .then((list) => {
          categories.value = list ?? []
          return categories.value
        })
        .finally(() => {
          pending = null
        })
    }
    return pending
  }

  function categoryName(code: string | null | undefined): string {
    if (!code) return '—'
    const c = categories.value.find((x) => x.code === code)
    if (!c) return code
    const loc = currentLocale()
    return (loc === 'es' ? c.name?.es : c.name?.en) || c.name?.en || code
  }

  return { categories, loadCategories, categoryName }
})
