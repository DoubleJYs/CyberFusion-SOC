<template>
  <div class="client-module-page">
    <section class="client-module-hero">
      <div>
        <span class="soc-page-kicker">MY TASKS</span>
        <h1>我的待办</h1>
        <p>这个页面帮你处理当前电脑需要确认的问题，并把处理结果同步给安全团队。</p>
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

    <section v-if="dataNotices.length" class="client-soft-notice">
      <div>
        <span>已自动适配当前电脑</span>
        <strong>部分辅助数据暂时不可用，页面已切换为当前电脑上下文继续展示。</strong>
        <p>{{ dataNotices.join('；') }}</p>
      </div>
      <div class="recover-actions">
        <el-button size="small" :loading="loading" @click="loadData">重新加载</el-button>
        <el-button size="small" text @click="showDiagnostics = !showDiagnostics">查看诊断</el-button>
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
          v-for="module in operationModules"
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
        <section v-if="activeModule === 'repair'" class="soc-panel module-panel">
          <div class="panel-title">
            <div>
              <strong>风险修复</strong>
              <span>把安全提醒、漏洞和工单任务转成普通员工可执行的说明项</span>
            </div>
            <div class="panel-actions">
              <el-tag effect="plain">{{ repairRecommendations.length }} 条</el-tag>
              <el-button size="small" :loading="repairLoading" @click="loadRepairRecommendations">刷新建议</el-button>
            </div>
          </div>
          <el-alert
            v-if="repairLoadIssue"
            class="module-inline-alert"
            title="修复建议暂时无法加载"
            :description="repairLoadIssue"
            type="warning"
            show-icon
            :closable="false"
          />

          <div v-if="repairRecommendations.length" class="repair-list">
            <article v-for="item in repairRecommendations" :key="item.id" class="repair-card">
              <div class="repair-card-main">
                <div class="repair-title-row">
                  <strong>{{ item.riskTitle }}</strong>
                  <SeverityBadge :severity="item.severity" />
                </div>
                <p>{{ item.impact }}</p>
                <div class="repair-meta">
                  <el-tag effect="plain">{{ relatedLabel(item) }}</el-tag>
                  <el-tag :type="repairStatusType(item.status)" effect="plain">{{ repairStatusLabel(item.status) }}</el-tag>
                  <span>{{ item.assetName || selectedAsset?.hostname || '当前电脑' }} / {{ item.assetIp || selectedIp || '-' }}</span>
                </div>
              </div>
              <div class="repair-action-box">
                <span>推荐动作</span>
                <p>{{ item.recommendedAction }}</p>
                <div class="repair-actions">
                  <el-button size="small" @click="openRepairDetail(item)">查看详情</el-button>
                  <el-button size="small" @click="openNoteDialog(item)">提交说明</el-button>
                  <el-button type="primary" size="small" :disabled="['confirmed', 'completed'].includes(item.status)" @click="confirmRepair(item)">
                    确认已处理
                  </el-button>
                </div>
              </div>
            </article>
          </div>
          <el-empty v-else description="当前没有需要处理的风险修复建议" :image-size="88" />
        </section>

        <section v-else-if="activeModule === 'tasks'" class="soc-panel module-panel">
          <div class="panel-title">
            <div>
              <strong>我的处置任务</strong>
              <span>安全团队分配给你的确认事项，只展示当前账号可处理的任务</span>
            </div>
            <el-tag effect="plain">{{ employeeTasks.length }} 项</el-tag>
          </div>
          <el-alert
            v-if="taskLoadIssue"
            class="module-inline-alert"
            title="处置任务暂时无法加载"
            :description="taskLoadIssue"
            type="warning"
            show-icon
            :closable="false"
          />
          <div v-if="employeeTasks.length" class="employee-task-list">
            <article v-for="task in employeeTasks" :key="task.id" class="employee-task-card">
              <div>
                <strong>{{ employeeTaskTitle(task) }}</strong>
                <span>{{ employeeTaskInstruction(task) }}</span>
                <em v-if="task.expectedEvidence">需要提供：{{ task.expectedEvidence }}</em>
              </div>
              <div class="employee-task-actions">
                <el-tag :type="taskStatusType(task.status)" effect="plain">{{ taskStatusLabel(task.status) }}</el-tag>
                <el-button size="small" @click="openTaskDetail(task)">查看详情</el-button>
                <el-button size="small" :disabled="['confirmed', 'completed'].includes(task.status)" @click="openTaskEvidenceDialog(task)">
                  提交说明
                </el-button>
                <el-button type="primary" size="small" :disabled="['confirmed', 'completed'].includes(task.status)" @click="confirmTask(task)">
                  确认已处理
                </el-button>
              </div>
            </article>
          </div>
          <el-empty v-else description="当前没有需要你处理的任务" :image-size="88" />
        </section>

        <section v-else-if="activeModule === 'alerts'" class="soc-panel module-panel">
          <div class="panel-title">
            <div>
              <strong>待处理告警</strong>
              <span>只展示当前电脑需要处理的提醒</span>
            </div>
            <div class="panel-actions">
              <el-tag effect="plain">{{ profile.alerts.length }} 条</el-tag>
              <el-tag v-if="profile.alerts.length > alertPageSize" effect="plain">第 {{ alertPage }} / {{ alertPageCount }} 页</el-tag>
            </div>
          </div>
          <div v-if="profile.alerts.length" class="alert-stack">
            <button
              v-for="alert in pagedAlerts"
              :key="alert.id"
              type="button"
              :class="{ active: currentAlert?.id === alert.id }"
              @click="currentAlert = alert"
            >
              <SeverityBadge :severity="alert.severity" />
              <strong>{{ alert.ruleDescription }}</strong>
              <span>{{ alert.alertUid }} · {{ formatTime(alert.eventTime) }}</span>
              <em>{{ alert.status }} / {{ alert.sourceIp || '-' }}</em>
            </button>
            <div v-if="profile.alerts.length > alertPageSize" class="queue-pagination">
              <el-button size="small" :disabled="alertPage <= 1" @click="goAlertPage(alertPage - 1)">上一页</el-button>
              <button
                v-for="page in alertVisiblePages"
                :key="page"
                type="button"
                :class="{ active: page === alertPage }"
                @click="goAlertPage(page)"
              >
                {{ page }}
              </button>
              <el-button size="small" :disabled="alertPage >= alertPageCount" @click="goAlertPage(alertPage + 1)">下一页</el-button>
            </div>
          </div>
          <el-empty v-else description="当前电脑暂无待处理告警" :image-size="88" />
        </section>

        <section v-else-if="activeModule === 'action'" class="soc-panel module-panel">
          <div class="panel-title">
            <div>
              <strong>处理动作</strong>
              <span>对选中的当前电脑提醒提交受控动作和说明</span>
            </div>
            <el-tag type="warning" effect="plain">可审计</el-tag>
          </div>
          <div v-if="currentAlert" class="selected-alert">
            <div class="soc-drawer-grid">
              <span>提醒 ID</span><strong>{{ currentAlert.alertUid }}</strong>
              <span>等级</span><strong><SeverityBadge :severity="currentAlert.severity" /></strong>
              <span>资产</span><strong>{{ currentAlert.assetName }}（{{ currentAlert.assetIp }}）</strong>
              <span>规则</span><strong>{{ currentAlert.ruleId }} / {{ currentAlert.ruleDescription }}</strong>
              <span>来源 IP</span><strong>{{ currentAlert.sourceIp || '-' }}</strong>
              <span>状态</span><strong><StatusBadge :status="currentAlert.noiseStatus || currentAlert.status" /></strong>
            </div>
            <el-input v-model="remark" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="填写处置说明" />
            <div class="action-option-grid">
              <button
                v-for="option in actionOptions"
                :key="option.value"
                type="button"
                :class="{ active: selectedAction === option.value }"
                @click="prepareAction(option.value)"
              >
                <strong>{{ option.label }}</strong>
                <span>{{ option.desc }}</span>
                <el-tag :type="option.tagType" effect="plain">{{ option.level }}</el-tag>
              </button>
            </div>
            <div class="action-confirm-panel">
              <div class="panel-title compact">
                <div>
                  <strong>处置前确认</strong>
                  <span>只提交当前电脑的处理动作，避免误点关闭或转错工单</span>
                </div>
                <el-tag :type="actionReady ? 'success' : 'warning'" effect="plain">{{ actionReady ? '可提交' : '待确认' }}</el-tag>
              </div>
              <div class="guard-grid">
                <article v-for="item in actionGuardRows" :key="item.label" :class="{ passed: item.passed }">
                  <strong>{{ item.label }}</strong>
                  <span>{{ item.desc }}</span>
                  <el-tag :type="item.passed ? 'success' : 'warning'" effect="plain">{{ item.passed ? '通过' : '待处理' }}</el-tag>
                </article>
              </div>
              <el-checkbox v-model="scopeConfirmed">
                我确认本次操作只针对当前电脑 {{ selectedAsset?.hostname || '-' }} / {{ selectedAsset?.ip || '-' }}，处理说明可进入审计记录。
              </el-checkbox>
              <div class="drawer-actions">
                <el-button :disabled="!selectedAction" @click="resetActionDraft">取消选择</el-button>
                <el-button type="primary" :disabled="!actionReady" :loading="submitting" @click="runSelectedAction">提交处理结果</el-button>
              </div>
            </div>
          </div>
          <el-empty v-else description="请先在待处理告警中选择一条记录" :image-size="88" />
        </section>

        <section v-else-if="activeModule === 'history'" class="soc-panel module-panel">
          <div class="panel-title">
            <div>
              <strong>处理记录</strong>
              <span>只保留当前电脑最近处理动作，完整审计由安全团队查看</span>
            </div>
            <div class="panel-actions">
              <el-tag effect="plain">{{ operationHistory.length }} 条</el-tag>
              <el-tag v-if="operationHistory.length > historyPageSize" effect="plain">第 {{ historyPage }} / {{ historyPageCount }} 页</el-tag>
            </div>
          </div>
          <div v-if="pagedOperationHistory.length" class="operation-history">
            <article v-for="item in pagedOperationHistory" :key="item.id" class="history-card">
              <div>
                <strong>{{ actionLabel(item.action) }}</strong>
                <span>{{ item.ruleDescription }}</span>
                <em>{{ item.alertUid }} · {{ formatTime(item.createdAt) }}</em>
              </div>
              <div>
                <el-tag effect="plain">{{ item.status }}</el-tag>
                <em v-if="item.backendRef">{{ item.backendRef }}</em>
                <span>{{ item.remark }}</span>
              </div>
            </article>
            <div v-if="operationHistory.length > historyPageSize" class="queue-pagination">
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
          <el-empty v-else description="当前电脑暂无处理记录" :image-size="88" />
        </section>

        <section v-else class="soc-panel module-panel">
          <div class="panel-title">
            <div>
              <strong>安全团队入口</strong>
              <span>只提供带当前电脑上下文的安全团队入口</span>
            </div>
            <el-tag effect="plain">同一资产上下文</el-tag>
          </div>
          <div class="support-grid">
            <button type="button" @click="copySummary">
              <strong>复制电脑安全摘要</strong>
              <span>给 IT 支持或安全分析工单使用。</span>
            </button>
            <button type="button" @click="openBackend('/soc/alerts')">
              <strong>待处理告警</strong>
              <span>查看当前电脑提醒和处理时间线。</span>
            </button>
            <button type="button" @click="openBackend('/soc/tickets')">
              <strong>处置工单</strong>
              <span>跟踪转工单后的 SLA 和闭环状态。</span>
            </button>
            <button type="button" @click="openBackend('/soc/assets')">
              <strong>资产详情</strong>
              <span>查看当前电脑资产记录。</span>
            </button>
          </div>
        </section>
      </main>
    </section>

    <el-dialog v-model="noteDialogVisible" title="提交修复说明" width="520px">
      <div v-if="currentRepair" class="repair-dialog-body">
        <strong>{{ currentRepair.riskTitle }}</strong>
        <p>{{ currentRepair.recommendedAction }}</p>
        <el-input v-model="repairNote" type="textarea" :rows="5" maxlength="500" show-word-limit placeholder="请说明你已经做了什么、是否需要管理员继续处理，或补充业务背景。" />
      </div>
      <template #footer>
        <el-button @click="noteDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="repairSubmitting" @click="submitRepairNote">提交说明</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="repairDrawerVisible" title="修复建议详情" size="420px">
      <div v-if="currentRepair" class="repair-detail-drawer">
        <div class="soc-drawer-grid">
          <span>风险标题</span><strong>{{ currentRepair.riskTitle }}</strong>
          <span>严重级别</span><strong><SeverityBadge :severity="currentRepair.severity" /></strong>
          <span>影响说明</span><strong>{{ currentRepair.impact }}</strong>
          <span>推荐动作</span><strong>{{ currentRepair.recommendedAction }}</strong>
          <span>关联类型</span><strong>{{ relatedLabel(currentRepair) }}</strong>
          <span>当前状态</span><strong>{{ repairStatusLabel(currentRepair.status) }}</strong>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="taskEvidenceDialogVisible" title="提交待办说明" width="520px">
      <div v-if="currentTask" class="repair-dialog-body">
        <strong>{{ employeeTaskTitle(currentTask) }}</strong>
        <p>{{ employeeTaskInstruction(currentTask) }}</p>
        <el-input v-model="taskEvidenceForm.evidenceText" type="textarea" :rows="5" maxlength="500" show-word-limit placeholder="请补充这次处理说明，例如：已完成本机检查、确认电脑名称、网络连接检查结果正常。" />
        <el-input v-model="taskEvidenceForm.remark" maxlength="200" show-word-limit placeholder="备注：提交给安全团队" />
      </div>
      <template #footer>
        <el-button @click="taskEvidenceDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="taskSubmitting" @click="submitTaskEvidence">提交说明</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="taskDetailVisible" title="待办详情" size="420px">
      <div v-if="currentTask" class="repair-detail-drawer">
        <div class="soc-drawer-grid">
          <span>待办标题</span><strong>{{ employeeTaskTitle(currentTask) }}</strong>
          <span>当前状态</span><strong>{{ taskStatusLabel(currentTask.status) }}</strong>
          <span>安全团队请求</span><strong>{{ employeeTaskInstruction(currentTask) }}</strong>
          <span>需要提供</span><strong>{{ currentTask.expectedEvidence || '请补充这次处理说明。' }}</strong>
          <span>关联工单</span><strong>#{{ currentTask.ticketId }}</strong>
          <span>已提交内容</span><strong>{{ currentTask.evidenceText || '暂无' }}</strong>
        </div>
        <div class="drawer-actions">
          <el-button @click="openTaskEvidenceDialog(currentTask)">提交说明</el-button>
          <el-button type="primary" :disabled="['confirmed', 'completed'].includes(currentTask.status)" @click="confirmTask(currentTask)">确认已处理</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import SeverityBadge from '@/components/security/SeverityBadge.vue'
