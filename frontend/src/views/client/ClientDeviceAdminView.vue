<template>
  <div class="device-admin-page">
    <section class="device-admin-hero">
      <div>
        <span class="soc-page-kicker">COMPUTER DETAILS</span>
        <h1>设备信息</h1>
        <p>这个页面帮你查看当前电脑归属、安全状态、允许操作和需要联系安全团队处理的事项。</p>
      </div>
      <div class="hero-actions">
        <el-tag effect="plain" size="large">{{ selectedAsset ? `${selectedAsset.hostname} / ${selectedAsset.ip}` : '电脑未接入' }}</el-tag>
        <el-button :loading="loading" @click="loadData">刷新</el-button>
        <el-button @click="router.push({ path: '/client/workbench', query: clientDeviceQuery })">回到我的电脑</el-button>
      </div>
    </section>

    <section v-if="error" class="client-recoverable-error">
      <div>
        <span>数据加载失败</span>
        <strong>{{ error }}</strong>
        <p>可能是后端未启动、数据库未初始化或当前账号没有查看当前电脑的权限。</p>
      </div>
      <div class="recover-actions">
        <el-button type="primary" :loading="loading" @click="loadData">重试</el-button>
        <el-button @click="useOfflineDemoData">使用离线演示数据</el-button>
        <el-button text @click="showDiagnostics = !showDiagnostics">查看诊断</el-button>
      </div>
      <pre v-if="showDiagnostics">{{ errorDiagnostic || '暂无更多诊断信息。' }}</pre>
    </section>

    <section v-if="selectedAsset" class="device-admin-shell">
      <aside class="device-admin-aside">
        <div class="device-chip">
          <strong>{{ selectedAsset.hostname }}</strong>
          <span>{{ selectedAsset.ip }} · {{ selectedAsset.osType }}</span>
          <em>{{ selectedAsset.ownerName || currentUsername }} · {{ selectedAsset.deptName || '未分配部门' }}</em>
          <AssetRiskTag :risk-level="selectedAsset.riskLevel" />
        </div>
        <button
          v-for="module in adminModules"
          :key="module.name"
          type="button"
          :class="{ active: activeModule === module.name }"
          @click="activeModule = module.name"
        >
          <strong>{{ module.label }}</strong>
          <span>{{ module.desc }}</span>
        </button>
      </aside>

      <main class="device-admin-main">
        <section v-if="activeModule === 'identity'" class="soc-panel admin-module-panel">
          <div class="panel-title">
            <div>
              <strong>身份纳管</strong>
              <span>员工、部门、电脑编号和平台接入状态只展示一次</span>
            </div>
            <el-tag :type="clientApiAvailable ? 'success' : 'warning'" effect="plain">{{ clientApiAvailable ? '正式用户端接口' : '兼容读取' }}</el-tag>
          </div>
          <div class="identity-grid">
            <article v-for="item in identityRows" :key="item.label">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
              <em>{{ item.hint }}</em>
            </article>
          </div>
        </section>

        <section v-else-if="activeModule === 'defense'" class="soc-panel admin-module-panel">
          <div class="panel-title">
            <div>
              <strong>安全防御中心</strong>
              <span>用平台记录推导当前办公电脑的防护、补丁、配置、文件变更和网络状态</span>
            </div>
            <el-tag effect="plain">{{ healthLabel }}</el-tag>
          </div>
          <div class="posture-list">
            <article v-for="item in postureRows" :key="item.key" :class="`posture-${item.state}`">
              <div>
                <strong>{{ item.label }}</strong>
                <span>{{ item.detail }}</span>
              </div>
              <el-tag :type="item.tagType" effect="plain">{{ item.status }}</el-tag>
            </article>
          </div>
        </section>

        <section v-else-if="activeModule === 'scope'" class="soc-panel admin-module-panel">
          <div class="panel-title">
            <div>
              <strong>权限与数据范围</strong>
              <span>明确这里只能查看和处理自己的当前电脑，跨电脑和角色授权由管理员处理</span>
            </div>
            <el-tag effect="plain">{{ currentUsername }}</el-tag>
          </div>
          <div class="scope-card-grid">
            <article v-for="item in accessScopeRows" :key="item.label" :class="`scope-${item.state}`">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
              <em>{{ item.hint }}</em>
            </article>
          </div>
          <div class="permission-matrix">
            <div class="matrix-head">
              <strong>用户端权限矩阵</strong>
              <span>员工端只暴露当前电脑上下文；管理员仍负责角色、部门、菜单和审计</span>
            </div>
            <div class="matrix-row matrix-title">
              <span>功能模块</span>
              <span>员工端权限</span>
              <span>平台数据来源</span>
              <span>边界</span>
            </div>
            <div v-for="item in permissionMatrix" :key="item.key" class="matrix-row">
              <strong>{{ item.module }}</strong>
              <span>{{ item.clientPermission }}</span>
              <span>{{ item.backendSource }}</span>
              <el-tag :type="item.tagType" effect="plain">{{ item.boundary }}</el-tag>
            </div>
          </div>
          <div class="scope-notice">
            <strong>权限原则</strong>
            <span>员工端不展示其他电脑、不授予管理角色、不绕过权限控制。需要变更责任人、部门或角色时，请联系管理员处理。</span>
          </div>
        </section>

        <section v-else-if="activeModule === 'backend'" class="soc-panel admin-module-panel">
          <div class="panel-title">
            <div>
              <strong>联系安全团队</strong>
              <span>不复制管理端页面，只提供带当前电脑上下文的处理入口</span>
            </div>
            <el-tag effect="plain">同一资产上下文</el-tag>
          </div>
          <div class="backend-grid">
            <button v-for="link in backendLinks" :key="link.path" type="button" @click="openBackend(link.path)">
              <strong>{{ link.label }}</strong>
              <span>{{ link.desc }}</span>
            </button>
          </div>
        </section>

        <section v-else-if="activeModule === 'policy'" class="soc-panel admin-module-panel">
          <div class="panel-title">
            <div>
              <strong>策略与适配</strong>
              <span>公司策略、运行环境和员工端模块规则集中展示</span>
            </div>
            <el-tag :type="runtimeReady ? 'success' : 'warning'" effect="plain">{{ runtimeReady ? '自动适配' : '降级可用' }}</el-tag>
          </div>
          <div class="policy-runtime-card">
            <div>
              <span>当前运行环境</span>
              <strong>{{ runtimePlatformText }}</strong>
              <em>{{ runtimeCompatibility?.adapter || '本地浏览器兼容适配器' }}</em>
            </div>
            <div>
              <span>运行数据边界</span>
              <strong>{{ runtimeDataBoundary }}</strong>
              <em>员工端只展示和写入授权安全记录，不保存密钥或客户数据。</em>
            </div>
          </div>
          <div class="policy-grid">
            <article v-for="item in policyRows" :key="item.key" :class="`policy-${item.state}`">
              <div>
                <strong>{{ item.label }}</strong>
                <span>{{ item.desc }}</span>
              </div>
              <el-tag :type="item.tagType" effect="plain">{{ item.status }}</el-tag>
            </article>
          </div>
          <div class="service-contract-table">
            <div class="contract-head">
              <strong>用户端微模块契约</strong>
              <span>模块只暴露当前电脑上下文，平台仍是数据来源</span>
            </div>
            <div class="contract-row contract-title">
              <span>模块</span>
              <span>平台服务</span>
              <span>当前状态</span>
            </div>
            <div v-for="item in serviceContracts" :key="item.key" class="contract-row">
              <span>{{ item.module }}</span>
              <span>{{ item.service }}</span>
              <strong>{{ item.status }}</strong>
            </div>
          </div>
        </section>

        <section v-else class="soc-panel admin-module-panel">
          <div class="panel-title">
            <div>
              <strong>员工安全操作</strong>
              <span>员工只能执行自检、提交摘要、复制说明和进入本机检查；所有写入都会留下审计记录</span>
            </div>
            <el-tag type="warning" effect="plain">受控动作</el-tag>
          </div>
          <div class="employee-action-grid">
            <button type="button" :disabled="snapshotRunning" @click="runSnapshot">
              <strong>运行本机安全自检</strong>
              <span>执行固定安全只读命令，生成安全记录。</span>
            </button>
            <button type="button" :disabled="reporting" @click="quickReport">
              <strong>提交给安全团队</strong>
              <span>提交当前电脑风险摘要，生成安全记录。</span>
            </button>
            <button type="button" :disabled="!profile.alerts.length" @click="openHighestAlert">
              <strong>处理最高优先级告警</strong>
              <span>打开待处理告警并携带当前电脑上下文。</span>
            </button>
            <button type="button" @click="router.push({ path: '/client/local-range', query: clientDeviceQuery })">
              <strong>进入本机检查</strong>
              <span>打开当前电脑绑定的本机检查。</span>
            </button>
            <button type="button" @click="copySummary">
              <strong>复制 IT 支持摘要</strong>
              <span>复制主机、风险和待处理项，便于提交工单。</span>
            </button>
          </div>
          <div v-if="lastSnapshot" class="snapshot-mini">
            <strong>{{ lastSnapshot.snapshotId }}</strong>
            <span>事件 {{ lastSnapshot.event.eventUid }} / 告警 {{ lastSnapshot.alertId || '-' }}</span>
          </div>
        </section>
      </main>
    </section>
    <el-empty v-else description="没有找到当前办公电脑" :image-size="96" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import AssetRiskTag from '@/components/security/AssetRiskTag.vue'
