import { defineStore } from 'pinia'
import type { RouteLocationNormalized } from 'vue-router'
import { roleExperience, type ViewMode } from '@/utils/roleExperience'
import type { UserInfo } from '@/types/user'

export interface ReturnRouteState {
  fullPath: string
  path: string
  title: string
}

export const useAppStore = defineStore('app', {
  state: () => ({
    sidebarCollapsed: false,
    visitedTitles: ['仪表盘'],
    returnRoute: null as ReturnRouteState | null,
    skipNextReturnCapture: false,
    darkMode: false,
    viewMode: 'simple' as ViewMode,
    showAdvanced: false,
    showDiagnostics: false,
    showRawEvidence: false,
  }),
  actions: {
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
    },
    setViewMode(mode: ViewMode) {
      this.viewMode = mode
      this.showAdvanced = mode !== 'simple'
      this.showDiagnostics = mode === 'expert'
      this.showRawEvidence = mode === 'expert'
    },
    applyRoleExperience(roles: string[], userInfo?: UserInfo | null) {
      const experience = roleExperience(roles, userInfo)
      this.viewMode = experience.viewMode
      this.showAdvanced = experience.showAdvanced
      this.showDiagnostics = experience.showDiagnostics
      this.showRawEvidence = experience.showRawEvidence
    },
    addVisitedTitle(title: string) {
      if (!this.visitedTitles.includes(title)) {
        this.visitedTitles.push(title)
      }
    },
    captureReturnRoute(from: RouteLocationNormalized, to: RouteLocationNormalized) {
      if (this.skipNextReturnCapture) {
        this.returnRoute = null
        this.skipNextReturnCapture = false
        return
      }
      if (!isReturnableRoute(from) || !isReturnableRoute(to)) return
      if (from.path === to.path || from.fullPath === to.fullPath) return
      this.returnRoute = {
        fullPath: from.fullPath,
        path: from.path,
        title: String(from.meta.title || '原界面'),
      }
    },
    beginReturnNavigation() {
      this.skipNextReturnCapture = true
    },
    clearReturnRoute() {
      this.returnRoute = null
      this.skipNextReturnCapture = false
    },
  },
})

function isReturnableRoute(route: RouteLocationNormalized) {
  if (!route.name || route.path === '/' || route.matched.some((record) => record.meta.public)) return false
  return route.path === '/showcase'
    || route.path.startsWith('/soc')
    || route.path.startsWith('/system')
    || route.path.startsWith('/client')
}