import StatusBadge from '@/components/security/StatusBadge.vue'
import {
  alertAction,
  confirmSecurityKeeperRecommendation,
  confirmClientTask,
  getClientTask,
  listClientTasks,
  listSecurityKeeperRecommendations,
  submitSecurityKeeperRecommendationNote,
  submitClientTaskEvidence,
  type AlertItem,
  type AssetItem,
  type ClientDeviceProfile,
  type SecurityKeeperRepairRecommendation,
  type TicketTaskItem,
  type TicketItem,
} from '@/api/soc'
import { useAuthStore } from '@/stores/auth'
import {
  buildEmptyClientProfile,
  buildClientDeviceRouteQuery,
  chooseClientAsset,
  emptyClientMetrics,
  loadClientAssets,
  loadClientProfile,
} from '@/composables/useClientDeviceContext'

type OperationModule = 'repair' | 'tasks' | 'alerts' | 'action' | 'history' | 'backend'
type OperationAction = 'acknowledge' | 'false-positive' | 'close' | 'ticket'
type OperationHistoryItem = {
  id: string
  assetIp: string
  assetName: string
  alertId: number
  alertUid: string
  ruleDescription: string
  action: OperationAction
  remark: string
  status: string
  backendRef?: string
  createdAt: string
}

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const loading = ref(false)
const repairLoading = ref(false)
const submitting = ref(false)
const repairSubmitting = ref(false)
const taskSubmitting = ref(false)
const error = ref('')
const errorDiagnostic = ref('')
const dataNotices = ref<string[]>([])
const repairLoadIssue = ref('')
const taskLoadIssue = ref('')
const showDiagnostics = ref(false)
const selectedIp = ref('')
const assets = ref<AssetItem[]>([])
const activeModule = ref<OperationModule>(route.query.tab === 'repair' ? 'repair' : 'tasks')
const currentAlert = ref<AlertItem>()
const currentRepair = ref<SecurityKeeperRepairRecommendation>()
const currentTask = ref<TicketTaskItem>()
const remark = ref('员工端确认当前电脑告警处置')
const repairNote = ref('')
const taskEvidenceForm = reactive({ evidenceText: '', remark: '员工端提交待办说明' })
const selectedAction = ref<OperationAction>()
const scopeConfirmed = ref(false)
const noteDialogVisible = ref(false)
const repairDrawerVisible = ref(false)
const taskEvidenceDialogVisible = ref(false)
const taskDetailVisible = ref(false)
const alertPage = ref(1)
const alertPageSize = 6
const historyPage = ref(1)
const historyPageSize = 5
const operationHistory = ref<OperationHistoryItem[]>([])
const employeeTasks = ref<TicketTaskItem[]>([])
const repairRecommendations = ref<SecurityKeeperRepairRecommendation[]>([])
const profile = reactive<ClientDeviceProfile>({
  asset: {} as AssetItem,
  metrics: emptyClientMetrics,
  alerts: [],
  vulnerabilities: [],
  baselines: [],
  fileIntegrityEvents: [],
  externalEvents: [],
  timeline: [],
})

