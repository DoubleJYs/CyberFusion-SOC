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

    <section class="soc-page-hero daily-hero">
      <div>
        <span class="soc-page-kicker">DAILY RESPONSE</span>
        <h1>每日处理</h1>
        <p>按事件簇、漏洞、工单和员工待办生成当前优先处理队列。</p>
      </div>
      <div class="daily-hero-actions">
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button type="primary" :icon="DataAnalysis" @click="router.push('/soc/dashboard')">安全运营工作台</el-button>
      </div>
    </section>

    <section class="daily-summary-grid">
      <article>
        <span>当前建议</span>
        <strong>{{ rows.length }}</strong>
        <small>{{ sourceCount }} 类来源</small>
      </article>
      <article>
        <span>高优先级</span>
        <strong>{{ urgentCount }}</strong>
        <small>严重 / 高</small>
      </article>
      <article>
        <span>最高评分</span>
        <strong>{{ highestScore }}</strong>
        <small>推荐排序分</small>
      </article>
      <article>
        <span>今日待办</span>
        <strong>{{ todoRows.length }}</strong>
        <small>事件簇 / 漏洞 / 工单 / 员工待办</small>
      </article>
    </section>

    <section class="soc-panel daily-filter-panel">
      <el-input
        v-model="keyword"
        clearable
        :prefix-icon="Search"
        placeholder="搜索标题、资产、建议动作..."
      />
      <el-select v-model="priorityFilter" placeholder="优先级" clearable>
        <el-option label="严重" value="critical" />
        <el-option label="高" value="high" />
        <el-option label="中" value="medium" />
        <el-option label="低" value="low" />
      </el-select>
      <el-select v-model="sourceFilter" placeholder="来源" clearable>
        <el-option
          v-for="source in sourceOptions"
          :key="source.value"
          :label="source.label"
          :value="source.value"
        />
      </el-select>
      <el-select v-model="limit" class="limit-select" @change="load">
        <el-option label="Top 10" :value="10" />
        <el-option label="Top 20" :value="20" />
        <el-option label="Top 50" :value="50" />
      </el-select>
    </section>

    <section v-loading="loading" class="soc-panel recommendation-worklist">
      <div class="panel-title">
        <div>
          <strong>今日优先处理建议</strong>
          <span>{{ filteredRows.length }} 条匹配 / {{ rows.length }} 条当前建议</span>
        </div>
      </div>

      <div v-if="filteredRows.length" class="recommendation-list">
        <button
          v-for="item in filteredRows"
          :key="item.key"
          :class="['recommendation-row', `recommendation-row--${item.priority}`]"
          type="button"
          @click="openRecommendation(item)"
        >
          <div class="recommendation-main">
            <div class="recommendation-heading">
              <el-tag :type="priorityTag(item.priority)" effect="plain" size="small">{{ priorityText(item.priority) }}</el-tag>
              <strong>{{ item.title }}</strong>
              <b>{{ item.priorityScore }}</b>
            </div>
            <div class="recommendation-meta">
              <span>{{ recommendationTypeText(item.relatedBizType) }}</span>
              <span v-if="item.assetName || item.assetIp">资产 {{ item.assetName || item.assetIp }}</span>
              <span v-if="item.status">状态 {{ recommendationStatusText(item.status) }}</span>
              <span>{{ item.assigneeType === 'employee' ? '员工' : '分析员' }}</span>
            </div>
            <p>{{ item.reason }}</p>
          </div>
          <div class="recommendation-action">
            <span>建议动作</span>
            <strong>{{ item.recommendedAction }}</strong>
            <div class="recommendation-buttons">
              <el-button size="small" @click.stop="recordView(item)">查看处理记录</el-button>
              <el-button size="small" type="primary" @click.stop="openRecommendation(item)">开始处理</el-button>
            </div>
          </div>
        </button>
      </div>
      <el-empty v-else description="暂无匹配建议" :image-size="76" />
    </section>

    <section v-loading="todoLoading" class="soc-panel daily-todo-worklist">
      <div class="panel-title todo-panel-head">
        <div>
          <strong>今日待办</strong>
          <span>所有尚未闭环的事件簇、漏洞、工单和员工待办，按当前权限范围实时汇总。</span>
        </div>
        <span>{{ filteredTodoRows.length }} 条匹配 / {{ todoRows.length }} 条待办</span>
      </div>
      <el-radio-group v-model="todoTypeFilter" class="todo-type-filter">
        <el-radio-button label="all">全部</el-radio-button>
        <el-radio-button label="incident">事件簇</el-radio-button>
        <el-radio-button label="vulnerability">漏洞</el-radio-button>
        <el-radio-button label="ticket">工单</el-radio-button>
        <el-radio-button label="employee_task">员工待办</el-radio-button>
      </el-radio-group>
      <el-table :data="pagedTodoRows" empty-text="当前没有待办" size="small" class="daily-todo-table">
        <el-table-column label="类型" width="104">
          <template #default="{ row }"><el-tag size="small" effect="plain">{{ todoTypeText(row.type) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="优先级" width="98">
          <template #default="{ row }"><el-tag size="small" :type="priorityTag(row.priority)" effect="plain">{{ priorityText(row.priority) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="待办内容" min-width="270" show-overflow-tooltip>
          <template #default="{ row }"><strong>{{ row.title }}</strong><small>{{ row.detail }}</small></template>
        </el-table-column>
        <el-table-column label="资产" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.asset || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">{{ recommendationStatusText(row.status) }}</template>
        </el-table-column>
        <el-table-column label="时间 / 截止" min-width="156">
          <template #default="{ row }">{{ row.time || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="96">
          <template #default="{ row }"><el-button link type="primary" @click="openTodo(row)">开始处理</el-button></template>
        </el-table-column>
      </el-table>
      <div class="todo-pagination">
        <span>共 {{ filteredTodoRows.length }} 条</span>
        <el-pagination v-model:current-page="todoPage" :page-size="todoPageSize" layout="prev, pager, next" :total="filteredTodoRows.length" />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { DataAnalysis, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listAlerts, listClientTasks, listIncidents, listTickets, listVulnerabilities, recordRecommendationAction, topRecommendations } from '@/api/soc'
