import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface ConfirmOptions {
  title: string
  message: string
  confirmText?: string
  cancelText?: string
  danger?: boolean
  showCancel?: boolean
}

/** Promise-based confirm dialog rendered once in App.vue (testids: confirm-ok / confirm-cancel). */
export const useConfirmStore = defineStore('confirm', () => {
  const visible = ref(false)
  const options = ref<ConfirmOptions>({ title: '', message: '' })
  let resolver: ((ok: boolean) => void) | null = null

  function open(opts: ConfirmOptions): Promise<boolean> {
    resolver?.(false)
    options.value = { showCancel: true, ...opts }
    visible.value = true
    return new Promise((resolve) => {
      resolver = resolve
    })
  }

  function close(ok: boolean) {
    visible.value = false
    resolver?.(ok)
    resolver = null
  }

  return { visible, options, open, close }
})
