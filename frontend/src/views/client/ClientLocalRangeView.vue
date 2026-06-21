<template>
  <div class="local-check-page">
    <section class="local-check-hero">
      <div>
        <span class="soc-page-kicker">SECURITY TOOLBOX</span>
        <h1>安全工具箱</h1>
        <p>这里只展示安全团队发布的只读工具。工具结果会作为安全日志提交，不会修改你的电脑。</p>
      </div>
      <div class="local-device-strip">
        <span><b>设备</b>{{ asset.name }}</span>
        <span><b>IP</b>{{ asset.ip }}</span>
        <span><b>OS</b>{{ asset.osType }}</span>
        <el-tag type="success" effect="plain">已授权</el-tag>
        <el-tag :type="connectionTag" effect="plain">{{ connectionLabel }}</el-tag>
        <el-button @click="router.push({ path: '/client/workbench', query: clientDeviceQuery })">返回我的电脑</el-button>
      </div>
    </section>

    <section v-if="showEnvironmentWarning" class="local-check-error">
      <div>
        <span>CHECK ENVIRONMENT</span>
        <strong>检查环境未完全就绪</strong>
        <p>可能是后端未启动、运行目录未配置或本地连接暂不可用。</p>
      </div>
      <div class="local-check-error-actions">
        <el-button type="primary" @click="reloadInspectionEnvironment">重新检测</el-button>
        <el-button @click="continueReadOnlyCheck">继续使用只读检查</el-button>
        <el-button text @click="diagnosticsVisible = true">查看诊断</el-button>
        <el-button text @click="router.push({ path: '/client/workbench', query: clientDeviceQuery })">返回我的电脑</el-button>
      </div>
    </section>

    <section class="local-check-shell">
      <aside class="check-stepper-card">
        <el-steps :active="activeStep" direction="vertical" finish-status="success">
          <el-step v-for="step in inspectionSteps" :key="step.title" :title="step.title" :description="step.description" />
        </el-steps>
      </aside>

      <main class="check-workspace">
        <section class="check-focus-card">
          <div class="panel-title">
            <div>
              <strong>{{ currentStepTitle }}</strong>
              <span>{{ currentStepDescription }}</span>
            </div>
            <el-tag effect="plain">第 {{ activeStep + 1 }} / 4 步</el-tag>
          </div>

          <div class="device-confirm-grid">
            <article>
              <span>当前检查电脑</span>
              <strong>{{ asset.name }}</strong>
              <em>{{ asset.ip }} · {{ asset.osType }}</em>
            </article>
            <article>
              <span>授权状态</span>
              <strong>已授权只读检查</strong>
              <em>不会修改电脑，不访问外部目标</em>
            </article>
            <article>
              <span>连接状态</span>
              <strong>{{ connectionLabel }}</strong>
              <em>{{ connectionHint }}</em>
            </article>
          </div>

          <div class="toolbox-authorization">
            <div>
              <strong>工具执行前授权说明</strong>
              <span>这些工具只读取当前电脑状态，不修改设置、不删除文件、不结束进程，也不会访问公网目标。</span>
            </div>
            <el-checkbox v-model="toolboxAuthorized">
              我确认在当前电脑上运行安全团队发布的只读工具
            </el-checkbox>
          </div>

          <div class="check-card-grid">
            <button
              v-for="command in terminalCommands"
              :key="command.key"
              type="button"
              class="check-option-card"
              :class="{ active: selectedCommandKey === command.key }"
              @click="selectCheckItem(command.key)"
            >
              <span>{{ command.phase }}</span>
              <strong>{{ command.label }}</strong>
              <em>{{ checkDescription(command.key) }}</em>
              <small>{{ command.builtInFallback ? '内置默认策略' : '后台 active 策略' }} · 只提交工具编号</small>
              <i v-if="command.builtInFallback">使用内置默认策略</i>
            </button>
          </div>

          <el-empty
            v-if="!selectedCommand"
            description="请选择一个检查项，然后点击“运行检查”。"
            :image-size="88"
          />

          <div v-else class="selected-check-panel">
            <div>
              <span>已选择检查项</span>
              <strong>{{ selectedCommand.label }}</strong>
              <em>员工端只提交工具编号：{{ selectedCommand.key }}。技术命令在“技术详情”中查看。</em>
            </div>
            <el-button type="primary" :disabled="!toolboxAuthorized" :loading="submitting === 'terminal_command'" @click="runSelectedCheck">
              运行工具
            </el-button>
          </div>
        </section>

        <section class="check-result-card">
          <div class="panel-title">
            <div>
              <strong>结果摘要</strong>
              <span>只展示员工需要理解的检查结果，技术输出默认收起。</span>
            </div>
            <el-button text @click="technicalOutputVisible = true">技术详情</el-button>
          </div>
          <el-empty v-if="!hasCheckResult" description="请选择一个检查项，然后点击“运行检查”。" :image-size="88" />
          <div v-else class="result-summary-grid">
            <article>
              <span>最近检查</span>
              <strong>{{ latestCheckTitle }}</strong>
              <em>{{ latestCheckMessage }}</em>
            </article>
            <article>
              <span>提交状态</span>
              <strong>{{ activityFeed.length ? '已提交安全记录' : '待提交' }}</strong>
              <em>{{ latestRunTime || '运行检查后自动提交' }}</em>
            </article>
            <article>
              <span>下一步</span>
              <strong>{{ nextEmployeeAction }}</strong>
              <em>需要补充信息时，可回到我的电脑或提交日志。</em>
            </article>
          </div>
          <div class="result-actions">
            <el-button type="primary" :loading="snapshotRunning" @click="runFactSnapshot">生成本机检查记录</el-button>
            <el-button @click="router.push({ path: '/client/data-report', query: clientDeviceQuery })">提交安全日志</el-button>
            <el-button @click="router.push({ path: '/client/workbench', query: clientDeviceQuery })">查看本机体检报告</el-button>
            <el-button text @click="diagnosticsVisible = true">诊断信息</el-button>
          </div>
        </section>
      </main>
    </section>

    <el-drawer v-model="diagnosticsVisible" title="诊断信息" size="520px">
      <div class="diagnostic-stack">
        <div class="diagnostic-grid">
          <span>浏览器平台</span><strong>{{ runtimePlatformLabel }}</strong>
          <span>目标系统</span><strong>{{ vmTargetOsLabel }}</strong>
          <span>运行适配</span><strong>{{ runtimeAdapterDetail }}</strong>
          <span>命令通道</span><strong>{{ terminalModeLabel }}</strong>
          <span>检查记录通道</span><strong>{{ snapshotModeLabel }}</strong>
          <span>资产来源</span><strong>{{ assetAutoMode ? '当前用户资产接口' : '演示默认资产' }}</strong>
        </div>
        <div class="diagnostic-list">
          <article v-for="item in runtimeCapabilityRows" :key="item.key">
            <strong>{{ item.label }}</strong>
            <span>{{ item.message }}</span>
          </article>
        </div>
      </div>
    </el-drawer>

    <el-drawer v-model="technicalOutputVisible" title="技术详情" size="560px">
      <div class="technical-output-stack">
        <el-alert title="技术命令和输出仅用于安全团队复核，员工端默认不展示。" type="info" show-icon :closable="false" />
        <div class="diagnostic-grid">
          <span>选中检查项</span><strong>{{ selectedCommand?.label || '-' }}</strong>
          <span>commandKey</span><strong>{{ selectedCommand?.key || '-' }}</strong>
          <span>技术命令</span><strong>{{ selectedCommand?.command || '-' }}</strong>
        </div>
        <pre>{{ technicalOutputText }}</pre>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import {
  importCyberFusionEvents,
  listClientLocalCommands,
  runClientSecuritySnapshot,
  runClientTerminalCommand,
  runLocalDemoSecuritySnapshot,
  runLocalDemoTerminalCommand,
  submitClientLabEvent,
  submitLocalDemoLabEvent,
  type ClientSecuritySnapshotResult,
  type ClientRuntimeCompatibility,
  type LocalCheckCommandOption,
} from '@/api/soc'
import { getToken } from '@/utils/storage'
import { useAuthStore } from '@/stores/auth'
import {
  chooseClientAsset,
  buildClientDeviceRouteQuery,
  DEMO_CLIENT_HOSTNAME,
  DEMO_CLIENT_IP,
  loadClientAssets,
} from '@/composables/useClientDeviceContext'
import { loadClientRuntimeCompatibility } from '@/composables/useClientRuntimeCompatibility'

