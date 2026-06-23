<template>
  <div class="page-shell demo-range-page">
    <section class="soc-page-hero">
      <div>
        <span class="soc-page-kicker">CYBERFUSION DEMO RANGE</span>
        <h1>安全验证</h1>
        <p>这个页面帮你导入演示批次，并串起事件、告警、工单、报告和通知记录。</p>
      </div>
      <div class="soc-page-tags">
        <el-tag effect="plain">离线演示</el-tag>
        <el-tag effect="plain">证据闭环</el-tag>
        <el-tag effect="plain">只读串联</el-tag>
      </div>
    </section>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false">
      <template #default>
        <el-button size="small" @click="load">重试</el-button>
      </template>
    </el-alert>

    <section class="soc-panel wizard-status-panel">
      <div class="wizard-status-head">
        <div>
          <span>当前批次</span>
          <strong>{{ currentBatch.batchId }}</strong>
          <em>{{ currentBatch.targetAsset }} · {{ currentBatch.source }}</em>
        </div>
        <div class="wizard-progress-copy">
          <strong>第 {{ activeStep + 1 }} / {{ wizardSteps.length }} 步</strong>
          <span>{{ activeStepInfo.title }} · {{ stepProgress }}%</span>
        </div>
      </div>
      <el-progress :percentage="stepProgress" :stroke-width="10" />
      <el-steps :active="activeStep" finish-status="success" align-center class="wizard-steps">
        <el-step v-for="step in wizardSteps" :key="step.key" :title="step.title" :description="step.shortTitle" />
      </el-steps>
    </section>

    <section class="soc-panel demo-outcome-panel">
      <div class="panel-title">
        <div>
          <strong>演示结果总览</strong>
          <span>导入批次后，用一组业务指标说明“证据 -> 事件簇 -> 风险 -> 推荐 -> 工单 -> 员工待办 -> 报告”。</span>
        </div>
        <el-button @click="goReports">生成或查看报告</el-button>
      </div>
      <div class="demo-outcome-grid">
        <article v-for="item in demoOutcomeCards" :key="item.label" class="demo-outcome-card">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <p>{{ item.description }}</p>
        </article>
      </div>
      <div class="recommendation-strip">
        <strong>推荐动作 Top 3</strong>
        <div v-if="topRecommendationRows.length" class="recommendation-list">
          <button v-for="item in topRecommendationRows" :key="item.key" type="button" @click="goRecommendations">
            <span>{{ item.title }}</span>
            <em>{{ item.recommendedAction }}</em>
          </button>
        </div>
        <el-empty v-else description="暂无推荐动作，可先导入批次并重新计算风险" :image-size="64" />
      </div>
    </section>

    <section v-loading="loading" class="wizard-shell">
      <main class="wizard-main">
        <section v-if="activeStep === 0" class="soc-panel wizard-step-panel">
          <div class="panel-title">
            <div>
              <strong>选择场景</strong>
              <span>选择本次客户演示要讲的风险场景，页面只展示该场景需要的证据说明。</span>
            </div>
            <el-button text @click="openTopologyDetails">查看拓扑详情</el-button>
          </div>
          <div class="case-list wizard-case-list">
            <article
              v-for="demoCase in demoCases"
              :key="demoCase.id"
              class="case-item"
              :class="{ active: selectedCase.id === demoCase.id }"
              role="button"
              tabindex="0"
              :aria-label="`选择 ${demoCase.title}`"
              @click="selectCase(demoCase)"
              @keydown.enter.prevent="selectCase(demoCase)"
              @keydown.space.prevent="selectCase(demoCase)"
            >
              <div>
                <span>{{ demoCase.category }}</span>
                <strong>{{ demoCase.title }}</strong>
                <p>{{ demoCase.summary }}</p>
              </div>
              <el-tag :type="demoCase.tagType" effect="plain">{{ demoCase.primarySource.toUpperCase() }}</el-tag>
            </article>
          </div>
          <div class="selected-case-panel">
            <div>
              <span>已选场景</span>
              <strong>{{ selectedCase.title }}</strong>
              <p>{{ selectedCase.description }}</p>
            </div>
            <el-button @click="openCaseDetails">查看详情</el-button>
          </div>
        </section>

        <section v-else-if="activeStep === 1" class="soc-panel wizard-step-panel">
          <div class="panel-title">
            <div>
              <strong>导入证据</strong>
              <span>导入固定离线样例，不执行攻击、不触发扫描、不访问外部目标。</span>
            </div>
            <el-button :icon="Refresh" text @click="load">刷新</el-button>
          </div>
          <div class="step-metric-grid">
            <RiskCard label="安全记录" :value="currentBatch.eventCount" delta="本批次事件" tone="low" />
            <RiskCard label="漏洞记录" :value="evidenceChain?.summary.vulnerabilityCount || vulnerabilities.length" delta="Trivy 离线结果" tone="medium" />
            <RiskCard label="拦截证据" :value="evidenceChain?.summary.blockedCount || 0" delta="WAF / 网关" tone="high" />
          </div>
          <el-empty v-if="!lastBatchResult && !hasEvidence" description="还没有演示证据，请从右侧导入离线批次" />
          <div v-else class="evidence-grid compact">
            <article v-for="item in evidenceCards" :key="item.source" class="evidence-card">
              <header>
                <strong>{{ item.title }}</strong>
                <el-tag :type="item.count ? 'success' : 'info'" effect="plain">{{ item.count ? '有证据' : '待导入' }}</el-tag>
              </header>
              <p>{{ item.summary }}</p>
              <dl>
                <div><dt>记录</dt><dd>{{ item.count }}</dd></div>
                <div><dt>高风险</dt><dd>{{ item.highRisk }}</dd></div>
                <div><dt>最新时间</dt><dd>{{ item.latestAt }}</dd></div>
              </dl>
            </article>
          </div>
          <div v-if="lastBatchResult" class="batch-result-strip">
            <span>本次导入：事件 {{ lastBatchResult.importedEvents }}，告警 {{ lastBatchResult.createdAlerts }}，漏洞 {{ lastBatchResult.createdVulnerabilities }}</span>
            <el-button text @click="openImportDetails">查看详情</el-button>
          </div>
        </section>

        <section v-else-if="activeStep === 2" class="soc-panel wizard-step-panel">
          <div class="panel-title">
            <div>
              <strong>查看告警</strong>
              <span>只关注本批次关联告警，用于确认风险是否进入处置队列。</span>
            </div>
            <el-button @click="goBatchAlerts">查看告警处置</el-button>
          </div>
          <div class="step-metric-grid">
            <RiskCard label="关联告警" :value="evidenceChain?.summary.alertCount || currentBatch.alertCount" delta="soc_alert" tone="high" />
            <RiskCard label="高危告警" :value="highRiskAlertCount" delta="critical / high" tone="critical" />
            <RiskCard label="已转工单" :value="ticketedAlertCount" delta="ticketed" tone="medium" />
          </div>
          <el-empty v-if="!alertPreview.length" description="暂无本批次告警，请先导入证据或刷新证据链" />
          <div v-else class="wizard-list">
            <article v-for="alert in alertPreview" :key="alert.id" class="wizard-list-row">
              <div>
                <span>{{ alert.alertUid }}</span>
                <strong>{{ alert.ruleName || alert.ruleDescription }}</strong>
                <em>{{ alert.assetName }} / {{ alert.assetIp }} · {{ alert.status }}</em>
              </div>
              <div>
                <el-tag :type="severityTag(alert.severity)" effect="plain">{{ alert.severity }}</el-tag>
                <el-button text @click="openAlertDetails(alert)">查看详情</el-button>
              </div>
            </article>
          </div>
        </section>

        <section v-else-if="activeStep === 3" class="soc-panel wizard-step-panel">
          <div class="panel-title">
            <div>
              <strong>转工单</strong>
              <span>从告警进入工单处置链路，保留本批次来源和时间线。</span>
            </div>
            <el-button @click="goTickets">查看工单中心</el-button>
          </div>
          <div class="step-metric-grid">
            <RiskCard label="工单数量" :value="evidenceChain?.summary.ticketCount || tickets.length" delta="soc_ticket" tone="medium" />
            <RiskCard label="待处理告警" :value="unticketedAlertCount" delta="可转工单" tone="high" />
            <RiskCard label="目标资产" :value="currentBatch.targetAsset" delta="本批次上下文" tone="low" />
          </div>
          <el-empty v-if="!ticketPreview.length" description="暂无工单。可从右侧进入告警详情并转工单。" />
          <div v-else class="wizard-list">
            <article v-for="ticket in ticketPreview" :key="ticket.id" class="wizard-list-row">
              <div>
                <span>{{ ticket.ticketNo }}</span>
                <strong>{{ ticket.title }}</strong>
                <em>{{ ticket.assigneeName || '未分派' }} · {{ ticket.status }}</em>
              </div>
              <div>
                <el-tag effect="plain">{{ ticket.severity }}</el-tag>
                <el-button text @click="openTicketDetails(ticket)">查看详情</el-button>
              </div>
            </article>
          </div>
        </section>

        <section v-else class="soc-panel wizard-step-panel">
          <div class="panel-title">
            <div>
              <strong>生成报告</strong>
              <span>基于当前批次生成安全验证报告，并保留通知 dry-run 日志入口。</span>
            </div>
            <el-button @click="goReports">查看报告中心</el-button>
          </div>
          <div class="step-metric-grid">
            <RiskCard label="报告数量" :value="evidenceChain?.summary.reportCount || reports.length" delta="security_validation" tone="safe" />
            <RiskCard label="通知留痕" :value="evidenceChain?.summary.notificationLogCount || 0" delta="dry-run" tone="low" />
            <RiskCard label="覆盖来源" :value="sourceCoverageText" delta="WAF/ZAP/Trivy/Wazuh/IDS" tone="medium" />
          </div>
          <el-empty v-if="!reportPreview.length" description="暂无本批次报告，请从右侧生成安全验证报告" />
          <div v-else class="wizard-list">
            <article v-for="report in reportPreview" :key="report.id" class="wizard-list-row">
              <div>
                <span>{{ report.reportNo }}</span>
                <strong>{{ report.title }}</strong>
                <em>{{ report.reportType }} · {{ report.status }}</em>
              </div>
              <div>
                <el-button text @click="openReportDetails(report)">查看详情</el-button>
              </div>
            </article>
          </div>
          <div class="batch-result-strip">
            <span>通知保持 dry-run，只写 soc_notification_log，不发送邮件、Webhook 或外部通知。</span>
            <el-button text :loading="notifying" @click="sendDryRunNotification">写入 dry-run 日志</el-button>
          </div>
        </section>
      </main>

      <aside class="soc-panel wizard-next-panel">
        <div class="next-card-head">
          <span>下一步操作</span>
          <strong>{{ activeStepInfo.title }}</strong>
          <p>{{ activeStepInfo.description }}</p>
        </div>
        <el-button
          v-permission="nextAction.permission"
          type="primary"
          :loading="nextAction.loading"
          :disabled="nextAction.disabled"
          @click="nextAction.handler"
        >
          {{ nextAction.label }}
        </el-button>
        <div class="step-nav">
          <el-button :disabled="activeStep === 0" @click="activeStep -= 1">上一步</el-button>
          <el-button :disabled="activeStep >= wizardSteps.length - 1" @click="activeStep += 1">跳到下一步</el-button>
        </div>
        <div class="shortcut-list">
          <button type="button" @click="goBatchExternalEvents">
            <strong>查看证据</strong>
            <span>{{ currentBatch.eventCount }} 条安全记录</span>
          </button>
          <button type="button" @click="goBatchAlerts">
            <strong>查看告警</strong>
            <span>{{ currentBatch.alertCount }} 条关联告警</span>
          </button>
          <button type="button" @click="goTickets">
            <strong>查看工单</strong>
            <span>{{ evidenceChain?.summary.ticketCount || tickets.length }} 个工单</span>
          </button>
          <button type="button" @click="goReports">
            <strong>生成报告</strong>
            <span>{{ evidenceChain?.summary.reportCount || reports.length }} 份报告</span>
          </button>
        </div>
        <el-button text @click="openBatchDetails">查看批次详情</el-button>
      </aside>
    </section>

    <section class="soc-panel validation-chain-panel">
      <div class="panel-title">
        <div>
          <strong>本次验证事件链</strong>
          <span>把当前批次的 WAF、ZAP、Trivy、Wazuh、Suricata、Zeek 证据聚合成可处置事件簇。</span>
        </div>
        <div class="chain-actions">
          <el-button :loading="correlating" @click="runCorrelation">执行关联</el-button>
          <el-button @click="goIncidents">查看安全事件簇</el-button>
        </div>
      </div>
      <el-empty v-if="!incidentPreview.length" description="暂无事件链。导入演示批次后点击“执行关联”。" :image-size="80" />
      <div v-else class="wizard-list">
        <article v-for="incident in incidentPreview" :key="incident.id" class="wizard-list-row">
          <div>
            <span>{{ incident.clusterNo }}</span>
            <strong>{{ incident.title }}</strong>
            <em>{{ incident.summary || incidentSourceLabel(incident) }}</em>
          </div>
          <div>
            <el-tag effect="plain">{{ incident.severity }}</el-tag>
            <el-tag effect="plain">{{ incidentEvidenceCount(incident) }} 条证据</el-tag>
            <el-button text @click="goIncidentDetail(incident)">查看链路</el-button>
          </div>
        </article>
      </div>
    </section>

    <el-drawer v-model="detailDrawer.visible" :title="detailDrawer.title" size="520px">
      <div class="detail-drawer-stack">
        <div v-if="detailDrawer.rows.length" class="soc-drawer-grid">
          <template v-for="row in detailDrawer.rows" :key="row.label">
            <span>{{ row.label }}</span>
            <strong>{{ row.value || '-' }}</strong>
          </template>
        </div>
        <div v-if="detailDrawer.tags.length" class="tag-row">
          <el-tag v-for="tag in detailDrawer.tags" :key="tag" effect="plain">{{ tag }}</el-tag>
        </div>
        <div v-if="detailDrawer.lines.length" class="drawer-lines">
          <strong>说明</strong>
          <span v-for="line in detailDrawer.lines" :key="line">{{ line }}</span>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import RiskCard from '@/components/security/RiskCard.vue'
