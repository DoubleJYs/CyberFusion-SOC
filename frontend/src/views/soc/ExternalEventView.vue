<template>
  <div class="page-shell">
    <section class="soc-page-hero">
      <div>
        <span class="soc-page-kicker">SECURITY EVIDENCE</span>
        <h1>安全证据</h1>
        <p>这个页面帮你查看和导入来自不同安全工具的原始安全记录，并关联到告警处置。</p>
      </div>
      <div class="soc-page-tags">
        <el-tag>Zeek</el-tag>
        <el-tag>Suricata</el-tag>
        <el-tag>MISP IOC</el-tag>
        <el-tag>CyberChef</el-tag>
      </div>
    </section>

    <section class="external-overview">
      <RiskCard label="安全记录" :value="totals.total" delta="规范化事件记录" tone="medium" />
      <RiskCard label="高风险事件" :value="totals.highRisk" delta="critical / high" tone="high" />
      <RiskCard label="已关联告警" :value="totals.linkedAlerts" delta="进入告警处置" tone="low" />
      <RiskCard label="能力引擎" :value="summary.length" delta="安全工具来源统计" tone="medium" />
    </section>

    <section class="soc-panel panel-pad">
      <div class="soc-filter-bar external-filter">
        <el-input v-model="query.keyword" clearable placeholder="事件、规则、资产、IOC" @keyup.enter="load" />
        <el-select v-model="query.sourceType" clearable placeholder="来源">
          <el-option v-for="source in sourceOptions" :key="source.value" :label="source.label" :value="source.value" />
        </el-select>
        <el-select v-model="query.severity" clearable placeholder="等级">
          <el-option label="严重" value="critical" />
          <el-option label="高危" value="high" />
          <el-option label="中危" value="medium" />
          <el-option label="低危" value="low" />
        </el-select>
        <el-button @click="load">查询</el-button>
        <el-button @click="openImport()">导入数据</el-button>
        <el-button @click="runCyberChef">CyberChef 分析</el-button>
        <el-button @click="runShuffle">Shuffle 通知</el-button>
      </div>
    </section>

    <section v-if="activeSourceContext" class="soc-panel adapter-context-panel">
      <div>
        <span class="adapter-kicker">{{ activeSourceContext.code }} / {{ activeSourceContext.upstream }}</span>
        <h2>{{ activeSourceContext.title }}</h2>
        <p>{{ activeSourceContext.description }}</p>
      </div>
      <dl>
        <div>
          <dt>当前记录</dt>
          <dd>{{ activeSourceSummary?.total || 0 }}</dd>
        </div>
        <div>
          <dt>高风险</dt>
          <dd>{{ activeSourceSummary?.highRisk || 0 }}</dd>
        </div>
        <div>
          <dt>已关联告警</dt>
          <dd>{{ activeSourceSummary?.linkedAlerts || 0 }}</dd>
        </div>
        <div>
          <dt>入湖位置</dt>
          <dd>{{ activeSourceContext.destination }}</dd>
        </div>
      </dl>
      <div class="adapter-actions">
        <el-button @click="openImport(query.sourceType)">导入 {{ activeSourceContext.shortName }} 数据</el-button>
        <el-button @click="runCyberChef">分析字段</el-button>
      </div>
    </section>

    <section v-if="trendHints.length" class="soc-panel trend-hint-panel">
      <div class="trend-hint-head">
        <div>
          <span class="adapter-kicker">TREND ANOMALY</span>
          <h2>趋势异常提示</h2>
          <p>按当前筛选条件对比当前窗口和 7 天均值，帮助判断是否需要优先复核。</p>
        </div>
        <el-tag effect="plain">{{ trendHints.length }} 项</el-tag>
      </div>
      <div class="trend-hint-list">
        <article v-for="item in trendHints" :key="`${item.title}-${item.assetIp}-${item.sourceType}`">
          <div>
            <SeverityBadge :severity="item.severity" />
            <strong>{{ item.title }}</strong>
            <b>{{ item.anomalyScore }}</b>
          </div>
          <span>{{ item.assetIp || '-' }} / {{ item.sourceType || '-' }} / 当前 {{ item.currentCount }} 条，基线 {{ item.baselineCount }} 条，{{ item.changeRatio }}x</span>
          <p>{{ item.reason }}</p>
        </article>
      </div>
    </section>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false">
      <template #default><el-button size="small" @click="load">重试</el-button></template>
    </el-alert>

    <section class="table-panel">
      <el-table v-loading="loading" :data="rows" empty-text="暂无安全记录" @row-click="open">
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
        <span>安全记录 {{ total }} 条</span>
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" layout="total, sizes, prev, pager, next" :total="total" @change="load" />
      </div>
    </section>

    <el-drawer v-model="drawer" title="安全记录详情" size="560px">
      <div v-if="current" class="drawer-stack">
        <div class="soc-drawer-grid">
          <span>事件 ID</span><strong>{{ current.eventUid }}</strong>
          <span>来源</span><strong><DataSourceBadge :source="current.sourceType" /></strong>
          <span>事件类型</span><strong>{{ current.eventType }}</strong>
          <span>等级</span><strong><SeverityBadge :severity="current.severity" /></strong>
          <span>规则</span><strong>{{ current.ruleId || '-' }} / {{ current.ruleName || '-' }}</strong>
          <span>资产</span><strong>{{ current.assetName || '-' }}（{{ current.assetIp || current.destIp || '-' }}）</strong>
          <span>源/目的</span><strong>{{ current.srcIp || '-' }} -> {{ current.destIp || '-' }}</strong>
          <span>目标 URL</span><strong>{{ current.targetUrl || '-' }}</strong>
          <span>处置动作</span><strong>{{ current.action || '-' }}</strong>
          <template v-if="appStore.showAdvanced">
            <span>请求 ID</span><strong>{{ current.requestId || '-' }}</strong>
            <span>Demo Case</span><strong>{{ current.demoCaseId || '-' }}</strong>
            <span>Batch ID</span><strong>{{ current.batchId || '-' }}</strong>
          </template>
          <template v-if="appStore.showRawEvidence">
            <span>关联键</span><strong>{{ current.correlationKey || '-' }}</strong>
          </template>
          <span>IOC</span><strong>{{ current.ioc || '-' }}</strong>
          <span>统一告警</span><strong>{{ current.alertId ? `#${current.alertId}` : '未关联' }}</strong>
          <span>状态</span><strong><StatusBadge :status="current.status" /></strong>
        </div>
        <section class="incident-mini-panel">
          <div class="mini-panel-head">
            <strong>所属事件簇</strong>
            <el-tag effect="plain">{{ relatedIncidents.length }} 个</el-tag>
          </div>
          <article v-for="incident in relatedIncidents" :key="incident.id" class="incident-mini-card">
            <div>
              <strong>{{ incident.clusterNo }} · {{ incident.title }}</strong>
              <span>{{ incident.summary || incident.correlationKey }}</span>
            </div>
            <el-button @click="router.push({ path: '/soc/incidents', query: { keyword: incident.clusterNo } })">查看</el-button>
          </article>
          <el-empty v-if="!relatedIncidents.length" description="暂无关联事件簇" :image-size="72" />
        </section>
        <el-input v-model="remark" type="textarea" :rows="3" placeholder="填写复核说明" />
        <div class="drawer-actions">
          <el-button @click="setStatus('reviewing')">待复核</el-button>
          <el-button type="success" @click="setStatus('linked')">标记已关联</el-button>
          <el-button @click="setStatus('ignored')">忽略</el-button>
          <el-button type="danger" @click="setStatus('closed')">关闭</el-button>
        </div>
        <div v-if="appStore.showRawEvidence" class="event-json">
          <strong>规范化事件</strong>
          <pre>{{ formatJson(current.normalizedEvent) }}</pre>
        </div>
        <div v-if="appStore.showRawEvidence" class="event-json">
          <strong>原始事件</strong>
          <pre>{{ formatJson(current.rawEvent) }}</pre>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="importVisible" title="CyberFusion 多源演示导入" width="720px">
      <el-form label-width="86px">
        <el-form-item label="来源" required>
          <el-select v-model="importForm.sourceType">
            <el-option label="Zeek conn.log" value="zeek" />
            <el-option label="Suricata eve.json" value="suricata" />
            <el-option label="WAF / 网关审计" value="waf" />
            <el-option label="Wazuh demo alert" value="wazuh" />
            <el-option label="MISP IOC" value="misp" />
            <el-option label="Trivy JSON" value="trivy" />
            <el-option label="ZAP JSON" value="zap" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="importForm.content" type="textarea" :rows="11" maxlength="200000" show-word-limit :placeholder="importPlaceholder" />
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

    <el-dialog v-model="chefVisible" title="CyberChef 字段分析结果" width="620px">
      <div v-if="chefResult" class="chef-result">
        <p>{{ chefResult.note }}</p>
        <el-tag v-for="operation in chefResult.suggestedOperations" :key="operation">{{ operation }}</el-tag>
        <div class="event-json">
          <strong>字段发现</strong>
          <pre>{{ JSON.stringify(chefResult.findings, null, 2) }}</pre>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import DataSourceBadge from '@/components/security/DataSourceBadge.vue'
