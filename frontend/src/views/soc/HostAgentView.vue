<template>
  <div class="page-shell host-agent-page">
    <section class="soc-page-hero host-agent-hero">
      <div>
        <span class="soc-page-kicker">FULL EXPERT VIEW / AGENT OPS</span>
        <h1>Agent 管理</h1>
        <p>统一管理当前 macOS 真实采集链路，并为 Windows 宿主机采集保留验收入口；未接入 Windows 实机前不把 fixture 当作真实结果。</p>
      </div>
      <div class="host-agent-actions">
        <el-tag :type="realDataReady ? 'success' : 'warning'" effect="light">
          {{ realDataReady ? '真实数据已接入' : '等待真实采集' }}
        </el-tag>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </section>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false">
      <template #default>
        <el-button size="small" @click="load">重试</el-button>
      </template>
    </el-alert>

    <section class="agent-command-grid">
      <article v-for="item in kpis" :key="item.label" class="soc-panel agent-kpi-card" :class="item.tone">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.hint }}</small>
      </article>
    </section>

    <section class="agent-readiness-grid">
      <section class="soc-panel panel-pad platform-panel">
        <div class="panel-head compact">
          <div>
            <h2>当前验收边界</h2>
            <p>Mac 负责当前真实闭环验证，Windows 只保留安装、采集和 Docker 宿主机验收位。</p>
          </div>
        </div>
        <div class="platform-card-grid">
          <article v-for="platform in platformCards" :key="platform.key" class="platform-card" :class="platform.tone">
            <div>
              <span>{{ platform.kicker }}</span>
              <strong>{{ platform.title }}</strong>
              <small>{{ platform.description }}</small>
            </div>
            <el-tag :type="platform.tagType" effect="light">{{ platform.status }}</el-tag>
          </article>
        </div>
      </section>

      <section class="soc-panel panel-pad gate-panel">
        <div class="panel-head compact">
          <div>
            <h2>真实化守门</h2>
            <p>专家视图只把 Host Agent 真实来源作为上线依据。</p>
          </div>
        </div>
        <div class="gate-list">
          <article v-for="gate in readinessGates" :key="gate.label">
            <span class="gate-dot" :class="gate.tone" />
            <div>
              <strong>{{ gate.label }}</strong>
              <small>{{ gate.hint }}</small>
            </div>
            <el-tag :type="gate.tagType" effect="plain">{{ gate.status }}</el-tag>
          </article>
        </div>
      </section>
    </section>

    <section class="soc-panel panel-pad source-panel">
      <div class="panel-head">
        <div>
          <h2>数据源健康度</h2>
          <p>按来源拆分在线 Agent、资产、事件、FIM 和基线失败，便于判断真实采集是否覆盖当前主线。</p>
        </div>
        <el-button text @click="activeSource = 'all'">查看全部</el-button>
      </div>
      <div class="source-health-grid">
        <button
          v-for="source in sourceRows"
          :key="source.sourceType"
          class="source-health-card"
          :class="{ active: activeSource === source.sourceType }"
          type="button"
          @click="activeSource = source.sourceType"
        >
          <div class="source-health-card-head">
            <div>
              <strong>{{ source.label }}</strong>
              <span>{{ source.sourceType }}</span>
            </div>
            <el-tag :type="sourceStatusType(source.status)" effect="light">{{ sourceStatusLabel(source.status) }}</el-tag>
          </div>
          <dl>
            <div>
              <dt>Agent</dt>
              <dd>{{ source.onlineCount }}/{{ source.agentCount }}</dd>
            </div>
            <div>
              <dt>资产</dt>
              <dd>{{ source.assetCount }}</dd>
            </div>
            <div>
              <dt>事件</dt>
              <dd>{{ source.eventCount24h }}</dd>
            </div>
            <div>
              <dt>FIM</dt>
              <dd>{{ source.fimCount24h }}</dd>
            </div>
          </dl>
          <div class="source-progress">
            <span :style="{ width: `${sourceProgress(source)}%` }" />
          </div>
        </button>
      </div>
    </section>

    <section class="agent-main-grid">
      <section class="soc-panel panel-pad agent-table-panel">
        <div class="panel-head">
          <div>
            <h2>采集器列表</h2>
            <p>按系统、状态和关键字定位采集器，点击行进入批次、事件、拒收和队列详情。</p>
          </div>
        </div>
        <div class="agent-filter-bar">
          <el-input
            v-model="query"
            :prefix-icon="Search"
            clearable
            placeholder="搜索 Agent、主机名、IP 或版本"
          />
          <el-select v-model="osFilter" placeholder="系统" clearable>
            <el-option label="macOS" value="macos" />
            <el-option label="Windows" value="windows" />
            <el-option label="Linux" value="linux" />
          </el-select>
          <el-select v-model="statusFilter" placeholder="状态" clearable>
            <el-option label="在线" value="online" />
            <el-option label="离线" value="offline" />
            <el-option label="异常" value="warning" />
          </el-select>
        </div>
        <div class="table-scroll">
          <el-table
            v-loading="loading"
            :data="filteredAgents"
            empty-text="暂无匹配的主机 Agent"
            @row-click="openAgent"
          >
            <el-table-column label="状态" width="96">
              <template #default="{ row }">
                <StatusBadge :status="row.status" />
              </template>
            </el-table-column>
            <el-table-column prop="agentName" label="Agent" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="agent-name-cell">
                  <strong>{{ row.agentName || row.hostname || row.agentId }}</strong>
                  <small>{{ row.agentId }}</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="hostname" label="主机" min-width="150" show-overflow-tooltip />
            <el-table-column label="系统" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ osLabel(row.osType) }} {{ row.osVersion || '' }}</template>
            </el-table-column>
            <el-table-column label="来源" width="120">
              <template #default="{ row }"><DataSourceBadge :source="agentSourceType(row)" /></template>
            </el-table-column>
            <el-table-column prop="agentVersion" label="版本" width="112" />
            <el-table-column label="地址" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ firstJsonValue(row.ipAddressesJson) || row.lastIp || '-' }}</template>
            </el-table-column>
            <el-table-column label="队列" width="138">
              <template #default="{ row }">{{ row.queueDepth || 0 }} / {{ formatBytes(row.queueBytes) }}</template>
            </el-table-column>
            <el-table-column label="采集/上报" width="144">
              <template #default="{ row }">{{ row.collectedCount || 0 }} / {{ row.sentCount || 0 }}</template>
            </el-table-column>
            <el-table-column label="失败" width="90">
              <template #default="{ row }">
                <el-tag :type="(row.failedCount || 0) > 0 ? 'warning' : 'success'" effect="plain">
                  {{ row.failedCount || 0 }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="lastSeenAt" label="最后心跳" min-width="180" />
            <el-table-column label="操作" width="112">
              <template #default="{ row }">
                <el-button size="small" @click.stop="openAgent(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <aside class="soc-panel panel-pad agent-side-panel">
        <div class="panel-head compact">
          <div>
            <h2>采集链路</h2>
            <p>从 Agent 到 SOC 对象的最短可验证路径。</p>
          </div>
        </div>
        <div class="ingest-step-list">
          <article v-for="step in ingestSteps" :key="step.label">
            <span>{{ step.index }}</span>
            <div>
              <strong>{{ step.label }}</strong>
              <small>{{ step.hint }}</small>
            </div>
          </article>
        </div>
        <div class="side-actions">
          <el-button plain @click="go('/soc/assets')">资产视图</el-button>
          <el-button plain @click="go('/soc/external-events')">证据中心</el-button>
          <el-button plain @click="go('/soc/fim')">FIM</el-button>
          <el-button plain @click="go('/soc/baselines')">基线</el-button>
        </div>
      </aside>
    </section>

    <section class="agent-diagnostics-grid">
      <section class="soc-panel panel-pad">
        <div class="panel-head">
          <div>
            <h2>最近上报事件</h2>
            <p>真实主机事件进入告警和事件簇前的证据流。</p>
          </div>
        </div>
        <div class="table-scroll compact-table-scroll">
          <el-table :data="recentEvents" empty-text="暂无真实主机事件">
            <el-table-column prop="eventUid" label="事件 ID" min-width="180" show-overflow-tooltip />
            <el-table-column label="来源" width="116">
              <template #default="{ row }"><DataSourceBadge :source="row.sourceType" /></template>
            </el-table-column>
            <el-table-column prop="eventType" label="类型" width="132" show-overflow-tooltip />
            <el-table-column label="等级" width="92">
              <template #default="{ row }"><SeverityBadge :severity="row.severity" /></template>
            </el-table-column>
            <el-table-column prop="ruleName" label="规则" min-width="220" show-overflow-tooltip />
            <el-table-column prop="assetName" label="资产" min-width="140" show-overflow-tooltip />
            <el-table-column prop="assetIp" label="IP" width="140" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }"><StatusBadge :status="row.status" /></template>
            </el-table-column>
            <el-table-column prop="eventTime" label="时间" min-width="180" />
          </el-table>
        </div>
      </section>

      <section class="soc-panel panel-pad">
        <div class="panel-head">
          <div>
            <h2>运行诊断</h2>
            <p>快速定位离线、队列积压、拒收和平台断链。</p>
          </div>
        </div>
        <div class="diagnostic-list">
          <article v-for="item in diagnostics" :key="item.label" :class="item.tone">
            <div>
              <strong>{{ item.label }}</strong>
              <small>{{ item.hint }}</small>
            </div>
            <span>{{ item.value }}</span>
          </article>
        </div>
      </section>
    </section>

    <el-drawer v-model="drawer" title="Agent 详情" size="720px">
      <div v-loading="detailLoading" class="agent-detail-stack">
        <template v-if="detail">
          <section class="detail-summary">
            <div>
              <span>{{ osLabel(detail.agent.osType) }} / {{ agentSourceType(detail.agent) }}</span>
              <strong>{{ detail.agent.agentName || detail.agent.hostname || detail.agent.agentId }}</strong>
              <small>{{ detail.agent.agentId }}</small>
            </div>
            <StatusBadge :status="detail.agent.status" />
          </section>

          <section class="detail-section">
            <h3>运行状态</h3>
            <div class="soc-drawer-grid">
              <span>主机</span><strong>{{ detail.agent.hostname || '-' }}</strong>
              <span>系统版本</span><strong>{{ detail.agent.osVersion || '-' }}</strong>
              <span>Agent 版本</span><strong>{{ detail.agent.agentVersion || '-' }}</strong>
              <span>最后心跳</span><strong>{{ detail.agent.lastSeenAt || '-' }}</strong>
              <span>来源 IP</span><strong>{{ detail.agent.lastIp || firstJsonValue(detail.agent.ipAddressesJson) || '-' }}</strong>
              <span>本地队列</span><strong>{{ detail.agent.queueDepth || 0 }} 条 / {{ formatBytes(detail.agent.queueBytes) }}</strong>
              <span>采集/上报</span><strong>{{ detail.agent.collectedCount || 0 }} / {{ detail.agent.sentCount || 0 }}</strong>
              <span>失败次数</span><strong>{{ detail.agent.failedCount || 0 }}</strong>
            </div>
          </section>

          <section class="detail-section">
            <h3>来源健康</h3>
            <div class="detail-source-card">
              <div>
                <strong>{{ detail.source.label }}</strong>
                <small>{{ detail.source.assetCount }} 资产 / {{ detail.source.eventCount24h }} 事件 / {{ detail.source.fimCount24h }} FIM</small>
              </div>
              <el-tag :type="sourceStatusType(detail.source.status)" effect="light">{{ sourceStatusLabel(detail.source.status) }}</el-tag>
            </div>
          </section>

          <section class="detail-section">
            <h3>最近批次</h3>
            <div class="table-scroll drawer-table-scroll">
              <el-table :data="detail.recentBatches" empty-text="暂无上报批次" size="small">
                <el-table-column prop="batchId" label="批次" min-width="220" show-overflow-tooltip />
                <el-table-column prop="ingestType" label="类型" width="92" />
                <el-table-column prop="acceptedCount" label="接收" width="80" />
                <el-table-column prop="duplicateCount" label="重复" width="80" />
                <el-table-column prop="rejectedCount" label="拒收" width="80" />
                <el-table-column prop="status" label="状态" width="100" />
                <el-table-column prop="finishedAt" label="完成时间" min-width="170" />
              </el-table>
            </div>
          </section>

          <section class="detail-section">
            <h3>最近事件</h3>
            <div class="table-scroll drawer-table-scroll">
              <el-table :data="detail.recentEvents" empty-text="暂无事件" size="small">
                <el-table-column prop="eventUid" label="事件 ID" min-width="180" show-overflow-tooltip />
                <el-table-column prop="eventType" label="类型" width="120" />
                <el-table-column label="等级" width="86">
                  <template #default="{ row }"><SeverityBadge :severity="row.severity" /></template>
                </el-table-column>
                <el-table-column prop="ruleName" label="规则" min-width="180" show-overflow-tooltip />
                <el-table-column prop="eventTime" label="时间" min-width="160" />
              </el-table>
            </div>
          </section>

          <section class="detail-section">
            <h3>拒收记录</h3>
            <div class="table-scroll drawer-table-scroll">
              <el-table :data="detail.recentRejects" empty-text="暂无拒收记录" size="small">
                <el-table-column prop="batchId" label="批次" min-width="180" show-overflow-tooltip />
                <el-table-column prop="ingestType" label="类型" width="90" />
                <el-table-column prop="reasonCode" label="原因码" width="150" />
                <el-table-column prop="reason" label="说明" min-width="220" show-overflow-tooltip />
                <el-table-column prop="createdAt" label="时间" min-width="160" />
              </el-table>
            </div>
          </section>
        </template>
        <el-empty v-else description="请选择一个 Agent 查看详情" :image-size="80" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, Search } from '@element-plus/icons-vue'
