<template>
  <div class="client-module-page">
    <section class="client-module-hero">
      <div>
        <span class="soc-page-kicker">SUBMIT LOGS</span>
        <h1>提交日志</h1>
        <p>这个页面帮你提交已授权的本机日志，并在提交前检查敏感内容。</p>
      </div>
      <div class="hero-actions">
        <el-tag effect="plain" size="large">{{ selectedAsset ? `${selectedAsset.hostname} / ${selectedAsset.ip}` : '电脑未接入' }}</el-tag>
        <el-button :loading="loading" @click="loadData">刷新</el-button>
        <el-button @click="router.push({ path: '/client/workbench', query: clientDeviceQuery })">回到我的电脑</el-button>
      </div>
    </section>

    <section v-if="error" class="client-recoverable-error">
      <div>
        <span>数据加载失败</span>
        <strong>{{ error }}</strong>
        <p>可能是后端未启动、数据库未初始化或当前账号没有查看当前电脑的权限。</p>
      </div>
      <div class="recover-actions">
        <el-button type="primary" :loading="loading" @click="loadData">重试</el-button>
        <el-button @click="useOfflineDemoData">使用离线演示数据</el-button>
        <el-button text @click="showDiagnostics = !showDiagnostics">查看诊断</el-button>
      </div>
      <pre v-if="showDiagnostics">{{ errorDiagnostic || '暂无更多诊断信息。' }}</pre>
    </section>

    <section class="module-shell">
      <aside class="module-aside">
        <div class="device-chip">
          <strong>{{ selectedAsset?.hostname || '当前电脑' }}</strong>
          <span>{{ selectedAsset?.ip || selectedIp || '-' }} · {{ selectedAsset?.osType || '待识别' }}</span>
          <em>{{ selectedAsset?.ownerName || currentUsername }} · {{ selectedAsset?.deptName || '本机环境' }}</em>
        </div>
        <button
          v-for="module in reportModules"
          :key="module.name"
          type="button"
          :class="{ active: activeModule === module.name }"
          @click="activeModule = module.name"
        >
          <strong>{{ module.label }}</strong>
          <span>{{ module.desc }}</span>
        </button>
      </aside>

      <main class="module-main">
        <section v-if="activeModule === 'boundary'" class="soc-panel module-panel">
          <div class="panel-title">
            <div>
              <strong>授权边界</strong>
              <span>先确认数据边界，再进入提交页</span>
            </div>
            <el-tag type="warning" effect="plain">不提交敏感内容</el-tag>
          </div>
          <div class="boundary-grid">
            <article v-for="item in boundaryChecklist" :key="item.title">
              <strong>{{ item.title }}</strong>
              <span>{{ item.desc }}</span>
            </article>
          </div>
        </section>

        <section v-else-if="activeModule === 'submit'" class="soc-panel module-panel">
          <div class="panel-title">
            <div>
              <strong>日志提交</strong>
              <span>提交后由安全团队统一分析，运行数据由 Environment 承载</span>
            </div>
            <el-tag effect="plain">{{ selectedAsset?.ip || '当前电脑' }}</el-tag>
          </div>
          <el-form class="report-form" label-position="top">
            <el-form-item label="数据来源">
              <el-select v-model="importForm.sourceType">
                <el-option label="Wazuh 主机告警" value="wazuh" />
                <el-option label="Zeek 网络日志" value="zeek" />
                <el-option label="Suricata EVE" value="suricata" />
                <el-option label="MISP IOC" value="misp" />
                <el-option label="Trivy JSON" value="trivy" />
                <el-option label="ZAP JSON" value="zap" />
                <el-option label="osquery / 本机记录" value="osquery" />
              </el-select>
            </el-form-item>
            <el-form-item label="日志内容">
              <el-input
                v-model="importForm.content"
                type="textarea"
                :rows="11"
                maxlength="200000"
                show-word-limit
                placeholder="粘贴已授权采集的日志或 JSON；不要提交密码、token、私钥或客户敏感数据。"
              />
            </el-form-item>
            <el-form-item label="同步提醒">
              <el-switch v-model="importForm.linkAlerts" active-text="生成提醒" inactive-text="只保存记录" />
            </el-form-item>
            <div class="form-actions">
              <el-button type="primary" :loading="importing" @click="submitImport">提交给安全团队</el-button>
              <el-button @click="fillDeviceSummary">填入当前电脑摘要</el-button>
            </div>
          </el-form>
        </section>

        <section v-else-if="activeModule === 'review'" class="soc-panel module-panel">
          <div class="panel-title">
            <div>
              <strong>脱敏检查</strong>
              <span>提交前在浏览器本地检查敏感字段，不上传敏感片段</span>
            </div>
            <el-tag :type="preflightBlocked ? 'danger' : 'success'" effect="plain">{{ preflightBlocked ? '禁止提交' : '允许提交' }}</el-tag>
          </div>
          <div class="review-grid">
            <article v-for="item in preflightRows" :key="item.key" :class="`review-${item.state}`">
              <div>
                <strong>{{ item.label }}</strong>
                <span>{{ item.desc }}</span>
              </div>
              <el-tag :type="item.tagType" effect="plain">{{ item.status }}</el-tag>
            </article>
          </div>
          <div v-if="sensitiveContentIssues.length" class="review-blocker">
            <strong>需要先处理</strong>
            <span v-for="item in sensitiveContentIssues" :key="item">{{ item }}</span>
          </div>
          <div class="form-actions review-actions">
            <el-button @click="activeModule = 'submit'">返回修改</el-button>
            <el-button :disabled="preflightBlocked" :loading="importing" @click="submitImport">通过检查并提交</el-button>
          </div>
        </section>

        <section v-else class="soc-panel module-panel">
          <div class="panel-title">
            <div>
              <strong>提交记录</strong>
              <span>按当前电脑保存最近提交批次，详细记录由安全团队查看</span>
            </div>
            <el-tag :type="lastResult ? 'success' : 'info'" effect="plain">{{ reportHistory.length }} 个批次</el-tag>
          </div>
          <div v-if="lastResult" class="result-grid">
            <article>
              <span>新增事件</span>
              <strong>{{ lastResult.createdEvents }}</strong>
            </article>
            <article>
              <span>更新事件</span>
              <strong>{{ lastResult.updatedEvents }}</strong>
            </article>
            <article>
              <span>关联告警</span>
              <strong>{{ lastResult.linkedAlerts }}</strong>
            </article>
            <article>
              <span>跳过行数</span>
              <strong>{{ lastResult.skippedLines }}</strong>
            </article>
          </div>
          <el-empty v-else description="还没有日志提交记录" :image-size="88" />
          <div v-if="lastResult?.errors.length" class="result-errors">
            <strong>导入提示</strong>
            <span v-for="item in lastResult.errors" :key="item">{{ item }}</span>
          </div>
          <div v-if="pagedReportHistory.length" class="report-history">
            <div class="history-title">
              <strong>最近提交批次</strong>
              <span>第 {{ historyPage }} / {{ historyPageCount }} 页</span>
            </div>
            <button
              v-for="item in pagedReportHistory"
              :key="item.id"
              type="button"
              class="history-row"
              @click="selectHistory(item)"
            >
              <span>{{ formatTime(item.submittedAt) }}</span>
              <strong>{{ sourceLabel(item.sourceType) }}</strong>
              <em>新增 {{ item.result.createdEvents }} / 告警 {{ item.result.linkedAlerts }} / 跳过 {{ item.result.skippedLines }}</em>
            </button>
            <div v-if="reportHistory.length > historyPageSize" class="history-pagination">
              <el-button size="small" :disabled="historyPage <= 1" @click="goHistoryPage(historyPage - 1)">上一页</el-button>
              <button
                v-for="page in historyVisiblePages"
                :key="page"
                type="button"
                :class="{ active: page === historyPage }"
                @click="goHistoryPage(page)"
              >
                {{ page }}
              </button>
              <el-button size="small" :disabled="historyPage >= historyPageCount" @click="goHistoryPage(historyPage + 1)">下一页</el-button>
            </div>
          </div>
          <div class="backend-actions">
            <el-button @click="router.push({ path: '/soc/external-events', query: backendQuery })">查看安全记录</el-button>
            <el-button @click="router.push({ path: '/soc/alerts', query: backendQuery })">查看关联提醒</el-button>
          </div>
        </section>
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import {
  importCyberFusionEvents,
  type AssetItem,
  type CyberFusionImportResult,
} from '@/api/soc'
import { useAuthStore } from '@/stores/auth'
import { buildClientDeviceRouteQuery, chooseClientAsset, loadClientAssets } from '@/composables/useClientDeviceContext'

