<template>
  <div class="page-shell rule-center-page">
    <section class="soc-page-hero">
      <div>
        <span class="soc-page-kicker">DETECTION CONTENT SETTINGS</span>
        <h1>检测内容规则设置</h1>
        <p>统一设置哪些已接入规则进入 SOC、以什么等级生成告警，以及每条规则检测的内容和发布版本。</p>
      </div>
      <div class="soc-page-tags">
        <el-tag effect="plain">Sigma / IDS / 主机规则</el-tag>
        <el-tag effect="plain">发布与停用</el-tag>
        <el-tag effect="plain">命中预览</el-tag>
      </div>
    </section>

    <el-alert class="rule-scope-alert" type="info" show-icon :closable="false">
      <template #title>这里配置 CyberFusion 对已接入规则的统一检测内容、等级和告警晋级状态。</template>
      上游 Sigma、Wazuh、Suricata 等引擎仍负责采集与匹配；本页发布后会影响平台后续导入证据是否生成统一告警，不会修改上游主机或网络设备上的规则文件。
    </el-alert>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false">
      <template #default><el-button size="small" @click="load">重试</el-button></template>
    </el-alert>

    <section class="rule-metrics">
      <RiskCard label="规则总数" :value="total" delta="来源目录与平台配置合并" tone="medium" />
      <RiskCard label="已发布规则" :value="activeCount" delta="后续导入可生成统一告警" tone="safe" />
      <RiskCard label="待发布草稿" :value="draftCount" delta="已保存，尚未影响告警" tone="low" />
      <RiskCard label="总命中" :value="totalHits" delta="外部事件与告警命中" tone="high" />
    </section>

    <section class="soc-panel panel-pad">
      <div class="soc-filter-bar">
        <el-input v-model="query.keyword" clearable placeholder="搜索规则 ID、检测内容或规则名称" @keyup.enter="load" />
        <el-select v-model="query.sourceType" clearable placeholder="规则来源">
          <el-option v-for="source in sourceOptions" :key="source.value" :label="source.label" :value="source.value" />
        </el-select>
        <el-select v-model="query.severity" clearable placeholder="严重级别">
          <el-option label="严重" value="critical" />
          <el-option label="高危" value="high" />
          <el-option label="中危" value="medium" />
          <el-option label="低危" value="low" />
        </el-select>
        <el-button @click="load">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button type="primary" @click="openCreate">新增检测内容规则</el-button>
      </div>
    </section>

    <section class="table-panel">
      <el-table v-loading="loading" :data="rows" empty-text="暂无检测内容规则" @row-click="openRule">
        <el-table-column prop="ruleId" label="规则 ID" min-width="152" show-overflow-tooltip />
        <el-table-column prop="ruleName" label="规则名称" min-width="205" show-overflow-tooltip />
        <el-table-column label="检测内容" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.detectionSummary || '由来源引擎管理，尚未设置平台检测说明。' }}</template>
        </el-table-column>
        <el-table-column label="来源" width="100">
          <template #default="{ row }"><DataSourceBadge :source="row.sourceType" /></template>
        </el-table-column>
        <el-table-column label="等级" width="86">
          <template #default="{ row }"><SeverityBadge :severity="row.severity" /></template>
        </el-table-column>
        <el-table-column label="发布状态" width="100">
          <template #default="{ row }"><el-tag :type="statusTone(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="98" />
        <el-table-column prop="hitCount" label="命中" width="72" />
        <el-table-column prop="lastHitAt" label="最近命中" min-width="160">
          <template #default="{ row }">{{ row.lastHitAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="250">
          <template #default="{ row }">
            <el-button text @click.stop="openRule(row)">命中预览</el-button>
            <el-button text type="primary" @click.stop="openSetting(row)">{{ row.policyId ? '编辑设置' : '设置规则' }}</el-button>
            <el-button v-if="row.policyId && row.status !== 'active'" text type="success" @click.stop="publishRule(row)">发布</el-button>
            <el-button v-if="row.policyId && row.status === 'active'" text type="warning" @click.stop="disableRule(row)">停用</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-row">
        <span>共 {{ total }} 条规则</span>
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" layout="total, sizes, prev, pager, next" :total="total" @change="load" />
      </div>
    </section>

    <section class="soc-panel mapping-panel">
      <div class="panel-title">
        <div>
          <strong>数据接入映射引用</strong>
          <span>规则 ID、来源字段、去重键和告警联动在数据进入统一检测目录前完成标准化。</span>
        </div>
        <el-button text type="primary" @click="router.push('/soc/policies')">查看接入字段映射</el-button>
      </div>
      <el-table v-loading="mappingLoading" :data="mappings" empty-text="暂无字段映射" size="small">
        <el-table-column prop="adapter" label="数据来源" width="105" />
        <el-table-column prop="sourceField" label="来源字段" min-width="210" show-overflow-tooltip />
        <el-table-column prop="normalizedField" label="统一字段" min-width="220" show-overflow-tooltip />
        <el-table-column prop="severityMapping" label="等级映射" min-width="230" show-overflow-tooltip />
        <el-table-column prop="dedupKey" label="去重键" min-width="210" show-overflow-tooltip />
      </el-table>
    </section>

    <el-drawer v-model="drawer" title="规则设置与命中预览" size="680px">
      <div v-if="currentRule" class="drawer-stack">
        <section class="rule-explanation">
          <h2>{{ currentRule.ruleName }}</h2>
          <p>{{ currentRule.detectionSummary || '该规则由接入来源维护。设置平台规则后，可定义它的检测说明、统一告警等级与发布状态。' }}</p>
          <el-button type="primary" plain @click="openSetting(currentRule)">{{ currentRule.policyId ? '编辑检测设置' : '设置检测规则' }}</el-button>
        </section>
        <div class="soc-drawer-grid">
          <span>规则 ID</span><strong>{{ currentRule.ruleId }}</strong>
          <span>来源</span><strong><DataSourceBadge :source="currentRule.sourceType" /></strong>
          <span>检测类别</span><strong>{{ categoryLabel(currentRule.detectionCategory) }}</strong>
          <span>告警等级</span><strong><SeverityBadge :severity="currentRule.severity" /></strong>
          <span>发布状态</span><strong><el-tag :type="statusTone(currentRule.status)" effect="plain">{{ statusLabel(currentRule.status) }}</el-tag></strong>
          <span>命中/误报</span><strong>{{ currentRule.hitCount }} / {{ currentRule.falsePositiveCount }}</strong>
        </div>
        <el-tabs>
          <el-tab-pane label="最近事件">
            <el-empty v-if="!hits?.events.length" description="暂无最近事件命中" />
            <div v-else class="hit-list">
              <article v-for="event in hits.events" :key="event.id" class="hit-item">
                <div><strong>{{ event.eventUid }}</strong><span>{{ event.eventType }} · {{ event.assetName || event.assetIp || '-' }} · {{ event.eventTime }}</span></div>
                <el-button text @click="goEvent(event)">查看事件</el-button>
              </article>
            </div>
          </el-tab-pane>
          <el-tab-pane label="最近告警">
            <el-empty v-if="!hits?.alerts.length" description="暂无最近告警命中" />
            <div v-else class="hit-list">
              <article v-for="alert in hits.alerts" :key="alert.id" class="hit-item">
                <div><strong>{{ alert.alertUid }}</strong><span>{{ alert.status }} · {{ alert.assetName || alert.assetIp || '-' }} · {{ alert.eventTime }}</span></div>
                <el-button text @click="goAlert(alert)">查看告警</el-button>
              </article>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <el-dialog v-model="settingDialogVisible" :title="editingPolicyId ? '编辑检测内容规则' : '新增检测内容规则'" width="720px" destroy-on-close>
      <el-alert title="发布规则后，平台会按设置的等级将后续同来源、同规则 ID 的导入证据提升为统一告警。上游引擎本身的匹配逻辑不在此页修改。" type="info" show-icon :closable="false" />
      <el-form :model="ruleForm" label-width="112px" class="rule-form">
        <el-form-item label="规则来源"><el-select v-model="ruleForm.sourceType" :disabled="Boolean(editingPolicyId)"><el-option v-for="source in sourceOptions" :key="source.value" :label="source.label" :value="source.value" /></el-select></el-form-item>
        <el-form-item label="规则 ID"><el-input v-model="ruleForm.ruleId" :disabled="Boolean(editingPolicyId)" placeholder="例如 SIGMA-LOCAL-001" /></el-form-item>
        <el-form-item label="规则名称"><el-input v-model="ruleForm.ruleName" /></el-form-item>
        <el-form-item label="检测类别"><el-select v-model="ruleForm.detectionCategory"><el-option label="身份与登录" value="identity" /><el-option label="主机安全" value="host" /><el-option label="网络检测" value="network" /><el-option label="Web 与应用" value="web" /><el-option label="漏洞暴露" value="vulnerability" /><el-option label="自定义检测" value="custom" /></el-select></el-form-item>
        <el-form-item label="告警等级"><el-segmented v-model="ruleForm.severity" :options="['low', 'medium', 'high', 'critical']" /></el-form-item>
        <el-form-item label="检测内容说明"><el-input v-model="ruleForm.detectionSummary" type="textarea" :rows="4" placeholder="说明该规则识别什么风险、什么情况下进入统一告警。" /></el-form-item>
        <el-form-item label="保存状态"><el-segmented v-model="ruleForm.status" :options="['draft', 'active', 'disabled']" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="settingDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRule">保存设置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import DataSourceBadge from '@/components/security/DataSourceBadge.vue'
