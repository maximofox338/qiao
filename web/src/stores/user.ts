import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { tokens } from '@/api/http'
import { authApi, meApi } from '@/api/user'
import type { Me } from '@/api/types'

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(tokens.get('user'))
  const me = ref<Me | null>(null)

  const isLoggedIn = computed(() => !!token.value)

  function setSession(newToken: string, user: Me | null) {
    tokens.set('user', newToken)
    token.value = newToken
    me.value = user
  }

  async function loadMe(force = false): Promise<Me | null> {
    if (!token.value) return null
    if (me.value && !force) return me.value
    me.value = await meApi.get()
    return me.value
  }

  function clear() {
    tokens.set('user', null)
    token.value = null
    me.value = null
  }

  /** Re-sync after an interceptor cleared the stored token (401). */
  function syncFromStorage() {
    token.value = tokens.get('user')
    if (!token.value) me.value = null
  }

  async function logout() {
    try {
      if (token.value) await authApi.logout()
    } catch {
      /* token may already be invalid */
    }
    clear()
  }

  return { token, me, isLoggedIn, setSession, loadMe, clear, syncFromStorage, logout }
})
