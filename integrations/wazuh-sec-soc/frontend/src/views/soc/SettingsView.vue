<template>
  <div class="page-shell">
    <section class="settings-grid">
      <div class="soc-panel panel-pad">
        <div class="panel-title">
          <strong>Wazuh 连接</strong>
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
        <div class="panel-title"><strong>同步任务</strong><span>P0 可使用模拟/导入数据</span></div>
        <el-table v-loading="loading" :data="tasks" size="small" empty-text="暂无同步任务">
          <el-table-column prop="taskName" label="任务" />
          <el-table-column label="来源" width="120"><template #default="{ row }"><DataSourceBadge :source="row.sourceType" /></template></el-table-column>
          <el-table-column prop="lastStatus" label="状态" width="140" />
        </el-table>
      </div>
    </section>
    <section class="soc-panel panel-pad">
      <div class="panel-title"><strong>连接配置</strong><span>凭据只从运行环境读取</span></div>
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
  syncTasks,
  testNotificationChannel,
  wazuhConfigs,
  wazuhHealth,
  type NotificationChannelItem,
  type NotificationLogItem
} from '@/api/soc'

const configs = ref<unknown[]>([])
const tasks = ref<unknown[]>([])
const channels = ref<NotificationChannelItem[]>([])
const notificationLogRows = ref<NotificationLogItem[]>([])
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

onMounted(load)
async function load() {
  loading.value = true
  error.value = ''
  try {
    const [configRes, taskRes, channelRes, logRes] = await Promise.all([
      wazuhConfigs(),
      syncTasks(),
      notificationChannels(),
      notificationLogs({ pageNum: 1, pageSize: 6 })
    ])
    configs.value = configRes.data.data
    tasks.value = taskRes.data.data
    channels.value = channelRes.data.data
    notificationLogRows.value = logRes.data.data.records
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
</script>

<style scoped>
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
  .settings-grid,
  .notification-grid {
    grid-template-columns: 1fr;
  }
  .health-grid {
    grid-template-columns: 1fr;
  }
}
</style>
