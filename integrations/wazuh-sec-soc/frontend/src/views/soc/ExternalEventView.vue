<template>
  <div class="page-shell">
    <section class="external-overview">
      <RiskCard label="外部事件" :value="totals.total" delta="规范化接入记录" tone="medium" />
      <RiskCard label="高风险事件" :value="totals.highRisk" delta="critical / high" tone="high" />
      <RiskCard label="已关联告警" :value="totals.linkedAlerts" delta="进入统一告警中心" tone="low" />
      <RiskCard label="接入来源" :value="summary.length" delta="Suricata 优先，Zeek/情报预留" tone="medium" />
    </section>

    <section class="soc-panel panel-pad">
      <div class="soc-filter-bar external-filter">
        <el-input v-model="query.keyword" clearable placeholder="事件、规则、资产、IOC" @keyup.enter="load" />
        <el-select v-model="query.sourceType" clearable placeholder="来源">
          <el-option label="Suricata" value="suricata" />
          <el-option label="Zeek" value="zeek" />
          <el-option label="MISP" value="misp" />
          <el-option label="OpenCTI" value="opencti" />
        </el-select>
        <el-select v-model="query.severity" clearable placeholder="等级">
          <el-option label="严重" value="critical" />
          <el-option label="高危" value="high" />
          <el-option label="中危" value="medium" />
          <el-option label="低危" value="low" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="openImport">导入 Suricata</el-button>
      </div>
    </section>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false">
      <template #default><el-button size="small" @click="load">重试</el-button></template>
    </el-alert>

    <section class="table-panel">
      <el-table v-loading="loading" :data="rows" empty-text="暂无外部事件" @row-click="open">
        <el-table-column prop="eventUid" label="事件 ID" min-width="170" />
        <el-table-column label="来源" width="92"><template #default="{ row }"><DataSourceBadge :source="row.sourceType" /></template></el-table-column>
        <el-table-column prop="eventType" label="类型" width="120" />
        <el-table-column label="等级" width="86"><template #default="{ row }"><SeverityBadge :severity="row.severity" /></template></el-table-column>
        <el-table-column prop="ruleName" label="规则/情报" min-width="230" show-overflow-tooltip />
        <el-table-column prop="assetName" label="资产" min-width="120" />
        <el-table-column prop="srcIp" label="源 IP" width="140" />
        <el-table-column prop="destIp" label="目的 IP" width="140" />
        <el-table-column prop="ioc" label="IOC" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="100"><template #default="{ row }"><StatusBadge :status="row.status" /></template></el-table-column>
        <el-table-column prop="eventTime" label="时间" width="180" />
      </el-table>
      <div class="pagination-row">
        <span>外部事件 {{ total }} 条</span>
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" layout="total, sizes, prev, pager, next" :total="total" @change="load" />
      </div>
    </section>

    <el-drawer v-model="drawer" title="外部事件详情" size="560px">
      <div v-if="current" class="drawer-stack">
        <div class="soc-drawer-grid">
          <span>事件 ID</span><strong>{{ current.eventUid }}</strong>
          <span>来源</span><strong><DataSourceBadge :source="current.sourceType" /></strong>
          <span>事件类型</span><strong>{{ current.eventType }}</strong>
          <span>等级</span><strong><SeverityBadge :severity="current.severity" /></strong>
          <span>规则</span><strong>{{ current.ruleId || '-' }} / {{ current.ruleName || '-' }}</strong>
          <span>资产</span><strong>{{ current.assetName || '-' }}（{{ current.assetIp || current.destIp || '-' }}）</strong>
          <span>源/目的</span><strong>{{ current.srcIp || '-' }} -> {{ current.destIp || '-' }}</strong>
          <span>IOC</span><strong>{{ current.ioc || '-' }}</strong>
          <span>统一告警</span><strong>{{ current.alertId ? `#${current.alertId}` : '未关联' }}</strong>
          <span>状态</span><strong><StatusBadge :status="current.status" /></strong>
        </div>
        <el-input v-model="remark" type="textarea" :rows="3" placeholder="填写复核说明" />
        <div class="drawer-actions">
          <el-button @click="setStatus('reviewing')">待复核</el-button>
          <el-button type="success" @click="setStatus('linked')">标记已关联</el-button>
          <el-button @click="setStatus('ignored')">忽略</el-button>
          <el-button type="danger" @click="setStatus('closed')">关闭</el-button>
        </div>
        <div class="event-json">
          <strong>规范化事件</strong>
          <pre>{{ formatJson(current.normalizedEvent) }}</pre>
        </div>
        <div class="event-json">
          <strong>原始事件</strong>
          <pre>{{ formatJson(current.rawEvent) }}</pre>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="importVisible" title="导入 Suricata EVE JSON" width="640px">
      <el-form label-width="86px">
        <el-form-item label="JSON Lines" required>
          <el-input v-model="importForm.content" type="textarea" :rows="9" maxlength="50000" show-word-limit placeholder='{"timestamp":"2026-05-27T22:45:00+08:00","event_type":"alert","src_ip":"203.0.113.88","dest_ip":"10.20.1.15","alert":{"signature_id":20260527,"signature":"ET SCAN Demo inbound scan","severity":1}}' />
        </el-form-item>
        <el-form-item label="联动告警">
          <el-switch v-model="importForm.linkAlerts" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import DataSourceBadge from '@/components/security/DataSourceBadge.vue'
