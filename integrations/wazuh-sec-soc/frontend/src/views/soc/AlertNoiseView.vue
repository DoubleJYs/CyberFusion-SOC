<template>
  <div class="page-shell">
    <section class="noise-overview">
      <RiskCard label="启用白名单" :value="summary.activeWhitelists" delta="当前有效规则" tone="low" />
      <RiskCard label="命中白名单" :value="summary.whitelistHits" delta="授权范围近 500 条" tone="medium" />
      <RiskCard label="误报告警" :value="summary.falsePositiveAlerts" delta="已标记误报" tone="low" />
      <RiskCard label="重复聚合组" :value="summary.duplicateGroups" delta="规则/资产/来源聚合" tone="high" />
    </section>

    <section class="noise-grid">
      <div class="soc-panel panel-pad">
        <div class="panel-title whitelist-title">
          <strong>白名单规则</strong>
          <div class="soc-filter-bar compact">
            <el-input v-model="query.keyword" clearable placeholder="规则、资产、来源 IP" @keyup.enter="load" />
            <el-select v-model="query.status" clearable placeholder="状态">
              <el-option label="启用" value="enabled" />
              <el-option label="停用" value="disabled" />
            </el-select>
            <el-button type="primary" @click="load">查询</el-button>
            <el-button @click="openForm()">新增规则</el-button>
          </div>
        </div>
        <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="panel-alert">
          <template #default><el-button size="small" @click="load">重试</el-button></template>
        </el-alert>
        <el-table v-loading="loading" :data="whitelists" empty-text="暂无白名单规则">
          <el-table-column prop="ruleName" label="名称" min-width="160" />
          <el-table-column prop="ruleId" label="规则" width="92" />
          <el-table-column prop="assetIp" label="资产 IP" width="130" />
          <el-table-column prop="sourceIp" label="来源 IP" width="130" />
          <el-table-column label="等级" width="86"><template #default="{ row }"><SeverityBadge :severity="row.severity || 'low'" /></template></el-table-column>
          <el-table-column prop="reason" label="原因" min-width="220" show-overflow-tooltip />
          <el-table-column prop="matchCount" label="命中" width="76" />
          <el-table-column label="状态" width="86">
            <template #default="{ row }"><StatusBadge :status="row.enabled ? 'enabled' : 'disabled'" /></template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button size="small" text @click="openForm(row)">编辑</el-button>
              <el-button size="small" @click="toggle(row)">{{ row.enabled ? '停用' : '启用' }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-row">
          <span>白名单规则 {{ total }} 条</span>
          <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" layout="total, prev, pager, next" :total="total" @change="load" />
        </div>
      </div>

      <div class="soc-panel panel-pad">
        <div class="panel-title">
          <strong>重复告警聚合</strong>
          <span>按规则、资产和来源聚合近 500 条授权范围内告警</span>
        </div>
        <el-table v-loading="loading" :data="aggregations" empty-text="暂无重复告警">
          <el-table-column label="等级" width="86"><template #default="{ row }"><SeverityBadge :severity="row.severity" /></template></el-table-column>
          <el-table-column prop="ruleId" label="规则" width="86" />
          <el-table-column prop="ruleDescription" label="描述" min-width="210" show-overflow-tooltip />
          <el-table-column prop="assetIp" label="资产 IP" width="128" />
          <el-table-column prop="sourceIp" label="来源 IP" width="128" />
          <el-table-column prop="repeatCount" label="重复" width="76" />
          <el-table-column label="白名单" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.whitelistRuleName || '-' }}</template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <el-dialog v-model="formVisible" :title="editingId ? '编辑白名单规则' : '新增白名单规则'" width="560px">
      <el-form label-width="92px">
        <el-form-item label="规则名称" required>
          <el-input v-model="form.ruleName" maxlength="128" show-word-limit placeholder="例如：维护窗口内配置调整" />
        </el-form-item>
        <el-form-item label="匹配条件" required>
          <div class="condition-grid">
            <el-input v-model="form.ruleId" clearable maxlength="64" placeholder="规则 ID" />
            <el-input v-model="form.assetIp" clearable maxlength="64" placeholder="资产 IP" />
            <el-input v-model="form.sourceIp" clearable maxlength="64" placeholder="来源 IP" />
            <el-select v-model="form.severity" clearable placeholder="等级">
              <el-option label="严重" value="critical" />
              <el-option label="高危" value="high" />
              <el-option label="中危" value="medium" />
              <el-option label="低危" value="low" />
            </el-select>
          </div>
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker v-model="form.expiresAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="不过期" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="原因说明" required>
          <el-input v-model="form.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明授权依据、变更单或降噪原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import RiskCard from '@/components/security/RiskCard.vue'
