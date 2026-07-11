<template>
  <div class="page-shell">
    <section class="soc-page-hero">
      <div>
        <span class="soc-page-kicker">ALERT OPERATIONS</span>
        <h1>告警处置</h1>
        <p>这个页面帮你查看待处理告警，并完成确认、误报、关闭或转工单。</p>
      </div>
      <div class="soc-page-tags">
        <el-tag>Wazuh</el-tag>
        <el-tag>Suricata</el-tag>
        <el-tag>Zeek</el-tag>
        <el-tag>SOAR Ticket</el-tag>
      </div>
    </section>

    <section class="soc-panel panel-pad">
      <div class="soc-filter-bar">
        <el-input v-model="query.keyword" clearable placeholder="搜索规则描述、资产、来源 IP" @keyup.enter="load" />
        <el-select v-model="query.sourceType" clearable placeholder="来源">
          <el-option v-for="source in sourceOptions" :key="source.value" :label="source.label" :value="source.value" />
        </el-select>
        <el-select v-model="query.severity" clearable placeholder="等级">
          <el-option label="严重" value="critical" /><el-option label="高危" value="high" /><el-option label="中危" value="medium" /><el-option label="低危" value="low" />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="状态">
          <el-option label="新告警" value="new" /><el-option label="已确认" value="acknowledged" /><el-option label="已转工单" value="ticketed" /><el-option label="已关闭" value="closed" />
        </el-select>
        <el-button @click="load">查询</el-button>
      </div>
    </section>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false">
      <template #default>
        <el-button size="small" @click="load">重试</el-button>
      </template>
    </el-alert>
    <section class="table-panel">
      <div class="batch-toolbar">
        <span>已选 {{ selected.length }} 条</span>
        <el-button :disabled="!selected.length" @click="batchAction('acknowledge')">批量确认</el-button>
        <el-button :disabled="!selected.length" @click="batchAction('ignore')">批量忽略</el-button>
        <el-button :disabled="!selected.length" type="danger" @click="batchAction('close')">批量关闭</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" empty-text="暂无告警数据" @selection-change="selected = $event" @row-click="open">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="alertUid" label="告警 ID" min-width="150" />
        <el-table-column label="等级" width="86"><template #default="{ row }"><SeverityBadge :severity="row.severity" /></template></el-table-column>
        <el-table-column label="来源" width="86"><template #default="{ row }"><DataSourceBadge :source="row.sourceType" /></template></el-table-column>
        <el-table-column prop="ruleId" label="规则" width="90" />
        <el-table-column prop="ruleDescription" label="规则描述" min-width="260" show-overflow-tooltip />
        <el-table-column prop="assetName" label="资产" min-width="130" />
        <el-table-column prop="sourceIp" label="来源 IP" width="140" />
        <el-table-column label="降噪" width="108">
          <template #default="{ row }">
            <StatusBadge v-if="row.whitelistHit" status="whitelisted" />
            <span v-else-if="row.repeatCount && row.repeatCount > 1" class="repeat-text">重复 {{ row.repeatCount }}</span>
            <span v-else class="muted-text">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100"><template #default="{ row }"><StatusBadge :status="row.noiseStatus || row.status" /></template></el-table-column>
        <el-table-column prop="eventTime" label="时间" width="180" />
      </el-table>
      <div class="pagination-row">
        <span>已选 {{ selected.length }} 条</span>
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" layout="total, sizes, prev, pager, next" :total="total" @change="load" />
      </div>
    </section>
    <el-drawer v-model="drawer" title="告警详情" size="520px">
      <div v-if="current" class="drawer-stack">
        <SecurityDispositionGuide
          category="alert"
          :subject="current.ruleName || current.eventType || current.ruleDescription"
          :source="current.sourceType"
          :severity="current.severity"
          :status="current.noiseStatus || current.status"
          :asset="`${current.assetName || '-'}（${current.assetIp || '-'}）`"
          :reason="current.evidenceSummary || current.ruleDescription"
          :recommendation="playbookSuggestions[0]?.matchReason"
        />
        <div class="soc-drawer-grid">
          <span>告警 ID</span><strong>{{ current.alertUid }}</strong>
          <span>等级</span><strong><SeverityBadge :severity="current.severity" /></strong>
          <span>数据来源</span><strong><DataSourceBadge :source="current.sourceType" /></strong>
          <span>事件类型</span><strong>{{ current.eventType || '-' }}</strong>
          <span>规则</span><strong>{{ current.ruleId }} / {{ current.ruleDescription }}</strong>
          <span>规则名称</span><strong>{{ current.ruleName || current.ruleDescription || '-' }}</strong>
          <span>资产</span><strong>{{ current.assetName }}（{{ current.assetIp }}）</strong>
          <span>来源 IP</span><strong>{{ current.sourceIp }}</strong>
          <span>目标 URL</span><strong>{{ current.targetUrl || '-' }}</strong>
          <span>HTTP</span><strong>{{ current.httpMethod || '-' }} / {{ current.httpStatus || '-' }}</strong>
          <span>处置动作</span><strong>{{ current.action || '-' }}</strong>
          <span>请求 ID</span><strong>{{ current.requestId || '-' }}</strong>
          <span>引擎</span><strong>{{ current.engine || '-' }}</strong>
          <span>Demo Case</span><strong>{{ current.demoCaseId || '-' }}</strong>
          <span>Batch ID</span><strong>{{ current.batchId || '-' }}</strong>
          <span>证据摘要</span><strong>{{ current.evidenceSummary || '-' }}</strong>
          <span>降噪状态</span><strong><StatusBadge :status="current.noiseStatus || current.status" /></strong>
          <span>白名单</span><strong>{{ current.whitelistRuleName || '未命中' }}</strong>
          <span>重复次数</span><strong>{{ current.repeatCount || 1 }}</strong>
          <span>战术</span><strong>{{ current.tactic }}</strong>
          <span>原始索引</span><strong>{{ current.rawRef }}</strong>
        </div>
        <el-input v-model="remark" type="textarea" :rows="3" placeholder="填写处置说明" />
        <section class="playbook-panel">
          <div class="playbook-panel-head">
            <div>
              <strong>推荐处置剧本</strong>
              <span>只生成处置任务和时间线，不执行自动修复。</span>
            </div>
            <el-tag effect="plain">{{ playbookSuggestions.length }} 个匹配</el-tag>
          </div>
          <div v-if="playbookSuggestions.length" class="playbook-list">
            <article v-for="suggestion in playbookSuggestions" :key="suggestion.playbook.id" class="playbook-card">
              <div>
                <strong>{{ suggestion.playbook.playbookName }}</strong>
                <span>{{ suggestion.matchReason }}</span>
                <em>{{ suggestion.steps.length }} 个处置步骤</em>
              </div>
              <el-button
                type="primary"
                :loading="applyingPlaybookId === suggestion.playbook.id"
                @click="applyPlaybook(suggestion.playbook.id)"
              >
                应用剧本
              </el-button>
            </article>
          </div>
          <el-empty v-else-if="!playbookLoading" description="暂无匹配剧本" :image-size="72" />
        </section>
        <section class="playbook-panel">
          <div class="playbook-panel-head">
            <div>
              <strong>关联事件簇</strong>
              <span>展示该告警已关联的多源证据链。</span>
            </div>
            <el-tag effect="plain">{{ relatedIncidents.length }} 个事件簇</el-tag>
          </div>
          <div v-if="relatedIncidents.length" class="playbook-list">
            <article v-for="incident in relatedIncidents" :key="incident.id" class="playbook-card">
              <div>
                <strong>{{ incident.clusterNo }} · {{ incident.title }}</strong>
                <span>{{ incident.summary || incident.correlationKey }}</span>
                <em>{{ incident.eventCount }} 事件 / {{ incident.alertCount }} 告警 / {{ incident.vulnerabilityCount }} 漏洞</em>
              </div>
              <el-button @click="router.push({ path: '/soc/incidents', query: { keyword: incident.clusterNo } })">查看</el-button>
            </article>
          </div>
          <el-empty v-else description="暂无关联事件簇，可在安全事件簇页面执行关联" :image-size="72" />
        </section>
        <div class="drawer-actions">
          <el-button @click="doAction('acknowledge')">确认</el-button>
          <el-button @click="doAction('false-positive')">误报</el-button>
          <el-button @click="doAction('ignore')">忽略</el-button>
          <el-button type="warning" @click="doAction('ticket')">转工单</el-button>
          <el-button type="danger" @click="doAction('close')">关闭</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import DataSourceBadge from '@/components/security/DataSourceBadge.vue'
