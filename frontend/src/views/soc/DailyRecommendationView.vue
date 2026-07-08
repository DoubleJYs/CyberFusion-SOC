<template>
  <div class="page-shell">
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
    >
      <template #default>
        <el-button size="small" @click="load">重试</el-button>
      </template>
    </el-alert>

    <section class="soc-page-hero daily-hero">
      <div>
        <span class="soc-page-kicker">DAILY RESPONSE</span>
        <h1>每日处理</h1>
        <p>按事件簇、漏洞、工单和员工待办生成当前优先处理队列。</p>
      </div>
      <div class="daily-hero-actions">
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button type="primary" :icon="DataAnalysis" @click="router.push('/soc/dashboard')">安全运营工作台</el-button>
      </div>
    </section>

    <section class="daily-summary-grid">
      <article>
        <span>当前建议</span>
        <strong>{{ rows.length }}</strong>
        <small>{{ sourceCount }} 类来源</small>
      </article>
      <article>
        <span>高优先级</span>
        <strong>{{ urgentCount }}</strong>
        <small>严重 / 高</small>
      </article>
      <article>
        <span>最高评分</span>
        <strong>{{ highestScore }}</strong>
        <small>推荐排序分</small>
      </article>
      <article>
        <span>待处理状态</span>
        <strong>{{ openStatusCount }}</strong>
        <small>open / pending / ticketed</small>
      </article>
    </section>

    <section class="soc-panel daily-filter-panel">
      <el-input
        v-model="keyword"
        clearable
        :prefix-icon="Search"
        placeholder="搜索标题、资产、建议动作..."
      />
      <el-select v-model="priorityFilter" placeholder="优先级" clearable>
        <el-option label="严重" value="critical" />
        <el-option label="高" value="high" />
        <el-option label="中" value="medium" />
        <el-option label="低" value="low" />
      </el-select>
      <el-select v-model="sourceFilter" placeholder="来源" clearable>
        <el-option
          v-for="source in sourceOptions"
          :key="source.value"
          :label="source.label"
          :value="source.value"
        />
      </el-select>
      <el-select v-model="limit" class="limit-select" @change="load">
        <el-option label="Top 10" :value="10" />
        <el-option label="Top 20" :value="20" />
        <el-option label="Top 50" :value="50" />
      </el-select>
    </section>

    <section v-loading="loading" class="soc-panel recommendation-worklist">
      <div class="panel-title">
        <div>
          <strong>今日优先处理建议</strong>
          <span>{{ filteredRows.length }} 条匹配 / {{ rows.length }} 条当前建议</span>
        </div>
      </div>

      <div v-if="filteredRows.length" class="recommendation-list">
        <button
          v-for="item in filteredRows"
          :key="item.key"
          :class="['recommendation-row', `recommendation-row--${item.priority}`]"
          type="button"
          @click="openRecommendation(item)"
        >
          <div class="recommendation-main">
            <div class="recommendation-heading">
              <el-tag :type="priorityTag(item.priority)" effect="plain" size="small">{{ priorityText(item.priority) }}</el-tag>
              <strong>{{ item.title }}</strong>
              <b>{{ item.priorityScore }}</b>
            </div>
            <div class="recommendation-meta">
              <span>{{ recommendationTypeText(item.relatedBizType) }}</span>
              <span v-if="item.assetName || item.assetIp">资产 {{ item.assetName || item.assetIp }}</span>
              <span v-if="item.status">状态 {{ recommendationStatusText(item.status) }}</span>
              <span>{{ item.assigneeType === 'employee' ? '员工' : '分析员' }}</span>
            </div>
            <p>{{ item.reason }}</p>
          </div>
          <div class="recommendation-action">
            <span>建议动作</span>
            <strong>{{ item.recommendedAction }}</strong>
            <div class="recommendation-buttons">
              <el-button size="small" @click.stop="recordView(item)">记录查看</el-button>
              <el-button size="small" type="primary" @click.stop="openRecommendation(item)">进入处理</el-button>
            </div>
          </div>
        </button>
      </div>
      <el-empty v-else description="暂无匹配建议" :image-size="76" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { DataAnalysis, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { recordRecommendationAction, topRecommendations } from '@/api/soc'
import type { RecommendationItem } from '@/api/soc'

const router = useRouter()
const rows = ref<RecommendationItem[]>([])
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const priorityFilter = ref('')
const sourceFilter = ref('')
const limit = ref(20)

const sourceCount = computed(() => new Set(rows.value.map((item) => item.relatedBizType).filter(Boolean)).size)
const urgentCount = computed(() => rows.value.filter((item) => item.priority === 'critical' || item.priority === 'high').length)
const highestScore = computed(() => rows.value.length ? Math.max(...rows.value.map((item) => item.priorityScore || 0)) : 0)
const openStatusCount = computed(() => rows.value.filter((item) => ['open', 'pending', 'todo', 'ticketed', 'in_progress', 'processing'].includes((item.status || '').toLowerCase())).length)

const sourceOptions = computed(() => {
  const types = Array.from(new Set(rows.value.map((item) => item.relatedBizType).filter(Boolean)))
  return types.map((value) => ({ value, label: recommendationTypeText(value) }))
})

const filteredRows = computed(() => {
  const term = keyword.value.trim().toLowerCase()
  return rows.value.filter((item) => {
    if (priorityFilter.value && item.priority !== priorityFilter.value) return false
    if (sourceFilter.value && item.relatedBizType !== sourceFilter.value) return false
    if (!term) return true
    return [
      item.title,
      item.reason,
      item.recommendedAction,
      item.assetName,
      item.assetIp,
      recommendationTypeText(item.relatedBizType),
      recommendationStatusText(item.status || ''),
    ].some((value) => String(value || '').toLowerCase().includes(term))
  })
})

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = (await topRecommendations(limit.value)).data.data
  } catch {
    rows.value = []
    error.value = '每日处理建议加载失败，请检查后端推荐动作接口。'
  } finally {
    loading.value = false
  }
}

