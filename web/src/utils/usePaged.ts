import { ref, shallowRef } from 'vue'
import type { Page } from '@/api/types'
import { errorText } from './errors'

/** Small helper for paged admin tables. */
export function usePaged<T>(fetcher: (page: number, size: number) => Promise<Page<T>>, pageSize = 20) {
  const rows = shallowRef<T[]>([])
  const total = ref(0)
  const page = ref(1)
  const size = ref(pageSize)
  const loading = ref(false)
  const error = ref('')

  async function load(p: number = page.value) {
    loading.value = true
    error.value = ''
    page.value = p
    try {
      const res = await fetcher(p, size.value)
      rows.value = res.records
      total.value = res.total
    } catch (e) {
      error.value = errorText(e)
      rows.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  return { rows, total, page, size, loading, error, load }
}