import {
  demoRangeEvidenceChain,
  correlateIncidents,
  externalEventSummary,
  generateReport,
  importDemoRangeBatch,
  listIncidents,
  listAlerts,
  listExternalEvents,
  listReports,
  listTickets,
  listVulnerabilities,
  operationsOverview,
  topRecommendations,
  sendShuffleDemoNotification,
  type AlertItem,
  type DemoRangeBatchImportResult,
  type DemoRangeEvidenceChain,
  type ExternalEventItem,
  type ExternalSourceSummary,
  type IncidentClusterItem,
  type OperationsOverview,
  type RecommendationItem,
  type ReportItem,
  type TicketItem,
  type VulnerabilityItem,
} from '@/api/soc'

type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

interface DemoCase {
  id: string
  category: string
  title: string
  summary: string
  description: string
  primarySource: string
  sources: string[]
  eventTypes: string[]
  expectedEvidence: string[]
  tagType: TagType
}

interface ParsedEvent {
  demoCaseId?: string
  demoBatchId?: string
  batchId?: string
  evidenceSummary?: string
  targetUrl?: string
  httpMethod?: string
  httpStatus?: number | string
  action?: string
  requestId?: string
  engine?: string
}

interface WizardStep {
  key: string
  title: string
  shortTitle: string
  description: string
}

