<template>
  <div class="page-shell">
    <section class="soc-page-hero">
      <div>
        <span class="soc-page-kicker">CASE AND TICKET CLOSURE</span>
        <h1>处置工单</h1>
        <p>这个页面帮你跟踪告警处置进度、负责人、SLA 和时间线。</p>
      </div>
      <div class="soc-page-tags">
        <el-tag>Alert to Ticket</el-tag>
        <el-tag>Timeline</el-tag>
        <el-tag>Review</el-tag>
      </div>
    </section>

    <section class="soc-panel panel-pad">
      <div class="soc-filter-bar">
        <el-input v-model="query.keyword" clearable placeholder="搜索工单号或标题" @keyup.enter="load" />
        <el-select v-model="query.severity" clearable placeholder="等级">
          <el-option label="严重" value="critical" /><el-option label="高危" value="high" /><el-option label="中危" value="medium" /><el-option label="低危" value="low" />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="状态">
          <el-option v-for="status in statuses" :key="status" :label="status" :value="status" />
        </el-select>
        <div class="toolbar-actions">
          <el-select v-model="batchTargetStatus" class="batch-select" placeholder="批量流转" clearable>
            <el-option label="处理中" value="处理中" />
            <el-option label="待复核" value="待复核" />
            <el-option label="已关闭" value="已关闭" />
            <el-option label="已归档" value="已归档" />
          </el-select>
          <el-button :disabled="!selectedTickets.length || !batchTargetStatus" @click="batchTransition">执行</el-button>
          <el-button @click="load">查询</el-button>
        </div>
      </div>
    </section>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false">
      <template #default>
        <el-button size="small" @click="load">重试</el-button>
      </template>
    </el-alert>
    <section class="table-panel">
      <el-table v-loading="loading" :data="rows" empty-text="暂无工单数据" @row-click="open" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="46" />
        <el-table-column prop="ticketNo" label="工单号" width="170" />
        <el-table-column label="等级" width="86"><template #default="{ row }"><SeverityBadge :severity="row.severity" /></template></el-table-column>
        <el-table-column prop="title" label="标题" min-width="260" show-overflow-tooltip />
        <el-table-column label="状态" width="100"><template #default="{ row }"><StatusBadge :status="row.status" /></template></el-table-column>
        <el-table-column prop="assigneeName" label="处理人" width="110" />
        <el-table-column prop="dueAt" label="截止时间" width="180" />
      </el-table>
      <div class="pagination-row">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" layout="total, sizes, prev, pager, next" :total="total" @change="load" />
      </div>
    </section>
    <el-drawer v-model="drawer" title="工单详情" size="540px">
      <div v-if="detail" class="drawer-stack">
        <div class="soc-drawer-grid">
          <span>工单号</span><strong>{{ detail.ticket.ticketNo }}</strong>
          <span>标题</span><strong>{{ detail.ticket.title }}</strong>
          <span>状态</span><strong><StatusBadge :status="detail.ticket.status" /></strong>
          <span>处理人</span><strong>{{ detail.ticket.assigneeName || '-' }}</strong>
          <span>处置说明</span><strong>{{ detail.ticket.resolution || '-' }}</strong>
          <span>复核结论</span><strong>{{ detail.ticket.reviewConclusion || '-' }}</strong>
        </div>
        <el-input v-model="remark" type="textarea" :rows="3" placeholder="填写流转说明" />
        <div class="drawer-actions">
          <el-button v-for="status in nextStatuses(detail.ticket.status)" :key="status" @click="transition(status)">{{ status }}</el-button>
        </div>
        <section class="task-panel">
          <div class="panel-title compact">
            <div>
              <strong>处置剧本任务</strong>
              <span>任务清单来自处置剧本，只记录人工推进状态。</span>
            </div>
            <el-tag effect="plain">{{ taskProgress }}</el-tag>
          </div>
          <div v-if="detail.tasks?.length" class="task-list">
            <article v-for="task in detail.tasks" :key="task.id" class="task-card">
              <div>
                <strong>{{ task.taskName }}</strong>
                <span>{{ task.instruction }}</span>
                <em v-if="task.expectedEvidence">预期证据：{{ task.expectedEvidence }}</em>
              </div>
              <div class="task-actions">
                <el-tag :type="taskStatusType(task.status)" effect="plain">{{ taskStatusLabel(task.status) }}</el-tag>
                <el-button size="small" :disabled="task.status !== 'pending'" @click.stop="runTask(task, 'start')">开始</el-button>
                <el-button size="small" type="primary" :disabled="['completed', 'confirmed'].includes(task.status)" @click.stop="runTask(task, 'complete')">完成</el-button>
                <el-button size="small" :disabled="['completed', 'confirmed', 'skipped'].includes(task.status)" @click.stop="runTask(task, 'skip')">跳过</el-button>
              </div>
            </article>
          </div>
          <el-empty v-else description="暂无处置剧本任务" :image-size="72" />
        </section>
        <AttackTimeline :items="detail.timeline" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AttackTimeline from '@/components/security/AttackTimeline.vue'