import SecurityDispositionGuide from '@/components/security/SecurityDispositionGuide.vue'
import SeverityBadge from '@/components/security/SeverityBadge.vue'
import StatusBadge from '@/components/security/StatusBadge.vue'
import {
  alertAction,
  alertDetail,
  alertPlaybookSuggestions,
  alertRelatedIncidents,
  applyAlertPlaybook,
  listAlerts,
  type AlertItem,
  type IncidentClusterItem,
  type PlaybookSuggestion,
} from '@/api/soc'

const route = useRoute()
const router = useRouter()
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', sourceType: '', severity: '', status: '' })
const rows = ref<AlertItem[]>([])
const total = ref(0)
const selected = ref<AlertItem[]>([])
const drawer = ref(false)
const current = ref<AlertItem>()
const remark = ref('已按 SOC 流程处置')
const loading = ref(false)
const error = ref('')
const playbookLoading = ref(false)
const applyingPlaybookId = ref<number>()
const playbookSuggestions = ref<PlaybookSuggestion[]>([])
const relatedIncidents = ref<IncidentClusterItem[]>([])

const sourceOptions = [
  { label: 'Wazuh', value: 'mock' },
  { label: 'Zeek', value: 'zeek' },
  { label: 'Suricata', value: 'suricata' },
  { label: 'MISP', value: 'misp' },
  { label: 'Trivy', value: 'trivy' },
  { label: 'ZAP', value: 'zap' },
  { label: 'WAF', value: 'waf' },
  { label: '用户端演练', value: 'osquery' },
  { label: '本地靶场', value: 'cyber-range' },
]

