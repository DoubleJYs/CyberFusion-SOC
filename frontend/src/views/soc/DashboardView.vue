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
      <div class="soc-page-tags">
        <el-tag effect="plain">SOC 主线</el-tag>
        <el-tag effect="plain">多源融合</el-tag>
        <el-tag effect="plain">处置闭环</el-tag>
      </div>
    </section>

    <section class="source-strip">
      <span>当前运行模式</span>
      <DataSourceBadge source="mock" />
      <strong>运营链路：告警趋势 / 风险分布 / 资产排行 / 工单 SLA / 事件时间线</strong>
    </section>

    <el-tabs v-model="activeView" class="cockpit-tabs">
      <el-tab-pane label="管理驾驶舱" name="management">
        <section class="kpi-grid">
          <RiskCard label="今日告警" :value="overview.todayAlerts" delta="来自告警处置" tone="medium" />
          <RiskCard label="高危告警" :value="overview.highAlerts" delta="critical + high 待处置" tone="critical" />
          <RiskCard label="待处理工单" :value="overview.pendingTickets" delta="未关闭/未归档" tone="high" />
          <RiskCard label="受管资产" :value="overview.assets" delta="P0 资产视图" tone="low" />
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

        <section v-loading="loading" class="soc-panel recommendation-panel">
          <div class="panel-title">
            <strong>今日优先处理建议 Top 5</strong>
            <span>事件簇、漏洞、工单和员工待办综合排序</span>
          </div>
          <div v-for="item in topRecommendationRows" :key="item.key" class="recommendation-row" @click="openRecommendation(item)">
            <div>
              <el-tag :type="priorityTag(item.priority)" effect="plain">{{ priorityText(item.priority) }}</el-tag>
              <strong>{{ item.title }}</strong>
              <span>{{ item.reason }}</span>
            </div>
            <p>{{ item.recommendedAction }}</p>
          </div>
          <el-empty v-if="!topRecommendationRows.length" description="暂无推荐建议" :image-size="76" />
        </section>
      </el-tab-pane>

      <el-tab-pane label="分析员工作台" name="analyst">
        <section v-loading="loading" class="analyst-grid">
          <div class="soc-panel">
            <div class="panel-title">
              <strong>资产风险评分</strong>
              <span>分值来源可解释</span>
            </div>
            <div v-for="asset in unifiedAssetRiskRows" :key="asset.ip" class="score-row">
              <div class="score-head">
                <div>
                  <strong>{{ asset.hostname }}</strong>
                  <span>{{ asset.ip }} / {{ asset.deptName || '未分配部门' }}</span>
                </div>
                <b>{{ asset.score }}</b>
              </div>
              <el-progress :percentage="asset.score" :stroke-width="9" />
              <p>{{ asset.explanation }}</p>
            </div>
            <el-empty v-if="!unifiedAssetRiskRows.length" description="暂无资产评分" :image-size="76" />
          </div>

          <div class="soc-panel">
            <div class="panel-title">
              <strong>告警优先级</strong>
              <span>等级、资产、IOC、重复次数</span>
            </div>
            <div v-for="alert in analytics.alertPriorities" :key="alert.alertUid" class="priority-row">
              <div class="priority-title">
                <SeverityBadge :severity="alert.severity" />
                <strong>{{ alert.score }}</strong>
              </div>
              <b>{{ alert.ruleDescription }}</b>
              <span>{{ alert.assetName }} / {{ alert.assetIp }} / {{ formatTime(alert.eventTime) }}</span>
              <p>{{ alert.reason }}</p>
            </div>
            <el-empty v-if="!analytics.alertPriorities.length" description="暂无告警优先级数据" :image-size="76" />
          </div>
        </section>

        <section v-loading="loading" class="soc-panel timeline-panel">
          <div class="panel-title">
            <strong>安全事件时间线</strong>
            <span>从事件产生到处置关闭</span>
          </div>
          <el-timeline>
            <el-timeline-item
              v-for="item in analytics.eventTimeline"
              :key="`${item.type}-${item.occurredAt}-${item.title}`"
              :timestamp="formatTime(item.occurredAt)"
              placement="top"
            >
              <div class="timeline-item">
                <SeverityBadge v-if="item.severity" :severity="item.severity" />
                <strong>{{ item.type }}：{{ item.title }}</strong>
                <span>{{ item.assetName || item.operatorName || item.status }}</span>
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-if="!analytics.eventTimeline.length" description="暂无事件时间线" :image-size="76" />
        </section>
      </el-tab-pane>
    </el-tabs>

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
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import DataSourceBadge from '@/components/security/DataSourceBadge.vue'
import RiskCard from '@/components/security/RiskCard.vue'
import RiskTrendChart from '@/components/security/RiskTrendChart.vue'
import SeverityBadge from '@/components/security/SeverityBadge.vue'
import { affectedAssets, alertTrend, dashboardOverview, listIncidents, riskAnalytics, severityDistribution, topRecommendations, topRiskAssets } from '@/api/soc'
import type { AssetRiskProfile, IncidentClusterItem, RecommendationItem, RiskAnalytics } from '@/api/soc'

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