type ReportModule = 'boundary' | 'submit' | 'review' | 'result'
type ReviewState = 'ok' | 'warn' | 'risk'
type ReportHistoryItem = {
  id: string
  submittedAt: string
  assetIp: string
  assetName: string
  sourceType: string
  linkAlerts: boolean
  result: CyberFusionImportResult
}

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const loading = ref(false)
const importing = ref(false)
const error = ref('')
const errorDiagnostic = ref('')
const showDiagnostics = ref(false)
const selectedIp = ref('')
const assets = ref<AssetItem[]>([])
const activeModule = ref<ReportModule>('boundary')
const lastResult = ref<CyberFusionImportResult>()
const reportHistory = ref<ReportHistoryItem[]>([])
const historyPage = ref(1)
const historyPageSize = 5
const importForm = reactive({ sourceType: 'osquery', content: '', linkAlerts: true })

const selectedAsset = computed(() => assets.value.find((asset) => asset.ip === selectedIp.value))
const currentUsername = computed(() => authStore.userInfo?.nickname || authStore.userInfo?.username || '当前办公用户')
const backendQuery = computed(() => {
  const asset = selectedAsset.value
  return asset ? { assetIp: asset.ip, keyword: asset.hostname, source: 'client-data-report' } : { source: 'client-data-report' }
})
const clientDeviceQuery = computed(() => buildClientDeviceRouteQuery({
  ip: selectedAsset.value?.ip || selectedIp.value,
  host: selectedAsset.value?.hostname,
  os: selectedAsset.value?.osType,
}))