import RiskCard from '@/components/security/RiskCard.vue'
import SeverityBadge from '@/components/security/SeverityBadge.vue'
import StatusBadge from '@/components/security/StatusBadge.vue'
import {
  externalEventSummary,
  analyzeCyberChefField,
  externalEventDetail,
  importCyberFusionEvents,
  listIncidents,
  listExternalEvents,
  sendShuffleDemoNotification,
  trendAnomalies,
  updateExternalEventStatus,
  type CyberChefAnalysis,
  type ExternalEventItem,
  type ExternalSourceSummary,
  type IncidentClusterItem,
  type TrendAnomalyItem
} from '@/api/soc'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', sourceType: '', severity: '', status: '', eventType: '' })
const rows = ref<ExternalEventItem[]>([])
const summary = ref<ExternalSourceSummary[]>([])
const trendHints = ref<TrendAnomalyItem[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const drawer = ref(false)
const current = ref<ExternalEventItem>()
const relatedIncidents = ref<IncidentClusterItem[]>([])
const remark = ref('已按安全记录处置流程复核')
const importVisible = ref(false)
const importing = ref(false)
const importForm = reactive({ sourceType: 'zeek', content: '', linkAlerts: true })
const chefVisible = ref(false)
const chefResult = ref<CyberChefAnalysis>()

const sourceOptions = [
  { label: 'WAF', value: 'waf' },
  { label: 'Wazuh', value: 'wazuh' },
  { label: 'Zeek', value: 'zeek' },
  { label: 'Suricata', value: 'suricata' },
  { label: 'Sigma', value: 'sigma' },
  { label: 'MISP', value: 'misp' },
  { label: 'Trivy', value: 'trivy' },
  { label: 'ZAP', value: 'zap' },
  { label: 'CyberChef', value: 'cyberchef' },
  { label: 'Shuffle', value: 'shuffle' },
]

const sourceContexts: Record<string, { code: string; shortName: string; upstream: string; title: string; description: string; destination: string }> = {
  waf: {
    code: '00',
    shortName: 'WAF',
    upstream: 'WAF / Gateway audit',
    title: 'WAF 网关审计能力内容',
    description: '接收离线 WAF / 网关审计日志，展示识别、拦截、请求 ID、目标 URL 和规则命中证据。',
    destination: '安全记录 / 告警处置',
  },
  wazuh: {
    code: '01',
    shortName: 'Wazuh',
    upstream: 'Wazuh demo alerts',
    title: 'Wazuh 主机告警能力内容',
    description: '接收主机安全告警、认证失败、配置变更和文件变更等事件，归一化后进入告警处置和工单闭环。',
    destination: '安全记录 / 告警处置',
  },
  zeek: {
    code: '03',
    shortName: 'Zeek',
    upstream: 'Zeek conn.log',
    title: 'Zeek 网络日志能力内容',
    description: '面向连接日志、协议元数据和网络会话字段，导入后支持源/目的 IP、服务、IOC 和资产维度分析。',
    destination: '安全记录 / IOC 识别',
  },
  suricata: {
    code: '04',
    shortName: 'Suricata',
    upstream: 'Suricata eve.json',
    title: 'Suricata IDS EVE 能力内容',
    description: '解析 IDS 告警、HTTP/DNS/TLS 等 EVE 记录，按规则、严重性和资产关联到统一告警。',
    destination: '安全记录 / 告警处置',
  },
  sigma: {
    code: '05',
    shortName: 'Sigma',
    upstream: 'Sigma rules',
    title: 'Sigma 检测规则能力内容',
    description: '保存检测规则元数据、规则标识、命中预览和后续告警降噪联动，作为统一规则中心的数据来源。',
    destination: '规则中心 / 命中预览',
  },
  misp: {
    code: '08',
    shortName: 'MISP',
    upstream: 'MISP attributes',
    title: 'MISP IOC 情报能力内容',
    description: '导入 IP、域名、URL、哈希等 IOC，命中资产或事件后提升告警优先级并进入分析闭环。',
    destination: '安全记录 / 告警关联',
  },
  zap: {
    code: '14',
    shortName: 'ZAP',
    upstream: 'ZAP JSON',
    title: 'ZAP Web 风险能力内容',
    description: '接入 Web 扫描发现、插件 ID、风险等级、URL 和修复建议，用于 Web 风险复核和报告输出。',
    destination: '安全记录 / 漏洞风险',
  },
}

const domainContexts: Record<string, { code: string; shortName: string; upstream: string; title: string; description: string; destination: string; sources: string[] }> = {
  network: {
    code: '02',
    shortName: '网络检测',
    upstream: 'Zeek + Suricata',
    title: '网络检测中心',
    description: '融合 Zeek 连接日志和 Suricata IDS EVE，将网络连接、协议元数据和告警信号统一成可处置事件。',
    destination: '安全记录 / 告警处置',
    sources: ['zeek', 'suricata'],
  },
}

const totals = computed(() => summary.value.reduce(
  (acc, item) => ({
    total: acc.total + item.total,
    highRisk: acc.highRisk + item.highRisk,
    linkedAlerts: acc.linkedAlerts + item.linkedAlerts
  }),
  { total: 0, highRisk: 0, linkedAlerts: 0 }
))

const activeDomainContext = computed(() => {
  const domain = routeQuery('domain')
  return domain ? domainContexts[domain] : undefined
})
const activeSourceContext = computed(() => activeDomainContext.value || (query.sourceType ? sourceContexts[query.sourceType] : undefined))
const activeSourceSummary = computed(() => {
  const domain = activeDomainContext.value
  if (domain) {
    return summary.value
      .filter((item) => domain.sources.includes(item.sourceType))
      .reduce(
        (acc, item) => ({
          sourceType: domain.shortName,
          total: acc.total + item.total,
          highRisk: acc.highRisk + item.highRisk,
          linkedAlerts: acc.linkedAlerts + item.linkedAlerts,
        }),
        { sourceType: domain.shortName, total: 0, highRisk: 0, linkedAlerts: 0 }
      )
  }
  return summary.value.find((item) => item.sourceType === query.sourceType)
})

const importPlaceholder = computed(() => {
  const samples: Record<string, string> = {
    waf: '{"sourceType":"waf","eventType":"waf_block","severity":"high","assetIp":"10.20.1.15","targetUrl":"https://demo.internal.local/admin","httpMethod":"POST","httpStatus":403,"action":"block","ruleId":"WAF-DEMO-1001","ruleName":"Admin route protected by WAF policy","engine":"CyberFusion Demo Gateway","requestId":"waf-demo-req-0001","demoCaseId":"demo-range-waf-block","batchId":"DEMO-RANGE-OFFLINE-V1","evidenceSummary":"Demo gateway blocked restricted admin access before it reached prod-app-01.","timestamp":"2026-06-18T10:00:00+08:00","sourceIp":"203.0.113.80"}',
    zeek: '#fields\\tts\\tuid\\tid.orig_h\\tid.resp_h\\tproto\\tservice\\n1719490200.1\\tC8demo\\t203.0.113.77\\t10.20.1.15\\ttcp\\tssh',
    suricata: '{"timestamp":"2026-05-27T22:45:00+08:00","event_type":"alert","src_ip":"203.0.113.88","dest_ip":"10.20.1.15","alert":{"signature_id":20260527,"signature":"ET SCAN Demo inbound scan","severity":1}}',
    wazuh: '{"timestamp":"2026-05-27T22:46:00+08:00","id":"wazuh-demo-1","rule":{"id":"5715","level":10,"description":"sshd authentication failure"},"agent":{"name":"edge-fw-01","ip":"10.20.1.15"},"data":{"srcip":"203.0.113.88"}}',
    misp: '{"uuid":"misp-demo-1","type":"ip-src","category":"Network activity","value":"203.0.113.88"}',
    trivy: '{"Results":[{"Target":"registry.local/app:demo","Vulnerabilities":[{"VulnerabilityID":"CVE-2026-DEMO","PkgName":"openssl","InstalledVersion":"1.1.1","FixedVersion":"1.1.1w","Severity":"HIGH"}]}]}',
    zap: '{"pluginid":"10021","name":"X-Content-Type-Options Header Missing","riskdesc":"Medium","url":"https://demo.example.local/login"}'
  }
  return samples[importForm.sourceType] || ''
})

watch(
  () => [route.query.sourceType, route.query.eventType, route.query.tool, route.query.domain, route.query.keyword, route.query.assetIp, route.query.openEventUid],
  () => {
    applyRouteQuery()
    void load()
    if (route.query.tool === 'cyberchef') {
      void runCyberChef()
    }
  },
  { immediate: true }
)

function routeQuery(name: string) {
  const value = route.query[name]
  return typeof value === 'string' ? value : ''
}

function applyRouteQuery() {
  query.sourceType = routeQuery('sourceType')
  query.eventType = routeQuery('eventType')
  query.keyword = routeQuery('keyword') || routeQuery('assetIp')
  query.pageNum = 1
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [listRes, summaryRes, trendRes] = await Promise.all([
      listExternalEvents(query),
      externalEventSummary(),
      trendAnomalies({
        assetIp: routeQuery('assetIp') || undefined,
        sourceType: query.sourceType || undefined,
        eventType: query.eventType || undefined,
        severity: query.severity || undefined,
        limit: 3,
      }).catch(() => undefined)
    ])
    rows.value = listRes.data.data.records
    total.value = listRes.data.data.total
    summary.value = summaryRes.data.data
    trendHints.value = trendRes?.data.data || []
    openRouteEventIfNeeded()
  } catch {
    error.value = '安全记录加载失败，请检查权限或后端服务状态。'
  } finally {
    loading.value = false
  }
}

