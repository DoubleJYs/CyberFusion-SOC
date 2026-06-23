<template>
  <div class="sidebar-shell" :class="{ collapsed }">
    <div v-if="!collapsed" class="sidebar-quick">
      <span>{{ sidebarHint.title }}</span>
      <strong>{{ sidebarHint.description }}</strong>
    </div>
    <el-menu
      ref="menuRef"
      :key="collapsed ? 'collapsed' : 'expanded'"
      :default-active="$route.path"
      :default-openeds="defaultOpeneds"
      router
      :collapse="collapsed"
      unique-opened
      class="sidebar-menu"
      @scroll.passive="rememberScroll"
    >
      <template v-for="group in menuGroups" :key="group.key">
        <div v-if="!collapsed" class="menu-section-label">{{ group.label }}</div>
        <SidebarMenuNode v-for="item in group.items" :key="item.id" :item="item" />
      </template>
    </el-menu>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import SidebarMenuNode from './SidebarMenuNode.vue'
import { useAuthStore } from '@/stores/auth'
import type { MenuItem } from '@/types/system'
import { roleExperience } from '@/utils/roleExperience'

defineProps<{ collapsed: boolean }>()

const authStore = useAuthStore()
const route = useRoute()
const experience = computed(() => roleExperience(authStore.roles, authStore.userInfo))
const menuRef = ref()
const SIDEBAR_SCROLL_KEY = 'cyberfusion:admin-sidebar-scroll-top'
const fallbackMenus: MenuItem[] = [
  {
    id: 2000,
    parentId: 0,
    name: '安全运营',
    path: '/soc',
    type: 'directory',
    sort: 1,
    visible: 1,
    status: 1,
    icon: 'Monitor',
    children: [
      { id: 2001, parentId: 2000, name: '安全总览', path: '/soc/dashboard', type: 'menu', sort: 1, visible: 1, status: 1, icon: 'DataAnalysis' },
      { id: 2012, parentId: 2000, name: '平台能力说明', path: '/soc/capabilities', type: 'menu', sort: 2, visible: 1, status: 1, icon: 'Grid' },
    ],
  },
]
const showcaseMenu: MenuItem = {
  id: -10,
  parentId: 0,
  name: '安全运营演示台',
  path: '/showcase',
  type: 'menu',
  sort: 0,
  visible: 1,
  status: 1,
  icon: 'Promotion',
  children: [],
}
const clientMenu: MenuItem = {
  id: -20,
  parentId: 0,
  name: '我的电脑安全助手',
  path: '/client/workbench',
  type: 'menu',
  sort: 1,
  visible: 1,
  status: 1,
  icon: 'Monitor',
  children: [],
}
const clientTaskMenu: MenuItem = directMenu(-21, '我的待办', '/client/tasks', 'Tickets', 2)
const clientReportMenu: MenuItem = directMenu(-22, '提交日志', '/client/data-report', 'DocumentChecked', 3)
const clientToolMenu: MenuItem = directMenu(-23, '安全工具', '/client/local-range', 'Tools', 4)
const clientLogMenu: MenuItem = directMenu(-24, '安全日志', '/client/security-logs', 'Document', 5)

interface MenuGroup {
  key: string
  label: string
  items: MenuItem[]
}

const menuGroups = computed<MenuGroup[]>(() => {
  const source = (authStore.menus.length ? authStore.menus : fallbackMenus)
    .filter((item) => item.visible !== 0 && item.status !== 0 && item.type !== 'button')
    .map(cloneVisible)
    .filter((item): item is MenuItem => Boolean(item))

  const items = normalizeTaskMenus(source)
  return [{ key: experience.value.persona, label: experience.value.label, items: items.length ? items : source }]
})

const sidebarHint = computed(() => {
  if (experience.value.isSuperAdmin) {
    return { title: '全量专家视图', description: '全部菜单、诊断、策略、审计和系统管理保持可见。' }
  }
  if (experience.value.isSecurityEngineer) {
    return { title: '策略治理视图', description: '默认聚焦策略、适配、剧本、风险评分和事件关联。' }
  }
  if (experience.value.isAnalyst) {
    return { title: '运营精简视图', description: '默认聚焦事件、告警、工单、资产风险和报告。' }
  }
  if (experience.value.isEmployee) {
    return { title: '安全管家', description: '只保留我的电脑、我的待办、提交日志、安全工具和安全日志。' }
  }
  return { title: '客户演示模式', description: '默认只进入安全运营演示台，不展示复杂后台菜单。' }
})