const reportModules = [
  { name: 'boundary' as const, label: '授权边界', desc: '确认可提交范围与脱敏要求' },
  { name: 'submit' as const, label: '日志提交', desc: '粘贴授权日志并提交' },
  { name: 'review' as const, label: '脱敏检查', desc: '本地检查敏感字段' },
  { name: 'result' as const, label: '提交记录', desc: '分页查看最近上报批次' },
]
const pagedReportHistory = computed(() => {
  const start = (historyPage.value - 1) * historyPageSize
  return reportHistory.value.slice(start, start + historyPageSize)
})
const historyPageCount = computed(() => Math.max(1, Math.ceil(reportHistory.value.length / historyPageSize)))
const historyVisiblePages = computed(() => Array.from({ length: historyPageCount.value }, (_, index) => index + 1))

const boundaryChecklist = [
  { title: '只提交当前电脑或授权系统数据', desc: '员工端不接收外部扫描结果，不导入未知来源样本。' },
  { title: '先脱敏再提交', desc: '不得提交密码、token、私钥、客户敏感数据或真实业务明细。' },
  { title: '安全团队留痕', desc: '提交内容会保存为安全记录，并由安全团队负责关联提醒和留痕。' },
  { title: '跨系统兼容', desc: '页面只依赖浏览器标准能力，macOS、Windows 和项目重启后都通过路由恢复。' },
]
const sensitiveContentIssues = computed(() => detectSensitiveContent(importForm.content))
const preflightBlocked = computed(() => Boolean(sensitiveContentIssues.value.length) || !importForm.content.trim() || !selectedAsset.value)
const preflightRows = computed(() => [
  {
    key: 'asset',
    label: '当前电脑上下文',
    status: selectedAsset.value ? '已绑定' : '未绑定',
    desc: selectedAsset.value ? `${selectedAsset.value.hostname} / ${selectedAsset.value.ip}` : '请先确认当前电脑资产。',
    state: selectedAsset.value ? 'ok' : 'risk',
    tagType: selectedAsset.value ? 'success' : 'danger',
  },
  {
    key: 'content',
    label: '日志内容',
    status: importForm.content.trim() ? `${importForm.content.length} 字符` : '为空',
    desc: importForm.content.trim() ? '仅在浏览器本地检查，不会保存敏感片段。' : '需要粘贴授权日志或使用当前电脑摘要。',
    state: importForm.content.trim() ? 'ok' : 'risk',
    tagType: importForm.content.trim() ? 'success' : 'danger',
  },
  {
    key: 'secret',
    label: '敏感字段',
    status: sensitiveContentIssues.value.length ? `${sensitiveContentIssues.value.length} 项风险` : '未发现',
    desc: sensitiveContentIssues.value.length ? '检测到密钥、token、密码字段或私钥特征。' : '未发现明显密钥、token、密码或私钥特征。',
    state: sensitiveContentIssues.value.length ? 'risk' : 'ok',
    tagType: sensitiveContentIssues.value.length ? 'danger' : 'success',
  },
  {
    key: 'source',
    label: '来源类型',
    status: sourceLabel(importForm.sourceType),
    desc: '来源类型会进入安全记录和审计记录。',
    state: 'ok',
    tagType: 'success',
  },
  {
    key: 'alert',
    label: '告警联动',
    status: importForm.linkAlerts ? '生成提醒' : '只保存记录',
    desc: importForm.linkAlerts ? '系统会尝试生成或关联提醒。' : '仅作为安全记录留存。',
    state: importForm.linkAlerts ? 'warn' : 'ok',
    tagType: importForm.linkAlerts ? 'warning' : 'success',
  },
] satisfies Array<{ key: string; label: string; status: string; desc: string; state: ReviewState; tagType: 'success' | 'warning' | 'danger' }>)