import type { AlertItem, IncidentClusterItem, RecommendationItem, TicketItem, TicketTaskItem, VulnerabilityItem } from '@/api/soc'

const router = useRouter()
const rows = ref<RecommendationItem[]>([])
const loading = ref(false)
const todoLoading = ref(false)
const error = ref('')
const keyword = ref('')
const priorityFilter = ref('')
const sourceFilter = ref('')
const limit = ref(20)
const todoRows = ref<DailyTodoItem[]>([])
const todoTypeFilter = ref('all')
const todoPage = ref(1)
const todoPageSize = 12

interface DailyTodoItem {
  id: number
  type: 'incident' | 'vulnerability' | 'ticket' | 'employee_task'
  priority: string
  title: string
  detail: string
  asset?: string
  status: string
  time?: string
}

const sourceCount = computed(() => new Set(rows.value.map((item) => item.relatedBizType).filter(Boolean)).size)
const urgentCount = computed(() => rows.value.filter((item) => item.priority === 'critical' || item.priority === 'high').length)
const highestScore = computed(() => rows.value.length ? Math.max(...rows.value.map((item) => item.priorityScore || 0)) : 0)
const filteredTodoRows = computed(() => todoRows.value.filter((item) => todoTypeFilter.value === 'all' || item.type === todoTypeFilter.value))
const pagedTodoRows = computed(() => {
  const start = (todoPage.value - 1) * todoPageSize
  return filteredTodoRows.value.slice(start, start + todoPageSize)
})

const sourceOptions = computed(() => {
  const types = Array.from(new Set(rows.value.map((item) => item.relatedBizType).filter(Boolean)))
  return types.map((value) => ({ value, label: recommendationTypeText(value) }))
})

const filteredRows = computed(() => {
  const term = keyword.value.trim().toLowerCase()
  return rows.value.filter((item) => {
    if (priorityFilter.value && item.priority !== priorityFilter.value) return false
    if (sourceFilter.value && item.relatedBizType !== sourceFilter.value) return false
    if (!term) return true
    return [
      item.title,
      item.reason,
      item.recommendedAction,
      item.assetName,
      item.assetIp,
      recommendationTypeText(item.relatedBizType),
      recommendationStatusText(item.status || ''),
    ].some((value) => String(value || '').toLowerCase().includes(term))
  })
})

