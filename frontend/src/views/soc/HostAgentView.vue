<template>
  <div class="page-shell host-agent-page">
    <section class="soc-page-hero host-agent-hero">
      <div>
        <span class="soc-page-kicker">FULL EXPERT VIEW / AGENT OPS</span>
        <h1>Agent 管理</h1>
        <p>{{ heroDescription }}</p>
      </div>
      <div class="host-agent-actions">
        <el-tag v-if="activeAgentView === 'global'" :type="realDataReady ? 'success' : 'warning'" effect="light">
          {{ realDataReady ? '真实数据已接入' : '等待真实采集' }}
        </el-tag>
        <el-button :icon="DocumentAdd" type="primary" plain @click="go('/soc/agents/install')">安装命令设置</el-button>
        <el-button v-if="activeAgentView === 'global'" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </section>

    <section class="soc-panel agent-view-tabs">
      <el-tabs v-model="activeAgentView">
        <el-tab-pane label="全局 Agent 管理" name="global" />
        <el-tab-pane label="按用户查看" name="users" />
      </el-tabs>
    </section>

    <UserWorkspaceCards
      v-if="activeAgentView === 'users'"
      target="/soc/agents"
      compact
    />

    <div v-show="activeAgentView === 'global'" class="agent-global-content">
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

    <section class="soc-panel panel-pad agent-readiness-panel">
      <div class="panel-head readiness-head">
        <div>
          <h2>真实采集状态</h2>
          <p>{{ readinessBoundarySummary }}</p>
        </div>
        <el-tag :type="realDataReady ? 'success' : 'warning'" effect="light">
          {{ realDataReady ? '真实来源可用' : '等待真实来源' }}
        </el-tag>
      </div>
      <div class="readiness-layout">
        <div class="platform-card-grid runtime-platform-grid">
          <article v-if="runtimePlatform" class="platform-card" :class="runtimePlatform.tone">
            <div class="platform-card-head">
              <div>
                <span>{{ runtimePlatform.kicker }}</span>
                <strong>{{ runtimePlatform.title }}</strong>
              </div>
              <el-tag :type="runtimePlatform.tagType" effect="light">{{ runtimePlatform.status }}</el-tag>
            </div>
            <dl class="platform-metrics">
              <div v-for="metric in runtimePlatform.metrics" :key="metric.label">
                <dt>{{ metric.label }}</dt>
                <dd>{{ metric.value }}</dd>
              </div>
            </dl>
            <small>{{ runtimePlatform.description }}</small>
          </article>
        </div>

        <div class="readiness-gates">
          <div class="gate-panel-title">
            <strong>上线守门</strong>
            <span>当前运行环境的资产、事件、FIM 与接收状态</span>
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
        </div>
      </div>
    </section>

    <section class="soc-panel panel-pad source-panel">
      <div class="panel-head">
        <div>
          <h2>数据源健康度</h2>
          <p>仅展示当前后端运行环境对应的 Agent 来源、资产、事件、FIM 和基线状态。</p>
        </div>
        <el-tag effect="plain">{{ runtimeEnvironment?.label || 'Host OS' }}</el-tag>
      </div>
      <div class="source-health-grid">
        <article
          v-for="source in sourceRows"
          :key="source.sourceType"
          class="source-health-card"
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
        </article>
      </div>
    </section>

    <section class="agent-main-grid">
      <section class="soc-panel panel-pad agent-table-panel">
        <div class="panel-head">
          <div>
            <h2>采集器列表</h2>
            <p>接收开关只控制平台是否接收上报；心跳由宿主机 Agent 进程产生，点击行进入批次、事件、拒收和队列详情。</p>
          </div>
        </div>
        <div class="agent-filter-bar">
          <el-input
            v-model="query"
            :prefix-icon="Search"
            clearable
            placeholder="搜索 Agent、主机名、IP 或版本"
          />
          <div class="runtime-os-filter">
            <span>运行环境</span>
            <strong>{{ runtimeEnvironment?.label || 'Host OS' }}</strong>
          </div>
          <el-select v-model="statusFilter" placeholder="状态" clearable>
            <el-option label="在线" value="online" />
            <el-option label="待心跳" value="pending_heartbeat" />
            <el-option label="停用" value="disabled" />
            <el-option label="异常" value="warning" />
          </el-select>
        </div>
        <div class="table-scroll agent-list-scroll">
          <el-table
            v-loading="loading"
            :data="pagedAgents"
            height="100%"
            empty-text="暂无匹配的主机 Agent"
            @row-click="openAgent"
          >
            <el-table-column label="心跳状态" width="132">
              <template #default="{ row }">
                <div class="agent-status-cell">
                  <StatusBadge :status="runtimeStatus(row)" />
                  <small>{{ runtimeHint(row) }}</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="接收开关" width="138">
              <template #default="{ row }">
                <div class="agent-control-cell" @click.stop>
                  <el-switch
                    :model-value="agentEnabled(row)"
                    :loading="switchingAgentIds.has(row.id)"
                    inline-prompt
                    active-text="开"
                    inactive-text="关"
                    @change="toggleAgent(row, $event)"
                  />
                  <span class="agent-control-text" :class="{ off: !agentEnabled(row) }">
                    {{ agentEnabled(row) ? '接收' : '拒收' }}
                  </span>
                  <el-button
                    v-if="canControlRuntime(row) && agentEnabled(row) && runtimeStatus(row) === 'pending_heartbeat'"
                    link
                    type="primary"
                    :loading="switchingAgentIds.has(row.id)"
                    @click.stop="openRuntimeDialog(row, 'start')"
                  >
                    等待心跳
                  </el-button>
                  <el-button
                    v-if="canControlRuntime(row) && runtimeStatus(row) === 'online'"
                    link
                    type="warning"
                    :loading="switchingAgentIds.has(row.id)"
                    @click.stop="openRuntimeDialog(row, 'stop')"
                  >
                    关闭
                  </el-button>
                  <el-tooltip
                    v-if="!canControlRuntime(row)"
                    :content="runtimeUnavailableHint(row)"
                    placement="top"
                  >
                    <el-tag class="runtime-local-tag" type="info" effect="plain">本地未安装</el-tag>
                  </el-tooltip>
                  <el-button
                    v-if="!canControlRuntime(row)"
                    link
                    type="primary"
                    :icon="DocumentAdd"
                    @click.stop="openInstallForAgent(row)"
                  >
                    去设置安装
                  </el-button>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="agentName" label="Agent" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="agent-name-cell">
                  <strong>{{ agentDisplay(row).name }}</strong>
                  <span class="agent-class-line">
                    <el-tag :type="agentDisplay(row).tagType" effect="plain">{{ agentDisplay(row).category }}</el-tag>
                    <small>{{ row.agentId }}</small>
                  </span>
                  <small>{{ row.agentName || row.hostname || '-' }}</small>
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
        <div class="table-pagination">
          <span>共 {{ filteredAgents.length }} 个当前环境 Agent</span>
          <el-pagination
            v-model:current-page="agentPage"
            v-model:page-size="agentPageSize"
            background
            :page-sizes="[6, 8, 12, 20]"
            layout="sizes, prev, pager, next"
            :total="filteredAgents.length"
          />
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
      <section class="soc-panel panel-pad agent-work-card-panel">
        <div class="panel-head">
          <div>
            <h2>Agent 工作概览</h2>
            <p>按 Agent 汇总当前返回窗口内的真实采集、上报、队列和事件类型。</p>
          </div>
          <el-tag effect="plain">{{ agentWorkCards.length }} 个 Agent</el-tag>
        </div>
        <div class="agent-work-scroll">
          <el-empty v-if="!agentWorkCards.length" description="当前环境尚未注册 Agent" :image-size="72" />
          <div v-else class="agent-work-grid">
            <article
              v-for="card in agentWorkCards"
              :key="card.agent.id"
              class="agent-work-card"
              :class="{ online: card.status === 'online', inactive: card.status !== 'online' }"
            >
              <div class="agent-work-card-head">
                <div>
                  <strong>{{ agentDisplay(card.agent).name }}</strong>
                  <small>{{ card.agent.hostname || card.agent.agentName || card.agent.agentId }}</small>
                </div>
                <StatusBadge :status="card.status" />
              </div>

              <div class="agent-work-metrics">
                <div><span>当前事件</span><strong>{{ card.eventCount }}</strong></div>
                <div><span>采集 / 上报</span><strong>{{ card.agent.collectedCount || 0 }} / {{ card.agent.sentCount || 0 }}</strong></div>
                <div><span>本地队列</span><strong>{{ card.agent.queueDepth || 0 }} / {{ formatBytes(card.agent.queueBytes || 0) }}</strong></div>
              </div>

              <div class="agent-work-detail">
                <span>工作内容</span>
                <strong>{{ card.workSummary }}</strong>
              </div>
              <div class="agent-work-footer">
                <el-tag :type="card.severityType" size="small" effect="plain">{{ card.severityLabel }}</el-tag>
                <small>{{ card.lastActivity }}</small>
              </div>
            </article>
          </div>
        </div>
      </section>

      <section class="soc-panel panel-pad diagnostics-panel">
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

    <el-dialog
      v-model="runtimeDialog.visible"
      :title="runtimeDialogTitle"
      width="560px"
      destroy-on-close
      @closed="stopRuntimeProgress"
    >
      <div v-if="runtimeDialog.agent" class="runtime-dialog-body">
        <el-alert
          :type="runtimeDialog.action === 'start' ? 'info' : 'warning'"
          :closable="false"
          show-icon
        >
          <template #title>{{ runtimeDialogSummary }}</template>
          <template #default>
            {{ runtimeDialog.action === 'start'
              ? '启动操作会先打开平台接收，再调用本机 Agent 服务；只有服务真实回连后才会显示在线。'
              : '停止操作会调用本机 Agent 服务停止脚本，并关闭平台接收；后续心跳和上报会被拒收。' }}
          </template>
        </el-alert>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="分类命名">{{ agentDisplay(runtimeDialog.agent).name }}</el-descriptions-item>
          <el-descriptions-item label="Agent ID">{{ runtimeDialog.agent.agentId }}</el-descriptions-item>
          <el-descriptions-item label="主机">{{ runtimeDialog.agent.hostname || runtimeDialog.agent.agentName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="当前心跳">
            <StatusBadge :status="runtimeStatus(runtimeDialog.agent)" />
            <span class="runtime-inline-hint">{{ runtimeHint(runtimeDialog.agent) }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <section v-if="runtimeDialog.action === 'start'" class="runtime-start-progress" :class="`phase-${runtimeDialog.phase}`">
          <span class="runtime-heart" :class="{ beating: runtimeDialog.phase === 'online' }" aria-hidden="true">&#9829;</span>
          <div class="runtime-progress-content">
            <strong>{{ runtimeProgressTitle }}</strong>
            <small>{{ runtimeDialog.progressDetail }}</small>
            <el-progress :percentage="runtimeDialog.progress" :status="runtimeDialog.phase === 'online' ? 'success' : runtimeDialog.phase === 'failed' ? 'exception' : undefined" :show-text="false" :stroke-width="8" />
            <span class="runtime-progress-caption">{{ runtimeDialog.progress }}%</span>
          </div>
        </section>
      </div>
      <template #footer>
        <el-button @click="closeRuntimeDialog">{{ runtimeDialog.action === 'start' && runtimeDialog.phase === 'online' ? '完成' : '取消' }}</el-button>
        <el-button
          v-if="runtimeDialog.action === 'stop' || runtimeDialog.phase !== 'online'"
          class="runtime-confirm-button"
          :class="{ 'is-stop-action': runtimeDialog.action === 'stop' }"
          :type="runtimeDialog.action === 'start' ? 'primary' : 'danger'"
          :loading="runtimeDialog.loading"
          @click="confirmRuntimeAction"
        >
          {{ runtimeDialogConfirmText }}
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="drawer" title="Agent 详情" size="720px">
      <div v-loading="detailLoading" class="agent-detail-stack">
        <template v-if="detail">
          <section class="detail-summary">
            <div>
              <span>{{ osLabel(detail.agent.osType) }} / {{ agentSourceType(detail.agent) }}</span>
              <strong>{{ agentDisplay(detail.agent).name }}</strong>
              <small>{{ detail.agent.agentId }}</small>
            </div>
            <StatusBadge :status="runtimeStatus(detail.agent)" />
          </section>

          <section class="detail-section">
            <h3>运行状态</h3>
            <div class="soc-drawer-grid">
              <span>主机</span><strong>{{ detail.agent.hostname || '-' }}</strong>
              <span>分类命名</span><strong>{{ agentDisplay(detail.agent).category }}</strong>
              <span>系统版本</span><strong>{{ detail.agent.osVersion || '-' }}</strong>
              <span>Agent 版本</span><strong>{{ detail.agent.agentVersion || '-' }}</strong>
              <span>管理状态</span><strong>{{ agentEnabled(detail.agent) ? '启用' : '停用' }}</strong>
              <span>心跳状态</span><strong>{{ runtimeHint(detail.agent) }}</strong>
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
                <el-table-column label="来源 Agent" min-width="180" show-overflow-tooltip>
                  <template #default="{ row }">
                    <div class="source-agent-cell">
                      <strong>{{ row.sourceAgentName || row.agentId || '-' }}</strong>
                      <small>{{ row.agentId || '-' }}</small>
                    </div>
                  </template>
                </el-table-column>
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
                <el-table-column label="来源 Agent" min-width="180" show-overflow-tooltip>
                  <template #default="{ row }">
                    <div class="source-agent-cell">
                      <strong>{{ eventSourceAgentName(row) }}</strong>
                      <small>{{ row.sourceAgentId || row.batchId || '-' }}</small>
                    </div>
                  </template>
                </el-table-column>
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
                <el-table-column label="来源 Agent" min-width="180" show-overflow-tooltip>
                  <template #default="{ row }">
                    <div class="source-agent-cell">
                      <strong>{{ row.sourceAgentName || row.agentId || '-' }}</strong>
                      <small>{{ row.agentId || '-' }}</small>
                    </div>
                  </template>
                </el-table-column>
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
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DocumentAdd, Refresh, Search } from '@element-plus/icons-vue'
import DataSourceBadge from '@/components/security/DataSourceBadge.vue'
import SeverityBadge from '@/components/security/SeverityBadge.vue'
import StatusBadge from '@/components/security/StatusBadge.vue'
import UserWorkspaceCards from '@/components/security/UserWorkspaceCards.vue'
import {
  controlHostAgentRuntime,
  hostAgentDetail,
  hostAgentOverview,
  updateHostAgentEnabled,
  type HostAgentDetail,
  type HostAgentItem,
  type HostAgentOverview,
  type HostAgentRecentEvent,
  type HostAgentSourceHealth,
} from '@/api/soc'

type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'
type PlatformTone = 'ready' | 'pending' | 'reserved'
type PlatformStatus = {
  status: string
  tagType: TagType
  tone: PlatformTone
}
type AgentDisplay = {
  name: string
  category: string
  detail: string
  tagType: TagType
  key: string
}
type RuntimeAction = 'start' | 'stop'

const router = useRouter()
const route = useRoute()
const activeAgentView = ref(route.query.view === 'users' ? 'users' : 'global')
const overview = ref<HostAgentOverview>()
const detail = ref<HostAgentDetail>()
const loading = ref(false)
const detailLoading = ref(false)
const drawer = ref(false)
const error = ref('')
const query = ref('')
const statusFilter = ref('')
const agentPage = ref(1)
const agentPageSize = ref(8)
const switchingAgentIds = ref<Set<number>>(new Set())
const runtimeDialog = ref<{
  visible: boolean
  loading: boolean
  action: RuntimeAction
  agent?: HostAgentItem
  phase: 'ready' | 'starting' | 'waiting' | 'online' | 'failed'
  progress: number
  progressDetail: string
}>({
  visible: false,
  loading: false,
  action: 'start',
  phase: 'ready',
  progress: 0,
  progressDetail: '',
})
let runtimeProgressTimer: ReturnType<typeof window.setInterval> | undefined
let heartbeatPollTimer: ReturnType<typeof window.setInterval> | undefined
let heartbeatPollingStartedAt = 0

const agents = computed(() => overview.value?.agents || [])
const rawSources = computed(() => overview.value?.sources || [])
const recentEvents = computed(() => overview.value?.recentEvents || [])
const realDataReady = computed(() => Boolean((overview.value?.realAssetCount || 0) + (overview.value?.events24h || 0) + (overview.value?.fim24h || 0)))
const onlineAgentCount = computed(() => agents.value.filter((agent) => runtimeStatus(agent) === 'online').length)
const pendingHeartbeatCount = computed(() => agents.value.filter((agent) => runtimeStatus(agent) === 'pending_heartbeat').length)
const disabledAgentCount = computed(() => agents.value.filter((agent) => runtimeStatus(agent) === 'disabled').length)
const totalQueueBytes = computed(() => agents.value.reduce((sum, agent) => sum + (agent.queueBytes || 0), 0))
const totalQueueDepth = computed(() => agents.value.reduce((sum, agent) => sum + (agent.queueDepth || 0), 0))
const failedAgents = computed(() => agents.value.filter((agent) => (agent.failedCount || 0) > 0).length)
const maxQueueDepth = computed(() => agents.value.reduce((max, agent) => Math.max(max, agent.queueDepth || 0), 0))
const runtimeEnvironment = computed(() => overview.value?.runtime)
const runtimeSourceType = computed(() => sourceTypeForOs(runtimeEnvironment.value?.osType))
const runtimeSource = computed(() => rawSources.value[0]
  || sourceFallback(runtimeSourceType.value, `${runtimeEnvironment.value?.label || 'Host OS'} Agent`))
const runtimeReadiness = computed(() => platformStatus(runtimeSource.value, agents.value))
const runtimePlatform = computed(() => {
  const runtime = runtimeEnvironment.value
  if (!runtime) return undefined
  return {
    kicker: `${runtime.label.toUpperCase()} SOURCE`,
    title: `${runtime.label} 采集状态`,
    description: `${sourceSummary(runtimeSource.value, agents.value)} 服务运行于 ${runtime.osName}${runtime.osVersion ? ` ${runtime.osVersion}` : ''}。`,
    metrics: platformMetrics(runtimeSource.value, agents.value),
    status: runtimeReadiness.value.status,
    tagType: runtimeReadiness.value.tagType,
    tone: runtimeReadiness.value.tone,
  }
})
const agentDisplayMap = computed(() => {
  const counters = new Map<string, number>()
  const display = new Map<number, AgentDisplay>()
  agents.value.forEach((agent) => {
    const base = agentCategory(agent)
    const next = (counters.get(base.key) || 0) + 1
    counters.set(base.key, next)
    display.set(agent.id, {
      ...base,
      name: `${base.category} ${String(next).padStart(2, '0')}`,
    })
  })
  return display
})
const heroDescription = computed(() => {
  if (activeAgentView.value === 'users') {
    return '按用户查看 Agent 归属、在线状态和采集范围。选择用户后进入原有 Agent 管理界面，并仅显示该用户的采集器。'
  }
  const runtime = runtimeEnvironment.value
  if (!agents.value.length) {
    return `当前 ${runtime?.label || 'Host OS'} 运行环境尚未接入 Host Agent。平台会在采集器注册、心跳和真实上报后显示当前环境状态。`
  }
  return `当前仅展示 ${runtime?.label || 'Host OS'} 运行环境下的 ${agents.value.length} 个 Agent，并跟踪队列、拒收、事件、FIM 和基线状态。`
})
const readinessBoundarySummary = computed(() => {
  const runtime = runtimeEnvironment.value
  if (!runtime) return '正在读取后端程序运行环境与对应 Host Agent 数据。'
  return `${runtime.label} 后端运行环境：${runtime.osName}${runtime.osVersion ? ` ${runtime.osVersion}` : ''}，${runtime.architecture || '架构未知'}。仅统计与展示该环境对应的真实采集数据。`
})
const runtimeDialogTitle = computed(() => {
  if (runtimeDialog.value.action === 'start' && runtimeDialog.value.phase === 'online') return 'Agent 已启动'
  return runtimeDialog.value.action === 'start' ? '启用并启动 Agent' : '关闭 Agent'
})
const runtimeDialogConfirmText = computed(() => {
  if (runtimeDialog.value.action === 'start' && runtimeDialog.value.phase === 'waiting') return '重新尝试启动'
  return runtimeDialog.value.action === 'start' ? '启用并等待心跳' : '关闭并停止接收'
})
const runtimeProgressTitle = computed(() => ({
  ready: '等待启动',
  starting: '正在启动本机 Agent',
  waiting: '正在等待真实心跳',
  online: '心跳已收到，Agent 已启动',
  failed: '启动或心跳校验未完成',
} as Record<string, string>)[runtimeDialog.value.phase])
const runtimeDialogSummary = computed(() => {
  const agent = runtimeDialog.value.agent
  if (!agent) {
    return ''
  }
  const name = agentDisplay(agent).name
  return runtimeDialog.value.action === 'start'
    ? `${name} 将启用平台接收并启动本机服务；只有收到新的心跳后才会被标记为启动成功。`
    : `${name} 将停止本机服务并关闭平台接收。`
})

const kpis = computed(() => [
  { label: '采集器', value: overview.value?.totalAgents || 0, hint: `${onlineAgentCount.value} 在线 / ${pendingHeartbeatCount.value} 待心跳 / ${disabledAgentCount.value} 停用`, tone: 'neutral' },
  { label: '运行环境', value: runtimeEnvironment.value?.label || '-', hint: runtimeEnvironment.value?.architecture || '读取中', tone: 'neutral' },
  { label: '真实资产', value: overview.value?.realAssetCount || 0, hint: 'Host Agent 入库资产', tone: realDataReady.value ? 'good' : 'warning' },
  { label: '24h 事件', value: overview.value?.events24h || 0, hint: '主机事件证据流', tone: 'neutral' },
  { label: '拒收 / 队列', value: `${overview.value?.rejects24h || 0} / ${totalQueueDepth.value}`, hint: `${formatBytes(totalQueueBytes.value)} 待补传`, tone: ((overview.value?.rejects24h || 0) > 0 || totalQueueDepth.value > 0) ? 'warning' : 'neutral' },
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
])

const sourceRows = computed<HostAgentSourceHealth[]>(() => [runtimeSource.value])

const filteredAgents = computed(() => {
  const normalizedQuery = query.value.trim().toLowerCase()
  return agents.value.filter((agent) => {
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
    return (!statusFilter.value || runtimeStatus(agent) === statusFilter.value)
      && (!normalizedQuery || haystack.includes(normalizedQuery))
  })
})

const pagedAgents = computed(() => {
  const start = (agentPage.value - 1) * agentPageSize.value
  return filteredAgents.value.slice(start, start + agentPageSize.value)
})

const agentWorkCards = computed(() => agents.value.map((agent) => {
  const events = recentEvents.value.filter((event) => eventBelongsToAgent(event, agent))
  const eventTypes = [...new Set(events.map((event) => event.eventType).filter(Boolean))]
  const highestSeverity = events.reduce((highest, event) => (
    severityWeight(event.severity) > severityWeight(highest) ? event.severity : highest
  ), '')
  const latestEventTime = events.reduce<string | undefined>((latest, event) => {
    if (!latest || Date.parse(event.eventTime) > Date.parse(latest)) return event.eventTime
    return latest
  }, undefined)
  const status = runtimeStatus(agent)
  return {
    agent,
    status,
    eventCount: events.length,
    workSummary: eventTypes.length ? eventTypes.slice(0, 3).join(' / ') : '暂无当前窗口事件上报',
    severityType: severityTagType(highestSeverity),
    severityLabel: highestSeverity ? `最高 ${severityLabel(highestSeverity)}` : '暂无事件等级',
    lastActivity: latestEventTime
      ? `最近事件 ${formatSince(latestEventTime)}`
      : status === 'online'
        ? '已启动，等待事件上报'
        : '已关闭或离线，保留历史统计',
  }
}).sort((left, right) => {
  const statusOrder = Number(right.status === 'online') - Number(left.status === 'online')
  if (statusOrder !== 0) return statusOrder
  return Date.parse(right.agent.lastSeenAt || '') - Date.parse(left.agent.lastSeenAt || '')
}))

watch([query, statusFilter, agentPageSize], () => {
  agentPage.value = 1
})

watch(filteredAgents, (rows) => {
  const lastPage = Math.max(1, Math.ceil(rows.length / agentPageSize.value))
  if (agentPage.value > lastPage) agentPage.value = lastPage
})

watch(activeAgentView, (view) => {
  const query = { ...route.query }
  delete query.ownerId
  if (view === 'users') query.view = 'users'
  else delete query.view
  void router.replace({ path: route.path, query })
})

watch(() => route.query.ownerId, (ownerId) => {
  if (typeof ownerId === 'string' && /^\d+$/.test(ownerId)) activeAgentView.value = 'global'
})

const diagnostics = computed(() => [
  {
    label: '离线 Agent',
    value: `${pendingHeartbeatCount.value}`,
    hint: '已启用但超过心跳窗口，需确认本机 Agent 是否运行',
    tone: pendingHeartbeatCount.value > 0 ? 'warning' : 'good',
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

onBeforeUnmount(() => {
  stopRuntimeProgressTimers()
})

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

async function toggleAgent(row: HostAgentItem, value: string | number | boolean) {
  const nextEnabled = Boolean(value)
  if (nextEnabled === agentEnabled(row)) {
    return
  }
  if (!canControlRuntime(row)) {
    if (nextEnabled) {
      ElMessage.info('该 Agent 本机运行时未安装，已进入安装页并带入当前 Agent 信息。')
      openInstallForAgent(row)
      return
    }
    await updateAcceptanceOnly(row, false)
    return
  }
  openRuntimeDialog(row, nextEnabled ? 'start' : 'stop')
}

function openRuntimeDialog(row: HostAgentItem, action: RuntimeAction) {
  if (!canControlRuntime(row)) {
    ElMessage.warning(runtimeUnavailableHint(row))
    return
  }
  runtimeDialog.value = {
    visible: true,
    loading: false,
    action,
    agent: row,
    phase: 'ready',
    progress: 0,
    progressDetail: action === 'start' ? '点击后将启用接收并启动本机 Agent 服务。' : '点击后将停止本机 Agent 并关闭平台接收。',
  }
}

function openInstallForAgent(row: HostAgentItem) {
  router.push({
    path: '/soc/agents/install',
    query: {
      agentId: row.agentId,
      agentName: row.agentName || row.hostname || row.agentId,
      hostname: row.hostname || row.agentName || row.agentId,
      os: normalizeInstallOs(row.osType),
      osVersion: row.osVersion || '',
      architecture: row.architecture || '',
      agentVersion: row.agentVersion || '0.1.0-dev',
      ip: firstJsonValue(row.ipAddressesJson) || row.lastIp || '',
      profile: agentInstallProfile(row),
    },
  })
}

async function confirmRuntimeAction() {
  const agent = runtimeDialog.value.agent
  if (!agent) {
    return
  }
  runtimeDialog.value.loading = true
  if (runtimeDialog.value.action === 'start') startRuntimeProgress()
  setAgentSwitching(agent.id, true)
  try {
    const res = await controlHostAgentRuntime(agent.id, runtimeDialog.value.action)
    if (runtimeDialog.value.action === 'start' && res.data.data.runtimeStatus === 'online') {
      markHeartbeatOnline()
      ElMessage.success('Agent 已启动并收到新的心跳。')
    } else if (runtimeDialog.value.action === 'start') {
      runtimeDialog.value.phase = 'waiting'
      runtimeDialog.value.progress = Math.max(runtimeDialog.value.progress, 72)
      runtimeDialog.value.progressDetail = '启动命令已执行，正在轮询后端接收的真实心跳。'
      startHeartbeatPolling(agent.id)
      ElMessage.warning('Agent 已启用并执行启动命令，仍在等待新的心跳。')
    } else if (res.data.data.commandExecuted) {
      ElMessage.success(res.data.data.message)
    } else {
      ElMessage.warning(res.data.data.message)
    }
    if (runtimeDialog.value.action === 'stop') runtimeDialog.value.visible = false
    if (detail.value?.agent.id === agent.id) {
      const detailRes = await hostAgentDetail(agent.id)
      detail.value = detailRes.data.data
    }
    await load()
  } catch (err) {
    if (runtimeDialog.value.action === 'start') {
      runtimeDialog.value.phase = 'failed'
      runtimeDialog.value.progressDetail = apiErrorMessage(err, '启动命令失败，请检查本机安装配置。')
      stopRuntimeProgressTimers()
    }
    ElMessage.error(apiErrorMessage(err, runtimeDialog.value.action === 'start' ? 'Agent 启动失败，请检查本机安装配置' : 'Agent 停止失败，请检查本机服务权限'))
  } finally {
    runtimeDialog.value.loading = false
    setAgentSwitching(agent.id, false)
  }
}

function startRuntimeProgress() {
  stopRuntimeProgressTimers()
  runtimeDialog.value.phase = 'starting'
  runtimeDialog.value.progress = 12
  runtimeDialog.value.progressDetail = '正在启用平台接收并提交本机 Agent 启动命令。'
  const startedAt = Date.now()
  runtimeProgressTimer = window.setInterval(() => {
    if (!runtimeDialog.value.visible || runtimeDialog.value.phase === 'online' || runtimeDialog.value.phase === 'failed') return
    const elapsedSeconds = Math.floor((Date.now() - startedAt) / 1000)
    if (elapsedSeconds < 3) {
      runtimeDialog.value.progress = Math.max(runtimeDialog.value.progress, 32)
      runtimeDialog.value.progressDetail = '本机 Agent 服务正在初始化。'
    } else {
      runtimeDialog.value.phase = 'waiting'
      runtimeDialog.value.progress = Math.max(runtimeDialog.value.progress, 62)
      runtimeDialog.value.progressDetail = '服务已启动，正在等待后端收到本次新的心跳。'
    }
  }, 700)
}

function startHeartbeatPolling(agentId: number) {
  if (heartbeatPollTimer) window.clearInterval(heartbeatPollTimer)
  heartbeatPollingStartedAt = Date.now()
  heartbeatPollTimer = window.setInterval(() => void pollHeartbeat(agentId), 2_000)
  void pollHeartbeat(agentId)
}

async function pollHeartbeat(agentId: number) {
  if (!runtimeDialog.value.visible || runtimeDialog.value.agent?.id !== agentId || runtimeDialog.value.phase === 'online') {
    stopHeartbeatPolling()
    return
  }
  try {
    const response = await hostAgentDetail(agentId)
    const current = response.data.data.agent
    if (runtimeStatus(current) === 'online') {
      markHeartbeatOnline()
      await load()
      return
    }
    if (Date.now() - heartbeatPollingStartedAt >= 60_000) {
      runtimeDialog.value.progress = Math.max(runtimeDialog.value.progress, 88)
      runtimeDialog.value.progressDetail = '60 秒内未收到新心跳。Agent 保持启用，可检查本机日志后重新尝试启动。'
      stopHeartbeatPolling()
    }
  } catch {
    runtimeDialog.value.progressDetail = '暂时无法读取心跳状态，仍在等待后端恢复查询。'
  }
}

function markHeartbeatOnline() {
  runtimeDialog.value.phase = 'online'
  runtimeDialog.value.progress = 100
  runtimeDialog.value.progressDetail = '后端已收到新的真实心跳，平台接收与 Agent 服务均正常。'
  stopRuntimeProgressTimers()
}

function closeRuntimeDialog() {
  runtimeDialog.value.visible = false
  stopRuntimeProgressTimers()
}

function stopRuntimeProgress() {
  stopRuntimeProgressTimers()
}

function stopRuntimeProgressTimers() {
  if (runtimeProgressTimer) window.clearInterval(runtimeProgressTimer)
  runtimeProgressTimer = undefined
  stopHeartbeatPolling()
}

function stopHeartbeatPolling() {
  if (heartbeatPollTimer) window.clearInterval(heartbeatPollTimer)
  heartbeatPollTimer = undefined
}

async function updateAcceptanceOnly(row: HostAgentItem, enabled: boolean) {
  setAgentSwitching(row.id, true)
  try {
    await updateHostAgentEnabled(row.id, enabled)
    ElMessage.success(enabled ? '平台接收已打开。' : '平台接收已关闭；该 Agent 本机运行时未安装，无需执行停止脚本。')
    if (detail.value?.agent.id === row.id) {
      const detailRes = await hostAgentDetail(row.id)
      detail.value = detailRes.data.data
    }
    await load()
  } catch (err) {
    ElMessage.error(apiErrorMessage(err, '平台接收状态更新失败'))
    await load()
  } finally {
    setAgentSwitching(row.id, false)
  }
}

function setAgentSwitching(id: number, active: boolean) {
  const next = new Set(switchingAgentIds.value)
  if (active) {
    next.add(id)
  } else {
    next.delete(id)
  }
  switchingAgentIds.value = next
}

function agentEnabled(agent: HostAgentItem) {
  if (agent.enabled === 0) {
    return false
  }
  return agent.status !== 'disabled'
}

function canControlRuntime(agent: HostAgentItem) {
  return agent.runtimeControllable === true
}

function runtimeUnavailableHint(agent: HostAgentItem) {
  return agent.runtimeControlReason || '本机未安装该 Agent 运行时，仅保留历史上报记录。'
}

function runtimeStatus(agent: HostAgentItem) {
  if (!agentEnabled(agent)) {
    return 'disabled'
  }
  if (agent.status === 'online') {
    return 'online'
  }
  if (agent.status === 'warning') {
    return 'warning'
  }
  return 'pending_heartbeat'
}

function runtimeHint(agent: HostAgentItem) {
  if (!agentEnabled(agent)) {
    return '平台拒收'
  }
  if (!canControlRuntime(agent)) {
    return runtimeUnavailableHint(agent)
  }
  if (agent.status === 'online') {
    return agent.lastSeenAt ? `${formatSince(agent.lastSeenAt)}心跳` : '心跳正常'
  }
  if (agent.status === 'warning') {
    return '需检查'
  }
  return agent.lastSeenAt ? `进程未回连 · 最后 ${formatSince(agent.lastSeenAt)}` : '等待 Agent 进程回连'
}

function apiErrorMessage(error: unknown, fallback: string) {
  const maybe = error as { response?: { data?: { message?: string } } }
  return maybe.response?.data?.message || fallback
}

function agentDisplay(agent: HostAgentItem): AgentDisplay {
  return agentDisplayMap.value.get(agent.id) || {
    ...agentCategory(agent),
    name: agentCategory(agent).category,
  }
}

function agentCategory(agent: HostAgentItem): Omit<AgentDisplay, 'name'> {
  const os = normalizeOs(agent.osType)
  const id = `${agent.agentId || ''} ${agent.agentName || ''}`.toLowerCase()
  if (os === 'macos') {
    if (id.includes('install')) {
      return { key: 'macos-install', category: 'macOS 安装校验', detail: '安装、启动和本机脚本验证采集器', tagType: 'info' }
    }
    if (id.includes('closure')) {
      return { key: 'macos-closure', category: 'macOS 闭环验证', detail: '真实资产、事件、FIM、基线闭环验证采集器', tagType: 'success' }
    }
    if (id.includes('real-agent')) {
      return { key: 'macos-primary', category: 'macOS 主采集', detail: '开发机真实主机数据采集器', tagType: 'success' }
    }
    return { key: 'macos-runtime', category: 'macOS 主机采集', detail: 'macOS runtime 数据采集器', tagType: 'primary' }
  }
  if (os === 'windows') {
    if (id.includes('fixture') || id.includes('docker') || id.includes('reserved')) {
      return { key: 'windows-reserved', category: 'Windows 预留验收', detail: '等待 Windows 实机 Docker 宿主机验证', tagType: 'warning' }
    }
    return { key: 'windows-primary', category: 'Windows 主机采集', detail: 'Windows EventLog / Defender / Sysmon 采集器', tagType: 'warning' }
  }
  if (os === 'linux') {
    return { key: 'linux-runtime', category: 'Linux 主机采集', detail: 'Linux 主机文本元数据采集器', tagType: 'info' }
  }
  return { key: 'host-runtime', category: '通用 Host 采集', detail: '未归类 Host Agent', tagType: 'info' }
}

function eventSourceAgentName(row: { sourceAgentName?: string; sourceAgentId?: string; batchId?: string }) {
  return row.sourceAgentName || row.sourceAgentId || sourceAgentFromBatch(row.batchId) || '-'
}

function eventBelongsToAgent(event: HostAgentRecentEvent, agent: HostAgentItem) {
  const eventIdentity = [event.sourceAgentId, event.sourceAgentName, sourceAgentFromBatch(event.batchId)]
    .filter(Boolean)
    .map((value) => String(value).toLowerCase())
  const agentIdentity = [agent.agentId, agent.agentName, agent.hostname]
    .filter(Boolean)
    .map((value) => String(value).toLowerCase())
  return agentIdentity.some((value) => eventIdentity.includes(value))
}

function severityWeight(severity?: string) {
  const value = (severity || '').toLowerCase()
  if (value === 'critical' || value === '严重') return 4
  if (value === 'high' || value === '高危' || value === '高') return 3
  if (value === 'medium' || value === '中危' || value === '中') return 2
  if (value === 'low' || value === '低危' || value === '低') return 1
  return 0
}

function severityLabel(severity?: string) {
  const labels: Record<string, string> = {
    critical: '严重',
    high: '高',
    medium: '中',
    low: '低',
    info: '提示',
  }
  return labels[(severity || '').toLowerCase()] || severity || '提示'
}

function severityTagType(severity?: string): TagType {
  const weight = severityWeight(severity)
  if (weight >= 4) return 'danger'
  if (weight >= 3) return 'warning'
  if (weight >= 1) return 'primary'
  return 'info'
}

function sourceAgentFromBatch(batchId?: string) {
  if (!batchId) {
    return ''
  }
  const match = batchId.match(/^HOST-(.+?)-(asset|event|fim|baseline)-\d{14}$/)
  return match?.[1] || ''
}

function formatSince(value?: string) {
  if (!value) {
    return ''
  }
  const timestamp = Date.parse(value)
  if (Number.isNaN(timestamp)) {
    return value
  }
  const minutes = Math.max(0, Math.floor((Date.now() - timestamp) / 60000))
  if (minutes < 1) {
    return '刚刚'
  }
  if (minutes < 60) {
    return `${minutes} 分钟前`
  }
  const hours = Math.floor(minutes / 60)
  if (hours < 24) {
    return `${hours} 小时前`
  }
  return `${Math.floor(hours / 24)} 天前`
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

function platformStatus(source: HostAgentSourceHealth, rows: HostAgentItem[]): PlatformStatus {
  const agentCount = source.agentCount || rows.length
  const onlineCount = source.onlineCount || rows.filter((agent) => agent.status === 'online').length
  const signalCount = source.assetCount + source.eventCount24h + source.fimCount24h + source.failedBaselineCount
  const queueDepth = rows.reduce((sum, agent) => sum + (agent.queueDepth || 0), 0)
  const failedCount = rows.reduce((sum, agent) => sum + (agent.failedCount || 0), 0)

  if (onlineCount > 0 && signalCount > 0 && queueDepth === 0 && failedCount === 0) {
    return { status: '真实在线', tagType: 'success', tone: 'ready' }
  }
  if (onlineCount > 0 && signalCount > 0) {
    return { status: '在线需关注', tagType: 'warning', tone: 'pending' }
  }
  if (onlineCount > 0) {
    return { status: '在线待数据', tagType: 'warning', tone: 'pending' }
  }
  if (agentCount > 0 && signalCount > 0) {
    return { status: '离线有数据', tagType: 'warning', tone: 'pending' }
  }
  if (agentCount > 0) {
    return { status: '已注册待心跳', tagType: 'warning', tone: 'pending' }
  }
  return { status: '未接入', tagType: 'info', tone: 'reserved' }
}

function sourceSummary(source: HostAgentSourceHealth, rows: HostAgentItem[]) {
  const agentCount = source.agentCount || rows.length
  const onlineCount = source.onlineCount || rows.filter((agent) => agent.status === 'online').length
  const signalCount = source.assetCount + source.eventCount24h + source.fimCount24h + source.failedBaselineCount
  const queueDepth = rows.reduce((sum, agent) => sum + (agent.queueDepth || 0), 0)
  const failedCount = rows.reduce((sum, agent) => sum + (agent.failedCount || 0), 0)
  if (!agentCount && !signalCount) {
    return '等待该平台 Agent 注册、心跳和首批真实上报。'
  }
  const queueText = queueDepth > 0 ? `，队列 ${queueDepth} 条待补传` : ''
  const failedText = failedCount > 0 ? `，失败 ${failedCount} 次` : ''
  return `${agentCount} 个 Agent，${onlineCount} 在线，${signalCount} 条真实信号${queueText}${failedText}。`
}

function platformMetrics(source: HostAgentSourceHealth, rows: HostAgentItem[]) {
  const agentCount = source.agentCount || rows.length
  const onlineCount = source.onlineCount || rows.filter((agent) => agent.status === 'online').length
  const queueDepth = rows.reduce((sum, agent) => sum + (agent.queueDepth || 0), 0)
  const failedCount = rows.reduce((sum, agent) => sum + (agent.failedCount || 0), 0)
  return [
    { label: '在线/总数', value: `${onlineCount}/${agentCount}` },
    { label: '资产', value: `${source.assetCount}` },
    { label: '24h 事件', value: `${source.eventCount24h}` },
    { label: 'FIM/基线', value: `${source.fimCount24h}/${source.failedBaselineCount}` },
    { label: '队列', value: `${queueDepth}` },
    { label: '失败', value: `${failedCount}` },
  ]
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
  return sourceTypeForOs(agent.osType)
}

function sourceTypeForOs(os?: string) {
  const normalized = normalizeOs(os)
  if (normalized === 'macos') return 'macos-agent'
  if (normalized === 'windows') return 'windows-agent'
  return 'host-agent'
}

function normalizeOs(os?: string) {
  const normalized = (os || '').toLowerCase()
  if (normalized.includes('mac')) return 'macos'
  if (normalized.includes('win')) return 'windows'
  if (normalized.includes('linux')) return 'linux'
  return normalized
}

function normalizeInstallOs(os?: string) {
  const normalized = normalizeOs(os)
  return normalized === 'windows' ? 'windows' : 'macos'
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

function agentInstallProfile(agent: HostAgentItem) {
  try {
    const labels = JSON.parse(agent.labelsJson || '{}') as Record<string, unknown>
    const profile = String(labels.profile || '')
    if (['full', 'host-log', 'patrol-audit', 'file-integrity', 'baseline-audit'].includes(profile)) {
      return profile
    }
  } catch {
    // Older Agent rows may not have structured labels yet.
  }
  return 'full'
}

function formatBytes(value?: number) {
  const bytes = value || 0
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
</script>

<style scoped>
.host-agent-page {
  width: 100%;
  max-width: 100%;
  overflow-x: hidden;
}

.host-agent-page > * {
  min-width: 0;
}

.agent-view-tabs {
  padding: 0 16px;
}

.agent-view-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.agent-view-tabs :deep(.el-tabs__content) {
  display: none;
}

.agent-global-content {
  display: contents;
}

.host-agent-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding-block: 18px;
}

.host-agent-hero p {
  max-width: 820px;
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
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
}

.agent-kpi-card {
  display: grid;
  align-content: start;
  gap: 6px;
  min-height: 92px;
  padding: 14px;
}

.agent-kpi-card span,
.agent-kpi-card small {
  color: var(--soc-text-muted);
}

.agent-kpi-card strong {
  color: var(--soc-text);
  font-size: 26px;
  line-height: 1;
}

.agent-kpi-card.good {
  border-color: rgba(28, 143, 88, 0.28);
}

.agent-kpi-card.warning {
  border-color: rgba(214, 130, 39, 0.36);
}

.agent-readiness-panel {
  display: grid;
  gap: 14px;
}

.readiness-head {
  align-items: flex-start;
  margin-bottom: 0;
}

.readiness-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(300px, 0.75fr);
  gap: 12px;
  align-items: stretch;
}

.platform-card-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
  min-width: 0;
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
  display: grid;
  align-content: start;
  gap: 12px;
  padding: 14px;
}

.platform-card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.platform-card-head > div {
  display: grid;
  gap: 4px;
}

.platform-card span,
.platform-card small,
.platform-metrics dt {
  color: var(--soc-text-muted);
}

.platform-card small {
  line-height: 1.5;
}

.platform-card strong {
  color: var(--soc-text);
  font-size: 17px;
}

.platform-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
}

.platform-metrics div {
  min-width: 0;
  padding: 8px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.46);
}

.platform-metrics dt,
.platform-metrics dd {
  margin: 0;
}

.platform-metrics dt {
  font-size: 12px;
}

.platform-metrics dd {
  margin-top: 3px;
  color: var(--soc-text);
  font-weight: 800;
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

.readiness-gates {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.gate-panel-title {
  display: grid;
  gap: 4px;
  padding: 2px 2px 0;
}

.gate-panel-title strong {
  color: var(--soc-text);
  font-size: 16px;
}

.gate-panel-title span {
  color: var(--soc-text-muted);
  font-size: 13px;
  line-height: 1.45;
}

.readiness-gates .gate-list {
  gap: 8px;
}

.gate-list article {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  padding: 10px 11px;
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
  grid-template-columns: 1fr;
  gap: 12px;
}

.source-health-card {
  display: grid;
  align-content: start;
  gap: 14px;
  padding: 14px;
  color: inherit;
  text-align: left;
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
  --agent-workspace-height: 650px;
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: 12px;
  align-items: stretch;
}

.agent-main-grid > *,
.agent-diagnostics-grid > * {
  min-width: 0;
}

.agent-filter-bar {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) 150px 150px;
  gap: 10px;
  margin-bottom: 12px;
}

.runtime-os-filter {
  display: grid;
  align-content: center;
  gap: 2px;
  min-height: 32px;
  padding: 5px 11px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
}

.runtime-os-filter span {
  color: var(--soc-text-muted);
  font-size: 11px;
}

.runtime-os-filter strong {
  color: var(--soc-text);
  font-size: 13px;
}

.table-scroll {
  max-width: 100%;
  overflow-x: auto;
  overscroll-behavior-x: contain;
}

.table-scroll :deep(.el-table) {
  min-width: 1180px;
}

.compact-table-scroll :deep(.el-table) {
  min-width: 1380px;
}

.drawer-table-scroll :deep(.el-table) {
  min-width: 920px;
}

.agent-table-panel,
.agent-side-panel {
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  height: var(--agent-workspace-height);
  min-height: 0;
}

.agent-list-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.agent-list-scroll :deep(.el-table) {
  height: 100%;
}

.table-pagination {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  min-height: 40px;
  padding-top: 10px;
  color: var(--soc-text-muted);
  font-size: 13px;
}

.table-pagination :deep(.el-pagination) {
  justify-content: flex-end;
}

.agent-status-cell,
.agent-name-cell,
.source-agent-cell {
  display: grid;
  gap: 3px;
}

.agent-status-cell small,
.agent-name-cell small,
.source-agent-cell small {
  overflow: hidden;
  color: var(--soc-text-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-control-cell {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 4px 8px;
  align-items: center;
  min-width: 110px;
}

.agent-control-text {
  color: var(--soc-success);
  font-weight: 700;
}

.agent-control-text.off {
  color: var(--soc-text-muted);
}

.agent-control-cell .el-button {
  justify-self: start;
  grid-column: 1 / -1;
  min-height: 20px;
  padding: 0;
}

.runtime-local-tag {
  justify-self: start;
  grid-column: 1 / -1;
  max-width: 96px;
}

.runtime-dialog-body {
  display: grid;
  gap: 14px;
}

.runtime-inline-hint {
  margin-left: 8px;
  color: var(--soc-text-muted);
}

.runtime-start-progress {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 14px;
  align-items: center;
  padding: 14px;
  border: 1px solid rgba(75, 139, 220, 0.3);
  border-radius: 8px;
  background: rgba(242, 248, 255, 0.76);
}

.runtime-start-progress.phase-online {
  border-color: rgba(26, 145, 92, 0.34);
  background: rgba(241, 251, 246, 0.84);
}

.runtime-start-progress.phase-failed {
  border-color: rgba(190, 73, 51, 0.34);
  background: rgba(255, 245, 243, 0.84);
}

.runtime-heart {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 54px;
  height: 54px;
  color: #8e9aab;
  font-size: 42px;
  line-height: 1;
  transform-origin: center;
}

.runtime-heart.beating {
  color: #d45059;
  animation: runtime-heartbeat 0.85s ease-in-out infinite;
}

.runtime-progress-content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 6px 10px;
  align-items: center;
}

.runtime-progress-content strong,
.runtime-progress-content small {
  grid-column: 1 / -1;
}

.runtime-progress-content strong {
  color: var(--soc-text);
}

.runtime-progress-content small {
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.45;
}

.runtime-progress-content :deep(.el-progress) {
  min-width: 0;
}

.runtime-progress-caption {
  color: var(--soc-warm-strong);
  font-size: 13px;
  font-weight: 800;
}

@keyframes runtime-heartbeat {
  0%, 100% { transform: scale(1); }
  20% { transform: scale(1.2); }
  40% { transform: scale(0.96); }
  60% { transform: scale(1.12); }
}

.runtime-confirm-button {
  min-width: 144px;
  min-height: 36px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 800;
  letter-spacing: 0;
}

.runtime-confirm-button.is-stop-action,
.runtime-confirm-button.is-stop-action:hover,
.runtime-confirm-button.is-stop-action:focus,
.runtime-confirm-button.is-stop-action:active {
  border-color: #b94933;
  background: #c9583e;
  color: #fff;
  box-shadow: 0 8px 18px rgba(185, 73, 51, 0.24);
}

.runtime-confirm-button.is-stop-action:hover {
  background: #b94933;
}

.runtime-confirm-button.is-stop-action:focus-visible {
  outline: 2px solid rgba(185, 73, 51, 0.36);
  outline-offset: 2px;
}

.agent-name-cell strong,
.source-agent-cell strong {
  overflow: hidden;
  color: var(--soc-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-class-line {
  display: flex;
  gap: 6px;
  align-items: center;
  min-width: 0;
}

.agent-side-panel {
  overflow-y: auto;
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
  --agent-diagnostics-height: 580px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 0.38fr);
  gap: 12px;
  align-items: stretch;
}

.agent-work-card-panel,
.diagnostics-panel {
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  height: var(--agent-diagnostics-height);
  min-height: 0;
}

.agent-work-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 2px;
}

.agent-work-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 10px;
  align-content: start;
}

.agent-work-card {
  display: grid;
  gap: 12px;
  min-height: 202px;
  padding: 14px;
  border: 1px solid rgba(129, 143, 166, 0.25);
  border-left: 3px solid rgba(120, 130, 145, 0.52);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.58);
  transition: background-color 160ms ease, border-color 160ms ease, opacity 160ms ease;
}

.agent-work-card.online {
  border-left-color: var(--el-color-success);
  border-color: rgba(28, 143, 88, 0.34);
  background: rgba(246, 255, 249, 0.78);
}

.agent-work-card.inactive {
  border-left-color: rgba(133, 143, 160, 0.64);
  background: rgba(239, 241, 245, 0.72);
  filter: grayscale(0.72);
  opacity: 0.72;
}

.agent-work-card-head,
.agent-work-footer {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: flex-start;
}

.agent-work-card-head > div {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.agent-work-card-head strong,
.agent-work-detail strong {
  color: var(--soc-text);
}

.agent-work-card-head small,
.agent-work-footer small {
  overflow: hidden;
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-work-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.agent-work-metrics div {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.agent-work-metrics span,
.agent-work-detail > span {
  color: var(--soc-text-muted);
  font-size: 12px;
}

.agent-work-metrics strong {
  overflow: hidden;
  color: var(--soc-text);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-work-detail {
  display: grid;
  gap: 4px;
  min-height: 39px;
}

.agent-work-detail strong {
  overflow: hidden;
  font-size: 13px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.diagnostics-panel .diagnostic-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
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
  .readiness-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .platform-card-grid {
    grid-column: auto;
  }

  .agent-main-grid,
  .agent-diagnostics-grid {
    grid-template-columns: 1fr;
  }

  .agent-table-panel,
  .agent-side-panel,
  .agent-work-card-panel,
  .diagnostics-panel {
    height: auto;
  }

  .agent-list-scroll,
  .agent-work-scroll {
    min-height: 440px;
  }
}

@media (max-width: 980px) {
  .agent-command-grid,
  .readiness-layout,
  .source-health-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

}

@media (max-width: 860px) {
  .host-agent-hero {
    grid-template-columns: 1fr;
  }

  .agent-command-grid,
  .platform-card-grid,
  .source-health-grid {
    grid-template-columns: 1fr;
  }

  .platform-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
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