const defaultOpeneds = computed(() => {
  const active = route.path
  for (const group of menuGroups.value) {
    for (const item of group.items) {
      const chain = openChainForPath(item, active)
      if (chain.length) return chain
    }
  }
  return []
})

onMounted(() => {
  void restoreSidebarPosition(true)
})

watch(() => route.path, () => {
  void restoreSidebarPosition(false)
})

function rememberScroll(event: Event) {
  const target = event.target as HTMLElement | null
  if (target) {
    window.sessionStorage.setItem(SIDEBAR_SCROLL_KEY, String(target.scrollTop))
  }
}

async function restoreSidebarPosition(initial: boolean) {
  await nextTick()
  const menu = menuElement()
  if (!menu) return
  const saved = Number(window.sessionStorage.getItem(SIDEBAR_SCROLL_KEY) || 0)
  if (saved > 0) {
    menu.scrollTop = saved
  }
  await nextTick()
  if (initial) {
    ensureActiveItemVisible(menu)
  }
}

function ensureActiveItemVisible(menu: HTMLElement) {
  const active = menu.querySelector('.el-menu-item.is-active') as HTMLElement | null
  if (!active) return
  const menuRect = menu.getBoundingClientRect()
  const activeRect = active.getBoundingClientRect()
  if (activeRect.top < menuRect.top || activeRect.bottom > menuRect.bottom) {
    active.scrollIntoView({ block: 'nearest' })
    window.sessionStorage.setItem(SIDEBAR_SCROLL_KEY, String(menu.scrollTop))
  }
}

function menuElement(): HTMLElement | null {
  const candidate = menuRef.value?.$el || menuRef.value
  return candidate instanceof HTMLElement ? candidate : null
}

function cloneVisible(item: MenuItem): MenuItem | null {
  if (item.visible === 0 || item.status === 0 || item.type === 'button') return null
  const children = (item.children || [])
    .map(cloneVisible)
    .filter((child): child is MenuItem => Boolean(child))
    .sort((left, right) => left.sort - right.sort)
  return { ...item, children }
}

