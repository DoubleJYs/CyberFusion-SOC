<template>
  <div class="page-shell">
    <SystemGovernanceHero
      title="角色管理"
      description="沉淀分析员、处置员、管理员等角色权限，控制核心接入、告警处置、工单和报表能力的可见范围。"
      scope="RBAC 策略"
      :total="total"
      :summary="roleSummary"
    >
      <template #action>
        <el-button v-permission="'system:role:create'" type="primary" :icon="Plus" @click="openCreate">新增角色</el-button>
      </template>
    </SystemGovernanceHero>
    <SearchPanel :model-value="query" @search="loadData" @reset="reset">
      <el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="角色名称 / 编码" /></el-form-item>
    </SearchPanel>
    <el-card shadow="never">
      <template #header>
        <div class="governance-table-header">
          <div>
            <strong>角色清单</strong>
            <span>角色编码、数据范围和菜单权限分配状态</span>
          </div>
          <el-tag effect="plain">权限边界</el-tag>
        </div>
      </template>
      <el-table v-loading="loading" :data="rows" empty-text="暂无角色数据">
        <el-table-column prop="roleName" label="角色名称" min-width="140" />
        <el-table-column prop="roleCode" label="角色编码" min-width="140" />
        <el-table-column prop="dataScope" label="数据范围" width="150">
          <template #default="{ row }">{{ dataScopeLabel(row.dataScope) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="150">
          <template #default="{ row }">
            <span class="status-cell">
              <StatusTag :value="row.status" />
              <el-switch v-permission="'system:role:update'" :model-value="row.status === 1" inline-prompt active-text="启" inactive-text="停" @change="toggleStatus(row)" />
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="170" />
        <el-table-column label="操作" width="210">
          <template #default="{ row }">
            <el-button v-permission="'system:role:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="'system:role:assign-menu'" link type="primary" @click="openMenu(row)">分配菜单</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" layout="total, prev, pager, next" :total="total" @current-change="loadData" />
    </el-card>
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑角色' : '新增角色'" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色名称" prop="roleName"><el-input v-model="form.roleName" /></el-form-item>
        <el-form-item label="角色编码" prop="roleCode"><el-input v-model="form.roleCode" /></el-form-item>
        <el-form-item label="数据范围" prop="dataScope">
          <el-select v-model="form.dataScope">
            <el-option label="本人" value="self" />
            <el-option label="本部门" value="dept" />
            <el-option label="本部门及下级" value="dept_tree" />
            <el-option label="全部" value="all" />
            <el-option label="自定义" value="custom" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.dataScope === 'custom'" label="自定义部门">
          <el-tree-select
            v-model="form.deptIds"
            :data="deptOptions"
            multiple
            check-strictly
            clearable
            collapse-tags
            collapse-tags-tooltip
            :props="{ label: 'deptName', value: 'id', children: 'children' }"
          />
        </el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.enabled" class="form-switch" inline-prompt active-text="启" inactive-text="停" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="menuDialogVisible" title="分配菜单权限" width="560px">
      <el-tree ref="menuTreeRef" :data="menus" show-checkbox node-key="id" :props="{ label: 'name', children: 'children' }" />
      <template #footer>
        <el-button @click="menuDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveMenus">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules, type TreeInstance } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import SearchPanel from '@/components/SearchPanel/SearchPanel.vue'
import SystemGovernanceHero from '@/components/SystemGovernanceHero/SystemGovernanceHero.vue'
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import { assignRoleMenus, changeRoleStatus, createRole, fetchRoles, updateRole } from '@/api/role'
import { fetchMenus } from '@/api/menu'
import { fetchDepts } from '@/api/org'
import type { DeptRecord, MenuItem, RoleRecord } from '@/types/system'

const loading = ref(false)
const rows = ref<RoleRecord[]>([])
const total = ref(0)
const menus = ref<MenuItem[]>([])
const deptOptions = ref<DeptRecord[]>([])
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' })
const dialogVisible = ref(false)
const menuDialogVisible = ref(false)
const editing = ref<RoleRecord | null>(null)
const menuTarget = ref<RoleRecord | null>(null)
const menuTreeRef = ref<TreeInstance>()
const formRef = ref<FormInstance>()
const form = reactive({ roleName: '', roleCode: '', dataScope: 'self', deptIds: [] as number[], enabled: true })
const rules: FormRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
}
const roleSummary = computed(() => [
  { label: '当前页角色', value: rows.value.length, hint: '已加载结果' },
  { label: '启用角色', value: rows.value.filter((row) => row.status === 1).length, hint: '可授权使用' },
  { label: '全局权限', value: rows.value.filter((row) => row.dataScope === 'all').length, hint: '需重点审计' },
])

async function loadData() {
  loading.value = true
  try {
    const page = await fetchRoles(query)
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

const dataScopeLabels: Record<string, string> = {
  self: '本人',
  dept: '本部门',
  dept_tree: '本部门及下级',
  all: '全部',
  custom: '自定义',
}

function dataScopeLabel(value: string) {
  return dataScopeLabels[value] || value || '-'
}

function reset() {
  query.keyword = ''
  query.pageNum = 1
  loadData()
}

function openCreate() {
  editing.value = null
  Object.assign(form, { roleName: '', roleCode: '', dataScope: 'self', deptIds: [], enabled: true })
  loadDeptOptions()
  dialogVisible.value = true
}

function openEdit(row: RoleRecord) {
  editing.value = row
  Object.assign(form, { roleName: row.roleName, roleCode: row.roleCode, dataScope: row.dataScope || 'self', deptIds: row.deptIds || [], enabled: row.status === 1 })
  loadDeptOptions()
  dialogVisible.value = true
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  const payload = { roleName: form.roleName, roleCode: form.roleCode, dataScope: form.dataScope, deptIds: form.deptIds, status: form.enabled ? 1 : 0 }
  if (editing.value) await updateRole(editing.value.id, payload)
  else await createRole(payload)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function loadDeptOptions() {
  if (!deptOptions.value.length) {
    deptOptions.value = await fetchDepts()
  }
}

async function toggleStatus(row: RoleRecord) {
  await changeRoleStatus(row.id, row.status === 1 ? 0 : 1)
  ElMessage.success('状态已更新')
  loadData()
}

async function openMenu(row: RoleRecord) {
  menuTarget.value = row
  menus.value = await fetchMenus()
  menuDialogVisible.value = true
  await nextTick()
  menuTreeRef.value?.setCheckedKeys(row.menuIds)
}

async function saveMenus() {
  if (!menuTarget.value) return
  await assignRoleMenus(menuTarget.value.id, menuTreeRef.value?.getCheckedKeys(false) as number[])
  ElMessage.success('菜单权限已更新')
  menuDialogVisible.value = false
  loadData()
}

onMounted(loadData)
</script>
