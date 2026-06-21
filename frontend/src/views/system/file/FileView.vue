<template>
  <div class="page-shell">
    <SystemGovernanceHero
      title="文件管理"
      description="管理导入模板、报表附件和处置证据文件，上传内容只进入授权文件链路并保留审计记录。"
      scope="上传与证据"
      :total="total"
      :summary="fileSummary"
    >
      <template #action>
        <UploadButton v-permission="'system:file:upload'" biz-type="system_file" @success="loadData" />
      </template>
    </SystemGovernanceHero>
    <SearchPanel :model-value="query" @search="loadData" @reset="reset">
      <el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="文件名 / MD5" /></el-form-item>
      <el-form-item label="业务类型"><el-input v-model="query.bizType" clearable placeholder="bizType" /></el-form-item>
    </SearchPanel>
    <el-card shadow="never">
      <template #header>
        <div class="governance-table-header">
          <div>
            <strong>文件清单</strong>
            <span>文件名、MD5、业务类型、上传人和上传时间</span>
          </div>
          <el-tag effect="plain">证据文件</el-tag>
        </div>
      </template>
      <el-table v-loading="loading" :data="rows" empty-text="暂无文件">
        <el-table-column prop="originalName" label="文件名" min-width="220" />
        <el-table-column prop="fileExt" label="类型" width="90" />
        <el-table-column label="大小" width="110">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="bizType" label="业务类型" width="130" />
        <el-table-column prop="uploaderName" label="上传人" width="120" />
        <el-table-column prop="createdAt" label="上传时间" width="180" />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:file:download'" link type="primary" @click="openPreview(row)">预览</el-button>
            <el-button v-permission="'system:file:download'" link type="primary" @click="download(row)">下载</el-button>
            <el-button v-permission="'system:file:delete'" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" layout="total, prev, pager, next" :total="total" @current-change="loadData" />
    </el-card>
    <el-drawer v-model="previewVisible" title="文件预览" size="420px">
      <FilePreview :file="previewFile" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SearchPanel from '@/components/SearchPanel/SearchPanel.vue'
import SystemGovernanceHero from '@/components/SystemGovernanceHero/SystemGovernanceHero.vue'
import UploadButton from '@/components/UploadButton/UploadButton.vue'
import FilePreview from '@/components/FilePreview/FilePreview.vue'
import { deleteFile, downloadFileBlob, fetchFiles, saveBlob } from '@/api/file'
import type { SysFileRecord } from '@/types/file'

const loading = ref(false)
const rows = ref<SysFileRecord[]>([])
const total = ref(0)
const previewVisible = ref(false)
const previewFile = ref<SysFileRecord | null>(null)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', bizType: '' })
const fileSummary = computed(() => [
  { label: '当前页文件', value: rows.value.length, hint: '已加载结果' },
  { label: '业务类型', value: new Set(rows.value.map((row) => row.bizType).filter(Boolean)).size, hint: '来源分类' },
  { label: '合计大小', value: formatSize(rows.value.reduce((sum, row) => sum + row.fileSize, 0)), hint: '当前页' },
])

async function loadData() {
  loading.value = true
  try {
    const page = await fetchFiles(query)
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(query, { pageNum: 1, keyword: '', bizType: '' })
  loadData()
}

function openPreview(row: SysFileRecord) {
  previewFile.value = row
  previewVisible.value = true
}

async function download(row: SysFileRecord) {
  const blob = await downloadFileBlob(row.id)
  saveBlob(blob, row.originalName)
}

async function remove(row: SysFileRecord) {
  await ElMessageBox.confirm(`确认删除文件 ${row.originalName}？`, '删除文件', { type: 'warning' })
  await deleteFile(row.id)
  ElMessage.success('文件已删除')
  loadData()
}

function formatSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

onMounted(loadData)
</script>