async function recordView(item: RecommendationItem, silent = false) {
  try {
    await recordRecommendationAction(item.key, {
      actionType: 'view',
      relatedBizType: item.relatedBizType,
      relatedBizId: item.relatedBizId,
      assetIp: item.assetIp,
      assetName: item.assetName,
      note: '每日处理页面查看推荐动作',
    })
    if (!silent) ElMessage.success('已记录查看动作')
  } catch {
    if (!silent) ElMessage.warning('推荐查看记录暂时无法写入')
  }
}

async function openRecommendation(item: RecommendationItem) {
  await recordView(item, true)
  const keyword = recommendationKeyword(item)
  if (item.relatedBizType === 'incident') {
    router.push({ path: '/soc/incidents', query: { keyword, openIncidentId: item.relatedBizId } })
  } else if (item.relatedBizType === 'ticket' || item.relatedBizType === 'playbook_task' || item.relatedBizType === 'client_task') {
    const ticketQuery: Record<string, string | number | undefined> = { keyword: ticketKeyword(item) }
    if (item.relatedBizType === 'ticket') ticketQuery.openTicketId = item.relatedBizId
    router.push({ path: '/soc/tickets', query: ticketQuery })
  } else if (item.relatedBizType === 'vulnerability') {
    router.push({ path: '/soc/vulnerabilities', query: { keyword, openVulnerabilityId: item.relatedBizId } })
  } else if (item.relatedBizType === 'client_checkup' && (item.assetIp || item.assetName)) {
    router.push({ path: '/client/workbench', query: { ip: item.assetIp || item.assetName } })
  } else if (item.assetIp || item.assetName) {
    router.push({ path: '/soc/assets', query: { keyword, openAssetId: item.assetId } })
  }
}

function recommendationKeyword(item: RecommendationItem) {
  return item.assetIp || item.assetName || normalizedRecommendationTitle(item) || String(item.relatedBizId || '')
}

function ticketKeyword(item: RecommendationItem) {
  const match = /INC-[A-Za-z0-9_-]+/.exec(item.title || '')
  return match?.[0] || recommendationKeyword(item)
}

function normalizedRecommendationTitle(item: RecommendationItem) {
  return String(item.title || '')
    .replace(/^(优先处理事件簇|推动超时工单|跟进工单|完成剧本任务|跟进员工待办|复核员工体检结果|请先完成本机检查)：/, '')
    .trim()
}

function priorityTag(priority: string) {
  if (priority === 'critical' || priority === 'high') return 'danger'
  if (priority === 'medium') return 'warning'
  return 'info'
}

function priorityText(priority: string) {
  if (priority === 'critical') return '严重'
  if (priority === 'high') return '高'
  if (priority === 'medium') return '中'
  return '低'
}

