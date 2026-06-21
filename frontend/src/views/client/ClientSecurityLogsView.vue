<template>
  <div class="client-security-logs">
    <section class="logs-hero">
      <div>
        <span class="soc-page-kicker">SECURITY LOGS</span>
        <h1>安全日志</h1>
        <p>这个页面展示当前电脑最近发生过的安全事项，方便你和安全团队确认处理进展。</p>
      </div>
      <div class="logs-hero-actions">
        <el-tag effect="plain" size="large">{{ deviceName }} / {{ assetIp || '-' }}</el-tag>
        <el-button :loading="loading" @click="loadLogs">刷新</el-button>
        <el-button @click="router.push({ path: '/client/workbench', query: clientDeviceQuery })">回到我的电脑</el-button>
      </div>
    </section>

    <section v-if="error" class="logs-error-card">
      <div>
        <span>安全日志加载失败</span>
        <strong>{{ error }}</strong>
        <p>可能是后端未启动、数据库未初始化或当前账号没有查看这台电脑的权限。</p>
      </div>
      <div class="logs-error-actions">
        <el-button type="primary" :loading="loading" @click="loadLogs">重试</el-button>
        <el-button @click="useOfflineDemoData">使用离线演示数据</el-button>
        <el-button text @click="showDiagnostics = !showDiagnostics">查看诊断</el-button>
      </div>
      <pre v-if="showDiagnostics">{{ diagnosticsText }}</pre>
    </section>

    <section class="logs-summary-grid">
      <article v-for="item in summaryCards" :key="item.type">
        <span>{{ item.label }}</span>
        <strong>{{ item.count }}</strong>
        <em>{{ item.hint }}</em>
      </article>
    </section>

    <section class="logs-toolbar">
      <div>
        <strong>最近安全记录</strong>
        <span>只展示和当前电脑有关的记录，不展示原始日志和系统管理员审计明细。</span>
      </div>
      <el-segmented v-model="activeType" :options="typeOptions" />
    </section>

    <section class="logs-list-card">
      <div v-if="filteredLogs.length" class="logs-list">
        <article v-for="item in filteredLogs" :key="item.id" class="log-row">
          <div class="log-icon" :class="`log-icon-${item.type}`">{{ typeIcon(item.type) }}</div>
          <div class="log-content">
            <div class="log-title-row">
              <strong>{{ item.title }}</strong>
              <el-tag :type="severityTag(item.severity)" effect="plain">{{ typeLabel(item.type) }}</el-tag>
            </div>
            <p>{{ item.description }}</p>
            <div class="log-meta">
              <span>{{ formatTime(item.occurredAt) }}</span>
              <span>{{ statusLabel(item.status) }}</span>
              <span>{{ item.assetName || deviceName }}</span>
            </div>
          </div>
        </article>
      </div>
      <el-empty v-else description="暂无安全日志" :image-size="96">
        <el-button type="primary" @click="router.push({ path: '/client/workbench', query: clientDeviceQuery })">返回我的电脑</el-button>
      </el-empty>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  listSecurityKeeperLogs,
  type SecurityKeeperLogItem,
} from '@/api/soc'
import { buildClientDeviceRouteQuery } from '@/composables/useClientDeviceContext'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const error = ref('')
const showDiagnostics = ref(false)
const logs = ref<SecurityKeeperLogItem[]>([])
const activeType = ref('all')

const assetIp = computed(() => typeof route.query.ip === 'string' ? route.query.ip : '')
const deviceName = computed(() => typeof route.query.host === 'string' ? route.query.host : '当前电脑')
const osType = computed(() => typeof route.query.os === 'string' ? route.query.os : 'Linux')
const clientDeviceQuery = computed(() => buildClientDeviceRouteQuery({
  ip: assetIp.value,
  host: deviceName.value,
  os: osType.value,
}))

const typeOptions = [
  { label: '全部', value: 'all' },
  { label: '体检', value: 'checkup' },
  { label: '工具', value: 'local_check' },
  { label: '提交', value: 'log_submission' },
  { label: '待办', value: 'ticket_task' },
  { label: '确认', value: 'employee_confirmation' },
]

const filteredLogs = computed(() => {
  if (activeType.value === 'all') return logs.value
  if (activeType.value === 'local_check') {
    return logs.value.filter((item) => ['local_check', 'local_check_record'].includes(item.type))
  }
  return logs.value.filter((item) => item.type === activeType.value)
})

const summaryCards = computed(() => [
  { type: 'all', label: '全部记录', count: logs.value.length, hint: '当前电脑最近安全事项' },
  { type: 'checkup', label: '一键体检', count: countByType('checkup'), hint: '安全管家体检记录' },
  { type: 'local_check', label: '本机检查', count: countByTypes(['local_check', 'local_check_record']), hint: '安全工具箱执行记录' },
  { type: 'ticket_task', label: '处理任务', count: countByType('ticket_task'), hint: '安全团队创建或更新的待办' },
])

const diagnosticsText = computed(() => JSON.stringify({
  assetIp: assetIp.value,
  host: deviceName.value,
  os: osType.value,
  error: error.value,
}, null, 2))

onMounted(loadLogs)

async function loadLogs() {
  loading.value = true
  error.value = ''
  try {
    if (!assetIp.value) {
      throw new Error('当前电脑 IP 缺失，无法查询安全日志')
    }
    const response = await listSecurityKeeperLogs(assetIp.value)
    logs.value = response.data.data
  } catch (err) {
    error.value = err instanceof Error ? err.message : '安全日志加载失败'
    logs.value = []
  } finally {
    loading.value = false
  }
}

