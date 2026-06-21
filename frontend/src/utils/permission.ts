import { useAuthStore } from '@/stores/auth'
import type { PermissionCode, RoleCode } from '@/types/system'

export type PermissionValue = PermissionCode | PermissionCode[]
export type RoleValue = RoleCode | RoleCode[]

export function matchPermission(value: PermissionValue | undefined, permissions: PermissionCode[], roles: RoleCode[]) {
  if (!value) return true
  if (roles.includes('admin')) return true
  const required = Array.isArray(value) ? value : [value]
  return required.some((permission) => permissions.includes(permission))
}

export function matchRole(value: RoleValue | undefined, roles: RoleCode[]) {
  if (!value) return true
  const required = Array.isArray(value) ? value : [value]
  return required.some((role) => roles.includes(role))
}

export function usePermissionAccess() {
  const authStore = useAuthStore()
  return {
    hasPermission: (value?: PermissionValue) => matchPermission(value, authStore.permissions, authStore.roles),
    hasRole: (value?: RoleValue) => matchRole(value, authStore.roles),
  }
}
