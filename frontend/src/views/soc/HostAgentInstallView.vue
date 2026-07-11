<template>
  <div class="page-shell host-agent-install-page">
    <section class="soc-page-hero install-hero">
      <div>
        <span class="soc-page-kicker">FULL EXPERT VIEW / AGENT INSTALL</span>
        <h1>Agent 安装命令设置与建立</h1>
        <p>{{ heroDescription }}</p>
      </div>
      <div class="install-hero-actions">
        <el-button :icon="Connection" plain @click="go('/soc/agents')">Agent 管理</el-button>
        <el-button :icon="CopyDocument" type="primary" @click="copyCommand">复制命令</el-button>
      </div>
    </section>

    <section class="install-workspace">
      <section class="soc-panel panel-pad install-form-panel">
        <div class="panel-head">
          <div>
            <h2>安装命令设置</h2>
            <p>主机环境、目录和 API 地址由后端实时读取；页面只允许在当前主机执行安装。</p>
          </div>
          <el-button :icon="Refresh" text :loading="contextLoading" @click="resetDefaults">重新读取主机</el-button>
        </div>

        <el-alert v-if="installContext" :title="installContext.message" :type="installContext.supported ? 'info' : 'warning'" show-icon :closable="false" />
        <el-alert
          v-if="hasRoutePreset"
          class="agent-preset-alert"
          :title="`已载入 ${form.agentName || form.agentId} 的安装配置`"
          description="主机、系统版本、架构、地址、Agent 版本和采集职责已从 Agent 管理带入。点击“在本机安装并校验”即可完成 Token 建立、安装、启动和真实上报验证。"
          type="success"
          show-icon
          :closable="false"
        />

        <el-form v-loading="contextLoading" label-position="top" class="install-form">
          <el-form-item label="目标系统">
            <div class="runtime-value">
              <el-tag :type="form.targetOs === 'macos' ? 'success' : 'warning'" effect="light">{{ runtimeLabel }}</el-tag>
              <span>{{ runtimeDetail }}</span>
            </div>
          </el-form-item>

          <el-form-item label="Agent ID">
            <el-input v-model="form.agentId" placeholder="一台主机固定一个 Agent ID" />
          </el-form-item>

          <div class="inline-fields">
            <el-form-item label="主机名">
              <el-input v-model="form.hostname" placeholder="真实主机名或预留主机名" />
            </el-form-item>
            <el-form-item label="显示名称">
              <el-input v-model="form.agentName" placeholder="Agent 管理页显示名称" />
            </el-form-item>
          </div>

          <div class="inline-fields">
            <el-form-item label="系统版本">
              <el-input v-model="form.osVersion" placeholder="macOS 14 / Windows 11" />
            </el-form-item>
            <el-form-item label="架构">
              <el-input v-model="form.architecture" placeholder="arm64 / amd64" />
            </el-form-item>
          </div>

          <el-form-item label="主机地址">
            <el-input v-model="form.ipAddresses" placeholder="可选，多个 IP 用逗号分隔" />
          </el-form-item>

          <el-form-item label="Agent 版本">
            <el-input v-model="form.agentVersion" placeholder="0.1.0-dev" />
            <small class="field-hint">用于标识本次部署的软件版本，并随 Agent 心跳上报。</small>
          </el-form-item>

          <el-form-item label="采集职责">
            <el-select v-model="form.profile" class="profile-select">
              <el-option v-for="profile in agentProfiles" :key="profile.value" :label="profile.label" :value="profile.value" />
            </el-select>
            <small class="field-hint">{{ selectedProfile.description }}</small>
          </el-form-item>

          <el-form-item label="后端 API">
            <el-input v-model="form.apiBaseUrl" readonly />
          </el-form-item>

          <el-form-item label="源码目录">
            <el-input v-model="form.projectRoot" readonly />
          </el-form-item>

          <el-form-item label="运行根目录">
            <el-input v-model="form.envRoot" readonly />
          </el-form-item>

          <section class="install-action-card">
            <div>
              <strong>页面直接安装</strong>
              <span>在当前主机建立 Token、安装运行时、启动并完成一次真实上报校验。</span>
            </div>
            <el-button
              type="primary"
              class="local-install-button"
              :loading="installing"
              :disabled="!installContext?.supported"
              @click="installOnLocalHost"
            >
              {{ hasRoutePreset ? '按此 Agent 配置安装并校验' : '在本机安装并校验' }}
            </el-button>
          </section>

          <section class="manual-token-card">
            <div class="manual-token-head">
              <div>
                <strong>手动命令安装</strong>
                <span>仅在需要复制命令到另一台同系统主机时使用。</span>
              </div>
              <el-radio-group v-model="form.tokenMode" size="small">
                <el-radio-button value="agent">平台生成 Token</el-radio-button>
                <el-radio-button value="admin">已有管理员 Token</el-radio-button>
              </el-radio-group>
            </div>
            <el-button class="manual-token-button" :loading="registering" @click="createAgentRegistration">
              生成一次性 Agent Token
            </el-button>
          </section>

          <el-alert
            v-if="registration"
            :title="registrationTitle"
            type="success"
            show-icon
            :closable="false"
          >
            <template #default>
              Token 已填入下方输入框和命令预览。离开页面或重置后不会恢复，请立即复制安装命令。
            </template>
          </el-alert>

          <el-form-item :label="tokenLabel" class="token-input-field">
            <el-input
              v-model="form.tokenValue"
              show-password
              :placeholder="tokenPlaceholder"
            />
            <el-checkbox v-model="form.embedSecret" class="secret-checkbox">
              复制命令时嵌入当前输入的令牌
            </el-checkbox>
          </el-form-item>

          <el-alert
            v-if="form.embedSecret"
            title="令牌会出现在复制后的命令里，只能在目标主机的临时会话中使用。"
            type="warning"
            show-icon
            :closable="false"
          />

          <el-form-item label="FIM 监控路径">
            <el-input v-model="form.fimPath" :placeholder="fimPlaceholder" />
          </el-form-item>

          <div class="inline-fields manual-runtime-settings">
            <el-form-item label="采集间隔">
              <el-input v-model="form.interval" placeholder="60s" />
            </el-form-item>
            <el-form-item label="本机常驻方式">
              <el-input :model-value="form.targetOs === 'macos' ? 'launchd（当前用户）' : 'Windows Service'" readonly />
            </el-form-item>
          </div>
        </el-form>
      </section>

      <section class="soc-panel panel-pad command-panel">
        <div class="panel-head">
          <div>
            <h2>安装命令预览</h2>
            <p>{{ commandSummary }}</p>
          </div>
          <el-tag effect="light" :type="form.targetOs === 'macos' ? 'success' : 'warning'">
            {{ form.targetOs === 'macos' ? 'macOS' : 'Windows' }}
          </el-tag>
        </div>
        <pre class="command-preview"><code>{{ generatedCommand }}</code></pre>
        <section v-if="installing || installProgress.finished" class="install-progress-panel" :class="{ failed: installProgress.failed }">
          <div class="install-progress-head">
            <div>
              <strong>{{ installProgress.label }}</strong>
              <small>{{ installProgress.detail }}</small>
            </div>
            <span>{{ installProgress.percentage }}%</span>
          </div>
          <el-progress :percentage="installProgress.percentage" :status="installProgress.failed ? 'exception' : installProgress.finished ? 'success' : undefined" :stroke-width="8" :show-text="false" />
          <div class="install-progress-steps">
            <span v-for="step in installProgressStages" :key="step.key" :class="{ active: installProgress.percentage >= step.threshold, complete: installProgress.finished && !installProgress.failed }">
              {{ step.label }}
            </span>
          </div>
        </section>
        <el-alert
          v-if="installResult"
          :title="installResult.message"
          :type="installResult.verified ? 'success' : 'warning'"
          show-icon
          :closable="false"
        >
          <template #default>
            <ul class="install-result-list">
              <li v-for="stage in installResult.stages" :key="stage.label">
                <strong>{{ stage.label }}</strong><span>{{ stage.detail }}</span>
              </li>
            </ul>
          </template>
        </el-alert>
        <div class="command-actions">
          <el-button type="primary" :loading="installing" :disabled="!installContext?.supported" @click="installOnLocalHost">安装并校验</el-button>
          <el-button :icon="CopyDocument" type="primary" @click="copyCommand">复制安装命令</el-button>
          <el-button :icon="Connection" plain @click="go('/soc/agents')">返回 Agent 管理</el-button>
        </div>
      </section>
    </section>

    <section class="install-support-grid">
      <section class="soc-panel panel-pad">
        <div class="panel-head compact">
          <div>
            <h2>建立流程</h2>
            <p>安装页只负责建立命令和交付步骤，心跳与真实数据仍回到 Agent 管理页核验。</p>
          </div>
        </div>
        <div class="install-step-list">
          <article v-for="step in installSteps" :key="step.index">
            <span>{{ step.index }}</span>
            <div>
              <strong>{{ step.label }}</strong>
              <small>{{ step.hint }}</small>
            </div>
          </article>
        </div>
      </section>

      <section class="soc-panel panel-pad">
        <div class="panel-head compact">
          <div>
            <h2>验收边界</h2>
            <p>macOS 当前可做真实采集闭环；Windows 先保留 Docker 宿主机实机验收位。</p>
          </div>
        </div>
        <div class="acceptance-list">
          <article v-for="item in acceptanceItems" :key="item.label" :class="item.tone">
            <div>
              <strong>{{ item.label }}</strong>
              <small>{{ item.hint }}</small>
            </div>
            <el-tag :type="item.tagType" effect="plain">{{ item.status }}</el-tag>
          </article>
        </div>
      </section>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Connection, CopyDocument, Refresh } from '@element-plus/icons-vue'