import RiskCard from '@/components/security/RiskCard.vue'
import SeverityBadge from '@/components/security/SeverityBadge.vue'
import {
  adapterFieldMappings,
  createDetectionRulePolicy,
  disableDetectionRulePolicy,
  listRules,
  publishDetectionRulePolicy,
  ruleHits,
  updateDetectionRulePolicy,
  type AdapterFieldMapping,
  type AlertItem,
  type DetectionRulePolicyPayload,
  type ExternalEventItem,
  type RuleHits,
  type RuleItem,
} from '@/api/soc'

const route = useRoute()
const router = useRouter()
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', sourceType: '', severity: '' })
const rows = ref<RuleItem[]>([])
const total = ref(0)
const loading = ref(false)
const mappingLoading = ref(false)
const saving = ref(false)
const error = ref('')
const mappings = ref<AdapterFieldMapping[]>([])
const drawer = ref(false)
const settingDialogVisible = ref(false)
const currentRule = ref<RuleItem>()
const hits = ref<RuleHits>()
const editingPolicyId = ref<number>()
const ruleForm = reactive<DetectionRulePolicyPayload>(defaultRuleForm())

const sourceOptions = [
  { label: 'Sigma', value: 'sigma' },
  { label: 'WAF', value: 'waf' },
  { label: 'ZAP', value: 'zap' },
  { label: 'Suricata', value: 'suricata' },
  { label: 'Wazuh', value: 'wazuh' },
  { label: 'Zeek', value: 'zeek' },
  { label: 'Host Agent', value: 'host' },
]

