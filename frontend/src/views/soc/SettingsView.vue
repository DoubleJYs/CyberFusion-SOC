<template>
  <div class="page-shell">
    <section class="soc-panel panel-pad integration-board">
      <div class="panel-title integration-title">
        <div>
          <strong>CyberFusion 能力模块</strong>
          <p>主系统把多源能力引擎统一编排到事件、告警、漏洞、工单、报表和自动化闭环；Wazuh 只是其中一个主机威胁引擎。</p>
        </div>
        <el-tag type="success" effect="plain">核心 {{ coreAdapters.length }}</el-tag>
      </div>
      <div class="integration-summary">
        <div v-for="item in integrationSummary" :key="item.label" class="summary-tile">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.tip }}</small>
        </div>
      </div>
      <div class="adapter-grid">
        <article v-for="adapter in coreAdapters" :key="adapter.id" class="adapter-card">
          <div class="adapter-head">
            <span class="adapter-no">{{ adapter.no }}</span>
            <div>
              <strong>{{ adapter.name }}</strong>
              <small>{{ adapter.lane }}</small>
            </div>
            <el-tag :type="adapter.tone" effect="plain">{{ adapter.status }}</el-tag>
          </div>
          <p>{{ adapter.mode }}</p>
          <div class="adapter-meta">
            <span>{{ adapter.evidence }}</span>
            <strong v-if="adapter.count !== undefined">{{ adapter.count }} 条</strong>
          </div>
        </article>
      </div>
      <div class="optional-row">
        <span>可选外部接入</span>
        <el-tag v-for="item in optionalAdapters" :key="item" effect="plain">{{ item }}</el-tag>
        <span class="excluded-note">15 Juice Shop 保留为培训靶场，不进入主线。</span>
      </div>
    </section>

    <section class="settings-grid">
      <div class="soc-panel panel-pad">
        <div class="panel-title">
          <strong>01 Wazuh 连接</strong>
          <el-button type="primary" :loading="checking" @click="check">检查连接</el-button>
        </div>
        <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="panel-alert" />
        <div v-if="hasHealth" class="health-grid">
          <div class="health-card">
            <span>Manager</span>
            <strong :class="statusClass(managerStatus)">{{ managerStatus }}</strong>
          </div>
          <div class="health-card">
            <span>Indexer</span>
            <strong :class="statusClass(indexerStatus)">{{ indexerStatus }}</strong>
          </div>
          <div class="health-card">
            <span>当前数据来源</span>
            <DataSourceBadge :source="dataSource" />
          </div>
        </div>
        <el-empty v-else description="尚未检查 Wazuh 连接" :image-size="76" />
        <pre v-if="hasHealth">{{ healthText }}</pre>
      </div>
      <div class="soc-panel panel-pad">
        <div class="panel-title"><strong>能力任务</strong><span>核心能力引擎与可选导入的运行状态</span></div>
        <el-table v-loading="loading" :data="tasks" size="small" empty-text="暂无同步任务">
          <el-table-column prop="taskName" label="任务" />
          <el-table-column label="来源" width="120"><template #default="{ row }"><DataSourceBadge :source="row.sourceType" /></template></el-table-column>
          <el-table-column prop="lastStatus" label="状态" width="140" />
        </el-table>
      </div>
    </section>
    <section class="soc-panel panel-pad">
      <div class="panel-title"><strong>凭据型连接配置</strong><span>当前只有需要外部凭据的 Wazuh 走此表，导入型能力引擎走证据中心</span></div>
      <el-table v-loading="loading" :data="configs" empty-text="暂无连接配置">
        <el-table-column prop="configName" label="名称" width="160" />
        <el-table-column prop="managerUrl" label="Manager" />
        <el-table-column prop="indexerUrl" label="Indexer" />
        <el-table-column prop="authMode" label="认证模式" width="100" />
        <el-table-column prop="lastStatus" label="状态" width="110" />
      </el-table>
    </section>
    <section class="notification-grid">
      <div class="soc-panel panel-pad">
        <div class="panel-title"><strong>通知通道</strong><span>邮件优先，密钥由运行环境托管</span></div>
        <el-table v-loading="loading" :data="channels" size="small" empty-text="暂无通知通道">
          <el-table-column prop="channelName" label="通道" min-width="170" />
          <el-table-column prop="channelType" label="类型" width="86" />
          <el-table-column prop="target" label="目标" min-width="180" show-overflow-tooltip />
          <el-table-column label="最低等级" width="92"><template #default="{ row }"><SeverityBadge :severity="row.minSeverity" /></template></el-table-column>
          <el-table-column prop="sendMode" label="模式" width="88" />
          <el-table-column label="状态" width="96"><template #default="{ row }"><StatusBadge :status="row.lastStatus || 'READY'" /></template></el-table-column>
          <el-table-column label="操作" width="96" fixed="right">
            <template #default="{ row }">
              <el-button size="small" :loading="testingId === row.id" @click="test(row.id)">测试</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="soc-panel panel-pad">
        <div class="panel-title"><strong>通知日志</strong><span>告警、工单、报表动作自动记录</span></div>
        <el-table v-loading="loading" :data="notificationLogRows" size="small" empty-text="暂无通知日志">
          <el-table-column prop="eventType" label="事件" width="130" />
          <el-table-column label="等级" width="86"><template #default="{ row }"><SeverityBadge :severity="row.severity || 'medium'" /></template></el-table-column>
          <el-table-column prop="title" label="标题" min-width="190" show-overflow-tooltip />
          <el-table-column prop="target" label="目标" min-width="170" show-overflow-tooltip />
          <el-table-column label="状态" width="96"><template #default="{ row }"><StatusBadge :status="row.status" /></template></el-table-column>
          <el-table-column prop="sentAt" label="时间" width="170" />
        </el-table>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import DataSourceBadge from '@/components/security/DataSourceBadge.vue'