import {
  importCyberFusionEvents,
  runClientSecuritySnapshot,
  runLocalDemoSecuritySnapshot,
  type AssetItem,
  type ClientDeviceProfile,
  type ClientRuntimeCompatibility,
  type ClientSecuritySnapshotResult,
} from '@/api/soc'
import { getToken } from '@/utils/storage'
import { useAuthStore } from '@/stores/auth'
import {
  buildEmptyClientProfile,
  buildClientDeviceRouteQuery,
  chooseClientAsset,
  emptyClientMetrics,
  loadClientAssets,
  loadClientProfile,
} from '@/composables/useClientDeviceContext'
import { loadClientRuntimeCompatibility } from '@/composables/useClientRuntimeCompatibility'

type AdminModule = 'identity' | 'defense' | 'scope' | 'backend' | 'policy' | 'actions'
type PostureState = 'ok' | 'warn' | 'risk'
type PolicyState = 'ok' | 'warn' | 'risk'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const loading = ref(false)
const error = ref('')
const errorDiagnostic = ref('')
const showDiagnostics = ref(false)
const activeModule = ref<AdminModule>('identity')
const selectedIp = ref('')
const assets = ref<AssetItem[]>([])
const profile = reactive<ClientDeviceProfile>({
  asset: {} as AssetItem,
  metrics: emptyClientMetrics,
  alerts: [],
  vulnerabilities: [],
  baselines: [],
  fileIntegrityEvents: [],
  externalEvents: [],
  timeline: [],
})
const clientApiAvailable = ref(true)
const snapshotRunning = ref(false)
const reporting = ref(false)
const lastSnapshot = ref<ClientSecuritySnapshotResult>()
const runtimeCompatibility = ref<ClientRuntimeCompatibility>()