const activeCount = computed(() => rows.value.filter((item) => item.status === 'active').length)
const draftCount = computed(() => rows.value.filter((item) => item.status === 'draft').length)
const totalHits = computed(() => rows.value.reduce((sum, item) => sum + item.hitCount, 0))

watch(
  () => [route.query.sourceType, route.query.keyword, route.query.ruleId],
  () => {
    query.sourceType = typeof route.query.sourceType === 'string' ? route.query.sourceType : ''
    query.keyword = typeof route.query.ruleId === 'string' ? route.query.ruleId : typeof route.query.keyword === 'string' ? route.query.keyword : ''
    query.pageNum = 1
    void load()
  },
  { immediate: true },
)

onMounted(loadMappings)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await listRules(query)
    rows.value = res.data.data.records
    total.value = res.data.data.total
  } catch {
    error.value = '检测内容规则加载失败，请检查登录状态、权限或后端服务。'
  } finally {
    loading.value = false
  }
}

async function loadMappings() {
  mappingLoading.value = true
  try {
    const res = await adapterFieldMappings()
    mappings.value = res.data.data
  } catch {
    ElMessage.warning('接入字段映射加载失败')
  } finally {
    mappingLoading.value = false
  }
}

function resetFilters() {
  query.keyword = ''
  query.sourceType = ''
  query.severity = ''
  query.pageNum = 1
  void load()
}

function defaultRuleForm(): DetectionRulePolicyPayload {
  return { sourceType: 'sigma', ruleId: '', ruleName: '', detectionCategory: 'custom', severity: 'medium', detectionSummary: '', status: 'draft', enabled: true }
}

function openCreate() {
  editingPolicyId.value = undefined
  Object.assign(ruleForm, defaultRuleForm())
  settingDialogVisible.value = true
}