import DataSourceBadge from '@/components/security/DataSourceBadge.vue'
import SeverityBadge from '@/components/security/SeverityBadge.vue'
import StatusBadge from '@/components/security/StatusBadge.vue'
import {
  hostAgentDetail,
  hostAgentOverview,
  type HostAgentDetail,
  type HostAgentItem,
  type HostAgentOverview,
  type HostAgentSourceHealth,
} from '@/api/soc'

type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'

const router = useRouter()
const overview = ref<HostAgentOverview>()
const detail = ref<HostAgentDetail>()
const loading = ref(false)
const detailLoading = ref(false)
const drawer = ref(false)
const error = ref('')
const query = ref('')
const osFilter = ref('')
const statusFilter = ref('')
const activeSource = ref('all')

const agents = computed(() => overview.value?.agents || [])
const rawSources = computed(() => overview.value?.sources || [])
const recentEvents = computed(() => overview.value?.recentEvents || [])
const realDataReady = computed(() => Boolean((overview.value?.realAssetCount || 0) + (overview.value?.events24h || 0) + (overview.value?.fim24h || 0)))
const totalQueueBytes = computed(() => agents.value.reduce((sum, agent) => sum + (agent.queueBytes || 0), 0))
const totalQueueDepth = computed(() => agents.value.reduce((sum, agent) => sum + (agent.queueDepth || 0), 0))
const failedAgents = computed(() => agents.value.filter((agent) => (agent.failedCount || 0) > 0).length)
const maxQueueDepth = computed(() => agents.value.reduce((max, agent) => Math.max(max, agent.queueDepth || 0), 0))