const selectedAsset = computed(() => assets.value.find((asset) => asset.ip === selectedIp.value))
const currentUsername = computed(() => authStore.userInfo?.nickname || authStore.userInfo?.username || '当前办公用户')
const endpointCode = computed(() => selectedAsset.value ? `CF-${selectedAsset.value.ip.replaceAll('.', '-')}` : 'CF-ENDPOINT-PENDING')
const clientDeviceQuery = computed(() => buildClientDeviceRouteQuery({
  ip: selectedAsset.value?.ip || selectedIp.value,
  host: selectedAsset.value?.hostname,
  os: selectedAsset.value?.osType,
}))
const openVulnerabilities = computed(() => profile.vulnerabilities.filter((item) => !['fixed', 'accepted'].includes(item.status)))
const failedBaselines = computed(() => profile.baselines.filter((item) => item.result === 'failed' || !['passed', 'confirmed'].includes(item.status)))
const reviewFim = computed(() => profile.fileIntegrityEvents.filter((item) => !['closed', 'confirmed', 'ignored'].includes(item.status)))
const localEvidenceCount = computed(() => profile.externalEvents.filter((item) => {
  const text = [item.sourceType, item.eventType, item.ruleName, item.rawEvent, item.normalizedEvent].join(' ').toLowerCase()
  return text.includes('osquery') || text.includes('terminal') || text.includes('local') || text.includes('本地')
}).length)

