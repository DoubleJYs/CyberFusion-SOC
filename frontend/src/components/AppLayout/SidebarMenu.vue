<template>
  <div class="sidebar-shell" :class="{ collapsed }">
    <div v-if="!collapsed" class="sidebar-quick">
      <span>{{ sidebarHint.title }}</span>
      <strong>{{ sidebarHint.description }}</strong>
    </div>
    <div v-if="showDemoDataActions" class="sidebar-demo-actions">
      <div class="demo-data-status" :class="{ active: demoStatus?.hasDemoData }">
        <span>{{ demoStatusText }}</span>
      </div>
      <el-button
        size="small"
        type="primary"
        :loading="demoImporting"
        :disabled="demoClearing || !canImportDemoData"
        @click="handleImportDemoData"
      >
        导入演示数据
      </el-button>
      <el-button
        size="small"
        plain
        type="danger"
        :loading="demoClearing"
        :disabled="demoImporting || !canClearDemoData"
        @click="handleClearDemoData"
      >
        清除演示数据
      </el-button>
    </div>
    <el-menu
      ref="menuRef"
      :key="`${collapsed ? 'collapsed' : 'expanded'}-${isSystemManagementArea ? 'system' : 'work'}`"
      :default-active="$route.path"
      :default-openeds="defaultOpeneds"
      router
      :collapse="collapsed"
      :unique-opened="!experience.isSuperAdmin"
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
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import SidebarMenuNode from './SidebarMenuNode.vue'
import { clearDemoData, demoDataStatus, importDemoData, type DemoDataStatus } from '@/api/soc'
import { useAuthStore } from '@/stores/auth'
import type { MenuItem } from '@/types/system'
import { roleExperience } from '@/utils/roleExperience'

const props = defineProps<{ collapsed: boolean }>()

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const collapsed = computed(() => props.collapsed)
const experience = computed(() => roleExperience(authStore.roles, authStore.userInfo))
const menuRef = ref()
const demoImporting = ref(false)
const demoClearing = ref(false)
const demoStatus = ref<DemoDataStatus>()
const SIDEBAR_SCROLL_KEY = 'cyberfusion:admin-sidebar-scroll-top'
const isSystemManagementArea = computed(() => route.path === '/dashboard' || route.path.startsWith('/system'))
const canImportDemoData = computed(() => authStore.hasPermission('soc:demo-range:import'))
const canClearDemoData = computed(() => authStore.hasPermission('soc:demo-range:clear'))
const showDemoDataActions = computed(() => {
  if (isSystemManagementArea.value) return false
  return !collapsed.value && (canImportDemoData.value || canClearDemoData.value)
})
const demoStatusText = computed(() => {
  if (!demoStatus.value) return '演示数据状态待同步'
  if (demoStatus.value.hasDemoData) return `演示数据 ${demoStatus.value.totalDemoRows} 条`
  return '当前无演示数据'
})
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
      { id: 2001, parentId: 2000, name: '安全运营工作台', path: '/soc/dashboard', type: 'menu', sort: 1, visible: 1, status: 1, icon: 'DataAnalysis' },
      { id: 2012, parentId: 2000, name: '平台能力说明', path: '/soc/capabilities', type: 'menu', sort: 2, visible: 1, status: 1, icon: 'Grid' },
      { id: 2019, parentId: 2000, name: '每日处理', path: '/soc/daily-recommendations', type: 'menu', sort: 3, visible: 1, status: 1, icon: 'Calendar' },
    ],
  },
  { id: 2018, parentId: 0, name: 'Agent 管理', path: '/soc/agents', type: 'menu', sort: 6, visible: 1, status: 1, icon: 'Connection' },
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

  if (isSystemManagementArea.value && experience.value.isPlatformAdmin) {
    return [{ key: `${experience.value.persona}:system`, label: '后台管理', items: systemManagementMenus(source) }]
  }

  const items = normalizeTaskMenus(source)
  return [{ key: experience.value.persona, label: experience.value.label, items: items.length ? items : source }]
})