const kpis = computed(() => [
  { label: '采集器', value: overview.value?.totalAgents || 0, hint: `${overview.value?.onlineAgents || 0} 在线 / ${overview.value?.offlineAgents || 0} 离线`, tone: 'neutral' },
  { label: 'Mac / Windows', value: `${overview.value?.macosAgents || 0} / ${overview.value?.windowsAgents || 0}`, hint: '当前验证 / 预留验收', tone: 'neutral' },
  { label: '真实资产', value: overview.value?.realAssetCount || 0, hint: 'Host Agent 入库资产', tone: realDataReady.value ? 'good' : 'warning' },
  { label: '24h 事件', value: overview.value?.events24h || 0, hint: '主机事件证据流', tone: 'neutral' },
  { label: '拒收 / 队列', value: `${overview.value?.rejects24h || 0} / ${totalQueueDepth.value}`, hint: `${formatBytes(totalQueueBytes.value)} 待补传`, tone: ((overview.value?.rejects24h || 0) > 0 || totalQueueDepth.value > 0) ? 'warning' : 'neutral' },
])

const platformCards = computed(() => [
  {
    key: 'macos',
    kicker: 'DEV VALIDATION',
    title: 'macOS 开发验证',
    description: '用于快速验证 Agent schema、采集器、API 入库和页面展示。',
    status: (overview.value?.macosAgents || 0) > 0 ? '已接入' : '待注册',
    tagType: ((overview.value?.macosAgents || 0) > 0 ? 'success' : 'warning') as TagType,
    tone: (overview.value?.macosAgents || 0) > 0 ? 'ready' : 'pending',
  },
  {
    key: 'windows',
    kicker: 'RESERVED ACCEPTANCE',
    title: 'Windows 实机预留',
    description: '当前没有 Windows 主机，只保留 EventLog、Defender、Sysmon、Windows Service 和 Docker 后端验收入口。',
    status: (overview.value?.windowsAgents || 0) > 0 ? '有记录待复核' : '预留待实机',
    tagType: ((overview.value?.windowsAgents || 0) > 0 ? 'warning' : 'info') as TagType,
    tone: 'reserved',
  },
])

