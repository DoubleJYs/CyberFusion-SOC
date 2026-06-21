<template>
  <div class="page-shell">
    <section class="soc-panel report-toolbar">
      <div class="report-filters">
        <el-input v-model="query.keyword" clearable placeholder="搜索报表编号或标题" @keyup.enter="load" />
        <el-select v-model="query.reportType" clearable placeholder="类型">
          <el-option label="日报" value="daily" />
          <el-option label="周报" value="weekly" />
          <el-option label="月报" value="monthly" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
      </div>
      <div class="toolbar-actions">
        <el-radio-group v-model="reportType">
          <el-radio-button label="daily">日报</el-radio-button>
          <el-radio-button label="weekly">周报</el-radio-button>
          <el-radio-button label="monthly">月报</el-radio-button>
        </el-radio-group>
        <el-button :disabled="!selectedReports.length" @click="batchOpenExport('xlsx')">批量 Excel</el-button>
        <el-button :disabled="!selectedReports.length" @click="batchOpenExport('pdf')">批量 PDF</el-button>
        <el-button type="primary" :loading="generating" @click="generate">生成报表</el-button>
      </div>
    </section>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false">
      <template #default>
        <el-button size="small" @click="load">重试</el-button>
      </template>
    </el-alert>
    <section class="table-panel">
      <el-table v-loading="loading" :data="rows" empty-text="暂无报表数据" @row-click="openReport" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="46" />
        <el-table-column prop="reportNo" label="报表编号" width="190" />
        <el-table-column prop="title" label="标题" min-width="240" />
        <el-table-column prop="reportType" label="类型" width="90" />
        <el-table-column label="周期" width="210"><template #default="{ row }">{{ row.periodStart }} ~ {{ row.periodEnd }}</template></el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="260" show-overflow-tooltip />
        <el-table-column label="导出" width="160">
          <template #default="{ row }">
            <el-button text type="primary" @click.stop="openExport(row.id, 'xlsx')">Excel</el-button>
            <el-button text type="primary" @click.stop="openExport(row.id, 'pdf')">PDF</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-row">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" layout="total, sizes, prev, pager, next" :total="total" @change="load" />
      </div>
    </section>
    <el-drawer v-model="drawer" title="报表详情" size="560px">
      <div v-if="currentReport" class="drawer-stack">
        <div class="soc-drawer-grid">
          <span>报表编号</span><strong>{{ currentReport.reportNo }}</strong>
          <span>标题</span><strong>{{ currentReport.title }}</strong>
          <span>类型</span><strong>{{ reportTypeLabel(currentReport.reportType) }}</strong>
          <span>周期</span><strong>{{ currentReport.periodStart }} ~ {{ currentReport.periodEnd }}</strong>
          <span>生成时间</span><strong>{{ currentReport.generatedAt || '-' }}</strong>
          <span>状态</span><strong>{{ currentReport.status }}</strong>
        </div>
        <section class="report-section">
          <h3>摘要</h3>
          <p>{{ currentReport.summary }}</p>
        </section>
        <section class="report-section">
          <h3>整改建议</h3>
          <p>{{ currentReport.recommendation }}</p>
        </section>
        <div class="drawer-actions">
          <el-button type="primary" @click="openExport(currentReport.id, 'xlsx')">导出 Excel</el-button>
          <el-button @click="openExport(currentReport.id, 'pdf')">导出 PDF</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { generateReport, listReports, reportExportUrl, type ReportItem } from '@/api/soc'

const reportType = ref('daily')
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', reportType: '' })
const rows = ref<ReportItem[]>([])
const total = ref(0)
const drawer = ref(false)
const currentReport = ref<ReportItem>()
const selectedReports = ref<ReportItem[]>([])
const loading = ref(false)
const generating = ref(false)
const error = ref('')

onMounted(load)
async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await listReports(query)
    rows.value = res.data.data.records
    total.value = res.data.data.total
  } catch {
    error.value = '报表列表加载失败，请检查网络、权限或后端服务状态。'
  } finally {
    loading.value = false
  }
}
async function generate() {
  generating.value = true
  try {
    await generateReport(reportType.value)
    ElMessage.success('报表已生成')
    await load()
  } finally {
    generating.value = false
  }
}
function onSelectionChange(selection: ReportItem[]) {
  selectedReports.value = selection
}
function openReport(row: ReportItem) {
  currentReport.value = row
  drawer.value = true
}
function openExport(id: number, format: 'xlsx' | 'pdf') {
  window.open(reportExportUrl(id, format), '_blank', 'noopener')
}
function batchOpenExport(format: 'xlsx' | 'pdf') {
  selectedReports.value.forEach((report) => openExport(report.id, format))
  ElMessage.success(`已打开 ${selectedReports.value.length} 个导出任务`)
}
function reportTypeLabel(type: string) {
  return ({ daily: '日报', weekly: '周报', monthly: '月报' } as Record<string, string>)[type] || type
}
</script>

<style scoped>
.report-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 14px;
  flex-wrap: wrap;
}
.report-filters,
.toolbar-actions,
.drawer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.report-filters {
  flex: 1 1 420px;
}
.report-filters .el-input {
  max-width: 280px;
}
.report-filters .el-select {
  width: 130px;
}
.drawer-stack {
  display: grid;
  gap: 16px;
}
.report-section {
  border-top: 1px solid var(--soc-border);
  padding-top: 14px;
}
.report-section h3 {
  margin: 0 0 8px;
  font-size: 14px;
}
.report-section p {
  margin: 0;
  color: var(--soc-text-muted);
  line-height: 1.7;
  white-space: pre-line;
}
@media (max-width: 760px) {
  .report-filters .el-input,
  .report-filters .el-select {
    max-width: none;
    width: 100%;
  }
}
</style>
