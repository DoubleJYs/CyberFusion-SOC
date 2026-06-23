import { defineStore } from 'pinia'
import { loginApi, logoutApi, meApi, refreshApi } from '@/api/auth'
import { clearToken, getToken, setToken } from '@/utils/storage'
import type { MenuItem } from '@/types/system'
import type { LoginRequest, UserInfo } from '@/types/user'

interface AuthState {
  accessToken: string
  userInfo: UserInfo | null
  roles: string[]
  permissions: string[]
  menus: MenuItem[]
  initialized: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    accessToken: getToken(),
    userInfo: null,
    roles: [],
    permissions: [],
    menus: [],
    initialized: false,
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.accessToken),
    profile: (state) => state.userInfo ? { ...state.userInfo, roles: state.roles } : null,
  },
  actions: {
    applySession(session: Awaited<ReturnType<typeof loginApi>>) {
      this.accessToken = session.accessToken
      this.userInfo = session.userInfo
      this.roles = session.roles || []
      this.permissions = session.permissions || []
      this.menus = session.menus || []
      this.initialized = true
      setToken(session.accessToken)
    },
    async login(form: LoginRequest) {
      this.applySession(await loginApi(form))
    },
    async fetchCurrentUser() {
      if (!this.accessToken) return
      this.applySession(await meApi())
    },
    async refreshToken() {
      this.applySession(await refreshApi())
    },
    async logout() {
      try {
        await logoutApi()
      } finally {
        this.accessToken = ''
        this.userInfo = null
        this.roles = []
        this.permissions = []
        this.menus = []
        this.initialized = false
        clearToken()
      }
    },
    hasPermission(permission?: string) {
      return !permission || this.roles.includes('admin') || this.roles.includes('super_admin') || this.permissions.includes(permission)
    },
    hasRole(role?: string) {
      return !role || this.roles.includes(role)
    },
  },
})
