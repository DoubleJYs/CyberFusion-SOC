<template>
  <div class="product-experience-shell">
    <header class="showcase-topbar">
      <div class="showcase-brand">
        <strong>CyberFusion SOC</strong>
        <span>安全运营演示台</span>
      </div>
      <div class="showcase-topbar-actions">
        <el-tag v-if="showcaseData" :type="showcaseData.source === 'offline' ? 'warning' : 'success'" effect="plain">
          {{ showcaseData.sourceLabel }}
        </el-tag>
        <el-button text @click="goExpertMode">查看专家模式</el-button>
      </div>
    </header>

    <main class="showcase-main">
      <RecoverableErrorState
        v-if="loadError"
        :retrying="loading"
        :show-diagnostics="showDiagnostics"
        :diagnostics-text="diagnosticsText"
        @retry="load"
        @use-offline="useOfflineData"
        @toggle-diagnostics="showDiagnostics = !showDiagnostics"
      />

      <template v-if="showcaseData">
        <section class="showcase-hero">
          <div class="showcase-hero-copy">
            <span class="showcase-kicker">CUSTOMER DEMO MODE</span>
            <h1>CyberFusion 安全运营演示台</h1>
            <p>用一条演示链路展示风险识别、证据归一化、告警处置和报告输出。</p>
            <div class="showcase-hero-actions">
              <el-button type="primary" size="large" @click="startValidation">开始安全验证</el-button>
              <el-button size="large" @click="goExpertMode">查看专家模式</el-button>
            </div>
            <el-alert
              v-if="showcaseData.source === 'offline'"
              class="offline-banner"
              title="当前展示离线演示数据，不代表生产环境，也不会写入后端数据库。"
              type="warning"
              show-icon
              :closable="false"
            />
          </div>

          <aside class="showcase-hero-card">
            <div class="showcase-status" :class="`status-${showcaseData.status}`">
              <div>
                <span>当前状态</span>
                <strong>{{ statusLabel }}</strong>
              </div>
              <el-tag :type="statusTagType" effect="plain">{{ showcaseData.sourceLabel }}</el-tag>
            </div>
            <div class="showcase-risk-copy">
              <span>最高优先级风险</span>
              <strong>{{ showcaseData.highestRisk }}</strong>
              <em class="showcase-batch-id">本次演示 batchId：{{ showcaseData.batchId }}</em>
            </div>
            <div class="metric-grid">
              <article class="metric-card">
                <span>待处理告警</span>
                <strong>{{ showcaseData.pendingAlerts }}</strong>
                <em>需要确认的安全提醒</em>
              </article>
              <article class="metric-card">
                <span>待关闭工单</span>
                <strong>{{ showcaseData.openTickets }}</strong>
                <em>处置链路中的任务</em>
              </article>
              <article class="metric-card">
                <span>证据数量</span>
                <strong>{{ showcaseData.report.importedEvidence }}</strong>
                <em>本批次归一化记录</em>
              </article>
            </div>
          </aside>
        </section>

        <section class="showcase-section storyline-section">
          <div class="showcase-section-head">
            <div>
              <span class="showcase-kicker">SECURITY OPERATIONS STORY</span>
              <h2>本次安全验证故事线</h2>
              <p>从证据导入到报告输出，把技术信号翻译成客户能理解的运营闭环。</p>
            </div>
            <el-button @click="goReports">查看报告入口</el-button>
          </div>
          <div class="storyline-grid">
            <article v-for="(step, index) in showcaseData.storySteps" :key="step.key" class="storyline-card">
              <span class="storyline-index">{{ index + 1 }}</span>
              <div class="storyline-copy">
                <div>
                  <strong>{{ step.title }}</strong>
                  <el-tag effect="plain">{{ step.status }}</el-tag>
                </div>
                <p>{{ step.explanation }}</p>
              </div>
              <div class="storyline-metric">
                <strong>{{ step.count }}</strong>
                <span>{{ step.countLabel }}</span>
              </div>
              <el-button text @click="goStoryStep(step.route)">跳转查看</el-button>
            </article>
          </div>
        </section>

        <section ref="flowSectionRef" class="showcase-section" v-loading="loading || actionLoading">
          <div class="showcase-section-head">
            <div>
              <span class="showcase-kicker">GUIDED STEPPER</span>
              <h2>5 步安全验证流程</h2>
              <p>一屏只突出一个动作，复杂字段收起到技术证据抽屉。</p>
            </div>
            <el-tag effect="plain">第 {{ activeStep + 1 }} / {{ showcaseData.steps.length }} 步</el-tag>
          </div>

          <div class="guided-showcase-layout">
            <div class="step-list">
              <article
                v-for="(step, index) in showcaseData.steps"
                :key="step.key"
                class="showcase-step-card"
                :class="{ active: index === activeStep }"
                role="button"
                tabindex="0"
                @click="activeStep = index"
                @keydown.enter.prevent="activeStep = index"
                @keydown.space.prevent="activeStep = index"
              >
                <span class="showcase-step-index">{{ index + 1 }}</span>
                <div>
                  <span class="showcase-step-eyebrow">{{ step.status }}</span>
                  <h3>{{ step.title }}</h3>
                  <p>{{ step.businessSummary }}</p>
                  <el-button text @click.stop="openStepEvidence(step.key)">查看技术证据</el-button>
                </div>
                <div class="showcase-step-meta">
                  <strong>{{ step.count }}</strong>
                  <span>{{ step.countLabel }}</span>
                </div>
              </article>
            </div>

            <aside class="action-rail">
              <div>
                <span class="showcase-kicker">NEXT ACTION</span>
                <h3>{{ currentStep.title }}</h3>
                <p>{{ currentStep.businessSummary }}</p>
              </div>
              <el-button type="primary" size="large" :loading="actionLoading" @click="runCurrentStepAction">
                {{ currentStep.primaryAction }}
              </el-button>
              <div class="rail-links">
                <button type="button" @click="goExternalEvents"><span>查看证据</span><strong>{{ showcaseData.report.importedEvidence }}</strong></button>
                <button type="button" @click="goAlerts"><span>查看告警</span><strong>{{ showcaseData.report.createdAlerts }}</strong></button>
                <button type="button" @click="goTickets"><span>查看工单</span><strong>{{ showcaseData.openTickets }}</strong></button>
                <button type="button" @click="goReports"><span>生成报告</span><strong>{{ showcaseData.reports.length }}</strong></button>
              </div>
            </aside>
          </div>
        </section>

        <section class="showcase-section">
          <div class="showcase-section-head">
            <div>
              <span class="showcase-kicker">EVIDENCE SUMMARY</span>
              <h2>证据摘要</h2>
              <p>默认展示业务化摘要，raw JSON、ruleId、sourceType、eventType、requestId、demoCaseId 放在抽屉里。</p>
            </div>
          </div>
          <div class="evidence-grid">
            <article v-for="item in showcaseData.evidence" :key="item.key" class="evidence-summary-card">
              <strong>{{ item.title }}</strong>
              <p>{{ item.summary }}</p>
              <span class="evidence-count">{{ item.count }}</span>
              <el-button text @click="openEvidence(item.key)">查看技术证据</el-button>
            </article>
          </div>
        </section>

        <section class="showcase-section">
          <div class="showcase-section-head">
            <div>
              <span class="showcase-kicker">INCIDENT CHAIN</span>
              <h2>本次验证事件链</h2>
              <p>把同一资产、同一批次和同一时间窗口内的证据聚合成客户可理解的处置链路。</p>
            </div>
            <el-button @click="goIncidentChain">进入安全事件簇</el-button>
          </div>
          <div class="incident-chain-grid">
            <article v-for="incident in showcaseData.incidentClusters" :key="incident.clusterNo" class="incident-chain-card">
              <div>
                <span>{{ incident.clusterNo }}</span>
                <strong>{{ incident.title }}</strong>
                <p>{{ incident.summary }}</p>
              </div>
              <div class="incident-chain-meta">
                <el-tag effect="plain">{{ incident.severity }}</el-tag>
                <span>{{ incidentEvidenceCount(incident) }} 条证据 · {{ incident.sourceSummary || incident.sourceTypes || '多源证据' }}</span>
                <el-button text @click="goIncidentChain(incident.clusterNo)">查看链路</el-button>
              </div>
            </article>
            <el-empty v-if="!showcaseData.incidentClusters.length" description="暂无事件链，可先导入演示批次并执行关联" :image-size="80" />
          </div>
        </section>

        <section class="showcase-section">
          <div class="showcase-section-head">
            <div>
              <span class="showcase-kicker">RESPONSE LOOP</span>
              <h2>处置闭环</h2>
              <p>从最高优先级告警进入工单处置链路，保留演示批次上下文。</p>
            </div>
          </div>
          <div class="closure-layout">
            <article class="closure-card">
              <el-tag type="danger" effect="plain">最高优先级</el-tag>
              <h3>{{ showcaseData.closure.title }}</h3>
              <p>{{ showcaseData.closure.whyItMatters }}</p>
              <div class="closure-facts">
                <div><span>影响资产</span><strong>{{ showcaseData.closure.assetIp }}</strong></div>
                <div><span>目标路径</span><strong>{{ showcaseData.closure.targetUrl }}</strong></div>
                <div><span>当前状态</span><strong>{{ showcaseData.closure.status }}</strong></div>
                <div><span>建议动作</span><strong>{{ showcaseData.closure.suggestion }}</strong></div>
              </div>
            </article>
            <aside class="closure-card">
              <h3>下一步处置</h3>
              <p>演示时优先展示“为什么重要”和“如何闭环”，技术细节只在客户追问时打开。</p>
              <div class="playbook-summary">
                <span>处置剧本</span>
                <strong>{{ showcaseData.playbookSummary }}</strong>
              </div>
              <div class="closure-actions">
                <el-button type="primary" @click="convertToTicket">转为处置工单</el-button>
                <el-button @click="goAlerts">进入告警处置</el-button>
              </div>
            </aside>
          </div>
        </section>

        <section class="showcase-section">
          <div class="showcase-section-head">
            <div>
              <span class="showcase-kicker">REPORT PREVIEW</span>
              <h2>验证报告</h2>
              <p>汇总本次导入证据、告警、阻断、漏洞、工单状态和 dry-run 通知记录。</p>
            </div>
            <div class="report-actions">
              <el-button type="primary" :loading="actionLoading" @click="generateValidationReport">生成安全验证报告</el-button>
              <el-button @click="goReports">进入报告中心</el-button>
            </div>
          </div>
          <div class="report-grid">
            <article v-for="item in reportCards" :key="item.label" class="report-preview-card">
              <strong>{{ item.label }}</strong>
              <span class="report-count">{{ item.value }}</span>
              <p>{{ item.description }}</p>
            </article>
          </div>
        </section>
      </template>
    </main>

    <EvidenceDrawer
      v-model="drawerVisible"
      :title="drawerTitle"
      :rows="drawerRows"
      :source-label="showcaseData?.sourceLabel"
      :normalized-event="drawerNormalizedEvent"
      :raw-json="drawerRawJson"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import EvidenceDrawer from '@/components/showcase/EvidenceDrawer.vue'
