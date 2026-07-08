import type { Component } from 'vue'
import type { Router, RouteRecordName } from 'vue-router'
import type { AppRouteRecord, MenuItem } from '@/types/system'
import type { UserInfo } from '@/types/user'
import { defaultRouteForExperience } from '@/utils/roleExperience'

type ComponentLoader = () => Promise<Component>
type DynamicRouteName = NonNullable<RouteRecordName>

const ADMIN_ROOT_ROUTE_NAME = 'adminRoot'

const componentRegistry: Record<string, ComponentLoader> = {
  'dashboard/DashboardView': () => import('@/views/dashboard/DashboardView.vue'),
  'soc/DashboardView': () => import('@/views/soc/DashboardView.vue'),
  'soc/DailyRecommendationView': () => import('@/views/soc/DailyRecommendationView.vue'),
  'soc/CapabilityView': () => import('@/views/soc/CapabilityView.vue'),
  'soc/DemoRangeView': () => import('@/views/soc/DemoRangeView.vue'),
  'soc/AlertCenterView': () => import('@/views/soc/AlertCenterView.vue'),
  'soc/IncidentClusterView': () => import('@/views/soc/IncidentClusterView.vue'),
  'soc/RuleCenterView': () => import('@/views/soc/RuleCenterView.vue'),
  'soc/PolicyCenterView': () => import('@/views/soc/PolicyCenterView.vue'),
  'soc/AlertNoiseView': () => import('@/views/soc/AlertNoiseView.vue'),
  'soc/AssetView': () => import('@/views/soc/AssetView.vue'),
  'soc/ClientSecurityPostureView': () => import('@/views/soc/ClientSecurityPostureView.vue'),
  'soc/VulnerabilityView': () => import('@/views/soc/VulnerabilityView.vue'),
  'soc/BaselineView': () => import('@/views/soc/BaselineView.vue'),
  'soc/FileIntegrityView': () => import('@/views/soc/FileIntegrityView.vue'),
  'soc/ExternalEventView': () => import('@/views/soc/ExternalEventView.vue'),
  'soc/HostAgentView': () => import('@/views/soc/HostAgentView.vue'),
  'soc/TicketView': () => import('@/views/soc/TicketView.vue'),
  'soc/ReportView': () => import('@/views/soc/ReportView.vue'),
  'soc/SettingsView': () => import('@/views/soc/SettingsView.vue'),
  'system/user/UserView': () => import('@/views/system/user/UserView.vue'),
  'system/org/DeptView': () => import('@/views/system/org/DeptView.vue'),
  'system/org/PostView': () => import('@/views/system/org/PostView.vue'),
  'system/role/RoleView': () => import('@/views/system/role/RoleView.vue'),
  'system/menu/MenuView': () => import('@/views/system/menu/MenuView.vue'),
  'system/dict/DictView': () => import('@/views/system/dict/DictView.vue'),
  'system/log/LogView': () => import('@/views/system/log/LogView.vue'),
  'system/file/FileView': () => import('@/views/system/file/FileView.vue'),
  'system/excel/ImportExportLogView': () => import('@/views/system/excel/ImportExportLogView.vue'),
  'system/workflow/BizSequenceView': () => import('@/views/system/workflow/BizSequenceView.vue'),
  'system/workflow/BizFlowLogView': () => import('@/views/system/workflow/BizFlowLogView.vue'),
  'system/notice/NoticeView': () => import('@/views/system/notice/NoticeView.vue'),
  'system/config/ConfigView': () => import('@/views/system/config/ConfigView.vue'),
}

