<template>
  <div class="page-shell">
    <section class="soc-page-hero">
      <div>
        <span class="soc-page-kicker">SECURITY REPORTING</span>
        <h1>报告中心</h1>
        <p>这个页面帮你生成、查看和导出安全运营或安全验证报告。</p>
      </div>
      <div class="soc-page-tags">
        <el-tag>Daily</el-tag>
        <el-tag>Weekly</el-tag>
        <el-tag>Monthly</el-tag>
        <el-tag>PDF / Excel</el-tag>
      </div>
    </section>

    <section class="soc-panel report-toolbar">
      <div class="report-filters">
        <el-input v-model="query.keyword" clearable placeholder="搜索报表编号或标题" @keyup.enter="load" />
        <el-select v-model="query.reportType" clearable placeholder="类型">
          <el-option label="日报" value="daily" />
          <el-option label="周报" value="weekly" />
          <el-option label="月报" value="monthly" />
          <el-option label="安全验证报告" value="security_validation" />
        </el-select>
        <el-button @click="load">查询</el-button>
      </div>
      <div class="toolbar-actions">
        <el-radio-group v-model="reportType">
          <el-radio-button label="daily">日报</el-radio-button>
          <el-radio-button label="weekly">周报</el-radio-button>
          <el-radio-button label="monthly">月报</el-radio-button>
          <el-radio-button label="security_validation">安全验证</el-radio-button>
        </el-radio-group>
        <el-input v-if="reportType === 'security_validation'" v-model="batchId" class="batch-input" clearable placeholder="Batch ID" />
        <el-button :disabled="!selectedReports.length" :loading="batchDownloading === 'xlsx'" @click="batchDownload('xlsx')">批量下载 Excel</el-button>
        <el-button :disabled="!selectedReports.length" :loading="batchDownloading === 'pdf'" @click="batchDownload('pdf')">批量下载 PDF</el-button>
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
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column label="批次" width="180">
          <template #default="{ row }">{{ reportBatch(row) }}</template>
        </el-table-column>
        <el-table-column label="风险摘要" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ reportRiskSummary(row) }}</template>
        </el-table-column>
        <el-table-column label="事件簇" width="86">
          <template #default="{ row }">{{ reportIncidentCount(row) }}</template>
        </el-table-column>
        <el-table-column label="推荐动作" width="96">
          <template #default="{ row }">{{ reportRecommendationCount(row) }}</template>
        </el-table-column>
        <el-table-column label="周期" width="210"><template #default="{ row }">{{ row.periodStart }} ~ {{ row.periodEnd }}</template></el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="260" show-overflow-tooltip />
        <el-table-column label="导出" width="190">
          <template #default="{ row }">
            <div class="export-cell" @click.stop>
              <el-dropdown trigger="click" @command="handleExcelCommand(row, $event)">
                <el-button text>Excel</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="preview">预览 Excel</el-dropdown-item>
                    <el-dropdown-item command="download">下载 Excel</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-dropdown trigger="click" @command="handlePdfCommand(row, $event)">
                <el-button text>PDF</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="preview">预览 PDF</el-dropdown-item>
                    <el-dropdown-item command="download">下载 PDF</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-row">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" layout="total, sizes, prev, pager, next" :total="total" @change="load" />
      </div>
    </section>
    <el-drawer v-model="drawer" title="报表详情" size="680px">
      <div v-if="currentReport" class="drawer-stack">
        <div class="soc-drawer-grid">
          <span>报表编号</span><strong>{{ currentReport.reportNo }}</strong>
          <span>标题</span><strong>{{ currentReport.title }}</strong>
          <span>类型</span><strong>{{ reportTypeLabel(currentReport.reportType) }}</strong>
          <span>周期</span><strong>{{ currentReport.periodStart }} ~ {{ currentReport.periodEnd }}</strong>
          <span>生成时间</span><strong>{{ currentReport.generatedAt || '-' }}</strong>
          <span>状态</span><strong>{{ currentReport.status }}</strong>
        </div>
        <template v-if="currentReport.reportType === 'security_validation'">
          <section v-for="section in securityValidationSections(currentReport)" :key="section.title" class="report-section">
            <h3>{{ section.title }}</h3>
            <p>{{ section.body }}</p>
          </section>
        </template>
        <section v-else class="report-section">
          <h3>摘要</h3>
          <p>{{ currentReport.summary }}</p>
        </section>
        <section class="report-section">
          <h3>整改建议</h3>
          <p>{{ currentReport.recommendation }}</p>
        </section>
        <div class="drawer-actions">
          <el-button @click="previewExport(currentReport, 'xlsx')">预览 Excel</el-button>
          <el-button @click="downloadReportExport(currentReport, 'xlsx')">下载 Excel</el-button>
          <el-button @click="previewExport(currentReport, 'pdf')">预览 PDF</el-button>
          <el-button @click="downloadReportExport(currentReport, 'pdf')">下载 PDF</el-button>
        </div>
      </div>
    </el-drawer>
    <el-drawer v-model="exportDrawer" :title="exportTitle" size="780px" @closed="releaseExportObjectUrl">
      <div v-loading="exportLoading" class="export-preview-shell">
        <template v-if="exportPreview">
          <div class="export-preview-head">
            <div>
              <strong>{{ exportPreview.title }}</strong>
              <span>{{ exportPreview.filename }}</span>
            </div>
            <el-button type="primary" @click="downloadCurrentExport">下载{{ exportPreview.format === 'pdf' ? ' PDF' : ' Excel' }}</el-button>
          </div>
          <iframe v-if="exportPreview.format === 'pdf' && exportObjectUrl" class="pdf-preview-frame" :src="exportObjectUrl" title="PDF 预览" />
          <div v-else-if="exportPreview.format === 'pdf'" class="pdf-fallback">
            <p v-for="line in exportPreview.lines" :key="line">{{ line }}</p>
          </div>
          <el-table v-else :data="excelPreviewRows" border height="520" empty-text="暂无可预览内容">
            <el-table-column
              v-for="(header, index) in exportPreview.headers"
              :key="header"
              :label="header"
              min-width="160"
              show-overflow-tooltip
            >
              <template #default="{ row }">{{ row.cells[index] || '-' }}</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { exportReportBlob, generateReport, listReports, previewReportExport, type ReportExportPreview, type ReportItem } from '@/api/soc'