watch(
  () => [route.query.sourceType, route.query.adapter, route.query.keyword, route.query.assetIp, route.query.openAlertId, route.query.openAlertUid],
  () => {
    query.sourceType = routeSourceType()
    query.keyword = routeKeyword()
    query.pageNum = 1
    void load()
  },
  { immediate: true }
)

function routeSourceType() {
  if (route.query.adapter === 'wazuh') return 'mock'
  return typeof route.query.sourceType === 'string' ? route.query.sourceType : ''
}

function routeKeyword() {
  if (typeof route.query.openAlertId === 'string' && typeof route.query.assetIp === 'string') return route.query.assetIp
  if (typeof route.query.keyword === 'string') return route.query.keyword
  if (typeof route.query.assetIp === 'string') return route.query.assetIp
  return ''
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await listAlerts(query)
    const records = res.data.data.records
    rows.value = records
    total.value = res.data.data.total
    openRouteAlertIfNeeded()
  } catch {
    error.value = '告警列表加载失败，请检查网络、权限或后端服务状态。'
  } finally {
    loading.value = false
  }
}
async function open(row: AlertItem) {
  current.value = row
  drawer.value = true
  playbookSuggestions.value = []
  relatedIncidents.value = []
  try {
    const res = await alertDetail(row.id)
    current.value = res.data.data
    await loadPlaybookSuggestions(row.id)
    await loadRelatedIncidents(row.id)
  } catch {
    ElMessage.warning('告警详情加载失败，已显示列表摘要。')
  }
}

