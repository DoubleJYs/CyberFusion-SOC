<template>
  <div class="page-shell">
    <el-alert v-if="fallback" title="后端不可用，当前显示 fallback mock 数据" type="warning" show-icon />
    <section class="metrics-grid">
      <article v-for="metric in metrics" :key="metric.label" class="metric">
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
        <small>{{ metric.tip }}</small>
      </article>
    </section>
    <section class="dashboard-grid">
      <div class="panel">
        <h2>近 7 日操作趋势</h2>
        <div ref="chartRef" class="chart"></div>
      </div>
      <div class="panel">
        <h2>最近登录</h2>
        <el-table :data="recentLogins" size="small" empty-text="暂无登录记录">
          <el-table-column prop="username" label="账号" min-width="90" />
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }"><StatusTag :value="row.status" /></template>
          </el-table-column>
          <el-table-column prop="createdAt" label="时间" min-width="150" />
        </el-table>
      </div>
    </section>
    <section class="dashboard-grid">
      <div class="panel notices-panel">
        <h2>通知公告</h2>
        <el-empty v-if="activeNotices.length === 0" description="暂无有效公告" :image-size="72" />
        <div v-else class="notice-list">
          <article v-for="notice in activeNotices" :key="notice.id" class="notice-item">
            <div class="notice-heading">
              <el-tag v-if="notice.pinned === 1" size="small" type="danger">置顶</el-tag>
              <el-tag size="small">{{ noticeTypeLabel(notice.noticeType) }}</el-tag>
              <strong>{{ notice.noticeTitle }}</strong>
            </div>
            <p>{{ notice.noticeContent }}</p>
            <small>{{ notice.publishAt }} · {{ notice.expireAt ? `有效至 ${notice.expireAt}` : '长期有效' }}</small>
          </article>
        </div>
      </div>
      <div class="panel">
        <h2>最近操作</h2>
        <el-table :data="overview?.recentOperations || []" size="small" empty-text="暂无操作记录">
          <el-table-column prop="username" label="用户" width="90" />
          <el-table-column prop="action" label="动作" min-width="130" />
          <el-table-column prop="path" label="接口" min-width="180" />
          <el-table-column prop="status" label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        </el-table>
      </div>
      <div class="panel">
        <h2>系统模块</h2>
        <el-timeline>
          <el-timeline-item v-for="item in modules" :key="item.name" type="success" :timestamp="item.status">
            <strong>{{ item.name }}</strong>
            <p>{{ item.description }}</p>
          </el-timeline-item>
        </el-timeline>
      </div>
    </section>
    <div class="panel guide">
      <h2>SOC 平台说明</h2>
      <p>该页面保留系统管理侧状态概览；安全运营主流程请使用安全总览、告警中心、资产视图、工单中心和报表中心。</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { init, use, type ECharts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import { fetchOperationTrend, fetchOverview, fetchRecentLogins, fetchSystemModules, type ModuleItem, type Overview, type TrendItem } from '@/api/dashboard'
import { fetchActiveNotices } from '@/api/notice'
import type { LoginLogRecord, NoticeRecord } from '@/types/system'

use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const overview = ref<Overview>()
const recentLogins = ref<LoginLogRecord[]>([])
const activeNotices = ref<NoticeRecord[]>([])
const trend = ref<TrendItem[]>([])
const modules = ref<ModuleItem[]>([])
const fallback = ref(false)
const chartRef = ref<HTMLDivElement>()
let chart: ECharts | null = null
function handleResize() {
  chart?.resize()
}

const metrics = computed(() => [
  { label: '用户数', value: overview.value?.userCount ?? 0, tip: 'sys_user' },
  { label: '角色数', value: overview.value?.roleCount ?? 0, tip: 'sys_role' },
  { label: '菜单数', value: overview.value?.menuCount ?? 0, tip: 'sys_menu' },
  { label: '有效公告', value: overview.value?.noticeCount ?? 0, tip: 'sys_notice' },
  { label: '今日登录', value: overview.value?.todayLoginCount ?? 0, tip: 'sys_login_log' },
])

async function load() {
  try {
    const [overviewData, loginData, trendData, moduleData, noticeData] = await Promise.all([
      fetchOverview(),
      fetchRecentLogins(),
      fetchOperationTrend(),
      fetchSystemModules(),
      fetchActiveNotices({ limit: 4 }),
    ])
    overview.value = overviewData
    recentLogins.value = loginData
    trend.value = trendData
    modules.value = moduleData
    activeNotices.value = noticeData
  } catch {
    fallback.value = true
    trend.value = Array.from({ length: 7 }, (_, index) => ({ date: `D-${6 - index}`, count: 0 }))
    modules.value = [{ name: '后端连接', description: '请启动 Spring Boot 服务后刷新', status: 'FALLBACK' }]
  }
  await nextTick()
  renderChart()
}

function noticeTypeLabel(value: string) {
  const labels: Record<string, string> = {
    system: '系统公告',
    maintenance: '维护通知',
    release: '版本发布',
  }
  return labels[value] || value
}

function renderChart() {
  if (!chartRef.value) return
  chart ||= init(chartRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 32, right: 20, top: 24, bottom: 28 },
    xAxis: { type: 'category', data: trend.value.map((item) => item.date) },
    yAxis: { type: 'value' },
    series: [{ type: 'line', smooth: true, areaStyle: {}, data: trend.value.map((item) => item.count) }],
    color: ['#2563eb'],
  })
}

onMounted(() => {
  load()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
})
</script>

<style scoped>
.metrics-grid,
.dashboard-grid {
  display: grid;
  gap: 16px;
}

.metrics-grid {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.dashboard-grid {
  grid-template-columns: minmax(0, 1.4fr) minmax(320px, 0.8fr);
  margin-top: 16px;
}

.metric,
.panel {
  min-width: 0;
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.metric {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.metric span,
.metric small,
.guide p {
  color: #64748b;
}

.metric strong {
  font-size: 28px;
}

.panel h2 {
  margin: 0 0 12px;
  font-size: 16px;
}

.notice-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.notice-item {
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.notice-heading {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.notice-heading strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-item p {
  margin: 8px 0;
  color: #334155;
  line-height: 1.6;
}

.notice-item small {
  color: #64748b;
}

.chart {
  height: 320px;
}

.guide {
  margin-top: 16px;
}

@media (max-width: 1100px) {
  .metrics-grid,
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}
</style>