type RangeActionType = 'login_failure' | 'sensitive_path' | 'upload_probe' | 'privilege_boundary' | 'data_query' | 'persistence_signal' | 'terminal_command'
type ActivityItem = {
  id: string
  title: string
  message: string
  time: string
  eventUid?: string
  alertId?: number
  sourceType?: string
  assetIp?: string
}
type TerminalLine = { id: string; prefix: string; text: string; kind: 'prompt' | 'output' | 'blocked' | 'success' }
type HostOs = 'windows' | 'macos' | 'linux'
type TerminalCommand = {
  key: string
  label: string
  command: string
  phase: string
  output: string[]
  severity: string
  description?: string
  builtInFallback?: boolean
}
type LocalVmAsset = { name: string; ip: string; osType: string }

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const ACTIVITY_STORAGE_PREFIX = 'cyberfusion_local_range_activity'
const portalStatus = ref<'locked' | 'demo'>('locked')
const submitting = ref<RangeActionType | ''>('')
const snapshotRunning = ref(false)
const queryKeyword = ref('')
const activityFeed = ref<ActivityItem[]>([])
const webApiMode = ref<'unknown' | 'real' | 'fallback'>('unknown')
const terminalApiMode = ref<'unknown' | 'real' | 'fallback'>('unknown')
const snapshotApiMode = ref<'unknown' | 'real' | 'fallback'>('unknown')
const runtimeCompatibility = ref<ClientRuntimeCompatibility>()
const vmUnlocked = ref(true)
const workspaceMode = ref<'terminal' | 'vm'>('terminal')
const loginForm = reactive({ username: 'demo.user', password: '' })
const vmLoginForm = reactive({ username: 'demo-user', password: '' })
const vmConsoleForm = reactive({ url: '' })
const terminalForm = reactive({ command: '' })
const detectedAsset = ref<LocalVmAsset>()
const assetAutoMode = ref(false)
const activeStep = ref(0)
const selectedCommandKey = ref('identity')
const terminalCommands = ref<TerminalCommand[]>([])
const diagnosticsVisible = ref(false)
const technicalOutputVisible = ref(false)
const toolboxAuthorized = ref(false)
const environmentWarningDismissed = ref(false)
const inspectionError = ref('')
const latestRunTime = ref('')
const terminalLines = ref<TerminalLine[]>([
  { id: 'hello', prefix: 'SOC', text: '本机只读检查已就绪。只能运行安全团队预设的白名单检查项。', kind: 'output' },
])

const asset = computed<LocalVmAsset>(() => {
  const queryHost = typeof route.query.host === 'string' && route.query.host ? route.query.host : ''
  const queryIp = typeof route.query.ip === 'string' && route.query.ip ? route.query.ip : ''
  const queryOs = typeof route.query.os === 'string' && route.query.os ? route.query.os : ''
  if (queryIp || queryHost) {
    return {
      name: queryHost || detectedAsset.value?.name || DEMO_CLIENT_HOSTNAME,
      ip: queryIp || detectedAsset.value?.ip || DEMO_CLIENT_IP,
      osType: queryOs || detectedAsset.value?.osType || hostOsLabel.value,
    }
  }
  return detectedAsset.value || { name: DEMO_CLIENT_HOSTNAME, ip: DEMO_CLIENT_IP, osType: hostOsLabel.value }
})
const clientDeviceQuery = computed(() => buildClientDeviceRouteQuery({
  ip: asset.value.ip,
  host: asset.value.name,
  os: asset.value.osType,
}))

const clientHostOs = computed<HostOs>(() => detectHostOs())
const vmTargetOs = computed<HostOs>(() => osFamilyFromText(asset.value.osType || hostOsLabel.value))
const hostOsLabel = computed(() => {
  if (clientHostOs.value === 'windows') return 'Windows'
  if (clientHostOs.value === 'macos') return 'macOS'
  return 'Linux'
})
const vmTargetOsLabel = computed(() => {
  if (vmTargetOs.value === 'windows') return 'Windows'
  if (vmTargetOs.value === 'macos') return 'macOS'
  return 'Linux'
})

const webModeLabel = computed(() => {
  if (webApiMode.value === 'real') return '真实用户端接口'
  if (webApiMode.value === 'fallback') return '兼容入湖通道'
  return '网页采集待检测'
})

const webModeTag = computed(() => {
  if (webApiMode.value === 'real') return 'success'
  if (webApiMode.value === 'fallback') return 'warning'
  return 'info'
})

const terminalModeLabel = computed(() => {
  if (terminalApiMode.value === 'real') return '真实终端接口'
  if (terminalApiMode.value === 'fallback') return '兼容演示通道'
  return '终端接口待检测'
})

const terminalModeTag = computed(() => {
  if (terminalApiMode.value === 'real') return 'success'
  if (terminalApiMode.value === 'fallback') return 'warning'
  return 'info'
})

const snapshotModeLabel = computed(() => {
  if (snapshotApiMode.value === 'real') return '真实事实采集'
  if (snapshotApiMode.value === 'fallback') return '本机回环采集'
  return '事实接口待检测'
})

const snapshotModeTag = computed(() => {
  if (snapshotApiMode.value === 'real') return 'success'
  if (snapshotApiMode.value === 'fallback') return 'warning'
  return 'info'
})

const safeVmConsoleUrl = computed(() => {
  const queryUrl = typeof route.query.vmConsoleUrl === 'string' ? route.query.vmConsoleUrl : ''
  return safeLocalConsoleUrl(queryUrl || vmConsoleForm.url)
})

watch(
  () => asset.value.ip,
  () => {
    loadActivityFeed()
    loadVmConsoleUrl()
  },
  { immediate: true },
)

const actionCards: Array<{ type: RangeActionType; title: string; desc: string; button: string; severity: string }> = [
  { type: 'sensitive_path', title: '敏感路径访问', desc: '打开受限报表路径，演示 Web 访问风险被安全团队记录。', button: '访问受限报表', severity: 'high' },
  { type: 'upload_probe', title: '异常上传拦截', desc: '选择受控可疑文件名，不读取本地文件内容。', button: '上报 invoice.exe 尝试', severity: 'high' },
  { type: 'privilege_boundary', title: '权限边界验证', desc: '访问经理审批接口，演示越权请求被拦截并上报。', button: '访问经理审批', severity: 'critical' },
  { type: 'persistence_signal', title: '启动项变更观察', desc: '记录终端侧启动项变化信号，进入主机威胁分析。', button: '触发观察信号', severity: 'high' },
]