onMounted(() => {
  void loadData()
})

async function loadData() {
  loading.value = true
  error.value = ''
  errorDiagnostic.value = ''
  try {
    const loaded = await loadClientAssets()
    assets.value = loaded.records
    selectedIp.value = chooseClientAsset(assets.value, {
      routeIp: typeof route.query.ip === 'string' ? route.query.ip : '',
      routeHost: typeof route.query.host === 'string' ? route.query.host : '',
      currentNames: [authStore.userInfo?.nickname, authStore.userInfo?.username],
    })?.ip || ''
    loadReportHistory()
  } catch (err) {
    error.value = '提交日志页面加载失败，请检查登录状态、后端服务或接口权限。'
    errorDiagnostic.value = err instanceof Error ? err.message : String(err)
  } finally {
    loading.value = false
  }
}

function useOfflineDemoData() {
  const asset: AssetItem = {
    id: 0,
    hostname: 'prod-app-01',
    ip: '10.20.1.15',
    osType: 'Linux',
    sourceType: 'offline-demo',
    riskLevel: 'medium',
    deptName: '演示部门',
    ownerName: currentUsername.value,
    openAlertCount: 0,
    lastSeenAt: new Date().toISOString(),
  }
  assets.value = [asset]
  selectedIp.value = asset.ip
  error.value = ''
  errorDiagnostic.value = ''
  showDiagnostics.value = false
  ElMessage.warning('已切换为离线演示数据')
}

function fillDeviceSummary() {
  const asset = selectedAsset.value
  if (!asset) {
    ElMessage.warning('当前电脑尚未接入，无法生成摘要')
    return
  }
  importForm.sourceType = 'osquery'
  importForm.content = JSON.stringify({
    eventType: 'client_manual_data_report',
    collectedAt: new Date().toISOString(),
    asset: {
      hostname: asset.hostname,
      ip: asset.ip,
      osType: asset.osType,
      ownerName: asset.ownerName,
      deptName: asset.deptName,
      riskLevel: asset.riskLevel,
    },
    note: '员工端授权提交日志页面生成的当前电脑摘要',
  }, null, 2)
}

async function submitImport() {
  if (!importForm.content.trim()) {
    ElMessage.warning('请先粘贴要提交的日志内容')
    activeModule.value = 'submit'
    return
  }
  if (sensitiveContentIssues.value.length || !selectedAsset.value) {
    ElMessage.warning('脱敏检查未通过，请先处理敏感字段或当前电脑上下文')
    activeModule.value = 'review'
    return
  }
  importing.value = true
  try {
    const res = await importCyberFusionEvents({ ...importForm })
    lastResult.value = res.data.data
    pushReportHistory(res.data.data)
    importForm.content = ''
    activeModule.value = 'result'
    ElMessage.success(`已提交给安全团队：新增 ${res.data.data.createdEvents} 条记录，关联提醒 ${res.data.data.linkedAlerts} 条`)
  } finally {
    importing.value = false
  }
}