function normalizeTaskMenus(source: MenuItem[]) {
  const findPath = (path: string) => findMenuByPath(source, path)
  const leaf = (path: string, name: string, icon?: string) => {
    const item = findPath(path)
    return item ? { ...item, name, icon: icon || item.icon } : null
  }
  const dir = (id: number, name: string, icon: string, children: Array<MenuItem | null>) => {
    const visibleChildren = children.filter((item): item is MenuItem => Boolean(item))
    return visibleChildren.length
      ? { id, parentId: 0, name, icon, type: 'directory', sort: Math.abs(id), visible: 1, status: 1, children: visibleChildren } as MenuItem
      : null
  }

  if (experience.value.isSuperAdmin) {
    return [
      dir(-100, '工作区', 'Odometer', [
        showcaseMenu,
        leaf('/soc/dashboard', '安全运营工作台', 'DataAnalysis'),
        leaf('/soc/capabilities', '平台能力说明', 'Grid'),
        leaf('/soc/demo-range', '安全验证中心', 'Operation'),
      ]),
      dir(-200, '安全运营', 'Monitor', [
        leaf('/soc/incidents', '安全事件簇', 'Share'),
        leaf('/soc/alerts', '告警中心', 'WarningFilled'),
        leaf('/soc/assets', '资产风险', 'Cpu'),
        leaf('/soc/client-security', '员工终端态势', 'Monitor'),
        leaf('/soc/vulnerabilities', '漏洞中心', 'Aim'),
        leaf('/soc/baselines', '基线核查', 'Checked'),
        leaf('/soc/fim', '文件完整性', 'Files'),
        leaf('/soc/external-events', '外部事件', 'Connection'),
      ]),
      dir(-300, '处置闭环', 'Tickets', [
        leaf('/soc/tickets', '工单中心', 'Tickets'),
        leaf('/soc/reports', '报表中心', 'DocumentChecked'),
        clientMenu,
        clientTaskMenu,
        clientReportMenu,
        clientToolMenu,
        clientLogMenu,
      ]),
      dir(-400, '策略治理', 'SetUp', [
        leaf('/soc/policies', '策略与规则中心', 'SetUp'),
        leaf('/soc/rules', '检测规则中心', 'List'),
        leaf('/soc/alert-noise', '告警降噪', 'Filter'),
        leaf('/soc/settings', '接入与诊断设置', 'Tools'),
      ]),
      dir(-500, '平台管理', 'Setting', [
        leaf('/dashboard', '平台仪表盘', 'DataLine'),
        dir(-510, '身份权限', 'UserFilled', [
          leaf('/system/user', '用户管理'),
          leaf('/system/role', '角色管理'),
          leaf('/system/menu', '菜单管理'),
        ]),
        dir(-520, '组织基础', 'OfficeBuilding', [
          leaf('/system/dept', '部门管理'),
          leaf('/system/post', '岗位管理'),
        ]),
        dir(-530, '配置与审计', 'DocumentChecked', [
          leaf('/system/dict', '字典管理'),
          leaf('/system/config', '参数配置'),
          leaf('/system/notice', '通知公告'),
          leaf('/system/log', '系统日志'),
          leaf('/system/file', '文件管理'),
          leaf('/system/excel/logs', '导入导出日志'),
          leaf('/system/workflow/biz-sequence', '编号规则'),
          leaf('/system/workflow/biz-flow-log', '流程日志'),
        ]),
      ]),
    ].filter((item): item is MenuItem => Boolean(item))
  }

  if (experience.value.isPlatformAdmin) {
    return [
      dir(-100, '工作区', 'Odometer', [showcaseMenu, leaf('/soc/dashboard', '安全运营工作台', 'DataAnalysis')]),
      dir(-200, '安全运营', 'Monitor', [
        leaf('/soc/incidents', '安全事件簇', 'Share'),
        leaf('/soc/alerts', '告警中心', 'WarningFilled'),
        leaf('/soc/assets', '资产风险', 'Cpu'),
        leaf('/soc/external-events', '证据中心', 'Connection'),
        leaf('/soc/tickets', '工单中心', 'Tickets'),
        leaf('/soc/reports', '报表中心', 'DocumentChecked'),
      ]),
      dir(-300, '策略治理', 'SetUp', [
        leaf('/soc/policies', '策略与规则中心', 'SetUp'),
        leaf('/soc/rules', '检测规则中心', 'List'),
        leaf('/soc/alert-noise', '告警降噪', 'Filter'),
      ]),
      dir(-400, '平台管理', 'Setting', [
        leaf('/dashboard', '平台仪表盘', 'DataLine'),
        leaf('/system/user', '用户管理'),
        leaf('/system/role', '角色管理'),
        leaf('/system/menu', '菜单管理'),
        leaf('/system/log', '系统日志'),
      ]),
    ].filter((item): item is MenuItem => Boolean(item))
  }

  if (experience.value.isSecurityEngineer) {
    return [
      dir(-100, '工作区', 'Odometer', [leaf('/soc/dashboard', '运营工作台', 'DataAnalysis')]),
      dir(-200, '策略治理', 'SetUp', [
        leaf('/soc/policies', '策略与规则中心', 'SetUp'),
        leaf('/soc/rules', '检测规则中心', 'List'),
        leaf('/soc/external-events', '事件适配', 'Connection'),
        leaf('/soc/alert-noise', '告警降噪', 'Filter'),
      ]),
      dir(-300, '风险与关联', 'Share', [
        leaf('/soc/incidents', '事件关联', 'Share'),
        leaf('/soc/assets', '资产风险评分', 'Cpu'),
        leaf('/soc/tickets', '剧本处置', 'Tickets'),
        leaf('/soc/reports', '治理报告', 'DocumentChecked'),
      ]),
    ].filter((item): item is MenuItem => Boolean(item))
  }

  if (experience.value.isAnalyst) {
    return [
      dir(-100, '运营工作台', 'Odometer', [leaf('/soc/dashboard', '运营工作台', 'DataAnalysis')]),
      dir(-200, '安全运营', 'Monitor', [
        leaf('/soc/incidents', '事件簇', 'Share'),
        leaf('/soc/alerts', '告警', 'WarningFilled'),
        leaf('/soc/assets', '资产风险', 'Cpu'),
        leaf('/soc/tickets', '工单', 'Tickets'),
        leaf('/soc/reports', '报告', 'DocumentChecked'),
      ]),
    ].filter((item): item is MenuItem => Boolean(item))
  }

  if (experience.value.isEmployee) {
    return [
      dir(-100, '安全管家', 'Monitor', [clientMenu, clientTaskMenu, clientReportMenu, clientToolMenu, clientLogMenu]),
    ].filter((item): item is MenuItem => Boolean(item))
  }

  return [showcaseMenu]
}