const actionMeta: Record<RangeActionType, { title: string; message: string; severity: string; eventType: string; ruleId: string }> = {
  login_failure: { title: '员工门户登录失败', message: '本地演示站点收到错误密码登录尝试', severity: 'medium', eventType: 'auth', ruleId: 'LOCAL_RANGE_LOGIN_FAILURE' },
  sensitive_path: { title: '受限报表路径访问', message: '客户演示站点拦截 /admin/reports 访问', severity: 'high', eventType: 'web', ruleId: 'LOCAL_RANGE_SENSITIVE_PATH' },
  upload_probe: { title: '异常上传入口触发', message: '客户演示站点记录 invoice.exe 上传尝试，未读取文件内容', severity: 'high', eventType: 'web', ruleId: 'LOCAL_RANGE_UPLOAD_PROBE' },
  privilege_boundary: { title: '权限边界访问被拒绝', message: '普通用户访问经理审批接口被拦截', severity: 'critical', eventType: 'access', ruleId: 'LOCAL_RANGE_PRIVILEGE_BOUNDARY' },
  data_query: { title: '业务数据查询观察', message: '本地站点记录脱敏业务查询行为', severity: 'medium', eventType: 'audit', ruleId: 'LOCAL_RANGE_DATA_QUERY' },
  persistence_signal: { title: '启动项变更信号观察', message: '终端侧启动项变化信号进入 SOC 分析', severity: 'high', eventType: 'host', ruleId: 'LOCAL_RANGE_PERSISTENCE_SIGNAL' },
  terminal_command: { title: '本地终端命令观察', message: '用户端终端演练台产生命令遥测', severity: 'medium', eventType: 'terminal', ruleId: 'LOCAL_RANGE_TERMINAL_COMMAND' },
}

const siteAddress = computed(() => window.location.href)
const allowedToolKeys = new Set(['identity', 'network', 'process', 'startup', 'hostname'])
const inspectionSteps = [
  { title: '确认授权', description: '确认当前电脑和只读授权' },
  { title: '选择工具', description: '选择安全团队发布的 active 工具' },
  { title: '运行工具', description: '只提交工具编号，由后端校验执行' },
  { title: '查看记录', description: '结果写入安全日志和本机检查记录' },
]
const currentStepTitle = computed(() => inspectionSteps[Math.min(activeStep.value, inspectionSteps.length - 1)].title)
const currentStepDescription = computed(() => inspectionSteps[Math.min(activeStep.value, inspectionSteps.length - 1)].description)
const selectedCommand = computed(() => terminalCommands.value.find((item) => item.key === selectedCommandKey.value) || terminalCommands.value[0])
const showEnvironmentWarning = computed(() => {
  if (environmentWarningDismissed.value) return false
  const dataRootNeedsCheck = runtimeCompatibility.value && !runtimeCompatibility.value.dataRoot.outsideSourceRoot
  return Boolean(inspectionError.value || dataRootNeedsCheck)
})
const connectionLabel = computed(() => {
  if (terminalApiMode.value === 'real' || snapshotApiMode.value === 'real') return '已连接'
  if (terminalApiMode.value === 'fallback' || snapshotApiMode.value === 'fallback') return '兼容可用'
  return '待检测'
})
const connectionTag = computed(() => {
  if (connectionLabel.value === '已连接') return 'success'
  if (connectionLabel.value === '兼容可用') return 'warning'
  return 'info'
})
const connectionHint = computed(() => {
  if (connectionLabel.value === '已连接') return '检查结果会提交到安全记录。'
  if (connectionLabel.value === '兼容可用') return '后端未配置 active 策略，正在使用后端内置默认策略。'
  return '运行一次检查后会自动更新。'
})
const hasCheckResult = computed(() => terminalLines.value.length > 1 || activityFeed.value.length > 0)
const latestActivity = computed(() => activityFeed.value[0])
const latestCheckTitle = computed(() => latestActivity.value?.title || selectedCommand.value?.label || '待运行')
const latestCheckMessage = computed(() => latestActivity.value?.message || (hasCheckResult.value ? '检查结果已生成，请按需查看技术输出。' : '还没有检查结果。'))
const nextEmployeeAction = computed(() => activityFeed.value.length ? '回到我的电脑查看记录' : '运行检查并提交记录')
const technicalOutputText = computed(() => terminalLines.value.map((line) => `${line.prefix} ${line.text}`).join('\n') || '暂无技术输出。')
const runtimeAdapterHint = computed(() => (
  assetAutoMode.value
    ? '已按当前电脑、浏览器系统、目标系统和安全命令自动适配。'
    : '操作结果会同步给安全团队，可在“我的电脑”查看。'
))

const runtimePlatformLabel = computed(() => {
  const runtime = runtimeCompatibility.value
  if (!runtime) return `${hostOsLabel.value} 本地适配`
  return `${runtime.platform.osFamily} · ${runtime.platform.browserFamily}`
})

const runtimeAdapterDetail = computed(() => {
  const runtime = runtimeCompatibility.value
  if (!runtime) return '后端适配状态待检测，前端按浏览器平台降级。'
  const dataStatus = runtime.dataRoot.environmentRoot && runtime.dataRoot.outsideSourceRoot ? '数据目录已隔离' : '数据目录需检查'
  return `${runtime.adapter} · ${dataStatus}`
})
const runtimeCapabilityRows = computed(() => {
  const runtimeRows = runtimeCompatibility.value?.capabilities || []
  const rows = runtimeRows.length ? runtimeRows : [
    { key: 'route_context', label: '页面重开上下文', status: 'ready', message: 'URL 与浏览器本地上下文保持当前电脑' },
    { key: 'local_terminal_guard', label: '安全终端观察', status: 'configurable', message: `${vmTargetOsLabel.value} 命令集已自动选择` },
    { key: 'vm_console_embed', label: '本地 VM 控制台', status: 'configurable', message: '支持 localhost / 内网控制台地址' },
  ]
  return rows
    .filter((item) => ['route_context', 'local_terminal_guard', 'vm_console_embed', 'data_root_isolation'].includes(item.key))
    .map((item) => item.key === 'local_terminal_guard'
      ? { ...item, message: `${vmTargetOsLabel.value} 目标现场命令集已自动选择` }
      : item)
    .slice(0, 4)
})

onMounted(() => {
  void loadRuntimeCompatibility()
  void loadDetectedAsset()
  void loadTerminalCommands()
})

watch(
  () => vmTargetOsLabel.value,
  () => {
    void loadTerminalCommands()
  },
)

watch(
  terminalCommands,
  (commands) => {
    if (!commands.some((item) => item.key === selectedCommandKey.value)) {
      selectedCommandKey.value = commands[0]?.key || ''
    }
    terminalForm.command = selectedCommand.value?.command || commands[0]?.command || ''
  },
  { immediate: true },
)

async function loadDetectedAsset() {
  const routeIp = typeof route.query.ip === 'string' ? route.query.ip : ''
  const routeHost = typeof route.query.host === 'string' ? route.query.host : ''
  try {
    const loaded = await loadClientAssets()
    const selected = chooseClientAsset(loaded.records, {
      routeIp,
      routeHost,
      currentNames: [authStore.userInfo?.nickname, authStore.userInfo?.username],
    })
    if (!selected) {
      assetAutoMode.value = false
      return
    }
    detectedAsset.value = {
      name: selected.hostname,
      ip: selected.ip,
      osType: selected.osType || hostOsLabel.value,
    }
    assetAutoMode.value = true
  } catch {
    assetAutoMode.value = false
  }
}

async function loadRuntimeCompatibility() {
  runtimeCompatibility.value = await loadClientRuntimeCompatibility()
}

async function loadTerminalCommands() {
  try {
    const response = await listClientLocalCommands(vmTargetOsLabel.value)
    const activeToolboxCommands = response.data.data.filter((item) => allowedToolKeys.has(item.key))
    terminalCommands.value = activeToolboxCommands.map(toTerminalCommand)
    terminalApiMode.value = activeToolboxCommands.some((item) => item.builtInFallback) ? 'fallback' : 'real'
    inspectionError.value = activeToolboxCommands.length ? '' : '后台没有可用的 active 安全工具，请联系安全团队发布策略。'
  } catch {
    terminalCommands.value = []
    selectedCommandKey.value = ''
    terminalApiMode.value = 'unknown'
    inspectionError.value = '检查策略加载失败，请重新检测或联系安全团队。'
  }
}

