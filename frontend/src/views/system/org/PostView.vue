<template>
  <div class="page-shell">
    <SystemGovernanceHero
      title="岗位管理"
      description="维护安全值守、研判、响应和平台治理岗位，辅助排班、责任追踪与工单分派。"
      scope="职责模型"
      :total="total"
      :summary="postSummary"
    >
      <template #action>
        <el-button v-permission="'system:post:create'" type="primary" :icon="Plus" @click="openCreate">新增岗位</el-button>
      </template>
    </SystemGovernanceHero>
    <SearchPanel :model-value="query" @search="loadData" @reset="reset">
      <el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="岗位名称 / 编码" /></el-form-item>
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
            <strong>岗位清单</strong>
            <span>岗位名称、编码、排序、状态和职责备注</span>
          </div>
          <el-tag effect="plain">Duty</el-tag>
        </div>
      </template>
      <el-table v-loading="loading" :data="rows" empty-text="暂无岗位数据">
        <el-table-column prop="postName" label="岗位名称" min-width="160" />
        <el-table-column prop="postCode" label="岗位编码" min-width="140" />
        <el-table-column prop="sort" label="排序" width="90" />
        <el-table-column label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:post:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="'system:post:delete'" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" layout="total, prev, pager, next" :total="total" @current-change="loadData" />
    </el-card>
    <FormDialog v-model="dialogVisible" :title="editing ? '编辑岗位' : '新增岗位'" :confirm-loading="saving" @confirm="save">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="岗位名称" prop="postName"><el-input v-model="form.postName" /></el-form-item>
        <el-form-item label="岗位编码" prop="postCode"><el-input v-model="form.postCode" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="enabledSwitch" class="form-switch" inline-prompt active-text="启" inactive-text="停" /></el-form-item>
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
import FormDialog from '@/components/FormDialog/FormDialog.vue'
import SystemGovernanceHero from '@/components/SystemGovernanceHero/SystemGovernanceHero.vue'
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import { createPost, deletePost, fetchPosts, updatePost, type PostForm } from '@/api/org'
import type { PostRecord } from '@/types/system'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const rows = ref<PostRecord[]>([])
const total = ref(0)
const editing = ref<PostRecord | null>(null)
const formRef = ref<FormInstance>()
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: undefined as number | undefined })
const form = reactive<PostForm>({ postCode: '', postName: '', sort: 0, status: 1, remark: '' })
const rules: FormRules = {
  postName: [{ required: true, message: '请输入岗位名称', trigger: 'blur' }],
  postCode: [{ required: true, message: '请输入岗位编码', trigger: 'blur' }],
}
const enabledSwitch = computed({
  get: () => form.status === 1,
  set: (value: boolean) => { form.status = value ? 1 : 0 },
})
const postSummary = computed(() => [
  { label: '当前页岗位', value: rows.value.length, hint: '已加载结果' },
  { label: '启用岗位', value: rows.value.filter((row) => row.status === 1).length, hint: '可参与分派' },
  { label: '停用岗位', value: rows.value.filter((row) => row.status !== 1).length, hint: '保留不分派' },
])

async function loadData() {
  loading.value = true
  try {
    const page = await fetchPosts(query)
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(query, { pageNum: 1, keyword: '', status: undefined })
  loadData()
}

function openCreate() {
  editing.value = null
  Object.assign(form, { postCode: '', postName: '', sort: 0, status: 1, remark: '' })
  dialogVisible.value = true
}

function openEdit(row: PostRecord) {
  editing.value = row
  Object.assign(form, { postCode: row.postCode, postName: row.postName, sort: row.sort, status: row.status, remark: row.remark || '' })
  dialogVisible.value = true
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    if (editing.value) await updatePost(editing.value.id, form)
    else await createPost(form)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

async function remove(row: PostRecord) {
  await ElMessageBox.confirm(`确认删除岗位 ${row.postName}？`, '删除岗位', { type: 'warning' })
  await deletePost(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>
