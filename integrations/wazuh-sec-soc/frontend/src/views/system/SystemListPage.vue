<template>
  <div class="page-shell">
    <div class="page-title-row">
      <h1>{{ title }}</h1>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增</el-button>
    </div>
    <SearchPanel :model-value="filters" @search="loadData" @reset="reset">
      <el-form-item label="关键词">
        <el-input v-model="filters.keyword" clearable placeholder="名称 / 编码" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="filters.status" clearable placeholder="全部" style="width: 140px">
          <el-option label="启用" value="enabled" />
          <el-option label="停用" value="disabled" />
        </el-select>
      </el-form-item>
    </SearchPanel>
    <DataTable
      :data="pagedRows"
      :columns="columns"
      :loading="loading"
      :error="error"
      :pagination="{ pageNum: current, pageSize, total: filteredRows.length }"
      @page-change="current = $event"
      @size-change="handleSizeChange"
    >
      <template #status="{ row }">
        <el-tag :type="row.status === 'enabled' ? 'success' : 'info'">{{ row.status === 'enabled' ? '启用' : '停用' }}</el-tag>
      </template>
      <template #operation="{ row }">
        <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        <el-button link type="danger" @click="removeRow(row)">删除</el-button>
      </template>
    </DataTable>
    <el-dialog v-model="dialogVisible" :title="editingRow ? '编辑' : '新增'" width="460px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="编码"><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import SearchPanel from '@/components/SearchPanel/SearchPanel.vue'
import DataTable, { type DataTableColumn } from '@/components/DataTable/DataTable.vue'
import type { TableRecord } from '@/types/system'

const props = defineProps<{
  title: string
  seed: TableRecord[]
}>()

const loading = ref(false)
const error = ref('')
const current = ref(1)
const pageSize = ref(10)
const rows = ref<TableRecord[]>([...props.seed])
const dialogVisible = ref(false)
const editingRow = ref<TableRecord | null>(null)
const filters = reactive({ keyword: '', status: '' })
const form = reactive({ name: '', code: '', enabled: true })
const columns: DataTableColumn[] = [
  { prop: 'name', label: '名称', minWidth: 160 },
  { prop: 'code', label: '编码', minWidth: 150 },
  { prop: 'owner', label: '维护人', width: 120 },
  { prop: 'updatedAt', label: '更新时间', width: 180 },
  { label: '状态', width: 100, slot: 'status' },
]

const filteredRows = computed(() => rows.value.filter((row) => {
  const keywordMatched = !filters.keyword || row.name.includes(filters.keyword) || row.code.includes(filters.keyword)
  const statusMatched = !filters.status || row.status === filters.status
  return keywordMatched && statusMatched
}))

const pagedRows = computed(() => {
  const start = (current.value - 1) * pageSize.value
  return filteredRows.value.slice(start, start + pageSize.value)
})

async function loadData() {
  loading.value = true
  error.value = ''
  await new Promise((resolve) => window.setTimeout(resolve, 260))
  loading.value = false
}

function reset() {
  filters.keyword = ''
  filters.status = ''
  current.value = 1
  loadData()
}

function handleSizeChange(size: number) {
  pageSize.value = size
  current.value = 1
}

function openCreate() {
  editingRow.value = null
  form.name = ''
  form.code = ''
  form.enabled = true
  dialogVisible.value = true
}

function openEdit(row: TableRecord) {
  editingRow.value = row
  form.name = row.name
  form.code = row.code
  form.enabled = row.status === 'enabled'
  dialogVisible.value = true
}

function save() {
  if (!form.name || !form.code) {
    ElMessage.error('请填写名称和编码')
    return
  }
  if (editingRow.value) {
    Object.assign(editingRow.value, { name: form.name, code: form.code, status: form.enabled ? 'enabled' : 'disabled' })
  } else {
    rows.value.unshift({ id: Date.now(), name: form.name, code: form.code, status: form.enabled ? 'enabled' : 'disabled', owner: 'admin', updatedAt: new Date().toLocaleString('zh-CN', { hour12: false }) })
  }
  dialogVisible.value = false
  ElMessage.success('保存成功')
}

async function removeRow(row: TableRecord) {
  await ElMessageBox.confirm(`确认删除 ${row.name}？`, '删除确认', { type: 'warning' })
  rows.value = rows.value.filter((item) => item.id !== row.id)
  ElMessage.success('删除成功')
}
</script>