interface DetailDrawerState {
  visible: boolean
  title: string
  rows: Array<{ label: string; value?: string | number }>
  tags: string[]
  lines: string[]
}

const router = useRouter()
const loading = ref(false)
const importing = ref(false)
const generating = ref(false)
const notifying = ref(false)
const correlating = ref(false)
const error = ref('')
const activeStep = ref(0)
const lastBatchResult = ref<DemoRangeBatchImportResult>()
const evidenceChain = ref<DemoRangeEvidenceChain>()
const externalEvents = ref<ExternalEventItem[]>([])
const externalTotal = ref(0)
const alerts = ref<AlertItem[]>([])
const alertTotal = ref(0)
const vulnerabilities = ref<VulnerabilityItem[]>([])
const tickets = ref<TicketItem[]>([])
const reports = ref<ReportItem[]>([])
const incidents = ref<IncidentClusterItem[]>([])
const operations = ref<OperationsOverview>()
const topRecommendationRows = ref<RecommendationItem[]>([])
const sourceSummary = ref<ExternalSourceSummary[]>([])
const detailDrawer = ref<DetailDrawerState>({
  visible: false,
  title: '',
  rows: [],
  tags: [],
  lines: [],
})

const wizardSteps: WizardStep[] = [
  { key: 'scenario', title: '选择场景', shortTitle: '选用例', description: '先确定本次演示要讲的风险场景。' },
  { key: 'evidence', title: '导入证据', shortTitle: '导入离线证据', description: '导入固定离线样例，生成安全记录、漏洞和告警。' },
  { key: 'alerts', title: '查看告警', shortTitle: '确认告警', description: '检查本批次告警是否进入处置队列。' },
  { key: 'tickets', title: '转工单', shortTitle: '进入工单', description: '从告警进入工单链路，保留时间线。' },
  { key: 'report', title: '生成报告', shortTitle: '输出报告', description: '生成本次安全验证报告，并保留 dry-run 通知日志。' },
]

const demoCases: DemoCase[] = [
  {
    id: 'access-control-risk',
    category: '访问控制风险',
    title: '越权路径被网关识别',
    summary: '使用 WAF / 网关审计日志证明受限路径请求已被识别并拦截。',
    description: '面向管理端路径、敏感接口和未授权访问尝试，只展示离线审计日志与拦截证据，不提供可复用攻击步骤。',
    primarySource: 'waf',
    sources: ['WAF', 'CyberFusion Import', 'soc_external_event', 'soc_alert'],
    eventTypes: ['waf_block', 'api_abuse_block'],
    expectedEvidence: ['sourceType=waf 写入多源事件', 'action=block 或 deny', 'ruleId / ruleName 标识访问控制规则', 'linkAlerts=true 时关联统一告警'],
    tagType: 'warning',
  },
  {
    id: 'upload-policy-risk',
    category: '上传策略风险',
    title: '上传策略命中拦截',
    summary: '展示上传入口策略命中后的 WAF 证据和 SOC 告警入口。',
    description: '验证上传策略是否记录目标 URL、请求方法、响应状态、规则名称和请求 ID，只保留演示字段，不保存真实文件内容。',
    primarySource: 'waf',
    sources: ['WAF', '网关审计', 'soc_external_event'],
    eventTypes: ['upload_block'],
    expectedEvidence: ['eventType=upload_block', 'targetUrl / httpMethod / httpStatus 可追溯', 'evidenceSummary 说明拦截原因', 'requestId 可用于报告引用'],
    tagType: 'danger',
  },
  {
    id: 'input-validation-risk',
    category: '输入校验风险',
    title: '输入校验风险被识别',
    summary: '汇总 WAF detect/block 与 ZAP Web 风险发现，进入事件或漏洞复核。',
    description: '面向表单、查询参数和 API 输入校验风险，页面只展示检测结论、规则和目标 URL，不展示攻击载荷。',
    primarySource: 'zap',
    sources: ['ZAP', 'WAF', 'soc_external_event', 'soc_vulnerability'],
    eventTypes: ['waf_detect', 'waf_block'],
    expectedEvidence: ['ZAP 风险或 WAF detect 进入统一事件', '高风险记录可联动告警', '目标 URL 与规则名称可在详情页追溯'],
    tagType: 'primary',
  },
  {
    id: 'dependency-vulnerability',
    category: '依赖漏洞',
    title: '依赖漏洞进入漏洞中心',
    summary: '用 Trivy 离线结果展示镜像或依赖风险进入 soc_vulnerability。',
    description: '展示软件包、版本、严重性和修复建议，支撑漏洞中心复核与报告输出，不触发真实扫描。',
    primarySource: 'trivy',
    sources: ['Trivy', 'soc_vulnerability', 'soc_report'],
    eventTypes: [],
    expectedEvidence: ['sourceType=trivy 的漏洞记录', 'CVE、软件包和修复建议可见', '报告中心可生成综合安全报告'],
    tagType: 'warning',
  },
  {
    id: 'fim',
    category: 'FIM',
    title: '文件完整性变更复核',
    summary: '用 Wazuh / FIM 事件说明主机侧配置变更已进入 SOC 复核。',
    description: '用于展示文件完整性、配置变更和主机侧风险证据，串联到统一告警和工单闭环。',
    primarySource: 'wazuh',
    sources: ['Wazuh', 'FIM', 'soc_alert', 'soc_ticket'],
    eventTypes: ['fim', 'file_integrity'],
    expectedEvidence: ['主机侧告警或 FIM 记录可见', '资产 IP 与主机名可追踪', '可从告警处置转工单'],
    tagType: 'success',
  },
  {
    id: 'network-ids',
    category: '网络 IDS',
    title: '网络侧 IDS 证据汇聚',
    summary: '汇总 Suricata 告警和 Zeek 连接日志，形成网络侧证据摘要。',
    description: '展示连接日志、IDS 规则、源/目的 IP 和高风险事件数量，支持从网络证据进入统一告警。',
    primarySource: 'suricata',
    sources: ['Suricata', 'Zeek', 'soc_external_event', 'soc_alert'],
    eventTypes: ['alert', 'conn'],
    expectedEvidence: ['Suricata / Zeek 多源事件可见', '高风险 IDS 事件可关联告警', '网络证据可进入日报或周报'],
    tagType: 'primary',
  },
]