watch(todoTypeFilter, () => { todoPage.value = 1 })
watch(filteredTodoRows, (items) => {
  const lastPage = Math.max(1, Math.ceil(items.length / todoPageSize))
  if (todoPage.value > lastPage) todoPage.value = lastPage
})

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = (await topRecommendations(limit.value)).data.data
    await loadTodos()
  } catch {
    rows.value = []
    error.value = '每日处理建议加载失败，请检查后端推荐动作接口。'
  } finally {
    loading.value = false
  }
}

async function loadTodos() {
  todoLoading.value = true
  try {
    const results = await Promise.allSettled([
      listIncidents({ pageNum: 1, pageSize: 100 }),
      listVulnerabilities({ pageNum: 1, pageSize: 100 }),
      listTickets({ pageNum: 1, pageSize: 100 }),
      listClientTasks(),
      listAlerts({ pageNum: 1, pageSize: 100 }),
    ])
    const incidents = recordsFrom<IncidentClusterItem>(results[0])
    const vulnerabilities = recordsFrom<VulnerabilityItem>(results[1])
    const tickets = recordsFrom<TicketItem>(results[2])
    const tasks = valuesFrom<TicketTaskItem>(results[3])
    const alertById = new Map(recordsFrom<AlertItem>(results[4]).map((item) => [item.id, item]))
    todoRows.value = [
      ...incidents.filter((item) => !['closed', 'ignored', 'archived'].includes((item.status || '').toLowerCase())).map(toIncidentTodo),
      ...vulnerabilities.filter((item) => !['fixed', 'accepted', 'closed'].includes((item.status || '').toLowerCase())).map(toVulnerabilityTodo),
      ...tickets.filter((item) => !['已关闭', '已归档', 'closed', 'resolved'].includes((item.status || '').toLowerCase())).map(toTicketTodo),
      ...tasks.filter((item) => !['completed', 'confirmed', 'skipped'].includes((item.status || '').toLowerCase())).map((item) => toEmployeeTaskTodo(item, alertById.get(item.alertId || 0))),
    ].sort((left, right) => todoPriority(right.priority) - todoPriority(left.priority))
  } finally {
    todoLoading.value = false
  }
}

function recordsFrom<T>(result: PromiseSettledResult<{ data: { data: { records: T[] } } }>) {
  return result.status === 'fulfilled' ? result.value.data.data.records : []
}

function valuesFrom<T>(result: PromiseSettledResult<{ data: { data: T[] } }>) {
  return result.status === 'fulfilled' ? result.value.data.data : []
}

function toIncidentTodo(item: IncidentClusterItem): DailyTodoItem {
  return { id: item.id, type: 'incident', priority: item.severity, title: item.title || item.clusterNo, detail: item.summary || item.recommendation || '核对事件证据并推动处置。', asset: item.hostname || item.assetIp || item.primaryHostname || item.primaryAssetIp, status: item.status, time: item.lastSeenAt || item.firstSeenAt }
}

function toVulnerabilityTodo(item: VulnerabilityItem): DailyTodoItem {
  return { id: item.id, type: 'vulnerability', priority: item.severity, title: `${item.cveId || '漏洞'} ${item.softwareName || ''}`.trim(), detail: item.fixSuggestion || '核对影响范围并安排修复。', asset: item.assetName || item.assetIp, status: item.status, time: item.detectedAt }
}

function toTicketTodo(item: TicketItem): DailyTodoItem {
  return { id: item.id, type: 'ticket', priority: item.severity, title: item.title || item.ticketNo, detail: item.reviewConclusion || item.resolution || '补充处置进展或完成复核。', status: item.status, time: item.dueAt }
}