const selectedAsset = computed(() => assets.value.find((asset) => asset.ip === selectedIp.value))
const currentUsername = computed(() => authStore.userInfo?.nickname || authStore.userInfo?.username || '当前办公用户')
const backendQuery = computed(() => {
  const asset = selectedAsset.value
  return asset ? { assetIp: asset.ip, keyword: asset.hostname, source: 'client-operations' } : { source: 'client-operations' }
})
const clientDeviceQuery = computed(() => buildClientDeviceRouteQuery({
  ip: selectedAsset.value?.ip || selectedIp.value,
  host: selectedAsset.value?.hostname,
  os: selectedAsset.value?.osType,
}))
const alertPageCount = computed(() => Math.max(1, Math.ceil(profile.alerts.length / alertPageSize)))
const alertVisiblePages = computed(() => Array.from({ length: alertPageCount.value }, (_, index) => index + 1))
const pagedAlerts = computed(() => {
  const start = (alertPage.value - 1) * alertPageSize
  return profile.alerts.slice(start, start + alertPageSize)
})
const historyPageCount = computed(() => Math.max(1, Math.ceil(operationHistory.value.length / historyPageSize)))
const historyVisiblePages = computed(() => Array.from({ length: historyPageCount.value }, (_, index) => index + 1))
const pagedOperationHistory = computed(() => {
  const start = (historyPage.value - 1) * historyPageSize
  return operationHistory.value.slice(start, start + historyPageSize)
})
const selectedAlertBelongsCurrent = computed(() => {
  const asset = selectedAsset.value
  const alert = currentAlert.value
  if (!asset || !alert) return false
  return alert.assetIp === asset.ip || alert.assetName === asset.hostname
})
const selectedActionOption = computed(() => actionOptions.find((item) => item.value === selectedAction.value))
const actionReady = computed(() => Boolean(
  currentAlert.value
    && selectedAction.value
    && selectedAlertBelongsCurrent.value
    && scopeConfirmed.value
    && remark.value.trim().length >= 8,
))
const actionGuardRows = computed(() => [
  {
    label: '当前电脑上下文',
    desc: selectedAlertBelongsCurrent.value
      ? `${selectedAsset.value?.hostname} / ${selectedAsset.value?.ip}`
      : '告警资产与当前电脑不一致，禁止员工端处置。',
    passed: selectedAlertBelongsCurrent.value,
  },
  {
    label: '处置动作',
    desc: selectedActionOption.value ? selectedActionOption.value.label : '请先选择确认、转工单、误报或关闭。',
    passed: Boolean(selectedAction.value),
  },
  {
    label: '处置说明',
    desc: remark.value.trim().length >= 8 ? '说明将进入审计记录和工单时间线。' : '至少填写 8 个字符，说明判断依据。',
    passed: remark.value.trim().length >= 8,
  },
  {
    label: '员工确认',
    desc: scopeConfirmed.value ? '已确认本次操作只针对当前电脑。' : '需要勾选确认范围，避免误操作其他资产。',
    passed: scopeConfirmed.value,
  },
])