import { saveBlob } from '@/api/file'

const route = useRoute()
type ExportFormat = 'xlsx' | 'pdf'

const reportType = ref('daily')
const batchId = ref('')
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', reportType: '' })
const rows = ref<ReportItem[]>([])
const total = ref(0)
const drawer = ref(false)
const currentReport = ref<ReportItem>()
const selectedReports = ref<ReportItem[]>([])
const loading = ref(false)
const generating = ref(false)
const batchDownloading = ref<ExportFormat | ''>('')
const error = ref('')
const exportDrawer = ref(false)
const exportLoading = ref(false)
const exportPreview = ref<ReportExportPreview | null>(null)
const exportSourceReport = ref<ReportItem | null>(null)
const exportObjectUrl = ref('')
const exportTitle = computed(() => {
  if (!exportPreview.value) return '导出预览'
  return `${exportPreview.value.format === 'pdf' ? 'PDF' : 'Excel'} 预览`
})
const excelPreviewRows = computed(() => (exportPreview.value?.rows || []).map((cells, index) => ({ id: index, cells })))

watch(
  () => [route.query.keyword, route.query.reportType, route.query.batchId],
  () => {
    query.keyword = typeof route.query.keyword === 'string' ? route.query.keyword : ''
    query.reportType = typeof route.query.reportType === 'string' ? route.query.reportType : ''
    reportType.value = typeof route.query.reportType === 'string' ? route.query.reportType : reportType.value
    batchId.value = typeof route.query.batchId === 'string' ? route.query.batchId : query.keyword
    query.pageNum = 1
    void load()
  },
  { immediate: true }
)

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
    await generateReport(reportType.value, reportType.value === 'security_validation' ? batchId.value : undefined)
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

function handleExcelCommand(row: ReportItem, command: unknown) {
  handleExportCommand(row, 'xlsx', command)
}

function handlePdfCommand(row: ReportItem, command: unknown) {
  handleExportCommand(row, 'pdf', command)
}

function handleExportCommand(row: ReportItem, format: ExportFormat, command: unknown) {
  if (command === 'preview') {
    void previewExport(row, format)
    return
  }
  void downloadReportExport(row, format)
}

async function previewExport(report: ReportItem, format: ExportFormat) {
  exportDrawer.value = true
  exportLoading.value = true
  exportSourceReport.value = report
  exportPreview.value = null
  releaseExportObjectUrl()
  try {
    const preview = await previewReportExport(report.id, format)
    exportPreview.value = preview.data.data
    if (format === 'pdf') {
      const response = await exportReportBlob(report.id, 'pdf', 'inline')
      const blob = new Blob([response.data], { type: 'application/pdf' })
      exportObjectUrl.value = URL.createObjectURL(blob)
    }
  } catch {
    ElMessage.error('导出预览加载失败')
  } finally {
    exportLoading.value = false
  }
}

async function downloadReportExport(report: ReportItem, format: ExportFormat, filename?: string, showError = true) {
  try {
    const preview = filename ? null : await previewReportExport(report.id, format)
    const response = await exportReportBlob(report.id, format, 'attachment')
    saveBlob(response.data, filename || preview?.data.data.filename || `soc-report-${report.id}.${format}`)
  } catch (error) {
    if (showError) {
      ElMessage.error('报表下载失败')
    }
    throw error
  }
}

