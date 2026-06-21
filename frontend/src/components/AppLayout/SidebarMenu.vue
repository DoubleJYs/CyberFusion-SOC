<template>
  <div class="sidebar-shell" :class="{ collapsed }">
    <div v-if="!collapsed" class="sidebar-quick">
      <span>客户演示模式</span>
      <strong>从安全运营演示台开始，再进入专家后台查看细节</strong>
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

defineProps<{ collapsed: boolean }>()

const authStore = useAuthStore()
const route = useRoute()
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
  const groups: MenuGroup[] = [
    { key: 'showcase', label: '客户演示', items: [showcaseMenu] },
    ...(items.length ? [{ key: 'expert', label: 'SOC 专家模式', items }] : []),
    { key: 'client', label: '员工端', items: [clientMenu] },
  ]

  return groups.length ? groups : [{ key: 'all', label: '导航', items: source }]
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
  const isAdmin = authStore.roles.includes('admin')
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

  return [
    dir(-100, '安全总览', 'Odometer', [
      leaf('/soc/dashboard', '安全总览', 'DataAnalysis'),
    ]),
    dir(-200, '安全验证', 'Operation', [
      leaf('/soc/demo-range', '安全验证', 'Operation'),
      leaf('/soc/capabilities', '平台能力说明', 'Grid'),
    ]),
    dir(-300, '告警处置', 'WarningFilled', [
      leaf('/soc/alerts', '告警处置', 'WarningFilled'),
      leaf('/soc/alert-noise', '降噪规则', 'Filter'),
    ]),
    dir(-350, '证据中心', 'Connection', [
      leaf('/soc/external-events', '证据中心', 'Connection'),
    ]),
    dir(-400, '资产风险', 'Cpu', [
      leaf('/soc/assets', '资产风险', 'Cpu'),
      leaf('/soc/vulnerabilities', '漏洞风险', 'Aim'),
      leaf('/soc/baselines', '配置检查', 'Checked'),
      leaf('/soc/fim', '文件变更', 'Files'),
    ]),
    dir(-450, '检测规则', 'List', [
      leaf('/soc/rules', '检测规则', 'List'),
      leaf('/soc/policies', '策略与规则', 'SetUp'),
    ]),
    dir(-500, '工单中心', 'Tickets', [
      leaf('/soc/tickets', '工单中心', 'Tickets'),
    ]),
    dir(-550, '报告中心', 'DocumentChecked', [
      leaf('/soc/reports', '报告中心', 'DocumentChecked'),
    ]),
    isAdmin ? dir(-600, '系统管理', 'Setting', [
      leaf('/soc/settings', '数据源与通知', 'Tools'),
      dir(-610, '身份权限', 'UserFilled', [
        leaf('/system/user', '用户账号'),
        leaf('/system/role', '角色权限'),
      ]),
      dir(-620, '组织基础', 'OfficeBuilding', [
        leaf('/system/dept', '部门'),
        leaf('/system/post', '岗位'),
      ]),
      dir(-630, '平台配置', 'Tools', [
        leaf('/system/menu', '菜单配置'),
        leaf('/system/dict', '字典配置'),
        leaf('/system/config', '参数配置'),
        leaf('/system/notice', '通知公告'),
      ]),
      dir(-640, '审计与文件', 'DocumentChecked', [
        leaf('/dashboard', '平台仪表盘'),
        leaf('/system/log', '审计日志'),
        leaf('/system/file', '文件管理'),
        leaf('/system/excel/logs', '导入导出记录'),
        leaf('/system/workflow/biz-sequence', '编号规则'),
        leaf('/system/workflow/biz-flow-log', '流程日志'),
      ]),
    ]) : leaf('/soc/settings', '系统设置', 'Tools'),
  ].filter((item): item is MenuItem => Boolean(item))
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
