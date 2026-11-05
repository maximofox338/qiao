import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { tokens } from '@/api/http'
import { adminApi } from '@/api/admin'
import type { AdminInfo, AdminRole } from '@/api/types'

const ADMIN_INFO_KEY = 'qiao.admin.info'

function readInfo(): AdminInfo | null {
  try {
    const raw = window.localStorage.getItem(ADMIN_INFO_KEY)
    return raw ? (JSON.parse(raw) as AdminInfo) : null
  } catch {
    return null
  }
}

function writeInfo(info: AdminInfo | null) {
  try {
    if (info) window.localStorage.setItem(ADMIN_INFO_KEY, JSON.stringify(info))
    else window.localStorage.removeItem(ADMIN_INFO_KEY)
  } catch {
    /* ignore */
  }
}

export function normaliseRoles(roles: AdminRole[] | string | null | undefined): AdminRole[] {
  if (!roles) return []
  if (Array.isArray(roles)) return roles
  return roles.split(',').map((r) => r.trim()).filter(Boolean) as AdminRole[]
}

export const useAdminStore = defineStore('admin', () => {
  const token = ref<string | null>(tokens.get('admin'))
  const admin = ref<AdminInfo | null>(token.value ? readInfo() : null)
  const loaded = ref(false)

  const isLoggedIn = computed(() => !!token.value)
  const roles = computed<AdminRole[]>(() => normaliseRoles(admin.value?.roles))

  /** SUPERADMIN can access every section. */
  function hasAnyRole(required?: AdminRole[]): boolean {
    if (!required || required.length === 0) return true
    if (roles.value.includes('SUPERADMIN')) return true
    return required.some((r) => roles.value.includes(r))
  }

  function setSession(newToken: string, info: AdminInfo) {
    tokens.set('admin', newToken)
    token.value = newToken
    admin.value = { ...info, roles: normaliseRoles(info.roles) }
    writeInfo(admin.value)
    loaded.value = true
  }

  async function loadMe(force = false) {
    if (!token.value) return null
    if (loaded.value && admin.value && !force) return admin.value
    const info = await adminApi.me()
    admin.value = { ...info, roles: normaliseRoles(info.roles) }
    writeInfo(admin.value)
    loaded.value = true
    return admin.value
  }

  function clear() {
    tokens.set('admin', null)
    writeInfo(null)
    token.value = null
    admin.value = null
    loaded.value = false
  }

  function syncFromStorage() {
    token.value = tokens.get('admin')
    if (!token.value) {
      admin.value = null
      loaded.value = false
    }
  }

  return { token, admin, roles, isLoggedIn, hasAnyRole, setSession, loadMe, clear, syncFromStorage }
})
