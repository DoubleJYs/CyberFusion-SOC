<template>
  <div class="client-workbench keeper-home">
    <section v-if="error" class="recoverable-error">
      <div>
        <strong>安全管家暂时无法加载</strong>
        <p>可能是后端未启动、数据库未初始化或当前电脑未绑定。你可以重试，或使用离线演示数据继续查看页面。</p>
      </div>
      <div class="error-actions">
        <el-button type="primary" :loading="loading" @click="loadWorkbench">重试</el-button>
        <el-button @click="useOfflineData">使用离线演示数据</el-button>
        <el-button text @click="showDiagnostics = !showDiagnostics">查看诊断</el-button>
      </div>
      <pre v-if="showDiagnostics">{{ diagnosticsText }}</pre>
    </section>

    <section v-if="dataNotices.length" class="soft-config-notice">
      <div>
        <strong>已使用本机作为数据来源</strong>
        <p>{{ dataNotices.join('；') }}</p>
      </div>
      <div class="error-actions">
        <el-button size="small" :loading="loading" @click="loadWorkbench">重新加载</el-button>
        <el-button size="small" text @click="showDiagnostics = !showDiagnostics">查看诊断</el-button>
      </div>
      <pre v-if="showDiagnostics">{{ diagnosticsText }}</pre>
    </section>

    <section v-if="showServiceConsent" class="keeper-consent-panel">
      <div>
        <span class="soc-page-kicker">安全管家服务</span>
        <h2>是否接受 CyberFusion 安全管家保护这台电脑？</h2>
        <p>接受后，员工端会绑定当前本机资产，并优先展示这台电脑的体检、待办、日志和只读检查结果。</p>
      </div>
      <div class="consent-device">
        <strong>{{ localCandidate?.hostname || '当前电脑' }}</strong>
        <span>{{ localCandidate?.ip || '等待识别' }} · {{ localCandidate?.osType || '待识别' }}</span>
        <em>不会安装 Agent，不执行任意命令，只使用后台已发布的只读检查策略。</em>
      </div>
      <div class="consent-actions">
        <el-button type="primary" size="large" :disabled="!localCandidate" @click="acceptKeeperService">
          接受并启用安全管家
        </el-button>
        <el-button @click="useOfflineData">暂时使用演示数据</el-button>
      </div>
    </section>

    <section class="keeper-hero">
      <div class="keeper-copy">
        <span class="soc-page-kicker">电脑安全助手</span>
        <h1>CyberFusion 安全管家</h1>
        <p>这个页面帮你判断当前电脑是否安全、为什么有风险，以及下一步应该点哪里。</p>
        <div class="device-strip">
          <span>{{ deviceName }}</span>
          <span>{{ assetIp || '-' }}</span>
          <span>{{ osType }}</span>
        </div>
      </div>

      <article class="keeper-status-card" :class="`keeper-status-${statusKey}`">
        <div class="status-heading">
          <span>当前状态</span>
          <el-tag :type="statusTagType" effect="plain">{{ statusLabel }}</el-tag>
        </div>
        <strong>{{ statusTitle }}</strong>
        <p>{{ statusReason }}</p>

        <div class="status-metric-grid">
          <div>
            <span>安全评分</span>
            <b>{{ riskScore }} / 100</b>
          </div>
          <div>
            <span>待办数量</span>
            <b>{{ taskCount }} 项</b>
          </div>
          <div>
            <span>最近一次体检</span>
            <b>{{ lastCheckupLabel }}</b>
          </div>
        </div>

        <div class="hero-action-row">
          <el-button type="primary" size="large" :loading="checkupLoading || loading" @click="runCheckup">
            一键体检
          </el-button>
          <el-button text @click="openRepair">查看修复建议</el-button>
          <el-button text @click="openLogs">查看安全日志</el-button>
        </div>
      </article>
    </section>

    <section class="keeper-entry-grid" aria-label="安全管家入口">
      <button class="keeper-entry-card keeper-entry-check" type="button" @click="runCheckup">
        <span class="entry-icon">✓</span>
        <strong>一键体检</strong>
        <p>刷新当前电脑安全状态，查看是否有新的提醒。</p>
        <em>{{ checkupLoading ? '体检中' : '立即开始' }}</em>
      </button>

      <button class="keeper-entry-card" type="button" @click="openRepair">
        <span class="entry-icon">!</span>
        <strong>风险修复</strong>
        <p>查看安全团队给出的待办和修复建议。</p>
        <em>{{ taskCount }} 项待处理</em>
      </button>

      <button class="keeper-entry-card" type="button" @click="openTools">
        <span class="entry-icon">○</span>
        <strong>安全工具箱</strong>
        <p>打开安全团队发布的只读工具，不会修改你的电脑。</p>
        <em>只读工具</em>
      </button>

      <button class="keeper-entry-card" type="button" @click="openLogs">
        <span class="entry-icon">≡</span>
        <strong>安全日志</strong>
        <p>查看和当前电脑相关的最近安全记录。</p>
        <em>{{ totalRecordCount }} 条记录</em>
      </button>
    </section>

    <section class="keeper-detail-grid">
      <article class="soc-panel action-panel">
        <div class="panel-title-row">
          <div>
            <h2>为什么有风险</h2>
            <p>只展示和当前电脑有关的主要原因</p>
          </div>
          <el-tag effect="plain">{{ nextActions.length }} 条建议</el-tag>
        </div>

        <div class="action-list">
          <article v-for="item in nextActions" :key="item.title" :class="`action-item action-${item.level}`">
            <strong>{{ item.title }}</strong>
            <p>{{ item.description }}</p>
            <span>{{ item.countText }}</span>
          </article>
        </div>
      </article>

      <article ref="recordsSection" class="soc-panel records-panel">
        <div class="panel-title-row">
          <div>
            <h2>安全日志</h2>
            <p>只展示和当前电脑有关的最近记录</p>
          </div>
          <el-tag effect="plain">{{ recentRecords.length }} / {{ totalRecordCount }} 条</el-tag>
        </div>

        <div class="record-list">
          <article v-for="item in recentRecords" :key="item.id" class="record-item">
            <div>
              <strong>{{ item.title }}</strong>
              <p>{{ item.description }}</p>
            </div>
            <span>{{ formatTime(item.time) }}</span>
          </article>
        </div>
        <el-empty v-if="!recentRecords.length" description="暂无安全日志" :image-size="80" />
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  clientDeviceRiskProfile,
  listClientTasks,
  listClientNextActions,
  listSecurityKeeperCheckups,
  runClientSecuritySnapshot,
  runSecurityKeeperCheckup,
  type AssetItem,
  type AssetRiskProfile,
  type ClientNextAction,
  type ClientDeviceProfile,
  type ClientEvidenceItem,
  type SecurityKeeperCheckupResult,
  type TicketTaskItem,
} from '@/api/soc'
import {
  buildClientDeviceRouteQuery,
  buildEmptyClientProfile,
  chooseClientAsset,
  acceptSecurityKeeperService,
  findClientLocalAsset,
  hasAcceptedSecurityKeeperService,
  isDemoClientContext,
  loadClientAssets,
  loadClientProfile,
} from '@/composables/useClientDeviceContext'