export const fallbackProtectedRoutes: AppRouteRecord[] = [
  { path: 'soc/dashboard', name: 'socDashboard', component: componentRegistry['soc/DashboardView'], meta: { title: '安全运营工作台', requiresAuth: true, permissions: ['soc:dashboard:view'] } },
  { path: 'soc/capabilities', name: 'socCapabilities', component: componentRegistry['soc/CapabilityView'], meta: { title: '平台能力说明', requiresAuth: true, permissions: ['soc:dashboard:view'] } },
  { path: 'soc/capabilities/:capabilityKey', name: 'socCapabilityDetail', component: componentRegistry['soc/CapabilityView'], meta: { title: '能力详情', requiresAuth: true, permissions: ['soc:dashboard:view'] } },
  { path: 'soc/demo-range', name: 'socDemoRange', component: componentRegistry['soc/DemoRangeView'], meta: { title: '安全验证', requiresAuth: true, permissions: ['soc:demo-range:view'] } },
  { path: 'soc/demo-range/runs/:runId', name: 'socDemoRangeRun', component: componentRegistry['soc/DemoRangeView'], meta: { title: '安全验证工作流', requiresAuth: true, permissions: ['soc:demo-range:view'] } },
  { path: 'soc/daily-recommendations', name: 'socDailyRecommendations', component: componentRegistry['soc/DailyRecommendationView'], meta: { title: '每日处理', requiresAuth: true, permissions: ['soc:recommendation:view', 'soc:dashboard:view'] } },
  { path: 'soc/alerts', name: 'socAlerts', component: componentRegistry['soc/AlertCenterView'], meta: { title: '告警处置', requiresAuth: true, permissions: ['soc:alert:view'] } },
  { path: 'soc/incidents', name: 'socIncidents', component: componentRegistry['soc/IncidentClusterView'], meta: { title: '安全事件簇', requiresAuth: true, permissions: ['soc:incident:list'] } },
  { path: 'soc/rules', name: 'socRules', component: componentRegistry['soc/RuleCenterView'], meta: { title: '检测规则中心', requiresAuth: true, permissions: ['soc:rules:view', 'soc:external-event:view'] } },
  { path: 'soc/policies', name: 'socPolicies', component: componentRegistry['soc/PolicyCenterView'], meta: { title: '策略与规则中心', requiresAuth: true, permissions: ['soc:policy:list'] } },
  { path: 'soc/alert-noise', name: 'socAlertNoise', component: componentRegistry['soc/AlertNoiseView'], meta: { title: '告警降噪', requiresAuth: true, permissions: ['soc:alert-noise:view'] } },
  { path: 'soc/assets', name: 'socAssets', component: componentRegistry['soc/AssetView'], meta: { title: '资产视图', requiresAuth: true, permissions: ['soc:asset:view'] } },
  { path: 'soc/client-security', name: 'socClientSecurity', component: componentRegistry['soc/ClientSecurityPostureView'], meta: { title: '员工终端安全态势', requiresAuth: true, permissions: ['soc:client-security:view', 'soc:asset:view'] } },
  { path: 'soc/vulnerabilities', name: 'socVulnerabilities', component: componentRegistry['soc/VulnerabilityView'], meta: { title: '漏洞中心', requiresAuth: true, permissions: ['soc:vulnerability:view'] } },
  { path: 'soc/baselines', name: 'socBaselines', component: componentRegistry['soc/BaselineView'], meta: { title: '基线核查', requiresAuth: true, permissions: ['soc:baseline:view'] } },
  { path: 'soc/fim', name: 'socFim', component: componentRegistry['soc/FileIntegrityView'], meta: { title: '文件完整性', requiresAuth: true, permissions: ['soc:fim:view'] } },
  { path: 'soc/external-events', name: 'socExternalEvents', component: componentRegistry['soc/ExternalEventView'], meta: { title: '证据中心', requiresAuth: true, permissions: ['soc:external-event:view'] } },
  { path: 'soc/agents', name: 'socHostAgents', component: componentRegistry['soc/HostAgentView'], meta: { title: 'Agent 管理', requiresAuth: true, permissions: ['soc:agent:view'] } },
  { path: 'soc/tickets', name: 'socTickets', component: componentRegistry['soc/TicketView'], meta: { title: '工单中心', requiresAuth: true, permissions: ['soc:ticket:view'] } },
  { path: 'soc/reports', name: 'socReports', component: componentRegistry['soc/ReportView'], meta: { title: '报告中心', requiresAuth: true, permissions: ['soc:report:view'] } },
  { path: 'soc/settings', name: 'socSettings', component: componentRegistry['soc/SettingsView'], meta: { title: '系统配置', requiresAuth: true, permissions: ['soc:settings:view'] } },
  { path: 'dashboard', name: 'dashboard', component: componentRegistry['dashboard/DashboardView'], meta: { title: '仪表盘', requiresAuth: true, permissions: ['dashboard:view'] } },
  { path: 'system/user', name: 'user', component: componentRegistry['system/user/UserView'], meta: { title: '用户管理', requiresAuth: true, permissions: ['system:user:view'] } },
  { path: 'system/dept', name: 'dept', component: componentRegistry['system/org/DeptView'], meta: { title: '部门管理', requiresAuth: true, permissions: ['system:dept:view'] } },
  { path: 'system/post', name: 'post', component: componentRegistry['system/org/PostView'], meta: { title: '岗位管理', requiresAuth: true, permissions: ['system:post:view'] } },
  { path: 'system/role', name: 'role', component: componentRegistry['system/role/RoleView'], meta: { title: '角色管理', requiresAuth: true, permissions: ['system:role:view'] } },
  { path: 'system/menu', name: 'menu', component: componentRegistry['system/menu/MenuView'], meta: { title: '菜单管理', requiresAuth: true, permissions: ['system:menu:view'] } },
  { path: 'system/dict', name: 'dict', component: componentRegistry['system/dict/DictView'], meta: { title: '字典管理', requiresAuth: true, permissions: ['system:dict:view'] } },
  { path: 'system/log', name: 'log', component: componentRegistry['system/log/LogView'], meta: { title: '系统日志', requiresAuth: true, permissions: ['system:log:view'] } },
  { path: 'system/file', name: 'file', component: componentRegistry['system/file/FileView'], meta: { title: '文件管理', requiresAuth: true, permissions: ['system:file:list'] } },
  { path: 'system/excel/logs', name: 'excelLogs', component: componentRegistry['system/excel/ImportExportLogView'], meta: { title: '导入导出日志', requiresAuth: true, permissions: ['system:excel:log'] } },
  { path: 'system/workflow/biz-sequence', name: 'bizSequence', component: componentRegistry['system/workflow/BizSequenceView'], meta: { title: '编号规则', requiresAuth: true, permissions: ['system:sequence:list'] } },
  { path: 'system/workflow/biz-flow-log', name: 'bizFlowLog', component: componentRegistry['system/workflow/BizFlowLogView'], meta: { title: '流程日志', requiresAuth: true, permissions: ['system:flowlog:list'] } },
  { path: 'system/notice', name: 'notice', component: componentRegistry['system/notice/NoticeView'], meta: { title: '通知公告', requiresAuth: true, permissions: ['system:notice:view'] } },
  { path: 'system/config', name: 'config', component: componentRegistry['system/config/ConfigView'], meta: { title: '参数配置', requiresAuth: true, permissions: ['system:config:view'] } },
]