import SeverityBadge from '@/components/security/SeverityBadge.vue'
import StatusBadge from '@/components/security/StatusBadge.vue'
import {
  notificationChannels,
  notificationLogs,
  externalEventSummary,
  syncTasks,
  testNotificationChannel,
  wazuhConfigs,
  wazuhHealth,
  type ExternalSourceSummary,
  type NotificationChannelItem,
  type NotificationLogItem
} from '@/api/soc'

const configs = ref<unknown[]>([])
const tasks = ref<unknown[]>([])
const channels = ref<NotificationChannelItem[]>([])
const notificationLogRows = ref<NotificationLogItem[]>([])
const sourceSummary = ref<ExternalSourceSummary[]>([])
const health = ref<Record<string, unknown>>({})
const loading = ref(false)
const checking = ref(false)
const testingId = ref<number>()
const error = ref('')
const healthText = computed(() => JSON.stringify(health.value, null, 2))
const hasHealth = computed(() => Object.keys(health.value).length > 0)
const managerStatus = computed(() => statusOf(health.value.manager))
const indexerStatus = computed(() => statusOf(health.value.indexer))
const dataSource = computed(() => {
  if (managerStatus.value === 'CONNECTED' && indexerStatus.value === 'CONNECTED') return 'wazuh-indexer'
  if (tasks.value.some((item) => typeof item === 'object' && item && String((item as { sourceType?: string }).sourceType || '').includes('import'))) return 'import'
  return 'mock'
})
const importedSourceTypes = computed(() => sourceSummary.value.filter((item) => item.total > 0).map((item) => item.sourceType))
const integrationSummary = computed(() => [
  { label: '核心模块', value: coreAdapters.value.length, tip: '01/03/04/05/06/08/13/14/16' },
  { label: '已有数据源', value: importedSourceTypes.value.length, tip: importedSourceTypes.value.join(' / ') || '待导入' },
  { label: '通知通道', value: channels.value.length, tip: 'Shuffle / email dry-run' },
  { label: '接入任务', value: tasks.value.length, tip: '同步、导入、分析、通知' }
])
const coreAdapters = computed(() => [
  {
    id: 'wazuh',
    no: '01',
    name: 'Wazuh',
    lane: 'XDR / 主机告警',
    status: configs.value.length ? '配置已登记' : '待配置',
    tone: 'success',
    mode: 'Manager / Indexer 连接检查，demo alerts 进入统一告警。',
    evidence: '告警、FIM、基线、资产',
    count: countOf('wazuh')
  },
  {
    id: 'zeek',
    no: '03',
    name: 'Zeek',
    lane: '网络日志',
    status: countOf('zeek') ? '已有导入' : '导入就绪',
    tone: countOf('zeek') ? 'success' : 'primary',
    mode: 'conn.log / JSON 归一化为外部事件，可联动统一告警。',
    evidence: '外部事件导入',
    count: countOf('zeek')
  },
  {
    id: 'suricata',
    no: '04',
    name: 'Suricata',
    lane: 'IDS 告警',
    status: countOf('suricata') ? '已有导入' : '导入就绪',
    tone: countOf('suricata') ? 'success' : 'primary',
    mode: 'eve.json 规范化，支持创建统一告警。',
    evidence: '外部事件 + 告警',
    count: countOf('suricata')
  },
  {
    id: 'sigma',
    no: '05',
    name: 'Sigma',
    lane: '检测规则',
    status: '规则导入就绪',
    tone: 'primary',
    mode: '规则记录按 detection_rule 进入统一规则/外部信号池。',
    evidence: '规则中心 P2',
    count: countOf('sigma')
  },
  {
    id: 'trivy',
    no: '06',
    name: 'Trivy',
    lane: '漏洞扫描',
    status: 'JSON 导入就绪',
    tone: 'primary',
    mode: 'Trivy JSON 写入漏洞中心，并纳入资产风险评分。',
    evidence: '漏洞中心',
    count: countOf('trivy')
  },
  {
    id: 'misp',
    no: '08',
    name: 'MISP',
    lane: 'IOC 情报',
    status: countOf('misp') ? '已有导入' : 'IOC 导入就绪',
    tone: countOf('misp') ? 'success' : 'primary',
    mode: 'MISP attribute 归一化 IOC，命中后提升告警优先级。',
    evidence: '威胁情报 / IOC',
    count: countOf('misp')
  },
  {
    id: 'cyberchef',
    no: '13',
    name: 'CyberChef',
    lane: '字段分析',
    status: '分析入口就绪',
    tone: 'success',
    mode: '对 IOC、URL、编码字段给出分析建议，不外发数据。',
    evidence: '外部事件分析',
    count: undefined
  },
  {
    id: 'zap',
    no: '14',
    name: 'ZAP',
    lane: 'Web 风险',
    status: countOf('zap') ? '已有导入' : 'JSON 导入就绪',
    tone: countOf('zap') ? 'success' : 'primary',
    mode: 'ZAP JSON 归一化为 Web finding，并可联动告警。',
    evidence: '外部事件 / 漏洞',
    count: countOf('zap')
  },
  {
    id: 'shuffle',
    no: '16',
    name: 'Shuffle',
    lane: 'SOAR 通知',
    status: channels.value.length ? 'dry-run 就绪' : '待配置',
    tone: channels.value.length ? 'success' : 'warning',
    mode: '告警、工单、报表事件写入通知日志，默认不触发真实外部请求。',
    evidence: '自动化闭环',
    count: undefined
  }
])
const optionalAdapters = ['02 Security Onion external', '07 Falco JSON', '09 OpenCTI-lite', '10 osquery result', '11 Velociraptor result', '12 Cowrie logs']

