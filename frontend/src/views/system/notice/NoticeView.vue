<template>
  <div class="page-shell">
    <SystemGovernanceHero
      title="通知公告"
      description="发布平台维护、版本更新和安全运营通知，让登录用户在统一门户内获取明确状态。"
      scope="平台通知"
      :total="total"
      :summary="noticeSummary"
    >
      <template #action>
        <el-button v-permission="'system:notice:create'" type="primary" :icon="Plus" @click="openCreate">新增公告</el-button>
      </template>
    </SystemGovernanceHero>
    <SearchPanel :model-value="query" @search="loadData" @reset="reset">
      <el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="标题 / 内容" /></el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.noticeType" clearable placeholder="全部类型" style="width: 150px">
          <el-option v-for="item in noticeTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width: 140px">
          <el-option label="发布" :value="1" />
          <el-option label="草稿" :value="0" />
        </el-select>
      </el-form-item>
    </SearchPanel>
    <el-card shadow="never">
      <template #header>
        <div class="governance-table-header">
          <div>
            <strong>公告清单</strong>
            <span>公告标题、类型、发布时间、失效时间和发布状态</span>
          </div>
          <el-tag effect="plain">Notice</el-tag>
        </div>
      </template>
      <el-table v-loading="loading" :data="rows" empty-text="暂无通知公告">
        <el-table-column label="标题" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="notice-title">
              <el-tag v-if="row.pinned === 1" size="small" type="danger">置顶</el-tag>
              <span>{{ row.noticeTitle }}</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }"><el-tag>{{ noticeTypeLabel(row.noticeType) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="noticeContent" label="内容摘要" min-width="260" show-overflow-tooltip />
        <el-table-column prop="publishAt" label="发布时间" width="170" />
        <el-table-column prop="expireAt" label="失效时间" width="170">
          <template #default="{ row }">{{ row.expireAt || '长期有效' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.status" :options="statusOptions" /></template></el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button v-permission="'system:notice:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="'system:notice:delete'" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" layout="total, prev, pager, next" :total="total" @current-change="loadData" />
    </el-card>
    <FormDialog v-model="dialogVisible" :title="editing ? '编辑公告' : '新增公告'" :confirm-loading="saving" width="680px" @confirm="save">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="公告标题" prop="noticeTitle"><el-input v-model="form.noticeTitle" /></el-form-item>
        <el-form-item label="公告类型" prop="noticeType">
          <el-select v-model="form.noticeType">
            <el-option v-for="item in noticeTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="公告内容" prop="noticeContent"><el-input v-model="form.noticeContent" type="textarea" :rows="6" maxlength="4000" show-word-limit /></el-form-item>
        <el-form-item label="发布时间" prop="publishAt"><el-date-picker v-model="form.publishAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="立即发布" /></el-form-item>
        <el-form-item label="失效时间"><el-date-picker v-model="form.expireAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="长期有效" /></el-form-item>
        <el-form-item label="发布"><el-switch v-model="publishedSwitch" class="form-switch" inline-prompt active-text="发" inactive-text="存" /></el-form-item>
        <el-form-item label="置顶"><el-switch v-model="pinnedSwitch" class="form-switch" inline-prompt active-text="顶" inactive-text="普" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
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
import StatusTag, { type StatusOption } from '@/components/StatusTag/StatusTag.vue'
import { createNotice, deleteNotice, fetchNotices, updateNotice, type NoticeForm, type NoticeQuery } from '@/api/notice'
import type { NoticeRecord } from '@/types/system'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const rows = ref<NoticeRecord[]>([])
const total = ref(0)
const editing = ref<NoticeRecord | null>(null)
const formRef = ref<FormInstance>()
const query = reactive<NoticeQuery>({ pageNum: 1, pageSize: 10, keyword: '', noticeType: '', status: undefined })
const form = reactive<NoticeForm>({ noticeTitle: '', noticeType: 'system', noticeContent: '', pinned: 0, publishAt: '', expireAt: undefined, status: 1, remark: '' })
const statusOptions: StatusOption[] = [
  { value: 1, label: '发布', type: 'success' },
  { value: 0, label: '草稿', type: 'info' },
]
const noticeTypeOptions = [
  { label: '系统公告', value: 'system' },
  { label: '维护通知', value: 'maintenance' },
  { label: '版本发布', value: 'release' },
]
const rules: FormRules = {
  noticeTitle: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  noticeType: [{ required: true, message: '请选择公告类型', trigger: 'change' }],
  noticeContent: [{ required: true, message: '请输入公告内容', trigger: 'blur' }],
}
const publishedSwitch = computed({
  get: () => form.status === 1,
  set: (value: boolean) => { form.status = value ? 1 : 0 },
})
const pinnedSwitch = computed({
  get: () => form.pinned === 1,
  set: (value: boolean) => { form.pinned = value ? 1 : 0 },
})
const noticeSummary = computed(() => [
  { label: '当前页公告', value: rows.value.length, hint: '已加载结果' },
  { label: '已发布', value: rows.value.filter((row) => row.status === 1).length, hint: '用户可见' },
  { label: '置顶公告', value: rows.value.filter((row) => row.pinned === 1).length, hint: '高优先级' },
])

function noticeTypeLabel(value: string) {
  return noticeTypeOptions.find((item) => item.value === value)?.label || value
}

function nowValue() {
  const date = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

async function loadData() {
  loading.value = true
  try {
    const page = await fetchNotices(query)
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(query, { pageNum: 1, keyword: '', noticeType: '', status: undefined })
  loadData()
}

function openCreate() {
  editing.value = null
  Object.assign(form, { noticeTitle: '', noticeType: 'system', noticeContent: '', pinned: 0, publishAt: nowValue(), expireAt: undefined, status: 1, remark: '' })
  dialogVisible.value = true
}

function openEdit(row: NoticeRecord) {
  editing.value = row
  Object.assign(form, {
    noticeTitle: row.noticeTitle,
    noticeType: row.noticeType,
    noticeContent: row.noticeContent,
    pinned: row.pinned,
    publishAt: row.publishAt,
    expireAt: row.expireAt,
    status: row.status,
    remark: row.remark || '',
  })
  dialogVisible.value = true
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    if (editing.value) await updateNotice(editing.value.id, form)
    else await createNotice(form)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

async function remove(row: NoticeRecord) {
  await ElMessageBox.confirm(`确认删除公告 ${row.noticeTitle}？`, '删除公告', { type: 'warning' })
  await deleteNotice(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.notice-title {
  align-items: center;
  display: inline-flex;
  gap: 6px;
  max-width: 100%;
}
</style>