import {
  hostAgentLocalInstallContext,
  installHostAgentOnLocalHost,
  registerHostAgent,
  type HostAgentLocalInstallContext,
  type HostAgentLocalInstallResult,
  type HostAgentRegistrationResult,
} from '@/api/soc'

type TargetOs = 'macos' | 'windows'
type TokenMode = 'agent' | 'admin'
type AgentProfile = 'full' | 'host-log' | 'patrol-audit' | 'file-integrity' | 'baseline-audit'
type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'

const router = useRouter()
const route = useRoute()

const form = reactive({
  targetOs: 'macos' as TargetOs,
  agentId: '',
  agentName: '',
  hostname: '',
  osVersion: '',
  architecture: '',
  agentVersion: '',
  ipAddresses: '',
  apiBaseUrl: '',
  projectRoot: '',
  envRoot: '',
  tokenMode: 'agent' as TokenMode,
  tokenValue: '',
  embedSecret: false,
  fimPath: '',
  interval: '60s',
  profile: 'full' as AgentProfile,
  launchdScope: 'user',
  serviceName: 'CyberFusionHostAgent',
})
const registering = ref(false)
const registration = ref<HostAgentRegistrationResult>()
const contextLoading = ref(false)
const installing = ref(false)
const installContext = ref<HostAgentLocalInstallContext>()
const installResult = ref<HostAgentLocalInstallResult>()
const installProgress = reactive({ percentage: 0, label: '等待安装', detail: '点击安装后将建立 Token、安装运行时、启动 Agent 并验证心跳。', finished: false, failed: false })
let installProgressTimer: ReturnType<typeof window.setInterval> | undefined