import SeverityBadge from '@/components/security/SeverityBadge.vue'
import StatusBadge from '@/components/security/StatusBadge.vue'
import { listTickets, ticketDetail, transitionTicket, updateTicketTask, type TicketItem, type TicketTaskItem, type TimelineItem } from '@/api/soc'

const route = useRoute()
const statuses = ['待分派', '处理中', '待复核', '已关闭', '已归档']
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', severity: '', status: '' })
const rows = ref<TicketItem[]>([])
const total = ref(0)
const drawer = ref(false)
const detail = ref<{ ticket: TicketItem; timeline: TimelineItem[]; tasks?: TicketTaskItem[] }>()
const remark = ref('按流程推进')
const selectedTickets = ref<TicketItem[]>([])
const batchTargetStatus = ref('')
const loading = ref(false)
const error = ref('')

watch(
  () => [route.query.keyword, route.query.openTicketId],
  () => {
    query.keyword = typeof route.query.keyword === 'string' ? route.query.keyword : ''
    query.pageNum = 1
    void load()
  },
  { immediate: true }
)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await listTickets(query)
    rows.value = res.data.data.records
    total.value = res.data.data.total
    openRouteTicketIfNeeded()
  } catch {
    error.value = '工单列表加载失败，请检查网络、权限或后端服务状态。'
  } finally {
    loading.value = false
  }
}
function onSelectionChange(selection: TicketItem[]) {
  selectedTickets.value = selection
}
async function open(row: TicketItem) {
  const res = await ticketDetail(row.id)
  detail.value = res.data.data
  drawer.value = true
}

function openRouteTicketIfNeeded() {
  const openTicketId = typeof route.query.openTicketId === 'string' ? Number(route.query.openTicketId) : 0
  if (!openTicketId) return
  const matched = rows.value.find((item) => item.id === openTicketId)
  if (matched) void open(matched)
}
function nextStatuses(status: string) {
  return ({ 待分派: ['处理中'], 处理中: ['待复核'], 待复核: ['处理中', '已关闭'], 已关闭: ['已归档'], 已归档: [] } as Record<string, string[]>)[status] || []
}
async function transition(status: string) {
  if (!detail.value) return
  await transitionTicket(detail.value.ticket.id, status, remark.value)
  ElMessage.success('工单状态已更新')
  await open(detail.value.ticket)
  await load()
}
async function runTask(task: TicketTaskItem, action: 'start' | 'complete' | 'skip') {
  if (!detail.value) return
  await updateTicketTask(detail.value.ticket.id, task.id, action, { remark: '工单详情推进处置任务' })
  ElMessage.success('处置任务已更新')
  await open(detail.value.ticket)
}
async function batchTransition() {
  const targetStatus = batchTargetStatus.value
  const allowed = selectedTickets.value.filter((ticket) => nextStatuses(ticket.status).includes(targetStatus))
  if (!allowed.length) {
    ElMessage.warning('选中工单没有可执行的目标状态')
    return
  }
  await ElMessageBox.confirm(`确认将 ${allowed.length} 个工单流转为「${targetStatus}」？`, '批量流转', { type: 'warning' })
  let succeeded = 0
  for (const ticket of allowed) {
    try {
      await transitionTicket(ticket.id, targetStatus, `批量流转：${targetStatus}`)
      succeeded += 1
    } catch {
      // The request interceptor has already surfaced the concrete API error.
    }
  }
  if (succeeded) ElMessage.success(`批量流转已完成 ${succeeded} 个工单`)
  await load()
  if (detail.value) await open(detail.value.ticket)
}
function taskStatusLabel(status: string) {
  const labels: Record<string, string> = {
    pending: '待处理',
    in_progress: '处理中',
    submitted: '已提交',
    confirmed: '已确认',
    completed: '已完成',
    skipped: '已跳过',
  }
  return labels[status] || status
}
function taskStatusType(status: string) {
  if (['completed', 'confirmed'].includes(status)) return 'success'
  if (status === 'skipped') return 'info'
  if (status === 'in_progress' || status === 'submitted') return 'warning'
  return ''
}
const taskProgress = computed(() => {
  const tasks = detail.value?.tasks || []
  if (!tasks.length) return '0 / 0'
  const done = tasks.filter((task) => ['completed', 'confirmed'].includes(task.status)).length
  return `${done} / ${tasks.length}`
})
</script>

<style scoped>
.panel-pad { padding: 14px; }
.drawer-stack { display: grid; gap: 16px; }
.drawer-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.toolbar-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}
.batch-select { width: 132px; }
.task-panel {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid rgba(179, 173, 163, 0.46);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.72);
}
.task-list {
  display: grid;
  gap: 10px;
}
.task-card {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid rgba(179, 173, 163, 0.36);
  border-radius: 8px;
  background: #fff;
}
.task-card div:first-child {
  display: grid;
  gap: 4px;
}
.task-card span,
.task-card em {
  color: var(--soc-text-muted);
  font-size: 12px;
  font-style: normal;
  line-height: 1.6;
}
.task-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