const readinessGates = computed(() => [
  {
    label: '真实资产可见',
    hint: `${overview.value?.realAssetCount || 0} 个 Host Agent 资产，不计演示数据`,
    status: (overview.value?.realAssetCount || 0) > 0 ? '通过' : '待验证',
    tagType: ((overview.value?.realAssetCount || 0) > 0 ? 'success' : 'warning') as TagType,
    tone: (overview.value?.realAssetCount || 0) > 0 ? 'good' : 'warning',
  },
  {
    label: '事件流可见',
    hint: `${overview.value?.events24h || 0} 条 24h 主机事件`,
    status: (overview.value?.events24h || 0) > 0 ? '通过' : '待验证',
    tagType: ((overview.value?.events24h || 0) > 0 ? 'success' : 'warning') as TagType,
    tone: (overview.value?.events24h || 0) > 0 ? 'good' : 'warning',
  },
  {
    label: 'FIM / 基线闭环',
    hint: `${overview.value?.fim24h || 0} 条 FIM，${overview.value?.failedBaselines || 0} 条基线失败`,
    status: (overview.value?.fim24h || 0) > 0 || (overview.value?.failedBaselines || 0) > 0 ? '有信号' : '待信号',
    tagType: ((overview.value?.fim24h || 0) > 0 || (overview.value?.failedBaselines || 0) > 0 ? 'success' : 'info') as TagType,
    tone: (overview.value?.fim24h || 0) > 0 || (overview.value?.failedBaselines || 0) > 0 ? 'good' : 'idle',
  },
  {
    label: '拒收与补传',
    hint: `${overview.value?.rejects24h || 0} 条拒收，${totalQueueDepth.value} 条本地队列`,
    status: (overview.value?.rejects24h || 0) > 0 || totalQueueDepth.value > 0 ? '需关注' : '正常',
    tagType: ((overview.value?.rejects24h || 0) > 0 || totalQueueDepth.value > 0 ? 'warning' : 'success') as TagType,
    tone: (overview.value?.rejects24h || 0) > 0 || totalQueueDepth.value > 0 ? 'warning' : 'good',
  },
  {
    label: 'Windows 实机验收',
    hint: (overview.value?.windowsAgents || 0) > 0
      ? `${overview.value?.windowsAgents || 0} 条 Windows 记录存在，需在真实 Windows 宿主机复核来源`
      : '当前无 Windows 主机，fixture 清理后不应出现在业务链路',
    status: (overview.value?.windowsAgents || 0) > 0 ? '待复核' : '预留',
    tagType: ((overview.value?.windowsAgents || 0) > 0 ? 'warning' : 'info') as TagType,
    tone: (overview.value?.windowsAgents || 0) > 0 ? 'warning' : 'idle',
  },
])

