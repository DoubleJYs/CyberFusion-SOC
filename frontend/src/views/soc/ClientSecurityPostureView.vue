<template>
  <div class="page-shell">
    <section class="soc-page-hero">
      <div>
        <span class="soc-page-kicker">EMPLOYEE ENDPOINTS</span>
        <h1>员工终端安全态势</h1>
        <p>这个页面帮你查看员工安全管家的体检覆盖、风险状态、待办完成和本机检查复核情况。</p>
      </div>
      <div class="hero-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" @click="goAssets">查看资产风险</el-button>
      </div>
    </section>

    <el-alert v-if="error" title="员工终端态势加载失败" type="error" show-icon :closable="false">
      <template #default>
        <p>{{ error }}</p>
        <el-button size="small" @click="load">重试</el-button>
      </template>
    </el-alert>

    <section v-loading="loading" class="kpi-grid">
      <article class="soc-panel kpi-card">
        <span>体检覆盖率</span>
        <strong>{{ metrics.checkupCoverageRate }}%</strong>
        <small>{{ metrics.checkedAssets }} / {{ metrics.totalAssets }} 台电脑已完成体检</small>
      </article>
      <article class="soc-panel kpi-card danger">
        <span>严重风险电脑</span>
        <strong>{{ metrics.seriousRiskAssets }}</strong>
        <small>按风险画像或最近体检状态统计</small>
      </article>
      <article class="soc-panel kpi-card warning">
        <span>员工待办未完成</span>
        <strong>{{ metrics.pendingEmployeeTasks }}</strong>
        <small>来自工单任务和处置剧本 employee_task</small>
      </article>
      <article class="soc-panel kpi-card">
        <span>本机检查提交</span>
        <strong>{{ metrics.localCheckSubmissions }}</strong>
        <small>待复核 {{ metrics.waitingReviewRecords }} 条</small>
      </article>
    </section>

    <section class="posture-grid">
      <div class="soc-panel">
        <div class="panel-title">
          <strong>Top 高风险员工电脑</strong>
          <span>只展示当前账号可见的数据范围</span>
        </div>
        <el-table v-loading="loading" :data="posture.highRiskAssets" empty-text="暂无高风险电脑">
          <el-table-column prop="hostname" label="电脑" min-width="150" />
          <el-table-column prop="assetIp" label="IP" width="130" />
          <el-table-column prop="ownerName" label="员工" width="110" />
          <el-table-column prop="deptName" label="部门" min-width="130" />
          <el-table-column label="风险" width="92">
            <template #default="{ row }">
              <el-tag :type="riskTagType(row.riskLevel)">{{ riskLabel(row.riskLevel) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="riskScore" label="分值" width="78" />
          <el-table-column prop="pendingTasks" label="待办" width="76" />
          <el-table-column label="最近体检" width="155">
            <template #default="{ row }">{{ formatTime(row.latestCheckupAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="176" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" @click="goAsset(row)">资产详情</el-button>
              <el-button link size="small" @click="goClient(row)">员工画像</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="soc-panel">
        <div class="panel-title">
          <strong>最近风险下降资产</strong>
          <span>根据最近两次安全管家体检对比</span>
        </div>
        <div v-if="posture.riskDownAssets.length" class="risk-down-list">
          <article v-for="item in posture.riskDownAssets" :key="`${item.assetIp}-${item.changedAt}`">
            <div>
              <strong>{{ item.hostname }}</strong>
              <span>{{ item.assetIp }} / {{ formatTime(item.changedAt) }}</span>
            </div>
            <b>{{ item.previousScore }} → {{ item.currentScore }}</b>
          </article>
        </div>
        <el-empty v-else description="暂无风险下降记录" :image-size="72" />
      </div>
    </section>

    <section class="posture-grid secondary">
      <div class="soc-panel">
        <div class="panel-title">
          <strong>待复核检查记录</strong>
          <span>展示摘要，不展示员工原始命令输出或敏感日志</span>
        </div>
        <el-table v-loading="loading" :data="posture.reviewRecords" empty-text="暂无待复核检查记录">
          <el-table-column prop="assetName" label="电脑" min-width="150" />
          <el-table-column prop="assetIp" label="IP" width="130" />
          <el-table-column label="类型" width="112">
            <template #default="{ row }">{{ eventTypeLabel(row.eventType) }}</template>
          </el-table-column>
          <el-table-column prop="summary" label="摘要" min-width="190" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><el-tag>{{ statusLabel(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="时间" width="155">
            <template #default="{ row }">{{ formatTime(row.occurredAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" @click="goEvents(row)">查看证据</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="soc-panel action-panel">
        <div class="panel-title">
          <strong>分析员下一步</strong>
          <span>围绕员工配合任务推进闭环</span>
        </div>
        <div class="action-stack">
          <el-button type="primary" @click="goTickets">查看员工待办工单</el-button>
          <el-button @click="goEventsByLocalCheck">复核本机检查证据</el-button>
          <el-button @click="goReports">生成验证报告</el-button>
        </div>
        <p class="privacy-note">页面只展示资产、状态和摘要，不展示员工隐私原文日志、raw JSON 或命令输出。</p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  clientSecurityPosture,
  type ClientSecurityHighRiskAsset,
  type ClientSecurityPosture,
  type ClientSecurityReviewRecord,
} from '@/api/soc'

const router = useRouter()
const loading = ref(false)
const error = ref('')
const posture = ref<ClientSecurityPosture>({
  metrics: {
    totalAssets: 0,
    checkedAssets: 0,
    checkupCoverageRate: 0,
    seriousRiskAssets: 0,
    pendingEmployeeTasks: 0,
    localCheckSubmissions: 0,
    waitingReviewRecords: 0,
  },
  highRiskAssets: [],
  riskDownAssets: [],
  reviewRecords: [],
})

const metrics = computed(() => posture.value.metrics)

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await clientSecurityPosture()
    posture.value = res.data.data
  } catch {
    error.value = '可能是后端未启动、数据库未初始化，或当前账号没有员工终端安全态势权限。'
  } finally {
    loading.value = false
  }
}

function goAssets() {
  void router.push('/soc/assets')
}

function goAsset(row: ClientSecurityHighRiskAsset) {
  void router.push({ path: '/soc/assets', query: { keyword: row.assetIp } })
}

function goClient(row: ClientSecurityHighRiskAsset) {
  void router.push({ path: '/client/workbench', query: { ip: row.assetIp, host: row.hostname, os: row.osType || '' } })
}

function goEvents(row: ClientSecurityReviewRecord) {
  void router.push({ path: '/soc/external-events', query: { assetIp: row.assetIp || '', sourceType: 'osquery' } })
}

function goEventsByLocalCheck() {
  void router.push({ path: '/soc/external-events', query: { sourceType: 'osquery' } })
}

function goTickets() {
  void router.push({ path: '/soc/tickets', query: { keyword: '员工' } })
}

function goReports() {
  void router.push('/soc/reports')
}

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

function riskLabel(value?: string) {
  const normalized = value || ''
  const labels: Record<string, string> = {
    critical: '严重',
    high: '高',
    medium: '中',
    low: '低',
    unknown: '未知',
  }
  return labels[normalized] || normalized || '-'
}

function riskTagType(value?: string) {
  if (value === 'critical' || value === 'high') return 'danger'
  if (value === 'medium') return 'warning'
  return 'success'
}

function statusLabel(value?: string) {
  const labels: Record<string, string> = {
    new: '待复核',
    reviewing: '复核中',
    closed: '已关闭',
  }
  return labels[value || ''] || value || '-'
}

function eventTypeLabel(value?: string) {
  const labels: Record<string, string> = {
    terminal: '本机检查',
    host_snapshot: '体检记录',
  }
  return labels[value || ''] || value || '-'
}
</script>

<style scoped>
.hero-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.kpi-card {
  padding: 18px;
  display: grid;
  gap: 8px;
}

.kpi-card span,
.kpi-card small {
  color: #64748b;
}

.kpi-card strong {
  font-size: 34px;
  line-height: 1;
}

.kpi-card.danger strong {
  color: #dc2626;
}

.kpi-card.warning strong {
  color: #d97706;
}

.posture-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(320px, .8fr);
  gap: 16px;
  margin-top: 16px;
}

.posture-grid.secondary {
  grid-template-columns: minmax(0, 1.4fr) minmax(300px, .7fr);
}

.soc-panel {
  padding: 16px;
}

.panel-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 14px;
}

.panel-title strong {
  font-size: 16px;
}

.panel-title span {
  color: #94a3b8;
  font-size: 13px;
}

.risk-down-list {
  display: grid;
  gap: 10px;
}

.risk-down-list article {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  border: 1px solid rgba(148, 163, 184, .24);
  border-radius: 8px;
  padding: 12px;
}

.risk-down-list article div {
  display: grid;
  gap: 4px;
}

.risk-down-list article span,
.privacy-note {
  color: #64748b;
  font-size: 13px;
}

.risk-down-list article b {
  color: #059669;
}

.action-panel {
  align-self: start;
}

.action-stack {
  display: grid;
  gap: 10px;
}

.action-stack .el-button {
  margin-left: 0;
}

.privacy-note {
  margin: 14px 0 0;
  line-height: 1.7;
}

@media (max-width: 1180px) {
  .kpi-grid,
  .posture-grid,
  .posture-grid.secondary {
    grid-template-columns: 1fr;
  }
}
</style>