function openSetting(row: RuleItem) {
  editingPolicyId.value = row.policyId
  Object.assign(ruleForm, {
    sourceType: row.sourceType,
    ruleId: row.ruleId,
    ruleName: row.ruleName,
    detectionCategory: row.detectionCategory || categoryForSource(row.sourceType),
    severity: row.severity as DetectionRulePolicyPayload['severity'],
    detectionSummary: row.detectionSummary || '',
    status: row.status === 'external' || !row.status ? 'draft' : row.status,
    enabled: row.enabled,
  })
  settingDialogVisible.value = true
}

async function saveRule() {
  saving.value = true
  try {
    if (editingPolicyId.value) {
      await updateDetectionRulePolicy(editingPolicyId.value, ruleForm)
    } else {
      await createDetectionRulePolicy(ruleForm)
    }
    ElMessage.success('检测内容规则已保存')
    settingDialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function publishRule(row: RuleItem) {
  if (!row.policyId) return
  await ElMessageBox.confirm('发布后，后续同来源和规则 ID 的导入证据会按当前等级生成统一告警。确认发布？', '发布检测内容规则')
  await publishDetectionRulePolicy(row.policyId)
  ElMessage.success('检测内容规则已发布')
  await load()
}

async function disableRule(row: RuleItem) {
  if (!row.policyId) return
  await ElMessageBox.confirm('停用后，后续同来源和规则 ID 的导入证据仍会保留，但不再生成新的统一告警。确认停用？', '停用检测内容规则')
  await disableDetectionRulePolicy(row.policyId)
  ElMessage.success('检测内容规则已停用')
  await load()
}

async function openRule(row: RuleItem) {
  currentRule.value = row
  drawer.value = true
  hits.value = undefined
  try {
    const res = await ruleHits(row.sourceType, row.ruleId)
    hits.value = res.data.data
  } catch {
    ElMessage.warning('规则命中预览加载失败')
  }
}

function statusLabel(status?: RuleItem['status']) {
  return { active: '已发布', draft: '草稿', disabled: '已停用', external: '来源管理' }[status || 'external']
}

function statusTone(status?: RuleItem['status']) {
  return status === 'active' ? 'success' : status === 'draft' ? 'warning' : 'info'
}

function categoryForSource(sourceType: string): DetectionRulePolicyPayload['detectionCategory'] {
  if (sourceType === 'wazuh' || sourceType === 'host') return 'host'
  if (sourceType === 'suricata' || sourceType === 'zeek') return 'network'
  if (sourceType === 'waf' || sourceType === 'zap') return 'web'
  return 'custom'
}

function categoryLabel(category?: string) {
  return { identity: '身份与登录', host: '主机安全', network: '网络检测', web: 'Web 与应用', vulnerability: '漏洞暴露', custom: '自定义检测' }[category || 'custom']
}

function goEvent(event: ExternalEventItem) {
  void router.push({ path: '/soc/external-events', query: { sourceType: event.sourceType, keyword: event.ruleId || event.eventUid, openEventUid: event.eventUid } })
}

function goAlert(alert: AlertItem) {
  void router.push({ path: '/soc/alerts', query: { sourceType: alert.sourceType, keyword: alert.ruleId, openAlertId: alert.id } })
}
</script>

<style scoped>
.rule-center-page { min-width: 0; }
.rule-scope-alert { margin-top: 14px; }
.rule-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }
.panel-pad, .mapping-panel { padding: 14px; }
.panel-title { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.panel-title strong { display: block; color: var(--soc-text); font-size: 15px; }
.panel-title span { display: block; margin-top: 4px; color: var(--soc-text-muted); font-size: 13px; line-height: 1.55; }
.drawer-stack { display: grid; gap: 16px; }
.rule-explanation { padding: 16px; border: 1px solid rgba(61, 147, 223, 0.32); border-radius: 8px; background: rgba(237, 246, 255, 0.68); }
.rule-explanation h2 { margin: 0 0 8px; color: var(--soc-text); font-size: 18px; }
.rule-explanation p { margin: 0 0 14px; color: var(--soc-text-muted); line-height: 1.65; }
.hit-list { display: grid; gap: 10px; }
.hit-item { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 12px; border: 1px solid rgba(179, 173, 163, 0.42); border-radius: 8px; background: rgba(255, 255, 255, 0.58); }
.hit-item strong { display: block; color: var(--soc-text); }
.hit-item span { color: var(--soc-text-muted); font-size: 13px; }
.rule-form { margin-top: 16px; padding-right: 12px; }
@media (max-width: 900px) { .rule-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 560px) { .rule-metrics { grid-template-columns: 1fr; } .panel-title { flex-direction: column; } }
</style>
