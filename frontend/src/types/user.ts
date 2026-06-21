import type { MenuItem, PermissionCode, RoleCode } from './system'

export interface LoginRequest {
  username: string
  password: string
  captchaCode?: string
  captchaId?: string
  rememberMe?: boolean
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  email?: string
  mobile?: string
  status: number
}

export interface LoginResponse {
  accessToken: string
  expiresIn: number
  tokenType: string
  userInfo: UserInfo
  roles: RoleCode[]
  permissions: PermissionCode[]
  menus: MenuItem[]
}

export interface UserProfile extends UserInfo {
  roles: RoleCode[]
}