const selectedCase = ref<DemoCase>(demoCases[0])

const activeStepInfo = computed(() => wizardSteps[activeStep.value] || wizardSteps[0])
const stepProgress = computed(() => Math.round(((activeStep.value + 1) / wizardSteps.length) * 100))
const parsedEvents = computed(() => externalEvents.value.map((event) => ({ event, parsed: parseNormalizedEvent(event) })))

const currentBatch = computed(() => {
  const batchEvent = parsedEvents.value.find(({ parsed }) => parsed.demoBatchId || parsed.batchId || parsed.demoCaseId)
  const targetAsset = findFirst([
    ...externalEvents.value.map((item) => item.assetIp || item.destIp || item.assetName),
    ...alerts.value.map((item) => item.assetIp || item.assetName),
    ...vulnerabilities.value.map((item) => item.assetIp || item.assetName),
  ])
  return {
    batchId: lastBatchResult.value?.batchId || batchEvent?.parsed.demoBatchId || batchEvent?.parsed.batchId || batchEvent?.parsed.demoCaseId || 'DEMO-RANGE-OFFLINE',
    targetAsset: targetAsset || '10.20.1.15 / prod-app-01',
    startedAt: earliestTime([...externalEvents.value.map((item) => item.eventTime), ...alerts.value.map((item) => item.eventTime)]) || '-',
    eventCount: lastBatchResult.value?.importedEvents ?? externalTotal.value,
    alertCount: lastBatchResult.value?.createdAlerts ?? alertTotal.value,
    source: lastBatchResult.value ? '本次导入' : batchEvent ? '样例字段' : '接口聚合',
  }
})

const topologyNodes = computed(() => [
  node('cyberfusion', 'CyberFusion', '统一接入与闭环', true, `${externalTotal.value} 条多源事件 / ${alertTotal.value} 条告警`, 'warm'),
  node('waf', 'WAF', '网关审计与拦截证据', sourceCount('waf') > 0, `${sourceCount('waf')} 条 WAF 事件`, 'warm'),
  node('target', '靶站', currentBatch.value.targetAsset, Boolean(currentBatch.value.targetAsset), '离线演示目标资产', 'blue'),
  node('zap', 'ZAP', 'Web 风险发现', sourceCount('zap') > 0, `${sourceCount('zap')} 条 Web 事件`, 'blue'),
  node('trivy', 'Trivy', '依赖漏洞导入', vulnerabilities.value.some((item) => item.sourceType === 'trivy'), `${vulnerabilities.value.length} 条漏洞记录`, 'blue'),
  node('wazuh', 'Wazuh', '主机告警与 FIM', hasSourceOrAlert('wazuh'), `${sourceCount('wazuh')} 条事件 / ${alertSourceCount('wazuh')} 条告警`, 'green'),
  node('suricata', 'Suricata', 'IDS EVE 证据', sourceCount('suricata') > 0, `${sourceCount('suricata')} 条 IDS 事件`, 'cyan'),
  node('zeek', 'Zeek', '连接日志证据', sourceCount('zeek') > 0, `${sourceCount('zeek')} 条连接事件`, 'cyan'),
])

const evidenceCards = computed(() => [
  evidence('waf', 'WAF / 网关', '拦截、识别、请求 ID、规则名和目标 URL 的离线审计证据。'),
  evidence('zap', 'ZAP', 'Web 风险发现、目标 URL 和风险等级摘要。'),
  evidence('trivy', 'Trivy', '依赖漏洞、CVE、软件包版本和修复建议。', vulnerabilities.value.filter((item) => item.sourceType === 'trivy')),
  evidence('wazuh', 'Wazuh', '主机告警、认证失败、配置变更和 FIM 证据。'),
  evidence('suricata', 'Suricata', 'IDS 告警规则、源/目的 IP 和高风险网络事件。'),
  evidence('zeek', 'Zeek', '连接日志和协议元数据，用于网络侧证据补充。'),
])

