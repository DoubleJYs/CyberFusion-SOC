<template>
  <div class="page-shell">
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
    >
      <template #default>
        <el-button size="small" @click="load">重试</el-button>
      </template>
    </el-alert>

    <section class="soc-page-hero overview-hero">
      <div>
        <span class="soc-page-kicker">SOC WORKBENCH</span>
        <h1>工作台</h1>
        <p>这个页面帮你快速了解今天的风险、告警、待处理工单和资产风险趋势。</p>
      </div>
    </section>

    <section class="kpi-grid">
      <RiskCard label="今日告警" :value="overview.todayAlerts" delta="来自告警处置" tone="medium" />
      <RiskCard label="高危告警" :value="overview.highAlerts" delta="critical + high 待处置" tone="critical" />
      <RiskCard label="待处理工单" :value="overview.pendingTickets" delta="未关闭/未归档" tone="high" />
      <RiskCard label="受管资产" :value="overview.assets" delta="P0 资产视图" tone="low" />
    </section>

    <section v-loading="agentLoading" class="soc-panel agent-status-panel">
      <div class="panel-title agent-status-head">
        <div>
          <strong>Agent 状态</strong>
          <span>当前主机可管理或正在心跳的采集器，数据每 30 秒同步一次。</span>
        </div>
        <div class="agent-status-actions">
          <span>{{ hostAgentsSummary.total }} 个当前 Agent · 在线 {{ hostAgentsSummary.online }} · 未在线 {{ hostAgentsSummary.offline }}</span>
          <el-button type="primary" plain @click="router.push('/soc/agents')">完整管理</el-button>
        </div>
      </div>
      <el-alert
        v-if="agentError"
        :title="agentError"
        type="warning"
        show-icon
        :closable="false"
      />
      <el-alert
        v-else-if="historicalAgentCount"
        :title="`已隐藏 ${historicalAgentCount} 个未安装且无当前心跳的历史 Agent 记录，可在完整管理页查看。`"
        type="info"
        show-icon
        :closable="false"
      />
      <div class="agent-status-table">
        <el-table :data="hostAgentRows" empty-text="暂无 Agent 状态" size="small">
          <el-table-column label="状态" width="92">
            <template #default="{ row }">
              <StatusBadge :status="row.status" />
            </template>
          </el-table-column>
          <el-table-column label="Agent / 主机" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="agent-status-name">
                <strong>{{ row.agentName || row.hostname || row.agentId }}</strong>
                <small>{{ row.hostname || row.agentId }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="系统" min-width="130">
            <template #default="{ row }">{{ osLabel(row.osType) }}</template>
          </el-table-column>
          <el-table-column label="队列" width="130">
            <template #default="{ row }">{{ row.queueDepth || 0 }} / {{ formatBytes(row.queueBytes) }}</template>
          </el-table-column>
          <el-table-column label="采集/上报" width="130">
            <template #default="{ row }">{{ row.collectedCount || 0 }} / {{ row.sentCount || 0 }}</template>
          </el-table-column>
          <el-table-column label="失败" width="86">
            <template #default="{ row }">
              <el-tag :type="(row.failedCount || 0) > 0 ? 'warning' : 'success'" effect="plain">
                {{ row.failedCount || 0 }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最后心跳" min-width="170">
            <template #default="{ row }">{{ row.lastSeenAt || '尚未收到心跳' }}</template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section v-if="operations" v-loading="operationsLoading" class="soc-panel operations-panel">
          <div class="panel-title">
            <strong>运营指标中心</strong>
            <span>事件簇、风险、SLA、推荐动作和员工待办的可解释指标</span>
          </div>
          <div class="operation-metric-grid">
            <button
              v-for="metric in operationHeroMetrics"
              :key="metric.metricCode"
              class="operation-metric-card"
              type="button"
              @click="openMetric(metric.drilldownTarget)"
            >
              <span>{{ metric.metricName }}</span>
              <strong>{{ metricValue(metric) }}</strong>
              <small>{{ metric.explanation }}</small>
            </button>
          </div>
          <div class="operation-detail-grid">
            <div>
              <h3>SLA 与工单效率</h3>
              <div class="operation-progress-row">
                <span>关闭率</span>
                <el-progress :percentage="operations.sla.closeRate" :stroke-width="8" />
              </div>
              <div class="operation-progress-row">
                <span>剧本完成率</span>
                <el-progress :percentage="operations.sla.playbookCompletionRate" :stroke-width="8" />
              </div>
              <p>MTTA {{ operations.sla.mttaHours }}h / MTTR {{ operations.sla.mttrHours }}h / 超时 {{ operations.sla.overdueTickets }} 个</p>
            </div>
            <div>
              <h3>推荐与员工完成</h3>
              <div class="operation-progress-row">
                <span>推荐采纳率</span>
                <el-progress :percentage="operations.recommendationAdoption.adoptionRate" :stroke-width="8" />
              </div>
              <div class="operation-progress-row">
                <span>员工待办完成率</span>
                <el-progress :percentage="operations.clientTasks.completionRate" :stroke-width="8" />
              </div>
              <p>体检覆盖率 {{ operations.clientTasks.checkupCoverageRate }}% / 逾期待办 {{ operations.clientTasks.overdueTasks }} 个</p>
            </div>
            <div>
              <h3>风险变化趋势</h3>
              <RiskTrendChart
                v-if="operations.riskTrend.points.some((item) => item.snapshotCount > 0)"
                :labels="operations.riskTrend.points.map((item) => item.date.slice(5))"
                :values="operations.riskTrend.points.map((item) => item.averageScore)"
              />
              <el-empty v-else description="暂无风险快照" :image-size="60" />
              <p>24h {{ signedMetric(operations.riskTrend.change24h) }} / 7d {{ signedMetric(operations.riskTrend.change7d) }}</p>
            </div>
          </div>
          <div class="operation-top-grid">
            <div>
              <h3>Top 风险资产</h3>
              <button
                v-for="asset in operations.topRiskAssets"
                :key="asset.assetId"
                class="operation-list-row"
                type="button"
                @click="openMetric(asset.drilldownTarget)"
              >
                <span>
                  <strong>{{ asset.hostname || asset.assetIp || '-' }}</strong>
                  <small>{{ asset.assetIp || asset.deptName || '资产风险画像' }}</small>
                </span>
                <el-tag size="small" effect="plain">{{ asset.riskScore }}</el-tag>
              </button>
              <el-empty v-if="!operations.topRiskAssets.length" description="暂无高风险资产" :image-size="52" />
            </div>
            <div>
              <h3>Top 事件簇</h3>
              <button
                v-for="incident in operations.topIncidents"
                :key="incident.incidentId"
                class="operation-list-row"
                type="button"
                @click="openMetric(incident.drilldownTarget)"
              >
                <span>
                  <strong>{{ incident.title || incident.clusterNo }}</strong>
                  <small>{{ incident.asset || incident.clusterNo || '安全事件簇' }}</small>
                </span>
                <el-tag size="small" effect="plain">{{ incident.score }}</el-tag>
              </button>
              <el-empty v-if="!operations.topIncidents.length" description="暂无事件簇" :image-size="52" />
            </div>
            <div>
              <h3>Top 趋势异常</h3>
              <button
                v-for="trendSource in operations.topTrendSources"
                :key="`${trendSource.title}-${trendSource.assetIp}-${trendSource.sourceType}`"
                class="operation-list-row"
                type="button"
                @click="openMetric(trendSource.drilldownTarget)"
              >
                <span>
                  <strong>{{ trendSource.title }}</strong>
                  <small>{{ trendSource.assetIp || trendSource.sourceType || trendSource.explanation }}</small>
                </span>
                <el-tag size="small" effect="plain">{{ trendSource.currentCount }}</el-tag>
              </button>
              <el-empty v-if="!operations.topTrendSources.length" description="暂无趋势异常" :image-size="52" />
            </div>
          </div>
    </section>

    <section v-loading="loading" class="dashboard-grid">
          <div class="soc-panel chart-panel">
            <div class="panel-title">
              <strong>七日风险趋势</strong>
              <span>按事件时间聚合</span>
            </div>
            <RiskTrendChart v-if="trend.length" :labels="trend.map((item) => item.date.slice(5))" :values="trend.map((item) => item.count)" />
            <el-empty v-else description="暂无趋势数据" :image-size="76" />
          </div>
          <div class="soc-panel side-panel">
            <div class="panel-title">
              <strong>告警等级分布</strong>
              <span>业务处置状态在 MySQL 保存</span>
            </div>
            <div v-for="item in severities" :key="item.name" class="distribution-row">
              <SeverityBadge :severity="item.name" />
              <el-progress :percentage="percentage(item.value)" :stroke-width="8" :show-text="false" />
              <strong>{{ item.value }}</strong>
            </div>
            <el-empty v-if="!severities.length" description="暂无等级分布" :image-size="76" />
          </div>
    </section>

    <section v-loading="loading" class="management-grid">
          <div class="soc-panel">
            <div class="panel-title">
              <strong>部门风险排行</strong>
              <span>告警、漏洞、基线、工单综合评分</span>
            </div>
            <div v-for="dept in analytics.departmentRisks" :key="dept.deptName" class="dept-risk-row">
              <div>
                <strong>{{ dept.deptName }}</strong>
                <span>{{ dept.assets }} 台资产 / 高危告警 {{ dept.highAlerts }} / 待修复漏洞 {{ dept.openVulnerabilities }}</span>
              </div>
              <el-progress :percentage="dept.score" :stroke-width="10" />
            </div>
            <el-empty v-if="!analytics.departmentRisks.length" description="暂无部门风险数据" :image-size="76" />
          </div>
          <div class="soc-panel">
            <div class="panel-title">
              <strong>处置效率</strong>
              <span>SLA、误报与重复告警</span>
            </div>
            <div class="metric-grid">
              <div class="metric-item">
                <span>SLA 达成率</span>
                <strong>{{ analytics.operationMetrics.slaRate }}%</strong>
              </div>
              <div class="metric-item">
                <span>超时工单</span>
                <strong>{{ analytics.operationMetrics.overdueTickets }}</strong>
              </div>
              <div class="metric-item">
                <span>误报率</span>
                <strong>{{ analytics.operationMetrics.falsePositiveRate }}%</strong>
              </div>
              <div class="metric-item">
                <span>重复聚合组</span>
                <strong>{{ analytics.operationMetrics.duplicateGroups }}</strong>
              </div>
              <div class="metric-item">
                <span>平均关闭耗时</span>
                <strong>{{ analytics.operationMetrics.averageCloseHours }}h</strong>
              </div>
              <div class="metric-item">
                <span>已关闭工单</span>
                <strong>{{ analytics.operationMetrics.closedTickets }}</strong>
              </div>
            </div>
          </div>
    </section>

    <section v-loading="loading" class="soc-panel incident-panel">
          <div class="panel-title">
            <strong>Top 5 安全事件簇</strong>
            <span>多源证据关联结果</span>
          </div>
          <div v-for="incident in topIncidents" :key="incident.id" class="incident-row" @click="router.push({ path: '/soc/incidents', query: { keyword: incident.clusterNo } })">
            <div>
              <strong>{{ incident.clusterNo }} · {{ incident.title }}</strong>
              <span>{{ incidentAssetLabel(incident) }} / {{ incidentSourceLabel(incident) }} / {{ incidentEvidenceCount(incident) }} 条证据</span>
            </div>
            <b>{{ incident.score }}</b>
          </div>
          <el-empty v-if="!topIncidents.length" description="暂无事件簇，前往安全事件簇页面执行关联" :image-size="76" />
    </section>

    <section v-loading="loading" class="soc-panel trend-anomaly-panel">
          <div class="panel-title">
            <strong>趋势异常 Top 5</strong>
            <span>当前 24 小时窗口对比 7 天均值</span>
          </div>
          <div v-for="item in topTrendAnomalyRows" :key="`${item.assetIp}-${item.sourceType}-${item.eventType}-${item.title}`" class="trend-anomaly-row" @click="openTrendAnomaly(item)">
            <div>
              <div class="trend-anomaly-title">
                <SeverityBadge :severity="item.severity" />
                <strong>{{ item.title }}</strong>
                <b>{{ item.anomalyScore }}</b>
              </div>
              <span>{{ item.assetIp || '-' }} / {{ item.sourceType || '-' }} / 当前 {{ item.currentCount }} 条，基线 {{ item.baselineCount }} 条，{{ item.changeRatio }}x</span>
              <p>{{ item.reason }}</p>
            </div>
          </div>
          <el-empty v-if="!topTrendAnomalyRows.length" description="暂无趋势异常" :image-size="76" />
    </section>

    <section v-loading="loading" class="soc-panel affected-panel">
      <div class="panel-title">
        <strong>受影响资产排行</strong>
        <span>按未关闭告警数量排序</span>
      </div>
      <div class="asset-bars">
        <div v-for="asset in assets" :key="asset.name" class="asset-bar">
          <span>{{ asset.name }}</span>
          <el-progress :percentage="percentage(asset.value)" :stroke-width="10" />
        </div>
      </div>
      <el-empty v-if="!assets.length" description="暂无受影响资产" :image-size="76" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import RiskCard from '@/components/security/RiskCard.vue'
import RiskTrendChart from '@/components/security/RiskTrendChart.vue'
import SeverityBadge from '@/components/security/SeverityBadge.vue'
import StatusBadge from '@/components/security/StatusBadge.vue'
import { affectedAssets, alertTrend, dashboardOverview, hostAgentOverview, listIncidents, operationsOverview, riskAnalytics, severityDistribution, topTrendAnomalies } from '@/api/soc'
import type { HostAgentItem, HostAgentOverview, IncidentClusterItem, OperationMetricItem, OperationsOverview, RiskAnalytics, TrendAnomalyItem } from '@/api/soc'

const router = useRouter()

const emptyAnalytics: RiskAnalytics = {
  assetRisks: [],
  alertPriorities: [],
  departmentRisks: [],
  operationMetrics: {
    pendingTickets: 0,
    overdueTickets: 0,
    closedTickets: 0,
    slaMetTickets: 0,
    falsePositiveAlerts: 0,
    duplicateGroups: 0,
    slaRate: 100,
    falsePositiveRate: 0,
    averageCloseHours: 0,
  },
  eventTimeline: [],
}

const overview = reactive({ todayAlerts: 0, highAlerts: 0, pendingTickets: 0, assets: 0, unhandledAlerts: 0 })
const analytics = reactive<RiskAnalytics>(structuredClone(emptyAnalytics))
const trend = ref<Array<{ date: string; count: number }>>([])
const severities = ref<Array<{ name: string; value: number }>>([])
const assets = ref<Array<{ name: string; value: number }>>([])
const topIncidents = ref<IncidentClusterItem[]>([])
const topTrendAnomalyRows = ref<TrendAnomalyItem[]>([])
const operations = ref<OperationsOverview>()
const hostAgents = ref<HostAgentOverview>()
const loading = ref(false)
const operationsLoading = ref(false)
const agentLoading = ref(false)
const error = ref('')
const agentError = ref('')

const operationHeroMetrics = computed(() => {
  const codes = [
    'incident.open.count',
    'incident.high_risk.count',
    'ticket.close.rate',
    'recommendation.adoption.rate',
    'client_task.completion.rate',
    'trend.anomaly.count',
  ]
  return codes
    .map((code) => operations.value?.metrics.find((metric) => metric.metricCode === code))
    .filter(Boolean) as OperationMetricItem[]
})

const hostAgentsSummary = computed(() => {
  const rows = currentHostAgents.value
  return {
    total: rows.length,
    online: rows.filter((agent) => agent.status === 'online').length,
    offline: rows.filter((agent) => agent.status !== 'online').length,
  }
})

const currentHostAgents = computed<HostAgentItem[]>(() => {
  return (hostAgents.value?.agents || [])
    .filter((agent) => agent.status === 'online' || Boolean(agent.runtimeControllable))
})

const hostAgentRows = computed<HostAgentItem[]>(() => {
  return currentHostAgents.value
    .slice(0, 8)
})

const historicalAgentCount = computed(() => Math.max(0, (hostAgents.value?.agents.length || 0) - currentHostAgents.value.length))
let agentRefreshTimer: ReturnType<typeof window.setInterval> | undefined

onMounted(() => {
  void load()
  agentRefreshTimer = window.setInterval(() => void loadAgentOverview(), 30_000)
})

onBeforeUnmount(() => {
  if (agentRefreshTimer) window.clearInterval(agentRefreshTimer)
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [overviewRes, trendRes, severityRes, assetRes, analyticsRes] = await Promise.all([
      dashboardOverview(), alertTrend(), severityDistribution(), affectedAssets(), riskAnalytics(),
    ])
    Object.assign(overview, overviewRes.data.data)
    trend.value = trendRes.data.data
    severities.value = severityRes.data.data
    assets.value = assetRes.data.data
    Object.assign(analytics, analyticsRes.data.data)
    operationsLoading.value = true
    try {
      operations.value = (await operationsOverview()).data.data
    } catch {
      operations.value = undefined
    } finally {
      operationsLoading.value = false
    }
    try {
      const incidentRes = await listIncidents({ pageNum: 1, pageSize: 5 })
      topIncidents.value = incidentRes.data.data.records
    } catch {
      topIncidents.value = []
    }
    try {
      const trendAnomalyRes = await topTrendAnomalies(5)
      topTrendAnomalyRows.value = trendAnomalyRes.data.data
    } catch {
      topTrendAnomalyRows.value = []
    }
    await loadAgentOverview()
  } catch {
    error.value = '安全总览数据加载失败，请检查后端服务或 Wazuh/数据库连接。'
  } finally {
    loading.value = false
  }
}

async function loadAgentOverview() {
  agentLoading.value = true
  agentError.value = ''
  try {
    hostAgents.value = (await hostAgentOverview()).data.data
  } catch {
    hostAgents.value = undefined
    agentError.value = 'Agent 状态加载失败，请检查后端服务或 Agent 上报接口。'
  } finally {
    agentLoading.value = false
  }
}

function percentage(value: number) {
  const max = Math.max(1, ...severities.value.map((item) => item.value), ...assets.value.map((item) => item.value))
  return Math.round((value / max) * 100)
}

function incidentAssetLabel(incident: IncidentClusterItem) {
  return incident.assetIp || incident.primaryAssetIp || incident.hostname || incident.primaryHostname || '-'
}

function incidentSourceLabel(incident: IncidentClusterItem) {
  return incident.sourceSummary || incident.sourceTypes || '-'
}

function incidentEvidenceCount(incident: IncidentClusterItem) {
  return incident.evidenceCount ?? ((incident.eventCount || 0) + (incident.alertCount || 0) + (incident.vulnerabilityCount || 0))
}

function openTrendAnomaly(item: TrendAnomalyItem) {
  router.push({
    path: '/soc/external-events',
    query: {
      assetIp: item.assetIp || undefined,
      sourceType: item.sourceType && item.sourceType !== 'multi_source' ? item.sourceType : undefined,
      eventType: item.eventType && !item.eventType.includes('rise') ? item.eventType : undefined,
    },
  })
}

function openMetric(target?: string) {
  if (!target) return
  router.push(target)
}

function metricValue(metric: OperationMetricItem) {
  const percentCodes = ['ticket.close.rate', 'recommendation.adoption.rate', 'client_checkup.coverage.rate', 'client_task.completion.rate', 'playbook.completion.rate']
  if (percentCodes.includes(metric.metricCode)) {
    return `${metric.value}%`
  }
  return String(metric.value ?? '-')
}

function signedMetric(value: number) {
  return value > 0 ? `+${value}` : String(value)
}

function osLabel(osType?: string) {
  const normalized = (osType || '').toLowerCase()
  if (normalized.includes('mac')) return 'macOS'
  if (normalized.includes('win')) return 'Windows'
  if (normalized.includes('linux')) return 'Linux'
  return osType || '未知系统'
}

function formatBytes(value?: number) {
  const bytes = value || 0
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

</script>

<style scoped>
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}
.agent-status-panel {
  padding: 16px;
}
.agent-status-head {
  align-items: flex-start;
}
.agent-status-head > div:first-child {
  min-width: 0;
}
.agent-status-head strong,
.agent-status-head span {
  display: block;
}
.agent-status-head span {
  margin-top: 4px;
  line-height: 1.5;
}
.agent-status-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}
.agent-status-actions > span {
  color: var(--soc-text-muted);
  font-size: 12px;
  font-weight: 650;
}
.agent-status-table {
  overflow-x: auto;
}
.agent-status-table :deep(.el-table) {
  min-width: 880px;
}
.agent-status-name {
  display: grid;
  gap: 3px;
}
.agent-status-name strong {
  overflow: hidden;
  color: var(--soc-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.agent-status-name small {
  overflow: hidden;
  color: var(--soc-text-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.operations-panel {
  padding: 16px;
}
.operation-metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.operation-metric-card {
  min-height: 136px;
  text-align: left;
  border: 1px solid rgba(190, 183, 171, 0.56);
  border-radius: var(--soc-radius-card);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.82), rgba(255, 248, 238, 0.72));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.76);
  cursor: pointer;
  padding: 14px;
}
.operation-metric-card span,
.operation-metric-card small {
  display: block;
  color: var(--soc-text-muted);
  line-height: 1.5;
}
.operation-metric-card strong {
  display: block;
  margin: 8px 0;
  color: var(--soc-text);
  font-size: 28px;
  line-height: 1.1;
}
.operation-detail-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}
.operation-detail-grid > div {
  border: 1px solid rgba(190, 183, 171, 0.46);
  border-radius: var(--soc-radius-card);
  background: rgba(255, 255, 255, 0.56);
  padding: 12px;
}
.operation-detail-grid h3 {
  margin: 0 0 10px;
  font-size: 14px;
}
.operation-detail-grid p {
  margin: 10px 0 0;
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.5;
}
.operation-progress-row {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  min-height: 32px;
  color: var(--soc-text-muted);
  font-size: 12px;
}
.operation-top-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}
.operation-top-grid > div {
  min-width: 0;
  border: 1px solid rgba(190, 183, 171, 0.46);
  border-radius: var(--soc-radius-card);
  background: rgba(255, 255, 255, 0.52);
  padding: 12px;
}
.operation-top-grid h3 {
  margin: 0 0 10px;
  font-size: 14px;
}
.operation-list-row {
  width: 100%;
  min-height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border: 0;
  border-bottom: 1px solid rgba(190, 183, 171, 0.32);
  background: transparent;
  color: var(--soc-text);
  cursor: pointer;
  padding: 8px 0;
  text-align: left;
}
.operation-list-row:last-of-type {
  border-bottom: 0;
}
.operation-list-row span {
  min-width: 0;
}
.operation-list-row strong,
.operation-list-row small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.operation-list-row strong {
  max-width: 240px;
  font-size: 13px;
}
.operation-list-row small {
  max-width: 240px;
  margin-top: 4px;
  color: var(--soc-text-muted);
}
.dashboard-grid,
.management-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(320px, 1fr);
  gap: 14px;
}
.chart-panel,
.side-panel,
.affected-panel,
.incident-panel,
.trend-anomaly-panel,
.management-grid .soc-panel {
  padding: 16px;
}
.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.panel-title span {
  color: var(--soc-text-muted);
  font-size: 13px;
}
.distribution-row {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) 40px;
  gap: 10px;
  align-items: center;
  min-height: 42px;
  padding: 4px 0;
}
.dept-risk-row,
.incident-row,
.trend-anomaly-row {
  border-bottom: 1px solid rgba(190, 183, 171, 0.42);
  padding: 12px 0;
}
.dept-risk-row:first-of-type,
.incident-row:first-of-type,
.trend-anomaly-row:first-of-type {
  padding-top: 0;
}
.dept-risk-row:last-child,
.incident-row:last-child,
.trend-anomaly-row:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}
.dept-risk-row > div,
.incident-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
.dept-risk-row span,
.incident-row span {
  display: block;
  margin: 4px 0 0;
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.5;
}
.incident-row b {
  font-size: 24px;
  color: var(--soc-high);
}
.incident-row {
  cursor: pointer;
}
.incident-row div {
  min-width: 0;
}
.incident-row strong {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.trend-anomaly-panel {
  margin-top: 14px;
}
.trend-anomaly-row {
  cursor: pointer;
}
.trend-anomaly-row span,
.trend-anomaly-row p {
  display: block;
  margin: 5px 0 0;
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.55;
}
.trend-anomaly-row p {
  color: var(--soc-warm-strong);
}
.trend-anomaly-title {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}
.trend-anomaly-title strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.trend-anomaly-title b {
  color: var(--soc-high);
  font-size: 22px;
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.metric-item {
  border: 1px solid var(--soc-border);
  border-radius: var(--soc-radius-card);
  background: rgba(255, 255, 255, 0.58);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72);
  padding: 12px;
}
.metric-item span {
  display: block;
  color: var(--soc-text-muted);
  font-size: 12px;
}
.metric-item strong {
  display: block;
  margin-top: 6px;
  color: var(--soc-text);
  font-size: 22px;
}
.asset-bars {
  display: grid;
  gap: 10px;
}
.asset-bar {
  display: grid;
  grid-template-columns: minmax(180px, 260px) minmax(0, 1fr);
  gap: 12px;
  align-items: center;
}
@media (max-width: 980px) {
  .kpi-grid,
  .operation-metric-grid,
  .operation-detail-grid,
  .operation-top-grid,
  .dashboard-grid,
  .management-grid {
    grid-template-columns: 1fr;
  }
  .metric-grid {
    grid-template-columns: 1fr;
  }
  .asset-bar {
    grid-template-columns: 1fr;
  }
  .agent-status-head {
    display: grid;
    grid-template-columns: 1fr;
  }
  .agent-status-actions {
    justify-content: flex-start;
  }
}
</style>
