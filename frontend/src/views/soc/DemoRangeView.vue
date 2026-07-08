<template>
  <div class="page-shell demo-range-page">
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false">
      <template #default>
        <el-button size="small" @click="load">重试</el-button>
      </template>
    </el-alert>

    <template v-if="!activeRun">
      <section class="soc-panel run-hub-panel">
        <div class="panel-title">
          <div>
            <strong>安全验证工作流</strong>
            <span>每条记录对应一个可恢复的验证子页面。</span>
          </div>
          <div class="panel-actions">
            <el-button :icon="Refresh" text @click="load">刷新数据</el-button>
            <el-button type="primary" @click="createWorkflow">新建验证工作流</el-button>
          </div>
        </div>
        <div class="hub-summary-grid">
          <article>
            <span>当前批次</span>
            <strong>{{ discoveredBatch.batchId }}</strong>
          </article>
          <article>
            <span>目标资产</span>
            <strong>{{ discoveredBatch.targetAsset }}</strong>
          </article>
          <article>
            <span>证据 / 告警 / 工单 / 报告</span>
            <strong>{{ currentCounts.events }} / {{ currentCounts.alerts }} / {{ currentCounts.tickets }} / {{ currentCounts.reports }}</strong>
          </article>
        </div>
      </section>

      <section v-loading="loading" class="soc-panel run-record-panel">
        <div class="panel-title">
          <div>
            <strong>工作流列表</strong>
            <span>从这里继续某一次安全验证。</span>
          </div>
          <el-tag effect="plain">{{ sortedRuns.length }} 个记录</el-tag>
        </div>
        <el-empty v-if="!sortedRuns.length" description="暂无工作流记录。" />
        <div v-else class="run-list">
          <article v-for="run in sortedRuns" :key="run.id" class="run-card">
            <header>
              <div>
                <span>工作流</span>
                <strong>{{ run.id }}</strong>
              </div>
              <el-tag :type="run.status === 'completed' ? 'success' : 'warning'" effect="plain">
                {{ run.status === 'completed' ? '已完成' : '进行中' }}
              </el-tag>
            </header>
            <div class="run-summary-grid">
              <div><span>批次</span><strong>{{ run.batchId }}</strong></div>
              <div><span>场景</span><strong>{{ caseTitle(run.selectedCaseId) }}</strong></div>
              <div><span>步骤</span><strong>{{ stepTitle(run.stepKey) }}</strong></div>
              <div><span>更新时间</span><strong>{{ formatDateTime(run.updatedAt) }}</strong></div>
            </div>
            <footer>
              <span>记录 {{ run.logs.length }} 条 · 告警 {{ run.counts.alerts }} · 工单 {{ run.counts.tickets }} · 报告 {{ run.counts.reports }}</span>
              <div>
                <el-button type="primary" @click="resumeWorkflow(run)">继续</el-button>
                <el-button text @click="removeWorkflow(run)">删除</el-button>
              </div>
            </footer>
          </article>
        </div>
      </section>
    </template>

    <template v-else>
      <section class="soc-panel wizard-status-panel">
        <div class="wizard-status-head">
          <div>
            <span>当前工作流</span>
            <strong>{{ activeRun.id }}</strong>
            <em>{{ currentBatch.batchId }} · {{ caseTitle(activeRun.selectedCaseId) }}</em>
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
        <div class="batch-context-grid">
          <article>
            <span>批次</span>
            <strong>{{ currentBatch.batchId }}</strong>
          </article>
          <article>
            <span>目标资产</span>
            <strong>{{ currentBatch.targetAsset }}</strong>
          </article>
          <article>
            <span>安全记录</span>
            <strong>{{ currentCounts.events }}</strong>
          </article>
          <article>
            <span>告警 / 工单 / 报告</span>
            <strong>{{ currentCounts.alerts }} / {{ currentCounts.tickets }} / {{ currentCounts.reports }}</strong>
          </article>
        </div>
      </section>

      <section v-loading="loading" class="wizard-shell">
        <main class="wizard-main">
          <section v-if="activeStep === 0" class="soc-panel wizard-step-panel">
            <div class="panel-title">
              <div>
                <strong>选择场景</strong>
                <span>这一步只确定本次验证要讲哪个风险场景，并写入当前工作流记录。</span>
              </div>
              <el-button text @click="openWorkflowDetails">查看工作流记录</el-button>
            </div>
            <div class="case-list">
              <article
                v-for="demoCase in demoCases"
                :key="demoCase.id"
                class="case-item"
                :class="{ active: selectedCase.id === demoCase.id }"
                role="button"
                tabindex="0"
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
                <span>当前场景</span>
                <strong>{{ selectedCase.title }}</strong>
                <p>{{ selectedCase.description }}</p>
              </div>
              <el-button @click="openCaseDetails">证据要求</el-button>
            </div>
          </section>

          <section v-else-if="activeStep === 1" class="soc-panel wizard-step-panel">
            <div class="panel-title">
              <div>
                <strong>导入证据</strong>
                <span>导入固定离线样例，生成本批次证据、漏洞和告警。该操作不执行扫描或外部访问。</span>
              </div>
              <el-button :icon="Refresh" text @click="load">刷新</el-button>
            </div>
            <div class="step-metric-grid">
              <RiskCard label="安全记录" :value="currentCounts.events" delta="本批次事件" tone="low" />
              <RiskCard label="漏洞记录" :value="currentCounts.vulnerabilities" delta="Trivy 离线结果" tone="medium" />
              <RiskCard label="拦截证据" :value="evidenceChain?.summary.blockedCount || 0" delta="WAF / 网关" tone="high" />
            </div>
            <el-empty v-if="!lastBatchResult && !hasEvidence" description="暂无本批次证据，请执行右侧导入动作。" />
            <div v-else class="evidence-grid">
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
          </section>

          <section v-else-if="activeStep === 2" class="soc-panel wizard-step-panel">
            <div class="panel-title">
              <div>
                <strong>查看告警</strong>
                <span>这一步只展示当前工作流批次的告警，并记录跳转到告警中心的动作。</span>
              </div>
              <div class="chain-actions">
                <el-button :loading="correlating" @click="runCorrelation">执行关联</el-button>
              </div>
            </div>
            <div class="step-metric-grid">
              <RiskCard label="关联告警" :value="currentCounts.alerts" delta="soc_alert" tone="high" />
              <RiskCard label="高危告警" :value="highRiskAlertCount" delta="critical / high" tone="critical" />
              <RiskCard label="事件簇" :value="currentCounts.incidents" delta="本批次关联" tone="medium" />
            </div>
            <el-empty v-if="!alertPreview.length" description="暂无本批次告警，请先导入证据或刷新证据链。" />
            <div v-else class="wizard-list">
              <article v-for="alert in alertPreview" :key="alert.id" class="wizard-list-row">
                <div>
                  <span>{{ alert.alertUid }}</span>
                  <strong>{{ alert.ruleName || alert.ruleDescription }}</strong>
                  <em>{{ alert.assetName || '-' }} / {{ alert.assetIp || '-' }} · {{ alert.status }}</em>
                </div>
                <div>
                  <el-tag :type="severityTag(alert.severity)" effect="plain">{{ alert.severity }}</el-tag>
                  <el-button text @click="openAlertDetails(alert)">详情</el-button>
                </div>
              </article>
            </div>
          </section>

          <section v-else-if="activeStep === 3" class="soc-panel wizard-step-panel">
            <div class="panel-title">
              <div>
                <strong>转工单</strong>
                <span>从当前批次告警进入工单链路，跳转时保留工作流回跳上下文。</span>
              </div>
            </div>
            <div class="step-metric-grid">
              <RiskCard label="工单数量" :value="currentCounts.tickets" delta="soc_ticket" tone="medium" />
              <RiskCard label="待转告警" :value="unticketedAlertCount" delta="可转工单" tone="high" />
              <RiskCard label="目标资产" :value="currentBatch.targetAsset" delta="本工作流上下文" tone="low" />
            </div>
            <el-empty v-if="!ticketPreview.length" description="暂无本批次工单，可从右侧进入告警转工单。" />
            <div v-else class="wizard-list">
              <article v-for="ticket in ticketPreview" :key="ticket.id" class="wizard-list-row">
                <div>
                  <span>{{ ticket.ticketNo }}</span>
                  <strong>{{ ticket.title }}</strong>
                  <em>{{ ticket.assigneeName || '未分派' }} · {{ ticket.status }}</em>
                </div>
                <div>
                  <el-tag effect="plain">{{ ticket.severity }}</el-tag>
                  <el-button text @click="openTicketDetails(ticket)">详情</el-button>
                </div>
              </article>
            </div>
          </section>

          <section v-else class="soc-panel wizard-step-panel">
            <div class="panel-title">
              <div>
                <strong>生成报告</strong>
                <span>基于当前工作流批次生成安全验证报告，完成后仍保留在当前子页面。</span>
              </div>
            </div>
            <div class="step-metric-grid">
              <RiskCard label="报告数量" :value="currentCounts.reports" delta="security_validation" tone="safe" />
              <RiskCard label="通知留痕" :value="evidenceChain?.summary.notificationLogCount || 0" delta="dry-run" tone="low" />
              <RiskCard label="覆盖来源" :value="sourceCoverageText" delta="证据覆盖" tone="medium" />
            </div>
            <el-empty v-if="!reportPreview.length" description="暂无本批次报告，请执行右侧生成动作。" />
            <div v-else class="wizard-list">
              <article v-for="report in reportPreview" :key="report.id" class="wizard-list-row">
                <div>
                  <span>{{ report.reportNo }}</span>
                  <strong>{{ report.title }}</strong>
                  <em>{{ report.reportType }} · {{ report.status }}</em>
                </div>
                <div>
                  <el-button text @click="openReportDetails(report)">详情</el-button>
                </div>
              </article>
            </div>
          </section>
        </main>

        <aside class="soc-panel wizard-next-panel">
          <div class="next-card-head">
            <span>当前步骤操作</span>
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
          <div v-if="nextStepInfo" class="next-preview-card">
            <span>下一页</span>
            <strong>{{ nextStepInfo.title }}</strong>
            <p>{{ nextStepInfo.description }}</p>
          </div>
          <div class="step-nav">
            <el-button :disabled="activeStep === 0" @click="setActiveStep(activeStep - 1)">上一步</el-button>
            <el-button :disabled="activeStep >= wizardSteps.length - 1" @click="setActiveStep(activeStep + 1)">下一页</el-button>
          </div>
          <div class="record-list">
            <header>
              <strong>本次工作流记录</strong>
              <el-button text @click="openWorkflowDetails">完整记录</el-button>
            </header>
            <article v-for="log in activeRun.logs.slice(0, 6)" :key="log.id">
              <span>{{ formatDateTime(log.time) }}</span>
              <strong>{{ log.title }}</strong>
              <em>{{ log.detail || stepTitle(log.stepKey) }}</em>
            </article>
          </div>
          <el-button text @click="goWorkflowHub">返回工作流列表</el-button>
        </aside>
      </section>
    </template>

    <el-drawer v-model="detailDrawer.visible" :title="detailDrawer.title" size="560px">
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
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import RiskCard from '@/components/security/RiskCard.vue'
import {
  demoRangeEvidenceChain,
  correlateIncidents,
  generateReport,
  importDemoData,
  listIncidents,
  listAlerts,
  listExternalEvents,
  listReports,
  listTickets,
  listVulnerabilities,
  sendShuffleDemoNotification,
  type AlertItem,
  type DemoDataOperationResult,
  type DemoRangeEvidenceChain,
  type ExternalEventItem,
  type IncidentClusterItem,
  type ReportItem,
  type TicketItem,
  type VulnerabilityItem,
} from '@/api/soc'