const hasEvidence = computed(() => evidenceCards.value.some((item) => item.count > 0))
const alertPreview = computed(() => (evidenceChain.value?.alerts?.length ? evidenceChain.value.alerts : alerts.value).slice(0, 5))
const ticketPreview = computed(() => (evidenceChain.value?.tickets?.length ? evidenceChain.value.tickets : tickets.value).slice(0, 5))
const reportPreview = computed(() => (evidenceChain.value?.reports?.length ? evidenceChain.value.reports : reports.value).slice(0, 5))
const incidentPreview = computed(() => incidents.value.slice(0, 5))
const demoOutcomeCards = computed(() => {
  const topAsset = operations.value?.topRiskAssets?.[0]
  const clientTasks = operations.value?.clientTasks
  return [
    { label: 'Batch ID', value: currentBatch.value.batchId, description: '本次安全验证的聚合键和报告上下文。' },
    { label: '多源证据', value: evidenceChain.value?.summary.eventCount || currentBatch.value.eventCount, description: 'WAF/ZAP/Trivy/Wazuh/Suricata/Zeek 归一化记录。' },
    { label: '事件簇', value: incidents.value.length, description: '解释性关联引擎生成的安全事件链。' },
    { label: '最高风险资产', value: topAsset ? `${topAsset.hostname || topAsset.assetIp} / ${topAsset.riskScore}` : '待计算', description: topAsset ? `${topAsset.riskLevel} · ${topAsset.assetIp}` : '导入证据后可重新计算风险评分。' },
    { label: '工单状态', value: `${evidenceChain.value?.summary.ticketCount || tickets.value.length} 个`, description: `当前待处理告警 ${unticketedAlertCount.value} 条。` },
    { label: '员工待办', value: clientTasks ? `${clientTasks.completedTasks}/${clientTasks.totalTasks}` : '待同步', description: clientTasks ? `完成率 ${clientTasks.completionRate}% / 体检覆盖 ${clientTasks.checkupCoverageRate}%` : '来自处置剧本的员工协同任务。' },
    { label: 'dry-run 通知', value: evidenceChain.value?.summary.notificationLogCount || 0, description: '只写 soc_notification_log，不真实发送外部通知。' },
  ]
})
const highRiskAlertCount = computed(() => alertPreview.value.filter((item) => ['critical', 'high'].includes((item.severity || '').toLowerCase())).length)
const ticketedAlertCount = computed(() => alertPreview.value.filter((item) => item.ticketId || item.status === 'ticketed').length)
const unticketedAlertCount = computed(() => Math.max(0, alertPreview.value.length - ticketedAlertCount.value))
const sourceCoverageText = computed(() => {
  if (evidenceChain.value?.summary.sourceCoverage) return evidenceChain.value.summary.sourceCoverage
  const covered = evidenceCards.value.filter((item) => item.count > 0).map((item) => item.source.toUpperCase())
  return covered.length ? covered.join(' / ') : '待导入'
})
const nextAction = computed(() => {
  if (activeStep.value === 0) {
    return {
      label: '确认场景并继续',
      loading: false,
      disabled: false,
      permission: undefined,
      handler: () => {
        activeStep.value = 1
      },
    }
  }
  if (activeStep.value === 1) {
    return {
      label: '导入离线证据',
      loading: importing.value,
      disabled: false,
      permission: 'soc:demo-range:import',
      handler: confirmImportBatch,
    }
  }
  if (activeStep.value === 2) {
    return {
      label: '打开告警详情',
      loading: false,
      disabled: false,
      permission: undefined,
      handler: goFirstAlert,
    }
  }
  if (activeStep.value === 3) {
    return {
      label: ticketPreview.value.length ? '查看工单时间线' : '进入告警转工单',
      loading: false,
      disabled: false,
      permission: undefined,
      handler: goTicketTimeline,
    }
  }
  return {
    label: '生成安全验证报告',
    loading: generating.value,
    disabled: false,
    permission: undefined,
    handler: confirmGenerateReport,
  }
})

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [eventRes, summaryRes, alertRes, vulnerabilityRes, ticketRes, reportRes] = await Promise.all([
      listExternalEvents({ pageNum: 1, pageSize: 100 }),
      externalEventSummary(),
      listAlerts({ pageNum: 1, pageSize: 100 }),
      listVulnerabilities({ pageNum: 1, pageSize: 100 }),
      listTickets({ pageNum: 1, pageSize: 50 }),
      listReports({ pageNum: 1, pageSize: 20 }),
    ])
    externalEvents.value = eventRes.data.data.records
    externalTotal.value = eventRes.data.data.total
    sourceSummary.value = summaryRes.data.data
    alerts.value = alertRes.data.data.records
    alertTotal.value = alertRes.data.data.total
    vulnerabilities.value = vulnerabilityRes.data.data.records
    tickets.value = ticketRes.data.data.records
    reports.value = reportRes.data.data.records
    await loadEvidenceChain(currentBatch.value.batchId)
    await loadIncidentClusters(currentBatch.value.batchId)
    await loadOutcomeMetrics()
  } catch {
    error.value = '安全验证数据加载失败，请检查登录状态、权限或后端服务。'
  } finally {
    loading.value = false
  }
}

async function loadOutcomeMetrics() {
  try {
    operations.value = (await operationsOverview()).data.data
  } catch {
    operations.value = undefined
  }
  try {
    topRecommendationRows.value = (await topRecommendations(3)).data.data
  } catch {
    topRecommendationRows.value = []
  }
}

async function loadIncidentClusters(batchId: string) {
  try {
    const res = await listIncidents({ pageNum: 1, pageSize: 5, keyword: batchId })
    incidents.value = res.data.data.records
  } catch {
    incidents.value = []
  }
}

async function loadEvidenceChain(batchId: string) {
  try {
    const res = await demoRangeEvidenceChain(batchId)
    evidenceChain.value = res.data.data
    if (res.data.data.events?.length) {
      externalEvents.value = res.data.data.events
      externalTotal.value = res.data.data.summary.eventCount
    }
    if (res.data.data.alerts?.length) {
      alerts.value = res.data.data.alerts
      alertTotal.value = res.data.data.summary.alertCount
    }
    if (res.data.data.vulnerabilities?.length) {
      vulnerabilities.value = res.data.data.vulnerabilities
    }
    if (res.data.data.tickets?.length) {
      tickets.value = res.data.data.tickets
    }
    if (res.data.data.reports?.length) {
      reports.value = res.data.data.reports
    }
  } catch {
    evidenceChain.value = undefined
  }
}

function selectCase(demoCase: DemoCase) {
  selectedCase.value = demoCase
}

function goExternalEvents(demoCase: DemoCase) {
  router.push({ path: '/soc/external-events', query: { sourceType: demoCase.primarySource } })
}

function goAlerts(demoCase: DemoCase) {
  router.push({ path: '/soc/alerts', query: { sourceType: demoCase.primarySource, keyword: currentBatch.value.targetAsset } })
}

function goTickets() {
  const firstTicket = evidenceChain.value?.tickets?.[0] || tickets.value[0]
  router.push({ path: '/soc/tickets', query: { keyword: firstTicket?.ticketNo || currentBatch.value.batchId, openTicketId: firstTicket?.id } })
}

function goReports() {
  router.push({ path: '/soc/reports', query: { keyword: currentBatch.value.batchId, reportType: 'security_validation', batchId: currentBatch.value.batchId } })
}

function goRecommendations() {
  router.push({ path: '/soc/dashboard', query: { section: 'recommendations' } })
}

function goBatchExternalEvents() {
  router.push({ path: '/soc/external-events', query: { keyword: currentBatch.value.batchId } })
}

function goBatchAlerts() {
  router.push({ path: '/soc/alerts', query: { keyword: currentBatch.value.batchId } })
}

function goVulnerabilities() {
  router.push({ path: '/soc/vulnerabilities', query: { sourceType: 'trivy', keyword: 'DEMO-RANGE' } })
}

function goFirstEvent() {
  const firstEvent = evidenceChain.value?.events?.[0]
  if (!firstEvent) {
    goBatchExternalEvents()
    return
  }
  router.push({ path: '/soc/external-events', query: { keyword: currentBatch.value.batchId, openEventUid: firstEvent?.eventUid } })
}

function goFirstAlert() {
  const firstAlert = evidenceChain.value?.alerts?.[0]
  if (!firstAlert) {
    goBatchAlerts()
    return
  }
  router.push({ path: '/soc/alerts', query: { keyword: currentBatch.value.batchId, openAlertId: firstAlert?.id } })
}

function goTicketTimeline() {
  const firstTicket = evidenceChain.value?.tickets?.[0]
  if (firstTicket) {
    router.push({ path: '/soc/tickets', query: { keyword: firstTicket.ticketNo, openTicketId: firstTicket.id } })
    return
  }
  const firstAlert = evidenceChain.value?.alerts?.find((item) => !item.ticketId) || evidenceChain.value?.alerts?.[0]
  router.push({ path: '/soc/alerts', query: { keyword: currentBatch.value.batchId, openAlertId: firstAlert?.id } })
}