interface ActionItem {
  title: string
  description: string
  countText: string
  level: 'danger' | 'warning' | 'info'
}

interface RecordItem {
  id: string
  title: string
  description: string
  time?: string
}

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const checkupLoading = ref(false)
const error = ref('')
const dataNotices = ref<string[]>([])
const diagnosticLines = ref<string[]>([])
const showDiagnostics = ref(false)
const selectedAsset = ref<AssetItem>()
const localCandidate = ref<AssetItem>()
const serviceAccepted = ref(hasAcceptedSecurityKeeperService())
const profile = ref<ClientDeviceProfile>()
const riskProfile = ref<AssetRiskProfile>()
const checkupResult = ref<SecurityKeeperCheckupResult>()
const employeeTasks = ref<TicketTaskItem[]>([])
const backendNextActions = ref<ClientNextAction[]>([])
const recordsSection = ref<HTMLElement>()
const lastCheckupAt = ref('')

const assetIp = computed(() => selectedAsset.value?.ip || profile.value?.asset.ip || (typeof route.query.ip === 'string' ? route.query.ip : ''))
const deviceName = computed(() => selectedAsset.value?.hostname || profile.value?.asset.hostname || (typeof route.query.host === 'string' ? route.query.host : '当前电脑'))
const osType = computed(() => selectedAsset.value?.osType || profile.value?.asset.osType || (typeof route.query.os === 'string' ? route.query.os : 'Linux'))