import RecoverableErrorState from '@/components/showcase/RecoverableErrorState.vue'
import { LOCAL_DEMO_TOKEN } from '@/stores/auth'
import { getToken } from '@/utils/storage'
import { EXPERT_HOME_PATH } from '@/utils/roleExperience'
import {
  createSecurityValidationReport,
  importShowcaseBatch,
  loadLiveShowcaseData,
  offlineShowcaseData,
  type ShowcaseData,
  type ShowcaseEvidence,
} from '@/api/showcase'
import type { IncidentClusterItem } from '@/api/soc'

const router = useRouter()
const showcaseData = ref<ShowcaseData | null>(null)
const loading = ref(false)
const actionLoading = ref(false)
const loadError = ref('')
const showDiagnostics = ref(false)
const activeStep = ref(0)
const flowSectionRef = ref<HTMLElement>()
const drawerVisible = ref(false)
const selectedEvidence = ref<ShowcaseEvidence | null>(null)
const drawerTitle = ref('技术证据')
const errorDiagnostics = ref<string[]>([])

const currentStep = computed(() => showcaseData.value?.steps[activeStep.value] || offlineShowcaseData.steps[0])
const statusLabel = computed(() => {
  if (showcaseData.value?.status === 'safe') return '安全'
  if (showcaseData.value?.status === 'attention') return '注意'
  return '严重'
})
const statusTagType = computed(() => {
  if (showcaseData.value?.status === 'safe') return 'success'
  if (showcaseData.value?.status === 'attention') return 'warning'
  return 'danger'
})
const diagnosticsText = computed(() => {
  const lines = [
    loadError.value,
    ...errorDiagnostics.value,
    ...(showcaseData.value?.diagnostics || []),
  ].filter(Boolean)
  return lines.join('\n')
})
const reportCards = computed(() => {
  const report = showcaseData.value?.report || offlineShowcaseData.report
  return [
    { label: '导入证据数', value: report.importedEvidence, description: 'soc_external_event 等证据记录' },
    { label: '生成告警数', value: report.createdAlerts, description: '由规则或导入联动产生' },
    { label: '阻断次数', value: report.blockedCount, description: '网关或策略阻断证据' },
    { label: '漏洞数量', value: report.vulnerabilityCount, description: '组件与依赖风险记录' },
    { label: '工单状态', value: report.ticketStatus, description: '处置链路当前进度' },
    { label: 'dry-run 通知', value: report.dryRunNotifications, description: '只写日志，不真实发送' },
  ]
})
const drawerRows = computed(() => {
  const item = selectedEvidence.value
  if (!item) return []
  return [
    { label: 'sourceType', value: item.sourceType },
    { label: 'eventType', value: item.eventType },
    { label: 'ruleId', value: item.ruleId },
    { label: 'ruleName', value: item.ruleName },
    { label: 'requestId', value: item.requestId },
    { label: 'demoCaseId', value: item.demoCaseId },
    { label: 'batchId', value: showcaseData.value?.batchId },
  ]
})
const drawerNormalizedEvent = computed(() => selectedEvidence.value?.normalizedEvent)
const drawerRawJson = computed(() => selectedEvidence.value?.rawJson)