async function confirmImportBatch() {
  try {
    await ElMessageBox.confirm('确认导入固定离线演示批次？该操作只写入 demo 元数据并按稳定 ID upsert，不会执行扫描、攻击测试或访问外部目标。', '导入演示批次', {
      type: 'warning',
      confirmButtonText: '导入批次',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  importing.value = true
  try {
    const res = await importDemoRangeBatch({ linkAlerts: true })
    lastBatchResult.value = res.data.data
    ElMessage.success(`演示批次 ${res.data.data.batchId} 已导入`)
    await load()
    await loadEvidenceChain(res.data.data.batchId)
    activeStep.value = 2
  } catch {
    ElMessage.error('演示批次导入失败，请检查权限或后端服务。')
  } finally {
    importing.value = false
  }
}

async function confirmGenerateReport() {
  try {
    await ElMessageBox.confirm('确认基于当前批次生成安全验证报告？该操作不会执行扫描、攻击测试或访问外部目标。', '生成安全验证报告', {
      type: 'warning',
      confirmButtonText: '生成报告',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  generating.value = true
  try {
    await generateReport('security_validation', currentBatch.value.batchId)
    ElMessage.success('安全验证报告已生成')
    await loadEvidenceChain(currentBatch.value.batchId)
    goReports()
  } catch {
    ElMessage.error('报表生成失败，请检查权限或后端服务。')
  } finally {
    generating.value = false
  }
}

async function sendDryRunNotification() {
  notifying.value = true
  try {
    await sendShuffleDemoNotification()
    ElMessage.success('通知 dry-run 日志已写入')
    await loadEvidenceChain(currentBatch.value.batchId)
  } catch {
    ElMessage.error('通知 dry-run 写入失败，请检查权限或后端服务。')
  } finally {
    notifying.value = false
  }
}

async function runCorrelation() {
  correlating.value = true
  try {
    const res = await correlateIncidents()
    ElMessage.success(`事件关联完成：刷新 ${res.data.data.upsertedClusters} 个事件簇`)
    await loadIncidentClusters(currentBatch.value.batchId)
  } catch {
    ElMessage.error('事件关联失败，请检查权限、数据表或后端服务。')
  } finally {
    correlating.value = false
  }
}

function goIncidents() {
  router.push({ path: '/soc/incidents', query: { keyword: currentBatch.value.batchId } })
}

function goIncidentDetail(incident: IncidentClusterItem) {
  router.push({ path: '/soc/incidents', query: { keyword: incident.clusterNo } })
}

function incidentSourceLabel(incident: IncidentClusterItem) {
  return incident.sourceSummary || incident.sourceTypes || '-'
}

function incidentEvidenceCount(incident: IncidentClusterItem) {
  return incident.evidenceCount ?? ((incident.eventCount || 0) + (incident.alertCount || 0) + (incident.vulnerabilityCount || 0))
}

function openDetails(title: string, rows: DetailDrawerState['rows'], tags: string[] = [], lines: string[] = []) {
  detailDrawer.value = {
    visible: true,
    title,
    rows,
    tags,
    lines,
  }
}

function openBatchDetails() {
  openDetails('批次详情', [
    { label: 'Batch ID', value: currentBatch.value.batchId },
    { label: '目标资产', value: currentBatch.value.targetAsset },
    { label: '开始时间', value: currentBatch.value.startedAt },
    { label: '事件数量', value: currentBatch.value.eventCount },
    { label: '告警数量', value: currentBatch.value.alertCount },
    { label: '来源', value: currentBatch.value.source },
  ], [], ['运行数据来自现有接口聚合或离线 demo 样例，不执行真实扫描。'])
}

function openCaseDetails() {
  openDetails(selectedCase.value.title, [
    { label: '场景', value: selectedCase.value.category },
    { label: '主要来源', value: selectedCase.value.primarySource },
    { label: '事件类型', value: selectedCase.value.eventTypes.join(' / ') || '漏洞记录' },
  ], selectedCase.value.sources, [
    selectedCase.value.description,
    ...selectedCase.value.expectedEvidence,
  ])
}

function openImportDetails() {
  if (!lastBatchResult.value) {
    openBatchDetails()
    return
  }
  openDetails('导入结果详情', [
    { label: 'Batch ID', value: lastBatchResult.value.batchId },
    { label: '导入事件', value: lastBatchResult.value.importedEvents },
    { label: '创建告警', value: lastBatchResult.value.createdAlerts },
    { label: '漏洞记录', value: lastBatchResult.value.createdVulnerabilities },
    { label: '跳过项', value: lastBatchResult.value.skippedItems },
    { label: '失败项', value: lastBatchResult.value.failedItems },
    { label: '去重规则', value: lastBatchResult.value.dedupRule },
  ], lastBatchResult.value.sources.map((item) => item.sourceType.toUpperCase()), lastBatchResult.value.sources.map((item) =>
    `${item.sourceType}: 事件 ${item.importedEvents}，告警 ${item.linkedAlerts}，漏洞 ${item.importedVulnerabilities}`,
  ))
}

function openTopologyDetails() {
  openDetails('演示拓扑详情', [
    { label: '批次', value: currentBatch.value.batchId },
    { label: '目标资产', value: currentBatch.value.targetAsset },
    { label: '覆盖来源', value: sourceCoverageText.value },
  ], topologyNodes.value.map((item) => `${item.name}:${item.status}`), topologyNodes.value.map((item) => `${item.name} - ${item.role} - ${item.detail}`))
}

function openAlertDetails(alert: AlertItem) {
  openDetails('告警详情字段', [
    { label: 'Alert UID', value: alert.alertUid },
    { label: 'sourceType', value: alert.sourceType },
    { label: 'eventType', value: alert.eventType },
    { label: 'ruleId', value: alert.ruleId },
    { label: 'ruleName', value: alert.ruleName || alert.ruleDescription },
    { label: 'assetIp', value: alert.assetIp },
    { label: 'targetUrl', value: alert.targetUrl },
    { label: 'action', value: alert.action },
    { label: 'demoCaseId', value: alert.demoCaseId },
    { label: 'batchId', value: alert.batchId },
  ], [alert.severity, alert.status].filter(Boolean), [alert.evidenceSummary || '暂无 evidenceSummary'])
}

function openTicketDetails(ticket: TicketItem) {
  openDetails('工单详情字段', [
    { label: '工单号', value: ticket.ticketNo },
    { label: '标题', value: ticket.title },
    { label: '等级', value: ticket.severity },
    { label: '状态', value: ticket.status },
    { label: '负责人', value: ticket.assigneeName },
    { label: '截止时间', value: ticket.dueAt },
  ], [], [ticket.reviewConclusion || ticket.resolution || '可进入工单中心查看完整时间线。'])
}

function openReportDetails(report: ReportItem) {
  openDetails('报告详情字段', [
    { label: '报告号', value: report.reportNo },
    { label: '类型', value: report.reportType },
    { label: '标题', value: report.title },
    { label: '状态', value: report.status },
    { label: '生成时间', value: report.generatedAt },
  ], [], [report.summary || '暂无摘要', report.recommendation || '暂无建议'])
}

function severityTag(severity?: string): TagType {
  const normalized = (severity || '').toLowerCase()
  if (normalized === 'critical' || normalized === 'high') return 'danger'
  if (normalized === 'medium') return 'warning'
  if (normalized === 'low') return 'success'
  return 'info'
}

function node(key: string, name: string, role: string, ready: boolean, detail: string, tone: string) {
  return {
    key,
    name,
    role,
    detail,
    tone,
    status: ready ? '有证据' : '待导入',
    tagType: (ready ? 'success' : 'info') as TagType,
  }
}

function evidence(source: string, title: string, fallback: string, records?: VulnerabilityItem[]) {
  const eventRecords = externalEvents.value.filter((item) => item.sourceType === source)
  const sourceRecords = records || eventRecords
  const highRisk = sourceRecords.filter((item) => ['critical', 'high'].includes((item.severity || '').toLowerCase())).length
  const latestAt = latestTime(sourceRecords.map((item) => ('eventTime' in item ? item.eventTime : item.detectedAt)))
  const demoSummary = eventRecords.map((item) => parseNormalizedEvent(item).evidenceSummary).find(Boolean)
  return {
    source,
    title,
    count: sourceRecords.length,
    highRisk,
    latestAt: latestAt || '-',
    summary: demoSummary || fallback,
  }
}

function sourceCount(source: string) {
  const fromSummary = sourceSummary.value.find((item) => item.sourceType === source)?.total
  return fromSummary ?? externalEvents.value.filter((item) => item.sourceType === source).length
}

function alertSourceCount(source: string) {
  return alerts.value.filter((item) => item.sourceType === source).length
}

function hasSourceOrAlert(source: string) {
  return sourceCount(source) > 0 || alertSourceCount(source) > 0
}

function parseNormalizedEvent(event: ExternalEventItem): ParsedEvent {
  const raw = parseJson(event.rawEvent) || {}
  const normalized = parseJson(event.normalizedEvent) || {}
  return normalizeParsedEvent({ ...raw, ...normalized })
}

function parseJson(value?: string): Record<string, unknown> | undefined {
  if (!value) return undefined
  try {
    const parsed = JSON.parse(value) as Record<string, unknown>
    return parsed && typeof parsed === 'object' ? parsed : undefined
  } catch {
    return undefined
  }
}

function normalizeParsedEvent(parsed: Record<string, unknown>): ParsedEvent {
  return {
    demoCaseId: stringField(parsed, 'demoCaseId') || stringField(parsed, 'demo_case_id'),
    demoBatchId: stringField(parsed, 'demoBatchId') || stringField(parsed, 'demo_batch_id'),
    batchId: stringField(parsed, 'batchId') || stringField(parsed, 'batch_id'),
    evidenceSummary: stringField(parsed, 'evidenceSummary') || stringField(parsed, 'evidence_summary'),
    targetUrl: stringField(parsed, 'targetUrl') || stringField(parsed, 'target_url'),
    httpMethod: stringField(parsed, 'httpMethod') || stringField(parsed, 'http_method'),
    httpStatus: stringField(parsed, 'httpStatus') || stringField(parsed, 'http_status'),
    action: stringField(parsed, 'action'),
    requestId: stringField(parsed, 'requestId') || stringField(parsed, 'request_id'),
    engine: stringField(parsed, 'engine'),
  }
}

function stringField(parsed: Record<string, unknown>, key: string) {
  const value = parsed[key]
  return value === undefined || value === null ? undefined : String(value)
}

function findFirst(values: Array<string | undefined>) {
  return values.find((value) => value && value.trim())
}

function earliestTime(values: Array<string | undefined>) {
  return values.filter(Boolean).sort()[0]
}

function latestTime(values: Array<string | undefined>) {
  const sorted = values.filter(Boolean).sort()
  return sorted[sorted.length - 1]
}
</script>

<style scoped>
.demo-range-page {
  min-width: 0;
}

.wizard-status-panel,
.wizard-step-panel,
.wizard-next-panel {
  min-width: 0;
  padding: 16px;
}

.demo-outcome-panel {
  display: grid;
  gap: 14px;
  padding: 16px;
}

.demo-outcome-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 12px;
}

.demo-outcome-card {
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 14px;
  border: 1px solid rgba(179, 173, 163, 0.35);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.7);
}

.demo-outcome-card span,
.demo-outcome-card p {
  margin: 0;
  color: var(--soc-text-muted);
  font-size: 13px;
}

.demo-outcome-card strong {
  overflow-wrap: anywhere;
  color: var(--soc-text);
  font-size: 20px;
}

.recommendation-strip {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid rgba(224, 133, 48, 0.22);
  border-radius: 12px;
  background: rgba(255, 248, 239, 0.62);
}

.recommendation-list {
  display: grid;
  gap: 8px;
}

.recommendation-list button {
  display: grid;
  gap: 3px;
  padding: 10px 12px;
  border: 1px solid rgba(179, 173, 163, 0.32);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.78);
  color: var(--soc-text);
  text-align: left;
  cursor: pointer;
}

.recommendation-list em {
  color: var(--soc-text-muted);
  font-style: normal;
  line-height: 1.5;
}

.wizard-status-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.wizard-status-head div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.wizard-status-head span,
.wizard-status-head em,
.wizard-progress-copy span {
  color: var(--soc-text-muted);
  font-size: 13px;
  font-style: normal;
}

.wizard-status-head strong {
  min-width: 0;
  overflow-wrap: anywhere;
  color: var(--soc-text);
  font-size: 18px;
}

.wizard-progress-copy {
  text-align: right;
}

.wizard-steps {
  margin-top: 14px;
}

.wizard-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 340px);
  gap: 14px;
  align-items: start;
}

.validation-chain-panel {
  display: grid;
  gap: 14px;
  padding: 16px;
}

.chain-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}

