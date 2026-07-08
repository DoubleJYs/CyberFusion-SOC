import type { MenuItem } from '@/types/system'
import type { UserInfo } from '@/types/user'

export type ViewMode = 'simple' | 'detail' | 'expert'
export const EXPERT_HOME_PATH = '/soc/dashboard'

export interface RoleExperience {
  persona: 'super_admin' | 'admin' | 'security_engineer' | 'analyst' | 'employee' | 'customer'
  label: string
  viewMode: ViewMode
  showAdvanced: boolean
  showDiagnostics: boolean
  showRawEvidence: boolean
  isSuperAdmin: boolean
  isPlatformAdmin: boolean
  isSecurityEngineer: boolean
  isAnalyst: boolean
  isEmployee: boolean
  isCustomer: boolean
}

const SUPER_ADMIN_ROLES = new Set(['super_admin', 'admin'])
const PLATFORM_ADMIN_ROLES = new Set(['platform_admin'])
const SECURITY_ENGINEER_ROLES = new Set(['security_engineer', 'security_admin'])
const ANALYST_ROLES = new Set(['analyst', 'security_analyst', 'auditor'])
const EMPLOYEE_ROLES = new Set(['employee', 'ops', 'user'])
const CUSTOMER_ROLES = new Set(['customer', 'demo'])

export function roleExperience(roles: string[] = [], userInfo?: UserInfo | null): RoleExperience {
  const roleSet = new Set(roles)
  const username = userInfo?.username || ''
  const isSuperAdmin = hasAny(roleSet, SUPER_ADMIN_ROLES)
  const isPlatformAdmin = isSuperAdmin || hasAny(roleSet, PLATFORM_ADMIN_ROLES)
  const isSecurityEngineer = hasAny(roleSet, SECURITY_ENGINEER_ROLES)
  const isAnalyst = hasAny(roleSet, ANALYST_ROLES)
  const isEmployee = hasAny(roleSet, EMPLOYEE_ROLES)
  const isCustomer = hasAny(roleSet, CUSTOMER_ROLES) || username === 'demo'

  if (isSuperAdmin) {
    return profile('super_admin', '全量专家视图', 'expert', true, true, true, {
      isSuperAdmin,
      isPlatformAdmin,
      isSecurityEngineer,
      isAnalyst,
      isEmployee,
      isCustomer,
    })
  }
  if (isPlatformAdmin) {
    return profile('admin', '管理详细视图', 'detail', true, false, false, {
      isSuperAdmin,
      isPlatformAdmin,
      isSecurityEngineer,
      isAnalyst,
      isEmployee,
      isCustomer,
    })
  }
  if (isSecurityEngineer) {
    return profile('security_engineer', '治理策略视图', 'detail', true, false, false, {
      isSuperAdmin,
      isPlatformAdmin,
      isSecurityEngineer,
      isAnalyst,
      isEmployee,
      isCustomer,
    })
  }
  if (isAnalyst) {
    return profile('analyst', '运营精简视图', 'detail', false, false, false, {
      isSuperAdmin,
      isPlatformAdmin,
      isSecurityEngineer,
      isAnalyst,
      isEmployee,
      isCustomer,
    })
  }
  if (isCustomer) {
    return profile('customer', '客户演示视图', 'simple', false, false, false, {
      isSuperAdmin,
      isPlatformAdmin,
      isSecurityEngineer,
      isAnalyst,
      isEmployee,
      isCustomer,
    })
  }
  return profile('employee', '安全管家视图', 'simple', false, false, false, {
    isSuperAdmin,
    isPlatformAdmin,
    isSecurityEngineer,
    isAnalyst,
    isEmployee,
    isCustomer,
  })
}

export function defaultRouteForExperience(roles: string[] = [], userInfo?: UserInfo | null, menus: MenuItem[] = []) {
  const experience = roleExperience(roles, userInfo)
  if (experience.isSuperAdmin || experience.isPlatformAdmin) return firstAllowed(menus, [EXPERT_HOME_PATH]) || EXPERT_HOME_PATH
  if (experience.isSecurityEngineer) return firstAllowed(menus, ['/soc/policies', '/soc/rules', '/soc/dashboard']) || '/showcase'
  if (experience.isAnalyst) return firstAllowed(menus, ['/soc/dashboard', '/soc/incidents', '/soc/alerts', '/soc/tickets']) || '/showcase'
  if (experience.isEmployee) return '/client/workbench'
  return '/showcase'
}

export function firstAllowed(menus: MenuItem[], candidates: string[]) {
  const paths = new Set(flattenMenuPaths(menus))
  return candidates.find((path) => paths.has(path))
}

export function flattenMenuPaths(menus: MenuItem[]): string[] {
  return menus.flatMap((item) => [
    ...(item.path ? [item.path] : []),
    ...flattenMenuPaths(item.children || []),
  ])
}

function hasAny(roleSet: Set<string>, candidates: Set<string>) {
  for (const role of candidates) {
    if (roleSet.has(role)) return true
  }
  return false
}

function profile(
  persona: RoleExperience['persona'],
  label: string,
  viewMode: ViewMode,
  showAdvanced: boolean,
  showDiagnostics: boolean,
  showRawEvidence: boolean,
  flags: Pick<RoleExperience, 'isSuperAdmin' | 'isPlatformAdmin' | 'isSecurityEngineer' | 'isAnalyst' | 'isEmployee' | 'isCustomer'>,
): RoleExperience {
  return {
    persona,
    label,
    viewMode,
    showAdvanced,
    showDiagnostics,
    showRawEvidence,
    ...flags,
  }
}