onMounted(load)

async function load() {
  loading.value = true
  loadError.value = ''
  errorDiagnostics.value = []
  if (getToken() === LOCAL_DEMO_TOKEN) {
    showcaseData.value = JSON.parse(JSON.stringify(offlineShowcaseData)) as ShowcaseData
    loading.value = false
    return
  }
  try {
    showcaseData.value = await loadLiveShowcaseData(showcaseData.value?.batchId)
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    showcaseData.value = JSON.parse(JSON.stringify(offlineShowcaseData)) as ShowcaseData
    loadError.value = ''
    errorDiagnostics.value = [`实时接口不可用，已自动切换离线演示数据：${message}`]
  } finally {
    loading.value = false
  }
}

function useOfflineData() {
  showcaseData.value = JSON.parse(JSON.stringify(offlineShowcaseData)) as ShowcaseData
  loadError.value = ''
  errorDiagnostics.value = []
  ElMessage.warning('已切换为离线演示数据')
}

function startValidation() {
  activeStep.value = 0
  flowSectionRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function openEvidence(key: string) {
  const item = showcaseData.value?.evidence.find((evidence) => evidence.key === key) || offlineShowcaseData.evidence[0]
  selectedEvidence.value = item
  drawerTitle.value = item.title
  drawerVisible.value = true
}

function openStepEvidence(stepKey: string) {
  const map: Record<string, string> = {
    scenario: 'waf',
    import: 'waf',
    alert: 'waf',
    ticket: 'wazuh',
    report: 'trivy',
  }
  openEvidence(map[stepKey] || 'waf')
}

async function runCurrentStepAction() {
  const stepKey = currentStep.value.key
  if (stepKey === 'scenario') {
    activeStep.value = 1
    return
  }
  if (stepKey === 'import') {
    await importEvidenceBatch()
    return
  }
  if (stepKey === 'alert') {
    goAlerts()
    return
  }
  if (stepKey === 'ticket') {
    goTickets()
    return
  }
  await generateValidationReport()
}

async function importEvidenceBatch() {
  if (showcaseData.value?.source === 'offline') {
    ElMessage.warning('离线演示数据不会写入后端，请进入专家模式执行真实批次导入。')
    return
  }
  actionLoading.value = true
  try {
    const result = await importShowcaseBatch()
    ElMessage.success(result.message || `已导入演示数据 ${result.demoRangeBatchId}`)
    showcaseData.value = await loadLiveShowcaseData(result.demoRangeBatchId)
    activeStep.value = 2
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    loadError.value = '演示数据加载失败'
    errorDiagnostics.value = [`导入演示批次失败：${message}`]
    showDiagnostics.value = true
  } finally {
    actionLoading.value = false
  }
}

async function generateValidationReport() {
  if (showcaseData.value?.source === 'offline') {
    ElMessage.warning('离线演示数据不生成真实报告，请进入报告中心查看真实数据。')
    goReports()
    return
  }
  actionLoading.value = true
  try {
    await createSecurityValidationReport(showcaseData.value?.batchId || offlineShowcaseData.batchId)
    ElMessage.success('安全验证报告已生成')
    await load()
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    loadError.value = '演示数据加载失败'
    errorDiagnostics.value = [`生成安全验证报告失败：${message}`]
    showDiagnostics.value = true
  } finally {
    actionLoading.value = false
  }
}

function convertToTicket() {
  const alertId = showcaseData.value?.closure.alertId
  if (alertId) {
    router.push({ path: '/soc/alerts', query: { openAlertId: alertId, keyword: showcaseData.value?.batchId } })
    return
  }
  goAlerts()
}

function goExpertMode() {
  router.push(EXPERT_HOME_PATH)
}

function goExternalEvents() {
  router.push({ path: '/soc/external-events', query: { keyword: showcaseData.value?.batchId } })
}

function goAlerts() {
  router.push({ path: '/soc/alerts', query: { keyword: showcaseData.value?.batchId } })
}

function goTickets() {
  router.push({ path: '/soc/tickets', query: { keyword: showcaseData.value?.batchId } })
}

function goReports() {
  router.push({ path: '/soc/reports', query: { reportType: 'security_validation', keyword: showcaseData.value?.batchId } })
}

function goStoryStep(route: string) {
  if (route === '/soc/reports') {
    goReports()
    return
  }
  router.push({ path: route, query: { keyword: showcaseData.value?.batchId } })
}

function goIncidentChain(keyword?: string) {
  router.push({ path: '/soc/incidents', query: { keyword: typeof keyword === 'string' ? keyword : showcaseData.value?.batchId } })
}

function incidentEvidenceCount(incident: IncidentClusterItem) {
  return incident.evidenceCount ?? ((incident.eventCount || 0) + (incident.alertCount || 0) + (incident.vulnerabilityCount || 0))
}
</script>

<style scoped>
.incident-chain-grid {
  display: grid;
  gap: 12px;
}

.incident-chain-card {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 18px;
  border: 1px solid rgba(179, 173, 163, 0.35);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.76);
}

