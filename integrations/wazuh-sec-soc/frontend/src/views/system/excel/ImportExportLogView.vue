<template>
  <div class="page-shell">
    <div class="tool-row page-actions">
      <div class="actions">
        <el-button v-permission="'system:excel:import'" type="primary" :icon="Upload" @click="importVisible = true">导入演示</el-button>
        <ExportButton v-permission="'system:excel:export'" :action="exportLogs" />
      </div>
    </div>
    <SearchPanel :model-value="query" @search="loadData" @reset="reset">
      <el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="任务号 / 操作人" /></el-form-item>
      <el-form-item label="模板"><el-input v-model="query.templateCode" clearable placeholder="templateCode" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width: 140px">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAIL" />
          <el-option label="部分失败" value="PARTIAL_FAIL" />
        </el-select>
      </el-form-item>
    </SearchPanel>
    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" empty-text="暂无导入导出日志">
        <el-table-column prop="taskNo" label="任务编号" min-width="190" />
        <el-table-column label="类型" width="100"><template #default="{ row }"><StatusTag :value="row.taskType" /></template></el-table-column>
        <el-table-column prop="templateCode" label="模板编码" min-width="170" />
        <el-table-column prop="totalCount" label="总数" width="80" />
        <el-table-column prop="successCount" label="成功" width="80" />
        <el-table-column prop="failCount" label="失败" width="80" />
        <el-table-column label="状态" width="110"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="120" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" layout="total, prev, pager, next" :total="total" @current-change="loadData" />
    </el-card>
    <ImportDialog v-model="importVisible" template-code="user-demo-import-template" title="用户演示 Excel 导入" @imported="loadData" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Upload } from '@element-plus/icons-vue'
import SearchPanel from '@/components/SearchPanel/SearchPanel.vue'
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import ImportDialog from '@/components/ImportDialog/ImportDialog.vue'
import ExportButton from '@/components/ExportButton/ExportButton.vue'
import { exportImportExportLogs, fetchImportExportLogs } from '@/api/excel'
import type { ImportExportLogRecord } from '@/types/excel'

const loading = ref(false)
const rows = ref<ImportExportLogRecord[]>([])
const total = ref(0)
const importVisible = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', templateCode: '', status: '' })

async function loadData() {
  loading.value = true
  try {
    const page = await fetchImportExportLogs(query)
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(query, { pageNum: 1, keyword: '', templateCode: '', status: '' })
  loadData()
}

async function exportLogs() {
  await exportImportExportLogs(query)
}

onMounted(loadData)
</script>

<style scoped>
.actions {
  display: flex;
  gap: 8px;
}
</style>
