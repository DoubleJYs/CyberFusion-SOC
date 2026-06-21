<template>
  <div class="page-shell">
    <el-tabs v-model="active" @tab-change="loadData">
      <el-tab-pane label="登录日志" name="login" />
      <el-tab-pane label="操作日志" name="operation" />
    </el-tabs>
    <SearchPanel :model-value="query" @search="loadData" @reset="reset">
      <el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="用户 / 动作" /></el-form-item>
      <el-form-item label="状态"><el-select v-model="query.status" clearable placeholder="全部" style="width: 140px"><el-option label="成功" value="SUCCESS" /><el-option label="失败" value="FAIL" /></el-select></el-form-item>
    </SearchPanel>
    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" empty-text="暂无日志">
        <el-table-column prop="username" label="用户" width="110" />
        <el-table-column v-if="active === 'operation'" prop="action" label="动作" min-width="130" />
        <el-table-column v-if="active === 'operation'" prop="method" label="方法" width="90" />
        <el-table-column v-if="active === 'operation'" prop="path" label="路径" min-width="180" />
        <el-table-column prop="ip" label="IP" width="120" />
        <el-table-column prop="status" label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column prop="message" label="消息" min-width="160" />
        <el-table-column prop="createdAt" label="时间" min-width="170" />
        <el-table-column label="详情" width="80"><template #default="{ row }"><el-button link type="primary" @click="detail = row">查看</el-button></template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" layout="total, prev, pager, next" :total="total" @current-change="loadData" />
    </el-card>
    <el-dialog v-model="detailVisible" title="日志详情" width="620px">
      <pre>{{ detailJson }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import SearchPanel from '@/components/SearchPanel/SearchPanel.vue'
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import { fetchLoginLogs, fetchOperationLogs } from '@/api/log'
import type { LoginLogRecord, OperationLogRecord } from '@/types/system'

const active = ref<'login' | 'operation'>('login')
const loading = ref(false)
const rows = ref<Array<LoginLogRecord | OperationLogRecord>>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '' })
const detail = ref<LoginLogRecord | OperationLogRecord | null>(null)
const detailVisible = computed({
  get: () => Boolean(detail.value),
  set: (value) => {
    if (!value) detail.value = null
  },
})
const detailJson = computed(() => JSON.stringify(detail.value, null, 2))

async function loadData() {
  loading.value = true
  try {
    const page = active.value === 'login' ? await fetchLoginLogs(query) : await fetchOperationLogs(query)
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function reset() {
  query.keyword = ''
  query.status = ''
  query.pageNum = 1
  loadData()
}

watch(active, () => {
  query.pageNum = 1
})

onMounted(loadData)
</script>

<style scoped>
pre {
  overflow: auto;
  padding: 12px;
  border-radius: 8px;
  background: #f8fafc;
}
</style>