async function open(row: ExternalEventItem) {
  current.value = row
  drawer.value = true
  relatedIncidents.value = []
  try {
    const res = await externalEventDetail(row.id)
    current.value = res.data.data
    await loadRelatedIncidents(res.data.data)
  } catch {
    ElMessage.warning('事件详情加载失败，已显示列表摘要。')
    await loadRelatedIncidents(row)
  }
}

async function loadRelatedIncidents(row: ExternalEventItem) {
  const keyword = row.correlationKey || row.batchId || row.demoCaseId || row.assetIp || row.eventUid
  if (!keyword) return
  try {
    const res = await listIncidents({ pageNum: 1, pageSize: 5, keyword })
    relatedIncidents.value = res.data.data.records
  } catch {
    relatedIncidents.value = []
  }
}

function openRouteEventIfNeeded() {
  const eventUid = routeQuery('openEventUid')
  if (!eventUid) return
  const matched = rows.value.find((item) => item.eventUid === eventUid)
  if (matched) open(matched)
}

async function setStatus(status: string) {
  if (!current.value) return
  await updateExternalEventStatus(current.value.id, status, remark.value)
  ElMessage.success('安全记录状态已更新')
  drawer.value = false
  await load()
}

function openImport(sourceType?: string) {
  if (sourceType && sourceOptions.some((item) => item.value === sourceType)) {
    importForm.sourceType = sourceType
  }
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
    const res = await importCyberFusionEvents({
      sourceType: importForm.sourceType,
      content: importForm.content.trim(),
      linkAlerts: importForm.linkAlerts
    })
    const result = res.data.data
    ElMessage.success(`导入事件 ${result.importedEvents} 条，漏洞 ${result.importedVulnerabilities || 0} 条，关联告警 ${result.linkedAlerts} 条`)
    if (result.skippedLines) {
      ElMessage.warning(`跳过 ${result.skippedLines} 行`)
    }
    importVisible.value = false
    await load()
  } finally {
    importing.value = false
  }
}

