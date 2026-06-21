import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'
import AdminLayout from '@/layouts/AdminLayout.vue'
import { ensureMenuRoutes, fallbackProtectedRoutes, firstRoutePathFromMenus, resetMenuRoutes } from './menuRoutes'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/login/LoginView.vue'), meta: { title: '登录', public: true } },
    { path: '/401', name: 'unauthorized', component: () => import('@/views/error/UnauthorizedView.vue'), meta: { title: '401', public: true } },
    { path: '/403', name: 'forbidden', component: () => import('@/views/error/ForbiddenView.vue'), meta: { title: '403', public: true } },
    { path: '/500', name: 'serverError', component: () => import('@/views/error/ServerErrorView.vue'), meta: { title: '500', public: true } },
    { path: '/', name: 'adminRoot', component: AdminLayout, redirect: '/soc/dashboard', children: fallbackProtectedRoutes },
    { path: '/:pathMatch(.*)*', name: 'notFound', component: () => import('@/views/error/NotFoundView.vue'), meta: { title: '404', public: true } },
  ],
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  const appStore = useAppStore()
  const title = String(to.meta.title || '安全运营')
  document.title = `${title} - Sec Wazuh SOC`

  if (to.path === '/login') {
    if (!authStore.isAuthenticated) return true
    if (!authStore.initialized) {
    try {
      await authStore.fetchCurrentUser()
      ensureMenuRoutes(router, authStore.menus)
    } catch {
      return true
    }
    }
    return firstRoutePathFromMenus(authStore.menus)
  }
  if (to.matched.some((record) => record.meta.public)) return true

  if (!authStore.isAuthenticated) {
    resetMenuRoutes(router)
    return { path: '/login', query: { redirect: to.fullPath }, replace: true }
  }
  if (!authStore.initialized) {
    try {
      await authStore.fetchCurrentUser()
    } catch {
      resetMenuRoutes(router)
      return { path: '/login', query: { redirect: to.fullPath }, replace: true }
    }
  }
  const addedRoutes = ensureMenuRoutes(router, authStore.menus)
  if (addedRoutes && to.matched.length === 0) {
    return { path: to.fullPath, replace: true }
  }
  const roles = to.matched.flatMap((record) => record.meta.roles || [])
  const permissions = to.matched.flatMap((record) => record.meta.permissions || [])
  const roleAllowed = roles.length === 0 || roles.some((role) => authStore.hasRole(role))
  const permissionAllowed = permissions.length === 0 || permissions.some((permission) => authStore.hasPermission(permission))
  if (!roleAllowed || !permissionAllowed) {
    return { path: '/403', replace: true }
  }
  appStore.addVisitedTitle(title)
  return true
})

export default router