function toEmployeeTaskTodo(item: TicketTaskItem, alert?: AlertItem): DailyTodoItem {
  return { id: item.id, type: 'employee_task', priority: alert?.severity || 'medium', title: item.taskName || item.taskKey, detail: item.instruction || item.expectedEvidence || '提交说明或完成本机检查。', asset: alert?.assetName || alert?.assetIp, status: item.status, time: item.updatedAt || item.createdAt }
}

function openTodo(item: DailyTodoItem) {
  if (item.type === 'incident') router.push({ path: '/soc/incidents', query: { openIncidentId: item.id } })
  else if (item.type === 'vulnerability') router.push({ path: '/soc/vulnerabilities', query: { openVulnerabilityId: item.id } })
  else if (item.type === 'ticket') router.push({ path: '/soc/tickets', query: { openTicketId: item.id } })
  else router.push({ path: '/client/tasks', query: { openTaskId: item.id } })
}

function todoTypeText(type: DailyTodoItem['type']) {
  return ({ incident: '事件簇', vulnerability: '漏洞', ticket: '工单', employee_task: '员工待办' })[type]
}

function todoPriority(priority: string) {
  return ({ critical: 4, high: 3, medium: 2, low: 1 } as Record<string, number>)[priority] || 0
}

async function recordView(item: RecommendationItem, silent = false) {
  try {
    await recordRecommendationAction(item.key, {
      actionType: 'view',
      relatedBizType: item.relatedBizType,
      relatedBizId: item.relatedBizId,
      assetIp: item.assetIp,
      assetName: item.assetName,
      note: '每日处理页面查看推荐动作',
    })
    if (!silent) ElMessage.success('已记录查看动作')
  } catch {
    if (!silent) ElMessage.warning('推荐查看记录暂时无法写入')
  }
}

async function openRecommendation(item: RecommendationItem) {
  await recordView(item, true)
  const targetType = item.navigationBizType || item.relatedBizType
  const targetId = item.navigationBizId || item.relatedBizId
  if (targetType === 'incident') {
    router.push({ path: '/soc/incidents', query: { openIncidentId: targetId, fromRecommendation: item.key } })
  } else if (targetType === 'ticket') {
    router.push({ path: '/soc/tickets', query: { openTicketId: targetId, fromRecommendation: item.key } })
  } else if (targetType === 'vulnerability') {
    router.push({ path: '/soc/vulnerabilities', query: { openVulnerabilityId: targetId, fromRecommendation: item.key } })
  } else if (targetType === 'client_checkup' && (item.assetIp || item.assetName)) {
    router.push({ path: '/client/workbench', query: { ip: item.assetIp || item.assetName } })
  } else if (targetType === 'asset' || item.assetId || item.assetIp || item.assetName) {
    const query: Record<string, string | number | undefined> = { fromRecommendation: item.key }
    if (targetType === 'asset' && targetId) query.openAssetId = targetId
    else if (item.assetId) query.openAssetId = item.assetId
    else query.keyword = recommendationKeyword(item)
    router.push({ path: '/soc/assets', query })
  }
}

function recommendationKeyword(item: RecommendationItem) {
  return item.assetIp || item.assetName || normalizedRecommendationTitle(item) || String(item.relatedBizId || '')
}

