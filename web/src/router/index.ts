import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import type { AdminRole } from '@/api/types'
import { isApiError } from '@/api/http'
import { useAreaLocale } from '@/i18n'
import { useUserStore } from '@/stores/user'
import { useAdminStore } from '@/stores/admin'

declare module 'vue-router' {
  interface RouteMeta {
    area?: 'client' | 'admin'
    requiresUser?: boolean
    requiresAdmin?: boolean
    /** Admin roles allowed to open the page (SUPERADMIN always allowed). */
    roles?: AdminRole[]
    titleKey?: string
  }
}

const ClientLayout = () => import('@/layouts/ClientLayout.vue')
const AdminLayout = () => import('@/layouts/AdminLayout.vue')

export const ADMIN_SECTIONS: Array<{ name: string; path: string; titleKey: string; roles?: AdminRole[]; icon: string }> = [
  { name: 'admin-dashboard', path: '', titleKey: 'admin.menu.dashboard', icon: 'DataBoard' },
  { name: 'admin-companies', path: 'companies', titleKey: 'admin.menu.companies', roles: ['OPS', 'COMPLIANCE'], icon: 'OfficeBuilding' },
  { name: 'admin-engineers', path: 'engineers', titleKey: 'admin.menu.engineers', roles: ['OPS'], icon: 'User' },
  { name: 'admin-projects', path: 'projects', titleKey: 'admin.menu.projects', roles: ['OPS'], icon: 'Document' },
  { name: 'admin-disputes', path: 'disputes', titleKey: 'admin.menu.disputes', roles: ['OPS'], icon: 'Warning' },
  { name: 'admin-settlements', path: 'settlements', titleKey: 'admin.menu.settlements', roles: ['FINANCE'], icon: 'Money' },
  { name: 'admin-compliance', path: 'compliance', titleKey: 'admin.menu.compliance', roles: ['COMPLIANCE'], icon: 'Lock' },
  { name: 'admin-categories', path: 'categories', titleKey: 'admin.menu.categories', roles: ['COMPLIANCE'], icon: 'CollectionTag' },
  { name: 'admin-outbox', path: 'outbox', titleKey: 'admin.menu.outbox', roles: ['OPS', 'COMPLIANCE'], icon: 'Message' },
  { name: 'admin-admins', path: 'admins', titleKey: 'admin.menu.admins', roles: ['SUPERADMIN'], icon: 'Key' },
]

