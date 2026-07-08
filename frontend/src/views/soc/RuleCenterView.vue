<template>
  <div class="page-shell rule-center-page">
    <section class="soc-page-hero">
      <div>
        <span class="soc-page-kicker">DETECTION RULE CENTER</span>
        <h1>检测规则</h1>
        <p>这个页面帮你看懂规则来源、命中情况、字段映射和为什么会生成告警。</p>
      </div>
      <div class="soc-page-tags">
        <el-tag effect="plain">规则生命周期</el-tag>
        <el-tag effect="plain">字段映射</el-tag>
        <el-tag effect="plain">命中预览</el-tag>
      </div>
    </section>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false">
      <template #default>
        <el-button size="small" @click="load">重试</el-button>
      </template>
    </el-alert>

    <section class="rule-metrics">
      <RiskCard label="规则总数" :value="total" delta="静态目录 + 实时命中聚合" tone="medium" />
      <RiskCard label="启用规则" :value="enabledCount" delta="当前仅展示，不在线编辑下发" tone="safe" />
      <RiskCard label="总命中" :value="totalHits" delta="外部事件 + 告警命中" tone="low" />
      <RiskCard label="误报记录" :value="falsePositiveTotal" delta="来自告警 false_positive 状态" tone="high" />
    </section>

    <section class="soc-panel panel-pad">
      <div class="soc-filter-bar">
        <el-input v-model="query.keyword" clearable placeholder="搜索 ruleId、规则名、来源" @keyup.enter="load" />
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
      </div>
    </section>

    <section class="table-panel">
      <el-table v-loading="loading" :data="rows" empty-text="暂无检测规则" @row-click="openRule">
        <el-table-column prop="ruleId" label="Rule ID" min-width="150" />
        <el-table-column prop="ruleName" label="规则名称" min-width="260" show-overflow-tooltip />
        <el-table-column label="来源" width="108">
          <template #default="{ row }"><DataSourceBadge :source="row.sourceType" /></template>
        </el-table-column>
        <el-table-column label="级别" width="90">
          <template #default="{ row }"><SeverityBadge :severity="row.severity" /></template>
        </el-table-column>
        <el-table-column label="状态" width="86">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="118" />
        <el-table-column prop="hitCount" label="命中" width="92" />
        <el-table-column prop="falsePositiveCount" label="误报" width="92" />
        <el-table-column prop="lastHitAt" label="最近命中" width="180">
          <template #default="{ row }">{{ row.lastHitAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="入口" width="210">
          <template #default="{ row }">
            <el-button text @click.stop="openRule(row)">命中预览</el-button>
            <el-button text @click.stop="goRuleEvents(row)">事件</el-button>
            <el-button text @click.stop="goRuleAlerts(row)">告警</el-button>
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
          <strong>Adapter 字段映射</strong>
          <span>说明每类来源如何进入统一字段、如何去重、何时联动告警。</span>
        </div>
        <el-tag effect="plain">{{ mappings.length }} 个 adapter</el-tag>
      </div>
      <el-table v-loading="mappingLoading" :data="mappings" empty-text="暂无字段映射" size="small">
        <el-table-column prop="adapter" label="Adapter" width="105" />
        <el-table-column prop="sourceField" label="Source field" min-width="210" show-overflow-tooltip />
        <el-table-column prop="normalizedField" label="Normalized field" min-width="220" show-overflow-tooltip />
        <el-table-column prop="requirement" label="Required / Optional" min-width="190" show-overflow-tooltip />
        <el-table-column prop="severityMapping" label="Severity mapping" min-width="230" show-overflow-tooltip />
        <el-table-column prop="dedupKey" label="Dedup key" min-width="210" show-overflow-tooltip />
        <el-table-column prop="alertLinkRule" label="Alert link rule" min-width="240" show-overflow-tooltip />
        <el-table-column prop="sampleFile" label="Sample file" min-width="180" show-overflow-tooltip />
        <el-table-column prop="failureCase" label="Failure case" min-width="240" show-overflow-tooltip />
      </el-table>
    </section>

    <el-drawer v-model="drawer" title="规则命中预览" size="640px">
      <div v-if="currentRule" class="drawer-stack">
        <div class="soc-drawer-grid">
          <span>Rule ID</span><strong>{{ currentRule.ruleId }}</strong>
          <span>规则名称</span><strong>{{ currentRule.ruleName }}</strong>
          <span>来源</span><strong><DataSourceBadge :source="currentRule.sourceType" /></strong>
          <span>级别</span><strong><SeverityBadge :severity="currentRule.severity" /></strong>
          <span>版本</span><strong>{{ currentRule.version }}</strong>
          <span>命中/误报</span><strong>{{ currentRule.hitCount }} / {{ currentRule.falsePositiveCount }}</strong>
        </div>

        <el-tabs>
          <el-tab-pane label="最近事件">
            <el-empty v-if="!hits?.events.length" description="暂无最近事件命中" />
            <div v-else class="hit-list">
              <article v-for="event in hits.events" :key="event.id" class="hit-item">
                <div>
                  <strong>{{ event.eventUid }}</strong>
                  <span>{{ event.eventType }} · {{ event.assetName || event.assetIp || '-' }} · {{ event.eventTime }}</span>
                </div>
                <el-button text @click="goEvent(event)">查看事件</el-button>
              </article>
            </div>
          </el-tab-pane>
          <el-tab-pane label="最近告警">
            <el-empty v-if="!hits?.alerts.length" description="暂无最近告警命中" />
            <div v-else class="hit-list">
              <article v-for="alert in hits.alerts" :key="alert.id" class="hit-item">
                <div>
                  <strong>{{ alert.alertUid }}</strong>
                  <span>{{ alert.status }} · {{ alert.assetName || alert.assetIp || '-' }} · {{ alert.eventTime }}</span>
                </div>
                <el-button text @click="goAlert(alert)">查看告警</el-button>
              </article>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import DataSourceBadge from '@/components/security/DataSourceBadge.vue'