const tokenLabel = computed(() => (form.tokenMode === 'agent' ? 'Agent Token' : '管理员 Access Token'))
const tokenPlaceholder = computed(() => (form.tokenMode === 'agent' ? 'PASTE_AGENT_TOKEN' : 'PASTE_ADMIN_ACCESS_TOKEN'))
const tokenVariable = computed(() => (form.tokenMode === 'agent' ? 'CYBERFUSION_AGENT_TOKEN' : 'CYBERFUSION_ADMIN_ACCESS_TOKEN'))
const fimPlaceholder = computed(() => (form.targetOs === 'macos' ? '$HOME/Documents' : 'C:/Users/Public/Documents'))
const runtimeLabel = computed(() => installContext.value?.runtime.label || '读取中')
const runtimeDetail = computed(() => {
  const runtime = installContext.value?.runtime
  return runtime ? `${runtime.osName} ${runtime.osVersion} · ${runtime.architecture}` : '正在读取当前后端所在主机环境'
})
const heroDescription = computed(() => installContext.value
  ? `当前目标为后端所在的 ${installContext.value.runtime.label} 主机。目录、主机名、IP、系统版本和 API 地址均来自实时环境检测。`
  : '正在读取后端所在主机的真实环境与安装能力。')
const agentProfiles = [
  { value: 'full', label: '全量主机安全', description: '资产、主机日志、巡回审计、FIM 和基线审计全部采集。' },
  { value: 'host-log', label: '主机日志', description: '采集资产身份与主机安全日志，不上报 FIM 或基线结果。' },
  { value: 'patrol-audit', label: '巡回审计', description: '采集资产、端口、进程、启动项和主机审计事件；不采集 FIM。' },
  { value: 'file-integrity', label: '文件完整性', description: '采集资产、FIM 指定路径摘要及关联检查，不上报主机日志。' },
  { value: 'baseline-audit', label: '基线审计', description: '采集资产与主机基线检查，不上报事件和 FIM。' },
] as const
const selectedProfile = computed(() => agentProfiles.find((profile) => profile.value === form.profile) || agentProfiles[0])
const hasRoutePreset = computed(() => Boolean(routeQueryString('agentId')))
const registrationTitle = computed(() => registration.value
  ? `${registration.value.agentId} 已建立，当前状态 ${agentStatusLabel(registration.value.status)}`
  : '')
