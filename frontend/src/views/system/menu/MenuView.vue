<template>
  <div class="page-shell">
    <SystemGovernanceHero
      title="菜单管理"
      description="管理 CyberFusion 统一门户导航，保障核心接入模块、处置流程和平台治理功能清晰分组。"
      scope="信息架构"
      :total="menuCount"
      :summary="menuSummary"
    >
      <template #action>
        <el-button v-permission="'system:menu:create'" type="primary" :icon="Plus" @click="openCreate()">新增菜单</el-button>
      </template>
    </SystemGovernanceHero>
    <el-card shadow="never">
      <template #header>
        <div class="governance-table-header">
          <div>
            <strong>菜单与权限路由</strong>
            <span>前端路由、权限标识、侧边栏展示结构和启停状态</span>
          </div>
          <el-tag effect="plain">导航治理</el-tag>
        </div>
      </template>
      <el-table v-loading="loading" :data="rows" row-key="id" default-expand-all empty-text="暂无菜单数据">
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="type" label="类型" width="110"><template #default="{ row }"><el-tag>{{ row.type }}</el-tag></template></el-table-column>
        <el-table-column prop="path" label="路由" min-width="160" />
        <el-table-column prop="permission" label="权限标识" min-width="180" />
        <el-table-column prop="sort" label="排序" width="90" />
        <el-table-column prop="status" label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:menu:create'" link type="primary" @click="openCreate(row)">新增下级</el-button>
            <el-button v-permission="'system:menu:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="'system:menu:delete'" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑菜单' : '新增菜单'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="父级 ID"><el-input-number v-model="form.parentId" :min="0" /></el-form-item>
        <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型" prop="type"><el-segmented v-model="form.type" :options="['directory', 'menu', 'button']" /></el-form-item>
        <el-form-item label="路由"><el-input v-model="form.path" /></el-form-item>
        <el-form-item label="组件"><el-input v-model="form.component" /></el-form-item>
        <el-form-item label="图标"><el-input v-model="form.icon" /></el-form-item>
        <el-form-item label="权限"><el-input v-model="form.permission" /></el-form-item>
        <el-form-item label="排序" prop="sort"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
        <el-form-item label="可见"><el-switch v-model="form.visibleBool" class="form-switch" inline-prompt active-text="显" inactive-text="隐" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" class="form-switch" inline-prompt active-text="启" inactive-text="停" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import SystemGovernanceHero from '@/components/SystemGovernanceHero/SystemGovernanceHero.vue'
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import { createMenu, deleteMenu, fetchMenus, updateMenu } from '@/api/menu'
import type { MenuItem } from '@/types/system'

const loading = ref(false)
const rows = ref<MenuItem[]>([])
const dialogVisible = ref(false)
const editing = ref<MenuItem | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({ parentId: 0, name: '', type: 'menu', path: '', component: '', icon: '', permission: '', sort: 0, visibleBool: true, enabled: true })
const rules: FormRules = {
  name: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
  sort: [{ required: true, message: '请输入排序', trigger: 'change' }],
}
const flatMenus = computed(() => flattenMenus(rows.value))
const menuCount = computed(() => flatMenus.value.length)
const menuSummary = computed(() => [
  { label: '菜单节点', value: menuCount.value, hint: '当前导航树' },
  { label: '页面路由', value: flatMenus.value.filter((row) => row.type === 'menu').length, hint: '可访问页面' },
  { label: '权限按钮', value: flatMenus.value.filter((row) => row.type === 'button').length, hint: '细粒度操作' },
])

function flattenMenus(items: MenuItem[]): MenuItem[] {
  return items.flatMap((item) => [item, ...flattenMenus(item.children || [])])
}

async function loadData() {
  loading.value = true
  try {
    rows.value = await fetchMenus()
  } finally {
    loading.value = false
  }
}

function openCreate(parent?: MenuItem) {
  editing.value = null
  Object.assign(form, { parentId: parent?.id || 0, name: '', type: 'menu', path: '', component: '', icon: '', permission: '', sort: 0, visibleBool: true, enabled: true })
  dialogVisible.value = true
}

function openEdit(row: MenuItem) {
  editing.value = row
  Object.assign(form, { parentId: row.parentId, name: row.name, type: row.type, path: row.path, component: row.component, icon: row.icon, permission: row.permission, sort: row.sort, visibleBool: row.visible === 1, enabled: row.status === 1 })
  dialogVisible.value = true
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  const payload = { parentId: form.parentId, name: form.name, type: form.type as MenuItem['type'], path: form.path, component: form.component, icon: form.icon, permission: form.permission, sort: form.sort, visible: form.visibleBool ? 1 : 0, status: form.enabled ? 1 : 0 }
  if (editing.value) await updateMenu(editing.value.id, payload)
  else await createMenu(payload)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function remove(row: MenuItem) {
  await ElMessageBox.confirm(`确认删除 ${row.name}？`, '删除确认', { type: 'warning' })
  await deleteMenu(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>