onMounted(load)
async function load() {
  loading.value = true
  error.value = ''
  try {
    const [configRes, taskRes, channelRes, logRes, summaryRes] = await Promise.all([
      wazuhConfigs(),
      syncTasks(),
      notificationChannels(),
      notificationLogs({ pageNum: 1, pageSize: 6 }),
      externalEventSummary()
    ])
    configs.value = configRes.data.data
    tasks.value = taskRes.data.data
    channels.value = channelRes.data.data
    notificationLogRows.value = logRes.data.data.records
    sourceSummary.value = summaryRes.data.data
  } catch {
    error.value = '系统配置加载失败，请检查权限或后端服务状态。'
  } finally {
    loading.value = false
  }
}
async function check() {
  checking.value = true
  error.value = ''
  try {
    const res = await wazuhHealth()
    health.value = res.data.data
  } catch {
    error.value = 'Wazuh 连接检查失败，请确认运行环境变量和服务状态。'
  } finally {
    checking.value = false
  }
}
async function test(id: number) {
  testingId.value = id
  try {
    await testNotificationChannel(id)
    ElMessage.success('通知测试已写入发送日志')
    await load()
  } finally {
    testingId.value = undefined
  }
}
function statusOf(value: unknown) {
  if (typeof value !== 'object' || !value) return 'UNKNOWN'
  return String((value as { status?: string }).status || 'UNKNOWN')
}
function statusClass(status: string) {
  return status === 'CONNECTED' ? 'ok' : 'warn'
}
function countOf(sourceType: string) {
  return sourceSummary.value.find((item) => item.sourceType === sourceType)?.total || 0
}
</script>

