<template>
  <div class="workspace-card-stack">
    <el-alert
      v-if="showNotice"
      title="张彥为当前本机真实采集用户；松松、老曹、刘哥为预置验证用户。验证数据会明确标识来源。"
      type="info"
      show-icon
      :closable="false"
    />

    <section v-loading="loading" class="workspace-grid" :class="{ 'workspace-grid--compact': compact }">
      <article
        v-for="user in workspaces"
        :key="user.ownerId"
        class="soc-panel workspace-user-card"
        :class="{ 'workspace-user-card--focus': focusedOwnerId === user.ownerId }"
      >
        <header>
          <div>
            <span class="workspace-user-kicker">{{ user.username }}</span>
            <h2>{{ user.nickname || user.username }}</h2>
          </div>
          <div class="workspace-user-tags">
            <el-tag :type="user.dataMode === 'validation' ? 'warning' : 'success'" effect="plain">
              {{ user.dataMode === 'validation' ? '预置验证数据' : '真实采集数据' }}
            </el-tag>
            <el-tag :type="user.onlineAgentCount > 0 ? 'success' : 'info'" effect="plain">
              {{ user.onlineAgentCount > 0 ? '采集在线' : '等待采集' }}
            </el-tag>
          </div>
        </header>

        <div class="workspace-user-metrics">
          <div v-for="metric in metricsFor(user)" :key="metric.label">
            <span>{{ metric.label }}</span>
            <strong>{{ metric.value }}</strong>
          </div>
        </div>

        <p>{{ summaryFor(user) }}</p>
        <footer>
          <el-button type="primary" @click="openWorkspace(user.ownerId)">{{ primaryActionLabel }}</el-button>
          <el-button v-if="relatedAction" plain @click="openRelated(user.ownerId)">{{ relatedAction.label }}</el-button>
        </footer>
      </article>
      <el-empty v-if="!workspaces.length && !loading" description="暂无该业务下的用户数据" :image-size="64" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { userWorkspaceCards, type UserWorkspaceCard } from '@/api/soc'
import { workspaceTargetPath, workspaceTargetRoute, workspaceTargetTitle } from '@/utils/socUserWorkspace'

const props = withDefaults(defineProps<{
  target?: string
  focusedOwnerId?: number
  compact?: boolean
  showNotice?: boolean
}>(), {
  target: '/soc/assets',
  focusedOwnerId: 0,
  compact: false,
  showNotice: true,
})

type MetricKey = Exclude<keyof UserWorkspaceCard, 'ownerId' | 'username' | 'nickname' | 'deptId' | 'dataMode'>
type TargetPresentation = {
  metrics: Array<{ label: string; key: MetricKey }>
  summary: (user: UserWorkspaceCard) => string
  related?: { label: string; path: string; ownerScoped?: boolean }
}

const router = useRouter()
const loading = ref(false)
const workspaces = ref<UserWorkspaceCard[]>([])
const targetPath = computed(() => workspaceTargetPath(props.target))
const targetTitle = computed(() => workspaceTargetTitle(props.target))
const primaryActionLabel = computed(() => targetPath.value === '/soc/agents' ? '查看此用户 Agent' : `进入${targetTitle.value}`)