const openAlerts = computed(() => profile.value?.metrics.alerts || 0)
const openVulnerabilities = computed(() => profile.value?.metrics.openVulnerabilities || 0)
const failedBaselines = computed(() => profile.value?.metrics.failedBaselines || 0)
const unreviewedFim = computed(() => profile.value?.metrics.pendingFileIntegrity || 0)

const riskScore = computed(() => checkupResult.value?.checkup.score ?? riskProfile.value?.snapshot?.score ?? profile.value?.asset.riskScore ?? profile.value?.metrics.riskScore ?? Math.min(100, openAlerts.value * 3 + openVulnerabilities.value * 5 + failedBaselines.value * 4 + unreviewedFim.value * 2))
const pendingEmployeeTasks = computed(() => employeeTasks.value.filter((task) => !['confirmed', 'completed', 'skipped'].includes(task.status)))
const highestPriorityTask = computed(() => pendingEmployeeTasks.value[0])
const taskCount = computed(() => pendingEmployeeTasks.value.length)

const statusKey = computed(() => {
  if (checkupResult.value?.checkup.status) return checkupResult.value.checkup.status
  if (riskScore.value >= 80 || openAlerts.value >= 10) return 'serious'
  if (riskScore.value >= 40 || taskCount.value > 0) return 'attention'
  return 'safe'
})

const statusTitle = computed(() => statusKey.value === 'serious' ? '严重风险' : statusKey.value === 'attention' ? '需要注意' : '安全')
const statusLabel = computed(() => statusTitle.value)
const statusTagType = computed(() => statusKey.value === 'serious' ? 'danger' : statusKey.value === 'attention' ? 'warning' : 'success')
const statusReason = computed(() => {
  if (checkupResult.value?.checkup.summary) return checkupResult.value.checkup.summary
  if (riskProfile.value?.statusReason) return riskProfile.value.statusReason
  if (riskProfile.value?.recommendationSummary) return riskProfile.value.recommendationSummary
  if (openAlerts.value > 0) return '当前电脑存在需要确认的安全提醒。请查看修复建议，必要时联系管理员。'
  if (openVulnerabilities.value > 0) return '当前电脑存在软件或网页风险，安全团队会安排处理。'
  if (failedBaselines.value > 0) return '当前电脑有配置项需要复核。'
  return '当前没有需要立即处理的问题。'
})
const lastCheckupLabel = computed(() => checkupResult.value?.checkup.checkedAt ? formatTime(checkupResult.value.checkup.checkedAt) : lastCheckupAt.value ? formatTime(lastCheckupAt.value) : '尚未体检')

const riskFactorActions = computed<ActionItem[]>(() => {
  return (riskProfile.value?.factors || [])
    .filter((factor) => factor.factorScore > 0)
    .slice(0, 4)
    .map((factor) => {
      const count = factor.factorCount || 1
      switch (factor.factorType) {
        case 'incident_high':
        case 'incident_open':
          return {
            title: '配合处理重点安全事件',
            description: '安全团队发现多条记录可能属于同一件事，请优先完成待办或提交说明。',
            countText: `${count} 项`,
            level: factor.factorType === 'incident_high' ? 'danger' : 'warning',
          } as ActionItem
        case 'client_checkup_critical':
        case 'client_checkup_warning':
          return {
            title: '完成本机体检',
            description: '最近一次安全管家体检提示需要关注，请重新体检并查看修复建议。',
            countText: `${count} 次体检`,
            level: factor.factorType === 'client_checkup_critical' ? 'danger' : 'warning',
          } as ActionItem
        case 'employee_pending':
        case 'playbook_open':
          return {
            title: '完成安全团队待办',
            description: '安全团队需要你确认信息或提交本机检查记录。',
            countText: `${count} 项待办`,
            level: 'warning',
          } as ActionItem
        case 'ticket_overdue':
          return {
            title: '处理超时事项',
            description: '这台电脑有关的处理事项已经超时，请尽快联系管理员确认进度。',
            countText: `${count} 项`,
            level: 'danger',
          } as ActionItem
        case 'vulnerability_critical':
        case 'vulnerability_high':
        case 'baseline':
          return {
            title: '等待修复安排',
            description: '当前电脑存在软件、网页或配置风险，安全团队会安排处理。',
            countText: `${count} 项`,
            level: factor.factorType === 'vulnerability_critical' ? 'danger' : 'warning',
          } as ActionItem
        default:
          return {
            title: factor.factorName || '查看安全建议',
            description: factor.recommendation || factor.explanation || '请按安全团队建议处理。',
            countText: `${count} 项`,
            level: factor.factorScore >= 20 ? 'danger' : factor.factorScore >= 8 ? 'warning' : 'info',
          } as ActionItem
      }
    })
})