const operationModules = [
  { name: 'repair' as const, label: '风险修复', desc: '体检后的下一步' },
  { name: 'tasks' as const, label: '我的处置任务', desc: '需要你确认的事项' },
  { name: 'alerts' as const, label: '待处理告警', desc: '当前电脑告警列表' },
  { name: 'action' as const, label: '处理动作', desc: '确认、转工单、误报、关闭' },
  { name: 'history' as const, label: '处理记录', desc: '当前电脑处理动作' },
  { name: 'backend' as const, label: '安全团队入口', desc: '资产、提醒、工单入口' },
]
const actionOptions: Array<{ value: OperationAction; label: string; desc: string; level: string; tagType: 'success' | 'warning' | 'danger' | 'info' }> = [
  { value: 'acknowledge', label: '确认告警', desc: '确认已知悉并进入人工观察。', level: '低风险', tagType: 'success' },
  { value: 'ticket', label: '转工单', desc: '交给安全团队或 IT 支持处理。', level: '闭环', tagType: 'warning' },
  { value: 'false-positive', label: '标记误报', desc: '需要说明业务原因，安全团队可确认。', level: '需确认', tagType: 'info' },
  { value: 'close', label: '关闭告警', desc: '高影响动作，必须确认当前电脑上下文。', level: '高影响', tagType: 'danger' },
]

onMounted(() => {
  void loadData()
})

async function loadData() {
  loading.value = true
  error.value = ''
  errorDiagnostic.value = ''
  dataNotices.value = []
  taskLoadIssue.value = ''
  repairLoadIssue.value = ''
  try {
    const loaded = await loadClientAssets()
    assets.value = loaded.records
    selectedIp.value = chooseClientAsset(assets.value, {
      routeIp: typeof route.query.ip === 'string' ? route.query.ip : '',
      routeHost: typeof route.query.host === 'string' ? route.query.host : '',
      currentNames: [authStore.userInfo?.nickname, authStore.userInfo?.username],
      allowDemoFallback: true,
      allowFirstFallback: true,
      preferAcceptedLocal: true,
    })?.ip || ''
    ensureRouteAssetContext()
    loadOperationHistory()
    await loadEmployeeTasks()
    await loadProfile()
    await loadRepairRecommendations()
  } catch (err) {
    error.value = '我的待办加载失败，请检查登录状态、后端服务或接口权限。'
    errorDiagnostic.value = err instanceof Error ? err.message : String(err)
  } finally {
    loading.value = false
  }
}