const commandSummary = computed(() => {
  const runtimeMode = form.targetOs === 'macos' ? 'launchd 常驻' : 'Windows Service 常驻'
  const tokenState = form.tokenValue.trim() ? '已填入 Agent Token' : '等待填入 Agent Token'
  return `${runtimeMode}，运行目录位于源码目录外，${tokenState}。`
})

const generatedCommand = computed(() => (form.targetOs === 'macos' ? macosCommand() : windowsCommand()))
const installProgressStages = [
  { key: 'identity', label: '建立身份', threshold: 8 },
  { key: 'runtime', label: '写入运行时', threshold: 32 },
  { key: 'start', label: '启动服务', threshold: 58 },
  { key: 'verify', label: '等待心跳', threshold: 82 },
]

const installSteps = [
  { index: '01', label: '选择版本与职责', hint: '设置软件版本与主机日志、巡回审计、FIM、基线或全量采集职责。' },
  { index: '02', label: '目标主机执行', hint: '脚本写入 agent.env，构建或复制 Agent 二进制。' },
  { index: '03', label: '启动采集器', hint: 'macOS 使用 launchd 或前台进程，Windows 使用 Service 或前台进程。' },
  { index: '04', label: '回到管理页验收', hint: '通过心跳、队列、批次、事件、FIM 和拒收记录确认真实链路。' },
]

const acceptanceItems = computed<Array<{ label: string; hint: string; status: string; tagType: TagType; tone: string }>>(() => [
  { label: `${runtimeLabel.value} 本机安装`, hint: '通过页面只在当前后端宿主机执行安装、启动和一次真实上报校验。', status: installContext.value?.supported ? '可执行' : '不可用', tagType: installContext.value?.supported ? 'success' : 'warning', tone: installContext.value?.supported ? 'ready' : 'pending' },
  { label: '运行目录边界', hint: form.envRoot ? `所有二进制、配置、队列和日志写入 ${form.envRoot}。` : '正在读取外部运行根目录。', status: '实时读取', tagType: 'info', tone: 'idle' },
  { label: '令牌边界', hint: '服务端仅保存 token hash；本机安装时明文只写入受限运行配置，不回传页面。', status: '必须遵守', tagType: 'info', tone: 'idle' },
])

async function resetDefaults() {
  await loadInstallContext(true)
}

function resetTransientFields() {
  form.tokenMode = 'agent'
  form.tokenValue = ''
  form.embedSecret = false
  form.interval = '60s'
  form.profile = 'full'
  registration.value = undefined
  installResult.value = undefined
}

function applyRoutePreset() {
  form.agentId = routeQueryString('agentId') || form.agentId
  form.agentName = routeQueryString('agentName') || form.agentName
  form.hostname = routeQueryString('hostname') || form.hostname
  form.osVersion = routeQueryString('osVersion') || form.osVersion
  form.architecture = routeQueryString('architecture') || form.architecture
  form.agentVersion = routeQueryString('agentVersion') || form.agentVersion
  form.ipAddresses = routeQueryString('ip') || ''
  const profile = routeQueryString('profile')
  if (isAgentProfile(profile)) form.profile = profile
}

function isAgentProfile(value: string): value is AgentProfile {
  return ['full', 'host-log', 'patrol-audit', 'file-integrity', 'baseline-audit'].includes(value)
}