const nextActions = computed<ActionItem[]>(() => {
  if (backendNextActions.value.length) {
    return backendNextActions.value.slice(0, 3).map((item) => ({
      title: item.title,
      description: item.recommendedAction || item.reason,
      countText: actionStatusText(item.status),
      level: item.priority === 'critical' || item.priority === 'high' ? 'danger' : item.priority === 'medium' ? 'warning' : 'info',
    }))
  }
  if (highestPriorityTask.value) {
    return [{
      title: '优先处理我的待办',
      description: highestPriorityTask.value.instruction || highestPriorityTask.value.taskName || '安全团队请求你补充这次处理说明。',
      countText: `${pendingEmployeeTasks.value.length} 项待办`,
      level: 'warning',
    }]
  }
  if (riskFactorActions.value.length) {
    return riskFactorActions.value.slice(0, 3)
  }
  if (checkupResult.value?.riskItems.length) {
    const items = checkupResult.value.riskItems
      .filter((item) => item.count > 0 || item.itemType === 'local_checks')
      .slice(0, 3)
      .map<ActionItem>((item) => ({
        title: item.itemName,
        description: item.summary,
        countText: item.itemType === 'local_checks' ? `${item.count} 条记录` : `${item.count} 项`,
        level: item.severity === 'critical' ? 'danger' : item.severity === 'warning' ? 'warning' : 'info',
      }))
    if (items.length) return items
  }
  const actions: ActionItem[] = []
  if (openAlerts.value > 0) {
    actions.push({
      title: '处理安全提醒',
      description: '当前电脑有需要确认的安全提醒，请优先查看修复建议。',
      countText: `${openAlerts.value} 条提醒`,
      level: 'danger',
    })
  }
  if (openVulnerabilities.value + failedBaselines.value > 0) {
    actions.push({
      title: '等待修复安排',
      description: '当前电脑存在软件或网页风险，安全团队会安排处理。',
      countText: `${openVulnerabilities.value + failedBaselines.value} 项`,
      level: 'warning',
    })
  }
  if (unreviewedFim.value > 0) {
    actions.push({
      title: '确认最近变更',
      description: '有安全记录需要你确认是否为本人操作。',
      countText: `${unreviewedFim.value} 条记录`,
      level: 'warning',
    })
  }
  actions.push({
    title: '补充说明或日志',
    description: '如果安全团队需要更多上下文，可以提交日志说明。',
    countText: `${profile.value?.externalEvents.length || 0} 条记录`,
    level: 'info',
  })
  return actions.slice(0, 3)
})

const recentRecords = computed<RecordItem[]>(() => {
  const timeline: ClientEvidenceItem[] = profile.value?.timeline || []
  return timeline.slice(0, 4).map((item) => ({
    id: item.id,
    title: item.title,
    description: item.description || item.type,
    time: item.occurredAt,
  }))
})

const totalRecordCount = computed(() => profile.value?.timeline.length || 0)
const showServiceConsent = computed(() => Boolean(!serviceAccepted.value && localCandidate.value))

const diagnosticsText = computed(() => JSON.stringify({
  assetIp: assetIp.value,
  host: deviceName.value,
  os: osType.value,
  error: error.value,
  notices: dataNotices.value,
  diagnostics: diagnosticLines.value,
}, null, 2))

