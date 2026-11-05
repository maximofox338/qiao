<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Menu as MenuIcon, Close } from '@element-plus/icons-vue'
import LocaleSwitch from '@/components/LocaleSwitch.vue'
import { useUserStore } from '@/stores/user'
import { meApi } from '@/api/user'
import { isApiError } from '@/api/http'
import type { AppLocale } from '@/i18n'

const { t } = useI18n()
const user = useUserStore()
const route = useRoute()
const router = useRouter()
const menuOpen = ref(false)

const loggedIn = computed(() => user.isLoggedIn && (!user.me || user.me.role === 'CLIENT'))

const nav = computed(() => [
  { to: '/client', label: t('nav.dashboard'), testid: 'nav-dashboard' },
  { to: '/client/company', label: t('nav.company'), testid: 'nav-company' },
  { to: '/account', label: t('nav.account'), testid: 'nav-account' },
])

watch(
  () => route.fullPath,
  () => {
    menuOpen.value = false
  },
)

onMounted(async () => {
  // Public pages: validate a stored token silently (no redirect on 401).
  if (user.isLoggedIn && !user.me) {
    try {
      user.me = await meApi.get({ skipAuthRedirect: true })
    } catch (e) {
      // Expired or revoked token on a public page: forget it without redirecting.
      if (isApiError(e) && (e.status === 401 || e.code === 'UNAUTHORIZED')) user.clear()
    }
  }
})

async function onLocale(l: AppLocale) {
  if (user.isLoggedIn) {
    try {
      await meApi.update(l)
    } catch {
      /* preference stays local */
    }
  }
}

async function logout() {
  await user.logout()
  await router.push({ name: 'landing' })
}

function isActive(to: string) {
  return to === '/client' ? route.path === '/client' || route.path.startsWith('/client/projects') || route.path.startsWith('/client/contracts') : route.path.startsWith(to)
}
</script>

<template>
  <div class="client-shell">
    <header class="site-header">
      <div class="q-container header-inner">
        <router-link :to="loggedIn ? '/client' : '/'" class="brand" data-testid="nav-brand">
          <svg class="brand-mark" viewBox="0 0 32 32" aria-hidden="true">
            <rect width="32" height="32" rx="7" fill="currentColor" />
            <path d="M5 22c4-9 18-9 22 0" fill="none" stroke="#fff" stroke-width="2.4" stroke-linecap="round" />
            <path d="M4 22h24M10 22v-5M16 22v-7M22 22v-5" stroke="#fff" stroke-width="2" stroke-linecap="round" />
          </svg>
          <span class="brand-name">{{ t('app.name') }}</span>
          <span class="brand-cn" aria-hidden="true">桥</span>
        </router-link>

        <nav v-if="loggedIn" class="main-nav" :class="{ open: menuOpen }" :aria-label="t('nav.main')">
          <router-link
            v-for="item in nav"
            :key="item.to"
            :to="item.to"
            class="nav-link"
            :class="{ active: isActive(item.to) }"
            :data-testid="item.testid"
          >
            {{ item.label }}
          </router-link>
        </nav>

        <div class="header-right">
          <LocaleSwitch area="client" @change="onLocale" />
          <template v-if="loggedIn">
            <el-button class="hide-sm" text data-testid="nav-logout" @click="logout">{{ t('nav.logout') }}</el-button>
            <button
              type="button"
              class="menu-toggle"
              :aria-expanded="menuOpen"
              :aria-label="t('nav.menu')"
              data-testid="nav-menu-toggle"
              @click="menuOpen = !menuOpen"
            >
              <el-icon :size="20"><Close v-if="menuOpen" /><MenuIcon v-else /></el-icon>
            </button>
          </template>
          <el-button v-else-if="route.name !== 'login'" type="primary" data-testid="nav-login" @click="router.push({ name: 'login' })">
            {{ t('nav.login') }}
          </el-button>
        </div>
      </div>
      <div v-if="loggedIn && menuOpen" class="mobile-nav">
        <router-link v-for="item in nav" :key="item.to" :to="item.to" class="mobile-link" :data-testid="`${item.testid}-mobile`">
          {{ item.label }}
        </router-link>
        <button type="button" class="mobile-link as-button" data-testid="nav-logout-mobile" @click="logout">{{ t('nav.logout') }}</button>
      </div>
    </header>

    <main class="site-main">
      <router-view />
    </main>

    <footer class="site-footer">
      <div class="q-container footer-inner">
        <div class="q-muted q-small">{{ t('footer.company') }}</div>
        <nav class="footer-links" :aria-label="t('footer.legal')">
          <router-link to="/legal/terms" data-testid="footer-terms">{{ t('legal.docs.terms') }}</router-link>
          <router-link to="/legal/privacy" data-testid="footer-privacy">{{ t('legal.docs.privacy') }}</router-link>
          <router-link to="/legal/client-declarations" data-testid="footer-declarations">{{ t('legal.docs.client-declarations') }}</router-link>
        </nav>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.client-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.site-header {
  background: var(--q-surface);
  border-bottom: 1px solid var(--q-border);
  position: sticky;
  top: 0;
  z-index: 50;
}
.header-inner {
  display: flex;
  align-items: center;
  gap: 24px;
  height: 60px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--q-accent);
  font-weight: 700;
  font-size: 18px;
  text-decoration: none;
  flex-shrink: 0;
}
.brand:hover {
  text-decoration: none;
}
.brand-mark {
  width: 28px;
  height: 28px;
}
.brand-name {
  color: var(--q-text);
  letter-spacing: 0.01em;
}
.brand-cn {
  color: var(--q-text-3);
  font-weight: 500;
  font-size: 15px;
}
.main-nav {
  display: flex;
  gap: 4px;
  flex: 1;
}
.nav-link {
  color: var(--q-text-2);
  padding: 6px 10px;
  border-radius: 6px;
  font-weight: 550;
  text-decoration: none;
}
.nav-link:hover {
  background: var(--q-bg);
  text-decoration: none;
}
.nav-link.active {
  color: var(--q-accent);
  background: var(--q-accent-soft);
}
.header-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}
.menu-toggle {
  display: none;
  border: 1px solid var(--q-border);
  background: var(--q-surface);
  border-radius: 6px;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--q-text-2);
}
.mobile-nav {
  display: none;
}
.site-main {
  flex: 1;
}
.site-footer {
  border-top: 1px solid var(--q-border);
  background: var(--q-surface);
  padding: 20px 0;
}
.footer-inner {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}
.footer-links {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 14px;
}

@media (max-width: 760px) {
  .main-nav {
    display: none;
  }
  .hide-sm {
    display: none;
  }
  .menu-toggle {
    display: inline-flex;
  }
  .mobile-nav {
    display: flex;
    flex-direction: column;
    border-top: 1px solid var(--q-border);
    padding: 8px 16px 12px;
  }
  .mobile-link {
    padding: 10px 4px;
    color: var(--q-text);
    font-weight: 550;
    border-bottom: 1px solid var(--q-border);
    text-decoration: none;
  }
  .as-button {
    font: inherit;
    text-align: left;
    background: none;
    border: 0;
    cursor: pointer;
    color: var(--q-text-2);
  }
  .brand-cn {
    display: none;
  }
}
</style>
