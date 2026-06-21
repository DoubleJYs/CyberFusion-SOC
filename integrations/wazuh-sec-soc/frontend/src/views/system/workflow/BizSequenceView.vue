<template>
  <div class="page-shell">
    <div class="tool-row page-actions">
      <el-button v-permission="'system:sequence:create'" type="primary" :icon="Plus" @click="openCreate">新增规则</el-button>
    </div>
    <SearchPanel :model-value="query" @search="loadData" @reset="reset">
      <el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="编码 / 名称" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.enabled" clearable placeholder="全部" style="width: 140px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-form-item>
    </SearchPanel>
    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" empty-text="暂无编号规则">
        <el-table-column prop="sequenceCode" label="规则编码" min-width="150" />
        <el-table-column prop="sequenceName" label="规则名称" min-width="160" />
        <el-table-column prop="prefix" label="前缀" width="90" />
        <el-table-column prop="datePattern" label="日期格式" width="120" />
        <el-table-column prop="currentValue" label="当前值" width="100" />
        <el-table-column prop="resetPolicy" label="重置策略" width="110" />
        <el-table-column label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.enabled" /></template></el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:sequence:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="'system:sequence:generate'" link type="success" @click="generate(row)">生成</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" layout="total, prev, pager, next" :total="total" @current-change="loadData" />
    </el-card>
    <FormDialog v-model="dialogVisible" :title="editing ? '编辑编号规则' : '新增编号规则'" :confirm-loading="saving" @confirm="save">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="规则编码" prop="sequenceCode"><el-input v-model="form.sequenceCode" :disabled="Boolean(editing)" /></el-form-item>
        <el-form-item label="规则名称" prop="sequenceName"><el-input v-model="form.sequenceName" /></el-form-item>
        <el-form-item label="前缀"><el-input v-model="form.prefix" /></el-form-item>
        <el-form-item label="日期格式"><el-input v-model="form.datePattern" /></el-form-item>
        <el-form-item label="当前值"><el-input-number v-model="form.currentValue" :min="0" /></el-form-item>
        <el-form-item label="步长"><el-input-number v-model="form.step" :min="1" /></el-form-item>
        <el-form-item label="序列长度"><el-input-number v-model="form.length" :min="1" /></el-form-item>
        <el-form-item label="重置策略">
          <el-select v-model="form.resetPolicy">
            <el-option label="不重置" value="NEVER" />
            <el-option label="每日" value="DAILY" />
            <el-option label="每月" value="MONTHLY" />
            <el-option label="每年" value="YEARLY" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="enabledSwitch" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
    </FormDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import SearchPanel from '@/components/SearchPanel/SearchPanel.vue'
import FormDialog from '@/components/FormDialog/FormDialog.vue'
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import { createBizSequence, fetchBizSequences, generateBizNo, updateBizSequence } from '@/api/workflow'
import type { BizSequenceForm, BizSequenceRecord } from '@/types/workflow'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const rows = ref<BizSequenceRecord[]>([])
const total = ref(0)
const editing = ref<BizSequenceRecord | null>(null)
const formRef = ref<FormInstance>()
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', enabled: undefined as number | undefined })
const form = reactive<BizSequenceForm>({ sequenceCode: '', sequenceName: '', prefix: '', datePattern: 'yyyyMMdd', currentValue: 0, step: 1, length: 4, resetPolicy: 'DAILY', enabled: 1, remark: '' })
const rules: FormRules = {
  sequenceCode: [{ required: true, message: '请输入规则编码', trigger: 'blur' }],
  sequenceName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
}
const enabledSwitch = computed({
  get: () => form.enabled === 1,
  set: (value: boolean) => { form.enabled = value ? 1 : 0 },
})

async function loadData() {
  loading.value = true
  try {
    const page = await fetchBizSequences(query)
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(query, { pageNum: 1, keyword: '', enabled: undefined })
  loadData()
}

function openCreate() {
  editing.value = null
  Object.assign(form, { sequenceCode: '', sequenceName: '', prefix: '', datePattern: 'yyyyMMdd', currentValue: 0, step: 1, length: 4, resetPolicy: 'DAILY', enabled: 1, remark: '' })
  dialogVisible.value = true
}

function openEdit(row: BizSequenceRecord) {
  editing.value = row
  Object.assign(form, row)
  dialogVisible.value = true
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    if (editing.value) await updateBizSequence(editing.value.id, form)
    else await createBizSequence(form)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

async function generate(row: BizSequenceRecord) {
  const result = await generateBizNo(row.sequenceCode)
  ElMessage.success(`已生成: ${result.bizNo}`)
  loadData()
}

onMounted(loadData)
</script>
