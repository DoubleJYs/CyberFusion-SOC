<template>
  <div class="client-page" v-loading="loading">
    <section class="client-hero">
      <div>
        <span>DEVICE PROTECTION</span>
        <h1>本机保护</h1>
        <p>仅展示当前账号所拥有电脑的采集连接、文件监控和基线检查状态。</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新状态</el-button>
    </section>

    <el-alert v-if="status.agentStatus === 'no_device'" title="当前账号尚未关联电脑，暂时无法读取本机保护状态。" type="info" show-icon :closable="false" />

    <template v-else>
      <section class="protection-summary">
        <article class="status-card" :class="`status-${status.agentStatus}`">
          <span>采集连接</span>
          <strong>{{ agentStatusLabel }}</strong>
          <small>{{ status.agentName || '尚未安装本机采集器' }}</small>
        </article>
        <article class="status-card"><span>24h 安全记录</span><strong>{{ status.event24hCount }}</strong><small>仅当前电脑</small></article>
        <article class="status-card"><span>24h 文件变更</span><strong>{{ status.fim24hCount }}</strong><small>已授权监控目录</small></article>
        <article class="status-card"><span>基线异常</span><strong>{{ status.baselineFailureCount }}</strong><small>需要复核的检查项</small></article>
      </section>

      <section class="client-panel">
        <div class="panel-title"><strong>{{ status.hostname || '当前电脑' }}</strong><span>{{ status.osType || '未知系统' }} · {{ status.assetIp || '-' }}</span></div>
        <div class="protection-detail-grid">
          <div><span>最后心跳</span><strong>{{ status.lastHeartbeatAt || '尚未收到心跳' }}</strong></div>
          <div><span>待上传队列</span><strong>{{ status.queueDepth }} 条 / {{ formatBytes(status.queueBytes) }}</strong></div>
          <div><span>下一步</span><strong>{{ nextAction }}</strong></div>
        </div>
        <div class="protection-actions">
          <el-button type="primary" :loading="installing" @click="openInstallSettings">{{ canControlAgent ? '重新设置安装' : '设置并安装 Agent' }}</el-button>
          <el-button v-if="canControlAgent" :type="status.agentStatus === 'online' ? 'warning' : 'success'" :loading="runtimeOperating" @click="toggleRuntime">
            {{ status.agentStatus === 'online' ? '关闭保护' : '启动保护' }}
          </el-button>
          <el-button type="primary" @click="router.push({ path: '/client/workbench', query: clientDeviceQuery })">查看我的电脑</el-button>
          <el-button @click="router.push({ path: '/client/security-logs', query: clientDeviceQuery })">查看安全日志</el-button>
        </div>
      </section>
    </template>

    <el-dialog v-model="settingsVisible" title="本机 Agent 设置与安装" width="560px" :close-on-click-modal="!installing">
      <el-alert title="安装只在当前打开页面的这台电脑执行。安装 Token 只写入本机运行配置，不会显示或保存到浏览器。" type="info" show-icon :closable="false" />
      <el-form class="install-form" label-position="top">
        <el-form-item label="采集职责">
          <el-select v-model="installForm.profile">
            <el-option label="完整保护" value="full" />
            <el-option label="主机日志" value="host-log" />
            <el-option label="巡检审计" value="patrol-audit" />
            <el-option label="文件变更" value="file-integrity" />
            <el-option label="基线核查" value="baseline-audit" />
          </el-select>
        </el-form-item>
        <el-form-item label="Agent 版本"><el-input v-model="installForm.agentVersion" /></el-form-item>
        <el-form-item label="文件监控目录"><el-input v-model="installForm.fimPath" /></el-form-item>
        <el-form-item label="采集间隔"><el-select v-model="installForm.interval"><el-option label="30 秒" value="30s" /><el-option label="60 秒" value="60s" /><el-option label="5 分钟" value="5m" /></el-select></el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="installing" @click="settingsVisible = false">取消</el-button>
        <el-button type="primary" :loading="installing" @click="installAgent">安装并校验</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="progressVisible" title="本机 Agent 安装进度" width="480px" :close-on-click-modal="false" :show-close="!installing">
      <div class="install-progress">
        <div class="progress-heart" :class="{ beating: installProgress >= 70 }">♥</div>
        <strong>{{ installStage }}</strong>
        <el-progress :percentage="installProgress" :status="installFailed ? 'exception' : installProgress === 100 ? 'success' : undefined" />
        <p>{{ installMessage }}</p>
      </div>
      <template #footer><el-button v-if="!installing" type="primary" @click="progressVisible = false">完成</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import type { ApiResult } from '@/types/api'