async function loadInstallContext(reset = false) {
  contextLoading.value = true
  try {
    const response = await hostAgentLocalInstallContext()
    const context = response.data.data
    installContext.value = context
    form.targetOs = context.runtime.osType === 'windows' ? 'windows' : 'macos'
    form.agentId = context.defaultAgentId
    form.agentName = context.defaultAgentName
    form.hostname = context.hostname
    form.osVersion = `${context.runtime.osName} ${context.runtime.osVersion}`.trim()
    form.architecture = context.runtime.architecture
    form.agentVersion = context.agentVersion
    form.ipAddresses = context.ipAddresses.join(', ')
    form.apiBaseUrl = context.apiBaseUrl
    form.projectRoot = context.projectRoot
    form.envRoot = context.envRoot
    form.fimPath = context.fimPath
    form.profile = 'full'
    form.launchdScope = 'user'
    form.serviceName = 'CyberFusionHostAgent'
    if (reset) resetTransientFields()
    applyRoutePreset()
  } catch (err) {
    ElMessage.error(apiErrorMessage(err, '无法读取当前主机安装环境'))
  } finally {
    contextLoading.value = false
  }
}

async function createAgentRegistration() {
  if (!form.hostname.trim()) {
    ElMessage.warning('请先填写主机名')
    return
  }
  if (!form.agentVersion.trim()) {
    ElMessage.warning('请先填写 Agent 版本')
    return
  }
  registering.value = true
  try {
    const res = await registerHostAgent({
      agentId: trimmedOrUndefined(form.agentId),
      agentName: trimmedOrUndefined(form.agentName),
      hostname: form.hostname.trim(),
      osType: form.targetOs,
      osVersion: trimmedOrUndefined(form.osVersion),
      architecture: trimmedOrUndefined(form.architecture),
      agentVersion: form.agentVersion.trim(),
      ipAddresses: splitCsv(form.ipAddresses),
      labels: {
        install: 'frontend',
        targetOs: form.targetOs,
        runtime: form.targetOs === 'macos' ? 'launchd' : 'windows-service',
        profile: form.profile,
      },
    })
    registration.value = res.data.data
    form.agentId = registration.value.agentId
    form.tokenMode = 'agent'
    form.tokenValue = registration.value.agentToken
    form.embedSecret = true
    ElMessage.success('Agent Token 已建立，请立即复制安装命令')
  } catch (err) {
    ElMessage.error(apiErrorMessage(err, 'Agent Token 建立失败'))
  } finally {
    registering.value = false
  }
}

async function installOnLocalHost() {
  if (!installContext.value?.supported) {
    ElMessage.warning('当前后端宿主机不支持通过页面直接安装 Agent')
    return
  }
  installing.value = true
  installResult.value = undefined
  startInstallProgress()
  try {
    const response = await installHostAgentOnLocalHost({
      agentId: form.agentId.trim(),
      agentName: form.agentName.trim(),
      hostname: form.hostname.trim(),
      agentVersion: form.agentVersion.trim(),
      profile: form.profile,
      ipAddresses: splitCsv(form.ipAddresses),
      fimPath: form.fimPath.trim(),
      interval: form.interval.trim(),
    })
    installResult.value = response.data.data
    finishInstallProgress(true, installResult.value.message)
    ElMessage.success(installResult.value.message)
  } catch (err) {
    finishInstallProgress(false, apiErrorMessage(err, '本机 Agent 安装或校验失败，请检查运行目录与本机依赖'))
    ElMessage.error(apiErrorMessage(err, '本机 Agent 安装或校验失败，请检查运行目录与本机依赖'))
  } finally {
    installing.value = false
  }
}

function startInstallProgress() {
  if (installProgressTimer) window.clearInterval(installProgressTimer)
  installProgress.percentage = 8
  installProgress.label = '正在建立 Agent 身份'
  installProgress.detail = '服务端正在生成本次安装使用的受限 Token。'
  installProgress.finished = false
  installProgress.failed = false
  const startedAt = Date.now()
  installProgressTimer = window.setInterval(() => {
    const elapsedSeconds = Math.floor((Date.now() - startedAt) / 1000)
    if (elapsedSeconds < 3) {
      installProgress.percentage = Math.max(installProgress.percentage, 22)
      installProgress.label = '正在写入本机运行时'
      installProgress.detail = '正在准备 Agent 二进制、受限配置和本地队列目录。'
    } else if (elapsedSeconds < 10) {
      installProgress.percentage = Math.max(installProgress.percentage, 52)
      installProgress.label = '正在启动 Agent 服务'
      installProgress.detail = '启动命令已提交，正在等待本机服务可用。'
    } else {
      installProgress.percentage = Math.min(92, Math.max(installProgress.percentage, 78))
      installProgress.label = '正在等待真实心跳与上报校验'
      installProgress.detail = '只有后端收到本次 Agent 的新心跳后，安装才会标记为成功。'
    }
  }, 800)
}