type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'
type WorkflowStatus = 'active' | 'completed'
type WorkflowStepKey = 'scenario' | 'evidence' | 'alerts' | 'tickets' | 'report'

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
}

interface WizardStep {
  key: WorkflowStepKey
  title: string
  shortTitle: string
  description: string
}

interface WorkflowCounts {
  events: number
  alerts: number
  vulnerabilities: number
  tickets: number
  reports: number
  incidents: number
}

interface WorkflowLog {
  id: string
  time: string
  type: string
  title: string
  detail?: string
  stepKey?: WorkflowStepKey
}

interface DemoWorkflowRun {
  id: string
  batchId: string
  selectedCaseId: string
  stepKey: WorkflowStepKey
  status: WorkflowStatus
  createdAt: string
  updatedAt: string
  lastVisitedAt: string
  counts: WorkflowCounts
  logs: WorkflowLog[]
}

interface DetailDrawerState {
  visible: boolean
  title: string
  rows: Array<{ label: string; value?: string | number }>
  tags: string[]
  lines: string[]
}

const WORKFLOW_STORAGE_KEY = 'cyberfusion_demo_range_workflows_v1'
const MAX_WORKFLOWS = 30
const MAX_LOGS = 80

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const importing = ref(false)
const generating = ref(false)
const notifying = ref(false)
const correlating = ref(false)
const error = ref('')
const activeStep = ref(0)
const runs = ref<DemoWorkflowRun[]>([])
const lastBatchResult = ref<DemoDataOperationResult>()
const evidenceChain = ref<DemoRangeEvidenceChain>()
const externalEvents = ref<ExternalEventItem[]>([])
const externalTotal = ref(0)
const alerts = ref<AlertItem[]>([])
const alertTotal = ref(0)
const vulnerabilities = ref<VulnerabilityItem[]>([])
const tickets = ref<TicketItem[]>([])
const reports = ref<ReportItem[]>([])
const incidents = ref<IncidentClusterItem[]>([])
const detailDrawer = ref<DetailDrawerState>({
  visible: false,
  title: '',
  rows: [],
  tags: [],
  lines: [],
})