const adminModules = [
  { name: 'identity' as const, label: '身份纳管', desc: '办公用户、部门、电脑编号' },
  { name: 'defense' as const, label: '防护状态', desc: '补丁、配置、文件、网络' },
  { name: 'scope' as const, label: '权限范围', desc: '可见范围、操作边界' },
  { name: 'backend' as const, label: '安全团队', desc: '资产、告警、漏洞、工单入口' },
  { name: 'policy' as const, label: '策略与适配', desc: '公司策略、跨系统、服务契约' },
  { name: 'actions' as const, label: '我的操作', desc: '自检、提交、检查、摘要' },
]
const identityRows = computed(() => {
  const asset = selectedAsset.value
  return [
    { label: '办公用户', value: asset?.ownerName || currentUsername.value, hint: asset?.ownerName ? '来自资产责任人' : '来自当前登录会话' },
    { label: '所属部门', value: asset?.deptName || '未分配部门', hint: '用于数据权限和工单路由' },
    { label: '电脑编号', value: endpointCode.value, hint: '员工端与管理端统一标识' },
    { label: '接入通道', value: clientApiAvailable.value ? '正式接入' : '兼容接入', hint: '安全记录写入平台' },
    { label: '最近在线', value: asset?.lastSeenAt?.replace('T', ' ').slice(0, 16) || '-', hint: '来自资产中心' },
    { label: '资产来源', value: asset?.sourceType || 'client', hint: '对应平台数据源' },
  ]
})
const healthLabel = computed(() => {
  if (profile.metrics.riskScore >= 85) return '需要安全介入'
  if (profile.metrics.riskScore >= 68) return '高风险办公电脑'
  if (profile.metrics.riskScore >= 45) return '待优化'
  return '防护稳定'
})
const postureRows = computed(() => [
  {
    key: 'edr',
    label: '安全客户端在线',
    status: clientApiAvailable.value ? '在线' : '降级',
    detail: clientApiAvailable.value ? '正式员工端接口可用，按当前账号和部门写入平台。' : '当前使用兼容通道读取和写入安全记录。',
    state: clientApiAvailable.value ? 'ok' : 'warn',
    tagType: clientApiAvailable.value ? 'success' : 'warning',
  },
  {
    key: 'patch',
    label: '补丁与漏洞',
    status: openVulnerabilities.value.length ? `${openVulnerabilities.value.length} 项待修复` : '正常',
    detail: openVulnerabilities.value.length ? '存在软件包或 Web 风险，需要安全团队安排修复。' : '当前没有待修复漏洞。',
    state: openVulnerabilities.value.length ? 'risk' : 'ok',
    tagType: openVulnerabilities.value.length ? 'danger' : 'success',
  },
  {
    key: 'baseline',
    label: '公司基线策略',
    status: failedBaselines.value.length ? `${failedBaselines.value.length} 项待确认` : '达标',
    detail: failedBaselines.value.length ? '存在基线失败或状态未确认项。' : '公司基线状态稳定。',
    state: failedBaselines.value.length ? 'warn' : 'ok',
    tagType: failedBaselines.value.length ? 'warning' : 'success',
  },
  {
    key: 'fim',
    label: '文件变更',
    status: reviewFim.value.length ? `${reviewFim.value.length} 条变更` : '稳定',
    detail: reviewFim.value.length ? '存在待确认关键文件变更。' : '关键文件完整性暂无待处理项。',
    state: reviewFim.value.length ? 'warn' : 'ok',
    tagType: reviewFim.value.length ? 'warning' : 'success',
  },
  {
    key: 'network',
    label: '网络与本机检查',
    status: localEvidenceCount.value ? `${localEvidenceCount.value} 条记录` : '待检查',
    detail: localEvidenceCount.value ? '本机检查或安全快照已写入当前电脑画像。' : '建议运行一次自检或进入本机检查。',
    state: localEvidenceCount.value ? 'ok' : 'warn',
    tagType: localEvidenceCount.value ? 'success' : 'warning',
  },
] satisfies Array<{ key: string; label: string; status: string; detail: string; state: PostureState; tagType: 'success' | 'warning' | 'danger' }>)
const accessScopeRows = computed(() => [
  {
    label: '可见资产',
    value: selectedAsset.value ? '当前电脑 1 台' : '未绑定',
    hint: selectedAsset.value ? `${selectedAsset.value.hostname} / ${selectedAsset.value.ip}` : '缺少当前电脑上下文',
    state: selectedAsset.value ? 'ok' : 'warn',
  },
  {
    label: '数据范围',
    value: selectedAsset.value?.deptName || '当前用户',
    hint: '提醒、漏洞、配置、文件变更和安全记录均按当前电脑过滤',
    state: 'ok',
  },
  {
    label: '操作权限',
    value: '受控员工动作',
    hint: '自检、提交、复制摘要、进入本机检查；处理动作需确认并写入审计',
    state: 'ok',
  },
  {
    label: '管理权限',
    value: '联系管理员',
    hint: '责任人、部门、角色、菜单权限和跨资产查询不在员工端变更',
    state: 'warn',
  },
] satisfies Array<{ label: string; value: string; hint: string; state: 'ok' | 'warn' }>)
const permissionMatrix = computed(() => [
  {
    key: 'device-admin',
    module: '设备信息',
    clientPermission: '查看当前电脑画像和公司策略',
    backendSource: '/client/devices/{ip}/profile',
    boundary: '只读画像',
    tagType: 'success',
  },
  {
    key: 'data-report',
    module: '提交日志',
    clientPermission: '提交授权日志或当前电脑摘要',
    backendSource: '/soc/external-events/import',
    boundary: '脱敏检查',
    tagType: 'warning',
  },
  {
    key: 'operations',
    module: '我的待办',
    clientPermission: '确认、转工单、误报或关闭当前电脑告警',
    backendSource: '/soc/alerts/{id}/action + /soc/tickets',
    boundary: '二次确认',
    tagType: 'warning',
  },
  {
    key: 'local-range',
    module: '本机检查',
    clientPermission: '使用安全检查命令和授权本机现场',
    backendSource: '/client/local-snapshot + /soc/external-events',
    boundary: '安全命令',
    tagType: 'success',
  },
  {
    key: 'backend',
    module: '安全团队入口',
    clientPermission: '带当前电脑上下文跳转',
    backendSource: '/soc/assets / /soc/alerts / /soc/tickets',
    boundary: '权限控制',
    tagType: 'info',
  },
] satisfies Array<{ key: string; module: string; clientPermission: string; backendSource: string; boundary: string; tagType: 'success' | 'warning' | 'danger' | 'info' }>)
const backendLinks = computed(() => [
  { path: '/soc/assets', label: '资产详情', desc: selectedAsset.value?.hostname || '当前电脑' },
  { path: '/soc/alerts', label: '待处理告警', desc: `${profile.alerts.length} 条当前电脑告警` },
  { path: '/soc/vulnerabilities', label: '漏洞风险', desc: `${openVulnerabilities.value.length} 项待修复风险` },
  { path: '/soc/tickets', label: '处置工单', desc: '告警转工单和进度跟踪' },
])
const runtimeReady = computed(() => {
  const runtime = runtimeCompatibility.value
  if (!runtime) return false
  return runtime.capabilities.every((item) => item.status === 'ready' || item.status === 'configurable')
})
const runtimePlatformText = computed(() => {
  const runtime = runtimeCompatibility.value
  if (!runtime) return `${selectedAsset.value?.osType || '当前系统'} · 待检测`
  return `${runtime.platform.browserFamily} · ${runtime.platform.osFamily} · ${runtime.platform.arch}`
})
const runtimeDataBoundary = computed(() => {
  const root = runtimeCompatibility.value?.dataRoot
  if (!root) return '后端待确认'
  return root.environmentRoot && root.outsideSourceRoot ? `${root.displayName} · 已隔离` : `${root.displayName} · 需检查`
})
const policyRows = computed(() => [
  {
    key: 'evidence',
    label: '记录写入边界',
    status: '受控',
    desc: '员工端只写入授权本机记录、只读快照和手动提交摘要。',
    state: 'ok',
    tagType: 'success',
  },
  {
    key: 'terminal',
    label: '终端命令策略',
    status: '安全命令',
    desc: '只允许身份、网络、进程、启动项、主机名等观察命令；其他命令拦截并审计。',
    state: 'ok',
    tagType: 'success',
  },
  {
    key: 'runtime',
    label: '跨系统适配',
    status: runtimeReady.value ? '自动' : '降级',
    desc: runtimeCompatibility.value?.adapter || '后端适配状态不可用时，前端按浏览器平台降级。',
    state: runtimeReady.value ? 'ok' : 'warn',
    tagType: runtimeReady.value ? 'success' : 'warning',
  },
  {
    key: 'risk',
    label: '高风险处置要求',
    status: profile.metrics.riskScore >= 68 ? '需要处理' : '正常',
    desc: profile.metrics.riskScore >= 68 ? '高风险电脑应进入我的待办或转处置工单。' : '当前风险未达到强制处理阈值。',
    state: profile.metrics.riskScore >= 68 ? 'risk' : 'ok',
    tagType: profile.metrics.riskScore >= 68 ? 'danger' : 'success',
  },
] satisfies Array<{ key: string; label: string; status: string; desc: string; state: PolicyState; tagType: 'success' | 'warning' | 'danger' }>)
const serviceContracts = computed(() => [
  { key: 'identity', module: '身份纳管', service: '/client/devices + /soc/assets', status: selectedAsset.value ? '已绑定当前电脑' : '待接入' },
  { key: 'defense', module: '防御状态', service: '/client/devices/{ip}/profile', status: clientApiAvailable.value ? '正式画像' : '兼容画像' },
  { key: 'runtime', module: '策略与适配', service: '/client/runtime/compatibility', status: runtimeReady.value ? '自动适配' : '前端降级' },
  { key: 'actions', module: '我的操作', service: '/client/local-snapshot + /soc/external-events', status: '受控写入' },
])