const activeView = ref('management')
const overview = reactive({ todayAlerts: 0, highAlerts: 0, pendingTickets: 0, assets: 0, unhandledAlerts: 0 })
const analytics = reactive<RiskAnalytics>(structuredClone(emptyAnalytics))
const trend = ref<Array<{ date: string; count: number }>>([])
const severities = ref<Array<{ name: string; value: number }>>([])
const assets = ref<Array<{ name: string; value: number }>>([])
const topRiskProfiles = ref<AssetRiskProfile[]>([])
const topIncidents = ref<IncidentClusterItem[]>([])
const topRecommendationRows = ref<RecommendationItem[]>([])
const loading = ref(false)
const error = ref('')

const unifiedAssetRiskRows = computed(() => {
  if (topRiskProfiles.value.length) {
    return topRiskProfiles.value.map((profile) => ({
      hostname: profile.asset.hostname,
      ip: profile.asset.ip,
      deptName: profile.asset.deptName,
      score: profile.snapshot.score,
      explanation: profile.statusReason || profile.recommendationSummary,
    }))
  }
  return analytics.assetRisks
})

onMounted(load)

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
    try {
      const topRiskRes = await topRiskAssets(5)
      topRiskProfiles.value = topRiskRes.data.data
    } catch {
      topRiskProfiles.value = []
    }
    try {
      const incidentRes = await listIncidents({ pageNum: 1, pageSize: 5 })
      topIncidents.value = incidentRes.data.data.records
    } catch {
      topIncidents.value = []
    }
    try {
      const recommendationRes = await topRecommendations(5)
      topRecommendationRows.value = recommendationRes.data.data
    } catch {
      topRecommendationRows.value = []
    }
  } catch {
    error.value = '安全总览数据加载失败，请检查后端服务或 Wazuh/数据库连接。'
  } finally {
    loading.value = false
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

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

function priorityTag(priority: string) {
  if (priority === 'critical' || priority === 'high') return 'danger'
  if (priority === 'medium') return 'warning'
  return 'info'
}

function priorityText(priority: string) {
  if (priority === 'critical') return '严重'
  if (priority === 'high') return '高'
  if (priority === 'medium') return '中'
  return '低'
}

function openRecommendation(item: RecommendationItem) {
  if (item.relatedBizType === 'incident') {
    router.push({ path: '/soc/incidents', query: { keyword: String(item.relatedBizId) } })
  } else if (item.relatedBizType === 'ticket' || item.relatedBizType === 'playbook_task' || item.relatedBizType === 'client_task') {
    router.push({ path: '/soc/tickets', query: { keyword: item.title } })
  } else if (item.relatedBizType === 'vulnerability') {
    router.push({ path: '/soc/vulnerabilities', query: { keyword: item.title } })
  } else if (item.assetIp) {
    router.push({ path: '/soc/assets', query: { keyword: item.assetIp } })
  }
}
</script>

<style scoped>
.cockpit-tabs {
  --el-border-color-light: var(--soc-border);
}
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}
.source-strip {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid rgba(190, 183, 171, 0.52);
  border-radius: var(--soc-radius-card);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.82), rgba(255, 246, 232, 0.64));
  box-shadow: var(--soc-shadow-soft), var(--soc-glass-highlight);
  backdrop-filter: blur(18px) saturate(1.12);
  padding: 10px 14px;
  color: var(--soc-text-muted);
  font-size: 13px;
}
.source-strip strong {
  color: var(--soc-text);
  font-weight: 500;
}
.dashboard-grid,
.management-grid,
.analyst-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(320px, 1fr);
  gap: 14px;
}
.chart-panel,
.side-panel,
.affected-panel,
.incident-panel,
.timeline-panel,
.management-grid .soc-panel,
.analyst-grid .soc-panel {
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
.recommendation-row,
.score-row,
.priority-row {
  border-bottom: 1px solid rgba(190, 183, 171, 0.42);
  padding: 12px 0;
}
.dept-risk-row:first-of-type,
.incident-row:first-of-type,
.recommendation-row:first-of-type,
.score-row:first-of-type,
.priority-row:first-of-type {
  padding-top: 0;
}
.dept-risk-row:last-child,
.incident-row:last-child,
.recommendation-row:last-child,
.score-row:last-child,
.priority-row:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}
.dept-risk-row > div,
.incident-row,
.score-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
.dept-risk-row span,
.incident-row span,
.score-row span,
.priority-row span,
.score-row p,
.priority-row p {
  display: block;
  margin: 4px 0 0;
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.5;
}
.score-head b,
.incident-row b,
.priority-title strong {
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
.recommendation-panel {
  margin-top: 14px;
}
.recommendation-row {
  display: grid;
  gap: 8px;
  cursor: pointer;
}
.recommendation-row > div {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 6px 10px;
  align-items: center;
}
.recommendation-row strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recommendation-row span {
  grid-column: 1 / -1;
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.5;
}
.recommendation-row p {
  margin: 0;
  color: var(--soc-warm-strong);
  font-size: 12px;
  line-height: 1.55;
}
.priority-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
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
.timeline-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 28px;
}
.timeline-item span {
  color: var(--soc-text-muted);
  font-size: 12px;
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
  .dashboard-grid,
  .management-grid,
  .analyst-grid {
    grid-template-columns: 1fr;
  }
  .source-strip {
    align-items: flex-start;
    flex-direction: column;
  }
  .metric-grid {
    grid-template-columns: 1fr;
  }
  .asset-bar {
    grid-template-columns: 1fr;
  }
}
</style>