const sourceRows = computed<HostAgentSourceHealth[]>(() => {
  const byType = new Map(rawSources.value.map((source) => [source.sourceType, source]))
  const expected = [
    sourceFallback('macos-agent', 'macOS Agent'),
    sourceFallback('windows-agent', 'Windows Agent（预留）'),
    sourceFallback('host-agent', '兼容 Host Agent'),
  ]
  const existing = new Set(expected.map((source) => source.sourceType))
  const extra = rawSources.value.filter((source) => !existing.has(source.sourceType) && source.sourceType !== 'demo')
  return [...expected.map((source) => byType.get(source.sourceType) || source), ...extra]
})

const filteredAgents = computed(() => {
  const normalizedQuery = query.value.trim().toLowerCase()
  return agents.value.filter((agent) => {
    const os = normalizeOs(agent.osType)
    const haystack = [
      agent.agentId,
      agent.agentName,
      agent.hostname,
      agent.osType,
      agent.osVersion,
      agent.agentVersion,
      agent.lastIp,
      agent.ipAddressesJson,
      agent.macAddressesJson,
    ].filter(Boolean).join(' ').toLowerCase()
    return (!osFilter.value || os === osFilter.value)
      && (!statusFilter.value || agent.status === statusFilter.value)
      && (activeSource.value === 'all' || agentSourceType(agent) === activeSource.value)
      && (!normalizedQuery || haystack.includes(normalizedQuery))
  })
})

