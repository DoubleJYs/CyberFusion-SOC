<template>
  <div class="page-shell">
    <section class="soc-page-hero">
      <div>
        <span class="soc-page-kicker">FILE INTEGRITY MONITORING</span>
        <h1>文件变更</h1>
        <p>复核已授权主机目录中的日志、审计和业务文件元数据变化；不采集或上传文件内容。</p>
      </div>
      <div class="soc-page-tags">
        <el-button plain @click="openWatchPolicies">管理监控目录</el-button>
        <el-tag>授权目录</el-tag>
        <el-tag>元数据变更</el-tag>
        <el-tag>审计复核</el-tag>
      </div>
    </section>

    <section class="soc-panel panel-pad">
      <div class="module-filter-bar">
        <el-input v-model="query.keyword" clearable placeholder="搜索事件、主机、IP 或路径" @keyup.enter="load" />
        <el-select v-model="query.action" clearable placeholder="动作">
          <el-option label="新增" value="created" /><el-option label="修改" value="modified" /><el-option label="删除" value="deleted" /><el-option label="权限变化" value="permission" /><el-option label="基线快照" value="hash" />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="状态">
          <el-option label="新事件" value="new" /><el-option label="复核中" value="reviewing" /><el-option label="已确认" value="confirmed" /><el-option label="已忽略" value="ignored" /><el-option label="已关闭" value="closed" />
        </el-select>
        <el-button @click="load">查询</el-button>
      </div>
    </section>
    <section class="summary-row">
      <div v-for="item in summary" :key="item.name" class="soc-panel summary-card">
        <span>{{ actionLabel(item.name) }}</span>
        <strong>{{ item.value }}</strong>
      </div>
    </section>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false"><template #default><el-button size="small" @click="load">重试</el-button></template></el-alert>
    <section class="table-panel">
      <el-table v-loading="loading" :data="rows" empty-text="暂无文件完整性事件" @row-click="open">
        <el-table-column prop="eventUid" label="事件 ID" width="170" />
        <el-table-column label="动作" width="96"><template #default="{ row }">{{ actionLabel(row.action) }}</template></el-table-column>
        <el-table-column label="等级" width="86"><template #default="{ row }"><SeverityBadge :severity="row.severity" /></template></el-table-column>
        <el-table-column prop="hostname" label="主机" width="140" />
        <el-table-column prop="assetIp" label="IP" width="140" />
        <el-table-column prop="filePath" label="路径" min-width="280" show-overflow-tooltip />
        <el-table-column label="状态" width="110"><template #default="{ row }"><StatusBadge :status="row.status" /></template></el-table-column>
        <el-table-column label="来源" width="86"><template #default="{ row }"><DataSourceBadge :source="row.sourceType" /></template></el-table-column>
        <el-table-column prop="eventTime" label="时间" width="180" />
      </el-table>
      <div class="pagination-row">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" layout="total, sizes, prev, pager, next" :total="total" @change="load" />
      </div>
    </section>
    <el-drawer v-model="drawer" title="文件完整性详情" size="560px">
      <div v-if="current" class="drawer-stack">
        <div class="soc-drawer-grid">
          <span>事件 ID</span><strong>{{ current.eventUid }}</strong>
          <span>动作</span><strong>{{ actionLabel(current.action) }}</strong>
          <span>等级</span><strong><SeverityBadge :severity="current.severity" /></strong>
          <span>主机</span><strong>{{ current.hostname }}（{{ current.assetIp }}）</strong>
          <span>路径</span><strong>{{ current.filePath }}</strong>
          <span>规则</span><strong>{{ current.ruleName }}</strong>
          <span>数据来源</span><strong><DataSourceBadge :source="current.sourceType" /></strong>
          <span>状态</span><strong><StatusBadge :status="current.status" /></strong>
        </div>
        <SecurityDispositionGuide
          category="fim"
          :subject="current.filePath || current.ruleName"
          :source="current.sourceType"
          :severity="current.severity"
          :status="current.status"
          :asset="`${current.hostname || '-'}（${current.assetIp || '-'}）`"
          :reason="current.ruleName"
          :recommendation="`核对 ${actionLabel(current.action)} 是否来自授权发布或预期采集。`"
        />
        <el-input v-model="remark" type="textarea" :rows="3" placeholder="填写复核说明" />
        <div class="drawer-actions">
          <el-button v-for="status in nextStatuses(current.status)" :key="status" @click="transition(status)">{{ statusLabel(status) }}</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import DataSourceBadge from '@/components/security/DataSourceBadge.vue'
import SecurityDispositionGuide from '@/components/security/SecurityDispositionGuide.vue'
import SeverityBadge from '@/components/security/SeverityBadge.vue'
import StatusBadge from '@/components/security/StatusBadge.vue'
import { fileIntegrityDetail, fileIntegritySummary, listFileIntegrityEvents, updateFileIntegrityStatus, type FileIntegrityItem } from '@/api/soc'

const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', action: '', status: '' })
const rows = ref<FileIntegrityItem[]>([])
const summary = ref<Array<{ name: string; value: number }>>([])
const total = ref(0)
const drawer = ref(false)
const current = ref<FileIntegrityItem>()
const loading = ref(false)
const error = ref('')
const remark = ref('按文件完整性复核流程推进')
const router = useRouter()

onMounted(load)
async function load() {
  loading.value = true
  error.value = ''
  try {
    const [listRes, summaryRes] = await Promise.all([listFileIntegrityEvents(query), fileIntegritySummary()])
    rows.value = listRes.data.data.records
    total.value = listRes.data.data.total
    summary.value = summaryRes.data.data
  } catch {
    error.value = '文件完整性数据加载失败，请检查网络、权限或后端服务状态。'
  } finally {
    loading.value = false
  }
}
async function open(row: FileIntegrityItem) {
  const res = await fileIntegrityDetail(row.id)
  current.value = res.data.data
  drawer.value = true
}
function nextStatuses(status: string) {
  return ({ new: ['reviewing'], reviewing: ['confirmed', 'ignored'], confirmed: ['closed'], ignored: [], closed: [] } as Record<string, string[]>)[status] || []
}
function statusLabel(status: string) {
  return ({ reviewing: '复核中', confirmed: '确认变更', ignored: '忽略', closed: '关闭' } as Record<string, string>)[status] || status
}
function actionLabel(action: string) {
  return ({ created: '新增', modified: '修改', deleted: '删除', permission: '权限变化', hash: '基线快照' } as Record<string, string>)[action] || action
}
function openWatchPolicies() { void router.push({ path: '/soc/policies', query: { tab: 'fim-watch' } }) }
async function transition(status: string) {
  if (!current.value) return
  await updateFileIntegrityStatus(current.value.id, status, remark.value)
  ElMessage.success('文件完整性事件状态已更新')
  await open(current.value)
  await load()
}
</script>

<style scoped>
.panel-pad { padding: 14px; }
.module-filter-bar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 160px 160px auto;
  gap: 10px;
  align-items: center;
}
.summary-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
}
.summary-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
}
.summary-card span {
  color: var(--soc-text-muted);
}
.drawer-stack { display: grid; gap: 16px; }
.drawer-actions { display: flex; flex-wrap: wrap; gap: 8px; }
@media (max-width: 860px) {
  .module-filter-bar,
  .summary-row {
    grid-template-columns: 1fr;
  }
}
</style>
