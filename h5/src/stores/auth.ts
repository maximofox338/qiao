import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getToken, setToken } from '@/api/client'
import { authApi, meApi } from '@/api/endpoints'
import type { ConsentRecord, ConsentType, Id, Me } from '@/api/types'
import { sameId } from '@/utils/format'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  const me = ref<Me | null>(null)
  const consents = ref<ConsentRecord[]>([])
  const consentsLoaded = ref(false)

  const isLoggedIn = computed(() => !!token.value)
  const profileStatus = computed(() => me.value?.engineerProfileStatus ?? null)

  function setSession(value: string) {
    setToken(value)
    token.value = value
  }

  async function loadMe() {
    me.value = await meApi.get()
    return me.value
  }

  async function loadConsents() {
    consents.value = await meApi.consents()
    consentsLoaded.value = true
    return consents.value
  }

  /** Latest state of a consent. Scoped consents (SHARE_FULL_CV) come from the consents list. */
  function hasConsent(type: ConsentType, scopeRef?: Id): boolean {
    if (scopeRef !== undefined) {
      const row = consents.value.find((c) => c.type === type && sameId(c.scopeRef, scopeRef))
      return !!row?.granted
    }
    const fromMe = me.value?.consents?.[type]
    if (fromMe !== undefined) return !!fromMe
    const row = consents.value.find((c) => c.type === type && (c.scopeRef === null || c.scopeRef === undefined))
    return !!row?.granted
  }

  async function setConsent(type: ConsentType, granted: boolean, scopeRef?: Id) {
    await meApi.setConsent(type, granted, scopeRef)
    await Promise.allSettled([loadMe(), loadConsents()])
  }

  function clear() {
    setToken(null)
    token.value = null
    me.value = null
    consents.value = []
    consentsLoaded.value = false
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch {
      /* token may already be invalid; logging out locally is enough */
    }
    clear()
  }

  return {
    token,
    me,
    consents,
    consentsLoaded,
    isLoggedIn,
    profileStatus,
    setSession,
    loadMe,
    loadConsents,
    hasConsent,
    setConsent,
    clear,
    logout,
  }
})