async function loadPlaybookSuggestions(alertId: number) {
  playbookLoading.value = true
  try {
    const res = await alertPlaybookSuggestions(alertId)
    playbookSuggestions.value = res.data.data
  } catch {
    playbookSuggestions.value = []
  } finally {
    playbookLoading.value = false
  }
}

async function loadRelatedIncidents(alertId: number) {
  try {
    const res = await alertRelatedIncidents(alertId)
    relatedIncidents.value = res.data.data
  } catch {
    relatedIncidents.value = []
  }
}

async function applyPlaybook(playbookId?: number) {
  if (!current.value || !playbookId) return
  applyingPlaybookId.value = playbookId
  try {
    const res = await applyAlertPlaybook(current.value.id, playbookId, remark.value || '告警详情应用处置剧本')
    ElMessage.success(res.data.data.message)
    drawer.value = false
    await load()
  } finally {
    applyingPlaybookId.value = undefined
  }
}

function openRouteAlertIfNeeded() {
  const openAlertId = typeof route.query.openAlertId === 'string' ? Number(route.query.openAlertId) : 0
  const openAlertUid = typeof route.query.openAlertUid === 'string' ? route.query.openAlertUid : ''
  if (!openAlertId && !openAlertUid) return
  const matched = rows.value.find((item) => (openAlertId && item.id === openAlertId) || (openAlertUid && item.alertUid === openAlertUid))
  if (matched) open(matched)
}

async function doAction(action: 'acknowledge' | 'false-positive' | 'ignore' | 'close' | 'ticket') {
  if (!current.value) return
  await alertAction(current.value.id, action, remark.value)
  ElMessage.success('处置动作已记录')
  drawer.value = false
  await load()
}

async function batchAction(action: 'acknowledge' | 'ignore' | 'close') {
  if (!selected.value.length) return
  await ElMessageBox.confirm(`确认对 ${selected.value.length} 条告警执行批量操作？`, '批量处置', { type: action === 'close' ? 'warning' : 'info' })
  let succeeded = 0
  for (const row of selected.value) {
    try {
      await alertAction(row.id, action, remark.value)
      succeeded += 1
    } catch {
      // The request interceptor has already surfaced the concrete API error.
    }
  }
  if (succeeded) ElMessage.success(`批量处置已完成 ${succeeded} 条`)
  selected.value = []
  await load()
}
</script>

<style scoped>
.panel-pad { padding: 14px; }
.batch-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--soc-border);
}
.pagination-row {
  align-items: center;
  justify-content: space-between;
}
.drawer-stack {
  display: grid;
  gap: 16px;
}
.drawer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.playbook-panel {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid rgba(179, 173, 163, 0.46);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.72);
}

.playbook-panel-head,
.playbook-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.playbook-panel-head div,
.playbook-card div {
  display: grid;
  gap: 4px;
}

.playbook-panel-head span,
.playbook-card span,
.playbook-card em {
  color: var(--soc-text-muted);
  font-size: 12px;
  font-style: normal;
  line-height: 1.5;
}

.playbook-list {
  display: grid;
  gap: 10px;
}

.playbook-card {
  padding: 12px;
  border: 1px solid rgba(179, 173, 163, 0.36);
  border-radius: 8px;
  background: #fff;
}

.drawer-actions :deep(.el-button--warning) {
  border-color: #d4934a;
  background: #d4934a;
  color: #fff;
}

.drawer-actions :deep(.el-button--danger) {
  border-color: #df4f4f;
  background: #df4f4f;
  color: #fff;
}

.drawer-actions :deep(.el-button--warning:hover),
.drawer-actions :deep(.el-button--danger:hover) {
  filter: brightness(0.96);
}

.repeat-text {
  color: var(--soc-medium);
  font-size: 12px;
}
.muted-text {
  color: var(--soc-text-muted);
}
</style>
