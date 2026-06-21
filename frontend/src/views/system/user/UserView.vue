<template>
  <div class="page-shell user-page">
    <SystemGovernanceHero
      title="用户管理"
      description="统一维护 SOC 登录身份、部门归属、岗位和角色分配，支撑告警、工单、报表的责任人闭环。"
      scope="账号与数据权限"
      :total="total"
      :summary="userSummary"
    >
      <template #action>
        <el-button v-permission="'system:user:create'" type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
      </template>
    </SystemGovernanceHero>
    <SearchPanel :model-value="query" @search="loadData" @reset="reset">
      <el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="账号 / 昵称" /></el-form-item>
      <el-form-item label="部门">
        <el-tree-select v-model="query.deptId" :data="deptOptions" clearable check-strictly placeholder="全部部门" :props="{ label: 'deptName', value: 'id', children: 'children' }" style="width: 180px" />
      </el-form-item>
      <el-form-item label="岗位">
        <el-select v-model="query.postId" clearable placeholder="全部岗位" style="width: 160px">
          <el-option v-for="post in postOptions" :key="post.id" :label="post.postName" :value="post.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width: 140px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-form-item>
    </SearchPanel>
    <el-card class="user-table-card" shadow="never">
      <template #header>
        <div class="governance-table-header">
          <div>
            <strong>账号清单</strong>
            <span>当前筛选 {{ total }} 个账号，展示身份、组织、角色和状态</span>
          </div>
          <el-tag effect="plain">RBAC</el-tag>
        </div>
      </template>
      <el-table v-loading="loading" class="user-table" :data="rows" empty-text="暂无用户数据" row-key="id">
        <el-table-column prop="username" label="账号" min-width="104" show-overflow-tooltip />
        <el-table-column prop="nickname" label="昵称" min-width="104" show-overflow-tooltip />
        <el-table-column prop="deptName" label="部门" min-width="120" show-overflow-tooltip />
        <el-table-column prop="postName" label="岗位" min-width="116" show-overflow-tooltip />
        <el-table-column prop="email" label="邮箱" min-width="158" show-overflow-tooltip />
        <el-table-column prop="roles" label="角色" width="118">
          <template #default="{ row }"><el-tag v-for="role in row.roles" :key="role" class="tag">{{ role }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="126">
          <template #default="{ row }">
            <span class="status-cell">
              <StatusTag :value="row.status" />
              <el-switch v-permission="'system:user:update'" :model-value="row.status === 1" inline-prompt active-text="启" inactive-text="停" @change="toggleStatus(row)" />
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="216" class-name="user-actions-column">
          <template #default="{ row }">
            <div class="user-row-actions">
              <el-button v-permission="'system:user:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button v-permission="'system:user:assign-role'" link type="primary" @click="openRoles(row)">分配角色</el-button>
              <el-button v-permission="'system:user:reset-password'" link type="warning" @click="resetPassword(row)">重置密码</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" layout="total, prev, pager, next" :total="total" @current-change="loadData" />
    </el-card>
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑用户' : '新增用户'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="账号" prop="username"><el-input v-model="form.username" :disabled="Boolean(editing)" /></el-form-item>
        <el-form-item v-if="!editing" label="密码" prop="password"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-form-item label="昵称" prop="nickname"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="手机"><el-input v-model="form.mobile" /></el-form-item>
        <el-form-item label="部门">
          <el-tree-select v-model="form.deptId" :data="deptOptions" clearable check-strictly placeholder="请选择部门" :props="{ label: 'deptName', value: 'id', children: 'children' }" />
        </el-form-item>
        <el-form-item label="岗位">
          <el-select v-model="form.postId" clearable placeholder="请选择岗位">
            <el-option v-for="post in postOptions" :key="post.id" :label="post.postName" :value="post.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.enabled" class="form-switch" inline-prompt active-text="启" inactive-text="停" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="roleDialogVisible" title="分配角色" width="460px">
      <el-checkbox-group v-model="selectedRoleIds">
        <el-checkbox v-for="role in roleOptions" :key="role.id" :label="role.id">{{ role.roleName }}</el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRoles">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import SearchPanel from '@/components/SearchPanel/SearchPanel.vue'