function toTerminalCommand(item: LocalCheckCommandOption): TerminalCommand {
  return {
    key: item.key,
    label: item.label,
    command: item.command,
    phase: item.phase,
    severity: item.severity,
    description: item.description,
    builtInFallback: item.builtInFallback,
    output: [item.builtInFallback ? '后端正在使用内置默认策略，真实输出以运行结果为准。' : '该检查项已由后台策略中心下发，真实输出以运行结果为准。'],
  }
}

async function recordAction(type: RangeActionType) {
  const meta = actionMeta[type]
  submitting.value = type
  try {
    const now = new Date().toISOString()
    const payload = {
      assetIp: asset.value.ip,
      actionType: type,
      targetName: '客户演示本地站点',
      targetType: 'local_site',
      targetAddress: window.location.href,
      targetScope: '仅限当前本机授权演示站点',
      sessionId: 'LOCAL-RANGE-DEMO',
      sessionName: '客户演示本地站点',
      sessionPhase: phaseOf(type),
      operatorNote: '客户现场演示真实交互产生的本地遥测',
      note: type === 'data_query' ? `脱敏查询关键词：${queryKeyword.value || 'CUST-1024'}` : meta.message,
      linkAlert: true,
    }
    const event = {
      id: `local-range-${type}-${Date.now()}`,
      name: meta.title,
      message: meta.message,
      rule: meta.title,
      rule_id: meta.ruleId,
      severity: meta.severity,
      event_type: meta.eventType,
      src_ip: '127.0.0.1',
      dest_ip: asset.value.ip,
      asset_ip: asset.value.ip,
      asset_name: asset.value.name,
      action_type: type,
      username: loginForm.username,
      query_keyword: type === 'data_query' ? queryKeyword.value || 'CUST-1024' : undefined,
      lab_session_id: 'LOCAL-RANGE-DEMO',
      lab_session_name: '客户演示本地站点',
      lab_session_phase: phaseOf(type),
      operator_note: '客户现场演示真实交互产生的本地遥测',
      lab_target_name: '客户演示本地站点',
      lab_target_type: 'local_site',
      lab_target_address: window.location.href,
      lab_target_scope: '仅限当前本机授权演示站点',
      authorized_lab: true,
      controlled_demo: true,
      simulated: false,
      timestamp: now,
    }
    try {
      const webRunner = getToken() ? submitClientLabEvent : submitLocalDemoLabEvent
      const response = await webRunner(payload)
      const result = response.data.data
      webApiMode.value = 'real'
      portalStatus.value = 'demo'
      pushActivity({
        id: result.event.eventUid,
        eventUid: result.event.eventUid,
        alertId: result.alertId,
        sourceType: result.event.sourceType,
        assetIp: result.event.assetIp || asset.value.ip,
        title: result.event.ruleName || meta.title,
        message: result.message,
        time: now,
      })
      ElMessage.success(result.message)
      return
    } catch {
      webApiMode.value = 'fallback'
      await importCyberFusionEvents({
        sourceType: 'osquery',
        content: JSON.stringify(event),
        linkAlerts: true,
      })
    }
    portalStatus.value = 'demo'
    pushActivity({
      id: event.id,
      eventUid: event.id,
      sourceType: 'osquery',
      assetIp: asset.value.ip,
      title: meta.title,
      message: meta.message,
      time: now,
    })
    ElMessage.success('已通过兼容通道写入 SOC 事件流并联动告警')
  } finally {
    submitting.value = ''
  }
}

function phaseOf(type: RangeActionType) {
  const phases: Record<RangeActionType, string> = {
    login_failure: 'auth_observe',
    sensitive_path: 'access_check',
    upload_probe: 'boundary_check',
    privilege_boundary: 'boundary_check',
    data_query: 'data_observe',
    persistence_signal: 'persistence_observe',
    terminal_command: 'terminal_observe',
  }
  return phases[type]
}

function detectHostOs(): HostOs {
  const nav = navigator as Navigator & { userAgentData?: { platform?: string } }
  const platform = [
    nav.userAgentData?.platform,
    nav.platform,
    nav.userAgent,
  ].filter(Boolean).join(' ').toLowerCase()
  return osFamilyFromText(platform)
}

function osFamilyFromText(value: string): HostOs {
  const platform = value.toLowerCase()
  if (platform.includes('win')) return 'windows'
  if (platform.includes('mac')) return 'macos'
  return 'linux'
}

function checkDescription(key: string) {
  const command = terminalCommands.value.find((item) => item.key === key)
  if (command?.description) return command.description
  const descriptions: Record<string, string> = {
    identity: '确认当前登录用户和权限组。',
    network: '查看当前电脑网络连接状态。',
    process: '查看正在运行的程序列表。',
    startup: '查看开机启动或用户服务项。',
    hostname: '核对电脑名称是否和安全团队记录一致。',
  }
  return descriptions[key] || '执行安全团队预设的只读检查。'
}

function selectCheckItem(key: string) {
  const command = terminalCommands.value.find((item) => item.key === key)
  if (!command) return
  selectedCommandKey.value = key
  terminalForm.command = command.command
  activeStep.value = Math.max(activeStep.value, 1)
}

async function runSelectedCheck() {
  const command = selectedCommand.value
  if (!command) {
    ElMessage.warning('请选择一个检查项')
    return
  }
  if (!toolboxAuthorized.value) {
    activeStep.value = 0
    ElMessage.warning('请先确认只读工具授权说明')
    return
  }
  inspectionError.value = ''
  terminalForm.command = command.command
  activeStep.value = 2
  try {
    await runTerminalCommand()
    latestRunTime.value = new Date().toLocaleString()
    activeStep.value = 3
  } catch {
    inspectionError.value = '检查未完成，请重新检测或查看诊断信息。'
  }
}

async function reloadInspectionEnvironment() {
  inspectionError.value = ''
  environmentWarningDismissed.value = false
  await loadRuntimeCompatibility()
  await loadDetectedAsset()
}

function continueReadOnlyCheck() {
  environmentWarningDismissed.value = true
  activeStep.value = Math.max(activeStep.value, 1)
}

function selectTerminalCommand(command: string) {
  terminalForm.command = command
  workspaceMode.value = 'terminal'
}

function selectTerminalCommandByKey(key: string) {
  const command = terminalCommands.value.find((item) => item.key === key)
  if (command) selectTerminalCommand(command.command)
}