const presentations: Record<string, TargetPresentation> = {
  '/soc/daily-recommendations': {
    metrics: [['待处置告警', 'openAlertCount'], ['高优先级', 'highAlertCount'], ['开放事件簇', 'openIncidentCount'], ['待处理工单', 'openTicketCount']].map(([label, key]) => ({ label, key: key as MetricKey })),
    summary: (user) => `当前共有 ${user.openAlertCount + user.openIncidentCount + user.openTicketCount} 项运营事项需要关注。`,
    related: { label: '查看工单', path: '/soc/tickets' },
  },
  '/soc/alerts': {
    metrics: [['待处置告警', 'openAlertCount'], ['高优先级告警', 'highAlertCount'], ['关联资产', 'assetCount'], ['开放事件簇', 'openIncidentCount']].map(([label, key]) => ({ label, key: key as MetricKey })),
    summary: (user) => `${user.highAlertCount} 条高优先级告警，需按资产和证据链完成研判。`,
    related: { label: '查看事件簇', path: '/soc/incidents' },
  },
  '/soc/incidents': {
    metrics: [['开放事件簇', 'openIncidentCount'], ['高危事件簇', 'highIncidentCount'], ['待处置告警', 'openAlertCount'], ['待处理工单', 'openTicketCount']].map(([label, key]) => ({ label, key: key as MetricKey })),
    summary: (user) => `${user.openIncidentCount} 个事件簇正在关联该用户的主机、批次和时间窗口。`,
    related: { label: '查看关联告警', path: '/soc/alerts' },
  },
  '/soc/assets': {
    metrics: [['统一资产', 'assetCount'], ['高风险资产', 'highRiskAssetCount'], ['在线 Agent', 'onlineAgentCount'], ['待处置告警', 'openAlertCount']].map(([label, key]) => ({ label, key: key as MetricKey })),
    summary: (user) => `${user.assetCount} 个受管资产，${user.highRiskAssetCount} 个处于高风险状态。`,
    related: { label: '查看 Agent', path: '/soc/agents' },
  },
  '/soc/client-security': {
    metrics: [['员工设备', 'assetCount'], ['在线 Agent', 'onlineAgentCount'], ['基线异常', 'failedBaselineCount'], ['24h 文件变更', 'fim24hCount']].map(([label, key]) => ({ label, key: key as MetricKey })),
    summary: (user) => `聚合该用户设备的采集连接、基线和文件监控状态。`,
    related: { label: '查看 Agent', path: '/soc/agents' },
  },
  '/soc/vulnerabilities': {
    metrics: [['待修复漏洞', 'openVulnerabilityCount'], ['高危漏洞', 'highVulnerabilityCount'], ['受影响资产', 'assetCount'], ['待处理工单', 'openTicketCount']].map(([label, key]) => ({ label, key: key as MetricKey })),
    summary: (user) => `${user.openVulnerabilityCount} 个漏洞待复核，其中 ${user.highVulnerabilityCount} 个为高优先级。`,
    related: { label: '查看资产风险', path: '/soc/assets' },
  },
  '/soc/baselines': {
    metrics: [['基线检查', 'baselineCount'], ['异常项', 'failedBaselineCount'], ['受检资产', 'assetCount'], ['24h 文件变更', 'fim24hCount']].map(([label, key]) => ({ label, key: key as MetricKey })),
    summary: (user) => `${user.failedBaselineCount} 个基线异常项需要复核配置与授权变更。`,
    related: { label: '查看文件变更', path: '/soc/fim' },
  },
  '/soc/fim': {
    metrics: [['文件变更', 'fimCount'], ['24h 变更', 'fim24hCount'], ['受管资产', 'assetCount'], ['基线异常', 'failedBaselineCount']].map(([label, key]) => ({ label, key: key as MetricKey })),
    summary: (user) => `${user.fim24hCount} 条文件变更发生在最近 24 小时。`,
    related: { label: '查看基线核查', path: '/soc/baselines' },
  },
  '/soc/external-events': {
    metrics: [['外部事件', 'externalEventCount'], ['高风险事件', 'highExternalEventCount'], ['关联告警', 'openAlertCount'], ['受影响资产', 'assetCount']].map(([label, key]) => ({ label, key: key as MetricKey })),
    summary: (user) => `${user.externalEventCount} 条外部访问、外联、扫描或 IOC 风险记录。`,
    related: { label: '查看关联告警', path: '/soc/alerts' },
  },
  '/soc/tickets': {
    metrics: [['工单总数', 'ticketCount'], ['待处理工单', 'openTicketCount'], ['已超时工单', 'overdueTicketCount'], ['开放事件簇', 'openIncidentCount']].map(([label, key]) => ({ label, key: key as MetricKey })),
    summary: (user) => `${user.openTicketCount} 个工单尚未闭环，${user.overdueTicketCount} 个已经超时。`,
    related: { label: '查看报告', path: '/soc/reports' },
  },
  '/soc/reports': {
    metrics: [['报告总数', 'reportCount'], ['24h 新报告', 'reports24hCount'], ['待处理工单', 'openTicketCount'], ['开放事件簇', 'openIncidentCount']].map(([label, key]) => ({ label, key: key as MetricKey })),
    summary: (user) => `${user.reportCount} 份报告归属于该用户，其中 ${user.reports24hCount} 份在 24 小时内生成。`,
    related: { label: '查看工单', path: '/soc/tickets' },
  },
  '/soc/agents': {
    metrics: [['Agent 总数', 'agentCount'], ['在线 Agent', 'onlineAgentCount'], ['统一资产', 'assetCount'], ['24h 文件变更', 'fim24hCount']].map(([label, key]) => ({ label, key: key as MetricKey })),
    summary: (user) => `${user.onlineAgentCount}/${user.agentCount} 个 Agent 在线，仅显示该用户所属采集器。`,
    related: { label: 'Agent 安装', path: '/soc/agents/install', ownerScoped: false },
  },
}

