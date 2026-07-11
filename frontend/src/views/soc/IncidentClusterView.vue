<template>
  <div class="page-shell incident-cluster-page">
    <section class="soc-page-hero">
      <div>
        <span class="soc-page-kicker">CORRELATION ENGINE</span>
        <h1>安全事件簇</h1>
        <p>这个页面帮你把同一资产、同一批次和同一时间窗口内的多源证据聚合成可处置事件链。</p>
      </div>
      <div class="hero-actions">
        <el-button type="primary" :loading="correlating" @click="runCorrelation">执行关联</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </section>

    <el-alert v-if="error" title="事件簇加载失败" type="error" show-icon :closable="false" class="recoverable-alert">
      <template #default>
        <p>可能是后端未启动、数据库未初始化或当前账号没有事件簇权限。</p>
        <div class="recoverable-actions">
          <el-button size="small" type="primary" @click="load">重试</el-button>
          <el-button size="small" @click="diagnosticsVisible = !diagnosticsVisible">查看诊断</el-button>
          <el-button size="small" @click="resetFilters">返回列表</el-button>
        </div>
        <pre v-if="diagnosticsVisible || appStore.showDiagnostics" class="diagnostic-box">{{ diagnosticText }}</pre>
      </template>
    </el-alert>

    <section class="soc-panel panel-pad">
      <div class="soc-filter-bar">
        <el-input v-model="query.keyword" clearable placeholder="搜索事件簇编号、资产 IP、batchId" @keyup.enter="load" />
        <el-select v-model="query.severity" clearable placeholder="等级">
          <el-option label="严重" value="critical" />
          <el-option label="高危" value="high" />
          <el-option label="中危" value="medium" />
          <el-option label="低危" value="low" />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="状态">
          <el-option label="打开" value="open" />
          <el-option label="调查中" value="investigating" />
          <el-option label="已转工单" value="ticketed" />
          <el-option label="已关闭" value="closed" />
        </el-select>
        <el-button @click="load">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </section>

    <section class="table-panel">
      <el-table v-loading="loading" :data="rows" empty-text="暂无事件簇，点击“执行关联”生成结果" @row-click="open">
        <el-table-column prop="clusterNo" label="事件簇" width="150" />
        <el-table-column label="等级" width="90">
          <template #default="{ row }"><SeverityBadge :severity="row.severity" /></template>
        </el-table-column>
        <el-table-column label="标题" min-width="250" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="title-cell">
              <strong>{{ row.title }}</strong>
              <span>{{ row.summary || sourceLabel(row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="资产" width="180">
          <template #default="{ row }">{{ assetLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="证据数" width="110">
          <template #default="{ row }">{{ evidenceCount(row) }}</template>
        </el-table-column>
        <el-table-column prop="alertCount" label="告警数" width="90" />
        <el-table-column prop="vulnerabilityCount" label="漏洞数" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusBadge :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="首次出现" width="170">
          <template #default="{ row }">{{ formatTime(row.firstSeenAt) }}</template>
        </el-table-column>
        <el-table-column label="最近出现" width="170">
          <template #default="{ row }">{{ formatTime(row.lastSeenAt) }}</template>
        </el-table-column>
        <el-table-column label="推荐动作" min-width="150">
          <template #default="{ row }">{{ actionLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button size="small" @click.stop="open(row)">详情</el-button>
            <el-button v-if="row.ticketId" size="small" type="primary" @click.stop="goTicket(row)">查看工单</el-button>
            <el-button v-else size="small" type="warning" @click.stop="createTicket(row)">转工单</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-row">
        <span>共 {{ total }} 个事件簇</span>
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" layout="total, sizes, prev, pager, next" :total="total" @change="load" />
      </div>
    </section>

    <el-drawer v-model="drawer" title="事件簇详情" size="760px">
      <el-alert v-if="detailError" title="事件簇详情加载失败" type="error" show-icon :closable="false" class="recoverable-alert">
        <template #default>
          <p>可能是事件簇已被更新、当前账号权限不足或后端暂时不可用。</p>
          <div class="recoverable-actions">
            <el-button size="small" type="primary" @click="current && open(current)">重试</el-button>
            <el-button size="small" @click="diagnosticsVisible = !diagnosticsVisible">查看诊断</el-button>
            <el-button size="small" @click="drawer = false">返回列表</el-button>
          </div>
        </template>
      </el-alert>

      <div v-if="current" class="drawer-stack">
        <div class="soc-drawer-grid">
          <span>事件簇编号</span><strong>{{ current.clusterNo }}</strong>
          <span>状态</span><strong><StatusBadge :status="current.status" /></strong>
          <span>等级</span><strong><SeverityBadge :severity="current.severity" /></strong>
          <span>关联分</span><strong>{{ current.score }}</strong>
          <span>资产</span><strong>{{ assetLabel(current) }}</strong>
          <span>来源</span><strong>{{ sourceLabel(current) }}</strong>
          <template v-if="appStore.showAdvanced">
            <span>Batch</span><strong>{{ current.batchId || '-' }}</strong>
            <span>Demo Case</span><strong>{{ current.demoCaseId || '-' }}</strong>
          </template>
          <span>首次出现</span><strong>{{ formatTime(current.firstSeenAt) }}</strong>
          <span>最近出现</span><strong>{{ formatTime(current.lastSeenAt) }}</strong>
          <template v-if="appStore.showRawEvidence">
            <span>关联规则</span><strong>{{ current.ruleKey || '-' }}</strong>
            <span>Correlation Key</span><strong>{{ current.correlationKey || '-' }}</strong>
          </template>
          <span>推荐动作</span><strong>{{ current.recommendation || actionLabel(current) }}</strong>
          <span>摘要</span><strong>{{ current.summary || '-' }}</strong>
        </div>
        <SecurityDispositionGuide
          category="incident"
          :subject="current.title || current.clusterNo"
          :severity="current.severity"
          :status="current.status"
          :asset="assetLabel(current)"
          :reason="current.summary || `该事件簇已聚合 ${current.eventCount || 0} 条事件与 ${current.alertCount || 0} 条告警。`"
          :recommendation="current.recommendation || actionLabel(current)"
        />

        <section class="detail-section">
          <div class="section-title">
            <strong>事件链时间线</strong>
            <el-tag effect="plain">{{ evidenceTimeline.length }} 条</el-tag>
          </div>
          <el-timeline v-if="evidenceTimeline.length">
            <el-timeline-item
              v-for="item in evidenceTimeline"
              :key="`${item.evidenceType}-${item.evidenceId}`"
              :timestamp="formatTime(item.eventTime)"
              placement="top"
            >
              <article class="timeline-card">
                <div>
                  <strong>{{ evidenceTypeLabel(item.evidenceType) }} #{{ item.evidenceId }}</strong>
                  <span>{{ item.sourceType || '-' }} / {{ item.eventType || '-' }} / {{ item.ruleId || '-' }}</span>
                  <p>{{ relationReasonText(item) }}</p>
                </div>
                <el-tag effect="plain">{{ item.relationScore }}</el-tag>
              </article>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无事件链时间线" :image-size="80" />
        </section>

        <section class="detail-section">
          <div class="section-title">
            <strong>关联证据</strong>
            <el-tag effect="plain">{{ externalEvidence.length }} 条</el-tag>
          </div>
          <article v-for="item in externalEvidence" :key="`${item.evidenceType}-${item.evidenceId}`" class="evidence-card">
            <div>
              <strong>{{ item.sourceType || 'external_event' }} / {{ item.eventType || '-' }}</strong>
              <span>{{ item.assetIp || item.hostname || '-' }} · {{ item.targetUrl || item.batchId || '-' }}</span>
              <p>{{ relationReasonText(item) }}</p>
            </div>
            <el-button text @click="goEvidence(item)">查看证据</el-button>
          </article>
          <el-empty v-if="!externalEvidence.length" description="暂无多源证据" :image-size="80" />
        </section>

        <section class="detail-grid">
          <div class="detail-section">
            <div class="section-title">
              <strong>关联告警</strong>
              <el-tag effect="plain">{{ alertEvidence.length }} 条</el-tag>
            </div>
            <article v-for="item in alertEvidence" :key="item.id" class="compact-card">
              <SeverityBadge :severity="item.severity || current.severity" />
              <div>
                <strong>{{ item.ruleId || `告警 #${item.evidenceId}` }}</strong>
                <span>{{ relationReasonText(item) }}</span>
              </div>
              <el-button text @click="goEvidence(item)">查看</el-button>
            </article>
            <el-empty v-if="!alertEvidence.length" description="暂无关联告警" :image-size="70" />
          </div>

          <div class="detail-section">
            <div class="section-title">
              <strong>关联漏洞</strong>
              <el-tag effect="plain">{{ vulnerabilityEvidence.length }} 条</el-tag>
            </div>
            <article v-for="item in vulnerabilityEvidence" :key="item.id" class="compact-card">
              <SeverityBadge :severity="item.severity || current.severity" />
              <div>
                <strong>{{ item.ruleId || `漏洞 #${item.evidenceId}` }}</strong>
                <span>{{ relationReasonText(item) }}</span>
              </div>
              <el-button text @click="goEvidence(item)">查看</el-button>
            </article>
            <el-empty v-if="!vulnerabilityEvidence.length" description="暂无关联漏洞" :image-size="70" />
          </div>
        </section>

        <section class="detail-section">
          <div class="section-title">
            <strong>关联工单</strong>
            <el-tag effect="plain">{{ current.ticketId ? '已转工单' : '未转工单' }}</el-tag>
          </div>
          <article class="ticket-card">
            <div>
              <strong>{{ current.ticketId ? `工单 #${current.ticketId}` : '尚未生成处置工单' }}</strong>
              <span>{{ current.ticketId ? '可以进入工单中心查看时间线和处置动作。' : '建议将高优先级事件簇转为工单，进入处置闭环。' }}</span>
            </div>
            <el-button v-if="current.ticketId" type="primary" @click="goTicket(current)">查看工单</el-button>
          </article>
        </section>

        <section class="detail-section closure-readiness-panel">
          <div class="section-title">
            <div>
              <strong>闭环检查</strong>
              <span>关闭前必须完成证据复核、工单处置和结论记录。</span>
            </div>
            <el-tag :type="closureReadiness?.canClose ? 'success' : 'warning'" effect="plain">{{ closureReadiness?.canClose ? '可关闭' : '待完成' }}</el-tag>
          </div>
          <div v-if="closureReadiness" class="closure-check-list">
            <article :class="{ passed: closureReadiness.evidenceCount > 0 }">
              <strong>证据链已复核</strong><span>{{ closureReadiness.evidenceCount }} 条可追溯证据</span>
            </article>
            <article :class="{ passed: !closureReadiness.ticketRequired || closureReadiness.ticketClosed }">
              <strong>{{ closureReadiness.ticketRequired ? '处置工单已完成' : '工单要求' }}</strong>
              <span>{{ closureReadiness.ticketRequired ? (closureReadiness.ticketClosed ? '工单已关闭或归档' : `当前工单状态：${closureReadiness.ticketStatus || '未创建'}`) : '当前等级可直接完成复核' }}</span>
            </article>
            <article class="closure-conclusion-card">
              <strong>复核结论</strong><span>关闭时必须填写处理结果、修复依据或接受风险的原因。</span>
            </article>
          </div>
          <el-alert v-if="closureReadiness?.blockers.length" type="warning" :closable="false" :title="closureReadiness.blockers.join('；')" />
        </section>

        <section v-if="appStore.showRawEvidence" class="detail-section">
          <div class="section-title">
            <strong>专家诊断</strong>
            <el-tag effect="plain">raw</el-tag>
          </div>
          <pre class="diagnostic-box">{{ diagnosticText }}</pre>
        </section>

        <div class="drawer-actions">
          <el-button v-if="current.status !== 'closed'" @click="startInvestigation">开始研判</el-button>
          <el-button v-if="current.ticketId" type="primary" @click="goTicket(current)">查看工单</el-button>
          <el-button v-else type="warning" @click="createTicket(current)">转为处置工单</el-button>
          <el-button type="danger" :disabled="current.status === 'closed'" @click="closeCurrent">关闭事件簇</el-button>
          <el-button @click="drawer = false">返回列表</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import SecurityDispositionGuide from '@/components/security/SecurityDispositionGuide.vue'
import SeverityBadge from '@/components/security/SeverityBadge.vue'
import StatusBadge from '@/components/security/StatusBadge.vue'
import {
  closeIncident,
  correlateIncidents,
  incidentDetail,
  incidentClosureReadiness,
  investigateIncident,
  listIncidents,
  ticketIncident,
  type IncidentClusterItem,
  type IncidentClosureReadiness,
  type IncidentEvidenceItem,
} from '@/api/soc'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', severity: '', status: '' })
const rows = ref<IncidentClusterItem[]>([])
const total = ref(0)
const loading = ref(false)
const correlating = ref(false)
const error = ref('')
const detailError = ref('')
const drawer = ref(false)
const closureReadiness = ref<IncidentClosureReadiness>()
const diagnosticsVisible = ref(false)
const current = ref<IncidentClusterItem>()

const evidenceTimeline = computed(() => {
  return [...(current.value?.evidence || [])].sort((a, b) => String(a.eventTime || '').localeCompare(String(b.eventTime || '')))
})
const externalEvidence = computed(() => evidenceTimeline.value.filter((item) => item.evidenceType === 'external_event' || item.evidenceType === 'fim' || item.evidenceType === 'baseline'))
const alertEvidence = computed(() => evidenceTimeline.value.filter((item) => item.evidenceType === 'alert'))
const vulnerabilityEvidence = computed(() => evidenceTimeline.value.filter((item) => item.evidenceType === 'vulnerability'))
const diagnosticText = computed(() => JSON.stringify({
  route: route.fullPath,
  query,
  listError: error.value || undefined,
  detailError: detailError.value || undefined,
  api: ['/soc/incidents', '/soc/incidents/{id}', '/soc/incidents/correlate'],
}, null, 2))

watch(
  () => [route.query.keyword, route.query.openIncidentId],
  ([keyword]) => {
    query.keyword = typeof keyword === 'string' ? keyword : ''
    query.pageNum = 1
    void load()
  },
  { immediate: true },
)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await listIncidents(query)
    rows.value = data.data.records
    total.value = data.data.total
    void openRouteIncidentIfNeeded()
  } catch {
    error.value = '事件簇加载失败'
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function runCorrelation() {
  correlating.value = true
  try {
    const { data } = await correlateIncidents()
    ElMessage.success(`关联完成：刷新 ${data.data.upsertedClusters} 个事件簇，写入 ${data.data.evidenceWritten} 条证据`)
    await load()
  } catch {
    ElMessage.error('事件关联执行失败，请检查权限、规则配置或后端服务。')
  } finally {
    correlating.value = false
  }
}

async function open(row: IncidentClusterItem) {
  drawer.value = true
  current.value = row
  closureReadiness.value = undefined
  detailError.value = ''
  try {
    const { data } = await incidentDetail(row.id)
    current.value = data.data
    await loadClosureReadiness(row.id)
  } catch {
    detailError.value = '事件簇详情加载失败'
  }
}

async function openRouteIncidentIfNeeded() {
  const openIncidentId = typeof route.query.openIncidentId === 'string' ? Number(route.query.openIncidentId) : 0
  if (!openIncidentId || current.value?.id === openIncidentId) return
  const matched = rows.value.find((item) => item.id === openIncidentId)
  if (matched) {
    await open(matched)
    return
  }
  drawer.value = true
  detailError.value = ''
  try {
    const { data } = await incidentDetail(openIncidentId)
    current.value = data.data
    await loadClosureReadiness(openIncidentId)
  } catch {
    detailError.value = '事件簇详情加载失败'
  }
}

async function createTicket(row: IncidentClusterItem) {
  try {
    await ElMessageBox.confirm('将该事件簇转为处置工单，继续？', '转工单确认', { type: 'warning' })
    await ticketIncident(row.id, '由安全事件簇转为处置工单')
    ElMessage.success('已转为处置工单')
    await load()
    if (current.value?.id === row.id) {
      current.value = (await incidentDetail(row.id)).data.data
      await loadClosureReadiness(row.id)
    }
  } catch (err) {
    if (err !== 'cancel') ElMessage.error('转工单失败，请检查权限或后端服务。')
  }
}

async function startInvestigation() {
  if (!current.value) return
  try {
    const { value } = await ElMessageBox.prompt('记录本次研判的范围或下一步计划。', '开始事件研判', {
      inputPlaceholder: '例如：先确认来源 IP、受影响资产和关联告警',
      inputValidator: (text) => text.trim().length >= 6 || '请填写至少 6 个字符的研判计划',
      confirmButtonText: '开始研判',
      cancelButtonText: '取消',
    })
    current.value = (await investigateIncident(current.value.id, value)).data.data
    ElMessage.success('事件簇已进入研判阶段')
    await loadClosureReadiness(current.value.id)
    await load()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error('开始研判失败，请检查权限或后端服务。')
  }
}

async function loadClosureReadiness(id: number) {
  try {
    closureReadiness.value = (await incidentClosureReadiness(id)).data.data
  } catch {
    closureReadiness.value = undefined
  }
}

async function closeCurrent() {
  if (!current.value) return
  try {
    const { value } = await ElMessageBox.prompt('请填写已采取的措施、验证结果或接受风险理由。该结论会写入工单时间线。', '提交闭环结论', {
      inputType: 'textarea',
      inputPlaceholder: '至少 12 个字符，例如：已确认访问由授权运维产生，工单任务与复核证据已完成。',
      inputValidator: (text) => text.trim().length >= 12 || '请填写至少 12 个字符的闭环结论',
      confirmButtonText: '确认关闭',
      cancelButtonText: '返回处理',
      type: 'warning',
    })
    await closeIncident(current.value.id, value)
    ElMessage.success('事件簇已关闭')
    await load()
    current.value = (await incidentDetail(current.value.id)).data.data
    await loadClosureReadiness(current.value.id)
  } catch (err) {
    if (err !== 'cancel') ElMessage.error('关闭事件簇失败，请检查权限或后端服务。')
  }
}

function resetFilters() {
  query.keyword = ''
  query.severity = ''
  query.status = ''
  query.pageNum = 1
  void router.replace({ path: '/soc/incidents' })
  void load()
}

function goTicket(row: IncidentClusterItem) {
  if (!row.ticketId) return
  router.push({ path: '/soc/tickets', query: { keyword: String(row.ticketId), openTicketId: row.ticketId } })
}

function goEvidence(item: IncidentEvidenceItem) {
  if (item.evidenceType === 'alert') {
    router.push({ path: '/soc/alerts', query: { openAlertId: item.evidenceId, keyword: item.batchId || item.assetIp || item.ruleId } })
    return
  }
  if (item.evidenceType === 'vulnerability') {
    router.push({ path: '/soc/vulnerabilities', query: { keyword: item.evidenceUid || item.ruleId || item.assetIp } })
    return
  }
  router.push({ path: '/soc/external-events', query: { openEventUid: item.evidenceUid, keyword: item.batchId || item.assetIp || item.ruleId } })
}

function assetLabel(row: IncidentClusterItem) {
  const host = row.hostname || row.primaryHostname || '-'
  const ip = row.assetIp || row.primaryAssetIp || '-'
  return `${host} / ${ip}`
}

function sourceLabel(row: IncidentClusterItem) {
  return row.sourceSummary || row.sourceTypes || '-'
}

function evidenceCount(row: IncidentClusterItem) {
  return row.evidenceCount ?? ((row.eventCount || 0) + (row.alertCount || 0) + (row.vulnerabilityCount || 0))
}

function actionLabel(row: IncidentClusterItem) {
  if (row.recommendation) return row.recommendation
  if (row.ticketId) return '查看处置工单'
  if (row.status === 'closed') return '已关闭，保留证据链'
  if (['critical', 'high'].includes(String(row.severity || '').toLowerCase())) return '建议转为处置工单'
  return '继续观察或关闭事件簇'
}

function relationReasonText(item: IncidentEvidenceItem) {
  const reason = item.relationReason || '命中关联规则'
  if (appStore.viewMode !== 'simple' || reason.length <= 72) return reason
  return `${reason.slice(0, 72)}...`
}

function evidenceTypeLabel(type: IncidentEvidenceItem['evidenceType']) {
  const labels: Record<IncidentEvidenceItem['evidenceType'], string> = {
    external_event: '多源事件',
    alert: '告警',
    vulnerability: '漏洞',
    ticket: '工单',
    fim: '文件变更',
    baseline: '基线',
  }
  return labels[type] || type
}

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}
</script>

<style scoped>
.hero-actions,
.recoverable-actions,
.drawer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.recoverable-alert {
  margin: 16px 0;
}

.recoverable-actions {
  margin-top: 10px;
}

.diagnostic-box {
  margin: 12px 0 0;
  padding: 12px;
  max-height: 180px;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: #fff;
  color: var(--el-text-color-regular);
  font-size: 12px;
  white-space: pre-wrap;
}

.title-cell,
.timeline-card div,
.evidence-card div,
.compact-card div,
.ticket-card div {
  display: grid;
  gap: 5px;
}

.title-cell span,
.timeline-card span,
.timeline-card p,
.evidence-card span,
.evidence-card p,
.compact-card span,
.ticket-card span {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.detail-section {
  display: grid;
  gap: 12px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.section-title,
.timeline-card,
.evidence-card,
.compact-card,
.ticket-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.timeline-card,
.evidence-card,
.compact-card,
.ticket-card {
  padding: 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: #fff;
}

.compact-card {
  justify-content: flex-start;
}

.closure-readiness-panel {
  display: grid;
  gap: 12px;
}

.closure-readiness-panel .section-title > div {
  display: grid;
  gap: 4px;
}

.closure-readiness-panel .section-title span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.closure-check-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.closure-check-list article {
  display: grid;
  gap: 5px;
  padding: 11px;
  border: 1px solid rgba(218, 139, 48, 0.38);
  border-radius: 8px;
  background: rgba(255, 250, 244, 0.7);
}

.closure-check-list article.passed {
  border-color: rgba(37, 161, 100, 0.42);
  background: rgba(244, 252, 247, 0.82);
}

.closure-check-list strong { color: var(--el-text-color-primary); }
.closure-check-list span { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.5; }

@media (max-width: 900px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
  .closure-check-list {
    grid-template-columns: 1fr;
  }
}
</style>