import SystemGovernanceHero from '@/components/SystemGovernanceHero/SystemGovernanceHero.vue'
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import { fetchDepts, fetchPosts } from '@/api/org'
import { assignUserRoles, changeUserStatus, createUser, fetchUsers, resetUserPassword, updateUser, type UserForm } from '@/api/user'
import { fetchRoles } from '@/api/role'
import type { DeptRecord, PostRecord, RoleRecord, UserRecord } from '@/types/system'

const loading = ref(false)
const rows = ref<UserRecord[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', deptId: undefined as number | undefined, postId: undefined as number | undefined, status: undefined as number | undefined })
const dialogVisible = ref(false)
const roleDialogVisible = ref(false)
const editing = ref<UserRecord | null>(null)
const roleTarget = ref<UserRecord | null>(null)
const deptOptions = ref<DeptRecord[]>([])
const postOptions = ref<PostRecord[]>([])
const roleOptions = ref<RoleRecord[]>([])
const selectedRoleIds = ref<number[]>([])
const formRef = ref<FormInstance>()
const form = reactive({
  username: '',
  password: '',
  nickname: '',
  email: '',
  mobile: '',
  deptId: undefined as number | undefined,
  postId: undefined as number | undefined,
  enabled: true,
})
const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
}
const userSummary = computed(() => [
  { label: '当前页账号', value: rows.value.length, hint: '已加载结果' },
  { label: '启用账号', value: rows.value.filter((row) => row.status === 1).length, hint: '可进入平台' },
  { label: '涉及部门', value: new Set(rows.value.map((row) => row.deptName).filter(Boolean)).size, hint: '数据权限边界' },
])

async function loadData() {
  loading.value = true
  try {
    const page = await fetchUsers(query)
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  const [depts, posts] = await Promise.all([
    fetchDepts({ status: 1 }),
    fetchPosts({ pageNum: 1, pageSize: 100, status: 1 }),
  ])
  deptOptions.value = depts
  postOptions.value = posts.records
}

function reset() {
  query.keyword = ''
  query.deptId = undefined
  query.postId = undefined
  query.status = undefined
  query.pageNum = 1
  loadData()
}

function openCreate() {
  editing.value = null
  Object.assign(form, { username: '', password: '', nickname: '', email: '', mobile: '', deptId: undefined, postId: undefined, enabled: true })
  dialogVisible.value = true
}

function openEdit(row: UserRecord) {
  editing.value = row
  Object.assign(form, {
    username: row.username,
    password: '',
    nickname: row.nickname,
    email: row.email,
    mobile: row.mobile,
    deptId: row.deptId,
    postId: row.postId,
    enabled: row.status === 1,
  })
  dialogVisible.value = true
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  const payload: UserForm = {
    username: form.username,
    password: form.password,
    nickname: form.nickname,
    email: form.email,
    mobile: form.mobile,
    deptId: form.deptId,
    postId: form.postId,
    status: form.enabled ? 1 : 0,
  }
  if (editing.value) await updateUser(editing.value.id, payload)
  else await createUser(payload)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function toggleStatus(row: UserRecord) {
  await changeUserStatus(row.id, row.status === 1 ? 0 : 1)
  ElMessage.success('状态已更新')
  loadData()
}

async function openRoles(row: UserRecord) {
  roleTarget.value = row
  selectedRoleIds.value = [...row.roleIds]
  roleOptions.value = (await fetchRoles({ pageNum: 1, pageSize: 100 })).records
  roleDialogVisible.value = true
}

async function saveRoles() {
  if (!roleTarget.value) return
  await assignUserRoles(roleTarget.value.id, selectedRoleIds.value)
  ElMessage.success('角色已更新')
  roleDialogVisible.value = false
  loadData()
}

async function resetPassword(row: UserRecord) {
  await ElMessageBox.confirm(`确认将 ${row.username} 的密码重置为 Admin@123456？`, '重置密码', { type: 'warning' })
  await resetUserPassword(row.id, 'Admin@123456')
  ElMessage.success('密码已重置')
}

onMounted(() => {
  loadOptions()
  loadData()
})
</script>

<style scoped>
.user-table-card {
  min-width: 0;
}

.user-table-card :deep(.el-card__body) {
  overflow-x: auto;
}

.user-table {
  min-width: 966px;
}

.user-table :deep(.el-table__cell .cell) {
  white-space: nowrap;
}

.user-row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: flex-start;
  white-space: nowrap;
}

:deep(.user-actions-column .cell) {
  overflow: visible;
}

.tag {
  margin-right: 4px;
}
</style>
