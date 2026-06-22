<template>
  <div class="page-shell">
    <section class="soc-page-hero">
      <div>
        <span class="soc-page-kicker">ASSET RISK</span>
        <h1>资产风险</h1>
        <p>这个页面帮你查看资产风险、责任人、未关闭告警，并进入单机分析。</p>
      </div>
      <div class="soc-page-tags">
        <el-tag>Wazuh Agent</el-tag>
        <el-tag>Zeek Host</el-tag>
        <el-tag>Trivy Target</el-tag>
        <el-tag type="warning">用户端联动</el-tag>
      </div>
    </section>

    <section class="soc-panel panel-pad">
      <div class="asset-filter-bar">
        <el-input v-model="query.keyword" clearable placeholder="搜索主机名或 IP" @keyup.enter="load" />
        <el-select v-model="query.riskLevel" clearable placeholder="风险">
          <el-option label="严重" value="critical" /><el-option label="高" value="high" /><el-option label="中" value="medium" /><el-option label="低" value="low" />
        </el-select>
        <span />
        <div class="toolbar-actions">
          <el-button :disabled="!selectedAssets.length" @click="copySelectedIps">复制 IP</el-button>
          <el-button :disabled="!selectedAssets.length" @click="exportSelectedAssets">导出清单</el-button>
          <el-button @click="load">查询</el-button>
        </div>
      </div>
    </section>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false">
      <template #default>
        <el-button size="small" @click="load">重试</el-button>
      </template>
    </el-alert>
    <section class="table-panel">
      <el-table v-loading="loading" :data="rows" empty-text="暂无资产数据" @row-click="openAsset" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="46" />
        <el-table-column prop="hostname" label="主机名" min-width="150" />
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column prop="osType" label="系统" width="110" />
        <el-table-column label="来源" width="86"><template #default="{ row }"><DataSourceBadge :source="row.sourceType" /></template></el-table-column>
        <el-table-column label="风险" width="100"><template #default="{ row }"><AssetRiskTag :risk-level="row.riskLevel" /></template></el-table-column>
        <el-table-column label="风险分" width="92">
          <template #default="{ row }">
            <strong class="risk-score-text">{{ row.riskScore ?? '-' }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="openAlertCount" label="未关闭告警" width="110" />
        <el-table-column prop="deptName" label="部门" min-width="140" />
        <el-table-column prop="ownerName" label="负责人" width="120" />
        <el-table-column prop="lastSeenAt" label="最后发现" width="180" />
        <el-table-column label="用户端操作" width="190" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button size="small" link @click.stop="openClientWorkbench(row)">单机分析</el-button>
              <el-button size="small" link @click.stop="openClientLocalRange(row)">本地现场</el-button>
              <el-button size="small" link @click.stop="recalculateRisk(row)">重算风险</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-row">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" layout="total, sizes, prev, pager, next" :total="total" @change="load" />
      </div>
    </section>
    <el-drawer v-model="drawer" title="资产详情" size="520px">
      <div v-if="currentAsset" class="drawer-stack">
        <div class="soc-drawer-grid">
          <span>主机名</span><strong>{{ currentAsset.hostname }}</strong>
          <span>IP 地址</span><strong>{{ currentAsset.ip }}</strong>
          <span>系统类型</span><strong>{{ currentAsset.osType }}</strong>
          <span>数据来源</span><strong><DataSourceBadge :source="currentAsset.sourceType" /></strong>
          <span>风险等级</span><strong><AssetRiskTag :risk-level="currentAsset.riskLevel" /></strong>
          <span>风险分</span><strong>{{ riskProfile?.snapshot.score ?? currentAsset.riskScore ?? '-' }}</strong>
          <span>未关闭告警</span><strong>{{ currentAsset.openAlertCount }}</strong>
          <span>所属部门</span><strong>{{ currentAsset.deptName || '-' }}</strong>
          <span>负责人</span><strong>{{ currentAsset.ownerName || '-' }}</strong>
          <span>最后发现</span><strong>{{ currentAsset.lastSeenAt || '-' }}</strong>
        </div>
        <section v-loading="riskProfileLoading" class="asset-risk-profile-card">
          <div class="profile-head">
            <div>
              <strong>风险画像</strong>
              <span>用告警、漏洞、基线、证据和工单状态解释当前分值。</span>
            </div>
            <el-button size="small" @click="currentAsset && recalculateRisk(currentAsset)">重新计算</el-button>
          </div>
          <div v-if="riskProfile" class="risk-profile-body">
            <div class="risk-reason-block">
              <span>风险升高原因</span>
              <p>{{ riskProfile.statusReason }}</p>
            </div>
            <div class="factor-list">
              <strong>Top 风险因子</strong>
              <article v-for="factor in riskProfile.factors.slice(0, 5)" :key="`${factor.factorType}-${factor.factorName}`">
                <span>{{ factor.factorName }}</span>
                <strong>{{ factor.factorScore > 0 ? '+' : '' }}{{ factor.factorScore }}</strong>
                <small>{{ factor.explanation }}</small>
              </article>
            </div>
            <div class="recommendation-order">
              <strong>推荐处理顺序</strong>
              <div v-if="assetRecommendationRows.length" class="recommendation-cards">
                <article v-for="item in assetRecommendationRows" :key="item.key">
                  <div>
                    <el-tag :type="priorityTag(item.priority)" effect="plain">{{ priorityText(item.priority) }}</el-tag>
                    <strong>{{ item.title }}</strong>
                  </div>
                  <p>{{ item.reason }}</p>
                  <span>{{ item.recommendedAction }}</span>
                  <el-button size="small" text @click="recordViewRecommendation(item)">查看记录</el-button>
                </article>
              </div>
              <ol v-else>
                <li v-for="item in riskRecommendations" :key="item">{{ item }}</li>
              </ol>
            </div>
            <el-empty v-if="!riskProfile.factors.length" description="暂无显著风险因子" :image-size="72" />
          </div>
          <el-empty v-else description="风险画像暂不可用，可稍后重试" :image-size="72" />
        </section>
        <section class="client-handoff">
          <div>
            <strong>用户端联动</strong>
            <span>把后台资产切换到单机工作台，或打开本地授权现场生成可分析遥测。</span>
          </div>
          <div class="client-handoff-actions">
            <el-button type="primary" @click="openClientWorkbench(currentAsset)">进入单机分析</el-button>
            <el-button @click="openClientLocalRange(currentAsset)">打开本地现场</el-button>
          </div>
        </section>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import AssetRiskTag from '@/components/security/AssetRiskTag.vue'
import DataSourceBadge from '@/components/security/DataSourceBadge.vue'
import {
  assetRecommendations,
  assetRiskProfile,
  listAssets,
  recalculateAssetRisk,
  recordRecommendationAction,
  type AssetItem,
  type AssetRiskProfile,
  type RecommendationItem,
} from '@/api/soc'

const router = useRouter()
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', riskLevel: '' })
const rows = ref<AssetItem[]>([])
const total = ref(0)
const drawer = ref(false)
const currentAsset = ref<AssetItem>()
const selectedAssets = ref<AssetItem[]>([])
const loading = ref(false)
const error = ref('')
const riskProfile = ref<AssetRiskProfile>()
const assetRecommendationRows = ref<RecommendationItem[]>([])
const riskProfileLoading = ref(false)
const riskRecommendations = computed(() => {
  const factorRecommendations = (riskProfile.value?.factors || [])
    .filter((factor) => factor.factorScore > 0)
    .map((factor) => factor.recommendation)
    .filter(Boolean)
  const items = [...new Set(factorRecommendations)]
  if (!items.length && riskProfile.value?.recommendationSummary) {
    return riskProfile.value.recommendationSummary.split('；').filter(Boolean).slice(0, 3)
  }
  return items.slice(0, 3)
})

onMounted(load)
async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await listAssets(query)
    rows.value = res.data.data.records
    total.value = res.data.data.total
  } catch {
    error.value = '资产数据加载失败，请检查网络、权限或后端服务状态。'
  } finally {
    loading.value = false
  }
}
function openAsset(row: AssetItem) {
  currentAsset.value = row
  drawer.value = true
  void loadRiskProfile(row)
}
function openClientWorkbench(row: AssetItem) {
  void router.push({ path: '/client/workbench', query: { ip: row.ip } })
}
function openClientLocalRange(row: AssetItem) {
  void router.push({ path: '/client/local-range', query: { ip: row.ip, host: row.hostname } })
}
function onSelectionChange(selection: AssetItem[]) {
  selectedAssets.value = selection
}
async function loadRiskProfile(row: AssetItem) {
  riskProfileLoading.value = true
  riskProfile.value = undefined
  assetRecommendationRows.value = []
  try {
    const [res, recommendationRes] = await Promise.all([
      assetRiskProfile(row.id),
      assetRecommendations(row.id, 8).catch(() => undefined),
    ])
    riskProfile.value = res.data.data
    assetRecommendationRows.value = recommendationRes?.data.data || []
  } catch {
    riskProfile.value = undefined
  } finally {
    riskProfileLoading.value = false
  }
}
async function recalculateRisk(row: AssetItem) {
  riskProfileLoading.value = true
  try {
    const res = await recalculateAssetRisk(row.id)
    riskProfile.value = res.data.data
    Object.assign(row, {
      riskScore: res.data.data.snapshot.score,
      riskLevel: res.data.data.snapshot.riskLevel,
    })
    const recommendationRes = await assetRecommendations(row.id, 8).catch(() => undefined)
    assetRecommendationRows.value = recommendationRes?.data.data || []
    ElMessage.success('资产风险画像已重新计算')
  } catch {
    ElMessage.error('风险画像计算失败，请检查权限或后端服务状态')
  } finally {
    riskProfileLoading.value = false
  }
}
async function copySelectedIps() {
  const text = selectedAssets.value.map((item) => item.ip).join('\n')
  await navigator.clipboard.writeText(text)
  ElMessage.success('已复制选中资产 IP')
}
function exportSelectedAssets() {
  const header = ['主机名', 'IP', '系统', '风险', '部门', '负责人', '未关闭告警', '最后发现']
  const lines = selectedAssets.value.map((item) =>
    [item.hostname, item.ip, item.osType, item.riskLevel, item.deptName, item.ownerName, item.openAlertCount, item.lastSeenAt]
      .map((value) => `"${String(value ?? '').replace(/"/g, '""')}"`)
      .join(',')
  )
  const blob = new Blob([[header.join(','), ...lines].join('\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'asset-risk-list.csv'
  link.click()
  URL.revokeObjectURL(url)
  ElMessage.success('资产清单已生成')
}

async function recordViewRecommendation(item: RecommendationItem) {
  try {
    await recordRecommendationAction(item.key, {
      actionType: 'view',
      relatedBizType: item.relatedBizType,
      relatedBizId: item.relatedBizId,
      assetIp: item.assetIp,
      assetName: item.assetName,
      note: '资产详情查看推荐处理顺序',
    })
    ElMessage.success('已记录查看动作')
  } catch {
    ElMessage.warning('推荐查看记录暂时无法写入')
  }
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
</script>

<style scoped>
.panel-pad { padding: 14px; }
.asset-filter-bar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 160px minmax(12px, 1fr) auto;
  gap: 10px;
  align-items: center;
}
.toolbar-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}
.row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.risk-score-text {
  color: var(--soc-warm-strong);
  font-size: 14px;
}
.asset-risk-profile-card {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid rgba(179, 173, 163, 0.38);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.62);
}
.profile-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}
.profile-head div,
.risk-profile-body,
.factor-list,
.risk-reason-block,
.recommendation-order {
  display: grid;
  gap: 8px;
}
.profile-head strong {
  color: var(--soc-text);
}
.profile-head span,
.risk-profile-body p,
.risk-profile-body em,
.factor-list small,
.risk-reason-block span,
.recommendation-order li {
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.55;
}
.risk-profile-body p,
.risk-profile-body em {
  margin: 0;
}
.risk-profile-body em {
  font-style: normal;
  color: var(--soc-warm-strong);
}
.factor-list > strong,
.recommendation-order > strong {
  color: var(--soc-text);
  font-size: 13px;
}
.risk-reason-block span {
  color: var(--soc-warm-strong);
  font-weight: 700;
}
.recommendation-order ol {
  display: grid;
  gap: 6px;
  margin: 0;
  padding-left: 18px;
}
.recommendation-cards {
  display: grid;
  gap: 8px;
}
.recommendation-cards article {
  display: grid;
  gap: 6px;
  padding: 10px;
  border: 1px solid rgba(179, 173, 163, 0.32);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.62);
}
.recommendation-cards article > div {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.recommendation-cards strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recommendation-cards p,
.recommendation-cards span {
  margin: 0;
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.55;
}
.recommendation-cards span {
  color: var(--soc-warm-strong);
}
.factor-list article {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 4px 10px;
  padding: 10px;
  border: 1px solid rgba(179, 173, 163, 0.32);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.58);
}
.factor-list small {
  grid-column: 1 / -1;
}
.factor-list strong {
  color: var(--soc-warm-strong);
}
.drawer-stack { display: grid; gap: 16px; }
.client-handoff {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid rgba(212, 147, 74, 0.34);
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(255, 248, 238, 0.82), rgba(255, 255, 255, 0.62));
}
.client-handoff div:first-child {
  display: grid;
  gap: 4px;
}
.client-handoff strong {
  color: var(--soc-text);
  font-size: 14px;
}
.client-handoff span {
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.55;
}
.client-handoff-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
@media (max-width: 860px) {
  .asset-filter-bar {
    grid-template-columns: 1fr;
  }
  .toolbar-actions {
    justify-content: flex-start;
  }
}
</style>
