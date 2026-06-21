<template>
  <div class="page-shell">
    <SystemGovernanceHero
      title="流程日志"
      description="记录工单、案件、导入任务等业务对象的状态迁移、操作人和原因，支撑处置闭环复盘。"
      scope="流程留痕"
      :total="total"
      :summary="flowSummary"
    >
      <template #action>
        <el-button v-permission="'system:flowlog:create'" type="primary" :icon="Plus" @click="openCreate">新增演示日志</el-button>
      </template>
    </SystemGovernanceHero>
    <SearchPanel :model-value="query" @search="loadData" @reset="reset">
      <el-form-item label="业务类型"><el-input v-model="query.bizType" clearable placeholder="bizType" /></el-form-item>
      <el-form-item label="业务ID"><el-input v-model="query.bizId" clearable placeholder="bizId" /></el-form-item>
      <el-form-item label="业务编号"><el-input v-model="query.bizNo" clearable placeholder="bizNo" /></el-form-item>
      <el-form-item label="操作人"><el-input v-model="query.operatorName" clearable placeholder="operator" /></el-form-item>
    </SearchPanel>
    <el-card shadow="never">
      <template #header>
        <div class="governance-table-header">
          <div>
            <strong>流程日志清单</strong>
            <span>业务类型、业务编号、状态迁移、动作、操作人和原因</span>
          </div>
          <el-tag effect="plain">Flow</el-tag>
        </div>
      </template>
      <el-table v-loading="loading" :data="rows" empty-text="暂无流程日志">
        <el-table-column prop="bizType" label="业务类型" width="120" />
        <el-table-column prop="bizId" label="业务ID" width="120" />
        <el-table-column prop="bizNo" label="业务编号" min-width="150" />
        <el-table-column prop="fromStatus" label="原状态" width="110" />
        <el-table-column prop="toStatus" label="新状态" width="110" />
        <el-table-column prop="action" label="动作" width="120" />
        <el-table-column prop="operatorName" label="操作人" width="120" />
        <el-table-column prop="reason" label="原因" min-width="160" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" layout="total, prev, pager, next" :total="total" @current-change="loadData" />
    </el-card>
    <FormDialog v-model="dialogVisible" title="新增演示流程日志" :confirm-loading="saving" @confirm="save">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="业务类型" prop="bizType"><el-input v-model="form.bizType" /></el-form-item>
        <el-form-item label="业务ID" prop="bizId"><el-input v-model="form.bizId" /></el-form-item>
        <el-form-item label="业务编号"><el-input v-model="form.bizNo" /></el-form-item>
        <el-form-item label="原状态"><el-input v-model="form.fromStatus" /></el-form-item>
        <el-form-item label="新状态"><el-input v-model="form.toStatus" /></el-form-item>
        <el-form-item label="动作" prop="action"><el-input v-model="form.action" /></el-form-item>
        <el-form-item label="原因"><el-input v-model="form.reason" /></el-form-item>
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
import SystemGovernanceHero from '@/components/SystemGovernanceHero/SystemGovernanceHero.vue'
import { createBizFlowLog, fetchBizFlowLogs } from '@/api/workflow'
import type { BizFlowLogForm, BizFlowLogRecord } from '@/types/workflow'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const rows = ref<BizFlowLogRecord[]>([])
const total = ref(0)
const formRef = ref<FormInstance>()
const query = reactive({ pageNum: 1, pageSize: 10, bizType: '', bizId: '', bizNo: '', operatorName: '' })
const form = reactive<BizFlowLogForm>({ bizType: 'demo', bizId: '1', bizNo: 'DEMO0001', fromStatus: 'draft', toStatus: 'submitted', action: 'submit', reason: '', remark: '' })
const flowSummary = computed(() => [
  { label: '当前页日志', value: rows.value.length, hint: '已加载结果' },
  { label: '业务类型', value: new Set(rows.value.map((row) => row.bizType).filter(Boolean)).size, hint: '流程来源' },
  { label: '操作人', value: new Set(rows.value.map((row) => row.operatorName).filter(Boolean)).size, hint: '责任主体' },
])
const rules: FormRules = {
  bizType: [{ required: true, message: '请输入业务类型', trigger: 'blur' }],
  bizId: [{ required: true, message: '请输入业务ID', trigger: 'blur' }],
  action: [{ required: true, message: '请输入动作', trigger: 'blur' }],
}

async function loadData() {
  loading.value = true
  try {
    const page = await fetchBizFlowLogs(query)
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(query, { pageNum: 1, bizType: '', bizId: '', bizNo: '', operatorName: '' })
  loadData()
}

function openCreate() {
  Object.assign(form, { bizType: 'demo', bizId: '1', bizNo: 'DEMO0001', fromStatus: 'draft', toStatus: 'submitted', action: 'submit', reason: '', remark: '' })
  dialogVisible.value = true
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    await createBizFlowLog(form)
    ElMessage.success('流程日志已写入')
    dialogVisible.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>
