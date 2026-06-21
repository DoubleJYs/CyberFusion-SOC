import type { PermissionCode, RoleCode } from './system'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    public?: boolean
    requiresAuth?: boolean
    roles?: RoleCode[]
    permissions?: PermissionCode[]
    menuId?: number
  }
}
