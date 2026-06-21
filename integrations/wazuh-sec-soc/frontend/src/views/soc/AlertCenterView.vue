<template>
  <div class="page-shell">
    <section class="soc-panel panel-pad">
      <div class="soc-filter-bar">
        <el-input v-model="query.keyword" clearable placeholder="搜索规则描述、资产、来源 IP" @keyup.enter="load" />
        <el-select v-model="query.severity" clearable placeholder="等级">
          <el-option label="严重" value="critical" /><el-option label="高危" value="high" /><el-option label="中危" value="medium" /><el-option label="低危" value="low" />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="状态">
          <el-option label="新告警" value="new" /><el-option label="已确认" value="acknowledged" /><el-option label="已转工单" value="ticketed" /><el-option label="已关闭" value="closed" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
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
        <div class="soc-drawer-grid">
          <span>告警 ID</span><strong>{{ current.alertUid }}</strong>
          <span>等级</span><strong><SeverityBadge :severity="current.severity" /></strong>
          <span>数据来源</span><strong><DataSourceBadge :source="current.sourceType" /></strong>
          <span>规则</span><strong>{{ current.ruleId }} / {{ current.ruleDescription }}</strong>
          <span>资产</span><strong>{{ current.assetName }}（{{ current.assetIp }}）</strong>
          <span>来源 IP</span><strong>{{ current.sourceIp }}</strong>
          <span>降噪状态</span><strong><StatusBadge :status="current.noiseStatus || current.status" /></strong>
          <span>白名单</span><strong>{{ current.whitelistRuleName || '未命中' }}</strong>
          <span>重复次数</span><strong>{{ current.repeatCount || 1 }}</strong>
          <span>战术</span><strong>{{ current.tactic }}</strong>
          <span>原始索引</span><strong>{{ current.rawRef }}</strong>
        </div>
        <el-input v-model="remark" type="textarea" :rows="3" placeholder="填写处置说明" />
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DataSourceBadge from '@/components/security/DataSourceBadge.vue'
import SeverityBadge from '@/components/security/SeverityBadge.vue'
import StatusBadge from '@/components/security/StatusBadge.vue'
import { alertAction, listAlerts, type AlertItem } from '@/api/soc'

const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', severity: '', status: '' })
const rows = ref<AlertItem[]>([])
const total = ref(0)
const selected = ref<AlertItem[]>([])
const drawer = ref(false)
const current = ref<AlertItem>()
const remark = ref('已按 SOC 流程处置')
const loading = ref(false)
const error = ref('')

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await listAlerts(query)
    rows.value = res.data.data.records
    total.value = res.data.data.total
  } catch {
    error.value = '告警列表加载失败，请检查网络、权限或后端服务状态。'
  } finally {
    loading.value = false
  }
}
function open(row: AlertItem) {
  current.value = row
  drawer.value = true
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
.repeat-text {
  color: var(--soc-medium);
  font-size: 12px;
}
.muted-text {
  color: var(--soc-text-muted);
}
</style>