async function unlockVm() {
  if (!vmLoginForm.username.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (!vmLoginForm.password.trim()) {
    loginForm.username = vmLoginForm.username
    await recordAction('login_failure')
    appendTerminalLine('AUTH', `登录失败：${vmLoginForm.username}`, 'blocked')
    ElMessage.warning('请输入本地演示口令')
    return
  }
  vmUnlocked.value = true
  portalStatus.value = 'demo'
  appendTerminalLine('AUTH', `${vmLoginForm.username} 已登录 ${asset.value.name}`, 'success')
  vmLoginForm.password = ''
}

function activateVmConsole() {
  workspaceMode.value = 'vm'
}

function vmConsoleStorageKey() {
  return `cyberfusion_vm_console_url_${asset.value.ip}`
}

function loadVmConsoleUrl() {
  vmConsoleForm.url = localStorage.getItem(vmConsoleStorageKey()) || ''
}

function saveVmConsoleUrl() {
  const safeUrl = safeLocalConsoleUrl(vmConsoleForm.url)
  if (!safeUrl) {
    ElMessage.warning('只允许接入 localhost 或内网 VM 控制台地址')
    return
  }
  vmConsoleForm.url = safeUrl
  localStorage.setItem(vmConsoleStorageKey(), safeUrl)
  workspaceMode.value = 'vm'
  ElMessage.success('本地 VM 控制台地址已保存')
}

function safeLocalConsoleUrl(value: string) {
  if (!value.trim()) return ''
  try {
    const url = new URL(value.trim())
    if (!['http:', 'https:'].includes(url.protocol)) return ''
    const host = url.hostname.toLowerCase()
    const privateHost =
      host === 'localhost'
      || host === '127.0.0.1'
      || host === '::1'
      || host.startsWith('10.')
      || host.startsWith('192.168.')
      || /^172\.(1[6-9]|2\d|3[0-1])\./.test(host)
    return privateHost ? url.toString() : ''
  } catch {
    return ''
  }
}

async function runTerminalCommand() {
  if (!vmUnlocked.value) {
    ElMessage.warning('请先确认本机检查授权')
    return
  }
  const commandText = terminalForm.command.trim()
  if (!commandText) {
    ElMessage.warning('请选择一个检查项')
    return
  }
  const allowed = terminalCommands.value.find((item) => item.key === selectedCommandKey.value)
  if (!allowed) {
    ElMessage.warning('检查策略尚未加载，请重新检测')
    return
  }
  appendTerminalLine('$', `local-demo$ ${allowed.command}`, 'prompt')
  submitting.value = 'terminal_command'
  try {
    try {
      const terminalRunner = getToken() ? runClientTerminalCommand : runLocalDemoTerminalCommand
      const response = await terminalRunner({
        assetIp: asset.value.ip,
        commandKey: allowed.key,
        osType: vmTargetOsLabel.value,
        note: '客户现场演示本机只读检查',
        linkAlert: true,
      })
      const result = response.data.data
      terminalApiMode.value = 'real'
      result.output.forEach((line) => appendTerminalLine('OUT', line, 'output'))
      appendTerminalLine('SOC', `真实本机输出已写入 SOC，告警 ${result.alertId || '-'}`, 'success')
      pushActivity({
        id: result.event.eventUid,
        eventUid: result.event.eventUid,
        alertId: result.alertId,
        sourceType: result.event.sourceType,
        assetIp: result.event.assetIp || asset.value.ip,
        title: `本机检查：${allowed.label}`,
        message: `已完成安全团队预设的只读检查`,
        time: new Date().toISOString(),
      })
      ElMessage.success('本机检查结果已提交')
    } catch {
      terminalApiMode.value = 'unknown'
      inspectionError.value = '检查接口暂不可用，请重新检测或查看诊断信息。'
      appendTerminalLine('SOC', '检查接口暂不可用，未生成本机检查结果。', 'blocked')
      throw new Error('local terminal run failed')
    }
  } finally {
    submitting.value = ''
  }
}

async function runFactSnapshot() {
  if (!vmUnlocked.value) {
    ElMessage.warning('请先确认本机检查授权')
    return
  }
  if (!toolboxAuthorized.value) {
    activeStep.value = 0
    ElMessage.warning('请先确认只读工具授权说明')
    return
  }
  snapshotRunning.value = true
  try {
    const snapshotRunner = getToken() ? runClientSecuritySnapshot : runLocalDemoSecuritySnapshot
    const response = await snapshotRunner({
      assetIp: asset.value.ip,
      osType: vmTargetOsLabel.value,
      note: '本机检查记录采集',
      linkAlert: true,
    })
    const result = response.data.data
    snapshotApiMode.value = getToken() ? 'real' : 'fallback'
    appendSnapshotResult(result)
    pushActivity({
      id: result.snapshotId,
      eventUid: result.event.eventUid,
      alertId: result.alertId,
      sourceType: result.event.sourceType,
      assetIp: result.event.assetIp || asset.value.ip,
      title: result.event.ruleName || '本机检查记录采集',
      message: result.message,
      time: new Date().toISOString(),
    })
    latestRunTime.value = new Date().toLocaleString()
    activeStep.value = 3
    ElMessage.success(result.message)
  } catch {
    snapshotApiMode.value = 'fallback'
    inspectionError.value = '检查记录采集失败，请重新检测或查看诊断信息。'
    appendTerminalLine('SOC', '检查记录接口暂不可用，请检查后端本机采集服务。', 'blocked')
    ElMessage.error('检查记录采集失败')
  } finally {
    snapshotRunning.value = false
  }
}

function appendSnapshotResult(result: ClientSecuritySnapshotResult) {
  appendTerminalLine('SNAP', `${result.snapshotId} · ${result.sections.length} 个事实项已写入 SOC`, 'success')
  result.sections.slice(0, 5).forEach((section) => {
    const preview = section.output.join(' / ').slice(0, 120) || `exit=${section.exitCode}`
    appendTerminalLine(section.key.toUpperCase(), `${section.label}: ${preview}`, section.exitCode === 0 && !section.timeout ? 'output' : 'blocked')
  })
}

function appendTerminalLine(prefix: string, text: string, kind: TerminalLine['kind']) {
  terminalLines.value.push({ id: `${Date.now()}-${terminalLines.value.length}`, prefix, text, kind })
  terminalLines.value = terminalLines.value.slice(-18)
}

function activityStorageKey() {
  return `${ACTIVITY_STORAGE_PREFIX}_${asset.value.ip}`
}

function loadActivityFeed() {
  try {
    const stored = JSON.parse(localStorage.getItem(activityStorageKey()) || '[]') as ActivityItem[]
    activityFeed.value = Array.isArray(stored) ? stored.slice(0, 8) : []
    if (activityFeed.value.length) portalStatus.value = 'demo'
  } catch {
    activityFeed.value = []
  }
}

function persistActivityFeed() {
  localStorage.setItem(activityStorageKey(), JSON.stringify(activityFeed.value.slice(0, 8)))
}

function pushActivity(item: ActivityItem) {
  activityFeed.value = [item, ...activityFeed.value].slice(0, 8)
  portalStatus.value = 'demo'
  persistActivityFeed()
}

function clearActivityFeed() {
  activityFeed.value = []
  persistActivityFeed()
  ElMessage.success('现场遥测记录已清空')
}

function resetLogin() {
  loginForm.username = 'demo.user'
  loginForm.password = ''
  portalStatus.value = 'locked'
}

function viewEvidence(keyword?: string) {
  router.push({
    path: '/soc/external-events',
    query: {
      sourceType: 'osquery',
      keyword: keyword || asset.value.ip,
      assetIp: asset.value.ip,
    },
  })
}

function viewActivityEvidence(item: ActivityItem) {
  const eventUid = item.eventUid || item.id
  router.push({
    path: '/soc/external-events',
    query: {
      sourceType: item.sourceType || 'osquery',
      keyword: eventUid,
      openEventUid: eventUid,
      assetIp: item.assetIp || asset.value.ip,
    },
  })
}

function viewAlerts(keyword?: string) {
  router.push({
    path: '/soc/alerts',
    query: {
      sourceType: 'osquery',
      keyword: keyword || asset.value.ip,
      assetIp: asset.value.ip,
    },
  })
}

function viewActivityAlerts(item: ActivityItem) {
  router.push({
    path: '/soc/alerts',
    query: {
      sourceType: item.sourceType || 'osquery',
      keyword: item.eventUid || item.title || item.assetIp || asset.value.ip,
      openAlertId: item.alertId ? String(item.alertId) : undefined,
      assetIp: item.assetIp || asset.value.ip,
    },
  })
}

function formatTime(value: string) {
  return value.replace('T', ' ').slice(11, 19)
}
</script>

<style scoped>
.local-check-page {
  display: grid;
  gap: 18px;
}

.local-check-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  align-items: end;
  padding: 24px;
  border: 1px solid rgba(180, 187, 198, 0.46);
  border-radius: 16px;
  background:
    radial-gradient(circle at 12% 8%, rgba(246, 179, 108, 0.2), transparent 30%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.96), rgba(246, 249, 250, 0.9) 58%, rgba(255, 247, 238, 0.82));
  box-shadow: var(--soc-shadow-soft), var(--soc-glass-highlight);
}