.incident-chain-card div:first-child {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.incident-chain-card span,
.incident-chain-card p {
  margin: 0;
  color: var(--soc-text-muted);
}

.incident-chain-card strong {
  color: var(--soc-text);
  font-size: 18px;
}

.incident-chain-meta {
  display: grid;
  gap: 8px;
  justify-items: end;
  min-width: 180px;
}

.playbook-summary {
  display: grid;
  gap: 6px;
  margin: 14px 0;
  padding: 12px;
  border: 1px solid rgba(179, 173, 163, 0.38);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.68);
}

.playbook-summary span {
  color: var(--soc-text-muted);
  font-size: 12px;
  font-weight: 760;
}

.playbook-summary strong {
  color: var(--soc-text);
  line-height: 1.6;
}

.storyline-section {
  position: relative;
}

.storyline-grid {
  display: grid;
  gap: 12px;
}

.storyline-card {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 84px auto;
  align-items: center;
  gap: 14px;
  padding: 16px;
  border: 1px solid rgba(179, 173, 163, 0.34);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.76);
}

.storyline-index {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 999px;
  color: #9a4f12;
  background: rgba(224, 133, 48, 0.14);
  font-weight: 800;
}

.storyline-copy {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.storyline-copy > div {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.storyline-copy strong {
  color: var(--soc-text);
  font-size: 16px;
}

.storyline-copy p,
.storyline-metric span {
  margin: 0;
  color: var(--soc-text-muted);
}

.storyline-metric {
  display: grid;
  justify-items: end;
  gap: 2px;
}

.storyline-metric strong {
  color: var(--soc-text);
  font-size: 22px;
}

@media (max-width: 760px) {
  .storyline-card {
    grid-template-columns: 34px minmax(0, 1fr);
  }

  .storyline-metric {
    justify-items: start;
    grid-column: 2;
  }
}
</style>