import SeverityBadge from '@/components/security/SeverityBadge.vue'
import StatusBadge from '@/components/security/StatusBadge.vue'
import {
  alertNoiseSummary,
  createAlertWhitelist,
  listAlertAggregations,
  listAlertWhitelists,
  updateAlertWhitelist,
  updateAlertWhitelistStatus,
  type AlertAggregationItem,
  type AlertNoiseSummary,
  type AlertWhitelistPayload,
  type AlertWhitelistItem
} from '@/api/soc'

const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '' })
const summary = ref<AlertNoiseSummary>({ activeWhitelists: 0, whitelistHits: 0, falsePositiveAlerts: 0, duplicateGroups: 0 })
const whitelists = ref<AlertWhitelistItem[]>([])
const aggregations = ref<AlertAggregationItem[]>([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const formVisible = ref(false)
const editingId = ref<number>()
const form = reactive<AlertWhitelistPayload>({
  ruleName: '',
  ruleId: '',
  assetIp: '',
  sourceIp: '',
  severity: '',
  reason: '',
  enabled: 1,
  expiresAt: ''
})

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [summaryRes, whitelistRes, aggregationRes] = await Promise.all([
      alertNoiseSummary(),
      listAlertWhitelists(query),
      listAlertAggregations({ pageNum: 1, pageSize: 12 })
    ])
    summary.value = summaryRes.data.data
    whitelists.value = whitelistRes.data.data.records
    total.value = whitelistRes.data.data.total
    aggregations.value = aggregationRes.data.data
  } catch {
    error.value = '告警降噪数据加载失败，请检查权限或后端服务状态。'
  } finally {
    loading.value = false
  }
}

async function toggle(row: AlertWhitelistItem) {
  const target = row.enabled ? 'disabled' : 'enabled'
  await updateAlertWhitelistStatus(row.id, target, target === 'enabled' ? '启用白名单规则' : '停用白名单规则')
  ElMessage.success('白名单状态已更新')
  await load()
}

function openForm(row?: AlertWhitelistItem) {
  editingId.value = row?.id
  form.ruleName = row?.ruleName || ''
  form.ruleId = row?.ruleId || ''
  form.assetIp = row?.assetIp || ''
  form.sourceIp = row?.sourceIp || ''
  form.severity = row?.severity || ''
  form.reason = row?.reason || ''
  form.enabled = row?.enabled ?? 1
  form.expiresAt = row?.expiresAt || ''
  formVisible.value = true
}

async function save() {
  const hasMatcher = Boolean(form.ruleId || form.assetIp || form.sourceIp || form.severity)
  if (!form.ruleName?.trim() || !form.reason?.trim() || !hasMatcher) {
    ElMessage.warning('请填写规则名称、原因说明，并至少填写一项匹配条件')
    return
  }
  saving.value = true
  try {
    const payload: AlertWhitelistPayload = {
      ruleName: form.ruleName.trim(),
      ruleId: normalize(form.ruleId),
      assetIp: normalize(form.assetIp),
      sourceIp: normalize(form.sourceIp),
      severity: normalize(form.severity),
      reason: form.reason.trim(),
      enabled: form.enabled,
      expiresAt: normalize(form.expiresAt)
    }
    if (editingId.value) {
      await updateAlertWhitelist(editingId.value, payload)
    } else {
      await createAlertWhitelist(payload)
    }
    ElMessage.success('白名单规则已保存')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

function normalize(value?: string) {
  return value?.trim() || undefined
}
</script>

<style scoped>
.noise-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}
.noise-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  gap: 14px;
}
.panel-pad { padding: 14px; }
.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.panel-title span {
  color: var(--soc-text-muted);
  font-size: 13px;
}
.compact {
  display: flex;
  flex-wrap: wrap;
  flex: 1;
  justify-content: flex-end;
  padding: 0;
  background: transparent;
  border: 0;
  min-width: 0;
}
.compact :deep(.el-input),
.compact :deep(.el-select) {
  max-width: 180px;
}
.whitelist-title {
  align-items: stretch;
  flex-direction: column;
}
.whitelist-title .compact {
  justify-content: flex-start;
}
.condition-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  width: 100%;
}
.panel-alert {
  margin-bottom: 12px;
}
@media (max-width: 1100px) {
  .noise-overview,
  .noise-grid {
    grid-template-columns: 1fr;
  }
  .panel-title {
    align-items: stretch;
    flex-direction: column;
  }
  .compact {
    justify-content: flex-start;
  }
}
</style>