const sidebarHint = computed(() => {
  if (isSystemManagementArea.value && experience.value.isPlatformAdmin) {
    return { title: '系统管理', description: '身份权限、组织基础、配置审计和平台运行后台。' }
  }
  if (experience.value.isSuperAdmin) {
    return { title: '全量专家视图', description: '侧栏聚焦工作区、运营、处置和治理；系统管理从更多入口进入。' }
  }
  if (experience.value.isSecurityEngineer) {
    return { title: '治理策略视图', description: '默认聚焦策略、适配、剧本、风险评分和事件关联。' }
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
  if (isSystemManagementArea.value && experience.value.isPlatformAdmin) {
    return menuGroups.value.flatMap((group) => group.items.flatMap(allDirectoryIndexes))
  }
  if (experience.value.isSuperAdmin) {
    return menuGroups.value.flatMap((group) => group.items.flatMap(allDirectoryIndexes))
  }
  const active = route.path
  for (const group of menuGroups.value) {
    for (const item of group.items) {
      const chain = openChainForPath(item, active)
      if (chain.length) return chain
    }
  }
  return []
})

function allDirectoryIndexes(item: MenuItem): string[] {
  const children = item.children || []
  if (!children.length) return []
  return [menuIndex(item), ...children.flatMap(allDirectoryIndexes)]
}

onMounted(() => {
  void restoreSidebarPosition(true)
  void loadDemoDataStatus()
})

watch(() => route.path, () => {
  void restoreSidebarPosition(false)
  void loadDemoDataStatus()
})

watch(showDemoDataActions, (visible) => {
  if (visible) void loadDemoDataStatus()
})

async function handleImportDemoData() {
  try {
    await ElMessageBox.confirm(
      '导入会先清理旧演示数据，再写入固定演示资产、告警、工单、报表和离线证据链；用户、角色和账号不会被修改。继续？',
      '导入演示数据',
      { type: 'info', confirmButtonText: '导入', cancelButtonText: '取消' },
    )
  } catch (error) {
    if (!isConfirmCancel(error)) {
      ElMessage.error(messageFromError(error, '导入演示数据已取消'))
    }
    return
  }

  demoImporting.value = true
  try {
    const response = await importDemoData()
    const payload = response.data.data
    ElMessage.success(payload.message || `演示数据已导入，当前演示记录 ${payload.totalDemoRows} 条`)
    demoStatus.value = {
      seedBatchId: payload.seedBatchId,
      demoRangeBatchId: payload.demoRangeBatchId,
      totalDemoRows: payload.totalDemoRows,
      hasDemoData: payload.totalDemoRows > 0,
      message: payload.message,
    }
    refreshCurrentRoute()
  } catch (error) {
    ElMessage.error(messageFromError(error, '导入演示数据失败'))
  } finally {
    demoImporting.value = false
  }
}

async function handleClearDemoData() {
  try {
    await ElMessageBox.confirm(
      '只清理演示批次、演示来源和固定演示标识的数据；用户、角色、菜单、策略和本机真实数据会保留。继续？',
      '清除演示数据',
      { type: 'warning', confirmButtonText: '清除', cancelButtonText: '取消' },
    )
  } catch (error) {
    if (!isConfirmCancel(error)) {
      ElMessage.error(messageFromError(error, '清除演示数据已取消'))
    }
    return
  }

  demoClearing.value = true
  try {
    const response = await clearDemoData()
    const payload = response.data.data
    ElMessage.success(payload.message || '演示数据已清除')
    demoStatus.value = {
      seedBatchId: payload.seedBatchId,
      demoRangeBatchId: payload.demoRangeBatchId,
      totalDemoRows: payload.totalDemoRows,
      hasDemoData: payload.totalDemoRows > 0,
      message: payload.message,
    }
    refreshCurrentRoute()
  } catch (error) {
    ElMessage.error(messageFromError(error, '清除演示数据失败'))
  } finally {
    demoClearing.value = false
  }
}

function refreshCurrentRoute() {
  window.setTimeout(() => router.go(0), 350)
}

async function loadDemoDataStatus() {
  if (!showDemoDataActions.value) return
  try {
    const response = await demoDataStatus()
    demoStatus.value = response.data.data
  } catch {
    demoStatus.value = undefined
  }
}

function isConfirmCancel(error: unknown) {
  return error === 'cancel' || error === 'close'
}

function messageFromError(error: unknown, fallback: string) {
  const candidate = error as { response?: { data?: { message?: string } }; message?: string }
  return candidate.response?.data?.message || candidate.message || fallback
}

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
  const leaf = (path: string, name: string, icon?: string, fallback = false) => {
    const item = findPath(path)
    if (item) return { ...item, name, icon: icon || item.icon }
    return fallback ? directMenu(fallbackId(path), name, path, icon || 'Menu', 1000) : null
  }
  const dir = (id: number, name: string, icon: string, children: Array<MenuItem | null>) => {
    const visibleChildren = children.filter((item): item is MenuItem => Boolean(item))
    return visibleChildren.length
      ? { id, parentId: 0, name, icon, type: 'directory', sort: Math.abs(id), visible: 1, status: 1, children: visibleChildren } as MenuItem
      : null
  }

  if (experience.value.isSuperAdmin) {
    const adminLeaf = (path: string, name: string, icon?: string) => leaf(path, name, icon, true)
    return [
      dir(-100, '工作区', 'Odometer', [
        adminLeaf('/soc/dashboard', '安全运营工作台', 'DataAnalysis'),
        adminLeaf('/soc/capabilities', '平台能力说明', 'Grid'),
        adminLeaf('/soc/demo-range', '安全验证中心', 'Operation'),
        adminLeaf('/soc/daily-recommendations', '每日处理', 'Calendar'),
      ]),
      adminLeaf('/soc/agents', 'Agent 管理', 'Connection'),
      dir(-200, '安全运营', 'Monitor', [
        adminLeaf('/soc/incidents', '安全事件簇', 'Share'),
        adminLeaf('/soc/alerts', '告警中心', 'WarningFilled'),
        adminLeaf('/soc/assets', '资产风险', 'Cpu'),
        adminLeaf('/soc/client-security', '员工终端态势', 'Monitor'),
        adminLeaf('/soc/vulnerabilities', '漏洞中心', 'Aim'),
        adminLeaf('/soc/baselines', '基线核查', 'Checked'),
        adminLeaf('/soc/fim', '文件完整性', 'Files'),
        adminLeaf('/soc/external-events', '外部事件', 'Connection'),
      ]),
      dir(-300, '处置闭环', 'Tickets', [
        adminLeaf('/soc/tickets', '工单中心', 'Tickets'),
        adminLeaf('/soc/reports', '报表中心', 'DocumentChecked'),
      ]),
      dir(-400, '治理策略', 'SetUp', [
        adminLeaf('/soc/policies', '策略与规则中心', 'SetUp'),
        adminLeaf('/soc/rules', '检测规则中心', 'List'),
        adminLeaf('/soc/alert-noise', '告警降噪', 'Filter'),
        adminLeaf('/soc/settings', '接入与诊断设置', 'Tools'),
      ]),
    ].filter((item): item is MenuItem => Boolean(item))
  }

  if (experience.value.isPlatformAdmin) {
    return [
      dir(-100, '工作区', 'Odometer', [
        leaf('/soc/dashboard', '安全运营工作台', 'DataAnalysis'),
        leaf('/soc/daily-recommendations', '每日处理', 'Calendar'),
      ]),
      leaf('/soc/agents', 'Agent 管理', 'Connection'),
      dir(-200, '安全运营', 'Monitor', [
        leaf('/soc/incidents', '安全事件簇', 'Share'),
        leaf('/soc/alerts', '告警中心', 'WarningFilled'),
        leaf('/soc/assets', '资产风险', 'Cpu'),
        leaf('/soc/external-events', '证据中心', 'Connection'),
        leaf('/soc/tickets', '工单中心', 'Tickets'),
        leaf('/soc/reports', '报表中心', 'DocumentChecked'),
      ]),
      dir(-300, '治理策略', 'SetUp', [
        leaf('/soc/policies', '策略与规则中心', 'SetUp'),
        leaf('/soc/rules', '检测规则中心', 'List'),
        leaf('/soc/alert-noise', '告警降噪', 'Filter'),
      ]),
    ].filter((item): item is MenuItem => Boolean(item))
  }

  if (experience.value.isSecurityEngineer) {
    return [
      dir(-100, '工作区', 'Odometer', [
        leaf('/soc/dashboard', '运营工作台', 'DataAnalysis'),
        leaf('/soc/daily-recommendations', '每日处理', 'Calendar'),
      ]),
      dir(-200, '治理策略', 'SetUp', [
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
      dir(-100, '运营工作台', 'Odometer', [
        leaf('/soc/dashboard', '运营工作台', 'DataAnalysis'),
        leaf('/soc/daily-recommendations', '每日处理', 'Calendar'),
      ]),
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

function systemManagementMenus(source: MenuItem[]) {
  const leaf = (path: string, name: string, icon?: string) => {
    const item = findMenuByPath(source, path)
    if (item) return { ...item, name, icon: icon || item.icon }
    return directMenu(fallbackId(path), name, path, icon || 'Menu', 1000)
  }
  const dir = (id: number, name: string, icon: string, children: MenuItem[]) => ({
    id,
    parentId: 0,
    name,
    icon,
    type: 'directory',
    sort: Math.abs(id),
    visible: 1,
    status: 1,
    children,
  }) as MenuItem

  return [
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
  ]
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

function fallbackId(path: string) {
  let hash = 0
  for (const char of path) {
    hash = ((hash << 5) - hash) + char.charCodeAt(0)
    hash |= 0
  }
  return -10000 - Math.abs(hash)
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

.sidebar-demo-actions {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
  margin: 10px 10px 0;
}

.demo-data-status {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 24px;
  padding: 3px 8px;
  border: 1px solid rgba(93, 135, 255, 0.22);
  border-radius: 8px;
  background: rgba(239, 249, 250, 0.76);
  color: var(--soc-text-muted);
  font-size: 11px;
  font-weight: 720;
}

.demo-data-status.active {
  border-color: rgba(211, 120, 39, 0.34);
  background: rgba(255, 248, 238, 0.9);
  color: var(--soc-warm-strong);
}

.sidebar-demo-actions .el-button {
  width: 100%;
  min-height: 32px;
  margin: 0;
  font-size: 12px;
  font-weight: 700;
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
  .sidebar-demo-actions,
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