async function loadEmployeeTasks() {
  try {
    const res = await listClientTasks()
    employeeTasks.value = res.data.data
    taskLoadIssue.value = ''
    currentTask.value = currentTask.value
      ? employeeTasks.value.find((task) => task.id === currentTask.value?.id) || currentTask.value
      : highestPriorityTask.value
  } catch (err) {
    employeeTasks.value = []
    taskLoadIssue.value = '后台待办接口暂时不可用，已保留当前电脑页面和其他入口。'
    addDataNotice('我的处置任务接口暂时不可用')
    appendDiagnostic('client tasks', err)
  }
}

const highestPriorityTask = computed(() => employeeTasks.value
  .filter((task) => !['confirmed', 'completed', 'skipped'].includes(task.status))
  .sort((left, right) => taskPriority(left) - taskPriority(right))[0])

async function loadRepairRecommendations() {
  const assetIp = selectedAsset.value?.ip || selectedIp.value
  if (!assetIp) {
    repairRecommendations.value = []
    return
  }
  repairLoading.value = true
  try {
    const res = await listSecurityKeeperRecommendations(assetIp)
    repairRecommendations.value = res.data.data
    repairLoadIssue.value = ''
  } catch (err) {
    repairRecommendations.value = []
    repairLoadIssue.value = '风险修复建议接口暂时不可用，已自动切换为“我的处置任务 / 待处理告警”继续展示。'
    addDataNotice('风险修复建议接口暂时不可用')
    appendDiagnostic('repair recommendations', err)
  } finally {
    repairLoading.value = false
  }
}

function ensureRouteAssetContext() {
  if (selectedIp.value) return
  const routeIp = typeof route.query.ip === 'string' ? route.query.ip : ''
  const routeHost = typeof route.query.host === 'string' ? route.query.host : ''
  if (!routeIp && !routeHost) return
  const fallbackAsset: AssetItem = {
    id: 0,
    hostname: routeHost || routeIp || '当前电脑',
    ip: routeIp || '',
    osType: typeof route.query.os === 'string' ? route.query.os : '待识别',
    sourceType: 'route-context',
    riskLevel: 'unknown',
    deptName: '当前电脑',
    ownerName: currentUsername.value,
    openAlertCount: 0,
    lastSeenAt: new Date().toISOString(),
  }
  assets.value = [fallbackAsset, ...assets.value]
  selectedIp.value = fallbackAsset.ip
  addDataNotice('未在资产清单中匹配到当前电脑，已使用地址栏中的电脑信息继续展示')
}

function useOfflineDemoData() {
  const asset: AssetItem = {
    id: 0,
    hostname: 'prod-app-01',
    ip: '10.20.1.15',
    osType: 'Linux',
    sourceType: 'offline-demo',
    riskLevel: 'high',
    deptName: '演示部门',
    ownerName: currentUsername.value,
    openAlertCount: 1,
    lastSeenAt: new Date().toISOString(),
  }
  assets.value = [asset]
  selectedIp.value = asset.ip
  Object.assign(profile, {
    ...buildEmptyClientProfile(asset),
    alerts: [{
      id: 0,
      alertUid: 'OFFLINE-ALERT-001',
      sourceType: 'offline-demo',
      level: 4,
      severity: 'high',
      ruleId: 'CLIENT-DEMO-001',
      ruleDescription: '当前电脑存在需要确认的安全提醒',
      assetName: asset.hostname,
      assetIp: asset.ip,
      sourceIp: '10.20.1.40',
      status: 'open',
      tactic: 'demo',
      rawRef: 'offline-demo',
      eventTime: new Date().toISOString(),
    }],
  })
  currentAlert.value = profile.alerts[0]
  repairRecommendations.value = [{
    id: 'alert-0',
    riskTitle: '当前电脑存在需要确认的安全提醒',
    severity: 'high',
    impact: '安全团队发现当前电脑有需要确认的提醒，可能与账号、网页访问或主机安全状态有关。',
    recommendedAction: '请先不要自行修改系统设置。若你知道业务背景，可以提交说明；如已按管理员建议处理，可以确认已处理。',
    relatedType: 'alert',
    relatedAlertId: 0,
    assetIp: asset.ip,
    assetName: asset.hostname,
    status: 'open',
  }]
  error.value = ''
  errorDiagnostic.value = ''
  showDiagnostics.value = false
  ElMessage.warning('已切换为离线演示数据')
}

async function loadProfile() {
  const asset = selectedAsset.value
  if (!asset) return
  try {
    Object.assign(profile, await loadClientProfile(asset))
  } catch (err) {
    Object.assign(profile, buildEmptyClientProfile(asset))
    addDataNotice('当前电脑安全画像接口暂时不可用')
    appendDiagnostic('client profile', err)
  }
  if (alertPage.value > alertPageCount.value) {
    alertPage.value = 1
  }
  currentAlert.value = profile.alerts.find((item) => item.id === currentAlert.value?.id) || pagedAlerts.value[0] || profile.alerts[0]
  resetActionDraft()
}

function addDataNotice(message: string) {
  if (!dataNotices.value.includes(message)) {
    dataNotices.value = [...dataNotices.value, message]
  }
}

function appendDiagnostic(scope: string, err: unknown) {
  const message = err instanceof Error ? err.message : String(err)
  const line = `[${scope}] ${message}`
  errorDiagnostic.value = errorDiagnostic.value ? `${errorDiagnostic.value}\n${line}` : line
}