async function runCyberChef() {
  const value = current.value?.ioc || current.value?.srcIp || current.value?.destIp || rows.value[0]?.ioc || rows.value[0]?.srcIp || 'https%3A%2F%2Fdemo.example.local%2Flogin%3Fsrc%3D203.0.113.88'
  const res = await analyzeCyberChefField(value, 'ioc_or_network_field')
  chefResult.value = res.data.data
  chefVisible.value = true
}

async function runShuffle() {
  const res = await sendShuffleDemoNotification()
  ElMessage.success(res.data.data.message)
  await load()
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
.adapter-context-panel {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(360px, 1fr) auto;
  gap: 18px;
  align-items: center;
  padding: 16px;
}
.adapter-context-panel h2 {
  margin: 4px 0 8px;
  color: var(--soc-text);
  font-size: 20px;
}
.adapter-context-panel p {
  margin: 0;
  color: var(--soc-text-muted);
  font-size: 13px;
  line-height: 1.6;
}
.adapter-kicker {
  color: var(--soc-warm-strong);
  font-size: 12px;
  font-weight: 760;
}
.adapter-context-panel dl {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}
.adapter-context-panel dl div {
  min-width: 0;
  padding: 10px;
  border: 1px solid rgba(190, 183, 171, 0.46);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.5);
}
.adapter-context-panel dt {
  color: var(--soc-text-muted);
  font-size: 11px;
}
.adapter-context-panel dd {
  overflow: hidden;
  margin: 4px 0 0;
  color: var(--soc-text);
  font-size: 15px;
  font-weight: 760;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.adapter-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.trend-hint-panel {
  display: grid;
  gap: 12px;
  padding: 16px;
}
.trend-hint-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}
.trend-hint-head h2 {
  margin: 4px 0 6px;
  color: var(--soc-text);
  font-size: 18px;
}
.trend-hint-head p {
  margin: 0;
  color: var(--soc-text-muted);
  font-size: 13px;
  line-height: 1.55;
}
.trend-hint-list {
  display: grid;
  gap: 8px;
}
.trend-hint-list article {
  display: grid;
  gap: 6px;
  padding: 10px;
  border: 1px solid rgba(179, 173, 163, 0.34);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.62);
}
.trend-hint-list article > div {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}
.trend-hint-list strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.trend-hint-list b {
  color: var(--soc-high);
  font-size: 20px;
}
.trend-hint-list span,
.trend-hint-list p {
  margin: 0;
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.55;
}
.trend-hint-list p {
  color: var(--soc-warm-strong);
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
.incident-mini-panel {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid rgba(179, 173, 163, 0.46);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.72);
}
.mini-panel-head,
.incident-mini-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.incident-mini-card {
  padding: 12px;
  border: 1px solid var(--soc-border);
  border-radius: 8px;
}
.incident-mini-card div {
  display: grid;
  gap: 4px;
  min-width: 0;
}
.incident-mini-card span {
  overflow: hidden;
  color: var(--soc-text-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
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
.chef-result {
  display: grid;
  gap: 12px;
}
.chef-result p {
  margin: 0;
  color: var(--soc-text-muted);
  line-height: 1.6;
}
@media (max-width: 1100px) {
  .external-overview {
    grid-template-columns: 1fr;
  }
  .adapter-context-panel {
    grid-template-columns: 1fr;
  }
  .adapter-context-panel dl {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 720px) {
  .adapter-context-panel dl {
    grid-template-columns: 1fr;
  }
}
</style>