function historyStorageKey() {
  return `cyberfusion_client_report_history_${selectedIp.value || 'unknown'}`
}

function loadReportHistory() {
  try {
    const parsed = JSON.parse(localStorage.getItem(historyStorageKey()) || '[]') as ReportHistoryItem[]
    reportHistory.value = Array.isArray(parsed) ? parsed.slice(0, 20) : []
    lastResult.value = reportHistory.value[0]?.result
    historyPage.value = 1
  } catch {
    reportHistory.value = []
    lastResult.value = undefined
  }
}

function persistReportHistory() {
  localStorage.setItem(historyStorageKey(), JSON.stringify(reportHistory.value.slice(0, 20)))
}

function pushReportHistory(result: CyberFusionImportResult) {
  const asset = selectedAsset.value
  const item: ReportHistoryItem = {
    id: `report-${Date.now()}`,
    submittedAt: new Date().toISOString(),
    assetIp: asset?.ip || selectedIp.value || '-',
    assetName: asset?.hostname || '当前电脑',
    sourceType: importForm.sourceType,
    linkAlerts: importForm.linkAlerts,
    result,
  }
  reportHistory.value = [item, ...reportHistory.value].slice(0, 20)
  historyPage.value = 1
  persistReportHistory()
}

function selectHistory(item: ReportHistoryItem) {
  lastResult.value = item.result
}

function goHistoryPage(page: number) {
  historyPage.value = Math.min(Math.max(1, page), historyPageCount.value)
}

function sourceLabel(value: string) {
  const labels: Record<string, string> = {
    wazuh: 'Wazuh 主机告警',
    zeek: 'Zeek 网络日志',
    suricata: 'Suricata EVE',
    misp: 'MISP IOC',
    trivy: 'Trivy JSON',
    zap: 'ZAP JSON',
    osquery: 'osquery / 本机记录',
  }
  return labels[value] || value
}

function formatTime(value: string) {
  return value.replace('T', ' ').slice(0, 16)
}

function detectSensitiveContent(value: string) {
  const text = value || ''
  const issues: string[] = []
  const patterns: Array<{ label: string; regex: RegExp }> = [
    { label: '私钥块', regex: /-----BEGIN [A-Z ]*PRIVATE KEY-----/i },
    { label: 'OpenAI/API token', regex: /\bsk-(proj|live|test)-[A-Za-z0-9_-]{12,}\b/i },
    { label: 'GitHub token', regex: /\bgh[pousr]_[A-Za-z0-9_]{20,}\b/i },
    { label: 'AWS Access Key', regex: /\bAKIA[0-9A-Z]{16}\b/ },
    { label: 'Slack token', regex: /\bxox[baprs]-[A-Za-z0-9-]{10,}\b/i },
    { label: '密码字段', regex: /"(password|passwd|pwd|secret|token|api[_-]?key)"\s*:\s*"[^"]+"/i },
    { label: 'URL 凭据', regex: /https?:\/\/[^/\s:@]+:[^/\s:@]+@/i },
  ]
  patterns.forEach((item) => {
    if (item.regex.test(text)) issues.push(item.label)
  })
  return Array.from(new Set(issues))
}
</script>

<style scoped>
.client-module-page {
  display: grid;
  gap: 14px;
}

.client-recoverable-error {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid rgba(216, 76, 88, 0.28);
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(255, 241, 242, 0.92), rgba(255, 255, 255, 0.74));
  box-shadow: var(--soc-shadow-soft), var(--soc-glass-highlight);
}

.client-recoverable-error span,
.client-recoverable-error p {
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.client-recoverable-error strong {
  color: var(--soc-text);
  font-size: 16px;
}

.client-recoverable-error p {
  margin: 0;
}

.client-recoverable-error pre {
  max-height: 160px;
  overflow: auto;
  margin: 0;
  padding: 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--soc-text-muted);
  font-size: 12px;
  white-space: pre-wrap;
}