async function downloadCurrentExport() {
  if (!exportSourceReport.value || !exportPreview.value) return
  await downloadReportExport(exportSourceReport.value, exportPreview.value.format, exportPreview.value.filename)
}

async function batchDownload(format: ExportFormat) {
  if (!selectedReports.value.length) return
  batchDownloading.value = format
  let success = 0
  let failed = 0
  try {
    for (const report of selectedReports.value) {
      try {
        await downloadReportExport(report, format, undefined, false)
        success += 1
      } catch {
        failed += 1
      }
    }
    if (failed) {
      ElMessage.warning(`已下载 ${success} 个，${failed} 个下载失败`)
    } else {
      ElMessage.success(`已下载 ${success} 个${format === 'pdf' ? ' PDF' : ' Excel'} 报表`)
    }
  } finally {
    batchDownloading.value = ''
  }
}

function releaseExportObjectUrl() {
  if (exportObjectUrl.value) {
    URL.revokeObjectURL(exportObjectUrl.value)
    exportObjectUrl.value = ''
  }
}

onBeforeUnmount(releaseExportObjectUrl)
function reportTypeLabel(type: string) {
  return ({ daily: '日报', weekly: '周报', monthly: '月报', security_validation: '安全验证报告' } as Record<string, string>)[type] || type
}

function reportBatch(report: ReportItem) {
  if (report.reportType !== 'security_validation') return '-'
  return matchFirst(report.summary, /验证批次[：\\s]*([^，；。]+)/) || matchFirst(report.title, /（([^）]+)）/) || '-'
}

function reportRiskSummary(report: ReportItem) {
  if (report.reportType !== 'security_validation') return '-'
  const management = sectionBody(report.summary, '管理摘要')
  return management || matchFirst(report.summary, /Top 高风险资产 ([^；]+)/) || '-'
}

function reportIncidentCount(report: ReportItem) {
  if (report.reportType !== 'security_validation') return '-'
  return matchFirst(report.summary, /事件簇\\s*(\\d+)\\s*个/) || '0'
}

function reportRecommendationCount(report: ReportItem) {
  if (report.reportType !== 'security_validation') return '-'
  return matchFirst(report.summary, /推荐动作\\s*(\\d+)\\s*个/) || '0'
}

function securityValidationSections(report: ReportItem) {
  const rawSections = [
    { title: '管理摘要', body: sectionBody(report.summary, '管理摘要') },
    { title: '技术证据', body: sectionBody(report.summary, '技术证据') },
    { title: '处置进度', body: sectionBody(report.summary, '处置进度') || sectionBody(report.summary, '事件簇与风险') },
    { title: '员工配合', body: sectionBody(report.summary, '员工配合') },
    { title: '安全边界', body: sectionBody(report.summary, '安全边界') },
  ]
  return rawSections.map((item) => ({ ...item, body: item.body || '当前报告未提供该区块，建议重新生成 security_validation 报告。' }))
}

function sectionBody(summary: string, title: string) {
  const parts = summary
    .split(/(?=【[^】]+】)/)
    .map((item) => item.trim())
    .filter(Boolean)
  const match = parts.find((item) => item.startsWith(`【${title}】`))
  return match ? match.replace(`【${title}】`, '').trim() : ''
}

function matchFirst(value: string, pattern: RegExp) {
  const match = value.match(pattern)
  return match?.[1]?.trim()
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
.batch-input {
  width: 220px;
}
.drawer-stack {
  display: grid;
  gap: 16px;
}
.export-cell {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}
.export-preview-shell {
  min-height: 560px;
}
.export-preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  padding: 12px;
  border: 1px solid var(--soc-border);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.62);
}
.export-preview-head div {
  display: grid;
  gap: 4px;
  min-width: 0;
}
.export-preview-head strong,
.export-preview-head span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.export-preview-head span {
  color: var(--soc-text-muted);
  font-size: 12px;
}
.pdf-preview-frame {
  width: 100%;
  height: 620px;
  border: 1px solid var(--soc-border);
  border-radius: 8px;
  background: #fff;
}
.pdf-fallback {
  display: grid;
  gap: 8px;
  padding: 14px;
  border: 1px solid var(--soc-border);
  border-radius: 8px;
  color: var(--soc-text-muted);
  background: rgba(255, 255, 255, 0.62);
}
.pdf-fallback p {
  margin: 0;
  line-height: 1.7;
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
  .export-preview-head {
    align-items: flex-start;
    flex-direction: column;
  }
  .pdf-preview-frame {
    height: 520px;
  }
}
</style>