onMounted(() => {
  void loadData()
})

async function loadData() {
  loading.value = true
  error.value = ''
  errorDiagnostic.value = ''
  try {
    await loadRuntime()
    const loaded = await loadClientAssets()
    clientApiAvailable.value = loaded.clientApiAvailable
    assets.value = loaded.records
    selectedIp.value = chooseClientAsset(assets.value, {
      routeIp: typeof route.query.ip === 'string' ? route.query.ip : '',
      routeHost: typeof route.query.host === 'string' ? route.query.host : '',
      currentNames: [authStore.userInfo?.nickname, authStore.userInfo?.username],
    })?.ip || ''
    await loadProfile()
  } catch (err) {
    error.value = '设备信息加载失败，请重新登录，或联系安全团队检查服务状态。'
    errorDiagnostic.value = err instanceof Error ? err.message : String(err)
  } finally {
    loading.value = false
  }
}

function useOfflineDemoData() {
  const asset: AssetItem = {
    id: 0,
    hostname: 'prod-app-01',
    ip: '10.20.1.15',
    osType: 'Linux',
    sourceType: 'offline-demo',
    riskLevel: 'medium',
    deptName: '演示部门',
    ownerName: currentUsername.value,
    openAlertCount: 0,
    lastSeenAt: new Date().toISOString(),
  }
  assets.value = [asset]
  selectedIp.value = asset.ip
  Object.assign(profile, buildEmptyClientProfile(asset))
  clientApiAvailable.value = false
  error.value = ''
  errorDiagnostic.value = ''
  showDiagnostics.value = false
  ElMessage.warning('已切换为离线演示数据')
}