function finishInstallProgress(success: boolean, detail: string) {
  if (installProgressTimer) window.clearInterval(installProgressTimer)
  installProgressTimer = undefined
  installProgress.percentage = success ? 100 : Math.max(installProgress.percentage, 20)
  installProgress.label = success ? '安装并校验完成' : '安装或校验未完成'
  installProgress.detail = detail
  installProgress.finished = true
  installProgress.failed = !success
}

onBeforeUnmount(() => {
  if (installProgressTimer) window.clearInterval(installProgressTimer)
})

function tokenValueForCommand() {
  if (form.embedSecret && form.tokenValue.trim()) {
    return form.tokenValue.trim()
  }
  return `<${tokenPlaceholder.value}>`
}

function macosCommand() {
  const lines = [
    `cd ${shQuote(form.projectRoot)}`,
    `export CYBERFUSION_ENV_ROOT=${shQuote(form.envRoot)}`,
    `export CYBERFUSION_API_BASE=${shQuote(form.apiBaseUrl)}`,
    `export CYBERFUSION_AGENT_ID=${shQuote(form.agentId)}`,
    `export CYBERFUSION_AGENT_VERSION=${shQuote(form.agentVersion)}`,
    `export CYBERFUSION_AGENT_PROFILE=${shQuote(form.profile)}`,
    `export ${tokenVariable.value}=${shQuote(tokenValueForCommand())}`,
    `export CYBERFUSION_AGENT_FIM_PATH=${shQuote(form.fimPath)}`,
    `export CYBERFUSION_AGENT_INTERVAL=${shQuote(form.interval)}`,
    `export CYBERFUSION_AGENT_LAUNCHD_SCOPE=${shQuote(form.launchdScope)}`,
  ]
  lines.push('scripts/mac/install-agent.sh')
  lines.push('scripts/mac/start-agent.sh')
  lines.push('export CYBERFUSION_AGENT_UPLOAD_ONCE=1')
  lines.push('scripts/mac/verify-agent.sh')
  return lines.join('\n')
}

function windowsCommand() {
  const installArgs = [
    '-EnvRoot $env:CYBERFUSION_ENV_ROOT',
    '-ApiBaseUrl $env:CYBERFUSION_API_BASE',
    '-AgentId $env:CYBERFUSION_AGENT_ID',
    `-${form.tokenMode === 'agent' ? 'AgentToken' : 'AdminAccessToken'} $env:${tokenVariable.value}`,
    '-FimPath $env:CYBERFUSION_AGENT_FIM_PATH',
    '-ServiceName $env:CYBERFUSION_AGENT_SERVICE_NAME',
  ]
  const startArgs = [
    '-EnvRoot $env:CYBERFUSION_ENV_ROOT',
    '-AgentId $env:CYBERFUSION_AGENT_ID',
    '-ServiceName $env:CYBERFUSION_AGENT_SERVICE_NAME',
  ]
  const lines = [
    `$ProjectRoot = ${psQuote(form.projectRoot)}`,
    'Set-Location $ProjectRoot',
    `$env:CYBERFUSION_ENV_ROOT = ${psQuote(form.envRoot)}`,
    `$env:CYBERFUSION_API_BASE = ${psQuote(form.apiBaseUrl)}`,
    `$env:CYBERFUSION_AGENT_ID = ${psQuote(form.agentId)}`,
    `$env:CYBERFUSION_AGENT_VERSION = ${psQuote(form.agentVersion)}`,
    `$env:CYBERFUSION_AGENT_PROFILE = ${psQuote(form.profile)}`,
    `$env:${tokenVariable.value} = ${psQuote(tokenValueForCommand())}`,
    `$env:CYBERFUSION_AGENT_FIM_PATH = ${psQuote(form.fimPath)}`,
    `$env:CYBERFUSION_AGENT_SERVICE_NAME = ${psQuote(form.serviceName)}`,
    `.${'\\'}scripts${'\\'}win${'\\'}install-agent.ps1 ${installArgs.join(' ')}`,
  ]
  lines.push(`.${'\\'}scripts${'\\'}win${'\\'}start-agent.ps1 ${startArgs.join(' ')}`)
  lines.push(`.${'\\'}scripts${'\\'}win${'\\'}verify-agent.ps1 -EnvRoot $env:CYBERFUSION_ENV_ROOT -ApiBaseUrl $env:CYBERFUSION_API_BASE -AgentId $env:CYBERFUSION_AGENT_ID -ServiceName $env:CYBERFUSION_AGENT_SERVICE_NAME -UploadOnce`)
  return lines.join('\n')
}