.recover-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.client-module-hero,
.module-shell,
.module-aside,
.module-panel {
  border: 1px solid var(--soc-border);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: var(--soc-shadow-soft), var(--soc-glass-highlight);
  backdrop-filter: blur(22px) saturate(1.12);
}

.client-module-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  align-items: end;
  padding: 18px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.9), rgba(255, 248, 238, 0.7), rgba(238, 246, 247, 0.56));
}

.client-module-hero h1 {
  margin: 0;
  color: var(--soc-text);
  font-size: 22px;
}

.client-module-hero p,
.device-chip span,
.device-chip em,
.module-aside button span,
.panel-title span,
.boundary-grid span,
.result-errors span {
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.55;
}

.hero-actions,
.form-actions,
.backend-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.module-shell {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 14px;
  padding: 14px;
  background: rgba(255, 255, 255, 0.46);
}

.module-aside {
  display: grid;
  gap: 10px;
  align-content: start;
  padding: 12px;
  background: linear-gradient(135deg, rgba(255, 248, 238, 0.58), rgba(255, 255, 255, 0.52));
}

.device-chip,
.module-aside button,
.boundary-grid article,
.result-grid article,
.result-errors,
.history-row,
.review-grid article,
.review-blocker {
  border: 1px solid rgba(179, 173, 163, 0.38);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.62);
}

.device-chip,
.module-aside button,
.boundary-grid article,
.result-grid article,
.result-errors,
.history-row,
.review-grid article,
.review-blocker {
  display: grid;
  gap: 5px;
  padding: 12px;
}

.module-aside button,
.history-row {
  text-align: left;
  cursor: pointer;
}

.module-aside button.active,
.module-aside button:hover,
.history-row:hover {
  border-color: rgba(212, 147, 74, 0.56);
  background: rgba(255, 248, 238, 0.84);
  box-shadow: inset 3px 0 0 var(--soc-warm);
}

.device-chip strong,
.module-aside strong,
.panel-title strong,
.boundary-grid strong,
.result-errors strong,
.history-title strong,
.history-row strong,
.review-grid strong,
.review-blocker strong {
  color: var(--soc-text);
}

.device-chip em {
  font-style: normal;
}

.module-panel {
  min-height: 460px;
  padding: 16px;
  box-shadow: none;
}

.panel-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-title div {
  display: grid;
  gap: 3px;
}

.boundary-grid,
.result-grid,
.review-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.review-grid article {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  border-left-width: 4px;
}

.review-grid article > div {
  display: grid;
  gap: 4px;
}

.review-ok {
  border-left-color: #2fac66 !important;
}

.review-warn {
  border-left-color: var(--soc-warm) !important;
}

.review-risk {
  border-left-color: #d84c58 !important;
}

.review-blocker {
  margin-top: 12px;
  border-color: rgba(216, 76, 88, 0.42);
  background: rgba(255, 241, 241, 0.72);
}

.review-blocker span {
  color: #9f2f3b;
  font-size: 12px;
}

.review-actions {
  justify-content: flex-start;
  margin-top: 14px;
}

.result-grid strong {
  color: var(--soc-text);
  font-size: 28px;
}

.report-form {
  max-width: 920px;
}

.result-errors {
  margin-top: 12px;
}

.report-history {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.history-title,
.history-pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.history-title span,
.history-row span,
.history-row em {
  color: var(--soc-text-muted);
  font-size: 12px;
  font-style: normal;
}

.history-pagination {
  justify-content: flex-end;
}

.history-pagination > button:not(.el-button) {
  min-width: 30px;
  height: 26px;
  border: 1px solid rgba(179, 173, 163, 0.42);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.74);
  color: var(--soc-text-muted);
  cursor: pointer;
}

.history-pagination > button:not(.el-button).active,
.history-pagination > button:not(.el-button):hover {
  border-color: rgba(212, 147, 74, 0.6);
  color: var(--soc-warm-strong);
  background: rgba(255, 248, 238, 0.9);
}

.backend-actions {
  justify-content: flex-start;
  margin-top: 14px;
}

@media (max-width: 1020px) {
  .client-module-hero,
  .module-shell,
  .boundary-grid,
  .result-grid,
  .review-grid,
  .review-grid article {
    grid-template-columns: 1fr;
  }

  .hero-actions {
    justify-content: flex-start;
  }
}
</style>