async function loadRuntime() {
  runtimeCompatibility.value = await loadClientRuntimeCompatibility()
}

async function loadProfile() {
  const asset = selectedAsset.value
  if (!asset) return
  try {
    Object.assign(profile, await loadClientProfile(asset))
    clientApiAvailable.value = true
  } catch {
    clientApiAvailable.value = false
    Object.assign(profile, buildEmptyClientProfile(asset))
  }
}

function backendQuery() {
  const asset = selectedAsset.value
  return asset ? { assetIp: asset.ip, keyword: asset.hostname, source: 'client-device-admin' } : { source: 'client-device-admin' }
}

function openBackend(path: string) {
  void router.push({ path, query: backendQuery() })
}

function openHighestAlert() {
  void router.push({ path: '/soc/alerts', query: { ...backendQuery(), openAlertId: profile.alerts[0]?.id } })
}

async function runSnapshot() {
  const asset = selectedAsset.value
  if (!asset) return
  snapshotRunning.value = true
  try {
    const runner = getToken() ? runClientSecuritySnapshot : runLocalDemoSecuritySnapshot
    const res = await runner({ assetIp: asset.ip, note: '员工端设备信息自检', linkAlert: true })
    lastSnapshot.value = res.data.data
    ElMessage.success(res.data.data.message)
    await loadProfile()
  } finally {
    snapshotRunning.value = false
  }
}

