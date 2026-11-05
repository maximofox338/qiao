import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { showFailToast } from 'vant'
import { i18n } from '@/i18n'

declare module 'vue-router' {
  interface RouteMeta {
    auth?: boolean
    tab?: boolean
  }
}

export const router = createRouter({
  // BASE_URL is '/m/' (vite `base`).
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior(_to, _from, saved) {
    return saved ?? { top: 0 }
  },
  routes: [
    { path: '/', name: 'landing', component: () => import('@/views/LandingView.vue') },
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
    { path: '/legal/:doc', name: 'legal', component: () => import('@/views/LegalView.vue') },
    {
      path: '/projects',
      name: 'projects',
      component: () => import('@/views/ProjectsView.vue'),
      meta: { auth: true, tab: true },
    },
    {
      path: '/projects/:id',
      name: 'project',
      component: () => import('@/views/ProjectDetailView.vue'),
      meta: { auth: true },
    },
    {
      path: '/applications',
      name: 'applications',
      component: () => import('@/views/ApplicationsView.vue'),
      meta: { auth: true, tab: true },
    },
    {
      path: '/contracts',
      name: 'contracts',
      component: () => import('@/views/ContractsView.vue'),
      meta: { auth: true, tab: true },
    },
    {
      path: '/contracts/:id',
      name: 'contract',
      component: () => import('@/views/ContractDetailView.vue'),
      meta: { auth: true },
    },
    { path: '/profile', name: 'profile', component: () => import('@/views/ProfileView.vue'), meta: { auth: true } },
    { path: '/earnings', name: 'earnings', component: () => import('@/views/EarningsView.vue'), meta: { auth: true } },
    { path: '/me', name: 'me', component: () => import('@/views/MeView.vue'), meta: { auth: true, tab: true } },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue') },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.name === 'login' && auth.isLoggedIn) {
    return { name: 'projects' }
  }
  if (!to.meta.auth) return true
  if (!auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (!auth.me) {
    try {
      await auth.loadMe()
    } catch {
      // 401 is handled globally (token cleared); anything else: let the page show its own error state.
      if (!auth.isLoggedIn) return { name: 'login', query: { redirect: to.fullPath } }
      return true
    }
  }
  if (auth.me && auth.me.role !== 'ENGINEER') {
    auth.clear()
    showFailToast(i18n.global.t('login.notEngineer'))
    return { name: 'login' }
  }
  return true
})