const adminViews: Record<string, () => Promise<unknown>> = {
  'admin-dashboard': () => import('@/views/admin/AdminDashboardView.vue'),
  'admin-companies': () => import('@/views/admin/AdminCompaniesView.vue'),
  'admin-engineers': () => import('@/views/admin/AdminEngineersView.vue'),
  'admin-projects': () => import('@/views/admin/AdminProjectsView.vue'),
  'admin-disputes': () => import('@/views/admin/AdminDisputesView.vue'),
  'admin-settlements': () => import('@/views/admin/AdminSettlementsView.vue'),
  'admin-compliance': () => import('@/views/admin/AdminComplianceView.vue'),
  'admin-categories': () => import('@/views/admin/AdminCategoriesView.vue'),
  'admin-outbox': () => import('@/views/admin/AdminOutboxView.vue'),
  'admin-admins': () => import('@/views/admin/AdminAdminsView.vue'),
}

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior(to, _from, saved) {
    if (saved) return saved
    if (to.hash) return { el: to.hash, behavior: 'smooth' }
    return { top: 0 }
  },
  routes: [
    {
      path: '/admin/login',
      name: 'admin-login',
      component: () => import('@/views/admin/AdminLoginView.vue'),
      meta: { area: 'admin', titleKey: 'admin.login.title' },
    },
    {
      path: '/admin',
      component: AdminLayout,
      meta: { area: 'admin', requiresAdmin: true },
      children: [
        ...ADMIN_SECTIONS.map((s) => ({
          path: s.path,
          name: s.name,
          component: adminViews[s.name]!,
          meta: { roles: s.roles, titleKey: s.titleKey },
        })),
        { path: ':pathMatch(.*)*', redirect: { name: 'admin-dashboard' } },
      ],
    },
    {
      path: '/',
      component: ClientLayout,
      meta: { area: 'client' },
      children: [
        { path: '', name: 'landing', component: () => import('@/views/public/LandingView.vue') },
        { path: 'login', name: 'login', component: () => import('@/views/public/LoginView.vue'), meta: { titleKey: 'login.title' } },
        { path: 'legal/:doc', name: 'legal', component: () => import('@/views/public/LegalView.vue') },
        {
          path: 'client',
          name: 'client-dashboard',
          component: () => import('@/views/client/ClientDashboardView.vue'),
          meta: { requiresUser: true, titleKey: 'nav.dashboard' },
        },
        {
          path: 'client/company',
          name: 'client-company',
          component: () => import('@/views/client/CompanyView.vue'),
          meta: { requiresUser: true, titleKey: 'company.title' },
        },
        {
          path: 'client/projects/new',
          name: 'project-new',
          component: () => import('@/views/client/ProjectFormView.vue'),
          meta: { requiresUser: true, titleKey: 'projectForm.newTitle' },
        },
        {
          path: 'client/projects/:id/edit',
          name: 'project-edit',
          component: () => import('@/views/client/ProjectFormView.vue'),
          meta: { requiresUser: true, titleKey: 'projectForm.editTitle' },
        },
        {
          path: 'client/projects/:id',
          name: 'project-detail',
          component: () => import('@/views/client/ProjectDetailView.vue'),
          meta: { requiresUser: true, titleKey: 'project.title' },
        },
        {
          path: 'client/contracts/:id',
          name: 'contract',
          component: () => import('@/views/client/ContractView.vue'),
          meta: { requiresUser: true, titleKey: 'contract.title' },
        },
        {
          path: 'pay/fake/:paymentId',
          name: 'fake-pay',
          component: () => import('@/views/client/FakePayView.vue'),
          meta: { requiresUser: true, titleKey: 'pay.title' },
        },
        {
          path: 'account',
          name: 'account',
          component: () => import('@/views/client/AccountView.vue'),
          meta: { requiresUser: true, titleKey: 'account.title' },
        },
        { path: ':pathMatch(.*)*', name: 'not-found', component: () => import('@/views/public/NotFoundView.vue') },
      ],
    },
  ],
})

function areaOf(to: RouteLocationNormalized): 'client' | 'admin' {
  return to.matched.some((r) => r.meta.area === 'admin') ? 'admin' : 'client'
}

router.beforeEach(async (to) => {
  useAreaLocale(areaOf(to))

  if (to.matched.some((r) => r.meta.requiresUser)) {
    const user = useUserStore()
    user.syncFromStorage()
    if (!user.isLoggedIn) return { name: 'login', query: { redirect: to.fullPath } }
    try {
      await user.loadMe()
    } catch (e) {
      if (isApiError(e, 'UNAUTHORIZED') || (isApiError(e) && e.status === 401)) {
        return { name: 'login', query: { redirect: to.fullPath } }
      }
      // Backend unreachable or other error: let the page render and report its own errors.
    }
    if (user.me && user.me.role !== 'CLIENT') {
      user.clear()
      return { name: 'login', query: { notClient: '1' } }
    }
  }

  if (to.matched.some((r) => r.meta.requiresAdmin)) {
    const admin = useAdminStore()
    admin.syncFromStorage()
    if (!admin.isLoggedIn) return { name: 'admin-login', query: { redirect: to.fullPath } }
    try {
      await admin.loadMe()
    } catch (e) {
      if (isApiError(e) && (e.code === 'UNAUTHORIZED' || e.status === 401)) {
        return { name: 'admin-login', query: { redirect: to.fullPath } }
      }
    }
    const roles = to.meta.roles
    if (roles && !admin.hasAnyRole(roles)) return { name: 'admin-dashboard', query: { denied: '1' } }
  }
  return true
})

export default router