import RiskCard from '@/components/security/RiskCard.vue'
import SeverityBadge from '@/components/security/SeverityBadge.vue'
import StatusBadge from '@/components/security/StatusBadge.vue'
import {
  externalEventSummary,
  importSuricataEvents,
  listExternalEvents,
  updateExternalEventStatus,
  type ExternalEventItem,
  type ExternalSourceSummary
} from '@/api/soc'

const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', sourceType: '', severity: '', status: '' })
const rows = ref<ExternalEventItem[]>([])
const summary = ref<ExternalSourceSummary[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const drawer = ref(false)
const current = ref<ExternalEventItem>()
const remark = ref('已按外部事件接入流程复核')
const importVisible = ref(false)
const importing = ref(false)
const importForm = reactive({ content: '', linkAlerts: true })

const totals = computed(() => summary.value.reduce(
  (acc, item) => ({
    total: acc.total + item.total,
    highRisk: acc.highRisk + item.highRisk,
    linkedAlerts: acc.linkedAlerts + item.linkedAlerts
  }),
  { total: 0, highRisk: 0, linkedAlerts: 0 }
))

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [listRes, summaryRes] = await Promise.all([
      listExternalEvents(query),
      externalEventSummary()
    ])
    rows.value = listRes.data.data.records
    total.value = listRes.data.data.total
    summary.value = summaryRes.data.data
  } catch {
    error.value = '外部事件加载失败，请检查权限或后端服务状态。'
  } finally {
    loading.value = false
  }
}

function open(row: ExternalEventItem) {
  current.value = row
  drawer.value = true
}

async function setStatus(status: string) {
  if (!current.value) return
  await updateExternalEventStatus(current.value.id, status, remark.value)
  ElMessage.success('外部事件状态已更新')
  drawer.value = false
  await load()
}

function openImport() {
  importForm.content = ''
  importForm.linkAlerts = true
  importVisible.value = true
}

async function submitImport() {
  if (!importForm.content.trim()) {
    ElMessage.warning('请填写 Suricata EVE JSON Lines')
    return
  }
  importing.value = true
  try {
    const res = await importSuricataEvents({
      content: importForm.content.trim(),
      linkAlerts: importForm.linkAlerts
    })
    const result = res.data.data
    ElMessage.success(`导入 ${result.importedEvents} 条，关联告警 ${result.linkedAlerts} 条`)
    if (result.skippedLines) {
      ElMessage.warning(`跳过 ${result.skippedLines} 行`)
    }
    importVisible.value = false
    await load()
  } finally {
    importing.value = false
  }
}

function formatJson(value?: string) {
  if (!value) return '-'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}
</script>

<style scoped>
.external-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}
.panel-pad { padding: 14px; }
.external-filter {
  display: flex;
  flex-wrap: wrap;
}
.external-filter :deep(.el-input) {
  flex: 1 1 280px;
  min-width: 220px;
}
.external-filter :deep(.el-select) {
  width: 160px;
}
.drawer-stack {
  display: grid;
  gap: 16px;
}
.drawer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.event-json {
  display: grid;
  gap: 8px;
}
.event-json strong {
  color: var(--soc-text);
  font-size: 13px;
}
.event-json pre {
  overflow: auto;
  max-height: 220px;
  padding: 12px;
  margin: 0;
  border: 1px solid var(--soc-border);
  border-radius: var(--soc-radius);
  background: var(--soc-canvas-soft);
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
}
@media (max-width: 1100px) {
  .external-overview {
    grid-template-columns: 1fr;
  }
}
</style>