function normalizedRecommendationTitle(item: RecommendationItem) {
  return String(item.title || '')
    .replace(/^(优先处理事件簇|推动超时工单|跟进工单|完成剧本任务|跟进员工待办|复核员工体检结果|请先完成本机检查)：/, '')
    .trim()
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

function recommendationTypeText(type: string) {
  if (type === 'incident') return '事件簇'
  if (type === 'vulnerability') return '漏洞'
  if (type === 'ticket') return '工单'
  if (type === 'client_task') return '员工待办'
  if (type === 'playbook_task') return '剧本任务'
  if (type === 'client_checkup') return '本机体检'
  if (type === 'risk_factor') return '风险因子'
  return type || '推荐'
}

function recommendationStatusText(status: string) {
  const normalized = status.toLowerCase()
  if (normalized === 'view') return '已查看'
  if (['open', 'pending', 'todo'].includes(normalized)) return '待处理'
  if (['ticketed', 'in_progress', 'processing'].includes(normalized)) return '处理中'
  if (['confirmed', 'completed', 'closed', 'fixed', 'accepted'].includes(normalized)) return '已闭环'
  return status
}
</script>

<style scoped>
.daily-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.daily-hero-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.daily-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}
.daily-summary-grid article {
  min-height: 118px;
  border: 1px solid rgba(190, 183, 171, 0.5);
  border-radius: var(--soc-radius-card);
  background: rgba(255, 255, 255, 0.68);
  padding: 16px;
}
.daily-summary-grid span,
.daily-summary-grid small {
  display: block;
  color: var(--soc-text-muted);
  line-height: 1.5;
}
.daily-summary-grid strong {
  display: block;
  margin: 8px 0;
  color: var(--soc-text);
  font-size: 32px;
  line-height: 1;
}
.daily-filter-panel {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) 140px 160px 120px;
  gap: 12px;
  padding: 14px 16px;
}
.recommendation-worklist {
  padding: 16px;
}
.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.panel-title strong,
.panel-title span {
  display: block;
}
.panel-title span {
  margin-top: 4px;
  color: var(--soc-text-muted);
  font-size: 13px;
}
.recommendation-list {
  display: grid;
  gap: 10px;
}
.recommendation-row {
  --recommendation-accent: var(--soc-medium);
  width: 100%;
  min-width: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(260px, 36%);
  gap: 14px;
  border: 1px solid rgba(190, 183, 171, 0.5);
  border-left: 4px solid var(--recommendation-accent);
  border-radius: var(--soc-radius-card);
  background: rgba(255, 255, 255, 0.64);
  cursor: pointer;
  padding: 12px;
  text-align: left;
}
.recommendation-row:hover {
  border-color: rgba(219, 126, 42, 0.48);
  background: rgba(255, 250, 243, 0.78);
}
.recommendation-row--critical {
  --recommendation-accent: #ef4444;
}
.recommendation-row--high {
  --recommendation-accent: var(--soc-high);
}
.recommendation-row--medium {
  --recommendation-accent: var(--soc-medium);
}
.recommendation-row--low {
  --recommendation-accent: var(--soc-low);
}
.recommendation-main,
.recommendation-action {
  min-width: 0;
}
.recommendation-heading {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}
.recommendation-heading strong {
  overflow: hidden;
  color: var(--soc-text);
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recommendation-heading b {
  color: var(--recommendation-accent);
  font-size: 24px;
  line-height: 1;
}
.recommendation-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  margin-top: 8px;
  color: var(--soc-text-muted);
  font-size: 12px;
}
.recommendation-main p {
  margin: 8px 0 0;
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.55;
}
.recommendation-action {
  display: flex;
  flex-direction: column;
  justify-content: center;
  border-left: 1px solid rgba(190, 183, 171, 0.38);
  padding-left: 12px;
}
.recommendation-action span {
  color: var(--soc-text-muted);
  font-size: 12px;
}
.recommendation-action strong {
  display: block;
  margin-top: 6px;
  color: var(--soc-warm-strong);
  font-size: 13px;
  line-height: 1.55;
}
.recommendation-buttons {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
}
.daily-todo-worklist {
  padding: 16px;
}
.todo-panel-head > span {
  margin-top: 0;
  white-space: nowrap;
}
.todo-type-filter {
  display: flex;
  flex-wrap: wrap;
  margin: 0 0 12px;
}
.daily-todo-table :deep(.cell) {
  color: var(--soc-text);
}
.daily-todo-table :deep(.cell small) {
  display: block;
  margin-top: 3px;
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.4;
}
.todo-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 14px;
  color: var(--soc-text-muted);
  font-size: 12px;
}
@media (max-width: 980px) {
  .daily-hero,
  .panel-title {
    align-items: flex-start;
    flex-direction: column;
  }
  .daily-hero-actions {
    justify-content: flex-start;
  }
  .daily-summary-grid,
  .daily-filter-panel,
  .recommendation-row {
    grid-template-columns: 1fr;
  }
  .recommendation-action {
    border-top: 1px solid rgba(190, 183, 171, 0.38);
    border-left: 0;
    padding-top: 10px;
    padding-left: 0;
  }
  .todo-pagination {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
