<template>
  <div class="page-shell">
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
          <el-button type="primary" @click="load">查询</el-button>
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
          <el-button v-for="status in nextStatuses(detail.ticket.status)" :key="status" type="primary" @click="transition(status)">{{ status }}</el-button>
        </div>
        <AttackTimeline :items="detail.timeline" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AttackTimeline from '@/components/security/AttackTimeline.vue'
import SeverityBadge from '@/components/security/SeverityBadge.vue'
import StatusBadge from '@/components/security/StatusBadge.vue'
import { listTickets, ticketDetail, transitionTicket, type TicketItem, type TimelineItem } from '@/api/soc'

const statuses = ['待分派', '处理中', '待复核', '已关闭', '已归档']
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', severity: '', status: '' })
const rows = ref<TicketItem[]>([])
const total = ref(0)
const drawer = ref(false)
const detail = ref<{ ticket: TicketItem; timeline: TimelineItem[] }>()
const remark = ref('按流程推进')
const selectedTickets = ref<TicketItem[]>([])
const batchTargetStatus = ref('')
const loading = ref(false)
const error = ref('')

onMounted(load)
async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await listTickets(query)
    rows.value = res.data.data.records
    total.value = res.data.data.total
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
</style>