import RiskCard from '@/components/security/RiskCard.vue'
import SeverityBadge from '@/components/security/SeverityBadge.vue'
import {
  adapterFieldMappings,
  listRules,
  ruleHits,
  type AdapterFieldMapping,
  type AlertItem,
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
const error = ref('')
const mappings = ref<AdapterFieldMapping[]>([])
const drawer = ref(false)
const currentRule = ref<RuleItem>()
const hits = ref<RuleHits>()

const sourceOptions = [
  { label: 'Sigma', value: 'sigma' },
  { label: 'WAF', value: 'waf' },
  { label: 'ZAP', value: 'zap' },
  { label: 'Suricata', value: 'suricata' },
  { label: 'Wazuh', value: 'wazuh' },
  { label: 'Zeek', value: 'zeek' },
]

const enabledCount = computed(() => rows.value.filter((item) => item.enabled).length)
const totalHits = computed(() => rows.value.reduce((sum, item) => sum + item.hitCount, 0))
const falsePositiveTotal = computed(() => rows.value.reduce((sum, item) => sum + item.falsePositiveCount, 0))

watch(
  () => [route.query.sourceType, route.query.keyword, route.query.ruleId],
  () => {
    query.sourceType = typeof route.query.sourceType === 'string' ? route.query.sourceType : ''
    query.keyword = typeof route.query.ruleId === 'string'
      ? route.query.ruleId
      : typeof route.query.keyword === 'string' ? route.query.keyword : ''
    query.pageNum = 1
    void load()
  },
  { immediate: true }
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
    error.value = '检测规则加载失败，请检查登录状态、权限或后端服务。'
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
    ElMessage.warning('Adapter 字段映射加载失败')
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

function goRuleEvents(row: RuleItem) {
  router.push({ path: '/soc/external-events', query: { sourceType: row.sourceType, keyword: row.ruleId } })
}

function goRuleAlerts(row: RuleItem) {
  router.push({ path: '/soc/alerts', query: { sourceType: row.sourceType === 'wazuh' ? 'mock' : row.sourceType, keyword: row.ruleId } })
}

function goEvent(event: ExternalEventItem) {
  router.push({ path: '/soc/external-events', query: { sourceType: event.sourceType, keyword: event.ruleId || event.eventUid, openEventUid: event.eventUid } })
}

function goAlert(alert: AlertItem) {
  router.push({ path: '/soc/alerts', query: { sourceType: alert.sourceType, keyword: alert.ruleId, openAlertId: alert.id } })
}
</script>

<style scoped>
.rule-center-page {
  min-width: 0;
}

.rule-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.panel-pad,
.mapping-panel {
  padding: 14px;
}

.panel-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-title strong {
  display: block;
  color: var(--soc-text);
  font-size: 15px;
}

.panel-title span {
  color: var(--soc-text-muted);
  font-size: 13px;
}

.drawer-stack {
  display: grid;
  gap: 16px;
}

.hit-list {
  display: grid;
  gap: 10px;
}

.hit-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px;
  border: 1px solid rgba(179, 173, 163, 0.42);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.58);
}

.hit-item strong {
  display: block;
  color: var(--soc-text);
}

.hit-item span {
  color: var(--soc-text-muted);
  font-size: 13px;
}

@media (max-width: 960px) {
  .rule-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .rule-metrics {
    grid-template-columns: 1fr;
  }

  .hit-item {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