<style scoped>
.integration-board {
  display: grid;
  gap: 14px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.86), rgba(255, 247, 235, 0.68)),
    var(--soc-glass);
}
.integration-title {
  align-items: flex-start;
}
.integration-title p {
  margin: 6px 0 0;
  color: var(--soc-text-muted);
  font-size: 13px;
  line-height: 1.5;
}
.integration-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}
.summary-tile {
  display: grid;
  gap: 4px;
  min-height: 76px;
  border: 1px solid var(--soc-border);
  border-radius: var(--soc-radius);
  background: rgba(255, 255, 255, 0.62);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.78);
  padding: 12px;
}
.summary-tile span,
.summary-tile small {
  color: var(--soc-text-muted);
}
.summary-tile strong {
  color: var(--soc-text);
  font-size: 24px;
  line-height: 1;
}
.adapter-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.adapter-card {
  display: grid;
  gap: 10px;
  min-height: 148px;
  border: 1px solid var(--soc-border);
  border-radius: var(--soc-radius-card);
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.76), rgba(247, 242, 234, 0.58));
  box-shadow: 0 12px 26px rgba(91, 77, 53, 0.08), inset 0 1px 0 rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(16px) saturate(1.1);
  padding: 12px;
}
.adapter-head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 10px;
  align-items: start;
}
.adapter-no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 28px;
  border-radius: var(--soc-radius);
  background: linear-gradient(135deg, rgba(255, 242, 220, 0.92), rgba(229, 246, 249, 0.88));
  color: var(--soc-warm-strong);
  font-weight: 700;
}
.adapter-head strong,
.adapter-head small {
  display: block;
}
.adapter-head small,
.adapter-card p,
.adapter-meta span {
  color: var(--soc-text-muted);
}
.adapter-card p {
  min-height: 42px;
  margin: 0;
  line-height: 1.5;
}
.adapter-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 12px;
}
.adapter-meta strong {
  color: var(--soc-warm-strong);
}
.optional-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  color: var(--soc-text-muted);
  font-size: 13px;
}
.excluded-note {
  margin-left: auto;
}
.settings-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr);
  gap: 14px;
}
.notification-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.15fr);
  gap: 14px;
}
.panel-pad { padding: 14px; }
.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}
.panel-title span {
  color: var(--soc-text-muted);
  font-size: 13px;
}
.panel-alert {
  margin-bottom: 12px;
}
.health-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}
.health-card {
  display: grid;
  gap: 6px;
  border: 1px solid var(--soc-border);
  border-radius: var(--soc-radius);
  background: var(--soc-surface-raised);
  padding: 12px;
}
.health-card span {
  color: var(--soc-text-muted);
  font-size: 12px;
}
.health-card strong {
  font-size: 14px;
}
.health-card .ok {
  color: var(--soc-success);
}
.health-card .warn {
  color: var(--soc-medium);
}
pre {
  min-height: 220px;
  margin: 0;
  overflow: auto;
  color: var(--soc-text-muted);
  white-space: pre-wrap;
}
@media (max-width: 980px) {
  .integration-summary,
  .adapter-grid,
  .settings-grid,
  .notification-grid {
    grid-template-columns: 1fr;
  }
  .health-grid {
    grid-template-columns: 1fr;
  }
}
</style>