function shQuote(value: string) {
  return `'${String(value || '').replace(/'/g, `'\"'\"'`)}'`
}

function psQuote(value: string) {
  return `'${String(value || '').replace(/'/g, "''")}'`
}

function splitCsv(value: string) {
  return value.split(',')
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, 32)
}

function trimmedOrUndefined(value: string) {
  const trimmed = value.trim()
  return trimmed || undefined
}

function routeQueryString(key: string) {
  const value = route.query[key]
  return Array.isArray(value) ? String(value[0] || '') : String(value || '')
}

function agentStatusLabel(status: string) {
  return ({ online: '在线', offline: '待心跳', disabled: '停用', warning: '异常' } as Record<string, string>)[status] || status
}

function apiErrorMessage(error: unknown, fallback: string) {
  const maybe = error as { response?: { data?: { message?: string } }; message?: string }
  return maybe.response?.data?.message || maybe.message || fallback
}

async function copyCommand() {
  try {
    await writeClipboard(generatedCommand.value)
    ElMessage.success('安装命令已复制')
  } catch {
    ElMessage.error('复制失败，请手动选中命令复制')
  }
}

async function writeClipboard(text: string) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text)
    return
  }
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.left = '-9999px'
  document.body.appendChild(textarea)
  textarea.select()
  document.execCommand('copy')
  document.body.removeChild(textarea)
}

function go(path: string) {
  router.push(path)
}

onMounted(() => loadInstallContext())
</script>

<style scoped>
.host-agent-install-page {
  width: 100%;
  max-width: 100%;
  overflow-x: hidden;
}

.install-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding-block: 18px;
}

.install-hero p {
  max-width: 760px;
}

.install-hero-actions,
.command-actions {
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
  align-items: flex-start;
  margin-bottom: 14px;
}

.panel-head.compact {
  margin-bottom: 12px;
}

.panel-head h2 {
  margin: 0;
  color: var(--soc-text);
  font-size: 18px;
  line-height: 1.25;
}

.panel-head p {
  margin: 6px 0 0;
  color: var(--soc-text-muted);
  font-size: 13px;
  line-height: 1.55;
}

.install-workspace {
  --install-workspace-height: 760px;
  display: grid;
  grid-template-columns: minmax(320px, 0.9fr) minmax(0, 1.1fr);
  gap: 12px;
  align-items: stretch;
}

.install-form-panel,
.command-panel {
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  height: var(--install-workspace-height);
  min-height: 0;
}

.install-form {
  display: grid;
  flex: 1;
  gap: 2px;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}

.install-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

.inline-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.runtime-value {
  display: flex;
  gap: 8px;
  align-items: center;
  min-height: 32px;
}

.runtime-value span {
  color: var(--soc-text-muted);
  font-size: 13px;
}

.field-hint {
  display: block;
  margin-top: 6px;
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.45;
}

.profile-select {
  width: 100%;
}

.secret-checkbox {
  margin-top: 8px;
}

.install-action-card,
.manual-token-card {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 12px;
  padding: 12px;
  border: 1px solid rgba(216, 128, 36, 0.36);
  border-radius: 8px;
  background: rgba(255, 248, 238, 0.68);
}

.install-action-card > div,
.manual-token-head > div {
  display: grid;
  gap: 4px;
}

.install-action-card strong,
.manual-token-card strong {
  color: var(--soc-text);
}

.install-action-card span,
.manual-token-card span {
  color: var(--soc-text-muted);
  font-size: 13px;
  line-height: 1.45;
}

.local-install-button {
  min-width: 156px;
  min-height: 36px;
  color: #fff;
  font-weight: 800;
}