.wizard-main {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.wizard-next-panel {
  position: sticky;
  top: 88px;
  display: grid;
  gap: 14px;
}

.next-card-head {
  display: grid;
  gap: 6px;
}

.next-card-head span {
  color: var(--soc-warm-strong);
  font-size: 12px;
  font-weight: 760;
}

.next-card-head strong {
  color: var(--soc-text);
  font-size: 17px;
}

.next-card-head p {
  margin: 0;
  color: var(--soc-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.step-nav,
.shortcut-list {
  display: grid;
  gap: 8px;
}

.step-nav {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.shortcut-list button,
.selected-case-panel,
.batch-result-strip,
.wizard-list-row {
  border: 1px solid rgba(179, 173, 163, 0.42);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.58);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82);
}

.shortcut-list button {
  display: grid;
  gap: 4px;
  padding: 10px;
  text-align: left;
  cursor: pointer;
}

.shortcut-list strong,
.selected-case-panel strong,
.wizard-list-row strong {
  color: var(--soc-text);
}

.shortcut-list span,
.selected-case-panel span,
.selected-case-panel p,
.wizard-list-row span,
.wizard-list-row em,
.batch-result-strip span {
  color: var(--soc-text-muted);
  font-size: 13px;
  font-style: normal;
}

.wizard-case-list {
  margin-bottom: 12px;
}

.selected-case-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
}

.selected-case-panel div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.selected-case-panel p {
  margin: 0;
  line-height: 1.6;
}

.step-metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.step-metric-grid :deep(.risk-card strong) {
  overflow-wrap: anywhere;
  font-size: 22px;
  line-height: 1.12;
}

.evidence-grid.compact {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.batch-result-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
  padding: 10px 12px;
}

.wizard-list {
  display: grid;
  gap: 10px;
}

.wizard-list-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
}