const fallbackRouteNamesByPath = fallbackProtectedRoutes.reduce<Record<string, DynamicRouteName>>((map, route) => {
  if (route.name) map[normalizeRoutePath(route.path)] = route.name
  return map
}, {})

const dynamicRouteNames = new Set<DynamicRouteName>()

export function buildRoutesFromMenus(menus: MenuItem[]): AppRouteRecord[] {
  const routes = flattenMenus(menus)
    .filter((menu) => menu.visible !== 0 && menu.status !== 0 && menu.type === 'menu' && menu.path && menu.component)
    .map(menuToRoute)
    .filter((route): route is AppRouteRecord => Boolean(route))

  return routes.length ? routes : fallbackProtectedRoutes
}

export function ensureMenuRoutes(router: Router, menus: MenuItem[]) {
  let added = false
  buildRoutesFromMenus(menus).forEach((route) => {
    const routeName = route.name
    if (!routeName || router.hasRoute(routeName)) return
    router.addRoute(ADMIN_ROOT_ROUTE_NAME, route)
    dynamicRouteNames.add(routeName)
    added = true
  })
  return added
}

export function resetMenuRoutes(router: Router) {
  dynamicRouteNames.forEach((routeName) => {
    if (router.hasRoute(routeName)) router.removeRoute(routeName)
  })
  dynamicRouteNames.clear()
}

export function firstRoutePathFromMenus(menus: MenuItem[], roles: string[] = [], userInfo?: UserInfo | null) {
  return defaultRouteForExperience(roles, userInfo, menus)
}

function flattenMenus(menus: MenuItem[]): MenuItem[] {
  return menus
    .slice()
    .sort((left, right) => left.sort - right.sort)
    .flatMap((menu) => [menu, ...flattenMenus(menu.children || [])])
}

function menuToRoute(menu: MenuItem): AppRouteRecord | null {
  const loader = menu.component ? componentRegistry[menu.component] : undefined
  if (!loader || !menu.path) return null
  return {
    path: normalizeRoutePath(menu.path),
    name: routeName(menu),
    component: loader,
    meta: {
      title: menu.name,
      requiresAuth: true,
      permissions: menu.permission ? [menu.permission] : [],
      menuId: menu.id,
    },
  }
}

function routeName(menu: MenuItem) {
  const path = normalizeRoutePath(menu.path)
  return fallbackRouteNamesByPath[path] || path.replace(/[^A-Za-z0-9]+(.)/g, (_match, chr: string) => chr.toUpperCase()) || `menu-${menu.id}`
}

function normalizeRoutePath(path?: string) {
  return path?.replace(/^\/+/, '').replace(/\/+$/, '') || ''
}