import { buildClientDeviceRouteQuery } from '@/composables/useClientDeviceContext'

interface ProtectionStatus {
  assetIp?: string
  hostname?: string
  osType?: string
  agentStatus: string
  agentName?: string
  lastHeartbeatAt?: string
  agentControllable: boolean
  queueDepth: number
  queueBytes: number
  event24hCount: number
  fim24hCount: number
  baselineFailureCount: number
}

interface LocalInstallContext {
  agentVersion: string
  fimPath: string
  supported: boolean
  message: string
}

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const installing = ref(false)
const runtimeOperating = ref(false)
const settingsVisible = ref(false)
const progressVisible = ref(false)
const installProgress = ref(0)
const installStage = ref('准备安装')
const installMessage = ref('正在读取本机安装环境。')
const installFailed = ref(false)
let installTimer: ReturnType<typeof window.setInterval> | undefined
const status = reactive<ProtectionStatus>({ agentStatus: 'no_device', agentControllable: false, queueDepth: 0, queueBytes: 0, event24hCount: 0, fim24hCount: 0, baselineFailureCount: 0 })
const installForm = reactive({ agentVersion: '0.1.0-dev', profile: 'full', fimPath: '', interval: '60s' })
const clientDeviceQuery = computed(() => buildClientDeviceRouteQuery({ ip: status.assetIp || String(route.query.ip || ''), host: status.hostname || String(route.query.host || ''), os: status.osType || String(route.query.os || '') }))
const agentStatusLabel = computed(() => status.agentStatus === 'online' ? '已连接' : status.agentStatus === 'not_installed' ? '未安装' : '等待心跳')
const hasAgent = computed(() => !['not_installed', 'no_device'].includes(status.agentStatus))
const canControlAgent = computed(() => hasAgent.value && status.agentControllable)
const nextAction = computed(() => status.agentStatus === 'online' ? '保持采集与定期查看待办' : canControlAgent.value ? '可由当前用户启动本机 Agent，收到心跳后即进入保护状态' : hasAgent.value ? '这是一条历史 Agent 记录，请在本机重新设置安装后再启动保护' : '可由当前用户设置采集职责并安装本机 Agent')

onMounted(() => void load())
onBeforeUnmount(() => { if (installTimer) window.clearInterval(installTimer) })

async function load() {
  loading.value = true
  try {
    const response = await request.get<ApiResult<ProtectionStatus>>('/client/protection/status', { params: { assetIp: route.query.ip } })
    Object.assign(status, response.data.data)
  } finally {
    loading.value = false
  }
}

async function openInstallSettings() {
  try {
    const response = await request.get<ApiResult<LocalInstallContext>>('/client/protection/install-context', { params: { assetIp: status.assetIp } })
    const context = response.data.data
    Object.assign(installForm, { agentVersion: context.agentVersion, fimPath: context.fimPath || installForm.fimPath })
    settingsVisible.value = true
  } catch {
    // The shared request interceptor already presents the server boundary reason.
  }
}