.local-check-hero h1 {
  margin: 4px 0 8px;
  color: var(--soc-text);
  font-size: 28px;
  letter-spacing: 0;
}

.local-check-hero p {
  max-width: 760px;
  margin: 0;
  color: var(--soc-text-muted);
  font-size: 14px;
  line-height: 1.8;
}

.local-device-strip {
  display: flex;
  max-width: 620px;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.local-device-strip span {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 8px 10px;
  border: 1px solid rgba(180, 187, 198, 0.44);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--soc-text-muted);
  font-size: 12px;
}

.local-device-strip b {
  color: var(--soc-text);
}

.local-check-error {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  padding: 16px;
  border: 1px solid rgba(212, 147, 74, 0.42);
  border-radius: 14px;
  background: rgba(255, 248, 238, 0.84);
}

.local-check-error div:first-child {
  display: grid;
  gap: 4px;
}

.local-check-error span {
  color: var(--soc-warm-strong);
  font-size: 11px;
  font-weight: 760;
}

.local-check-error strong {
  color: var(--soc-text);
  font-size: 15px;
}

.local-check-error p {
  margin: 0;
  color: var(--soc-text-muted);
  font-size: 13px;
}

.local-check-error-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.local-check-shell {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.check-stepper-card,
.check-focus-card,
.check-result-card {
  border: 1px solid rgba(180, 187, 198, 0.46);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: var(--soc-shadow-soft);
}

.check-stepper-card {
  position: sticky;
  top: 16px;
  padding: 18px 16px;
}

.check-workspace {
  display: grid;
  gap: 16px;
}

.check-focus-card,
.check-result-card {
  padding: 18px;
}

.device-confirm-grid,
.result-summary-grid,
.diagnostic-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.device-confirm-grid article,
.result-summary-grid article,
.toolbox-authorization {
  display: grid;
  gap: 6px;
  min-height: 112px;
  padding: 14px;
  border: 1px solid rgba(180, 187, 198, 0.38);
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.86), rgba(248, 250, 251, 0.72));
}

.device-confirm-grid span,
.result-summary-grid span,
.toolbox-authorization span,
.selected-check-panel span,
.check-option-card span {
  color: var(--soc-text-muted);
  font-size: 12px;
}

.device-confirm-grid strong,
.result-summary-grid strong,
.toolbox-authorization strong,
.selected-check-panel strong,
.check-option-card strong {
  color: var(--soc-text);
  font-size: 15px;
  line-height: 1.35;
}

.device-confirm-grid em,
.result-summary-grid em,
.selected-check-panel em,
.check-option-card em {
  color: var(--soc-text-muted);
  font-size: 12px;
  font-style: normal;
  line-height: 1.6;
}

.toolbox-authorization {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  min-height: auto;
  margin-top: 14px;
  border-color: rgba(212, 147, 74, 0.36);
  background:
    linear-gradient(135deg, rgba(255, 248, 238, 0.86), rgba(255, 255, 255, 0.82));
}

.toolbox-authorization div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.check-card-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.check-option-card {
  display: grid;
  gap: 7px;
  min-height: 150px;
  padding: 14px;
  border: 1px solid rgba(180, 187, 198, 0.42);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.72);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.check-option-card:hover,
.check-option-card.active {
  border-color: rgba(212, 147, 74, 0.68);
  box-shadow: 0 12px 28px rgba(121, 91, 50, 0.1);
  transform: translateY(-1px);
}

.check-option-card small {
  overflow: hidden;
  padding-top: 6px;
  border-top: 1px solid rgba(180, 187, 198, 0.32);
  color: var(--soc-text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.check-option-card i {
  width: fit-content;
  padding: 3px 7px;
  border-radius: 999px;
  background: rgba(212, 147, 74, 0.12);
  color: #a35d22;
  font-size: 11px;
  font-style: normal;
}

.selected-check-panel {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
  padding: 14px;
  border: 1px solid rgba(212, 147, 74, 0.36);
  border-radius: 14px;
  background: rgba(255, 248, 238, 0.76);
}

.selected-check-panel div {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.result-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 14px;
}

.diagnostic-stack,
.technical-output-stack {
  display: grid;
  gap: 14px;
}

.diagnostic-grid {
  padding: 12px;
  border: 1px solid rgba(180, 187, 198, 0.38);
  border-radius: 12px;
  background: rgba(248, 250, 251, 0.78);
}

.diagnostic-grid span {
  color: var(--soc-text-muted);
  font-size: 12px;
}

.diagnostic-grid strong {
  overflow-wrap: anywhere;
  color: var(--soc-text);
  font-size: 12px;
}

.diagnostic-list {
  display: grid;
  gap: 10px;
}

.diagnostic-list article {
  display: grid;
  gap: 4px;
  padding: 12px;
  border: 1px solid rgba(180, 187, 198, 0.34);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.72);
}

.diagnostic-list strong {
  color: var(--soc-text);
}

.diagnostic-list span {
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.technical-output-stack pre {
  overflow: auto;
  max-height: 420px;
  margin: 0;
  padding: 14px;
  border: 1px solid rgba(180, 187, 198, 0.42);
  border-radius: 12px;
  background: rgba(248, 250, 251, 0.94);
  color: var(--soc-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.65;
  white-space: pre-wrap;
}

.range-page {
  display: grid;
  gap: 14px;
}

.range-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: end;
  padding: 18px;
  border: 1px solid var(--soc-border);
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.92), rgba(255, 249, 241, 0.78) 54%, rgba(239, 244, 245, 0.78)),
    var(--soc-glass);
  box-shadow: var(--soc-shadow-soft), var(--soc-glass-highlight);
  backdrop-filter: blur(22px) saturate(1.12);
}

.hero-copy {
  display: grid;
  gap: 5px;
}

.hero-copy h1 {
  margin: 0;
  color: var(--soc-text);
  font-size: 24px;
  letter-spacing: 0;
}

.hero-copy p {
  max-width: 760px;
  margin: 0;
  color: var(--soc-text-muted);
  font-size: 13px;
  line-height: 1.7;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.range-grid {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr) 310px;
  gap: 14px;
  align-items: start;
}

.target-panel,
.query-panel,
.activity-panel {
  padding: 16px;
}

.vm-browser-panel {
  overflow: hidden;
  border: 1px solid rgba(152, 145, 134, 0.42);
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(247, 248, 248, 0.92), rgba(252, 248, 242, 0.9));
  box-shadow: var(--soc-shadow-soft), var(--soc-glass-highlight);
}

.browser-frame {
  display: grid;
}

.browser-titlebar {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 10px 12px;
  border-bottom: 1px solid rgba(152, 145, 134, 0.32);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.88), rgba(244, 240, 234, 0.84));
}

.browser-address {
  min-width: 0;
  padding: 7px 10px;
  border: 1px solid rgba(152, 145, 134, 0.28);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
}

.browser-address span {
  display: block;
  overflow: hidden;
  color: var(--soc-text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.browser-page {
  padding: 16px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.72), rgba(246, 249, 249, 0.7)),
    rgba(255, 255, 255, 0.56);
}

.panel-title,
.portal-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-title div,
.portal-head div {
  display: grid;
  gap: 3px;
}

.panel-title strong,
.portal-head strong {
  color: var(--soc-text);
  font-size: 15px;
}

.panel-title span,
.portal-head span {
  color: var(--soc-text-muted);
  font-size: 12px;
}