onMounted(loadWorkbench)

async function loadWorkbench() {
  loading.value = true
  error.value = ''
  dataNotices.value = []
  diagnosticLines.value = []
  try {
    const asset = await resolveWorkbenchAsset()
    if (!asset) {
      useOfflineData()
      return
    }
    selectedAsset.value = asset
    try {
      profile.value = await loadClientProfile(asset)
    } catch (err) {
      profile.value = buildEmptyClientProfile(asset)
      addDataNotice('当前电脑已绑定为真实资产，但安全画像聚合接口暂时不可用，已使用资产信息继续展示')
      appendDiagnostic('client profile', err)
    }
    const [profileRes, tasksRes, riskRes] = await Promise.all([
      Promise.resolve(undefined),
      listClientTasks().catch(() => undefined),
      clientDeviceRiskProfile(asset.ip).catch((err) => {
        appendDiagnostic('risk profile', err)
        return undefined
      }),
    ])
    employeeTasks.value = tasksRes?.data.data || []
    riskProfile.value = riskRes?.data.data
    const actionRes = await listClientNextActions(asset.ip, 5).catch((err) => {
      appendDiagnostic('next actions', err)
      return undefined
    })
    backendNextActions.value = actionRes?.data.data || []
    await loadLatestCheckup()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '请求失败'
  } finally {
    loading.value = false
  }
}

async function resolveWorkbenchAsset() {
  const loaded = await loadClientAssets()
  const records = loaded.records || []
  const routeIp = typeof route.query.ip === 'string' ? route.query.ip : ''
  const routeHost = typeof route.query.host === 'string' ? route.query.host : ''
  const localAsset = findClientLocalAsset(records)
  localCandidate.value = localAsset
  const isDemoRoute = isDemoClientContext(routeIp, routeHost)
  if (localAsset && serviceAccepted.value && (!routeIp || isDemoRoute)) {
    addDataNotice(`${localAsset.hostname} / ${localAsset.ip} 已配置为本机真实数据来源`)
    return localAsset
  }
  const selected = chooseClientAsset(records, {
    routeIp,
    routeHost,
    allowDemoFallback: true,
    allowFirstFallback: true,
    preferAcceptedLocal: true,
  })
  if (selected) return selected
  if (!routeIp && !routeHost) return undefined
  addDataNotice('未在资产清单中匹配到当前电脑，已使用地址栏中的电脑信息继续展示')
  return {
    id: 0,
    hostname: routeHost || routeIp || '当前电脑',
    ip: routeIp || '',
    osType: typeof route.query.os === 'string' ? route.query.os : 'Linux',
    sourceType: 'route-context',
    riskLevel: 'unknown',
    deptName: '当前电脑',
    ownerName: '当前用户',
    openAlertCount: 0,
    lastSeenAt: new Date().toISOString(),
  } as AssetItem
}

async function acceptKeeperService() {
  const asset = localCandidate.value
  if (!asset) return
  checkupLoading.value = true
  acceptSecurityKeeperService(asset)
  serviceAccepted.value = true
  selectedAsset.value = asset
  addDataNotice(`${asset.hostname} / ${asset.ip} 已接入安全管家服务`)
  try {
    const snapshot = await runClientSecuritySnapshot({
      assetIp: asset.ip,
      osType: asset.osType,
      note: '用户接受安全管家服务后自动生成首次本机只读快照',
      linkAlert: false,
    })
    lastCheckupAt.value = snapshot.data.data.event.eventTime || new Date().toISOString()
    addDataNotice('已生成首次本机只读安全记录')
  } catch (err) {
    appendDiagnostic('service acceptance snapshot', err)
    addDataNotice('本机资产已绑定，首次只读快照暂时未完成')
  } finally {
    checkupLoading.value = false
  }
  await router.replace({ path: '/client/workbench', query: routeQueryForAsset(asset) })
  await loadWorkbench()
}