const wizardSteps: WizardStep[] = [
  { key: 'scenario', title: '选择场景', shortTitle: '选用例', description: '确定本次验证要讲的风险场景，并写入当前工作流。' },
  { key: 'evidence', title: '导入证据', shortTitle: '导入离线证据', description: '导入固定离线样例，形成本批次证据、漏洞和告警。' },
  { key: 'alerts', title: '查看告警', shortTitle: '确认告警', description: '检查本批次告警是否进入处置队列，并可跳转到告警中心。' },
  { key: 'tickets', title: '转工单', shortTitle: '进入工单', description: '从告警进入工单链路，保留工作流回跳上下文。' },
  { key: 'report', title: '生成报告', shortTitle: '输出报告', description: '生成安全验证报告，并记录通知 dry-run 或报告查看动作。' },
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

const activeRunId = computed(() => routeParam(route.params.runId))
const activeRun = computed(() => runs.value.find((run) => run.id === activeRunId.value))
const sortedRuns = computed(() => [...runs.value].sort((left, right) => right.lastVisitedAt.localeCompare(left.lastVisitedAt)))
const activeStepInfo = computed(() => wizardSteps[activeStep.value] || wizardSteps[0])
const nextStepInfo = computed(() => wizardSteps[activeStep.value + 1])
const stepProgress = computed(() => Math.round(((activeStep.value + 1) / wizardSteps.length) * 100))
const selectedCase = computed(() => demoCases.find((item) => item.id === activeRun.value?.selectedCaseId) || demoCases[0])
const parsedEvents = computed(() => externalEvents.value.map((event) => ({ event, parsed: parseNormalizedEvent(event) })))

const discoveredBatch = computed(() => {
  const batchEvent = parsedEvents.value.find(({ parsed }) => parsed.demoBatchId || parsed.batchId || parsed.demoCaseId)
  const targetAsset = findFirst([
    ...externalEvents.value.map((item) => item.assetIp || item.destIp || item.assetName),
    ...alerts.value.map((item) => item.assetIp || item.assetName),
    ...vulnerabilities.value.map((item) => item.assetIp || item.assetName),
  ])
  return {
    batchId: batchEvent?.parsed.demoBatchId || batchEvent?.parsed.batchId || batchEvent?.parsed.demoCaseId || 'DEMO-RANGE-OFFLINE-V1',
    targetAsset: targetAsset || '10.20.1.15 / prod-app-01',
    startedAt: earliestTime([...externalEvents.value.map((item) => item.eventTime), ...alerts.value.map((item) => item.eventTime)]) || '-',
    source: batchEvent ? '样例字段' : '接口聚合',
  }
})

const currentCounts = computed<WorkflowCounts>(() => ({
  events: evidenceChain.value?.summary.eventCount || externalTotal.value,
  alerts: evidenceChain.value?.summary.alertCount || alertTotal.value,
  vulnerabilities: evidenceChain.value?.summary.vulnerabilityCount || vulnerabilities.value.length,
  tickets: evidenceChain.value?.summary.ticketCount || tickets.value.length,
  reports: evidenceChain.value?.summary.reportCount || reports.value.length,
  incidents: incidents.value.length,
}))

const currentBatch = computed(() => ({
  batchId: activeRun.value?.batchId || discoveredBatch.value.batchId,
  targetAsset: discoveredBatch.value.targetAsset,
  startedAt: discoveredBatch.value.startedAt,
  source: activeRun.value ? `工作流 ${activeRun.value.id}` : discoveredBatch.value.source,
}))

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
    return action('确认场景并继续', false, false, undefined, () => setActiveStep(1))
  }
  if (activeStep.value === 1) {
    return action('导入演示数据', importing.value, false, 'soc:demo-range:import', confirmImportBatch)
  }
  if (activeStep.value === 2) {
    return action('进入告警中心', false, false, undefined, goBatchAlerts)
  }
  if (activeStep.value === 3) {
    return action(ticketPreview.value.length ? '查看工单时间线' : '进入告警转工单', false, false, undefined, goTicketTimeline)
  }
  return action('生成安全验证报告', generating.value, false, undefined, confirmGenerateReport)
})