const diagnostics = computed(() => [
  {
    label: '离线 Agent',
    value: `${overview.value?.offlineAgents || 0}`,
    hint: '超过心跳窗口会影响实时性',
    tone: (overview.value?.offlineAgents || 0) > 0 ? 'warning' : 'good',
  },
  {
    label: '失败 Agent',
    value: `${failedAgents.value}`,
    hint: '存在上传失败或采集失败计数',
    tone: failedAgents.value > 0 ? 'warning' : 'good',
  },
  {
    label: '最大队列深度',
    value: `${maxQueueDepth.value}`,
    hint: '用于判断后端断链后的补传压力',
    tone: maxQueueDepth.value > 0 ? 'warning' : 'good',
  },
  {
    label: '24h 批次',
    value: `${overview.value?.batches24h || 0}`,
    hint: '资产、事件、FIM、基线的入库批次',
    tone: (overview.value?.batches24h || 0) > 0 ? 'good' : 'idle',
  },
])

const ingestSteps = [
  { index: '01', label: '注册与心跳', hint: 'Agent 注册、token hash、在线状态' },
  { index: '02', label: '采集与队列', hint: '资产、事件、FIM、基线进入本地队列' },
  { index: '03', label: '上传与幂等', hint: '批次入库、eventUid 去重、拒收可追踪' },
  { index: '04', label: 'SOC 联动', hint: '资产、证据、告警、事件簇、工单和报表' },
]

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await hostAgentOverview()
    overview.value = res.data.data
  } catch {
    error.value = 'Agent 管理状态加载失败，请检查后端服务、权限或 Host Agent 数据表。'
  } finally {
    loading.value = false
  }
}

async function openAgent(row: HostAgentItem) {
  detailLoading.value = true
  drawer.value = true
  detail.value = undefined
  try {
    const res = await hostAgentDetail(row.id)
    detail.value = res.data.data
  } finally {
    detailLoading.value = false
  }
}

function go(path: string) {
  router.push(path)
}

function sourceFallback(sourceType: string, label: string): HostAgentSourceHealth {
  return {
    sourceType,
    label,
    agentCount: 0,
    onlineCount: 0,
    assetCount: 0,
    eventCount24h: 0,
    fimCount24h: 0,
    failedBaselineCount: 0,
    status: 'empty',
  }
}

function sourceProgress(source: HostAgentSourceHealth) {
  const signals = [
    source.agentCount > 0,
    source.onlineCount > 0,
    source.assetCount > 0,
    source.eventCount24h > 0,
    source.fimCount24h > 0 || source.failedBaselineCount > 0,
  ]
  return Math.round((signals.filter(Boolean).length / signals.length) * 100)
}

