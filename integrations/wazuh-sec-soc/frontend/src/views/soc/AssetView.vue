<template>
  <div class="page-shell">
    <section class="soc-panel panel-pad">
      <div class="asset-filter-bar">
        <el-input v-model="query.keyword" clearable placeholder="搜索主机名或 IP" @keyup.enter="load" />
        <el-select v-model="query.riskLevel" clearable placeholder="风险">
          <el-option label="严重" value="critical" /><el-option label="高" value="high" /><el-option label="中" value="medium" /><el-option label="低" value="low" />
        </el-select>
        <span />
        <div class="toolbar-actions">
          <el-button :disabled="!selectedAssets.length" @click="copySelectedIps">复制 IP</el-button>
          <el-button :disabled="!selectedAssets.length" @click="exportSelectedAssets">导出清单</el-button>
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
      <el-table v-loading="loading" :data="rows" empty-text="暂无资产数据" @row-click="openAsset" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="46" />
        <el-table-column prop="hostname" label="主机名" min-width="150" />
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column prop="osType" label="系统" width="110" />
        <el-table-column label="来源" width="86"><template #default="{ row }"><DataSourceBadge :source="row.sourceType" /></template></el-table-column>
        <el-table-column label="风险" width="100"><template #default="{ row }"><AssetRiskTag :risk-level="row.riskLevel" /></template></el-table-column>
        <el-table-column prop="openAlertCount" label="未关闭告警" width="110" />
        <el-table-column prop="deptName" label="部门" min-width="140" />
        <el-table-column prop="ownerName" label="负责人" width="120" />
        <el-table-column prop="lastSeenAt" label="最后发现" width="180" />
      </el-table>
      <div class="pagination-row">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" layout="total, sizes, prev, pager, next" :total="total" @change="load" />
      </div>
    </section>
    <el-drawer v-model="drawer" title="资产详情" size="520px">
      <div v-if="currentAsset" class="drawer-stack">
        <div class="soc-drawer-grid">
          <span>主机名</span><strong>{{ currentAsset.hostname }}</strong>
          <span>IP 地址</span><strong>{{ currentAsset.ip }}</strong>
          <span>系统类型</span><strong>{{ currentAsset.osType }}</strong>
          <span>数据来源</span><strong><DataSourceBadge :source="currentAsset.sourceType" /></strong>
          <span>风险等级</span><strong><AssetRiskTag :risk-level="currentAsset.riskLevel" /></strong>
          <span>未关闭告警</span><strong>{{ currentAsset.openAlertCount }}</strong>
          <span>所属部门</span><strong>{{ currentAsset.deptName || '-' }}</strong>
          <span>负责人</span><strong>{{ currentAsset.ownerName || '-' }}</strong>
          <span>最后发现</span><strong>{{ currentAsset.lastSeenAt || '-' }}</strong>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AssetRiskTag from '@/components/security/AssetRiskTag.vue'
import DataSourceBadge from '@/components/security/DataSourceBadge.vue'
import { listAssets, type AssetItem } from '@/api/soc'

const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', riskLevel: '' })
const rows = ref<AssetItem[]>([])
const total = ref(0)
const drawer = ref(false)
const currentAsset = ref<AssetItem>()
const selectedAssets = ref<AssetItem[]>([])
const loading = ref(false)
const error = ref('')

onMounted(load)
async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await listAssets(query)
    rows.value = res.data.data.records
    total.value = res.data.data.total
  } catch {
    error.value = '资产数据加载失败，请检查网络、权限或后端服务状态。'
  } finally {
    loading.value = false
  }
}
function openAsset(row: AssetItem) {
  currentAsset.value = row
  drawer.value = true
}
function onSelectionChange(selection: AssetItem[]) {
  selectedAssets.value = selection
}
async function copySelectedIps() {
  const text = selectedAssets.value.map((item) => item.ip).join('\n')
  await navigator.clipboard.writeText(text)
  ElMessage.success('已复制选中资产 IP')
}
function exportSelectedAssets() {
  const header = ['主机名', 'IP', '系统', '风险', '部门', '负责人', '未关闭告警', '最后发现']
  const lines = selectedAssets.value.map((item) =>
    [item.hostname, item.ip, item.osType, item.riskLevel, item.deptName, item.ownerName, item.openAlertCount, item.lastSeenAt]
      .map((value) => `"${String(value ?? '').replace(/"/g, '""')}"`)
      .join(',')
  )
  const blob = new Blob([[header.join(','), ...lines].join('\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'asset-risk-list.csv'
  link.click()
  URL.revokeObjectURL(url)
  ElMessage.success('资产清单已生成')
}
</script>

<style scoped>
.panel-pad { padding: 14px; }
.asset-filter-bar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 160px minmax(12px, 1fr) auto;
  gap: 10px;
  align-items: center;
}
.toolbar-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}
.drawer-stack { display: grid; gap: 16px; }
@media (max-width: 860px) {
  .asset-filter-bar {
    grid-template-columns: 1fr;
  }
  .toolbar-actions {
    justify-content: flex-start;
  }
}
</style>
