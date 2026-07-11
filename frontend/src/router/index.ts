import { createRouter, createWebHistory } from 'vue-router'
import { nextTick } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'
import AdminLayout from '@/layouts/AdminLayout.vue'
import ClientLayout from '@/layouts/ClientLayout.vue'
import { EXPERT_HOME_PATH } from '@/utils/roleExperience'
import { requiresUserWorkspace } from '@/utils/socUserWorkspace'
import { ensureMenuRoutes, fallbackProtectedRoutes, firstRoutePathFromMenus, resetMenuRoutes } from './menuRoutes'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) {
      return new Promise((resolve) => {
        window.setTimeout(() => {
          resolve({ el: to.hash, top: 96 })
        }, 60)
      })
    }
    return { top: 0 }
  },
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/login/LoginView.vue'), meta: { title: '登录', public: true } },
    { path: '/401', name: 'unauthorized', component: () => import('@/views/error/UnauthorizedView.vue'), meta: { title: '401', public: true } },
    { path: '/403', name: 'forbidden', component: () => import('@/views/error/ForbiddenView.vue'), meta: { title: '403', public: true } },
    { path: '/500', name: 'serverError', component: () => import('@/views/error/ServerErrorView.vue'), meta: { title: '500', public: true } },
    { path: '/showcase', name: 'showcase', component: () => import('@/views/showcase/ShowcaseView.vue'), meta: { title: '安全运营演示台', requiresAuth: true } },
    {
      path: '/client',
      name: 'clientRoot',
      component: ClientLayout,
      redirect: '/client/workbench',
      children: [
        { path: 'workbench', name: 'clientWorkbench', component: () => import('@/views/client/ClientWorkbenchView.vue'), meta: { title: 'CyberFusion 安全管家', requiresAuth: true } },
        { path: 'device-admin', name: 'clientDeviceAdmin', component: () => import('@/views/client/ClientDeviceAdminView.vue'), meta: { title: '设备信息', requiresAuth: true } },
        { path: 'data-report', name: 'clientDataReport', component: () => import('@/views/client/ClientDataReportView.vue'), meta: { title: '提交日志', requiresAuth: true } },
        { path: 'tasks', name: 'clientTasks', component: () => import('@/views/client/ClientOperationsView.vue'), meta: { title: '我的待办', requiresAuth: true } },
        { path: 'operations', name: 'clientOperations', component: () => import('@/views/client/ClientOperationsView.vue'), meta: { title: '我的待办', requiresAuth: true } },
        { path: 'local-range', name: 'clientLocalRange', component: () => import('@/views/client/ClientLocalRangeView.vue'), meta: { title: '安全工具箱', requiresAuth: true } },
        { path: 'security-logs', name: 'clientSecurityLogs', component: () => import('@/views/client/ClientSecurityLogsView.vue'), meta: { title: '安全日志', requiresAuth: true } },
        { path: 'protection', name: 'clientProtection', component: () => import('@/views/client/ClientProtectionView.vue'), meta: { title: '本机保护', requiresAuth: true } },
      ],
    },
    { path: '/', name: 'adminRoot', component: AdminLayout, redirect: EXPERT_HOME_PATH, children: fallbackProtectedRoutes },
    { path: '/:pathMatch(.*)*', name: 'notFound', component: () => import('@/views/error/NotFoundView.vue'), meta: { title: '404', public: true } },
  ],
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  const appStore = useAppStore()
  const title = String(to.meta.title || '安全运营')
  document.title = `${title} - CyberFusion SOC`

  if (to.path === '/login') {
    if (!authStore.isAuthenticated) return true
    if (!authStore.initialized) {
      try {
        await authStore.fetchCurrentUser()
        appStore.applyRoleExperience(authStore.roles, authStore.userInfo)
        ensureMenuRoutes(router, authStore.menus)
      } catch {
        return true
      }
    }
    return firstRoutePathFromMenus(authStore.menus, authStore.roles, authStore.userInfo)
  }
  if (to.matched.some((record) => record.meta.public)) return true

  const previousOwnerId = typeof router.currentRoute.value.query.ownerId === 'string'
    ? router.currentRoute.value.query.ownerId
    : ''
  const targetOwnerId = typeof to.query.ownerId === 'string' ? to.query.ownerId : ''
  const remainsInSocWorkspace = requiresUserWorkspace(to.path)
  if (remainsInSocWorkspace && /^\d+$/.test(previousOwnerId) && !targetOwnerId) {
    return {
      path: to.path,
      query: { ...to.query, ownerId: previousOwnerId },
      hash: to.hash,
      replace: true,
    }
  }

  if (!authStore.isAuthenticated) {
    resetMenuRoutes(router)
    return { path: '/login', query: { redirect: to.fullPath }, replace: true }
  }
  if (!authStore.initialized) {
    try {
      await authStore.fetchCurrentUser()
      appStore.applyRoleExperience(authStore.roles, authStore.userInfo)
    } catch {
      resetMenuRoutes(router)
      return { path: '/login', query: { redirect: to.fullPath }, replace: true }
    }
  } else {
    appStore.applyRoleExperience(authStore.roles, authStore.userInfo)
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
  if (requiresUserWorkspace(to.path) && !targetOwnerId) {
    return { path: '/soc/user-workspaces', query: { target: to.fullPath }, replace: true }
  }
  appStore.addVisitedTitle(title)
  return true
})

function scrollToRouteHash(hash: string) {
  let target: Element | null = null
  try {
    target = document.querySelector(hash)
  } catch {
    target = null
  }
  if (!(target instanceof HTMLElement)) return
  const top = target.getBoundingClientRect().top + window.scrollY - 96
  window.scrollTo({ top: Math.max(0, top) })
}

router.afterEach((to, from) => {
  const appStore = useAppStore()
  appStore.captureReturnRoute(from, to)
  if (!to.hash) return
  void nextTick(() => {
    window.setTimeout(() => scrollToRouteHash(to.hash), 120)
    window.setTimeout(() => scrollToRouteHash(to.hash), 520)
  })
})

export default router
