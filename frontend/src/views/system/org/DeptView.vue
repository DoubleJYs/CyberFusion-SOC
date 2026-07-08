<template>
  <div class="page-shell">
    <SystemGovernanceHero
      title="部门管理"
      description="将资产、事件、漏洞和工单绑定到组织单元，形成可审计的数据权限视图。"
      scope="组织与数据域"
      :total="deptCount"
      :summary="deptSummary"
    >
      <template #action>
        <el-button v-permission="'system:dept:create'" type="primary" :icon="Plus" @click="openCreate()">新增部门</el-button>
      </template>
    </SystemGovernanceHero>
    <SearchPanel :model-value="query" @search="loadData" @reset="reset">
      <el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="部门名称 / 编码" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width: 140px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-form-item>
    </SearchPanel>
    <el-card shadow="never">
      <template #header>
        <div class="governance-table-header">
          <div>
            <strong>组织树</strong>
            <span>部门层级、负责人、联系电话和启停状态</span>
          </div>
          <el-tag effect="plain">数据域</el-tag>
        </div>
      </template>
      <el-table v-loading="loading" :data="rows" row-key="id" default-expand-all empty-text="暂无部门数据">
        <el-table-column prop="deptName" label="部门名称" min-width="180" />
        <el-table-column prop="deptCode" label="部门编码" min-width="140" />
        <el-table-column prop="leader" label="负责人" width="120" />
        <el-table-column prop="phone" label="联系电话" width="140" />
        <el-table-column prop="sort" label="排序" width="90" />
        <el-table-column label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column label="操作" width="210">
          <template #default="{ row }">
            <el-button v-permission="'system:dept:create'" link type="primary" @click="openCreate(row)">新增下级</el-button>
            <el-button v-permission="'system:dept:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="'system:dept:delete'" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <FormDialog v-model="dialogVisible" :title="editing ? '编辑部门' : '新增部门'" :confirm-loading="saving" @confirm="save">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="上级部门">
          <el-tree-select v-model="form.parentId" :data="deptOptions" check-strictly clearable :props="{ label: 'deptName', value: 'id', children: 'children' }" />
        </el-form-item>
        <el-form-item label="部门名称" prop="deptName"><el-input v-model="form.deptName" /></el-form-item>
        <el-form-item label="部门编码" prop="deptCode"><el-input v-model="form.deptCode" /></el-form-item>
        <el-form-item label="负责人"><el-input v-model="form.leader" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="enabledSwitch" class="form-switch" inline-prompt active-text="启" inactive-text="停" /></el-form-item>
      </el-form>
    </FormDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import SearchPanel from '@/components/SearchPanel/SearchPanel.vue'
import FormDialog from '@/components/FormDialog/FormDialog.vue'
import SystemGovernanceHero from '@/components/SystemGovernanceHero/SystemGovernanceHero.vue'
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import { createDept, deleteDept, fetchDepts, updateDept, type DeptForm, type DeptQuery } from '@/api/org'
import type { DeptRecord } from '@/types/system'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const rows = ref<DeptRecord[]>([])
const deptOptions = ref<DeptRecord[]>([])
const editing = ref<DeptRecord | null>(null)
const formRef = ref<FormInstance>()
const query = reactive<DeptQuery>({ keyword: '', status: undefined })
const form = reactive<DeptForm>({ parentId: 0, deptName: '', deptCode: '', leader: '', phone: '', sort: 0, status: 1 })
const rules: FormRules = {
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  deptCode: [{ required: true, message: '请输入部门编码', trigger: 'blur' }],
}
const enabledSwitch = computed({
  get: () => form.status === 1,
  set: (value: boolean) => { form.status = value ? 1 : 0 },
})
const flatRows = computed(() => flattenDepts(rows.value))
const deptCount = computed(() => flatRows.value.length)
const deptSummary = computed(() => [
  { label: '部门节点', value: deptCount.value, hint: '当前组织树' },
  { label: '启用部门', value: flatRows.value.filter((row) => row.status === 1).length, hint: '进入数据权限' },
  { label: '顶层部门', value: rows.value.length, hint: '一级责任域' },
])

function flattenDepts(items: DeptRecord[]): DeptRecord[] {
  return items.flatMap((item) => [item, ...flattenDepts(item.children || [])])
}

async function loadData() {
  loading.value = true
  try {
    rows.value = await fetchDepts(query)
    deptOptions.value = await fetchDepts()
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(query, { keyword: '', status: undefined })
  loadData()
}

function openCreate(parent?: DeptRecord) {
  editing.value = null
  Object.assign(form, { parentId: parent?.id || 0, deptName: '', deptCode: '', leader: '', phone: '', sort: 0, status: 1 })
  dialogVisible.value = true
}

function openEdit(row: DeptRecord) {
  editing.value = row
  Object.assign(form, { parentId: row.parentId, deptName: row.deptName, deptCode: row.deptCode, leader: row.leader || '', phone: row.phone || '', sort: row.sort, status: row.status })
  dialogVisible.value = true
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    if (editing.value) await updateDept(editing.value.id, form)
    else await createDept(form)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

async function remove(row: DeptRecord) {
  await ElMessageBox.confirm(`确认删除部门 ${row.deptName}？`, '删除部门', { type: 'warning' })
  await deleteDept(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>