function sourceStatusLabel(status: string) {
  return ({ online: '在线', warning: '有数据未在线', empty: '无数据' } as Record<string, string>)[status] || status
}

function sourceStatusType(status: string): TagType {
  return ({ online: 'success', warning: 'warning', empty: 'info' } as Record<string, TagType>)[status] || 'info'
}

function agentSourceType(agent: HostAgentItem) {
  const os = normalizeOs(agent.osType)
  if (os === 'macos') return 'macos-agent'
  if (os === 'windows') return 'windows-agent'
  return 'host-agent'
}

function normalizeOs(os?: string) {
  const normalized = (os || '').toLowerCase()
  if (normalized.includes('mac')) return 'macos'
  if (normalized.includes('win')) return 'windows'
  if (normalized.includes('linux')) return 'linux'
  return normalized
}

function osLabel(os?: string) {
  const normalized = normalizeOs(os)
  if (normalized === 'macos') return 'macOS'
  if (normalized === 'windows') return 'Windows'
  if (normalized === 'linux') return 'Linux'
  return os || '-'
}

function firstJsonValue(value?: string) {
  if (!value) return ''
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? String(parsed[0] || '') : ''
  } catch {
    return ''
  }
}

function formatBytes(value?: number) {
  const bytes = value || 0
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
</script>

<style scoped>
.host-agent-hero {
  align-items: center;
}

.host-agent-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.panel-pad {
  padding: 16px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.panel-head.compact {
  margin-bottom: 12px;
}

.panel-head h2 {
  margin: 0;
  font-size: 18px;
  line-height: 1.25;
}

.panel-head p {
  margin: 6px 0 0;
  color: var(--soc-text-muted);
  font-size: 13px;
  line-height: 1.55;
}

.agent-command-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.agent-kpi-card {
  display: grid;
  gap: 8px;
  min-height: 112px;
  padding: 16px;
}

.agent-kpi-card span,
.agent-kpi-card small {
  color: var(--soc-text-muted);
}

.agent-kpi-card strong {
  color: var(--soc-text);
  font-size: 28px;
  line-height: 1;
}

.agent-kpi-card.good {
  border-color: rgba(28, 143, 88, 0.28);
}

.agent-kpi-card.warning {
  border-color: rgba(214, 130, 39, 0.36);
}

.agent-readiness-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.18fr) minmax(360px, 0.82fr);
  gap: 12px;
}

.platform-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.platform-card,
.gate-list article,
.source-health-card,
.diagnostic-list article,
.detail-source-card {
  border: 1px solid rgba(129, 143, 166, 0.24);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.48);
}

.platform-card {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  min-height: 118px;
  padding: 14px;
}

.platform-card > div {
  display: grid;
  gap: 6px;
}

.platform-card span,
.platform-card small {
  color: var(--soc-text-muted);
}

.platform-card strong {
  color: var(--soc-text);
  font-size: 17px;
}

.platform-card.ready {
  border-color: rgba(28, 143, 88, 0.28);
}

.platform-card.pending {
  border-color: rgba(214, 130, 39, 0.32);
}

.platform-card.reserved {
  border-style: dashed;
  border-color: rgba(129, 143, 166, 0.42);
}

.gate-list,
.ingest-step-list,
.diagnostic-list,
.agent-detail-stack {
  display: grid;
  gap: 12px;
}

.gate-list article {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  padding: 11px 12px;
}

.gate-list strong,
.diagnostic-list strong {
  display: block;
  color: var(--soc-text);
}

.gate-list small,
.diagnostic-list small {
  display: block;
  margin-top: 3px;
  color: var(--soc-text-muted);
}

.gate-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: var(--soc-text-subtle);
}

.gate-dot.good {
  background: var(--soc-success);
}

.gate-dot.warning {
  background: var(--soc-medium);
}

.gate-dot.idle {
  background: var(--soc-text-subtle);
}