function directMenu(id: number, name: string, path: string, icon: string, sort: number): MenuItem {
  return {
    id,
    parentId: 0,
    name,
    path,
    type: 'menu',
    sort,
    visible: 1,
    status: 1,
    icon,
    children: [],
  }
}

function findMenuByPath(items: MenuItem[], path: string): MenuItem | null {
  for (const item of items) {
    if (item.path === path) return item
    const child = findMenuByPath(item.children || [], path)
    if (child) return child
  }
  return null
}

function openChainForPath(item: MenuItem, activePath: string): string[] {
  const ownIndex = menuIndex(item)
  if (item.path === activePath) return []
  for (const child of item.children || []) {
    if (child.path === activePath) return [ownIndex]
    const childChain = openChainForPath(child, activePath)
    if (childChain.length) return [ownIndex, ...childChain]
  }
  return []
}

function menuIndex(item: MenuItem) {
  return item.path || String(item.id)
}
</script>

<style scoped>
.sidebar-shell {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 84px);
  min-height: 0;
}

.sidebar-quick {
  display: grid;
  gap: 4px;
  margin: 10px 10px 0;
  padding: 10px;
  border: 1px solid rgba(212, 147, 74, 0.24);
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(255, 248, 238, 0.92), rgba(239, 249, 250, 0.78));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

.sidebar-quick span {
  color: var(--soc-warm-strong);
  font-size: 11px;
  font-weight: 760;
}

.sidebar-quick strong {
  color: var(--soc-text-muted);
  font-size: 11px;
  font-weight: 650;
  line-height: 1.45;
}

.sidebar-menu {
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #475467;
  --el-menu-active-color: #c76f2e;
  --el-menu-hover-bg-color: rgba(255, 246, 232, 0.76);
  flex: 1 1 auto;
  min-height: 0;
  padding: 10px;
  border-right: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.menu-section-label {
  margin: 12px 8px 6px;
  color: var(--soc-text-subtle);
  font-size: 11px;
  font-weight: 760;
}

.sidebar-menu :deep(.el-sub-menu__title),
.sidebar-menu :deep(.el-menu-item) {
  height: 40px;
  margin: 2px 0;
  border-radius: 8px;
  color: #475467;
  font-size: 13px;
  font-weight: 650;
}

.sidebar-menu :deep(.el-sub-menu.is-opened > .el-sub-menu__title) {
  color: var(--soc-text);
  background: rgba(255, 255, 255, 0.54);
}

.sidebar-menu :deep(.el-sub-menu .el-menu-item) {
  height: 34px;
  margin-left: 2px;
  font-size: 12px;
  font-weight: 600;
}

.sidebar-menu :deep(.el-sub-menu__title:hover),
.sidebar-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 246, 232, 0.82);
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, rgba(255, 244, 224, 0.94), rgba(255, 255, 255, 0.78));
  box-shadow: inset 3px 0 0 var(--soc-warm), 0 8px 18px rgba(212, 147, 74, 0.1);
  color: var(--soc-warm-strong);
}

.sidebar-menu :deep(.el-icon) {
  color: currentColor;
}

.sidebar-menu :deep(.el-menu--inline) {
  background: transparent;
}

@media (max-width: 760px) {
  .sidebar-shell {
    height: calc(100vh - 68px);
  }

  .sidebar-quick,
  .menu-section-label {
    display: none;
  }

  .sidebar-menu {
    padding: 8px;
  }

  .sidebar-menu :deep(.el-sub-menu__title),
  .sidebar-menu :deep(.el-menu-item) {
    justify-content: center;
    width: 40px;
    padding: 0 !important;
  }

  .sidebar-menu :deep(.el-sub-menu__title span),
  .sidebar-menu :deep(.el-menu-item span),
  .sidebar-menu :deep(.el-sub-menu__icon-arrow) {
    display: none;
  }

  .sidebar-menu :deep(.el-icon) {
    margin: 0;
  }

  .sidebar-menu :deep(.el-menu--inline) {
    padding-left: 0;
  }
}
</style>