function recommendationTypeText(type: string) {
  if (type === 'incident') return '事件簇'
  if (type === 'vulnerability') return '漏洞'
  if (type === 'ticket') return '工单'
  if (type === 'client_task') return '员工待办'
  if (type === 'playbook_task') return '剧本任务'
  if (type === 'client_checkup') return '本机体检'
  if (type === 'risk_factor') return '风险因子'
  return type || '推荐'
}

function recommendationStatusText(status: string) {
  const normalized = status.toLowerCase()
  if (normalized === 'view') return '已查看'
  if (['open', 'pending', 'todo'].includes(normalized)) return '待处理'
  if (['ticketed', 'in_progress', 'processing'].includes(normalized)) return '处理中'
  if (['confirmed', 'completed', 'closed', 'fixed', 'accepted'].includes(normalized)) return '已闭环'
  return status
}
</script>

<style scoped>
.daily-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.daily-hero-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.daily-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}
.daily-summary-grid article {
  min-height: 118px;
  border: 1px solid rgba(190, 183, 171, 0.5);
  border-radius: var(--soc-radius-card);
  background: rgba(255, 255, 255, 0.68);
  padding: 16px;
}
.daily-summary-grid span,
.daily-summary-grid small {
  display: block;
  color: var(--soc-text-muted);
  line-height: 1.5;
}
.daily-summary-grid strong {
  display: block;
  margin: 8px 0;
  color: var(--soc-text);
  font-size: 32px;
  line-height: 1;
}
.daily-filter-panel {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) 140px 160px 120px;
  gap: 12px;
  padding: 14px 16px;
}
.recommendation-worklist {
  padding: 16px;
}
.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.panel-title strong,
.panel-title span {
  display: block;
}
.panel-title span {
  margin-top: 4px;
  color: var(--soc-text-muted);
  font-size: 13px;
}
.recommendation-list {
  display: grid;
  gap: 10px;
}
.recommendation-row {
  --recommendation-accent: var(--soc-medium);
  width: 100%;
  min-width: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(260px, 36%);
  gap: 14px;
  border: 1px solid rgba(190, 183, 171, 0.5);
  border-left: 4px solid var(--recommendation-accent);
  border-radius: var(--soc-radius-card);
  background: rgba(255, 255, 255, 0.64);
  cursor: pointer;
  padding: 12px;
  text-align: left;
}
.recommendation-row:hover {
  border-color: rgba(219, 126, 42, 0.48);
  background: rgba(255, 250, 243, 0.78);
}
.recommendation-row--critical {
  --recommendation-accent: #ef4444;
}
.recommendation-row--high {
  --recommendation-accent: var(--soc-high);
}
.recommendation-row--medium {
  --recommendation-accent: var(--soc-medium);
}
.recommendation-row--low {
  --recommendation-accent: var(--soc-low);
}
.recommendation-main,
.recommendation-action {
  min-width: 0;
}
.recommendation-heading {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}
.recommendation-heading strong {
  overflow: hidden;
  color: var(--soc-text);
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recommendation-heading b {
  color: var(--recommendation-accent);
  font-size: 24px;
  line-height: 1;
}
.recommendation-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  margin-top: 8px;
  color: var(--soc-text-muted);
  font-size: 12px;
}
.recommendation-main p {
  margin: 8px 0 0;
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.55;
}
.recommendation-action {
  display: flex;
  flex-direction: column;
  justify-content: center;
  border-left: 1px solid rgba(190, 183, 171, 0.38);
  padding-left: 12px;
}
.recommendation-action span {
  color: var(--soc-text-muted);
  font-size: 12px;
}
.recommendation-action strong {
  display: block;
  margin-top: 6px;
  color: var(--soc-warm-strong);
  font-size: 13px;
  line-height: 1.55;
}
.recommendation-buttons {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
}
@media (max-width: 980px) {
  .daily-hero,
  .panel-title {
    align-items: flex-start;
    flex-direction: column;
  }
  .daily-hero-actions {
    justify-content: flex-start;
  }
  .daily-summary-grid,
  .daily-filter-panel,
  .recommendation-row {
    grid-template-columns: 1fr;
  }
  .recommendation-action {
    border-top: 1px solid rgba(190, 183, 171, 0.38);
    border-left: 0;
    padding-top: 10px;
    padding-left: 0;
  }
}
</style>