const fallbackPresentation: TargetPresentation = {
  metrics: [['统一资产', 'assetCount'], ['在线 Agent', 'onlineAgentCount'], ['待处置告警', 'openAlertCount'], ['开放事件簇', 'openIncidentCount']].map(([label, key]) => ({ label, key: key as MetricKey })),
  summary: (user) => `该用户共有 ${user.assetCount} 个受管资产。`,
}

const presentation = computed(() => presentations[targetPath.value] || fallbackPresentation)
const relatedAction = computed(() => presentation.value.related)

onMounted(() => void load())

async function load() {
  loading.value = true
  try {
    workspaces.value = (await userWorkspaceCards()).data.data
  } finally {
    loading.value = false
  }
}

function metricsFor(user: UserWorkspaceCard) {
  return presentation.value.metrics.map((metric) => ({ label: metric.label, value: user[metric.key] }))
}

function summaryFor(user: UserWorkspaceCard) {
  return presentation.value.summary(user)
}

function openWorkspace(ownerId: number) {
  void router.push(workspaceTargetRoute(props.target, ownerId))
}

function openRelated(ownerId: number) {
  const action = relatedAction.value
  if (!action) return
  void router.push({ path: action.path, query: action.ownerScoped === false ? {} : { ownerId: String(ownerId) } })
}
</script>

<style scoped>
.workspace-card-stack { display: grid; gap: 14px; min-width: 0; }
.workspace-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 14px; }
.workspace-grid--compact { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.workspace-user-card { display: grid; gap: 16px; padding: 18px; min-width: 0; }
.workspace-user-card--focus { border-color: var(--el-color-primary); box-shadow: 0 0 0 3px color-mix(in srgb, var(--el-color-primary) 18%, transparent); }
.workspace-user-card header, .workspace-user-card footer { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.workspace-user-tags { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 6px; }
.workspace-user-card h2 { margin: 5px 0 0; font-size: 21px; }
.workspace-user-kicker, .workspace-user-card p { color: var(--soc-text-muted); font-size: 12px; }
.workspace-user-card p { margin: 0; line-height: 1.5; }
.workspace-user-metrics { display: grid; grid-template-columns: 1fr 1fr; border-top: 1px solid var(--soc-border); border-left: 1px solid var(--soc-border); }
.workspace-user-metrics div { min-width: 0; border-right: 1px solid var(--soc-border); border-bottom: 1px solid var(--soc-border); padding: 10px; }
.workspace-user-metrics span, .workspace-user-metrics strong { display: block; }
.workspace-user-metrics span { color: var(--soc-text-muted); font-size: 12px; }
.workspace-user-metrics strong { margin-top: 5px; font-size: 21px; }
.workspace-user-card footer { justify-content: flex-start; flex-wrap: wrap; }
@media (max-width: 900px) { .workspace-grid--compact { grid-template-columns: 1fr; } }
</style>