.source-health-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.source-health-card {
  display: grid;
  gap: 14px;
  padding: 14px;
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.source-health-card:hover,
.source-health-card.active {
  border-color: rgba(216, 128, 36, 0.5);
  box-shadow: 0 14px 32px rgba(91, 77, 53, 0.1);
  transform: translateY(-1px);
}

.source-health-card-head,
.detail-source-card {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.source-health-card-head > div {
  display: grid;
  gap: 4px;
}

.source-health-card-head strong {
  color: var(--soc-text);
  font-size: 16px;
}

.source-health-card-head span,
.source-health-card dt,
.source-health-card small {
  color: var(--soc-text-muted);
}

.source-health-card dl {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
}

.source-health-card dt,
.source-health-card dd {
  margin: 0;
}

.source-health-card dd {
  margin-top: 4px;
  color: var(--soc-text);
  font-weight: 800;
}

.source-progress {
  overflow: hidden;
  height: 6px;
  border-radius: 999px;
  background: rgba(129, 143, 166, 0.18);
}

.source-progress span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--soc-warm), rgba(16, 179, 199, 0.72));
}

.agent-main-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 0.31fr);
  gap: 12px;
  align-items: start;
}

.agent-filter-bar {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) 150px 150px;
  gap: 10px;
  margin-bottom: 12px;
}

.table-scroll {
  overflow-x: auto;
}

.table-scroll :deep(.el-table) {
  min-width: 1040px;
}

.compact-table-scroll :deep(.el-table) {
  min-width: 1180px;
}

.drawer-table-scroll :deep(.el-table) {
  min-width: 760px;
}

.agent-name-cell {
  display: grid;
  gap: 3px;
}

.agent-name-cell strong {
  color: var(--soc-text);
}

.agent-name-cell small {
  color: var(--soc-text-muted);
}

.agent-side-panel {
  position: sticky;
  top: 92px;
}

.ingest-step-list article {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  gap: 10px;
  padding: 12px;
  border: 1px solid rgba(129, 143, 166, 0.22);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.44);
}

.ingest-step-list article > span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  color: var(--soc-warm-strong);
  font-weight: 800;
  background: rgba(216, 128, 36, 0.12);
}

.ingest-step-list strong {
  display: block;
  color: var(--soc-text);
}

.ingest-step-list small {
  display: block;
  margin-top: 4px;
  color: var(--soc-text-muted);
  line-height: 1.45;
}

.side-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 14px;
}

.agent-diagnostics-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 0.38fr);
  gap: 12px;
  align-items: start;
}

.diagnostic-list article {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  padding: 12px;
}

.diagnostic-list article > span {
  color: var(--soc-text);
  font-size: 22px;
  font-weight: 800;
}

.diagnostic-list article.good {
  border-color: rgba(28, 143, 88, 0.24);
}

.diagnostic-list article.warning {
  border-color: rgba(214, 130, 39, 0.34);
}

.detail-summary {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  padding: 12px;
  border: 1px solid rgba(129, 143, 166, 0.24);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.48);
}

.detail-summary div {
  display: grid;
  gap: 4px;
}

.detail-summary span,
.detail-summary small {
  color: var(--soc-text-muted);
}

.detail-summary strong {
  color: var(--soc-text);
  font-size: 18px;
}

.detail-section h3 {
  margin: 0 0 10px;
  font-size: 16px;
}

.detail-source-card {
  padding: 12px;
}

.detail-source-card > div {
  display: grid;
  gap: 4px;
}

.detail-source-card small {
  color: var(--soc-text-muted);
}

@media (max-width: 1240px) {
  .agent-command-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .agent-readiness-grid,
  .agent-main-grid,
  .agent-diagnostics-grid {
    grid-template-columns: 1fr;
  }

  .agent-side-panel {
    position: static;
  }
}

@media (max-width: 860px) {
  .agent-command-grid,
  .platform-card-grid,
  .source-health-grid {
    grid-template-columns: 1fr;
  }

  .agent-filter-bar {
    grid-template-columns: 1fr;
  }

  .host-agent-actions {
    justify-content: flex-start;
  }

  .panel-head,
  .source-health-card-head,
  .detail-source-card {
    flex-direction: column;
  }
}
</style>