function openRepairDetail(item: SecurityKeeperRepairRecommendation) {
  currentRepair.value = item
  repairDrawerVisible.value = true
}

function openNoteDialog(item: SecurityKeeperRepairRecommendation) {
  currentRepair.value = item
  repairNote.value = ''
  noteDialogVisible.value = true
}

async function submitRepairNote() {
  if (!currentRepair.value) return
  const note = repairNote.value.trim()
  if (note.length < 4) {
    ElMessage.warning('请至少填写 4 个字符的说明')
    return
  }
  repairSubmitting.value = true
  try {
    const res = await submitSecurityKeeperRecommendationNote(currentRepair.value.id, { note })
    replaceRepairRecommendation(res.data.data)
    noteDialogVisible.value = false
    ElMessage.success('说明已提交给安全团队')
    await loadEmployeeTasks()
  } finally {
    repairSubmitting.value = false
  }
}

async function confirmRepair(item: SecurityKeeperRepairRecommendation) {
  repairSubmitting.value = true
  try {
    const res = await confirmSecurityKeeperRecommendation(item.id, { note: '员工确认已按建议处理' })
    replaceRepairRecommendation(res.data.data)
    ElMessage.success('已确认处理状态')
    await loadEmployeeTasks()
  } finally {
    repairSubmitting.value = false
  }
}

function replaceRepairRecommendation(item: SecurityKeeperRepairRecommendation) {
  repairRecommendations.value = repairRecommendations.value.map((current) => current.id === item.id ? item : current)
  currentRepair.value = item
}

function prepareAction(action: OperationAction) {
  selectedAction.value = action
  scopeConfirmed.value = false
}

function resetActionDraft() {
  selectedAction.value = undefined
  scopeConfirmed.value = false
}

async function runSelectedAction() {
  if (!selectedAction.value) return
  await runAction(selectedAction.value)
}

async function confirmTask(task: TicketTaskItem) {
  await confirmClientTask(task.id, { remark: '员工端确认处置任务完成', evidenceText: task.evidenceText || '员工确认已按安全团队要求处理。' })
  ElMessage.success('待办已确认')
  await loadEmployeeTasks()
  await loadRepairRecommendations()
  taskDetailVisible.value = false
}

async function openTaskDetail(task: TicketTaskItem) {
  try {
    const res = await getClientTask(task.id)
    currentTask.value = res.data.data
  } catch {
    currentTask.value = task
  }
  taskDetailVisible.value = true
}

function openTaskEvidenceDialog(task: TicketTaskItem) {
  currentTask.value = task
  taskEvidenceForm.evidenceText = task.evidenceText || suggestedTaskEvidence(task)
  taskEvidenceForm.remark = '员工端提交待办说明'
  taskEvidenceDialogVisible.value = true
}

async function submitTaskEvidence() {
  const task = currentTask.value
  if (!task) return
  const evidenceText = taskEvidenceForm.evidenceText.trim()
  if (evidenceText.length < 4) {
    ElMessage.warning('请补充这次处理说明')
    return
  }
  taskSubmitting.value = true
  try {
    const res = await submitClientTaskEvidence(task.id, {
      evidenceText,
      remark: taskEvidenceForm.remark.trim() || '员工端提交待办说明',
    })
    replaceEmployeeTask(res.data.data)
    currentTask.value = res.data.data
    taskEvidenceDialogVisible.value = false
    ElMessage.success('说明已提交给安全团队')
    await loadRepairRecommendations()
  } finally {
    taskSubmitting.value = false
  }
}

function replaceEmployeeTask(task: TicketTaskItem) {
  employeeTasks.value = employeeTasks.value.map((item) => item.id === task.id ? task : item)
}

async function runAction(action: OperationAction) {
  if (!currentAlert.value) return
  if (!actionReady.value) {
    ElMessage.warning('请先完成处置前确认')
    return
  }
  const actionAlert = currentAlert.value
  const actionRemark = remark.value.trim() || '员工端提交处理动作'
  submitting.value = true
  try {
    const response = await alertAction(actionAlert.id, action, actionRemark)
    pushOperationHistory(actionAlert, action, actionRemark, backendReceipt(action, response.data.data))
    ElMessage.success('处理结果已提交')
    await loadProfile()
    resetActionDraft()
    activeModule.value = 'history'
  } finally {
    submitting.value = false
  }
}

function openBackend(path: string) {
  void router.push({ path, query: backendQuery.value })
}

function goAlertPage(page: number) {
  alertPage.value = Math.min(Math.max(1, page), alertPageCount.value)
  currentAlert.value = pagedAlerts.value[0] || profile.alerts[0]
}

function goHistoryPage(page: number) {
  historyPage.value = Math.min(Math.max(1, page), historyPageCount.value)
}

function operationHistoryStorageKey() {
  return `cyberfusion_client_operation_history_${selectedIp.value || 'unknown'}`
}

function loadOperationHistory() {
  try {
    const parsed = JSON.parse(localStorage.getItem(operationHistoryStorageKey()) || '[]') as OperationHistoryItem[]
    operationHistory.value = Array.isArray(parsed) ? parsed.slice(0, 20) : []
    historyPage.value = 1
  } catch {
    operationHistory.value = []
  }
}

function persistOperationHistory() {
  localStorage.setItem(operationHistoryStorageKey(), JSON.stringify(operationHistory.value.slice(0, 20)))
}

