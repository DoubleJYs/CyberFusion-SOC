import { defineStore } from 'pinia'
import { loginApi, logoutApi, meApi, refreshApi } from '@/api/auth'
import { clearToken, getToken, setToken } from '@/utils/storage'
import type { MenuItem } from '@/types/system'
import type { LoginRequest, LoginResponse, UserInfo } from '@/types/user'

interface AuthState {
  accessToken: string
  userInfo: UserInfo | null
  roles: string[]
  permissions: string[]
  menus: MenuItem[]
  initialized: boolean
}

export const LOCAL_DEMO_TOKEN = 'local-demo-admin-token'

const localDemoSession: LoginResponse = {
  accessToken: LOCAL_DEMO_TOKEN,
  expiresIn: 7200,
  tokenType: 'Bearer',
  userInfo: {
    id: 1,
    username: 'admin',
    nickname: '本地演示管理员',
    status: 1,
  },
  roles: ['admin'],
  permissions: ['*'],
  menus: [],
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
      try {
        this.applySession(await loginApi(form))
      } catch (error) {
        if (canUseLocalDemoSession(form, error)) {
          this.applySession(localDemoSession)
          return
        }
        throw error
      }
    },
    async fetchCurrentUser() {
      if (!this.accessToken) return
      if (this.accessToken === LOCAL_DEMO_TOKEN) {
        try {
          this.applySession(await loginApi({
            username: 'admin',
            password: 'Admin@123456',
            rememberMe: true,
          }))
        } catch {
          this.applySession(localDemoSession)
        }
        return
      }
      this.applySession(await meApi())
    },
    async refreshToken() {
      this.applySession(await refreshApi())
    },
    async logout() {
      try {
        if (this.accessToken !== LOCAL_DEMO_TOKEN) {
          await logoutApi()
        }
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

function canUseLocalDemoSession(form: LoginRequest, error: unknown) {
  if (form.username !== 'admin' || form.password !== 'Admin@123456') return false
  const candidate = error as { response?: { status?: number }; code?: string; message?: string }
  if (!candidate.response) return true
  const status = candidate.response.status || 0
  return status >= 500
}