.wizard-list-row > div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.wizard-list-row > div:last-child {
  justify-items: end;
}

.detail-drawer-stack,
.drawer-lines {
  display: grid;
  gap: 12px;
}

.drawer-lines {
  padding-top: 8px;
}

.drawer-lines strong {
  color: var(--soc-text);
}

.drawer-lines span {
  color: var(--soc-text-muted);
  line-height: 1.65;
}

.range-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.range-metrics :deep(.risk-card strong) {
  overflow-wrap: anywhere;
  font-size: 22px;
  line-height: 1.12;
}

.range-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 380px);
  gap: 14px;
}

.topology-panel,
.batch-panel,
.cases-panel,
.case-detail-panel,
.evidence-panel,
.batch-result-panel,
.close-loop-panel {
  min-width: 0;
  padding: 16px;
}

.panel-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-title strong,
.close-loop-panel strong {
  display: block;
  color: var(--soc-text);
  font-size: 15px;
}

.panel-title span,
.close-loop-panel span {
  color: var(--soc-text-muted);
  font-size: 13px;
}

.topology-grid,
.evidence-grid,
.chain-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.topology-node,
.evidence-card,
.chain-grid article,
.case-item {
  border: 1px solid rgba(179, 173, 163, 0.42);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.58);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82);
}

.topology-node {
  display: grid;
  gap: 10px;
  min-height: 132px;
  padding: 12px;
  border-top: 3px solid var(--soc-warm);
}

.topology-node.blue {
  border-top-color: var(--soc-blue);
}

.topology-node.cyan {
  border-top-color: var(--soc-cyan);
}

.topology-node.green {
  border-top-color: var(--soc-success);
}

.topology-node strong,
.case-item strong,
.evidence-card strong {
  color: var(--soc-text);
}

.topology-node span,
.topology-node small,
.case-item span,
.case-item p,
.chain-grid span,
.chain-grid p,
.evidence-card p,
.batch-list dt {
  color: var(--soc-text-muted);
}

.chain-grid {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.chain-grid article {
  display: grid;
  gap: 8px;
  min-height: 170px;
  padding: 12px;
}

.chain-grid strong {
  color: var(--soc-text);
  font-size: 28px;
  line-height: 1;
}

.chain-grid p {
  margin: 0;
  line-height: 1.55;
}

.batch-list {
  display: grid;
  gap: 10px;
  margin: 0;
}

.batch-list div {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 10px;
  padding: 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.54);
}

.batch-list dt,
.batch-list dd {
  margin: 0;
}

.batch-list dd {
  min-width: 0;
  overflow-wrap: anywhere;
  color: var(--soc-text);
  font-weight: 700;
}

.batch-result-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 10px;
}

.batch-result-grid div,
.source-result-list article {
  border: 1px solid rgba(179, 173, 163, 0.38);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.58);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82);
}

.batch-result-grid div {
  display: grid;
  gap: 6px;
  padding: 12px;
}

.batch-result-grid span,
.source-result-list span {
  color: var(--soc-text-muted);
  font-size: 13px;
}

.batch-result-grid strong {
  color: var(--soc-text);
  font-size: 24px;
}

.source-result-list {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}

.source-result-list article {
  display: grid;
  gap: 4px;
  padding: 10px;
}

.source-result-list strong {
  color: var(--soc-warm-strong);
}

.case-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.case-item {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  min-height: 132px;
  padding: 12px;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.case-item:hover,
.case-item:focus-visible,
.case-item.active {
  border-color: rgba(212, 147, 74, 0.72);
  box-shadow: 0 12px 28px rgba(91, 77, 53, 0.1), inset 0 1px 0 rgba(255, 255, 255, 0.9);
  outline: 0;
  transform: translateY(-1px);
}

.case-item p {
  margin: 6px 0 0;
  line-height: 1.55;
}

.case-description {
  margin: 0 0 14px;
  color: var(--soc-text-muted);
  line-height: 1.7;
}

.detail-block {
  display: grid;
  gap: 8px;
  padding: 12px 0;
  border-top: 1px solid var(--soc-border);
}

.detail-block strong {
  color: var(--soc-text);
}

.tag-row,
.entry-actions,
.close-loop-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-block ul {
  margin: 0;
  padding-left: 18px;
  color: var(--soc-text-muted);
  line-height: 1.8;
}

.evidence-card {
  display: grid;
  gap: 10px;
  min-height: 160px;
  padding: 12px;
}

.evidence-card header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.evidence-card p {
  margin: 0;
  line-height: 1.6;
}

.evidence-card dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
}

.evidence-card dt,
.evidence-card dd {
  margin: 0;
}

.evidence-card dt {
  color: var(--soc-text-subtle);
  font-size: 12px;
}

.evidence-card dd {
  min-width: 0;
  overflow-wrap: anywhere;
  color: var(--soc-text);
  font-weight: 700;
}

.close-loop-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

@media (max-width: 1180px) {
  .wizard-shell {
    grid-template-columns: 1fr;
  }

  .wizard-next-panel {
    position: static;
  }

  .evidence-grid.compact,
  .step-metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .range-metrics,
  .batch-result-grid,
  .topology-grid,
  .evidence-grid,
  .chain-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .source-result-list {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .range-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .wizard-status-head,
  .selected-case-panel,
  .batch-result-strip,
  .wizard-list-row {
    align-items: stretch;
    flex-direction: column;
  }

  .wizard-progress-copy {
    text-align: left;
  }

  .evidence-grid.compact,
  .step-metric-grid,
  .step-nav {
    grid-template-columns: 1fr;
  }

  .range-metrics,
  .batch-result-grid,
  .topology-grid,
  .case-list,
  .source-result-list,
  .evidence-grid,
  .chain-grid {
    grid-template-columns: 1fr;
  }

  .close-loop-panel {
    align-items: stretch;
    flex-direction: column;
  }

  .batch-list div {
    grid-template-columns: 1fr;
  }
}
</style>