async function runCheckup() {
  checkupLoading.value = true
  error.value = ''
  try {
    if (!assetIp.value) {
      throw new Error('当前电脑 IP 缺失，无法生成体检结果')
    }
    const result = await runSecurityKeeperCheckup(assetIp.value)
    await loadWorkbench()
    checkupResult.value = result.data.data
    lastCheckupAt.value = result.data.data.checkup.checkedAt
  } catch (err) {
    appendDiagnostic('security keeper checkup', err)
    if (selectedAsset.value?.sourceType === 'client-local') {
      try {
        const snapshot = await runClientSecuritySnapshot({
          assetIp: assetIp.value,
          osType: osType.value,
          note: '安全管家本机只读体检',
          linkAlert: false,
        })
        await loadWorkbench()
        lastCheckupAt.value = snapshot.data.data.event.eventTime || new Date().toISOString()
        addDataNotice('安全管家聚合接口暂时不可用，已自动使用本机只读快照采集真实数据')
        return
      } catch (snapshotErr) {
        appendDiagnostic('local snapshot fallback', snapshotErr)
      }
    }
    error.value = err instanceof Error ? err.message : '一键体检失败'
  } finally {
    checkupLoading.value = false
  }
}

async function loadLatestCheckup() {
  if (!assetIp.value) return
  const history = await listSecurityKeeperCheckups(assetIp.value).catch((err) => {
    appendDiagnostic('checkup history', err)
    return undefined
  })
  const latest = history?.data.data?.[0]
  if (!latest) return
  checkupResult.value = {
    checkup: {
      id: latest.id,
      checkupNo: latest.checkupNo,
      assetId: profile.value?.asset.id || 0,
      assetIp: latest.assetIp,
      assetName: latest.assetName,
      osType: osType.value,
      score: latest.score,
      status: latest.status,
      summary: latest.summary,
      checkedAt: latest.checkedAt,
    },
    riskItems: [],
    recommendations: [],
  }
  lastCheckupAt.value = latest.checkedAt
}

function useOfflineData() {
  error.value = ''
  dataNotices.value = []
  diagnosticLines.value = []
  lastCheckupAt.value = new Date().toISOString()
  checkupResult.value = undefined
  const asset: AssetItem = {
    id: 0,
    hostname: deviceName.value || 'prod-app-01',
    ip: assetIp.value || '10.20.1.15',
    osType: osType.value || 'Linux',
    sourceType: 'offline-demo',
    riskLevel: 'critical',
    deptName: '基础设施运维组',
    ownerName: '基础设施运维组',
    openAlertCount: 30,
    riskScore: 100,
    lastSeenAt: '',
  }
  selectedAsset.value = asset
  profile.value = {
    asset,
    metrics: {
      riskScore: 100,
      alerts: 30,
      openVulnerabilities: 1,
      failedBaselines: 0,
      pendingFileIntegrity: 4,
      pendingExternalEvents: 30,
      summary: '离线演示数据',
    },
    alerts: [],
    vulnerabilities: [],
    baselines: [],
    fileIntegrityEvents: [],
    externalEvents: [],
    timeline: [
      { id: 'offline-1', type: '安全提醒', title: '当前电脑有安全提醒需要确认', description: '离线演示数据', occurredAt: new Date().toISOString() },
      { id: 'offline-2', type: '处理任务', title: '安全团队创建了待办任务', description: '离线演示数据', occurredAt: new Date().toISOString() },
      { id: 'offline-3', type: '本机检查', title: '等待员工完成只读检查', description: '离线演示数据', occurredAt: new Date().toISOString() },
    ],
  }
  employeeTasks.value = [{
    id: 0,
    ticketId: 0,
    taskKey: 'offline-demo',
    taskName: '安全团队请求你提交一次本机检查记录',
    taskType: 'employee_confirm',
    assigneeType: 'employee',
    instruction: '请确认电脑名称和网络连接检查结果，并补充这次处理说明。',
    expectedEvidence: '本机检查记录或处理说明。',
    status: 'pending',
    sortOrder: 10,
    createdAt: new Date().toISOString(),
  }]
  backendNextActions.value = []
}

function addDataNotice(message: string) {
  if (!dataNotices.value.includes(message)) {
    dataNotices.value = [...dataNotices.value, message]
  }
}

