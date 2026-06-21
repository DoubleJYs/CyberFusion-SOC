import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
  state: () => ({
    sidebarCollapsed: false,
    visitedTitles: ['仪表盘'],
    darkMode: false,
  }),
  actions: {
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
    },
    addVisitedTitle(title: string) {
      if (!this.visitedTitles.includes(title)) {
        this.visitedTitles.push(title)
      }
    },
  },
})