async function quickReport() {
  const asset = selectedAsset.value
  if (!asset) return
  reporting.value = true
  try {
    await importCyberFusionEvents({
      sourceType: 'osquery',
      linkAlerts: true,
      content: JSON.stringify({
        eventType: 'client_device_admin_report',
        collectedAt: new Date().toISOString(),
        asset: { hostname: asset.hostname, ip: asset.ip, osType: asset.osType, ownerName: asset.ownerName, deptName: asset.deptName },
        metrics: profile.metrics,
      }, null, 2),
    })
    ElMessage.success('已提交当前电脑摘要')
    await loadProfile()
  } finally {
    reporting.value = false
  }
}

async function copySummary() {
  const asset = selectedAsset.value
  if (!asset) return
  await navigator.clipboard.writeText([
    `主机：${asset.hostname}`,
    `IP：${asset.ip}`,
    `风险分：${profile.metrics.riskScore}`,
    `未关闭告警：${profile.alerts.length}`,
    `待修复漏洞：${openVulnerabilities.value.length}`,
    `待确认记录：${failedBaselines.value.length}`,
  ].join('\n'))
  ElMessage.success('电脑安全摘要已复制')
}
</script>

<style scoped>
.device-admin-page {
  display: grid;
  gap: 14px;
}

.client-recoverable-error {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid rgba(216, 76, 88, 0.28);
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(255, 241, 242, 0.92), rgba(255, 255, 255, 0.74));
  box-shadow: var(--soc-shadow-soft), var(--soc-glass-highlight);
}

.client-recoverable-error span,
.client-recoverable-error p {
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.client-recoverable-error strong {
  color: var(--soc-text);
  font-size: 16px;
}

.client-recoverable-error p {
  margin: 0;
}

.client-recoverable-error pre {
  max-height: 160px;
  overflow: auto;
  margin: 0;
  padding: 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--soc-text-muted);
  font-size: 12px;
  white-space: pre-wrap;
}

.recover-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.device-admin-hero,
.device-admin-shell,
.device-admin-aside,
.admin-module-panel {
  border: 1px solid var(--soc-border);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: var(--soc-shadow-soft), var(--soc-glass-highlight);
  backdrop-filter: blur(22px) saturate(1.12);
}

.device-admin-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  align-items: end;
  padding: 18px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.88), rgba(255, 248, 238, 0.68), rgba(238, 246, 247, 0.58));
}

.device-admin-hero h1 {
  margin: 0;
  color: var(--soc-text);
  font-size: 22px;
}

.device-admin-hero p {
  margin: 6px 0 0;
  color: var(--soc-text-muted);
  font-size: 13px;
  line-height: 1.65;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.device-admin-shell {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 14px;
  padding: 14px;
  background: rgba(255, 255, 255, 0.46);
}

.device-admin-aside {
  display: grid;
  gap: 10px;
  align-content: start;
  padding: 12px;
  background: linear-gradient(135deg, rgba(255, 248, 238, 0.58), rgba(255, 255, 255, 0.52));
}

.device-chip,
.device-admin-aside button,
.identity-grid article,
.posture-list article,
.backend-grid button,
.employee-action-grid button,
.snapshot-mini,
.policy-runtime-card,
.policy-grid article,
.service-contract-table,
.scope-card-grid article,
.permission-matrix,
.scope-notice {
  border: 1px solid rgba(179, 173, 163, 0.38);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.62);
}

.device-chip {
  display: grid;
  gap: 5px;
  padding: 12px;
}

.device-chip strong,
.device-admin-aside strong,
.identity-grid strong,
.posture-list strong,
.backend-grid strong,
.employee-action-grid strong,
.snapshot-mini strong,
.policy-runtime-card strong,
.policy-grid strong,
.service-contract-table strong,
.scope-card-grid strong,
.permission-matrix strong,
.scope-notice strong {
  color: var(--soc-text);
}