onMounted(() => {
  runs.value = loadWorkflowRuns()
  syncRouteWorkflow()
  load()
})

watch(
  () => [route.params.runId, route.query.step],
  () => syncRouteWorkflow(),
)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [eventRes, alertRes, vulnerabilityRes, ticketRes, reportRes] = await Promise.all([
      listExternalEvents({ pageNum: 1, pageSize: 100 }),
      listAlerts({ pageNum: 1, pageSize: 100 }),
      listVulnerabilities({ pageNum: 1, pageSize: 100 }),
      listTickets({ pageNum: 1, pageSize: 50 }),
      listReports({ pageNum: 1, pageSize: 20 }),
    ])
    externalEvents.value = eventRes.data.data.records
    externalTotal.value = eventRes.data.data.total
    alerts.value = alertRes.data.data.records
    alertTotal.value = alertRes.data.data.total
    vulnerabilities.value = vulnerabilityRes.data.data.records
    tickets.value = ticketRes.data.data.records
    reports.value = reportRes.data.data.records
    await loadEvidenceChain(currentBatch.value.batchId)
    await loadIncidentClusters(currentBatch.value.batchId)
    syncActiveRunSnapshot()
  } catch {
    error.value = '安全验证数据加载失败，请检查登录状态、权限或后端服务。'
  } finally {
    loading.value = false
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
    if (res.data.data.vulnerabilities?.length) vulnerabilities.value = res.data.data.vulnerabilities
    if (res.data.data.tickets?.length) tickets.value = res.data.data.tickets
    if (res.data.data.reports?.length) reports.value = res.data.data.reports
  } catch {
    evidenceChain.value = undefined
  }
}