function pushOperationHistory(alert: AlertItem, action: OperationHistoryItem['action'], actionRemark: string, backendRef?: string) {
  const item: OperationHistoryItem = {
    id: `operation-${Date.now()}`,
    assetIp: alert.assetIp,
    assetName: alert.assetName,
    alertId: alert.id,
    alertUid: alert.alertUid,
    ruleDescription: alert.ruleDescription,
    action,
    remark: actionRemark,
    status: action === 'ticket' ? '已转工单' : '已提交',
    backendRef,
    createdAt: new Date().toISOString(),
  }
  operationHistory.value = [item, ...operationHistory.value].slice(0, 20)
  historyPage.value = 1
  persistOperationHistory()
}

function backendReceipt(action: OperationAction, data: AlertItem | TicketItem) {
  if (action === 'ticket' && 'ticketNo' in data) {
    return `工单 ${data.ticketNo}`
  }
  if ('alertUid' in data) {
    return `提醒 ${data.alertUid} / ${data.status}`
  }
  return '已接收'
}

function actionLabel(action: OperationHistoryItem['action']) {
  const labels: Record<OperationHistoryItem['action'], string> = {
    acknowledge: '确认告警',
    'false-positive': '标记误报',
    close: '关闭告警',
    ticket: '转工单',
  }
  return labels[action]
}

function taskStatusLabel(status: string) {
  const labels: Record<string, string> = {
    pending: '待处理',
    in_progress: '处理中',
    submitted: '已提交',
    confirmed: '已确认',
    completed: '已完成',
    skipped: '已跳过',
  }
  return labels[status] || status
}

function taskStatusType(status: string) {
  if (['confirmed', 'completed'].includes(status)) return 'success'
  if (status === 'skipped') return 'info'
  if (['in_progress', 'submitted'].includes(status)) return 'warning'
  return ''
}

function taskPriority(task: TicketTaskItem) {
  const statusWeight: Record<string, number> = {
    pending: 0,
    in_progress: 1,
    submitted: 2,
    confirmed: 8,
    completed: 8,
    skipped: 9,
  }
  return (statusWeight[task.status] ?? 5) * 1000 + (task.sortOrder || 100)
}

function employeeTaskTitle(task: TicketTaskItem) {
  const text = `${task.taskName || ''} ${task.instruction || ''} ${task.expectedEvidence || ''}`
  if (text.includes('本机') || text.includes('检查')) return '安全团队请求你提交一次本机检查记录'
  if (text.includes('网络') || text.includes('电脑名称') || text.includes('主机')) return '请确认电脑名称和网络连接检查结果'
  return task.taskName || '安全团队创建了处理任务'
}

function employeeTaskInstruction(task: TicketTaskItem) {
  if (task.instruction) return task.instruction
  if (task.expectedEvidence) return `请补充这次处理说明：${task.expectedEvidence}`
  return '请补充这次处理说明。'
}

function suggestedTaskEvidence(task: TicketTaskItem) {
  const text = `${task.taskName || ''} ${task.instruction || ''} ${task.expectedEvidence || ''}`
  if (text.includes('本机') || text.includes('检查')) return '已完成本机检查，并生成本机检查记录。'
  if (text.includes('网络') || text.includes('电脑名称') || text.includes('主机')) return '已核对电脑名称和网络连接检查结果。'
  return '请补充这次处理说明。'
}

function relatedLabel(item: SecurityKeeperRepairRecommendation) {
  if (item.relatedType === 'task') return item.relatedTicketId ? `工单任务 #${item.relatedTicketId}` : '我的待办'
  if (item.relatedType === 'vulnerability') return item.relatedVulnerabilityId ? `漏洞 #${item.relatedVulnerabilityId}` : '软件风险'
  return item.relatedAlertId ? `安全提醒 #${item.relatedAlertId}` : '安全提醒'
}

function repairStatusLabel(status: string) {
  const labels: Record<string, string> = {
    new: '待查看',
    open: '待处理',
    pending: '待处理',
    in_progress: '处理中',
    submitted: '已提交说明',
    confirmed: '已确认',
    completed: '已完成',
    fixed: '已修复',
    accepted: '已接受',
    acknowledged: '已确认',
    closed: '已关闭',
  }
  return labels[status] || status || '待处理'
}

function repairStatusType(status: string) {
  if (['confirmed', 'completed', 'fixed', 'accepted', 'closed'].includes(status)) return 'success'
  if (['submitted', 'in_progress', 'acknowledged'].includes(status)) return 'warning'
  return ''
}

async function copySummary() {
  const asset = selectedAsset.value
  if (!asset) return
  await navigator.clipboard.writeText([
    `主机：${asset.hostname}`,
    `IP：${asset.ip}`,
    `风险分：${profile.metrics.riskScore}`,
    `未关闭提醒：${profile.alerts.length}`,
    `待修复漏洞：${profile.metrics.openVulnerabilities}`,
    `待确认记录：${profile.metrics.failedBaselines + profile.metrics.pendingFileIntegrity + profile.metrics.pendingExternalEvents}`,
  ].join('\n'))
  ElMessage.success('电脑安全摘要已复制')
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
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

.client-soft-notice {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: start;
  padding: 14px 16px;
  border: 1px solid rgba(212, 147, 74, 0.26);
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(255, 248, 238, 0.88), rgba(255, 255, 255, 0.74));
  box-shadow: var(--soc-shadow-soft), var(--soc-glass-highlight);
}