.manual-token-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  border-color: rgba(129, 143, 166, 0.3);
  background: rgba(255, 255, 255, 0.56);
}

.manual-token-head {
  display: flex;
  grid-column: 1 / -1;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.manual-token-card :deep(.el-radio-button__inner) {
  min-width: 110px;
  color: var(--soc-text);
  font-weight: 700;
}

.manual-token-button {
  grid-column: 1 / -1;
  justify-self: start;
  min-height: 34px;
  border-color: var(--soc-warm);
  color: var(--soc-warm-strong);
  font-weight: 800;
}

.token-input-field {
  padding: 10px;
  border: 1px solid rgba(129, 143, 166, 0.24);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.5);
}

.command-panel {
  min-width: 0;
}

.command-preview {
  flex: 1;
  overflow: auto;
  min-height: 0;
  margin: 0;
  padding: 14px;
  border: 1px solid rgba(129, 143, 166, 0.24);
  border-radius: 8px;
  background: rgba(22, 31, 46, 0.94);
  color: #f5f7fb;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre;
}

.command-actions {
  margin-top: 12px;
}

.install-progress-panel {
  display: grid;
  gap: 10px;
  margin-top: 12px;
  padding: 12px;
  border: 1px solid rgba(75, 139, 220, 0.3);
  border-radius: 8px;
  background: rgba(242, 248, 255, 0.76);
}

.install-progress-panel.failed {
  border-color: rgba(190, 73, 51, 0.36);
  background: rgba(255, 245, 243, 0.82);
}

.install-progress-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.install-progress-head > div {
  min-width: 0;
}

.install-progress-head strong,
.install-progress-head small {
  display: block;
}

.install-progress-head strong {
  color: var(--soc-text);
}

.install-progress-head small {
  margin-top: 3px;
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.45;
}

.install-progress-head > span {
  color: var(--soc-warm-strong);
  font-size: 14px;
  font-weight: 800;
}

.install-progress-steps {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
}

.install-progress-steps span {
  overflow: hidden;
  color: var(--soc-text-subtle);
  font-size: 11px;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.install-progress-steps span.active {
  color: var(--soc-text);
  font-weight: 700;
}

.install-progress-steps span.complete {
  color: #15865a;
}

.install-result-list {
  display: grid;
  gap: 6px;
  margin: 8px 0 0;
  padding-left: 18px;
}

.install-result-list li {
  display: grid;
  gap: 2px;
}

.install-result-list strong {
  color: var(--soc-text);
}

.install-result-list span {
  color: var(--soc-text-muted);
  font-size: 13px;
}

.install-support-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
}

.install-step-list,
.acceptance-list {
  display: grid;
  gap: 10px;
}

.install-step-list article,
.acceptance-list article {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 12px;
  border: 1px solid rgba(129, 143, 166, 0.22);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.48);
}

.install-step-list article > span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 8px;
  color: var(--soc-warm-strong);
  font-weight: 800;
  background: rgba(216, 128, 36, 0.12);
}

.install-step-list strong,
.acceptance-list strong {
  display: block;
  color: var(--soc-text);
}

.install-step-list small,
.acceptance-list small {
  display: block;
  margin-top: 4px;
  color: var(--soc-text-muted);
  line-height: 1.45;
}

.acceptance-list article {
  grid-template-columns: minmax(0, 1fr) auto;
}

.acceptance-list article.ready {
  border-color: rgba(28, 143, 88, 0.28);
}

.acceptance-list article.pending {
  border-color: rgba(214, 130, 39, 0.34);
}

@media (max-width: 1080px) {
  .install-workspace,
  .install-support-grid,
  .install-hero {
    grid-template-columns: 1fr;
  }

  .install-hero-actions,
  .command-actions {
    justify-content: flex-start;
  }

  .install-form-panel,
  .command-panel {
    height: auto;
  }

  .install-form {
    overflow: visible;
  }
}

@media (max-width: 720px) {
  .inline-fields {
    grid-template-columns: 1fr;
  }

  .install-step-list article {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .manual-token-card {
    grid-template-columns: 1fr;
  }

  .manual-token-head {
    grid-column: auto;
    align-items: flex-start;
    flex-direction: column;
  }

  .manual-token-button {
    grid-column: auto;
  }

  .install-progress-steps {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