.target-card,
.boundary-note,
.query-result,
.activity-list article {
  display: grid;
  gap: 5px;
  padding: 12px;
  border: 1px solid rgba(179, 173, 163, 0.42);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.56);
}

.target-card + .target-card,
.boundary-note {
  margin-top: 10px;
}

.target-card span,
.target-card em,
.boundary-note span,
.query-result span,
.query-result em {
  color: var(--soc-text-muted);
  font-size: 12px;
  font-style: normal;
  line-height: 1.5;
}

.target-card strong,
.boundary-note strong,
.query-result strong {
  overflow-wrap: anywhere;
  color: var(--soc-text);
  font-size: 13px;
}

.boundary-note {
  border-color: rgba(212, 147, 74, 0.32);
  background: rgba(255, 248, 238, 0.76);
}

.demo-route-card {
  display: grid;
  gap: 10px;
  margin-top: 10px;
  padding: 12px;
  border: 1px solid rgba(49, 182, 198, 0.24);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.62);
}

.demo-route-card strong {
  color: var(--soc-text);
  font-size: 13px;
}

.demo-route-card ol {
  display: grid;
  gap: 7px;
  margin: 0;
  padding-left: 18px;
  color: var(--soc-text-muted);
  font-size: 12px;
}

.demo-route-card li.done {
  color: var(--soc-warm-strong);
  font-weight: 760;
}

.demo-route-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.demo-surface {
  display: grid;
  gap: 14px;
}

.portal-body {
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  gap: 14px;
}

.login-box {
  padding: 14px;
  border: 1px solid rgba(179, 173, 163, 0.42);
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.72), rgba(250, 247, 241, 0.7));
}

.box-title {
  display: grid;
  gap: 4px;
  margin-bottom: 12px;
}

.box-title strong {
  color: var(--soc-text);
}

.box-title span {
  color: var(--soc-text-muted);
  font-size: 12px;
}

.form-actions,
.query-row {
  display: flex;
  gap: 8px;
}

.portal-widgets {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.portal-widgets article,
.workflow-card {
  display: grid;
  gap: 7px;
  padding: 14px;
  border: 1px solid rgba(179, 173, 163, 0.42);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.58);
}

.portal-widgets span,
.portal-widgets em,
.workflow-card span,
.activity-list span,
.activity-list em {
  color: var(--soc-text-muted);
  font-size: 12px;
  font-style: normal;
  line-height: 1.5;
}

.portal-widgets strong {
  color: var(--soc-warm-strong);
  font-size: 24px;
}

.workflow-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.workflow-card {
  align-content: space-between;
  min-height: 154px;
}

.workflow-card strong,
.activity-list strong {
  color: var(--soc-text);
  font-size: 14px;
}

.query-row {
  margin-bottom: 10px;
}

.activity-list {
  display: grid;
  gap: 9px;
}

.activity-list article {
  border-color: rgba(49, 182, 198, 0.2);
}

.activity-list div {
  display: grid;
  gap: 3px;
}

.evidence-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}

.evidence-meta span {
  overflow: hidden;
  padding: 5px 7px;
  border: 1px solid rgba(179, 173, 163, 0.34);
  border-radius: 7px;
  background: rgba(255, 255, 255, 0.54);
  color: var(--soc-text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-list footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.activity-links {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 4px;
}

.local-vm-stage {
  overflow: hidden;
  border: 1px solid rgba(128, 121, 111, 0.5);
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(251, 250, 247, 0.94), rgba(237, 242, 241, 0.9)),
    rgba(255, 255, 255, 0.72);
  box-shadow: 0 26px 56px rgba(64, 70, 82, 0.16), var(--soc-glass-highlight);
}

.vm-frame {
  display: grid;
  grid-template-rows: auto minmax(560px, 1fr) auto;
  min-height: 650px;
}

.vm-titlebar {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  min-height: 48px;
  padding: 10px 14px;
  border-bottom: 1px solid rgba(152, 145, 134, 0.34);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.92), rgba(244, 240, 234, 0.9)),
    rgba(255, 255, 255, 0.72);
}

.window-controls {
  display: flex;
  gap: 6px;
}

.window-controls i {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: #e97171;
}

.window-controls i:nth-child(2) {
  background: #e7b85a;
}

.window-controls i:nth-child(3) {
  background: #57b879;
}

.vm-title {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.vm-title strong {
  color: var(--soc-text);
  font-size: 15px;
}

.vm-title span {
  color: var(--soc-text-muted);
  font-size: 12px;
}

.mode-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}

.vm-lock-screen {
  display: grid;
  min-height: 560px;
  place-items: center;
  padding: 32px;
  background:
    radial-gradient(circle at 50% 24%, rgba(212, 147, 74, 0.18), transparent 30%),
    linear-gradient(135deg, rgba(246, 248, 247, 0.94), rgba(229, 236, 235, 0.86)),
    repeating-linear-gradient(90deg, rgba(52, 64, 84, 0.035) 0 1px, transparent 1px 80px),
    repeating-linear-gradient(0deg, rgba(52, 64, 84, 0.028) 0 1px, transparent 1px 80px);
}

.lock-panel {
  display: grid;
  width: min(360px, 100%);
  justify-items: center;
  gap: 12px;
  padding: 24px;
  border: 1px solid rgba(152, 145, 134, 0.32);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.74);
  box-shadow: 0 22px 48px rgba(64, 70, 82, 0.14);
}

.lock-avatar {
  display: grid;
  width: 64px;
  height: 64px;
  place-items: center;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--soc-warm), #48a6b5);
  color: #fff;
  font-weight: 850;
}

.lock-panel strong {
  color: var(--soc-text);
  font-size: 20px;
}

.lock-panel span,
.lock-panel em {
  color: var(--soc-text-muted);
  font-size: 12px;
  font-style: normal;
  text-align: center;
}

.vm-login-form {
  display: grid;
  width: 100%;
  gap: 10px;
}

.vm-desktop {
  display: grid;
  grid-template-columns: 218px minmax(0, 1fr);
  min-height: 560px;
  background:
    radial-gradient(circle at 16% 14%, rgba(212, 147, 74, 0.16), transparent 28%),
    linear-gradient(135deg, rgba(246, 248, 247, 0.9), rgba(229, 236, 235, 0.82)),
    repeating-linear-gradient(90deg, rgba(52, 64, 84, 0.035) 0 1px, transparent 1px 80px),
    repeating-linear-gradient(0deg, rgba(52, 64, 84, 0.028) 0 1px, transparent 1px 80px);
}

.vm-sidebar {
  display: grid;
  align-content: start;
  gap: 12px;
  padding: 16px 14px;
  border-right: 1px solid rgba(152, 145, 134, 0.28);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.46), rgba(255, 248, 238, 0.32));
}

.vm-host-card,
.vm-adapter-card,
.desktop-icons,
.vm-shortcuts {
  display: grid;
  gap: 7px;
}

.vm-host-card,
.vm-adapter-card {
  padding: 12px;
  border: 1px solid rgba(212, 147, 74, 0.34);
  border-radius: 8px;
  background: rgba(255, 248, 238, 0.84);
  box-shadow: 0 10px 24px rgba(84, 65, 44, 0.08);
}

.vm-adapter-card {
  border-color: rgba(49, 182, 198, 0.26);
  background: rgba(239, 248, 248, 0.72);
}

.vm-host-card span,
.vm-host-card small,
.vm-adapter-card span,
.vm-adapter-card em,
.vm-adapter-card small,
.vm-shortcuts > span,
.vm-host-card em {
  color: var(--soc-text-muted);
  font-size: 12px;
  font-style: normal;
  line-height: 1.5;
}

.vm-host-card strong,
.vm-adapter-card strong {
  color: var(--soc-text);
  font-size: 15px;
}

