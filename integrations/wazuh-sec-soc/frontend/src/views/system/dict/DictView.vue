<template>
  <div class="dict-layout">
    <el-card shadow="never" class="type-card">
      <template #header><div class="card-header"><span>字典类型</span><el-button v-permission="'system:dict:create'" type="primary" :icon="Plus" @click="openType()">新增</el-button></div></template>
      <el-table v-loading="typeLoading" :data="types" highlight-current-row empty-text="暂无字典类型" @current-change="selectType">
        <el-table-column prop="dictName" label="名称" />
        <el-table-column prop="dictCode" label="编码" />
        <el-table-column label="操作" width="120"><template #default="{ row }"><el-button v-permission="'system:dict:update'" link type="primary" @click.stop="openType(row)">编辑</el-button><el-button v-permission="'system:dict:delete'" link type="danger" @click.stop="removeType(row)">删除</el-button></template></el-table-column>
      </el-table>
    </el-card>
    <el-card shadow="never" class="data-card">
      <template #header><div class="card-header"><span>字典数据</span><el-button v-permission="'system:dict:create'" type="primary" :icon="Plus" :disabled="!currentType" @click="openData()">新增</el-button></div></template>
      <el-table v-loading="dataLoading" :data="dataRows" empty-text="请选择字典类型">
        <el-table-column prop="dictLabel" label="标签" />
        <el-table-column prop="dictValue" label="值" />
        <el-table-column prop="sortOrder" label="排序" width="90" />
        <el-table-column prop="status" label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column label="操作" width="130"><template #default="{ row }"><el-button v-permission="'system:dict:update'" link type="primary" @click="openData(row)">编辑</el-button><el-button v-permission="'system:dict:delete'" link type="danger" @click="removeData(row)">删除</el-button></template></el-table-column>
      </el-table>
    </el-card>
    <el-dialog v-model="typeDialog" title="字典类型" width="460px">
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="90px">
        <el-form-item label="名称" prop="dictName"><el-input v-model="typeForm.dictName" /></el-form-item>
        <el-form-item label="编码" prop="dictCode"><el-input v-model="typeForm.dictCode" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="typeForm.enabled" class="form-switch" inline-prompt active-text="启" inactive-text="停" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="typeDialog = false">取消</el-button><el-button type="primary" @click="saveTypeRow">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="dataDialog" title="字典数据" width="460px">
      <el-form ref="dataFormRef" :model="dataForm" :rules="dataRules" label-width="90px">
        <el-form-item label="标签" prop="dictLabel"><el-input v-model="dataForm.dictLabel" /></el-form-item>
        <el-form-item label="值" prop="dictValue"><el-input v-model="dataForm.dictValue" /></el-form-item>
        <el-form-item label="排序" prop="sortOrder"><el-input-number v-model="dataForm.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="dataForm.enabled" class="form-switch" inline-prompt active-text="启" inactive-text="停" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dataDialog = false">取消</el-button><el-button type="primary" @click="saveDataRow">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import { deleteDictData, deleteDictType, fetchDictData, fetchDictTypes, saveDictData, saveDictType } from '@/api/dict'
import type { DictDataRecord, DictTypeRecord } from '@/types/system'

const typeLoading = ref(false)
const dataLoading = ref(false)
const types = ref<DictTypeRecord[]>([])
const dataRows = ref<DictDataRecord[]>([])
const currentType = ref<DictTypeRecord | null>(null)
const editingType = ref<DictTypeRecord | null>(null)
const editingData = ref<DictDataRecord | null>(null)
const typeDialog = ref(false)
const dataDialog = ref(false)
const typeFormRef = ref<FormInstance>()
const dataFormRef = ref<FormInstance>()
const typeForm = reactive({ dictName: '', dictCode: '', enabled: true })
const dataForm = reactive({ dictLabel: '', dictValue: '', sortOrder: 0, enabled: true })
const typeRules: FormRules = {
  dictName: [{ required: true, message: '请输入字典名称', trigger: 'blur' }],
  dictCode: [{ required: true, message: '请输入字典编码', trigger: 'blur' }],
}
const dataRules: FormRules = {
  dictLabel: [{ required: true, message: '请输入字典标签', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入字典值', trigger: 'blur' }],
  sortOrder: [{ required: true, message: '请输入排序', trigger: 'change' }],
}

async function loadTypes() {
  typeLoading.value = true
  try {
    types.value = (await fetchDictTypes({ pageNum: 1, pageSize: 100 })).records
  } finally {
    typeLoading.value = false
  }
}

async function loadData() {
  if (!currentType.value) return
  dataLoading.value = true
  try {
    dataRows.value = (await fetchDictData({ pageNum: 1, pageSize: 100, dictTypeId: currentType.value.id })).records
  } finally {
    dataLoading.value = false
  }
}

function selectType(row?: DictTypeRecord) {
  currentType.value = row || null
  loadData()
}

function openType(row?: DictTypeRecord) {
  editingType.value = row || null
  Object.assign(typeForm, { dictName: row?.dictName || '', dictCode: row?.dictCode || '', enabled: row?.status !== 0 })
  typeDialog.value = true
}

async function saveTypeRow() {
  if (!(await typeFormRef.value?.validate().catch(() => false))) return
  await saveDictType({ dictName: typeForm.dictName, dictCode: typeForm.dictCode, status: typeForm.enabled ? 1 : 0 }, editingType.value?.id)
  ElMessage.success('保存成功')
  typeDialog.value = false
  loadTypes()
}

async function removeType(row: DictTypeRecord) {
  await ElMessageBox.confirm(`确认删除 ${row.dictName}？`, '删除确认', { type: 'warning' })
  await deleteDictType(row.id)
  loadTypes()
}

function openData(row?: DictDataRecord) {
  editingData.value = row || null
  Object.assign(dataForm, { dictLabel: row?.dictLabel || '', dictValue: row?.dictValue || '', sortOrder: row?.sortOrder || 0, enabled: row?.status !== 0 })
  dataDialog.value = true
}

async function saveDataRow() {
  if (!currentType.value) return
  if (!(await dataFormRef.value?.validate().catch(() => false))) return
  await saveDictData({ dictTypeId: currentType.value.id, dictLabel: dataForm.dictLabel, dictValue: dataForm.dictValue, sortOrder: dataForm.sortOrder, status: dataForm.enabled ? 1 : 0 }, editingData.value?.id)
  ElMessage.success('保存成功')
  dataDialog.value = false
  loadData()
}

async function removeData(row: DictDataRecord) {
  await ElMessageBox.confirm(`确认删除 ${row.dictLabel}？`, '删除确认', { type: 'warning' })
  await deleteDictData(row.id)
  loadData()
}

onMounted(loadTypes)
</script>

<style scoped>
.dict-layout {
  display: grid;
  grid-template-columns: minmax(320px, 0.8fr) minmax(0, 1.2fr);
  gap: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
@media (max-width: 1000px) {
  .dict-layout {
    grid-template-columns: 1fr;
  }
}
</style>
