import { defineStore } from 'pinia'
import { roleExperience, type ViewMode } from '@/utils/roleExperience'
import type { UserInfo } from '@/types/user'

export const useAppStore = defineStore('app', {
  state: () => ({
    sidebarCollapsed: false,
    visitedTitles: ['仪表盘'],
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
  },
})
