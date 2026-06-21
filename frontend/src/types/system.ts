import type { RouteRecordRaw } from 'vue-router'

export type RoleCode = string
export type PermissionCode = string
export type MenuType = 'directory' | 'menu' | 'button'

export interface MenuItem {
  id: number
  parentId: number
  name: string
  path?: string
  component?: string
  icon?: string
  type: MenuType
  permission?: PermissionCode
  sort: number
  visible: number
  status: number
  children?: MenuItem[]
}

export interface AppRouteMeta {
  title: string
  public?: boolean
  requiresAuth?: boolean
  roles?: RoleCode[]
  permissions?: PermissionCode[]
  menuId?: number
}

export type AppRouteRecord = RouteRecordRaw & {
  meta?: AppRouteMeta
  children?: AppRouteRecord[]
}

export interface TableRecord {
  id: number
  name: string
  code: string
  status: string
  owner: string
  updatedAt: string
}

export interface UserRecord {
  id: number
  username: string
  nickname: string
  email?: string
  mobile?: string
  deptId?: number
  deptName?: string
  postId?: number
  postName?: string
  status: number
  roleIds: number[]
  roles: string[]
  createdAt: string
}

export interface DeptRecord {
  id: number
  parentId: number
  deptName: string
  deptCode: string
  leader?: string
  phone?: string
  sort: number
  status: number
  createdAt: string
  children?: DeptRecord[]
}

export interface PostRecord {
  id: number
  postCode: string
  postName: string
  sort: number
  status: number
  remark?: string
  createdAt: string
}

export interface RoleRecord {
  id: number
  roleCode: string
  roleName: string
  dataScope: 'self' | 'dept' | 'dept_tree' | 'all' | 'custom' | string
  status: number
  menuIds: number[]
  deptIds: number[]
  createdAt: string
}

export interface DictTypeRecord {
  id: number
  dictName: string
  dictCode: string
  status: number
  createdAt: string
}

export interface DictDataRecord {
  id: number
  dictTypeId: number
  dictLabel: string
  dictValue: string
  sortOrder: number
  status: number
  createdAt: string
}

export interface ConfigRecord {
  id: number
  configKey: string
  configName: string
  configValue: string
  valueType: 'string' | 'number' | 'boolean' | 'json' | string
  groupCode: string
  editable: number
  status: number
  remark?: string
  createdAt: string
  updatedAt: string
}

export interface NoticeRecord {
  id: number
  noticeTitle: string
  noticeType: string
  noticeContent: string
  pinned: number
  publishAt: string
  expireAt?: string
  status: number
  remark?: string
  createdAt: string
  updatedAt: string
}

export interface LoginLogRecord {
  id: number
  username: string
  ip: string
  userAgent: string
  status: string
  message: string
  createdAt: string
}

export interface OperationLogRecord {
  id: number
  username: string
  action: string
  method: string
  path: string
  ip: string
  userAgent: string
  status: string
  message: string
  createdAt: string
}