function appendDiagnostic(scope: string, err: unknown) {
  const message = err instanceof Error ? err.message : String(err)
  diagnosticLines.value = [...diagnosticLines.value, `[${scope}] ${message}`]
}

function routeQuery() {
  return buildClientDeviceRouteQuery({
    ip: assetIp.value,
    host: deviceName.value,
    os: osType.value,
  })
}

function routeQueryForAsset(asset: AssetItem) {
  return buildClientDeviceRouteQuery({
    ip: asset.ip,
    host: asset.hostname,
    os: asset.osType,
  })
}

function openRepair() {
  router.push({ path: '/client/operations', query: { ...routeQuery(), tab: 'repair' } })
}

function openTools() {
  router.push({ path: '/client/local-range', query: routeQuery() })
}

async function openLogs() {
  await router.push({ path: '/client/security-logs', query: routeQuery() })
}

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

function actionStatusText(status?: string) {
  if (!status || status === 'open' || status === 'pending') return '待处理'
  if (status === 'confirmed' || status === 'completed') return '已确认'
  if (status === 'submitted') return '已提交'
  return status
}
</script>

<style scoped>
.client-workbench {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.recoverable-error,
.soft-config-notice,
.keeper-consent-panel,
.keeper-hero,
.soc-panel,
.keeper-entry-card {
  border: 1px solid var(--soc-border);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: var(--soc-shadow-soft), var(--soc-glass-highlight);
}

.recoverable-error {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 18px 22px;
  border-color: rgba(225, 83, 97, 0.38);
}

.soft-config-notice,
.keeper-consent-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 18px 22px;
  border-color: rgba(49, 182, 198, 0.26);
  background:
    radial-gradient(circle at 0 0, rgba(49, 182, 198, 0.12), transparent 32%),
    linear-gradient(135deg, rgba(248, 253, 254, 0.95), rgba(255, 255, 255, 0.82));
}

.keeper-consent-panel {
  grid-template-columns: minmax(0, 1fr) minmax(260px, 0.38fr) auto;
  border-color: rgba(212, 147, 74, 0.32);
  background:
    radial-gradient(circle at 0 0, rgba(239, 180, 102, 0.18), transparent 34%),
    linear-gradient(135deg, rgba(255, 248, 238, 0.92), rgba(255, 255, 255, 0.82));
}

.keeper-consent-panel h2 {
  margin: 6px 0;
  color: #172033;
  font-size: 22px;
}

.consent-device {
  display: grid;
  gap: 5px;
  min-width: 0;
  padding: 13px;
  border: 1px solid rgba(179, 173, 163, 0.34);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
}

.consent-device strong {
  color: #172033;
}

.consent-device span,
.consent-device em {
  color: #65728a;
  font-size: 12px;
  font-style: normal;
  line-height: 1.5;
}

.consent-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.recoverable-error strong {
  display: block;
  margin-bottom: 6px;
  color: #172033;
}

.recoverable-error p,
.soft-config-notice p,
.keeper-consent-panel p,
.soc-panel p,
.keeper-hero p,
.keeper-entry-card p {
  margin: 0;
  color: #65728a;
  line-height: 1.7;
}

.error-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.recoverable-error pre,
.soft-config-notice pre {
  grid-column: 1 / -1;
  width: 100%;
  margin: 12px 0 0;
  padding: 12px;
  overflow: auto;
  border-radius: 8px;
  background: #f6f8fb;
}

.keeper-hero {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(420px, 1.08fr);
  gap: 22px;
  padding: 30px;
  background:
    radial-gradient(circle at 8% 8%, rgba(239, 180, 102, 0.16), transparent 32%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.92), rgba(248, 251, 252, 0.86));
}

.keeper-copy {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}

.keeper-copy h1 {
  margin: 8px 0;
  color: #172033;
  font-size: 36px;
  line-height: 1.16;
}

.device-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 20px;
}

.device-strip span {
  padding: 7px 11px;
  border: 1px solid rgba(106, 166, 184, 0.28);
  border-radius: 999px;
  color: #43516a;
  background: rgba(255, 255, 255, 0.72);
}

