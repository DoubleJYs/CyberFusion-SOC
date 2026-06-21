<template>
  <div class="page-shell">
    <div class="tool-row page-actions">
      <el-button v-if="canCreateConfig" v-permission="'system:config:create'" type="primary" :icon="Plus" @click="openCreate">新增参数</el-button>
    </div>
    <SearchPanel :model-value="query" :loading="loading" @search="loadData" @reset="reset">
      <el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="键名 / 名称" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width: 140px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-form-item>
      <template #advanced>
        <el-form-item label="分组"><el-input v-model="query.groupCode" clearable placeholder="site / security" /></el-form-item>
      </template>
    </SearchPanel>
    <el-card shadow="never">
      <DataTable
        :data="rows"
        :columns="columns"
        :loading="loading"
        empty-text="暂无参数配置"
        :pagination="{ pageNum: query.pageNum, pageSize: query.pageSize, total }"
        @page-change="handlePageChange"
        @size-change="handleSizeChange"
      >
        <template #group="{ row }"><el-tag type="primary">{{ row.groupCode }}</el-tag></template>
        <template #editable="{ row }"><StatusTag :value="row.editable" :options="editableOptions" /></template>
        <template #status="{ row }"><StatusTag :value="row.status" /></template>
        <template #operation="{ row }">
          <el-button v-permission="'system:config:update'" link type="primary" :disabled="row.editable === 0" @click="openEdit(row)">编辑</el-button>
          <el-button v-permission="'system:config:delete'" link type="danger" :disabled="row.editable === 0" @click="remove(row)">删除</el-button>
        </template>
      </DataTable>
    </el-card>
    <FormDialog v-model="dialogVisible" :title="editing ? '编辑参数' : '新增参数'" :loading="saving" @submit="save">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="参数键" prop="configKey"><el-input v-model="form.configKey" placeholder="site.title" /></el-form-item>
        <el-form-item label="参数名称" prop="configName"><el-input v-model="form.configName" /></el-form-item>
        <el-form-item label="分组" prop="groupCode"><el-input v-model="form.groupCode" placeholder="site / security / file" /></el-form-item>
        <el-form-item label="值类型" prop="valueType">
          <el-select v-model="form.valueType">
            <el-option label="字符串" value="string" />
            <el-option label="数字" value="number" />
            <el-option label="布尔" value="boolean" />
            <el-option label="JSON" value="json" />
          </el-select>
        </el-form-item>
        <el-form-item label="参数值" prop="configValue">
          <el-input v-model="form.configValue" :type="form.valueType === 'json' ? 'textarea' : 'text'" :rows="4" />
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="enabledSwitch" class="form-switch" inline-prompt active-text="启" inactive-text="停" /></el-form-item>
        <el-form-item label="可编辑"><el-switch v-model="editableSwitch" class="form-switch" inline-prompt active-text="可" inactive-text="锁" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
    </FormDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import SearchPanel from '@/components/SearchPanel/SearchPanel.vue'
import DataTable, { type DataTableColumn } from '@/components/DataTable/DataTable.vue'
import FormDialog from '@/components/FormDialog/FormDialog.vue'
import StatusTag, { type StatusOption } from '@/components/StatusTag/StatusTag.vue'
import { createConfig, deleteConfig, fetchConfigs, updateConfig, type ConfigForm, type ConfigQuery } from '@/api/config'
import type { ConfigRecord } from '@/types/system'
import { usePermissionAccess } from '@/utils/permission'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const rows = ref<ConfigRecord[]>([])
const total = ref(0)
const editing = ref<ConfigRecord | null>(null)
const formRef = ref<FormInstance>()
const { hasPermission } = usePermissionAccess()
const query = reactive<ConfigQuery>({ pageNum: 1, pageSize: 10, keyword: '', groupCode: '', status: undefined })
const form = reactive<ConfigForm>({ configKey: '', configName: '', configValue: '', valueType: 'string', groupCode: 'system', editable: 1, status: 1, remark: '' })
const columns: DataTableColumn[] = [
  { prop: 'configKey', label: '参数键', minWidth: 190, showOverflowTooltip: true },
  { prop: 'configName', label: '参数名称', minWidth: 150 },
  { label: '分组', width: 120, slot: 'group' },
  { prop: 'valueType', label: '类型', width: 100 },
  { prop: 'configValue', label: '参数值', minWidth: 180, showOverflowTooltip: true },
  { label: '可编辑', width: 100, slot: 'editable' },
  { label: '状态', width: 100, slot: 'status' },
]
const editableOptions: StatusOption[] = [
  { value: 1, label: '可编辑', type: 'success' },
  { value: 0, label: '内置', type: 'info' },
]
const rules: FormRules = {
  configKey: [{ required: true, message: '请输入参数键', trigger: 'blur' }],
  configName: [{ required: true, message: '请输入参数名称', trigger: 'blur' }],
  configValue: [{ required: true, message: '请输入参数值', trigger: 'blur' }],
  valueType: [{ required: true, message: '请选择值类型', trigger: 'change' }],
  groupCode: [{ required: true, message: '请输入分组', trigger: 'blur' }],
}
const enabledSwitch = computed({
  get: () => form.status === 1,
  set: (value: boolean) => { form.status = value ? 1 : 0 },
})
const editableSwitch = computed({
  get: () => form.editable === 1,
  set: (value: boolean) => { form.editable = value ? 1 : 0 },
})
const canCreateConfig = computed(() => hasPermission('system:config:create'))

async function loadData() {
  loading.value = true
  try {
    const page = await fetchConfigs(query)
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(query, { pageNum: 1, keyword: '', groupCode: '', status: undefined })
  loadData()
}

function handlePageChange(page: number) {
  query.pageNum = page
  loadData()
}

function handleSizeChange(size: number) {
  query.pageNum = 1
  query.pageSize = size
  loadData()
}

function openCreate() {
  editing.value = null
  Object.assign(form, { configKey: '', configName: '', configValue: '', valueType: 'string', groupCode: 'system', editable: 1, status: 1, remark: '' })
  dialogVisible.value = true
}

function openEdit(row: ConfigRecord) {
  editing.value = row
  Object.assign(form, {
    configKey: row.configKey,
    configName: row.configName,
    configValue: row.configValue,
    valueType: row.valueType,
    groupCode: row.groupCode,
    editable: row.editable,
    status: row.status,
    remark: row.remark || '',
  })
  dialogVisible.value = true
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    if (editing.value) await updateConfig(editing.value.id, form)
    else await createConfig(form)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

async function remove(row: ConfigRecord) {
  await ElMessageBox.confirm(`确认删除参数 ${row.configKey}？`, '删除参数', { type: 'warning' })
  await deleteConfig(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>
