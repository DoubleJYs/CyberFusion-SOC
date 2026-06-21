<template>
  <div class="page-shell">
    <section class="soc-page-hero system-hero">
      <div>
        <span class="soc-page-kicker">CyberFusion Governance</span>
        <h1>{{ title }}</h1>
        <p>{{ pageMeta.description }}</p>
      </div>
      <div class="system-hero-side">
        <span>{{ pageMeta.scope }}</span>
        <strong>{{ filteredRows.length }}</strong>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增</el-button>
      </div>
    </section>
    <section class="system-summary-grid">
      <article v-for="item in summaryCards" :key="item.label" class="system-summary-card">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.hint }}</small>
      </article>
    </section>
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
      :title="`${pageMeta.entity}清单`"
      :subtitle="`${pageMeta.scope} · 当前筛选 ${filteredRows.length} 条`"
      :badge="title"
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
    <el-dialog v-model="dialogVisible" :title="editingRow ? `编辑 ${pageMeta.entity}` : `新增 ${pageMeta.entity}`" width="460px" class="governance-dialog">
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
const metaByTitle: Record<string, { entity: string; description: string; scope: string }> = {
  用户管理: {
    entity: '用户',
    description: '统一维护 SOC 登录身份、部门归属和数据权限，支撑告警、工单、报表的责任人闭环。',
    scope: '账号与部门边界',
  },
  角色管理: {
    entity: '角色',
    description: '沉淀分析员、处置员、管理员等角色权限，避免安全运营能力无序暴露。',
    scope: 'RBAC 策略',
  },
  部门管理: {
    entity: '部门',
    description: '将资产、事件、漏洞和工单绑定到组织单元，形成可审计的数据权限视图。',
    scope: '组织与数据域',
  },
  岗位管理: {
    entity: '岗位',
    description: '维护安全值守、研判、响应和平台治理岗位，辅助排班与责任追踪。',
    scope: '职责模型',
  },
  菜单管理: {
    entity: '菜单',
    description: '管理 CyberFusion 统一门户导航，保障核心接入模块和治理功能清晰分组。',
    scope: '信息架构',
  },
  字典管理: {
    entity: '字典',
    description: '统一告警等级、工单状态、数据源类型等枚举口径，减少跨模块解释偏差。',
    scope: '标准化枚举',
  },
  参数配置: {
    entity: '参数',
    description: '集中维护平台显示、安全阈值、导入策略等非敏感配置模板。',
    scope: '运行配置',
  },
}
const pageMeta = computed(() => metaByTitle[props.title] || {
  entity: '记录',
  description: '统一维护平台治理数据，保持身份、权限、审计和工作流配置可追踪。',
  scope: '平台治理',
})
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
const enabledCount = computed(() => rows.value.filter((row) => row.status === 'enabled').length)
const disabledCount = computed(() => rows.value.length - enabledCount.value)
const summaryCards = computed(() => [
  { label: '全部记录', value: rows.value.length, hint: pageMeta.value.scope },
  { label: '启用', value: enabledCount.value, hint: '可参与当前平台流程' },
  { label: '停用', value: disabledCount.value, hint: '已保留但不进入主链路' },
])

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

<style scoped>
.system-hero {
  align-items: center;
}

.system-hero-side {
  display: grid;
  min-width: 180px;
  justify-items: end;
  gap: 8px;
}

.system-hero-side span {
  color: var(--soc-text-muted);
  font-size: 12px;
  font-weight: 700;
}

.system-hero-side strong {
  color: var(--soc-text);
  font-size: 30px;
  line-height: 1;
}

.system-summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.system-summary-card {
  position: relative;
  overflow: hidden;
  min-width: 0;
  min-height: 92px;
  padding: 16px;
  border: 1px solid var(--soc-border);
  border-radius: var(--soc-radius-card);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.86), rgba(249, 245, 238, 0.68)),
    var(--soc-glass);
  box-shadow: var(--soc-shadow-soft), var(--soc-glass-highlight);
  backdrop-filter: blur(22px) saturate(1.14);
}

.system-summary-card::after {
  position: absolute;
  top: -42px;
  right: -26px;
  width: 108px;
  height: 160px;
  transform: rotate(18deg);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.68), rgba(212, 147, 74, 0.1));
  content: "";
  pointer-events: none;
}

.system-summary-card span,
.system-summary-card small,
.system-summary-card strong {
  position: relative;
  z-index: 1;
}

.system-summary-card span {
  color: var(--soc-text-muted);
  font-size: 12px;
  font-weight: 700;
}

.system-summary-card strong {
  display: block;
  margin-top: 8px;
  color: var(--soc-text);
  font-size: 28px;
  line-height: 1;
}

.system-summary-card small {
  display: block;
  margin-top: 10px;
  color: var(--soc-text-subtle);
  font-size: 12px;
}

@media (max-width: 760px) {
  .system-hero-side {
    justify-items: start;
    min-width: 0;
  }

  .system-summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