.keeper-status-card {
  padding: 24px;
  border: 1px solid rgba(225, 83, 97, 0.26);
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(255, 246, 246, 0.95), rgba(255, 255, 255, 0.86));
}

.keeper-status-card strong {
  display: block;
  margin: 10px 0;
  color: #172033;
  font-size: 44px;
  line-height: 1.12;
}

.status-heading,
.hero-action-row,
.panel-title-row,
.record-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.keeper-status-safe {
  border-color: rgba(56, 161, 105, 0.34);
  background: linear-gradient(135deg, rgba(240, 253, 244, 0.95), rgba(255, 255, 255, 0.86));
}

.keeper-status-attention {
  border-color: rgba(221, 141, 54, 0.35);
  background: linear-gradient(135deg, rgba(255, 247, 237, 0.95), rgba(255, 255, 255, 0.86));
}

.status-heading span,
.status-metric-grid span {
  color: #65728a;
}

.status-metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 22px 0;
}

.status-metric-grid div {
  padding: 14px;
  border: 1px solid rgba(52, 64, 84, 0.08);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.7);
}

.status-metric-grid b {
  display: block;
  margin-top: 6px;
  color: #172033;
  font-size: 18px;
}

.hero-action-row {
  justify-content: flex-start;
  flex-wrap: wrap;
}

.keeper-entry-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.keeper-entry-card {
  display: grid;
  gap: 10px;
  min-height: 168px;
  padding: 20px;
  text-align: left;
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.keeper-entry-card:hover {
  border-color: rgba(221, 141, 54, 0.42);
  box-shadow: 0 18px 40px rgba(52, 64, 84, 0.12);
  transform: translateY(-2px);
}

.keeper-entry-card strong {
  color: #172033;
  font-size: 18px;
}

.keeper-entry-card em {
  align-self: end;
  color: #cf6f22;
  font-style: normal;
  font-weight: 700;
}

.entry-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 999px;
  color: #fff;
  background: linear-gradient(135deg, #efb466, #31b6c6);
}

.keeper-entry-check {
  position: relative;
  overflow: hidden;
}

.keeper-entry-check::after {
  position: absolute;
  inset: 0;
  content: "";
  pointer-events: none;
  background: linear-gradient(90deg, transparent, rgba(221, 141, 54, 0.16), transparent);
  transform: translateX(-100%);
  animation: keeper-check-sweep 2.8s ease-in-out infinite;
}

.keeper-detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.88fr) minmax(0, 1.12fr);
  gap: 18px;
}

.soc-panel {
  padding: 22px;
}

.panel-title-row {
  align-items: flex-start;
  margin-bottom: 18px;
}

.panel-title-row h2 {
  margin: 0 0 6px;
  color: #172033;
  font-size: 20px;
}

.action-list,
.record-list {
  display: grid;
  gap: 14px;
}

.action-item,
.record-item {
  padding: 16px;
  border: 1px solid var(--soc-border);
  border-left: 4px solid #dd8d36;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.78);
}

.action-danger {
  border-left-color: #e15361;
}

.action-warning {
  border-left-color: #dd8d36;
}

.action-info {
  border-left-color: #6aa6b8;
}

.action-item strong,
.record-item strong {
  color: #172033;
  font-size: 17px;
}

.action-item span,
.record-item span {
  color: #cf6f22;
  font-weight: 700;
}

.records-panel {
  min-height: 210px;
  scroll-margin-top: 110px;
}

.record-item {
  border-left-color: #6aa6b8;
}

@keyframes keeper-check-sweep {
  0% { transform: translateX(-100%); }
  55%, 100% { transform: translateX(100%); }
}

@media (max-width: 1180px) {
  .keeper-hero,
  .keeper-detail-grid,
  .keeper-consent-panel,
  .soft-config-notice {
    grid-template-columns: 1fr;
  }

  .keeper-entry-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .keeper-hero {
    padding: 22px;
  }

  .keeper-copy h1,
  .keeper-status-card strong {
    font-size: 32px;
  }

  .keeper-entry-grid,
  .status-metric-grid {
    grid-template-columns: 1fr;
  }

  .record-item,
  .recoverable-error {
    flex-direction: column;
  }
}
</style>