.vm-host-card small,
.vm-adapter-card small {
  display: block;
}

.adapter-check-list {
  display: grid;
  gap: 6px;
  margin-top: 8px;
}

.adapter-check-list span {
  display: grid;
  gap: 3px;
  padding: 7px;
  border: 1px solid rgba(179, 173, 163, 0.32);
  border-radius: 7px;
  background: rgba(255, 255, 255, 0.5);
}

.adapter-check-list b {
  color: var(--soc-text);
  font-size: 12px;
}

.adapter-check-list b.cap-ready {
  color: #16845b;
}

.adapter-check-list b.cap-warning {
  color: #bf6b25;
}

.adapter-check-list b.cap-configurable {
  color: #2c6ba4;
}

.adapter-check-list em {
  color: var(--soc-text-muted);
  font-size: 11px;
  font-style: normal;
  line-height: 1.45;
}

.desktop-icons {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.desktop-icons button {
  display: grid;
  justify-items: center;
  gap: 5px;
  min-width: 0;
  padding: 8px 4px;
  border: 1px solid rgba(152, 145, 134, 0.24);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.42);
  color: var(--soc-text);
  cursor: pointer;
}

.desktop-icons button:hover {
  border-color: rgba(212, 147, 74, 0.44);
  background: rgba(255, 248, 238, 0.72);
}

.desktop-icons span {
  display: grid;
  place-items: center;
  width: 36px;
  height: 32px;
  border: 1px solid rgba(49, 182, 198, 0.26);
  border-radius: 7px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.88), rgba(237, 246, 247, 0.72));
  color: #0f7590;
  font-size: 11px;
  font-weight: 800;
}

.desktop-icons strong {
  overflow: hidden;
  max-width: 100%;
  color: var(--soc-text-muted);
  font-size: 11px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.vm-shortcuts button {
  display: grid;
  gap: 3px;
  padding: 9px 10px;
  border: 1px solid rgba(152, 145, 134, 0.18);
  border-radius: 7px;
  background: rgba(255, 255, 255, 0.54);
  color: var(--soc-text);
  text-align: left;
  cursor: pointer;
}

.vm-shortcuts button.active,
.vm-shortcuts button:hover {
  border-color: rgba(212, 147, 74, 0.48);
  background: rgba(255, 248, 238, 0.88);
  box-shadow: inset 3px 0 0 var(--soc-warm);
}

.vm-shortcuts strong {
  font-size: 13px;
}

.vm-shortcuts em {
  overflow-wrap: anywhere;
  color: var(--soc-text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  font-style: normal;
}

.vm-workspace {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  align-content: stretch;
  gap: 12px;
  min-width: 0;
  padding: 16px;
}

.vm-session-ribbon {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
  padding: 9px 12px;
  border: 1px solid rgba(152, 145, 134, 0.26);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.5);
  box-shadow: 0 10px 22px rgba(64, 70, 82, 0.08);
}

.vm-session-ribbon span,
.vm-session-ribbon em {
  color: var(--soc-text-muted);
  font-size: 12px;
  font-style: normal;
}

.vm-session-ribbon span {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-weight: 800;
}

.vm-session-ribbon strong {
  color: var(--soc-text);
  font-size: 13px;
}

.vm-console-window {
  display: grid;
  overflow: hidden;
  min-height: 500px;
  border: 1px solid rgba(81, 86, 96, 0.28);
  border-radius: 8px;
  background: rgba(252, 251, 248, 0.94);
  box-shadow: 0 24px 48px rgba(54, 59, 68, 0.16);
}

.vm-console-window iframe {
  width: 100%;
  min-height: 500px;
  border: 0;
  background: #fff;
}

.vm-console-empty {
  display: grid;
  place-items: center;
  align-content: center;
  gap: 12px;
  padding: 24px;
  text-align: center;
}

.vm-console-empty strong {
  color: var(--soc-text);
  font-size: 18px;
}

.vm-console-empty span {
  color: var(--soc-text-muted);
  font-size: 12px;
}

.vm-console-form {
  display: grid;
  grid-template-columns: minmax(280px, 1fr) auto auto;
  gap: 8px;
  width: min(720px, 100%);
}

.terminal-window {
  display: grid;
  grid-template-rows: auto minmax(390px, 1fr) auto;
  overflow: hidden;
  min-height: 500px;
  border: 1px solid rgba(81, 86, 96, 0.28);
  border-radius: 8px;
  background: #fcfbf8;
  box-shadow: 0 24px 48px rgba(54, 59, 68, 0.16);
}

.terminal-window-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 48px;
  padding: 9px 12px;
  border-bottom: 1px solid rgba(81, 86, 96, 0.16);
  background:
    linear-gradient(135deg, #fdfcf8, #eef1f0),
    rgba(255, 255, 255, 0.9);
}

.terminal-window-head span,
.terminal-window-head em {
  color: var(--soc-text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  font-style: normal;
}

.terminal-window-head div {
  display: grid;
  gap: 2px;
}

.terminal-window-head strong {
  color: var(--soc-text);
  font-size: 13px;
}

.terminal-window-head em {
  color: #047857;
  font-weight: 760;
}

.terminal-screen {
  display: grid;
  align-content: start;
  overflow: auto;
  padding: 16px;
  background:
    linear-gradient(135deg, rgba(255, 254, 250, 0.95), rgba(244, 247, 247, 0.92)),
    #fff;
}

.terminal-line {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr);
  gap: 8px;
  align-items: start;
  padding: 4px 0;
  color: var(--soc-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.55;
}

.terminal-line span {
  color: var(--soc-text-muted);
  font-weight: 760;
}

.terminal-line strong {
  overflow-wrap: anywhere;
  font-weight: 620;
}

.terminal-line.blocked strong {
  color: #b54708;
}

.terminal-line.success strong {
  color: #047857;
}

.terminal-input-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  padding: 11px 12px;
  border-top: 1px solid rgba(81, 86, 96, 0.16);
  background: rgba(255, 255, 255, 0.88);
}

.terminal-input-row > span {
  color: var(--soc-text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  white-space: nowrap;
}

.vm-taskbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 42px;
  padding: 9px 14px;
  border-top: 1px solid rgba(152, 145, 134, 0.3);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.88), rgba(244, 240, 234, 0.82));
}

.vm-taskbar span,
.vm-taskbar em {
  color: var(--soc-text-muted);
  font-size: 12px;
  font-style: normal;
}

.vm-taskbar strong {
  color: var(--soc-text);
  font-size: 13px;
}

@media (max-width: 1380px) {
  .check-card-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .range-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1180px) {
  .local-check-hero,
  .local-check-error,
  .local-check-shell {
    grid-template-columns: 1fr;
  }

  .local-device-strip,
  .local-check-error-actions {
    justify-content: flex-start;
  }

  .workflow-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .evidence-meta {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .local-check-hero,
  .check-focus-card,
  .check-result-card {
    padding: 16px;
  }

  .check-card-grid,
  .device-confirm-grid,
  .result-summary-grid,
  .diagnostic-grid,
  .toolbox-authorization {
    grid-template-columns: 1fr;
  }

  .selected-check-panel,
  .result-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .range-hero,
  .browser-titlebar,
  .portal-body,
  .portal-widgets,
  .workflow-grid,
  .vm-titlebar,
  .vm-desktop,
  .terminal-input-row,
  .vm-console-form,
  .vm-taskbar,
  .vm-session-ribbon {
    grid-template-columns: 1fr;
  }

  .form-actions,
  .query-row,
  .activity-list footer,
  .mode-tags,
  .vm-taskbar,
  .vm-session-ribbon {
    flex-direction: column;
    align-items: stretch;
  }

  .activity-links {
    justify-content: flex-start;
  }
}
</style>