.device-chip span,
.device-chip em,
.device-admin-aside span,
.identity-grid span,
.identity-grid em,
.posture-list span,
.backend-grid span,
.employee-action-grid span,
.snapshot-mini span,
.policy-runtime-card span,
.policy-runtime-card em,
.policy-grid span,
.service-contract-table span,
.scope-card-grid span,
.scope-card-grid em,
.permission-matrix span,
.scope-notice span {
  color: var(--soc-text-muted);
  font-size: 12px;
  font-style: normal;
  line-height: 1.5;
}

.device-admin-aside button,
.backend-grid button,
.employee-action-grid button {
  display: grid;
  gap: 4px;
  padding: 11px;
  text-align: left;
  cursor: pointer;
}

.device-admin-aside button.active,
.device-admin-aside button:hover,
.backend-grid button:hover,
.employee-action-grid button:hover:not(:disabled) {
  border-color: rgba(212, 147, 74, 0.56);
  background: rgba(255, 248, 238, 0.84);
  box-shadow: inset 3px 0 0 var(--soc-warm);
}

.device-admin-aside button.active strong {
  color: var(--soc-warm-strong);
}

.admin-module-panel {
  min-height: 420px;
  padding: 16px;
  box-shadow: none;
}

.panel-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-title div {
  display: grid;
  gap: 3px;
}

.panel-title strong {
  color: var(--soc-text);
  font-size: 15px;
}

.panel-title span {
  color: var(--soc-text-muted);
  font-size: 12px;
}

.identity-grid,
.backend-grid,
.employee-action-grid,
.policy-grid,
.scope-card-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.identity-grid article,
.snapshot-mini,
.policy-runtime-card,
.policy-grid article,
.service-contract-table,
.scope-card-grid article,
.permission-matrix,
.scope-notice {
  display: grid;
  gap: 4px;
  padding: 12px;
}

.scope-card-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.scope-card-grid article {
  border-left-width: 4px;
}

.scope-ok {
  border-left-color: #2fac66 !important;
}

.scope-warn {
  border-left-color: var(--soc-warm) !important;
}

.policy-runtime-card {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-bottom: 10px;
  background: linear-gradient(135deg, rgba(255, 248, 238, 0.62), rgba(239, 248, 248, 0.62));
}

.policy-runtime-card > div,
.policy-grid article > div {
  display: grid;
  gap: 4px;
}

.posture-list {
  display: grid;
  gap: 10px;
}

.posture-list article {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border-left-width: 4px;
}

.posture-list article > div {
  display: grid;
  gap: 4px;
}

.posture-ok {
  border-left-color: #2fac66 !important;
}

.posture-warn {
  border-left-color: var(--soc-warm) !important;
}

.posture-risk {
  border-left-color: #d84c58 !important;
}

.policy-grid article {
  border-left-width: 4px;
}

.policy-ok {
  border-left-color: #2fac66 !important;
}

.policy-warn {
  border-left-color: var(--soc-warm) !important;
}

.policy-risk {
  border-left-color: #d84c58 !important;
}

.service-contract-table {
  margin-top: 10px;
}

.permission-matrix,
.scope-notice {
  margin-top: 10px;
}

.matrix-head,
.contract-head {
  display: grid;
  gap: 4px;
  margin-bottom: 10px;
}

.matrix-row,
.contract-row {
  display: grid;
  grid-template-columns: minmax(120px, 0.8fr) minmax(180px, 1.2fr) minmax(180px, 1.2fr) minmax(110px, 0.6fr);
  gap: 10px;
  padding: 9px 0;
  border-top: 1px solid rgba(179, 173, 163, 0.24);
}

.contract-row {
  grid-template-columns: minmax(120px, 0.8fr) minmax(180px, 1.2fr) minmax(120px, 0.8fr);
}

.matrix-row .el-tag {
  justify-self: start;
}

.matrix-title span,
.contract-title span {
  color: var(--soc-text);
  font-weight: 700;
}

.employee-action-grid button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.snapshot-mini {
  margin-top: 12px;
}

@media (max-width: 1020px) {
  .device-admin-hero,
  .device-admin-shell,
  .identity-grid,
  .backend-grid,
  .employee-action-grid,
  .policy-runtime-card,
  .policy-grid,
  .scope-card-grid,
  .matrix-row,
  .contract-row {
    grid-template-columns: 1fr;
  }

  .hero-actions {
    justify-content: flex-start;
  }
}
</style>