function createWorkflow() {
  const now = new Date().toISOString()
  const run: DemoWorkflowRun = {
    id: `SV-${compactDate(now)}-${Date.now().toString(36).toUpperCase()}`,
    batchId: discoveredBatch.value.batchId,
    selectedCaseId: demoCases[0].id,
    stepKey: 'scenario',
    status: 'active',
    createdAt: now,
    updatedAt: now,
    lastVisitedAt: now,
    counts: currentCounts.value,
    logs: [workflowLog('create', '创建安全验证工作流', `批次 ${discoveredBatch.value.batchId}`, 'scenario')],
  }
  runs.value = [run, ...runs.value].slice(0, MAX_WORKFLOWS)
  persistWorkflowRuns()
  router.push({ path: `/soc/demo-range/runs/${run.id}`, query: { step: run.stepKey } })
}

function resumeWorkflow(run: DemoWorkflowRun) {
  patchRun(run.id, { lastVisitedAt: new Date().toISOString() }, workflowLog('resume', '继续安全验证工作流', stepTitle(run.stepKey), run.stepKey))
  router.push({ path: `/soc/demo-range/runs/${run.id}`, query: { step: run.stepKey } })
}

async function removeWorkflow(run: DemoWorkflowRun) {
  try {
    await ElMessageBox.confirm(`确认删除工作流记录 ${run.id}？只删除本地工作流记录，不删除 SOC 数据。`, '删除工作流记录', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  runs.value = runs.value.filter((item) => item.id !== run.id)
  persistWorkflowRuns()
}

function syncRouteWorkflow() {
  const run = activeRun.value
  if (!activeRunId.value) {
    activeStep.value = 0
    const requestedStep = stepKeyFromQuery(route.query.step)
    if (requestedStep) {
      const latestRun = sortedRuns.value[0]
      if (latestRun) {
        router.replace({ path: `/soc/demo-range/runs/${latestRun.id}`, query: { step: requestedStep } }).catch(() => undefined)
      } else {
        router.replace({ path: '/soc/demo-range' }).catch(() => undefined)
      }
    }
    return
  }
  if (!run) {
    router.replace('/soc/demo-range')
    return
  }
  const stepKey = stepKeyFromQuery(route.query.step) || run.stepKey
  activeStep.value = stepIndex(stepKey)
  if (route.query.step !== stepKey) {
    router.replace({ path: `/soc/demo-range/runs/${run.id}`, query: { ...route.query, step: stepKey } }).catch(() => undefined)
  }
  patchRun(run.id, { stepKey, lastVisitedAt: new Date().toISOString() })
}

function setActiveStep(index: number) {
  const run = activeRun.value
  if (!run) return
  const bounded = Math.min(Math.max(index, 0), wizardSteps.length - 1)
  const stepKey = wizardSteps[bounded].key
  activeStep.value = bounded
  patchRun(run.id, { stepKey }, workflowLog('step', `进入步骤：${stepTitle(stepKey)}`, currentBatch.value.batchId, stepKey))
  router.replace({ path: `/soc/demo-range/runs/${run.id}`, query: { ...route.query, step: stepKey } }).catch(() => undefined)
}

function selectCase(demoCase: DemoCase) {
  const run = activeRun.value
  if (!run) return
  patchRun(run.id, { selectedCaseId: demoCase.id }, workflowLog('case', `选择场景：${demoCase.title}`, demoCase.category, 'scenario'))
}

async function confirmImportBatch() {
  const run = activeRun.value
  if (!run) return
  try {
    await ElMessageBox.confirm('确认导入完整演示数据？该操作会先清理旧演示数据，再写入固定演示资产、告警、工单、报表和离线证据链；不会修改用户、角色或账号。', '导入演示数据', {
      type: 'warning',
      confirmButtonText: '导入演示数据',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  importing.value = true
  try {
    const res = await importDemoData()
    lastBatchResult.value = res.data.data
    patchRun(run.id, {
      batchId: res.data.data.demoRangeBatchId,
      counts: {
        ...run.counts,
        events: res.data.data.importedRangeEvents,
        alerts: res.data.data.createdRangeAlerts,
        vulnerabilities: res.data.data.createdRangeVulnerabilities,
      },
    }, workflowLog('import', '导入演示数据', `事件 ${res.data.data.importedRangeEvents}，告警 ${res.data.data.createdRangeAlerts}`, 'evidence'))
    ElMessage.success(res.data.data.message || `演示数据 ${res.data.data.demoRangeBatchId} 已导入`)
    await load()
    await loadEvidenceChain(res.data.data.demoRangeBatchId)
    setActiveStep(2)
  } catch {
    ElMessage.error('演示数据导入失败，请检查权限或后端服务。')
  } finally {
    importing.value = false
  }
}

async function confirmGenerateReport() {
  const run = activeRun.value
  if (!run) return
  try {
    await ElMessageBox.confirm('确认基于当前工作流批次生成安全验证报告？该操作不会执行扫描或访问外部目标。', '生成安全验证报告', {
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
    patchRun(run.id, { status: 'completed' }, workflowLog('report', '生成安全验证报告', currentBatch.value.batchId, 'report'))
    ElMessage.success('安全验证报告已生成')
    await loadEvidenceChain(currentBatch.value.batchId)
    syncActiveRunSnapshot()
  } catch {
    ElMessage.error('报表生成失败，请检查权限或后端服务。')
  } finally {
    generating.value = false
  }
}

async function sendDryRunNotification() {
  const run = activeRun.value
  if (!run) return
  notifying.value = true
  try {
    await sendShuffleDemoNotification()
    patchRun(run.id, {}, workflowLog('notify', '写入 dry-run 通知日志', currentBatch.value.batchId, 'report'))
    ElMessage.success('通知 dry-run 日志已写入')
    await loadEvidenceChain(currentBatch.value.batchId)
  } catch {
    ElMessage.error('通知 dry-run 写入失败，请检查权限或后端服务。')
  } finally {
    notifying.value = false
  }
}

async function runCorrelation() {
  const run = activeRun.value
  if (!run) return
  correlating.value = true
  try {
    const res = await correlateIncidents()
    await loadIncidentClusters(currentBatch.value.batchId)
    patchRun(run.id, {}, workflowLog('correlate', '执行事件关联', `刷新 ${res.data.data.upsertedClusters} 个事件簇`, 'alerts'))
    ElMessage.success(`事件关联完成：刷新 ${res.data.data.upsertedClusters} 个事件簇`)
    syncActiveRunSnapshot()
  } catch {
    ElMessage.error('事件关联失败，请检查权限、数据表或后端服务。')
  } finally {
    correlating.value = false
  }
}

function goBatchAlerts() {
  trackedPush('/soc/alerts', { keyword: currentBatch.value.batchId }, '打开告警中心', 'alerts')
}

function goTicketTimeline() {
  const firstTicket = evidenceChain.value?.tickets?.[0]
  if (firstTicket) {
    trackedPush('/soc/tickets', { keyword: firstTicket.ticketNo, openTicketId: firstTicket.id }, '查看工单时间线', 'tickets')
    return
  }
  const firstAlert = evidenceChain.value?.alerts?.find((item) => !item.ticketId) || evidenceChain.value?.alerts?.[0]
  trackedPush('/soc/alerts', { keyword: currentBatch.value.batchId, openAlertId: firstAlert?.id }, '进入告警转工单', 'tickets')
}

function goWorkflowHub() {
  router.push('/soc/demo-range')
}

function trackedPush(path: string, query: Record<string, string | number | undefined>, title: string, stepKey: WorkflowStepKey) {
  const run = activeRun.value
  const returnTo = run ? `/soc/demo-range/runs/${run.id}?step=${stepKey}` : '/soc/demo-range'
  if (run) {
    patchRun(run.id, { stepKey }, workflowLog('jump', title, `returnTo=${returnTo}`, stepKey))
  }
  router.push({
    path,
    query: {
      ...query,
      workflowRunId: run?.id,
      batchId: currentBatch.value.batchId,
      returnTo,
    },
  })
}

function syncActiveRunSnapshot() {
  const run = activeRun.value
  if (!run) return
  patchRun(run.id, {
    batchId: currentBatch.value.batchId,
    counts: currentCounts.value,
  })
}

function patchRun(runId: string, patch: Partial<DemoWorkflowRun>, log?: WorkflowLog) {
  const now = new Date().toISOString()
  runs.value = runs.value.map((run) => {
    if (run.id !== runId) return run
    return {
      ...run,
      ...patch,
      counts: patch.counts ? { ...run.counts, ...patch.counts } : run.counts,
      updatedAt: now,
      lastVisitedAt: patch.lastVisitedAt || now,
      logs: log ? [log, ...run.logs].slice(0, MAX_LOGS) : run.logs,
    }
  })
  persistWorkflowRuns()
}

function loadWorkflowRuns() {
  try {
    const raw = localStorage.getItem(WORKFLOW_STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as DemoWorkflowRun[]
    return Array.isArray(parsed) ? parsed.filter(isWorkflowRun).slice(0, MAX_WORKFLOWS) : []
  } catch {
    return []
  }
}

function persistWorkflowRuns() {
  localStorage.setItem(WORKFLOW_STORAGE_KEY, JSON.stringify(runs.value.slice(0, MAX_WORKFLOWS)))
}

function workflowLog(type: string, title: string, detail?: string, stepKey?: WorkflowStepKey): WorkflowLog {
  return {
    id: `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`,
    time: new Date().toISOString(),
    type,
    title,
    detail,
    stepKey,
  }
}

function isWorkflowRun(value: unknown): value is DemoWorkflowRun {
  const candidate = value as DemoWorkflowRun
  return Boolean(candidate?.id && candidate.batchId && candidate.stepKey && candidate.createdAt)
}

function action(label: string, loading: boolean, disabled: boolean, permission: string | undefined, handler: () => void | Promise<void>) {
  return { label, loading, disabled, permission, handler }
}

function openWorkflowDetails() {
  const run = activeRun.value
  if (!run) return
  openDetails('工作流记录', [
    { label: 'Run ID', value: run.id },
    { label: 'Batch ID', value: run.batchId },
    { label: '当前步骤', value: stepTitle(run.stepKey) },
    { label: '场景', value: caseTitle(run.selectedCaseId) },
    { label: '状态', value: run.status === 'completed' ? '已完成' : '进行中' },
    { label: '创建时间', value: formatDateTime(run.createdAt) },
    { label: '更新时间', value: formatDateTime(run.updatedAt) },
  ], [], run.logs.map((log) => `${formatDateTime(log.time)} ${log.title}${log.detail ? `：${log.detail}` : ''}`))
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

function openDetails(title: string, rows: DetailDrawerState['rows'], tags: string[] = [], lines: string[] = []) {
  detailDrawer.value = { visible: true, title, rows, tags, lines }
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
  }
}

function stringField(parsed: Record<string, unknown>, key: string) {
  const value = parsed[key]
  return value === undefined || value === null ? undefined : String(value)
}

function stepKeyFromQuery(step: unknown): WorkflowStepKey | undefined {
  const value = Array.isArray(step) ? step[0] : step
  if (typeof value !== 'string') return undefined
  const byKey = wizardSteps.find((item) => item.key === value)
  if (byKey) return byKey.key
  const byNumber = Number(value)
  if (!Number.isFinite(byNumber)) return undefined
  return wizardSteps[Math.min(Math.max(byNumber - 1, 0), wizardSteps.length - 1)]?.key
}

function stepIndex(stepKey: WorkflowStepKey) {
  return Math.max(0, wizardSteps.findIndex((step) => step.key === stepKey))
}

function stepTitle(stepKey?: string) {
  return wizardSteps.find((step) => step.key === stepKey)?.title || '-'
}

function caseTitle(caseId?: string) {
  return demoCases.find((item) => item.id === caseId)?.title || '未选择'
}

function severityTag(severity?: string): TagType {
  const normalized = (severity || '').toLowerCase()
  if (normalized === 'critical' || normalized === 'high') return 'danger'
  if (normalized === 'medium') return 'warning'
  if (normalized === 'low') return 'success'
  return 'info'
}

function routeParam(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value
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

function compactDate(value: string) {
  return value.slice(0, 10).replace(/-/g, '')
}

function formatDateTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}
</script>

<style scoped>
.demo-range-page,
.wizard-main,
.wizard-step-panel,
.wizard-next-panel,
.run-card {
  min-width: 0;
}

.run-hub-panel,
.run-record-panel,
.wizard-status-panel,
.wizard-step-panel,
.wizard-next-panel {
  padding: 16px;
}

.panel-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-title strong {
  display: block;
  color: var(--soc-text);
  font-size: 15px;
}

.panel-title span,
.run-card span,
.run-card footer,
.hub-summary-grid span,
.wizard-status-head span,
.wizard-status-head em,
.wizard-progress-copy span,
.next-card-head p,
.next-preview-card p,
.selected-case-panel p,
.wizard-list-row span,
.wizard-list-row em,
.record-list span,
.record-list em {
  color: var(--soc-text-muted);
  font-size: 13px;
  font-style: normal;
  line-height: 1.55;
}

.panel-actions,
.chain-actions,
.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.hub-summary-grid,
.run-summary-grid,
.batch-context-grid,
.step-metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.batch-context-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: 14px;
}

.hub-summary-grid article,
.run-summary-grid div,
.batch-context-grid article,
.next-preview-card,
.selected-case-panel,
.batch-result-strip,
.wizard-list-row,
.evidence-card,
.case-item,
.record-list article {
  border: 1px solid rgba(179, 173, 163, 0.42);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.58);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82);
}

.hub-summary-grid article,
.run-summary-grid div,
.batch-context-grid article,
.next-preview-card {
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 12px;
}

.hub-summary-grid strong,
.run-card strong,
.batch-context-grid strong,
.next-preview-card strong,
.selected-case-panel strong,
.wizard-list-row strong,
.case-item strong,
.evidence-card strong,
.record-list strong {
  min-width: 0;
  overflow-wrap: anywhere;
  color: var(--soc-text);
}

.run-list,
.wizard-main,
.wizard-list,
.record-list {
  display: grid;
  gap: 10px;
}

.run-card {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid rgba(179, 173, 163, 0.42);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.62);
}

.run-card header,
.run-card footer,
.wizard-status-head,
.selected-case-panel,
.batch-result-strip,
.wizard-list-row,
.record-list header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.run-card header div,
.next-card-head,
.selected-case-panel div,
.wizard-list-row > div,
.record-list article {
  display: grid;
  gap: 5px;
  min-width: 0;
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

.wizard-next-panel {
  position: sticky;
  top: 88px;
  display: grid;
  gap: 14px;
}

.next-card-head span {
  color: var(--soc-warm-strong);
  font-size: 12px;
  font-weight: 760;
}

.step-nav {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.case-list,
.evidence-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.evidence-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
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
  color: var(--soc-text-muted);
  line-height: 1.55;
}

.selected-case-panel,
.batch-result-strip,
.wizard-list-row {
  padding: 12px;
}

.step-metric-grid {
  margin-bottom: 14px;
}

.step-metric-grid :deep(.risk-card strong) {
  overflow-wrap: anywhere;
  font-size: 22px;
  line-height: 1.12;
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
  color: var(--soc-text-muted);
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

.wizard-list-row > div:last-child {
  justify-items: end;
}

.record-list {
  padding-top: 4px;
  border-top: 1px solid var(--soc-border);
}

.record-list article {
  padding: 10px;
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

@media (max-width: 1180px) {
  .wizard-shell {
    grid-template-columns: 1fr;
  }

  .wizard-next-panel {
    position: static;
  }

  .hub-summary-grid,
  .batch-context-grid,
  .evidence-grid,
  .step-metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .panel-title,
  .run-card header,
  .run-card footer,
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

  .hub-summary-grid,
  .run-summary-grid,
  .batch-context-grid,
  .case-list,
  .evidence-grid,
  .step-metric-grid,
  .step-nav {
    grid-template-columns: 1fr;
  }
}
</style>