.client-recoverable-error span,
.client-recoverable-error p,
.client-soft-notice span,
.client-soft-notice p {
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.client-recoverable-error strong,
.client-soft-notice strong {
  color: var(--soc-text);
  font-size: 16px;
}

.client-recoverable-error p,
.client-soft-notice p {
  margin: 0;
}

.client-recoverable-error pre,
.client-soft-notice pre {
  grid-column: 1 / -1;
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

.module-inline-alert {
  margin-bottom: 12px;
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
.alert-stack button span,
.alert-stack button em,
.support-grid span,
.history-card span,
.history-card em {
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.55;
}

.hero-actions,
.drawer-actions {
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
.alert-stack button,
.support-grid button,
.history-card,
.action-option-grid button,
.guard-grid article,
.action-confirm-panel {
  border: 1px solid rgba(179, 173, 163, 0.38);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.62);
}

.device-chip,
.module-aside button,
.alert-stack button,
.support-grid button,
.history-card,
.action-option-grid button,
.guard-grid article,
.action-confirm-panel {
  display: grid;
  gap: 5px;
  padding: 12px;
}

.module-aside button,
.alert-stack button,
.support-grid button,
.action-option-grid button {
  text-align: left;
  cursor: pointer;
}

.module-aside button.active,
.module-aside button:hover,
.alert-stack button.active,
.alert-stack button:hover,
.support-grid button:hover,
.action-option-grid button.active,
.action-option-grid button:hover {
  border-color: rgba(212, 147, 74, 0.56);
  background: rgba(255, 248, 238, 0.84);
  box-shadow: inset 3px 0 0 var(--soc-warm);
}

.device-chip strong,
.module-aside strong,
.panel-title strong,
.alert-stack strong,
.support-grid strong,
.history-card strong,
.action-option-grid strong,
.guard-grid strong {
  color: var(--soc-text);
}

.device-chip em,
.alert-stack button em,
.history-card em {
  font-style: normal;
}

.module-panel {
  min-height: 460px;
  padding: 16px;
  box-shadow: none;
}

.panel-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.panel-title div {
  display: grid;
  gap: 3px;
}

.alert-stack,
.selected-alert,
.support-grid,
.operation-history,
.action-option-grid,
.action-confirm-panel {
  display: grid;
  gap: 10px;
}

.action-option-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.action-option-grid button {
  min-height: 104px;
}

.action-option-grid button span,
.guard-grid span {
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.action-option-grid .el-tag {
  justify-self: start;
}

.action-confirm-panel {
  background: linear-gradient(135deg, rgba(255, 248, 238, 0.72), rgba(255, 255, 255, 0.68));
}

.panel-title.compact {
  margin-bottom: 4px;
}

.guard-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.guard-grid article {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  border-left: 4px solid rgba(212, 147, 74, 0.56);
}

.guard-grid article.passed {
  border-left-color: #2fac66;
}

.guard-grid article span {
  grid-column: 1 / -1;
}

.history-card {
  grid-template-columns: minmax(0, 1.4fr) minmax(180px, 0.8fr);
  align-items: start;
}

.history-card > div {
  display: grid;
  gap: 5px;
}

.alert-stack button {
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
}

.alert-stack button span,
.alert-stack button em {
  grid-column: 2 / -1;
}

.soc-drawer-grid {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 10px 12px;
  padding: 12px;
  border: 1px solid rgba(179, 173, 163, 0.38);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.58);
}

.soc-drawer-grid span {
  color: var(--soc-text-muted);
}

.soc-drawer-grid strong {
  color: var(--soc-text);
}

.support-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.employee-task-list {
  display: grid;
  gap: 10px;
}

.employee-task-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: start;
  padding: 14px;
  border: 1px solid rgba(179, 173, 163, 0.36);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.78);
}

.employee-task-card > div:first-child {
  display: grid;
  gap: 5px;
}

.employee-task-card span,
.employee-task-card em {
  color: var(--soc-text-muted);
  font-size: 12px;
  font-style: normal;
  line-height: 1.6;
}

.employee-task-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.repair-list {
  display: grid;
  gap: 12px;
}

.repair-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 0.48fr);
  gap: 14px;
  padding: 16px;
  border: 1px solid rgba(179, 173, 163, 0.36);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.78);
}

.repair-card-main,
.repair-action-box,
.repair-dialog-body,
.repair-detail-drawer {
  display: grid;
  gap: 10px;
}

.repair-title-row,
.repair-meta,
.repair-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.repair-title-row {
  justify-content: space-between;
}

.repair-title-row strong {
  color: var(--soc-text);
  font-size: 16px;
}

.repair-card p,
.repair-action-box span,
.repair-meta span,
.repair-dialog-body p {
  margin: 0;
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.7;
}

.repair-action-box {
  padding: 12px;
  border: 1px solid rgba(212, 147, 74, 0.22);
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(255, 248, 238, 0.78), rgba(255, 255, 255, 0.7));
}

.repair-action-box span {
  color: var(--soc-warm-strong);
  font-weight: 760;
}

.repair-actions {
  justify-content: flex-end;
}

.queue-pagination {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  justify-content: flex-end;
  padding-top: 4px;
}

.queue-pagination > button:not(.el-button) {
  min-width: 28px;
  height: 24px;
  padding: 0 8px;
  border: 1px solid rgba(179, 173, 163, 0.42);
  border-radius: 7px;
  background: rgba(255, 255, 255, 0.68);
  color: var(--soc-text-muted);
  font-size: 12px;
  font-weight: 760;
  cursor: pointer;
}

.queue-pagination > button:not(.el-button).active,
.queue-pagination > button:not(.el-button):hover {
  border-color: rgba(212, 147, 74, 0.56);
  background: rgba(255, 248, 238, 0.94);
  color: var(--soc-warm-strong);
}

@media (max-width: 1020px) {
  .client-module-hero,
  .module-shell,
  .support-grid {
    grid-template-columns: 1fr;
  }

  .hero-actions,
  .drawer-actions {
    justify-content: flex-start;
  }

  .alert-stack button {
    grid-template-columns: 1fr;
  }

  .history-card {
    grid-template-columns: 1fr;
  }

  .employee-task-card {
    grid-template-columns: 1fr;
  }

  .repair-card {
    grid-template-columns: 1fr;
  }

  .action-option-grid,
  .guard-grid {
    grid-template-columns: 1fr;
  }

  .alert-stack button span,
  .alert-stack button em {
    grid-column: auto;
  }
}
</style>