function useOfflineDemoData() {
  const now = new Date().toISOString()
  error.value = ''
  logs.value = [
    {
      id: 'offline-checkup',
      type: 'checkup',
      title: '已完成一次一键体检',
      description: '离线演示数据：安全管家已汇总当前电脑风险状态。',
      status: 'attention',
      severity: 'medium',
      assetIp: assetIp.value || '10.20.1.15',
      assetName: deviceName.value,
      occurredAt: now,
    },
    {
      id: 'offline-tool',
      type: 'local_check',
      title: '已完成一次本机检查',
      description: '离线演示数据：只读工具已运行完成。',
      status: 'linked',
      severity: 'low',
      assetIp: assetIp.value || '10.20.1.15',
      assetName: deviceName.value,
      occurredAt: now,
    },
    {
      id: 'offline-task',
      type: 'ticket_task',
      title: '安全团队创建了处理任务',
      description: '离线演示数据：请按页面提示提交说明或确认结果。',
      status: 'pending',
      severity: 'warning',
      assetIp: assetIp.value || '10.20.1.15',
      assetName: deviceName.value,
      occurredAt: now,
    },
  ]
}

function countByType(type: string) {
  return logs.value.filter((item) => item.type === type).length
}

function countByTypes(types: string[]) {
  return logs.value.filter((item) => types.includes(item.type)).length
}

function typeLabel(type?: string) {
  const labels: Record<string, string> = {
    checkup: '一键体检',
    local_check: '本机检查',
    local_check_record: '检查记录',
    log_submission: '日志提交',
    ticket_task: '处理任务',
    risk_status_change: '风险变化',
    employee_confirmation: '员工确认',
    security_event: '安全记录',
  }
  return labels[type || ''] || '安全记录'
}

function typeIcon(type?: string) {
  const icons: Record<string, string> = {
    checkup: '检',
    local_check: '工',
    local_check_record: '记',
    log_submission: '提',
    ticket_task: '办',
    risk_status_change: '变',
    employee_confirmation: '确',
    security_event: '安',
  }
  return icons[type || ''] || '安'
}

function severityTag(severity?: string) {
  if (['critical', 'high'].includes(severity || '')) return 'danger'
  if (['medium', 'warning'].includes(severity || '')) return 'warning'
  if (severity === 'low') return 'success'
  return 'info'
}

function statusLabel(status?: string) {
  const labels: Record<string, string> = {
    safe: '安全',
    attention: '需要注意',
    serious: '严重风险',
    pending: '待处理',
    submitted: '已提交',
    confirmed: '已确认',
    completed: '已完成',
    linked: '已关联',
    closed: '已关闭',
    new: '新记录',
    note: '已提交说明',
    confirm: '已确认',
  }
  return labels[status || ''] || status || '已记录'
}

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}
</script>

<style scoped>
.client-security-logs {
  display: grid;
  gap: 18px;
}

.logs-hero,
.logs-error-card,
.logs-toolbar,
.logs-list-card,
.logs-summary-grid article {
  border: 1px solid var(--soc-border);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: var(--soc-shadow-soft), var(--soc-glass-highlight);
}

.logs-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: end;
  padding: 22px;
}

.logs-hero h1 {
  margin: 4px 0 8px;
  color: var(--soc-text);
  font-size: 26px;
  letter-spacing: 0;
}

.logs-hero p,
.logs-toolbar span,
.logs-error-card p,
.log-row p,
.log-meta,
.logs-summary-grid em {
  margin: 0;
  color: var(--soc-text-muted);
  font-size: 13px;
  line-height: 1.7;
}

.logs-hero-actions,
.logs-error-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.logs-error-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  padding: 16px;
  border-color: rgba(225, 83, 97, 0.34);
}

.logs-error-card span {
  color: #b42318;
  font-size: 12px;
  font-weight: 760;
}

.logs-error-card strong,
.logs-toolbar strong,
.logs-summary-grid strong,
.log-row strong {
  color: var(--soc-text);
}

.logs-error-card pre {
  grid-column: 1 / -1;
  overflow: auto;
  max-height: 180px;
  margin: 0;
  padding: 12px;
  border-radius: 8px;
  background: rgba(248, 250, 251, 0.92);
  color: var(--soc-text);
  font-size: 12px;
}

.logs-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.logs-summary-grid article {
  display: grid;
  gap: 6px;
  padding: 16px;
}

.logs-summary-grid span {
  color: var(--soc-text-muted);
  font-size: 12px;
}

.logs-summary-grid strong {
  font-size: 26px;
}

.logs-toolbar {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
}

.logs-toolbar div {
  display: grid;
  gap: 4px;
}

.logs-list-card {
  padding: 8px;
}

.logs-list {
  display: grid;
  gap: 8px;
}

.log-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  padding: 14px;
  border: 1px solid rgba(180, 187, 198, 0.35);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
}

.log-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 8px;
  color: #fff;
  background: linear-gradient(135deg, #efb466, #31b6c6);
  font-size: 13px;
  font-weight: 760;
}

.log-content {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.log-title-row,
.log-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.log-title-row {
  justify-content: space-between;
}

.log-meta span {
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(248, 250, 251, 0.88);
}

@media (max-width: 900px) {
  .logs-hero,
  .logs-error-card,
  .logs-toolbar,
  .logs-summary-grid {
    grid-template-columns: 1fr;
  }

  .logs-toolbar,
  .logs-hero-actions,
  .logs-error-actions {
    align-items: stretch;
    justify-content: flex-start;
  }
}
</style>