async function installAgent() {
  installing.value = true
  installFailed.value = false
  settingsVisible.value = false
  progressVisible.value = true
  installProgress.value = 12
  installStage.value = '建立本机 Agent 身份'
  installMessage.value = '正在为当前用户的电脑写入受限安装配置。'
  let tick = 0
  if (installTimer) window.clearInterval(installTimer)
  installTimer = window.setInterval(() => {
    tick += 1
    installProgress.value = Math.min(78, 12 + tick * 8)
    if (installProgress.value >= 38) installStage.value = '写入本机运行时'
    if (installProgress.value >= 62) installStage.value = '启动并等待心跳'
  }, 900)
  try {
    const response = await request.post<ApiResult<{ message: string; verified: boolean }>>('/client/protection/install', { assetIp: status.assetIp, ...installForm })
    installProgress.value = 100
    installStage.value = response.data.data.verified ? '安装与真实校验完成' : '安装完成，等待新心跳'
    installMessage.value = response.data.data.message
    if (response.data.data.verified) {
      ElMessage.success('本机 Agent 已完成安装并收到新心跳')
    } else {
      ElMessage.warning('安装进程已启动，等待新心跳后才会显示为已连接')
    }
    await load()
  } catch {
    installFailed.value = true
    installStage.value = '安装未完成'
    installMessage.value = '请检查当前电脑环境、监控目录和本机运行日志后重试。'
  } finally {
    if (installTimer) window.clearInterval(installTimer)
    installTimer = undefined
    installing.value = false
  }
}

async function toggleRuntime() {
  const action = status.agentStatus === 'online' ? 'stop' : 'start'
  runtimeOperating.value = true
  try {
    const response = await request.post<ApiResult<{ message: string }>>('/client/protection/runtime', null, { params: { assetIp: status.assetIp, action } })
    ElMessage.success(response.data.data.message)
    await load()
  } finally {
    runtimeOperating.value = false
  }
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
</script>

<style scoped>
.client-page { max-width: 1180px; margin: 0 auto; padding: 16px 10px 38px; }
.client-hero { display:flex; align-items:center; justify-content:space-between; gap:16px; margin-bottom:14px; padding:22px; border:1px solid var(--soc-border); border-radius:8px; background:rgba(255,255,255,.7); }
.client-hero span, .client-hero p { color:var(--soc-text-muted); }
.client-hero h1 { margin:6px 0; font-size:24px; }
.client-hero p { margin:0; }
.protection-summary { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:12px; }
.status-card, .client-panel { border:1px solid var(--soc-border); border-radius:8px; background:rgba(255,255,255,.72); padding:16px; }
.status-card span, .status-card small { display:block; color:var(--soc-text-muted); }
.status-card strong { display:block; margin:12px 0 6px; font-size:26px; }
.status-online { border-color:rgba(58,160,105,.52); }
.status-not_installed { border-color:rgba(212,147,74,.52); }
.client-panel { margin-top:14px; }
.panel-title strong, .panel-title span { display:block; }
.panel-title span { margin-top:4px; color:var(--soc-text-muted); }
.protection-detail-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:12px; margin-top:16px; }
.protection-detail-grid div { border-top:1px solid var(--soc-border); padding-top:10px; }
.protection-detail-grid span, .protection-detail-grid strong { display:block; }
.protection-detail-grid span { color:var(--soc-text-muted); font-size:12px; }
.protection-detail-grid strong { margin-top:5px; }
.protection-actions { display:flex; gap:10px; margin-top:18px; }
.install-form { margin-top: 16px; }
.install-progress { display: grid; gap: 14px; text-align: center; }
.progress-heart { color: #c6cedb; font-size: 42px; line-height: 1; }
.progress-heart.beating { color: #dc5f6b; animation: heartbeat 0.9s ease-in-out infinite; }
.install-progress p { margin: 0; color: var(--soc-text-muted); font-size: 13px; line-height: 1.5; }
@keyframes heartbeat { 0%, 100% { transform: scale(1); } 45% { transform: scale(1.18); } }
@media (max-width: 860px) { .protection-summary, .protection-detail-grid { grid-template-columns:1fr 1fr; } }
@media (max-width: 560px) { .client-hero { align-items:flex-start; flex-direction:column; } .protection-summary, .protection-detail-grid { grid-template-columns:1fr; } }
</style>
