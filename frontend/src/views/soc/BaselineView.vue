<template>
  <div class="page-shell">
    <section class="soc-page-hero">
      <div>
        <span class="soc-page-kicker">CONFIGURATION BASELINE</span>
        <h1>配置检查</h1>
        <p>这个页面帮你查看安全配置检查结果、风险原因和整改状态。</p>
      </div>
      <div class="soc-page-tags">
        <el-tag>SSH</el-tag>
        <el-tag>Password</el-tag>
        <el-tag>Firewall</el-tag>
        <el-tag>Service</el-tag>
      </div>
    </section>

    <section class="soc-panel panel-pad">
      <div class="module-filter-bar">
        <el-input v-model="query.keyword" clearable placeholder="搜索核查项、资产或 IP" @keyup.enter="load" />
        <el-select v-model="query.category" clearable placeholder="类别">
          <el-option label="SSH" value="SSH" /><el-option label="密码策略" value="PASSWORD" /><el-option label="防火墙" value="FIREWALL" /><el-option label="系统服务" value="SERVICE" /><el-option label="敏感文件权限" value="FILE_PERMISSION" />
        </el-select>
        <el-select v-model="query.result" clearable placeholder="结果">
          <el-option label="未通过" value="failed" /><el-option label="通过" value="passed" />
        </el-select>
        <el-button @click="load">查询</el-button>
      </div>
    </section>
    <section class="summary-row">
      <div v-for="item in summary" :key="item.name" class="soc-panel summary-card">
        <span>{{ categoryLabel(item.name) }}</span>
        <strong>{{ item.value }}</strong>
      </div>
    </section>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false"><template #default><el-button size="small" @click="load">重试</el-button></template></el-alert>
    <section class="table-panel">
      <el-table v-loading="loading" :data="rows" empty-text="暂无基线核查数据" @row-click="open">
        <el-table-column prop="checkCode" label="核查编码" width="170" />
        <el-table-column label="类别" width="120"><template #default="{ row }">{{ categoryLabel(row.category) }}</template></el-table-column>
        <el-table-column prop="checkItem" label="核查项" min-width="230" show-overflow-tooltip />
        <el-table-column prop="assetName" label="资产" width="140" />
        <el-table-column prop="assetIp" label="IP" width="140" />
        <el-table-column label="结果" width="90"><template #default="{ row }"><el-tag :type="row.result === 'passed' ? 'success' : 'danger'">{{ row.result === 'passed' ? '通过' : '未通过' }}</el-tag></template></el-table-column>
        <el-table-column label="通过率" width="130"><template #default="{ row }"><el-progress :percentage="row.passRate" :stroke-width="8" /></template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><StatusBadge :status="row.status" /></template></el-table-column>
        <el-table-column label="来源" width="86"><template #default="{ row }"><DataSourceBadge :source="row.sourceType" /></template></el-table-column>
      </el-table>
      <div class="pagination-row">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" layout="total, sizes, prev, pager, next" :total="total" @change="load" />
      </div>
    </section>
    <el-drawer v-model="drawer" title="基线核查详情" size="560px">
      <div v-if="current" class="drawer-stack">
        <div class="soc-drawer-grid">
          <span>核查编码</span><strong>{{ current.checkCode }}</strong>
          <span>类别</span><strong>{{ categoryLabel(current.category) }}</strong>
          <span>核查项</span><strong>{{ current.checkItem }}</strong>
          <span>资产</span><strong>{{ current.assetName }}（{{ current.assetIp }}）</strong>
          <span>结果</span><strong>{{ current.result === 'passed' ? '通过' : '未通过' }}</strong>
          <span>通过率</span><strong>{{ current.passRate }}%</strong>
          <span>数据来源</span><strong><DataSourceBadge :source="current.sourceType" /></strong>
          <span>整改建议</span><strong>{{ current.remediation }}</strong>
        </div>
        <SecurityDispositionGuide
          category="baseline"
          :subject="current.checkItem || current.checkCode"
          :source="current.sourceType"
          :severity="current.severity"
          :status="current.status"
          :asset="`${current.assetName || '-'}（${current.assetIp || '-'}）`"
          :reason="current.remediation"
          :recommendation="current.remediation"
        />
        <el-input v-model="remark" type="textarea" :rows="3" placeholder="填写整改或复核说明" />
        <div class="drawer-actions">
          <el-button v-for="status in nextStatuses(current.status)" :key="status" @click="transition(status)">{{ statusLabel(status) }}</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import DataSourceBadge from '@/components/security/DataSourceBadge.vue'
import SecurityDispositionGuide from '@/components/security/SecurityDispositionGuide.vue'
import StatusBadge from '@/components/security/StatusBadge.vue'
import { baselineDetail, baselineSummary, listBaselines, updateBaselineStatus, type BaselineItem } from '@/api/soc'

const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', category: '', result: '' })
const rows = ref<BaselineItem[]>([])
const summary = ref<Array<{ name: string; value: number }>>([])
const total = ref(0)
const drawer = ref(false)
const current = ref<BaselineItem>()
const loading = ref(false)
const error = ref('')
const remark = ref('按基线整改流程推进')

onMounted(load)
async function load() {
  loading.value = true
  error.value = ''
  try {
    const [listRes, summaryRes] = await Promise.all([listBaselines(query), baselineSummary()])
    rows.value = listRes.data.data.records
    total.value = listRes.data.data.total
    summary.value = summaryRes.data.data
  } catch {
    error.value = '基线核查数据加载失败，请检查网络、权限或后端服务状态。'
  } finally {
    loading.value = false
  }
}
async function open(row: BaselineItem) {
  const res = await baselineDetail(row.id)
  current.value = res.data.data
  drawer.value = true
}
function nextStatuses(status: string) {
  return ({ failed: ['remediating'], remediating: ['reviewing'], reviewing: ['passed', 'accepted'], passed: [], accepted: [] } as Record<string, string[]>)[status] || []
}
function statusLabel(status: string) {
  return ({ remediating: '整改中', reviewing: '待复核', passed: '复核通过', accepted: '接受风险' } as Record<string, string>)[status] || status
}
function categoryLabel(category: string) {
  return ({ SSH: 'SSH', PASSWORD: '密码策略', FIREWALL: '防火墙', SERVICE: '系统服务', FILE_PERMISSION: '敏感文件权限' } as Record<string, string>)[category] || category
}
async function transition(status: string) {
  if (!current.value) return
  await updateBaselineStatus(current.value.id, status, remark.value)
  ElMessage.success('基线状态已更新')
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
  grid-template-columns: repeat(5, minmax(0, 1fr));
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
@media (max-width: 980px) {
  .module-filter-bar,
  .summary-row {
    grid-template-columns: 1fr;
  }
}
</style>
